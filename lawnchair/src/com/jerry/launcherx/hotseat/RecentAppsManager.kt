package com.jerry.launcherx.hotseat

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.jerry.launcherx.preferences.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecentAppInfo(
    val packageName: String,
    val label: String,
    val lastUsedTime: Long,
)

class RecentAppsManager(private val context: Context) {

    private val _recentApps = MutableStateFlow<List<RecentAppInfo>>(emptyList())
    val recentApps: StateFlow<List<RecentAppInfo>> = _recentApps.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var excludeProvider: () -> Set<String> = { emptySet() }

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val prefs = PreferenceManager.getInstance(context)

    init {
        // The strip is meant to be stable: restoring the last known list immediately keeps the
        // icons visible after a launcher restart (the usage query alone only knows the last week).
        _recentApps.value = loadPersisted()
    }

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE)
            as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            "android:get_usage_stats",
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun startObserving(provider: () -> Set<String>) {
        // Initial load only; further updates are triggered on every return to
        // the desktop (Launcher.onResume) and when an app is launched from
        // the strip itself (refreshNow). The exclude set is obtained lazily on
        // the calling (main) thread so it reflects the pinned dock once the
        // model has been bound, instead of a stale empty snapshot taken here.
        excludeProvider = provider
        refresh()
    }

    fun refresh() {
        val excludePackages = excludeProvider()
        scope.launch {
            val fresh = queryRecentApps(excludePackages)
            // Never drop entries just because they fell out of the usage window: only a newly
            // used app should push the oldest one out (or an uninstall / pin change).
            val merged = mergeRecentApps(fresh, _recentApps.value, excludePackages)
            _recentApps.value = merged
            persist(merged)
        }
    }

    private fun mergeRecentApps(
        fresh: List<RecentAppInfo>,
        previous: List<RecentAppInfo>,
        excludePackages: Set<String>,
    ): List<RecentAppInfo> {
        val merged = LinkedHashMap<String, RecentAppInfo>()
        fresh.forEach { merged[it.packageName] = it }
        previous.forEach { merged.putIfAbsent(it.packageName, it) }
        return merged.values
            .filter { it.packageName !in excludePackages }
            .filter { isLaunchable(it.packageName) }
            .take(maxCount)
            .map { app ->
                // Refresh a stale label (e.g. after an app update).
                val label = currentLabel(app.packageName)
                if (label != null && label != app.label) app.copy(label = label) else app
            }
    }

    private fun isLaunchable(packageName: String): Boolean = runCatching {
        launcherApps.getActivityList(packageName, Process.myUserHandle()).isNotEmpty()
    }.getOrDefault(false)

    private fun currentLabel(packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    private fun loadPersisted(): List<RecentAppInfo> {
        val raw = runCatching { prefs.recentAppsStrip.get() }.getOrNull()
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val pkg = item.optString("pkg")
                    if (pkg.isEmpty() || !isLaunchable(pkg)) continue
                    add(
                        RecentAppInfo(
                            packageName = pkg,
                            label = item.optString("label"),
                            lastUsedTime = item.optLong("time"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(apps: List<RecentAppInfo>) {
        val array = org.json.JSONArray()
        apps.forEach { app ->
            array.put(
                org.json.JSONObject().apply {
                    put("pkg", app.packageName)
                    put("label", app.label)
                    put("time", app.lastUsedTime)
                },
            )
        }
        runCatching { prefs.recentAppsStrip.set(array.toString()) }
    }

    fun refreshNow() {
        refresh()
    }

    private val maxCount: Int
        get() = PreferenceManager.getInstance(context)
            .hotseatRecentAppsMaxCount.get().coerceIn(1, 10)

    private suspend fun queryRecentApps(
        excludePackages: Set<String>,
    ): List<RecentAppInfo> = withContext(Dispatchers.IO) {
        if (!hasUsagePermission()) {
            return@withContext emptyList()
        }

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - USAGE_WINDOW_MS

        val usageEvents = try {
            usageStatsManager.queryEvents(startTime, endTime)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query usage events", e)
            return@withContext emptyList()
        }

        val packageLastUsed = LinkedHashMap<String, Long>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED &&
                event.eventType != UsageEvents.Event.ACTIVITY_PAUSED
            ) {
                continue
            }
            val pkg = event.packageName ?: continue
            if (pkg == context.packageName || excludePackages.contains(pkg)) continue
            val existing = packageLastUsed[pkg] ?: 0L
            if (event.timeStamp > existing) {
                packageLastUsed[pkg] = event.timeStamp
            }
        }

        val result = packageLastUsed.entries
            .sortedByDescending { it.value }
            .asSequence()
            // Usage events include system packages (dialogs, other launchers)
            // that have no launcher activity; skip them so they never occupy
            // an empty slot in the strip.
            .filter { (pkg, _) ->
                !launcherApps.getActivityList(pkg, Process.myUserHandle()).isEmpty()
            }
            .mapNotNull { (pkg, time) ->
                resolveAppInfo(pkg, time)
            }
            .take(maxCount)
            .toList()
        result
    }

    private fun resolveAppInfo(packageName: String, lastUsedTime: Long): RecentAppInfo? {
        return try {
            val pm = context.packageManager
            val label = pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, 0),
            ).toString()
            RecentAppInfo(
                packageName = packageName,
                label = label,
                lastUsedTime = lastUsedTime,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    companion object {
        private const val TAG = "RecentAppsManager"
        /**
         * How far back the usage query looks. This only seeds/updates the strip; entries are kept
         * after they age out, so the window can be wider than the 24h it used to be.
         */
        private const val USAGE_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: RecentAppsManager? = null

        @JvmStatic
        fun getInstance(context: Context): RecentAppsManager {
            return instance ?: synchronized(this) {
                instance ?: RecentAppsManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
