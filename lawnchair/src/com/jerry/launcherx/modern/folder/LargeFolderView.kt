package com.jerry.launcherx.modern.folder

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.jerry.launcherx.preferences2.firstCached
import com.android.launcher3.BubbleTextView
import com.android.launcher3.EditModeSelectionBadge
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.WorkspaceItemInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.abs

class LargeFolderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "LargeFolder"
        private const val DEFAULT_BG_PADDING_DP = 4f
        private const val DEFAULT_BG_INSET_DP = 0f
        private const val DEFAULT_ICON_GAP_DP = 4f
        private const val DEFAULT_ICON_SCALE = 0.9f

        /** Horizontal starts at the edge; vertical packs to the top. */
        val DEFAULT_ALIGN_H = Align.START.prefValue
        val DEFAULT_ALIGN_V = Align.START.prefValue

        /** How long the liquid specular keeps flowing after a card is built. */
        private const val SPECULAR_DURATION_MS = 2400L
        private const val FROSTED_BORDER_COLOR = 0x1AFFFFFF
    }

    /** Axis alignment, mapped 1:1 to the CSS flex keywords. */
    enum class Align(val prefValue: String) {
        START("flex-start"),
        END("flex-end"),
        CENTER("center"),
        SPACE_BETWEEN("space-between"),
        SPACE_AROUND("space-around");

        companion object {
            fun fromPref(value: String?): Align =
                entries.firstOrNull { it.prefValue == value } ?: START
        }
    }

    data class FolderItem(
        val id: Long,
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val category: AppCategory = AppCategory.OTHER,
        val isApp: Boolean = true,
        val itemInfo: ItemInfoWithIcon? = null,
    )

    enum class AppCategory(val displayName: String, val color: Int) {
        SOCIAL("social", Color.parseColor("#4CAF50")),
        PRODUCTIVITY("productivity", Color.parseColor("#2196F3")),
        ENTERTAINMENT("entertainment", Color.parseColor("#FF9800")),
        TOOLS("tools", Color.parseColor("#9C27B0")),
        FINANCE("finance", Color.parseColor("#F44336")),
        OTHER("other", Color.parseColor("#607D8B"))
    }

    private var gridCols = 3
    private var gridRows = 3
    private var spanX = 1
    private var spanY = 1
    private val landscape: Boolean
        get() = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
    private var alignH = Align.START
    private var alignV = Align.START
    private var bgPaddingPx =
        context.resources.displayMetrics.density * DEFAULT_BG_PADDING_DP
    private var bgInsetPx =
        context.resources.displayMetrics.density * DEFAULT_BG_INSET_DP
    private var iconGapPx = context.resources.displayMetrics.density * DEFAULT_ICON_GAP_DP

    /** Hover scale for the overflow deck cell, matching FastBitmapDrawable.HOVERED_SCALE. */
    private val overflowHoverScale = 1.1f
    private val overflowHoverDurationMs = 150L
    /** Each stacked icon fills this fraction of the overflow cell (2 icons / 3+ icons). */
    private val deckIconFraction2 = 0.90f
    private val deckIconFraction3 = 0.80f
    /** "+N" badge diameter as a fraction of the overflow cell. */
    private val deckBadgeFraction = 0.30f
    private val deckBadgeTextFraction = 0.15f
    private fun deckIconFraction(layers: Int): Float =
        if (layers <= 2) deckIconFraction2 else deckIconFraction3
    private var inlineMode = false
    private var bgAlpha = 1f
    private var bgColor = Color.WHITE
    private var frostedGlass = false
    private var liquidGlass = false
    /** Wallpaper blur radius (screen dp) for the glass backdrop; tunable in settings. */
    private var glassBlurDp = GlassBackdrop.FROSTED_RADIUS_DP
    /** Edge-refraction strength for liquid mode (0 disables it). */
    private var glassRefraction = GlassBackdrop.REFRACTION_STRENGTH
    /** Chromatic dispersion amount for liquid mode (0 disables the fringe). */
    private var glassDispersion = 1f
    /** When true the liquid specular keeps flowing instead of running once. */
    private var glassSpecularFlow = false
    private var specularStartMs = 0L
    /** Extra bottom inset (px) reserved inside the card for the folder title band. */
    var bottomContentInset = 0f
        set(value) {
            if (value != field) {
                field = value
                requestLayout()
            }
        }
    private var iconSizeFactor = DEFAULT_ICON_SCALE
    private val reappliedState = java.util.WeakHashMap<BubbleTextView, String>()
    var showItemLabels = true
    private val items = mutableListOf<FolderItem>()
    private val itemViews = mutableListOf<View>()
    private var draggedIndex = -1
    private var dragOverIndex = -1
    private var isEditing = false

    // Swiper paging (see SwiperMode): only visible items live in the view tree, and the
    // page index selects which slice of `items` they represent.
    private var swiperMode = SwiperMode.OFF
    private var pageIndex = 0
    private var pageCount = 1
    private var pageStart = 0
    private var pageAnimator: ValueAnimator? = null
    private var swipeTracking = false
    private var swipeTriggered = false
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private val swipeSlop by lazy {
        ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }
    private val pageIndicatorBandPx by lazy { 10f * resources.displayMetrics.density }
    private val pageIndicatorRadiusPx by lazy { 2.5f * resources.displayMetrics.density }
    private val pageIndicatorGapPx by lazy { 7f * resources.displayMetrics.density }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FFFFFF")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private var dropHighlight = false
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A73E8")
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val cornerRadius by lazy { resources.displayMetrics.density * 16f }
    /** Corner radius used when the glass surface is drawn — the iOS continuous-curve look. */
    private val glassCornerRadius by lazy { resources.displayMetrics.density * 20f }
    /** Width of the glass edge highlight; the flat non-glass border keeps its 1px look. */
    private val oneDpPx by lazy { resources.displayMetrics.density }
    private val bgRect = RectF()
    /** Folder this card renders while used as an inline (workspace) large folder. */
    private var folderInfo: FolderInfo? = null
    private val badgeBounds = RectF()
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glassW = 0
    private var glassH = 0
    private var glassShadersReady = false
    private var glassShaderDark = false
    private var frostedShader: Shader? = null
    private var liquidShader: Shader? = null
    private var liquidShader2: Shader? = null
    private var edgeTopShader: Shader? = null
    private var edgeBottomShader: Shader? = null
    private var borderHighlightShader: Shader? = null
    private var specularShader: Shader? = null
    private var specularShader2: Shader? = null

    /** Blurred wallpaper slice behind the card; null until GlassBackdrop has built one. */
    private var glassBackdrop: Bitmap? = null
    private var glassGeneration = GlassBackdrop.generation()
    private var glassPrefsJob: Job? = null
    private val glassBackdropListener = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            glassBackdrop = loadGlassBackdrop()
            if (glassBackdrop != null) {
                invalidate()
            } else {
                GlassBackdrop.addListener(this)
            }
        }
    }
    private val screenLocation = IntArray(2)
    private var lastScreenX = Int.MIN_VALUE
    private var lastScreenY = Int.MIN_VALUE
    private var preDrawObserver: ViewTreeObserver? = null
    private val positionListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (!(liquidGlass || frostedGlass)) return true
            getLocationOnScreen(screenLocation)
            if (screenLocation[0] != lastScreenX || screenLocation[1] != lastScreenY) {
                lastScreenX = screenLocation[0]
                lastScreenY = screenLocation[1]
                invalidate()
            }
            return true
        }
    }
    /** 0..1 travel of the liquid specular streak, animated only for a short window. */
    private var specularPhase = 0f
    private var specularAnimator: android.animation.ValueAnimator? = null
    private var glassIsDark = false
    private var glassNoise: Shader? = null

    var onItemClicked: ((FolderItem, Int) -> Unit)? = null
    var onItemLongClicked: ((View, FolderItem, Int) -> Boolean)? = null
    var onItemReordered: ((Int, Int) -> Unit)? = null
    var onEditStateChanged: ((Boolean) -> Unit)? = null
    var onOverflowClicked: (() -> Unit)? = null
    var onOverflowLongClicked: (() -> Boolean)? = null
    var onEmptyAreaLongPress: (() -> Unit)? = null
    var onEmptyAreaDragStart: (() -> Unit)? = null

    private var emptyPressActive = false
    private var emptyLongPressed = false
    private var emptyDragStarted = false
    private var emptyDownX = 0f
    private var emptyDownY = 0f
    private val emptyTouchSlop by lazy {
        android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }
    private val emptyLongPressTask = Runnable {
        if (emptyPressActive) {
            emptyPressActive = false
            emptyLongPressed = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onEmptyAreaLongPress?.invoke()
        }
    }

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onLongPress(e: MotionEvent) {
                val idx = hitTest(e.x, e.y)
                if (isVisibleItemIndex(idx)) {
                    performHapticFeedback()
                    resetSwipeTracking()
                    isEditing = true
                    onEditStateChanged?.invoke(true)
                    onItemLongClicked?.invoke(
                        getChildForItemIndex(idx) ?: getChildAt(idx - pageStart),
                        items[idx],
                        idx,
                    )
                }
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val idx = hitTest(e.x, e.y)
                if (isVisibleItemIndex(idx)) {
                    onItemClicked?.invoke(items[idx], idx)
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val idx = hitTest(e.x, e.y)
                if (isVisibleItemIndex(idx)) {
                    onItemClicked?.invoke(items[idx], idx)
                    return true
                }
                return false
            }
        })

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    fun setInlineMode(inline: Boolean) {
        inlineMode = inline
        if (inline) {
            isEditing = false
        }
    }

    /** Selects +N (OFF) or horizontal/vertical paging for this card. */
    fun setSwiperMode(mode: SwiperMode) {
        if (swiperMode == mode) return
        swiperMode = mode
        pageIndex = 0
        rebuildViews()
        requestLayout()
        invalidate()
    }

    fun getSwiperMode(): SwiperMode = swiperMode

    /**
     * Derives the internal grid from the folder's workspace span and the screen
     * orientation (long-side `2n` rule). The span and the grid are independent
     * concepts: the grid never affects the folder's footprint on the workspace, it is
     * recomputed (never persisted) on every span change and on rotation.
     */
    fun setSpan(newSpanX: Int, newSpanY: Int) {
        spanX = newSpanX
        spanY = newSpanY
        rederiveGrid(log = true)
    }

    /** Re-derives the grid from the current span/orientation; never touches the items. */
    private fun rederiveGrid(log: Boolean) {
        val size = LargeFolderGrid.gridForSpan(spanX, spanY, landscape)
        if (size.columns != gridCols || size.rows != gridRows) {
            gridCols = size.columns
            gridRows = size.rows
            if (log) {
                Log.d(TAG, "setSpan ${spanX}x$spanY -> grid ${gridCols}x$gridRows")
            }
            rebuildViews()
            requestLayout()
        }
    }

    private fun applyPrefs(
        liveLiquidEnabled: Boolean? = null,
        liveRefraction: Float? = null,
        liveDispersion: Float? = null,
        liveSpecularFlow: Boolean? = null,
    ) {
        setBgAlpha(prefs.getFloat(LargeFolderBinder.KEY_BG_OPACITY, 0.9f))
        setIconScale(prefs.getFloat(LargeFolderBinder.KEY_ICON_SCALE, DEFAULT_ICON_SCALE))
        val density = context.resources.displayMetrics.density
        val newPadding =
            prefs.getFloat(LargeFolderBinder.KEY_BG_PADDING, DEFAULT_BG_PADDING_DP) * density
        val newInset =
            prefs.getFloat(LargeFolderBinder.KEY_BG_INSET, DEFAULT_BG_INSET_DP) * density
        val newGap =
            prefs.getFloat(LargeFolderBinder.KEY_ICON_GAP, DEFAULT_ICON_GAP_DP) * density
        if (newPadding != bgPaddingPx || newInset != bgInsetPx || newGap != iconGapPx) {
            bgPaddingPx = newPadding
            bgInsetPx = newInset
            iconGapPx = newGap
            // Cell size is derived from these in onMeasure; the next measure pass
            // re-fits every child to the new cell.
            requestLayout()
            invalidate()
        }
        val newColor = prefs.getInt(
            LargeFolderBinder.KEY_BG_COLOR, Color.WHITE
        ) and 0xFFFFFF // keep RGB only; alpha is applied separately via bgAlpha
        if (newColor != bgColor) {
            bgColor = newColor
            invalidate()
        }
        val pm2 = PreferenceManager2.getInstance(context)
        if (pm2.liquidGlassEnabled.firstCached()) {
            // Warm the lens once the desktop is bound so the first popup-folder open does not
            // have to wait for the wallpaper blur to finish.
            GlassBackdrop.getLens(context)
        }
        val newFrosted = prefs.getBoolean(LargeFolderBinder.KEY_FROSTED_GLASS, false)
        if (newFrosted != frostedGlass) {
            frostedGlass = newFrosted
            invalidate()
        }
        val newLiquid = (liveLiquidEnabled ?: pm2.liquidGlassEnabled.firstCached()) &&
            prefs.getBoolean(LargeFolderBinder.KEY_LIQUID_GLASS, false)
        if (newLiquid != liquidGlass) {
            liquidGlass = newLiquid
            glassBackdrop = null
            invalidate()
        }
        glassRefraction = liveRefraction ?: pm2.liquidGlassRefraction.firstCached()
        glassDispersion = liveDispersion ?: pm2.liquidGlassDispersion.firstCached()
        glassSpecularFlow = liveSpecularFlow ?: pm2.liquidGlassSpecularFlow.firstCached()
        val newBlur = prefs.getFloat(LargeFolderBinder.KEY_GLASS_BLUR, GlassBackdrop.FROSTED_RADIUS_DP)
        if (newBlur != glassBlurDp) {
            glassBlurDp = newBlur
            glassBackdrop = null
            invalidate()
        }
        if (newFrosted || newLiquid) {
            ensureGlassReady()
            if (glassSpecularFlow) {
                if (specularStartMs == 0L) specularStartMs = android.os.SystemClock.uptimeMillis()
            } else {
                startSpecularFlow()
            }
        }
        val newAlignH = Align.fromPref(
            prefs.getString(LargeFolderBinder.KEY_ALIGN_H, DEFAULT_ALIGN_H)
        )
        val newAlignV = Align.fromPref(
            prefs.getString(LargeFolderBinder.KEY_ALIGN_V, DEFAULT_ALIGN_V)
        )
        if (newAlignH != alignH || newAlignV != alignV) {
            alignH = newAlignH
            alignV = newAlignV
            requestLayout()
        }
        folderInfo?.let { info ->
            val mode = LargeFolderBinder.getSwiperMode(context, info.id)
            if (mode != swiperMode) setSwiperMode(mode)
        }
    }

    private fun setBgAlpha(alpha: Float) {
        val a = alpha.coerceIn(0f, 1f)
        if (a != bgAlpha) {
            bgAlpha = a
            invalidate()
        }
    }

    private fun setIconScale(scale: Float) {
        val s = scale.coerceIn(0.4f, 2f)
        if (s != iconSizeFactor) {
            iconSizeFactor = s
            itemViews.forEach {
                if (it is FolderItemView) {
                    it.setIconSizeFactor(s)
                } else {
                    // Inline BubbleTextView items re-apply the size in onMeasure.
                    it.requestLayout()
                }
            }
            invalidate()
        }
    }

    private val prefs: android.content.SharedPreferences
        get() = context.getSharedPreferences(
            LargeFolderBinder.PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )

    private val prefsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyPrefs() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preDrawObserver = viewTreeObserver
        preDrawObserver?.addOnPreDrawListener(positionListener)
        lastScreenX = Int.MIN_VALUE
        lastScreenY = Int.MIN_VALUE
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        applyPrefs()
        if (frostedGlass || liquidGlass) {
            ensureGlassReady()
        }
        startSpecularFlow()
        glassPrefsJob?.cancel()
        val owner = findViewTreeLifecycleOwner()
        if (owner != null) {
            val pm2 = PreferenceManager2.getInstance(context)
            glassPrefsJob = owner.lifecycleScope.launch {
                combine(
                    pm2.liquidGlassEnabled.get(),
                    pm2.liquidGlassRefraction.get(),
                    pm2.liquidGlassDispersion.get(),
                    pm2.liquidGlassSpecularFlow.get(),
                ) { enabled, refraction, dispersion, flow ->
                    applyPrefs(enabled, refraction, dispersion, flow)
                }.collect { }
            }
        }
    }

    override fun onDetachedFromWindow() {
        glassPrefsJob?.cancel()
        glassPrefsJob = null
        preDrawObserver?.let { observer ->
            if (observer.isAlive) observer.removeOnPreDrawListener(positionListener)
        }
        preDrawObserver = null
        lastScreenX = Int.MIN_VALUE
        lastScreenY = Int.MIN_VALUE
        GlassBackdrop.removeListener(glassBackdropListener)
        removeCallbacks(emptyLongPressTask)
        emptyPressActive = false
        emptyLongPressed = false
        emptyDragStarted = false
        specularAnimator?.cancel()
        specularAnimator = null
        specularStartMs = 0L
        cancelPageAnimation()
        resetSwipeTracking()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDetachedFromWindow()
    }

    fun setItems(newItems: List<FolderItem>) {
        items.clear()
        items.addAll(newItems)
        rebuildViews()
        requestLayout()
        invalidate()
    }

    fun getItems(): List<FolderItem> = items.toList()

    /** Finds a grid child whose tag [ItemInfo] matches, for app-close animations. */
    fun findChildByMatcher(matcher: (ItemInfo) -> Boolean): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val tag = child.tag
            if (tag is ItemInfo && matcher(tag)) return child
        }
        return null
    }

    fun addItem(item: FolderItem, index: Int = items.size) {
        items.add(index.coerceIn(0, items.size), item)
        rebuildViews()
        requestLayout()
        invalidate()
    }

    fun removeItem(index: Int) {
        if (index in items.indices) {
            items.removeAt(index)
            rebuildViews()
            requestLayout()
            invalidate()
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from in items.indices && to in items.indices && from != to) {
            val item = items.removeAt(from)
            items.add(to, item)
            onItemReordered?.invoke(from, to)
            rebuildViews()
            requestLayout()
            invalidate()
        }
    }

    fun renameItem(index: Int, newName: String) {
        if (index in items.indices) {
            items[index] = items[index].copy(label = newName)
            updateView(index)
        }
    }

    fun exitEditing() {
        isEditing = false
        onEditStateChanged?.invoke(false)
        invalidate()
    }

    fun sortByCategory() {
        val sorted = items.sortedBy { it.category.ordinal }
        items.clear()
        items.addAll(sorted)
        rebuildViews()
        requestLayout()
        invalidate()
    }

    fun sortByFrequency(frequencyMap: Map<String, Int>) {
        val sorted = items.sortedByDescending { frequencyMap[it.packageName] ?: 0 }
        items.clear()
        items.addAll(sorted)
        rebuildViews()
        requestLayout()
        invalidate()
    }

    private fun rebuildViews() {
        removeAllViews()
        itemViews.clear()
        cancelPageAnimation()

        if (swiperMode.isSwiper) {
            val capacity = (gridCols * gridRows).coerceAtLeast(1)
            pageCount = if (items.isEmpty()) 1 else (items.size + capacity - 1) / capacity
            pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            pageStart = pageIndex * capacity
            val end = min(pageStart + capacity, items.size)
            for (index in pageStart until end) {
                val item = items[index]
                val view = if (inlineMode) {
                    createInlineItemView(item, index)
                } else {
                    createFolderItemView(item, index)
                }
                itemViews.add(view)
                addView(view)
            }
        } else {
            pageCount = 1
            pageIndex = 0
            pageStart = 0
            val plan = LargeFolderGrid.planOverflow(items.size, gridCols * gridRows)
            items.take(plan.normalCount).forEachIndexed { index, item ->
                val view = if (inlineMode) {
                    createInlineItemView(item, index)
                } else {
                    createFolderItemView(item, index)
                }
                itemViews.add(view)
                addView(view)
            }
            if (plan.hasOverflow) {
                addView(createOverflowView(plan))
            }
            Log.d(
                TAG,
                "rebuildViews: items=${items.size} normal=${plan.normalCount} " +
                    "stack=${plan.stackItems} plus=${plan.showPlus} hidden=${plan.hiddenCount} " +
                    "grid=${gridCols}x$gridRows inline=$inlineMode " +
                    "nullIcons=${items.take(plan.normalCount).count { it.icon == null }}"
            )
        }
        if (swiperMode.isSwiper) {
            Log.d(
                TAG,
                "rebuildViews(swiper=${swiperMode}): items=${items.size} page=${pageIndex + 1}/" +
                    "$pageCount start=$pageStart grid=${gridCols}x$gridRows inline=$inlineMode"
            )
        }
    }

    private fun createFolderItemView(item: FolderItem, index: Int): View {
        return FolderItemView(context).apply {
            setItem(item)
            setShowLabel(showItemLabels)
            setIconSizeFactor(iconSizeFactor)
            setOnClickListener { onItemClicked?.invoke(item, index) }
            setOnLongClickListener {
                performHapticFeedback()
                onItemLongClicked?.invoke(this, item, index) ?: false
            }
        }
    }

    private fun createInlineItemView(
        item: FolderItem,
        index: Int,
        clickable: Boolean = true,
    ): BubbleTextView {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.folder_application, this, false) as BubbleTextView
        when (val info = item.itemInfo) {
            is WorkspaceItemInfo -> view.applyFromWorkspaceItem(info)
            is ItemInfoWithIcon -> view.applyFromItemInfoWithIcon(info)
            else -> view.text = item.label
        }
        view.tag = item.itemInfo ?: item
        view.verifyHighRes()
        view.setTextVisibility(showItemLabels)
        if (!showItemLabels) {
            // setTextVisibility only zeroes the alpha and can be reset by later color
            // updates; clearing the text guarantees labels never show up inline.
            view.text = ""
        }
        // The XML sets centerVertically, which derives its top padding from the full-size
        // workspace icon; inside a small grid cell that produces negative padding and a
        // clipped icon. Our own layout centers the cell, so disable it.
        view.setCenterVertically(false)
        // Icon size is applied in onMeasure by resizing the icon drawable bounds (scaled,
        // clamped to the cell) — scaling the view itself would overflow and get clipped.
        if (clickable) {
            view.setOnClickListener { onItemClicked?.invoke(item, index) }
            view.setOnLongClickListener {
                performHapticFeedback()
                onItemLongClicked?.invoke(it, item, index) ?: false
            }
        } else {
            // Overflow mini-grid icons: the whole cell owns the gestures (tap opens the
            // folder), so the quadrant icons must never consume the touch themselves.
            view.isClickable = false
            view.isLongClickable = false
        }
        return view
    }

    private fun createOverflowView(plan: LargeFolderGrid.OverflowPlan): View {
        val start = plan.normalCount
        val end = min(start + plan.stackItems, items.size)
        return OverflowGridView(items.subList(start, end), plan.showPlus, plan.hiddenCount)
    }

    private fun updateView(index: Int) {
        val local = index - pageStart
        if (local in itemViews.indices && index in items.indices) {
            (itemViews[local] as? FolderItemView)?.setItem(items[index])
        }
    }

    /** The child rendering [index] in `items`, or null when it is on another swiper page. */
    fun getChildForItemIndex(index: Int): View? {
        val local = index - pageStart
        return if (local in 0 until childCount) getChildAt(local) else null
    }

    private fun switchPage(delta: Int) {
        if (!swiperMode.isSwiper || pageCount <= 1 || delta == 0) return
        pageIndex = ((pageIndex + delta) % pageCount + pageCount) % pageCount
        rebuildViews()
        requestLayout()
        animatePageIn(delta)
        invalidate()
    }

    /** Slides the freshly built page in from the side the user swiped away from. */
    private fun animatePageIn(delta: Int) {
        pageAnimator?.cancel()
        val horizontal = swiperMode == SwiperMode.HORIZONTAL
        val distance = if (horizontal) width.toFloat() else height.toFloat()
        if (distance <= 0f || childCount == 0) return
        val sign = if (delta > 0) 1f else -1f
        applyPageOffset(horizontal, sign * distance)
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                applyPageOffset(horizontal, sign * distance * (anim.animatedValue as Float))
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    applyPageOffset(horizontal, 0f)
                    if (pageAnimator === animation) pageAnimator = null
                }
            })
        }
        pageAnimator = animator
        animator.start()
    }

    private fun applyPageOffset(horizontal: Boolean, offset: Float) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (horizontal) {
                child.translationX = offset
            } else {
                child.translationY = offset
            }
        }
    }

    private fun cancelPageAnimation() {
        pageAnimator?.cancel()
        pageAnimator = null
        applyPageOffset(true, 0f)
        applyPageOffset(false, 0f)
    }

    private fun resetSwipeTracking() {
        swipeTracking = false
        swipeTriggered = false
    }

    private fun startSwipeTracking(event: MotionEvent) {
        swipeTracking = swiperMode.isSwiper && !isPagingBlocked() && pageCount > 1
        swipeTriggered = false
        swipeDownX = event.x
        swipeDownY = event.y
    }

    /** @return true when the gesture belongs to the pager and must be consumed. */
    private fun trackSwipe(event: MotionEvent): Boolean {
        if (!swipeTracking || isPagingBlocked()) return false
        val dx = event.x - swipeDownX
        val dy = event.y - swipeDownY
        val horizontal = swiperMode == SwiperMode.HORIZONTAL
        val main = if (horizontal) abs(dx) else abs(dy)
        val cross = if (horizontal) abs(dy) else abs(dx)
        if (swipeTriggered) return true
        if (main > swipeSlop && main > cross) {
            swipeTriggered = true
            removeCallbacks(emptyLongPressTask)
            emptyPressActive = false
            return true
        }
        if (cross > swipeSlop && cross > main) {
            // Perpendicular gesture: release the card so the workspace/child can use it.
            swipeTracking = false
            if (inlineMode) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return false
    }

    private fun finishSwipe(event: MotionEvent) {
        if (!swipeTriggered) return
        swipeTriggered = false
        val dx = event.x - swipeDownX
        val dy = event.y - swipeDownY
        val threshold = 44f * resources.displayMetrics.density
        val delta = when (swiperMode) {
            SwiperMode.HORIZONTAL -> if (abs(dx) >= threshold) {
                if (dx < 0f) 1 else -1
            } else {
                0
            }
            SwiperMode.VERTICAL -> if (abs(dy) >= threshold) {
                if (dy < 0f) 1 else -1
            } else {
                0
            }
            SwiperMode.OFF -> 0
        }
        if (delta != 0) switchPage(delta)
    }

    /**
     * Independent cell dimensions for the grid, fitted to the actual available pixels.
     * Horizontal and vertical are computed separately so a 1-row grid fills the full
     * width (and a 1-column grid fills the full height), matching the CSS
     * `text-align: justify` look where edge spacing equals the inter-icon gap.
     * The icon itself is still drawn square (contain-scaled) inside the cell.
     */
    private fun cellSize(w: Int, h: Int): Pair<Float, Float> {
        val edge = bgInsetPx + bgPaddingPx
        val pagerBand = if (swiperMode.isSwiper && pageCount > 1) pageIndicatorBandPx else 0f
        val contentW = w - 2f * edge - if (swiperMode == SwiperMode.VERTICAL) pagerBand else 0f
        val contentH = h - 2f * edge - bottomContentInset -
            if (swiperMode == SwiperMode.HORIZONTAL) pagerBand else 0f
        val cellW = (contentW - (gridCols - 1) * iconGapPx) / gridCols
        val cellH = (contentH - (gridRows - 1) * iconGapPx) / gridRows
        return cellW.coerceAtLeast(1f) to cellH.coerceAtLeast(1f)
    }

    /**
     * Free space distribution for one flex axis, following the CSS formulas.
     * [count] items/lines of [itemSize] with [gap] inside [containerSize]; the returned
     * value is the extra offset for index [i] on top of the natural packed position.
     * Physical left/top = start regardless of RTL, per spec.
     */
    private fun flexExtra(i: Int, count: Int, free: Float, align: Align): Float {
        if (free <= 0f || count <= 0) return 0f
        return when (align) {
            // Pack to the start edge: no extra offset.
            Align.START -> 0f
            // Pack to the end edge: shift every item by the full free space.
            Align.END -> free
            Align.CENTER -> free / 2f
            // Evenly distribute with first at start, last at end.
            Align.SPACE_BETWEEN ->
                if (count > 1) i * free / (count - 1) else 0f
            // Evenly distribute with equal space around each item.
            Align.SPACE_AROUND -> {
                val share = free / count
                i * share + share / 2f
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val exactly = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY &&
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
        val mw: Int
        val mh: Int
        if (exactly) {
            mw = w
            mh = h
        } else {
            val size = min(w, h)
            mw = size
            mh = size
        }
        setMeasuredDimension(mw, mh)
        // The parent also runs a placeholder measure pass (layout params height 0);
        // only the real EXACTLY pass must touch the children, otherwise their icon
        // bounds get flipped by a stale size in between the real measure and layout.
        if (mw <= 0 || mh <= 0) return

        val (cellWf, cellHf) = cellSize(mw, mh)
        val cellWi = cellWf.toInt()
        val cellHi = cellHf.toInt()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(cellWi, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellHi, MeasureSpec.EXACTLY)
            )
            if (child is BubbleTextView) {
                // The icon drawable is sized for the workspace cell (much larger than an
                // inline grid cell) and would be clipped by the view bounds. Re-fit it to
                // the cell (contain-scaled by the user's icon-size preference, never
                // exceeding the cell) and inset the view padding so the TextView lays the
                // icon out centered both horizontally and vertically. Scaling the view
                // itself would overflow the cell and get clipped on the sides.
                fitInlineIconToCell(child, cellWi, cellHi, reapplyCompound = false)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // An async icon load calls BubbleTextView.setIcon, which resets the compound
        // drawable bounds to the full workspace icon size while suppressing requestLayout
        // (mDisableRelayout). The cell is smaller than that, so without this repair pass
        // the icon renders clipped at the cell edge until the next real layout pass.
        refitInlineIcons()
        super.dispatchDraw(canvas)
        drawPageIndicator(canvas)
        drawFolderSelectionBadge(canvas)
    }

    /** Small dots showing the current swiper page; horizontal at the bottom, vertical on the right. */
    private fun drawPageIndicator(canvas: Canvas) {
        if (!swiperMode.isSwiper || pageCount <= 1 || bgRect.isEmpty) return
        val radius = pageIndicatorRadiusPx
        val gap = pageIndicatorGapPx
        val total = (pageCount - 1) * gap
        val dark = Utilities.isDarkTheme(context)
        val active = if (dark) Color.WHITE else Color.BLACK
        val edge = bgInsetPx + bgPaddingPx
        bgPaint.shader = null
        for (i in 0 until pageCount) {
            val offset = i * gap - total / 2f
            val cx: Float
            val cy: Float
            if (swiperMode == SwiperMode.HORIZONTAL) {
                cx = bgRect.centerX() + offset
                cy = height - edge - pageIndicatorBandPx / 2f
            } else {
                cx = width - edge - pageIndicatorBandPx / 2f
                cy = bgRect.centerY() + offset
            }
            bgPaint.alpha = if (i == pageIndex) 190 else 70
            bgPaint.color = active
            canvas.drawCircle(cx, cy, radius, bgPaint)
        }
    }

    /** Folder whose card this view renders, used to draw the edit-mode selection check. */
    fun setFolderInfo(info: FolderInfo?) {
        folderInfo = info
    }

    private fun findActivityContext(): ActivityContext? {
        var ctx: android.content.Context? = context
        while (ctx != null) {
            if (ctx is ActivityContext) return ctx
            ctx = (ctx as? android.content.ContextWrapper)?.baseContext
        }
        return null
    }

    /** True while a long-press item drag is in progress or the launcher is in edit mode. */
    private fun isPagingBlocked(): Boolean = isEditing || isLauncherEditMode()

    private fun isLauncherEditMode(): Boolean {
        val activity = findActivityContext()
        return activity is Launcher && activity.isInState(LauncherState.EDIT_MODE)
    }

    /**
     * Draws the edit-mode checkbox for the folder as a whole, in the card's free top-right
     * corner: an empty "to be selected" box while the folder is not selected and a check mark
     * once it is. The per-app checkboxes belong to the cells inside the card.
     */
    private fun drawFolderSelectionBadge(canvas: Canvas) {
        val info = folderInfo ?: return
        val activity = findActivityContext() ?: return
        if (!EditModeSelectionBadge.shouldShow(activity, BubbleTextView.DISPLAY_WORKSPACE)) {
            return
        }
        val density = resources.displayMetrics.density
        // Match the per-icon badges instead of scaling with the (much larger) card.
        val diameter = maxOf(activity.deviceProfile.iconSizePx * 0.30f, density * 13f)
        val radius = diameter / 2f
        val corner = if (frostedGlass || liquidGlass) glassCornerRadius else cornerRadius
        val inset = radius + corner * 0.9f
        val centerX = bgRect.right - inset
        val centerY = bgRect.top + inset
        badgeBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        if (EditModeSelectionBadge.isSelected(activity, info)) {
            EditModeSelectionBadge.draw(canvas, badgeBounds)
        } else {
            EditModeSelectionBadge.drawUnselected(canvas, badgeBounds)
        }
    }

    private fun refitInlineIcons() {
        if (!inlineMode) return
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is BubbleTextView && child.width > 0 && child.height > 0) {
                fitInlineIconToCell(child, child.width, child.height, reapplyCompound = true)
            } else if (child is OverflowGridView) {
                child.refitIcons()
            }
        }
    }

    private fun fitInlineIconToCell(
        child: BubbleTextView,
        cellW: Int,
        cellH: Int,
        reapplyCompound: Boolean,
    ) {
        if (cellW <= 0 || cellH <= 0) return
        // Icon box is a square inscribed in the cell, scaled by the user's factor.
        val side = min(cellW, cellH)
        val iconBox = (side * iconSizeFactor).roundToInt().coerceIn(1, side)
        // Center the icon box inside the (possibly non-square) cell via padding.
        val padX = (cellW - iconBox) / 2
        val padY = (cellH - iconBox) / 2
        if (child.paddingLeft != padX || child.paddingTop != padY
            || child.paddingRight != padX || child.paddingBottom != padY
        ) {
            child.setPadding(padX, padY, padX, padY)
        }
        val icon = child.compoundDrawables.firstOrNull { it != null } ?: return
        // Contain: keep the drawable's aspect ratio and fit its long side into the box,
        // so odd-shaped icons are fully visible and never distorted.
        val iw = icon.intrinsicWidth
        val ih = icon.intrinsicHeight
        val (bw, bh) = if (iw > 0 && ih > 0) {
            val scale = min(iconBox / iw.toFloat(), iconBox / ih.toFloat())
            val w = (iw * scale).roundToInt().coerceAtLeast(1)
            val h = (ih * scale).roundToInt().coerceAtLeast(1)
            w to h
        } else {
            iconBox to iconBox
        }
        val changed = icon.bounds.width() != bw || icon.bounds.height() != bh
        if (changed) {
            icon.setBounds(0, 0, bw, bh)
        }
        // Edit-mode adornments are positioned from the icon bounds, which normally follow the
        // full-size workspace icon. Point them at this cell's actual icon box instead so the
        // selection checkbox sits on the icon the user is tapping.
        child.setIconBoundsOverride(
            android.graphics.Rect(padX, padY, padX + iconBox, padY + iconBox),
        )
        // TextView caches the compound-drawable offsets from the view size, padding and
        // drawable bounds it saw at set time. Re-applying the same drawables makes it
        // recompute them. Track the state each icon was last re-applied with, because a
        // measure pass changes bounds/padding without re-applying (doing that inside
        // measure would bake in offsets from a not-yet-laid-out view); the first refit
        // after layout then has to repair it even though bounds look "already correct".
        val state = "$cellW:$cellH:$bw:$bh"
        val recorded = reappliedState[child]
        if (recorded == state && !changed) return
        if (reapplyCompound) {
            reappliedState[child] = state
            val rel = child.compoundDrawablesRelative
            child.setCompoundDrawablesRelative(rel[0], rel[1], rel[2], rel[3])
        } else {
            reappliedState.remove(child)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        if (childCount == 0 || gridCols < 1) return
        val (cellW, cellH) = cellSize(w, h)
        val cellWt = cellW.toInt()
        val cellHt = cellH.toInt()
        val cols = gridCols
        val edge = bgInsetPx + bgPaddingPx
        val pagerBand = if (swiperMode.isSwiper && pageCount > 1) pageIndicatorBandPx else 0f
        val contentL = edge
        val contentT = edge
        val contentW = w - 2f * edge - if (swiperMode == SwiperMode.VERTICAL) pagerBand else 0f
        val contentH = h - 2f * edge - bottomContentInset -
            if (swiperMode == SwiperMode.HORIZONTAL) pagerBand else 0f
        val lines = (childCount + cols - 1) / cols
        val usedH = lines * cellH + (lines - 1) * iconGapPx
        val freeV = (contentH - usedH).coerceAtLeast(0f)
        for (line in 0 until lines) {
            val lineCount = min(cols, childCount - line * cols)
            val usedW = lineCount * cellW + (lineCount - 1) * iconGapPx
            val freeH = (contentW - usedW).coerceAtLeast(0f)
            val top = (contentT + line * (cellH + iconGapPx) +
                flexExtra(line, lines, freeV, alignV)).roundToInt()
            for (c in 0 until lineCount) {
                val i = line * cols + c
                val left = (contentL + c * (cellW + iconGapPx) +
                    flexExtra(c, lineCount, freeH, alignH)).roundToInt()
                val view = getChildAt(i)
                view.layout(left, top, left + cellWt, top + cellHt)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val inset = bgInsetPx
        bgRect.set(inset, inset, width - inset, height - inset)
        val glass = frostedGlass || liquidGlass
        val radius = if (glass) glassCornerRadius else cornerRadius
        val path = android.graphics.Path().apply {
            addRoundRect(bgRect, radius, radius, android.graphics.Path.Direction.CW)
        }
        val save = canvas.save()
        canvas.clipPath(path)

        if (glass) {
            // 1. Whatever is actually behind the card, blurred: the wallpaper, sampled at
            //    this card's on-screen position. Without this the card only tints the
            //    wallpaper and reads as tinted plastic rather than glass.
            val gen = GlassBackdrop.generation()
            if (gen != glassGeneration) {
                glassGeneration = gen
                glassBackdrop = null
                ensureGlassReady()
            }
            drawGlassBackdrop(canvas)
            if (frostedGlass) {
                drawFrostedVeil(canvas)
            }
            drawGlassNoise(canvas)
        } else {
            bgPaint.shader = null
            bgPaint.color = bgColor
            bgPaint.alpha = (230 * bgAlpha).toInt()
            canvas.drawRoundRect(bgRect, radius, radius, bgPaint)
        }

        canvas.restoreToCount(save)
        if (glass) {
            if (frostedGlass) {
                borderPaint.shader = null
                borderPaint.color = FROSTED_BORDER_COLOR
            } else {
                // 1dp edge highlight: brightest at the top-left, fading toward the
                // bottom-right, which is what sells the "light refracting along the rim" read.
                borderPaint.shader = borderHighlightShader
            }
            borderPaint.strokeWidth = oneDpPx
        } else {
            borderPaint.shader = null
            borderPaint.strokeWidth = 1f
        }
        canvas.drawRoundRect(bgRect, radius, radius, borderPaint)
        if (dropHighlight) {
            canvas.drawRoundRect(bgRect, radius, radius, highlightPaint)
        }
        if (liquidGlass && glassSpecularFlow) postInvalidateOnAnimation()
        super.onDraw(canvas)
    }

    /**
     * Ensures the blurred wallpaper backdrop and the light/dark glass palette are ready.
     * The backdrop is built once in the background; until then the card falls back to the
     * tint-only look and repaints when [GlassBackdrop] reports it has landed.
     */
    private fun loadGlassBackdrop(): Bitmap? =
        if (liquidGlass) {
            GlassBackdrop.getLens(context)
        } else {
            GlassBackdrop.get(context, glassBlurDp)
        }

    private fun ensureGlassReady() {
        glassIsDark = Utilities.isDarkTheme(context)
        glassGeneration = GlassBackdrop.generation()
        glassBackdrop = loadGlassBackdrop()
        if (glassBackdrop == null) {
            GlassBackdrop.addListener(glassBackdropListener)
            glassBackdrop = loadGlassBackdrop()
            if (glassBackdrop != null) {
                GlassBackdrop.removeListener(glassBackdropListener)
            }
        }
        if (glassNoise == null) {
            glassNoise = buildNoiseShader()
        }
    }

    private fun drawGlassBackdrop(canvas: Canvas) {
        val backdrop = glassBackdrop ?: return
        val phase = when {
            !liquidGlass -> -1f
            glassSpecularFlow -> {
                if (specularStartMs == 0L) {
                    specularStartMs = android.os.SystemClock.uptimeMillis()
                }
                val elapsed = (android.os.SystemClock.uptimeMillis() - specularStartMs) %
                    SPECULAR_DURATION_MS
                elapsed.toFloat() / SPECULAR_DURATION_MS
            }
            specularAnimator?.isRunning == true -> specularPhase
            else -> -1f
        }
        GlassBackdrop.draw(
            this,
            canvas,
            backdrop,
            bgRect.left.roundToInt(),
            bgRect.top.roundToInt(),
            bgRect.right.roundToInt(),
            bgRect.bottom.roundToInt(),
            if (liquidGlass) glassRefraction else 0f,
            glassCornerRadius,
            glassDispersion,
            blurPx = 2.4f * resources.displayMetrics.density,
            specularPhase = phase,
        )
    }

    /** System-material colour overlay: a light veil on light themes, a dark one on dark. */
    private fun drawGlassTint(canvas: Canvas) {
        // ~20% white in light mode, ~12% black in dark mode.
        val alpha = if (glassIsDark) 0x40 else 0x33
        bgPaint.shader = null
        bgPaint.color = if (glassIsDark) {
            ColorUtils.setAlphaComponent(Color.BLACK, alpha)
        } else {
            ColorUtils.setAlphaComponent(Color.WHITE, alpha)
        }
        canvas.drawRect(bgRect, bgPaint)
    }

    /**
     * Grain over the finished glass. Without it the large flat gradients above band visibly
     * on the big panels a 3x3 or 4x3 folder occupies.
     */
    private fun drawGlassNoise(canvas: Canvas) {
        val noise = glassNoise ?: return
        glassPaint.shader = noise
        glassPaint.alpha = 0x07
        canvas.drawRect(bgRect, glassPaint)
    }

    private fun buildNoiseShader(): Shader? {
        val size = 96
        val pixels = IntArray(size * size)
        val random = java.util.Random(0x5DEECE66L)
        for (i in pixels.indices) {
            val n = random.nextInt(96) + 80
            pixels[i] = Color.argb(0xFF, n, n, n)
        }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    /**
     * iOS UIBlurEffect-style frosted veil: a soft white wash on top of the tinted base,
     * heavier toward the centre so the edges of the card stay slightly more translucent.
     */
    private fun drawFrostedVeil(canvas: Canvas) {
        if (width == 0 || height == 0) return
        ensureGlassShader()
        glassPaint.shader = frostedShader
        glassPaint.alpha = 0xFF
        canvas.drawRect(bgRect, glassPaint)
    }


    /**
     * iOS 26 liquid-glass sheen: a bright diagonal specular streak that reads as a
     * wet, glossy surface catching the light.
     */
    private fun drawLiquidGlassSheen(canvas: Canvas) {
        if (width == 0 || height == 0) return
        ensureGlassShader()
        glassPaint.shader = liquidShader
        glassPaint.alpha = 160
        canvas.drawRect(bgRect, glassPaint)

        // Secondary softer highlight from the opposite corner for depth.
        glassPaint.shader = liquidShader2
        glassPaint.alpha = 70
        canvas.drawRect(bgRect, glassPaint)
    }

    /**
     * iOS liquid-glass edge lighting: a thin bright rim along the top/left edges and a
     * subtle darker line along the bottom/right, giving the card a raised, refractive look.
     */
    private fun drawLiquidGlassEdgeHighlight(canvas: Canvas) {
        if (width == 0 || height == 0) return
        ensureGlassShader()
        // Bright top edge.
        glassPaint.shader = edgeTopShader
        glassPaint.alpha = 120
        canvas.drawRect(bgRect, glassPaint)
        // Subtle bottom shadow.
        glassPaint.shader = edgeBottomShader
        glassPaint.alpha = 60
        canvas.drawRect(bgRect, glassPaint)
    }

    /**
     * A bright band sweeping across the card, which is what reads as light sliding over a
     * wet surface. Only animated for a short window after the card is built (see
     * [startSpecularFlow]) so an idle desktop does not invalidate every frame.
     */
    private fun drawLiquidSpecular(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val phase = if (glassSpecularFlow) {
            if (specularStartMs == 0L) specularStartMs = android.os.SystemClock.uptimeMillis()
            val elapsed = (android.os.SystemClock.uptimeMillis() - specularStartMs) %
                SPECULAR_DURATION_MS
            elapsed.toFloat() / SPECULAR_DURATION_MS
        } else {
            specularPhase
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val centre = (-0.2f + phase * 1.4f) * w
        val band = w * 0.16f
        glassPaint.shader = safeGradient(
            centre - band, -h * 0.2f, centre + band, h * 1.2f,
            0x8CFFFFFF.toInt(), 0x00FFFFFF,
        )
        glassPaint.alpha = 150
        canvas.drawRect(bgRect, glassPaint)
        if (glassSpecularFlow) postInvalidateOnAnimation()
    }

    /** Runs the liquid specular for [SPECULAR_DURATION_MS] after the card appears. */
    private fun startSpecularFlow() {
        if (!liquidGlass || glassSpecularFlow || !isAttachedToWindow) return
        specularAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SPECULAR_DURATION_MS
            interpolator = LinearInterpolator()
            repeatCount = 1
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                specularPhase = it.animatedValue as Float
                invalidate()
            }
        }
        specularAnimator = animator
        animator.start()
    }

    /**
     * Creates a LinearGradient without triggering [android.graphics.Shader.detectColorSpace],
     * which crashes on Android 13+ when packed int colours carry an invalid colour-space
     * ID in their upper bits. The Color-based constructor (API 26+) skips that detection
     * because Color objects already carry an explicit colour space.
     */
    private fun safeGradient(
        x0: Float, y0: Float, x1: Float, y1: Float,
        c0: Int, c1: Int,
    ): LinearGradient {
        return try {
            LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP)
        } catch (_: IllegalArgumentException) {
            val a0 = (c0 shr 24) and 0xFF
            val r0 = (c0 shr 16) and 0xFF
            val g0 = (c0 shr 8) and 0xFF
            val b0 = c0 and 0xFF
            val a1 = (c1 shr 24) and 0xFF
            val r1 = (c1 shr 16) and 0xFF
            val g1 = (c1 shr 8) and 0xFF
            val b1 = c1 and 0xFF
            LinearGradient(
                x0, y0, x1, y1,
                android.graphics.Color.argb(a0, r0, g0, b0),
                android.graphics.Color.argb(a1, r1, g1, b1),
                Shader.TileMode.CLAMP,
            )
        }
    }

    /** Lazily builds the gradient shaders for the glass overlays (reused across draws). */
    private fun ensureGlassShader() {
        if (glassShadersReady && width == glassW && height == glassH &&
            glassShaderDark == glassIsDark
        ) {
            return
        }
        glassW = width
        glassH = height
        glassShaderDark = glassIsDark
        glassShadersReady = true
        val w = width.toFloat()
        val h = height.toFloat()
        frostedShader = if (glassIsDark) {
            safeGradient(0f, 0f, 0f, h, 0x33000000, 0x00000000)
        } else {
            safeGradient(0f, 0f, 0f, h, 0x22FFFFFF, 0x00FFFFFF)
        }
        liquidShader = safeGradient(
            -w * 0.1f, -h * 0.1f, w * 0.8f, h * 0.8f,
            0x8CFFFFFF.toInt(), 0x00FFFFFF,
        )
        liquidShader2 = safeGradient(
            w * 1.1f, h * 1.1f, w * 0.2f, h * 0.2f,
            0x4DFFFFFF, 0x00FFFFFF,
        )
        edgeTopShader = safeGradient(
            0f, 0f, 0f, h * 0.12f,
            0xB3FFFFFF.toInt(), 0x00FFFFFF,
        )
        edgeBottomShader = safeGradient(
            0f, h * 0.88f, 0f, h,
            0x00000000, 0x1A000000,
        )
        // 1dp glass rim: 30% white at the top-left falling to 5% at the bottom-right, so the
        // edge reads as a refracting highlight rather than a drawn outline.
        borderHighlightShader = safeGradient(
            0f, 0f, w, h,
            0x4DFFFFFF.toInt(), 0x0DFFFFFF,
        )
    }

    /** Highlights the whole card while a drag hovers over it (inline drop target). */
    fun setDropHighlight(active: Boolean) {
        if (dropHighlight != active) {
            dropHighlight = active
            invalidate()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        // The long-side capacity rule depends on orientation; re-derive the grid.
        rederiveGrid(log = true)
        requestLayout()
        invalidate()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            isEditing = false
        }
        if (!inlineMode || !swiperMode.isSwiper || pageCount <= 1 || isLauncherEditMode()) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startSwipeTracking(event)
                // Keep the workspace pager out of this card: a horizontal fold page swipe
                // must not become a workspace page swipe.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (trackSwipe(event)) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetSwipeTracking()
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (inlineMode) {
            // Empty-area touches are handled here so that:
            //  - a tap does nothing (opening the folder popup is reserved for the
            //    overflow mini-grid cell),
            //  - a long-press shows the resize handles,
            //  - a swiper swipe pages the card,
            //  - nothing leaks to the workspace swipe gesture handlers.
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isEditing = false
                    emptyPressActive = true
                    emptyDownX = event.x
                    emptyDownY = event.y
                    removeCallbacks(emptyLongPressTask)
                    postDelayed(
                        emptyLongPressTask,
                        android.view.ViewConfiguration.getLongPressTimeout().toLong()
                    )
                    startSwipeTracking(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (trackSwipe(event)) {
                        return true
                    }
                    val dx = event.x - emptyDownX
                    val dy = event.y - emptyDownY
                    val beyondSlop = dx * dx + dy * dy > emptyTouchSlop * emptyTouchSlop
                    if (emptyPressActive && beyondSlop) {
                        // Moved before the long-press fired: treat it as a scroll attempt.
                        removeCallbacks(emptyLongPressTask)
                        emptyPressActive = false
                    } else if (emptyLongPressed && !emptyDragStarted && beyondSlop) {
                        // The long-press already showed the handles; keep going and turn this
                        // same gesture into a workspace drag of the folder.
                        emptyDragStarted = true
                        onEmptyAreaDragStart?.invoke()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(emptyLongPressTask)
                    emptyPressActive = false
                    emptyLongPressed = false
                    emptyDragStarted = false
                    finishSwipe(event)
                    resetSwipeTracking()
                    isEditing = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(emptyLongPressTask)
                    emptyPressActive = false
                    emptyLongPressed = false
                    emptyDragStarted = false
                    resetSwipeTracking()
                    isEditing = false
                }
            }
            return true
        }
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggedIndex = hitTest(event.x, event.y)
                startSwipeTracking(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (trackSwipe(event)) return true
                if (draggedIndex >= 0) {
                    val over = hitTest(event.x, event.y)
                    if (over >= 0 && over != dragOverIndex) {
                        dragOverIndex = over
                        animateHighlight(dragOverIndex)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggedIndex >= 0 && dragOverIndex >= 0 && draggedIndex != dragOverIndex) {
                    moveItem(draggedIndex, dragOverIndex)
                }
                draggedIndex = -1
                dragOverIndex = -1
                if (event.action == MotionEvent.ACTION_UP) finishSwipe(event)
                resetSwipeTracking()
                isEditing = false
            }
        }
        return true
    }

    private fun isVisibleItemIndex(index: Int): Boolean =
        index in pageStart until (pageStart + itemViews.size) && index in items.indices

    private fun hitTest(x: Float, y: Float): Int {
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            if (child.left <= x && x <= child.right && child.top <= y && y <= child.bottom) {
                return pageStart + i
            }
        }
        return -1
    }

    private fun animateHighlight(index: Int) {
        val local = index - pageStart
        if (local !in itemViews.indices) return
        val view = itemViews[local]
        view.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(100)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }
            .start()
    }

    private fun performHapticFeedback() {
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    /**
     * The last cell of an overflowing folder: a stacked deck of up to 3 overflow app
     * icons, offset diagonally from the top-left toward the bottom-right (front icon
     * on top). When more apps are hidden, a "+N" badge sits on the deck front; the
     * hidden ones are reachable by opening the folder. The whole cell owns the
     * gestures (tap opens the folder, long-press shows the resize handles) and scales
     * up on hover like a regular app icon.
     */
    private inner class OverflowGridView(
        overflowItems: List<FolderItem>,
        private val showPlus: Boolean,
        private val hiddenCount: Int,
    ) : FrameLayout(context) {

        private val iconViews: List<BubbleTextView> =
            overflowItems
                .take(LargeFolderGrid.MAX_STACKED_ICONS)
                .map { item -> createInlineItemView(item, 0, clickable = false) }

        private val plusView = TextView(context).apply {
            text = "+$hiddenCount"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFFFFFFF"))
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#CC1A1A1A"))
            }
            isClickable = false
            isLongClickable = false
        }

        init {
            iconViews.forEach { addView(it) }
            if (showPlus) {
                addView(plusView)
            }
            isClickable = true
            isLongClickable = true
            setOnClickListener { onOverflowClicked?.invoke() }
            setOnLongClickListener {
                performHapticFeedback()
                onOverflowLongClicked?.invoke() ?: false
            }
        }

        override fun onHoverChanged(hovered: Boolean) {
            super.onHoverChanged(hovered)
            animate().cancel()
            animate()
                .scaleX(if (hovered) overflowHoverScale else 1f)
                .scaleY(if (hovered) overflowHoverScale else 1f)
                .setDuration(overflowHoverDurationMs)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val side = minOf(measuredWidth, measuredHeight)
            if (side <= 0) return
            val layers = iconViews.size
            if (layers == 0) return
            val iconSide = (side * deckIconFraction(layers)).toInt().coerceAtLeast(1)
            val spec = MeasureSpec.makeMeasureSpec(iconSide, MeasureSpec.EXACTLY)
            for (child in iconViews) {
                child.measure(spec, spec)
                fitInlineIconToCell(child, iconSide, iconSide, reapplyCompound = false)
            }
            if (showPlus) {
                val badge = (side * deckBadgeFraction).toInt().coerceAtLeast(1)
                plusView.setTextSize(TypedValue.COMPLEX_UNIT_PX, side * deckBadgeTextFraction)
                val bspec = MeasureSpec.makeMeasureSpec(badge, MeasureSpec.EXACTLY)
                plusView.measure(bspec, bspec)
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val side = minOf(r - l, b - t)
            if (side <= 0) return
            val layers = iconViews.size
            if (layers == 0) return
            val iconSide = (side * deckIconFraction(layers)).toInt().coerceAtLeast(1)
            val offX = (r - l - side) / 2
            val offY = (b - t - side) / 2
            if (layers == 1) {
                val cx = offX + (side - iconSide) / 2
                val cy = offY + (side - iconSide) / 2
                val child = iconViews[0]
                child.layout(cx, cy, cx + iconSide, cy + iconSide)
            } else {
                val step = (side - iconSide).toFloat() / (layers - 1)
                for (i in iconViews.indices) {
                    val child = iconViews[i]
                    val left = offX + (i * step).roundToInt()
                    val top = offY + (i * step).roundToInt()
                    child.layout(left, top, left + iconSide, top + iconSide)
                }
            }
            if (showPlus && plusView.measuredWidth > 0) {
                val badge = plusView.measuredWidth
                val margin = (side * 0.04f).roundToInt()
                val left = offX + side - badge - margin
                val top = offY + side - badge - margin
                plusView.layout(left, top, left + badge, top + badge)
            }
        }

        fun refitIcons() {
            val layers = iconViews.size
            for ((idx, child) in iconViews.withIndex()) {
                if (child.width > 0 && child.height > 0) {
                    fitInlineIconToCell(child, child.width, child.height, reapplyCompound = true)
                }
                val icon = child.compoundDrawables.firstOrNull { it != null } ?: continue
                val depth = layers - 1 - idx
                icon.colorFilter = when (depth) {
                    1 -> android.graphics.PorterDuffColorFilter(
                        Color.argb(26, 128, 128, 128),
                        android.graphics.PorterDuff.Mode.SRC_ATOP,
                    )
                    2 -> android.graphics.PorterDuffColorFilter(
                        Color.argb(51, 128, 128, 128),
                        android.graphics.PorterDuff.Mode.SRC_ATOP,
                    )
                    else -> null
                }
            }
        }
    }

    class FolderItemView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr) {

        private val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        private val labelView = TextView(context).apply {
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            layoutParams = LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        private val categoryIndicator = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.density * 4f).toInt(),
                (resources.displayMetrics.density * 4f).toInt()
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
            }
        }

        private val itemBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A000000")
            style = Paint.Style.FILL
        }

        private var currentItem: FolderItem? = null

        init {
            setWillNotDraw(false)
            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                setPadding(8, 8, 8, 4)
            }
            container.addView(iconView, android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            container.addView(labelView)
            addView(container)
            addView(categoryIndicator)
        }

        fun setItem(item: FolderItem) {
            currentItem = item
            iconView.setImageDrawable(item.icon)
            labelView.text = item.label
            categoryIndicator.setBackgroundColor(item.category.color)
        }

        fun setShowLabel(show: Boolean) {
            labelView.visibility = if (show) View.VISIBLE else View.GONE
        }

        fun setIconSizeFactor(factor: Float) {
            iconView.scaleX = factor
            iconView.scaleY = factor
        }

        override fun onDraw(canvas: Canvas) {
            val r = resources.displayMetrics.density * 8f
            canvas.drawRoundRect(
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                r, r, itemBgPaint
            )
            super.onDraw(canvas)
        }
    }
}
