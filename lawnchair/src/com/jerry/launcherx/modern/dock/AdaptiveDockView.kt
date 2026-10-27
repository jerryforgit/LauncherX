package com.jerry.launcherx.modern.dock

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import kotlin.math.abs

class AdaptiveDockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class DockMode {
        PHONE,
        TABLET,
        DESKTOP
    }

    data class DockItem(
        val id: Long,
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val isRecent: Boolean = false,
        val isPinned: Boolean = true
    )

    private var dockMode = DockMode.PHONE
    private val pinnedItems = mutableListOf<DockItem>()
    private val recentItems = mutableListOf<DockItem>()
    private val dockViews = mutableListOf<DockItemView>()
    private var dividerIndex = -1
    private var draggedIndex = -1
    private var dragOverIndex = -1

    private val dockBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC1A1A1A")
        style = Paint.Style.FILL
    }
    private val dockBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val cornerRadius by lazy { resources.displayMetrics.density * 24f }
    private val itemSize by lazy { resources.displayMetrics.density * 48f }
    private val itemMargin by lazy { resources.displayMetrics.density * 4f }
    private val bgRect = RectF()
    private val blurOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
    }

    var onItemClicked: ((DockItem, Int, Boolean) -> Unit)? = null
    var onItemLongClicked: ((DockItem, Int) -> Boolean)? = null
    var onItemRemoved: ((DockItem, Int) -> Unit)? = null
    var onDrawerClicked: (() -> Unit)? = null
    var onModeChanged: ((DockMode) -> Unit)? = null

    private val maxPinnedPhone = 5
    private val maxPinnedTablet = 8
    private val maxRecentTablet = 5

    private val appDrawerIcon: Drawable? by lazy {
        context.getDrawable(android.R.drawable.ic_menu_sort_by_size)
    }

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
        enableDragAndDrop()
    }

    fun setDockMode(mode: DockMode) {
        if (dockMode == mode) return
        dockMode = mode
        rebuildViews()
        requestLayout()
        invalidate()
        onModeChanged?.invoke(mode)
    }

    fun getDockMode() = dockMode

    fun setPinnedItems(items: List<DockItem>) {
        pinnedItems.clear()
        pinnedItems.addAll(items)
        rebuildViews()
        requestLayout()
        invalidate()
    }

    fun setRecentItems(items: List<DockItem>) {
        recentItems.clear()
        recentItems.addAll(items)
        rebuildViews()
        requestLayout()
        invalidate()
    }

    fun addRecentItem(item: DockItem) {
        recentItems.removeAll { it.packageName == item.packageName }
        recentItems.add(0, item)
        val maxRecent = if (dockMode == DockMode.TABLET || dockMode == DockMode.DESKTOP) maxRecentTablet else 0
        while (recentItems.size > maxRecent) {
            recentItems.removeAt(recentItems.lastIndex)
        }
        rebuildViews()
        requestLayout()
        invalidate()
    }

    fun getPinnedItems(): List<DockItem> = pinnedItems.toList()
    fun getRecentItems(): List<DockItem> = recentItems.toList()

    private fun getMaxPinned(): Int = when (dockMode) {
        DockMode.PHONE -> maxPinnedPhone
        DockMode.TABLET, DockMode.DESKTOP -> maxPinnedTablet
    }

    private fun rebuildViews() {
        removeAllViews()
        dockViews.clear()
        dividerIndex = -1

        pinnedItems.take(getMaxPinned()).forEachIndexed { index, item ->
            val view = DockItemView(context).apply {
                setItem(item)
                setOnClickListener { onItemClicked?.invoke(item, index, false) }
                setOnLongClickListener {
                    performHapticFeedback()
                    onItemLongClicked?.invoke(item, index) ?: false
                }
            }
            dockViews.add(view)
            addView(view)
        }

        if (dockMode != DockMode.PHONE && recentItems.isNotEmpty()) {
            dividerIndex = dockViews.size

            val divider = View(context).apply {
                layoutParams = LayoutParams(2, itemSize.toInt()).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                setBackgroundColor(Color.parseColor("#66FFFFFF"))
            }
            addView(divider)

            recentItems.forEachIndexed { rIdx, item ->
                val globalIndex = dockViews.size
                val view = DockItemView(context).apply {
                    setItem(item)
                    setAlpha(0.7f)
                    setOnClickListener { onItemClicked?.invoke(item, globalIndex, true) }
                    setOnLongClickListener {
                        performHapticFeedback()
                        onItemLongClicked?.invoke(item, globalIndex) ?: false
                    }
                }
                dockViews.add(view)
                addView(view)
            }
        }

        if (dockMode == DockMode.TABLET || dockMode == DockMode.DESKTOP) {
            val drawerView = DockDrawerButton(context).apply {
                setOnClickListener { onDrawerClicked?.invoke() }
            }
            addView(drawerView)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentW = MeasureSpec.getSize(widthMeasureSpec)
        val totalItems = dockViews.size + if (dockMode != DockMode.PHONE) 2 else 0
        val spacing = itemMargin
        val totalWidth = totalItems * (itemSize + spacing) - spacing
        val dockHeight = itemSize + paddingTop + paddingBottom

        val w = if (dockMode == DockMode.PHONE) parentW else (totalWidth + paddingStart + paddingEnd).toInt()
        setMeasuredDimension(w, dockHeight.toInt())

        var xOffset = ((w - totalWidth) / 2).coerceAtLeast(paddingStart.toFloat())

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val childW = if (child is DockDrawerButton) itemSize.toInt() else itemSize.toInt()
            child.measure(
                MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(itemSize.toInt(), MeasureSpec.EXACTLY)
            )
            child.layout(
                xOffset.toInt(),
                paddingTop,
                (xOffset + childW).toInt(),
                paddingTop + itemSize.toInt()
            )
            xOffset += childW + spacing
        }
    }

    override fun onDraw(canvas: Canvas) {
        bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, dockBgPaint)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, dockBorderPaint)

        if (dividerIndex >= 0 && dividerIndex < childCount) {
            val dividerChild = getChildAt(dividerIndex)
            val cx = (dividerChild.left + dividerChild.right) / 2f
            canvas.drawLine(
                cx, paddingTop + itemSize * 0.2f,
                cx, paddingTop + itemSize * 0.8f,
                dividerPaint
            )
        }
        super.onDraw(canvas)
    }

    private fun enableDragAndDrop() {
        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED -> {
                    animateScale(1.05f)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    animateScale(1f)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    animateScale(1f)
                    true
                }
                else -> true
            }
        }
    }

    private fun animateScale(targetScale: Float) {
        animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun performHapticFeedback() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    class DockItemView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr) {

        private val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        private val labelView = TextView(context).apply {
            textSize = 9f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(2f, 0f, 1f, Color.BLACK)
            visibility = GONE
        }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val bgRect = RectF()

        private var isRecentItem = false

        init {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
            container.addView(iconView, LinearLayout.LayoutParams(
                (resources.displayMetrics.density * 36).toInt(),
                (resources.displayMetrics.density * 36).toInt()
            ))
            container.addView(labelView)
            addView(container)
        }

        fun setItem(item: DockItem) {
            isRecentItem = item.isRecent
            iconView.setImageDrawable(item.icon)
            labelView.text = item.label
            bgPaint.color = if (isRecentItem) {
                Color.parseColor("#22FFFFFF")
            } else {
                Color.parseColor("#11FFFFFF")
            }
        }

        override fun onDraw(canvas: Canvas) {
            bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            val r = resources.displayMetrics.density * 12f
            canvas.drawRoundRect(bgRect, r, r, bgPaint)
            super.onDraw(canvas)
        }
    }

    class DockDrawerButton @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr) {

        private val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22FFFFFF")
            style = Paint.Style.FILL
        }
        private val bgRect = RectF()

        init {
            addView(iconView)
        }

        override fun onDraw(canvas: Canvas) {
            bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            val r = resources.displayMetrics.density * 12f
            canvas.drawRoundRect(bgRect, r, r, bgPaint)
            super.onDraw(canvas)
        }
    }
}
