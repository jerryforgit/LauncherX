package com.jerry.launcherx.hotseat

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.AttributeSet
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jerry.launcherx.LauncherXApp
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.Executors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecentAppsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    private val manager = RecentAppsManager.getInstance(context)
    private val density = resources.displayMetrics.density
    private var iconSize = (ICON_SIZE_DP * density).toInt()
    private var appSpacing = (APP_SPACING_DP * density).toInt()
    private val dividerWidth = (DIVIDER_WIDTH_DP * density).toInt()

    // Divider settings (loaded from prefs)
    var dividerEnabled = true
        set(value) {
            if (field != value) {
                field = value
                forceRebuild = true
                if (childCount > 0) updateApps(currentApps)
            }
        }
    var dividerColor = Color.parseColor("#888888")
        set(value) {
            if (field != value) {
                field = value
                applyDividerPaintColor()
                dividerView?.invalidate()
            }
        }

    /** Divider opacity, 0–100 (%). Applied on top of [dividerColor]'s RGB. */
    var dividerAlphaPercent = 40
        set(value) {
            val v = value.coerceIn(0, 100)
            if (field != v) {
                field = v
                applyDividerPaintColor()
                dividerView?.invalidate()
            }
        }

    /** Divider height as a percentage of the strip height (icon size); 100 = full height. */
    var dividerHeightPercent = 58
        set(value) {
            val v = value.coerceIn(0, 100)
            if (field != v) {
                field = v
                dividerView?.requestLayout()
            }
        }

    private var dividerView: View? = null

    /** Space between the strip's left edge and the first app, in px. */
    private var leftPaddingPx = 0

    /** Space between the last app and the strip's right edge, in px. */
    private var rightPaddingPx = 0

    /** Width of the leading slot (divider space) in px; 0 when the divider is off. */
    private var leadingSlotPx = 0

    private fun applyDividerPaintColor() {
        dividerPaint.color = Color.argb(
            (dividerAlphaPercent * 255) / 100,
            Color.red(dividerColor),
            Color.green(dividerColor),
            Color.blue(dividerColor),
        )
    }

    private fun currentDividerHeightPx(): Int = (iconSize * dividerHeightPercent) / 100

    fun setIconSize(sizePx: Int) {
        if (sizePx > 0 && sizePx != iconSize) {
            iconSize = sizePx
            forceRebuild = true
            if (childCount > 0) {
                updateApps(currentApps)
            }
        }
    }

    /** Horizontal gap between two recent-app icons, in pixels. */
    fun setAppSpacingPx(spacingPx: Int) {
        val spacing = spacingPx.coerceAtLeast(0)
        if (spacing == appSpacing) return
        appSpacing = spacing
        // Keep the loaded icon views, only re-space them.
        applyIconLayout()
        requestLayout()
    }

    /**
     * Lays the icons out edge to edge: every icon is exactly [iconSize] wide and neighbours
     * are separated by [appSpacing]. There is no half-cell slack at the strip's outer edges,
     * so the capsule background hugs the outermost icons.
     */
    private fun applyIconLayout() {
        for (i in 1 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? LayoutParams
                ?: LayoutParams(iconSize, LayoutParams.WRAP_CONTENT).also { child.layoutParams = it }
            lp.width = iconSize
            lp.marginStart = if (i > 1) appSpacing else 0
            child.layoutParams = lp
        }
    }

    /** Inner left/right padding of the strip, in pixels. */
    /**
     * Sets the space between the strip's edges and its outermost apps, in pixels. The left gap
     * also hosts the separator line (when enabled).
     */
    fun setStripHorizontalPaddingPx(leftPx: Int, rightPx: Int) {
        val l = leftPx.coerceAtLeast(0)
        val r = rightPx.coerceAtLeast(0)
        if (l == leftPaddingPx && r == rightPaddingPx) return
        leftPaddingPx = l
        rightPaddingPx = r
        setPadding(0, 0, r, 0)
        if (childCount > 0) {
            removeViewAt(0)
            addView(createDivider(), 0)
            applyIconLayout()
            forceRebuild = false
            requestLayout()
        }
    }

    private val dividerPaint = Paint().apply {
        strokeWidth = (2 * density)
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(
            (40 * 255) / 100,
            Color.red(dividerColor),
            Color.green(dividerColor),
            Color.blue(dividerColor),
        )
    }

    private var currentApps: List<RecentAppInfo> = emptyList()
    private var previousAppPackages: Set<String> = emptySet()
    private val iconViews = HashMap<String, View>()
    private var forceRebuild = false
    private var onAppClickListener: ((RecentAppInfo) -> Unit)? = null
    private var onVisibilityChangedListener: Runnable? = null

    /**
     * Width budget (px) for this strip, set by the parent Hotseat before measuring.
     * During onMeasure, icon containers that would exceed this budget are hidden
     * (set GONE) so the strip never overflows into the pinned-icons row.
     */
    var maxAvailableWidth: Int = Int.MAX_VALUE

    fun setOnVisibilityChangedListener(listener: Runnable?) {
        onVisibilityChangedListener = listener
    }

    fun applyCachedApps() {
        updateApps(manager.recentApps.value)
    }

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        // No extra horizontal padding — divider sits flush against icons
        setPadding(0, 0, 0, 0)
        visibility = INVISIBLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Trim icon containers to the width budget set by the parent Hotseat,
        // so the strip never overflows into the pinned-icons row when the dock
        // is tight on space. All children start visible; ones that don't fit
        // are hidden. The leading divider (index 0) is kept only if at least
        // one icon remains visible.
        if (maxAvailableWidth != Int.MAX_VALUE && childCount > 0) {
            // Reset visibility: children hidden by a previous tighter budget
            // must become visible again if the budget has since grown.
            for (i in 0 until childCount) {
                getChildAt(i).visibility = VISIBLE
            }
            var used = paddingLeft + paddingRight + leadingSlotPx
            var visibleIcons = 0
            for (i in 1 until childCount) {
                val child = getChildAt(i)
                val cw = iconSize + if (visibleIcons > 0) appSpacing else 0
                if (used + cw <= maxAvailableWidth) {
                    used += cw
                    visibleIcons++
                } else {
                    child.visibility = GONE
                }
            }
            // If no icons fit, also hide the leading divider.
            if (visibleIcons == 0 && childCount > 0) {
                getChildAt(0).visibility = GONE
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (measuredWidth == 0 && childCount > 0) {
            // Parent gave us 0 width (likely UNSPECIFIED/AT_MOST edge case).
            // Measure ourselves based on children.
            var totalWidth = paddingLeft + paddingRight
            var visibleIcons = 0
            for (i in 1 until childCount) {
                if (getChildAt(i).visibility == GONE) continue
                totalWidth += iconSize + if (visibleIcons > 0) appSpacing else 0
                visibleIcons++
            }
            if (visibleIcons > 0) totalWidth += leadingSlotPx
            setMeasuredDimension(totalWidth, iconSize)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = findViewTreeLifecycleOwner()
        if (owner != null) {
            owner.lifecycleScope.launch {
                manager.recentApps.collectLatest { apps ->
                    updateApps(apps)
                }
            }
        } else {
            Log.w(TAG, "No lifecycle owner found; collecting via GlobalScope")
            kotlinx.coroutines.GlobalScope.launch {
                manager.recentApps.collectLatest { apps ->
                    post { updateApps(apps) }
                }
            }
        }
    }

    fun setOnAppClickListener(listener: (RecentAppInfo) -> Unit) {
        onAppClickListener = listener
    }

    private fun updateApps(apps: List<RecentAppInfo>) {
        if (apps.isEmpty()) {
            visibility = INVISIBLE
            currentApps = emptyList()
            previousAppPackages = emptySet()
            iconViews.clear()
            onVisibilityChangedListener?.run()
            return
        }

        // Rebuild if the package list or its ORDER changed: relaunching an app
        // moves it to the front even when the set and size are identical.
        val newPackages = apps.map { it.packageName }
        val changed = newPackages != currentApps.map { it.packageName }
        if (!changed && !forceRebuild && childCount > 0) return
        // Settings-driven rebuilds (icon size, divider) must recreate containers.
        if (forceRebuild) {
            iconViews.clear()
        }
        forceRebuild = false

        visibility = VISIBLE
        onVisibilityChangedListener?.run()
        val newPackageSet = newPackages.toSet()
        val inserted = newPackageSet - previousAppPackages
        val removed = previousAppPackages - newPackageSet
        removed.forEach { iconViews.remove(it) }

        // Rebuild the child order while REUSING already-loaded icon containers,
        // so returning to the desktop reorders in place instead of flashing an
        // empty strip while icons reload asynchronously.
        removeAllViews()
        addView(createDivider())
        apps.forEach { app ->
            addView(iconViews.getOrPut(app.packageName) {
                createIconView(app, app.packageName in inserted)
            })
        }
        applyIconLayout()

        if (removed.isNotEmpty() && childCount > 0) {
            animateInsertion()
        }

        previousAppPackages = newPackageSet
        currentApps = apps
        requestLayout()
    }

    private fun createDivider(): View {
        // The leading slot is as wide as the divider and/or the left padding, so the first app
        // sits exactly [leftPaddingPx] away from the strip's left edge.
        val leadingWidth = maxOf(if (dividerEnabled) dividerWidth else 0, leftPaddingPx)
        leadingSlotPx = leadingWidth
        val h = if (dividerEnabled) currentDividerHeightPx() else iconSize
        return object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (!dividerEnabled) return
                val cx = minOf(dividerWidth, width) / 2f
                val cy = height / 2f
                val half = height / 2f
                canvas.drawLine(cx, cy - half, cx, cy + half, dividerPaint)
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(leadingWidth, h)
            }
        }.apply {
            layoutParams = LayoutParams(leadingWidth, h)
            visibility = if (leadingWidth > 0) VISIBLE else GONE
            dividerView = this
        }
    }

    private fun createIconView(app: RecentAppInfo, animateIn: Boolean): View {
        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(iconSize, LayoutParams.WRAP_CONTENT)
        }

        val iconView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = android.view.Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        container.addView(iconView)

        container.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            launchApp(app.packageName, iconView)
            onAppClickListener?.invoke(app)
        }

        loadIcon(app.packageName, iconView)

        if (animateIn) {
            container.alpha = 0f
            container.scaleX = 0.8f
            container.scaleY = 0.8f
            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        return container
    }

    private fun loadIcon(packageName: String, imageView: ImageView) {
        Executors.MODEL_EXECUTOR.execute {
            try {
                val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
                val lai = launcherApps
                    .getActivityList(packageName, Process.myUserHandle())
                    .firstOrNull()
                if (lai == null) {
                    Log.w(TAG, "No launch activity for $packageName")
                    return@execute
                }
                val appInfo = AppInfo(context, lai, Process.myUserHandle())
                val iconCache = com.android.launcher3.LauncherAppState
                    .getInstance(context).iconCache
                iconCache.getTitleAndIcon(
                    appInfo, lai,
                    com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG,
                )
                val drawable = appInfo.newIcon(context)
                imageView.post {
                    imageView.setImageDrawable(drawable)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load icon for $packageName", e)
            }
        }
    }

    private fun launchApp(packageName: String, sourceView: View) {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val launcher = LauncherXApp.launcher
                if (launcher != null) {
                    launcher.startActivitySafely(sourceView, intent, null)
                } else {
                    context.startActivity(intent)
                }
                // Refresh immediately after launch so the list updates without waiting
                manager.refreshNow()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch $packageName", e)
        }
    }

    private fun animateInsertion() {
        if (childCount == 0) return
        val lastChild = getChildAt(childCount - 1)
        lastChild.alpha = 0f
        lastChild.translationX = 20 * density
        lastChild.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    fun getExcludePackages(): Set<String> {
        return emptySet()
    }

    companion object {
        private const val TAG = "RecentAppsView"
        private const val ICON_SIZE_DP = 48
        private const val APP_SPACING_DP = 8
        private const val DIVIDER_WIDTH_DP = 12
        private const val DIVIDER_HEIGHT_DP = 28
    }
}
