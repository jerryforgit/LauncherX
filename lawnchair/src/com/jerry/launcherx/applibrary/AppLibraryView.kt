package com.jerry.launcherx.applibrary

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.Executors
import kotlin.math.max

/**
 * iOS-style app library: the apps installed on the device grouped into category cards, shown when
 * the user swipes past the last workspace page.
 */
class AppLibraryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private data class Entry(
        val packageName: String,
        val component: ComponentName,
        val label: String,
        val icon: Drawable?,
        val category: Int,
        val info: AppInfo,
    )

    private val density = resources.displayMetrics.density
    private val isDark = Utilities.isDarkTheme(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cardColor = if (isDark) 0xFF23262B.toInt() else 0xFFFFFFFF.toInt()
    private val pageColor = if (isDark) 0xFF111316.toInt() else 0xFFF4F6F8.toInt()
    private val titleColor = if (isDark) 0xFFF2F3F5.toInt() else 0xFF1A1C1E.toInt()
    private val labelColor = if (isDark) 0xFFD6D9DE.toInt() else 0xFF3C4043.toInt()

    private var contentBuilt = false
    private var building = false
    private val categories = LinearLayout(context)
    private var search: EditText? = null
    private var allEntries: List<Entry> = emptyList()

    init {
        setBackgroundColor(pageColor)
        isClickable = true
        buildShell()
    }

    private fun buildShell() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(12))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(context).apply {
                text = context.getString(R.string.app_library_title)
                setTextColor(titleColor)
                textSize = 26f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        header.addView(
            TextView(context).apply {
                text = "✕"
                setTextColor(labelColor)
                textSize = 20f
                gravity = Gravity.CENTER
                contentDescription = context.getString(R.string.app_library_close)
                setOnClickListener { (context as? Launcher)?.hideAppLibrary() }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            },
        )
        root.addView(header)

        val searchField = EditText(context).apply {
            hint = context.getString(R.string.app_library_search_hint)
            setSingleLine()
            textSize = 15f
            setTextColor(titleColor)
            setHintTextColor(labelColor and 0x99FFFFFF.toInt())
            background = roundedBackground(
                if (isDark) 0xFF2C3036.toInt() else 0xFFFFFFFF.toInt(),
                14,
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) }
            addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(s: Editable?) = applyFilter(s?.toString().orEmpty())
                },
            )
        }
        search = searchField
        root.addView(searchField)

        catsRoot = categories
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(
                categories,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply { topMargin = dp(12) },
        )
        addView(
            root,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    private var catsRoot: LinearLayout? = null

    /** Builds the content once; safe to call every time the library is shown. */
    fun prepare() {
        if (contentBuilt || building) return
        building = true
        Executors.MODEL_EXECUTOR.execute {
            val entries = runCatching { loadEntries() }.getOrDefault(emptyList())
            mainHandler.post {
                allEntries = entries
                building = false
                contentBuilt = true
                renderGroups(groupByCategory(entries))
            }
        }
    }

    private fun loadEntries(): List<Entry> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val user = Process.myUserHandle()
        val iconCache = LauncherAppState.getInstance(context).iconCache
        val result = ArrayList<Entry>()
        for (lai in launcherApps.getActivityList(null, user)) {
            val appInfo = AppInfo(context, lai, user)
            runCatching {
                iconCache.getTitleAndIcon(
                    appInfo,
                    lai,
                    com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG,
                )
            }
            val icon = runCatching { appInfo.newIcon(context) }.getOrNull()
            val label = appInfo.title?.toString().takeUnless { it.isNullOrEmpty() }
                ?: lai.label.toString()
            result.add(
                Entry(
                    packageName = appInfo.targetPackage ?: lai.applicationInfo.packageName,
                    component = ComponentName(lai.applicationInfo.packageName, lai.name),
                    label = label,
                    icon = icon,
                    category = runCatching { lai.applicationInfo.category }.getOrDefault(-1),
                    info = appInfo,
                ),
            )
        }
        return result.sortedBy { it.label.lowercase() }
    }

    private fun categoryName(category: Int): String = when (category) {
        ApplicationInfo.CATEGORY_SOCIAL -> context.getString(R.string.app_library_cat_social)
        ApplicationInfo.CATEGORY_GAME -> context.getString(R.string.app_library_cat_games)
        ApplicationInfo.CATEGORY_AUDIO,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_IMAGE,
        -> context.getString(R.string.app_library_cat_media)
        ApplicationInfo.CATEGORY_NEWS -> context.getString(R.string.app_library_cat_news)
        ApplicationInfo.CATEGORY_MAPS -> context.getString(R.string.app_library_cat_maps)
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> context.getString(R.string.app_library_cat_productivity)
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> context.getString(R.string.app_library_cat_utilities)
        else -> context.getString(R.string.app_library_cat_other)
    }

    private fun groupByCategory(entries: List<Entry>): List<Pair<String, List<Entry>>> =
        entries.groupBy { categoryName(it.category) }
            .toList()
            .sortedByDescending { it.second.size }

    private fun renderGroups(groups: List<Pair<String, List<Entry>>>) {
        val container = catsRoot ?: return
        container.orientation = LinearLayout.VERTICAL
        container.removeAllViews()
        if (groups.isEmpty()) {
            container.addView(
                TextView(context).apply {
                    text = context.getString(R.string.app_library_empty)
                    setTextColor(labelColor)
                    textSize = 15f
                    setPadding(dp(8), dp(24), dp(8), dp(24))
                },
            )
            return
        }
        for ((name, entries) in groups) {
            container.addView(createCategoryCard(name, entries))
        }
    }

    private fun createCategoryCard(name: String, entries: List<Entry>): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(cardColor, 20)
            setPadding(dp(16), dp(14), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(14) }
        }
        card.addView(
            TextView(context).apply {
                text = name
                setTextColor(titleColor)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, dp(10))
            },
        )
        var row: LinearLayout? = null
        entries.forEachIndexed { index, entry ->
            if (index % 4 == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                card.addView(row)
            }
            row?.addView(createAppItem(entry))
        }
        // Pad the last row so items keep their column width.
        val lastRow = row
        if (lastRow != null) {
            val missing = 4 - (entries.size % 4)
            if (missing in 1..3) {
                repeat(missing) {
                    lastRow.addView(
                        View(context),
                        LinearLayout.LayoutParams(0, dp(1), 1f),
                    )
                }
            }
        }
        return card
    }

    private fun createAppItem(entry: Entry): View {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(6), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
        }
        item.addView(
            ImageView(context).apply {
                setImageDrawable(entry.icon)
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            },
        )
        item.addView(
            TextView(context).apply {
                text = entry.label
                setTextColor(labelColor)
                textSize = 11.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            },
        )
        item.setOnClickListener {
            val launcher = context as? Launcher ?: return@setOnClickListener
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(entry.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launcher.startActivitySafely(it, intent, entry.info)
        }
        return item
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allEntries
        } else {
            allEntries.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        renderGroups(groupByCategory(filtered))
    }

    private fun dp(value: Int): Int = max(1, (value * density).toInt())

    private fun roundedBackground(color: Int, radiusDp: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
        }
}
