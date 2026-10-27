package com.jerry.launcherx.modern.widget

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.RemoteViews
import kotlin.math.abs
import kotlin.math.min

class SmartStackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class StackWidget(
        val widgetId: Int,
        val label: String,
        val provider: String,
        val minWidthDp: Int,
        val minHeightDp: Int,
        val isInteractive: Boolean = false
    )

    private val widgets = mutableListOf<StackWidget>()
    private var currentIndex = 0
    private var isAnimating = false
    private var autoRotateInterval = 30_000L
    private var autoRotateEnabled = true
    private var lastAutoRotateTime = 0L

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val indicatorInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.FILL
    }
    private val indicatorRadius by lazy { resources.displayMetrics.density * 3f }
    private val indicatorSpacing by lazy { resources.displayMetrics.density * 6f }

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isSwiping = false

    var onWidgetChanged: ((Int, StackWidget) -> Unit)? = null
    var onWidgetLongPressed: ((Int, StackWidget) -> Unit)? = null
    var onWidgetRemoved: ((Int) -> Unit)? = null

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (abs(velocityX) > abs(velocityY)) {
                    if (velocityX < -500) {
                        showNext()
                        return true
                    } else if (velocityX > 500) {
                        showPrevious()
                        return true
                    }
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                onWidgetLongPressed?.invoke(currentIndex, widgets[currentIndex])
            }
        })

    init {
        setWillNotDraw(false)
    }

    fun addWidget(widget: StackWidget) {
        widgets.add(widget)
        requestLayout()
        invalidate()
    }

    fun removeWidget(index: Int) {
        if (index in widgets.indices) {
            widgets.removeAt(index)
            if (currentIndex >= widgets.size) {
                currentIndex = (widgets.size - 1).coerceAtLeast(0)
            }
            onWidgetRemoved?.invoke(index)
            requestLayout()
            invalidate()
        }
    }

    fun showNext() {
        if (widgets.size <= 1 || isAnimating) return
        val nextIndex = (currentIndex + 1) % widgets.size
        animateTransition(currentIndex, nextIndex, slideLeft = true)
    }

    fun showPrevious() {
        if (widgets.size <= 1 || isAnimating) return
        val prevIndex = if (currentIndex - 1 < 0) widgets.size - 1 else currentIndex - 1
        animateTransition(currentIndex, prevIndex, slideLeft = false)
    }

    fun showWidget(index: Int) {
        if (index == currentIndex || index !in widgets.indices || isAnimating) return
        animateTransition(currentIndex, index, slideLeft = index > currentIndex)
    }

    fun setAutoRotate(enabled: Boolean, intervalMs: Long = 30_000L) {
        autoRotateEnabled = enabled
        autoRotateInterval = intervalMs
    }

    fun getCurrentWidget(): StackWidget? = widgets.getOrNull(currentIndex)
    fun getCurrentIndex() = currentIndex
    fun getWidgetCount() = widgets.size

    private fun animateTransition(from: Int, to: Int, slideLeft: Boolean) {
        if (from !in 0 until childCount || to !in 0 until childCount) return

        isAnimating = true
        val fromChild = getChildAt(from)
        val toChild = getChildAt(to) ?: return

        val direction = if (slideLeft) -1f else 1f
        val width = width.toFloat()

        toChild.visibility = VISIBLE
        toChild.translationX = width * direction

        val animFrom = ObjectAnimator.ofFloat(fromChild, "translationX", width * -direction)
        val animTo = ObjectAnimator.ofFloat(toChild, "translationX", 0f)

        AnimatorSet().apply {
            playTogether(animFrom, animTo)
            duration = 250
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    fromChild.visibility = GONE
                    fromChild.translationX = 0f
                    currentIndex = to
                    isAnimating = false
                    onWidgetChanged?.invoke(currentIndex, widgets[currentIndex])
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            child.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            )
            child.layout(0, 0, w, h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (widgets.size <= 1) return

        val totalWidth = widgets.size * indicatorRadius * 2 + (widgets.size - 1) * indicatorSpacing
        var startX = (width - totalWidth) / 2f
        val y = height - indicatorRadius * 3

        for (i in widgets.indices) {
            val paint = if (i == currentIndex) indicatorPaint else indicatorInactivePaint
            canvas.drawCircle(
                startX + indicatorRadius,
                y,
                indicatorRadius,
                paint
            )
            startX += indicatorRadius * 2 + indicatorSpacing
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                isSwiping = false
                if (autoRotateEnabled) {
                    lastAutoRotateTime = System.currentTimeMillis()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (abs(dx) > abs(dy) && abs(dx) > 10) {
                    isSwiping = true
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isSwiping) {
                    performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoRotateEnabled) {
            startAutoRotate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler?.removeCallbacksAndMessages(null)
    }

    private fun startAutoRotate() {
        handler?.postDelayed({
            if (autoRotateEnabled && widgets.size > 1) {
                val now = System.currentTimeMillis()
                if (now - lastAutoRotateTime >= autoRotateInterval) {
                    showNext()
                    lastAutoRotateTime = now
                }
                startAutoRotate()
            }
        }, 1000)
    }
}
