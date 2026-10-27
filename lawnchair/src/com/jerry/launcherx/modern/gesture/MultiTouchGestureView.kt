package com.jerry.launcherx.modern.gesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.min

class MultiTouchGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface GestureCallback {
        fun onThreeFingerSwipeLeft() {}
        fun onThreeFingerSwipeRight() {}
        fun onFourFingerSwipeUp() {}
        fun onFourFingerSwipeDown() {}
        fun onThreeFingerTap() {}
        fun onFourFingerTap() {}
    }

    private var callback: GestureCallback? = null
    private var pointerCount = 0
    private var startX = 0f
    private var startY = 0f
    private var tracking = false
    private var gestureStarted = false

    private val threshold by lazy { 80 * resources.displayMetrics.density }

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val trailPath = Path()
    private var trailPoints = mutableListOf<PointF>()

    fun setGestureCallback(cb: GestureCallback) {
        this.callback = cb
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val count = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerCount = 1
                startX = event.x
                startY = event.y
                tracking = true
                gestureStarted = false
                trailPoints.clear()
                trailPath.reset()
                trailPoints.add(PointF(event.x, event.y))
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerCount = count
                if (count >= 3) {
                    startX = getAvgX(event)
                    startY = getAvgY(event)
                    tracking = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (tracking && pointerCount >= 3) {
                    trailPoints.add(PointF(getAvgX(event), getAvgY(event)))
                    invalidate()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (tracking && pointerCount >= 3 && !gestureStarted) {
                    val endX = getAvgX(event)
                    val endY = getAvgY(event)
                    val dx = endX - startX
                    val dy = endY - startY
                    val dist = min(abs(dx), abs(dy))

                    if (dist > threshold) {
                        gestureStarted = true
                        when (pointerCount) {
                            3 -> handleThreeFingerGesture(dx, dy)
                            4 -> handleFourFingerGesture(dx, dy)
                        }
                    }
                }
                pointerCount = count - 1
                if (pointerCount < 3) {
                    tracking = false
                    trailPoints.clear()
                    trailPath.reset()
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerCount = 0
                tracking = false
                gestureStarted = false
                trailPoints.clear()
                trailPath.reset()
                invalidate()
            }
        }
        return true
    }

    private fun handleThreeFingerGesture(dx: Float, dy: Float) {
        if (abs(dx) > abs(dy)) {
            if (dx > 0) callback?.onThreeFingerSwipeRight()
            else callback?.onThreeFingerSwipeLeft()
        } else {
            if (dy > 0) callback?.onFourFingerSwipeDown()
            else callback?.onFourFingerSwipeUp()
        }
    }

    private fun handleFourFingerGesture(dx: Float, dy: Float) {
        if (abs(dx) > abs(dy)) {
            if (dx > 0) callback?.onThreeFingerSwipeRight()
            else callback?.onThreeFingerSwipeLeft()
        } else {
            if (dy > 0) callback?.onFourFingerSwipeDown()
            else callback?.onFourFingerSwipeUp()
        }
    }

    private fun getAvgX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getX(i)
        }
        return sum / event.pointerCount
    }

    private fun getAvgY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getY(i)
        }
        return sum / event.pointerCount
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trailPoints.size < 2) return

        trailPath.reset()
        trailPath.moveTo(trailPoints[0].x, trailPoints[0].y)
        for (i in 1 until trailPoints.size) {
            trailPath.lineTo(trailPoints[i].x, trailPoints[i].y)
        }
        canvas.drawPath(trailPath, trailPaint)

        val lastPoint = trailPoints.last()
        canvas.drawCircle(lastPoint.x, lastPoint.y, 6f, trailPaint)
    }
}
