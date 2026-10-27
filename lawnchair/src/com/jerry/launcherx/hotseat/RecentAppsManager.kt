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
            val apps = queryRecentApps(excludePackages)
            _recentApps.value = apps
        }
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
        private const val USAGE_WINDOW_MS = 24 * 60 * 60 * 1000L

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
