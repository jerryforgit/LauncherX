package com.jerry.launcherx.modern.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.PopupMenu

class DesktopInputAdapter(private val context: Context) {

    interface DesktopInputCallback {
        fun onRightClick(view: View, x: Float, y: Float) {}
        fun onMouseHover(view: View, x: Float, y: Float) {}
        fun onMouseDrag(view: View, fromX: Float, fromY: Float, toX: Float, toY: Float) {}
        fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = false
        fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false
    }

    private var callback: DesktopInputCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isMouseConnected = false
    private var isKeyboardConnected = false
    private var cursorX = 0f
    private var cursorY = 0f

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }
    private val cursorPath = Path()

    fun setCallback(cb: DesktopInputCallback) {
        this.callback = cb
    }

    fun isMouseActive(): Boolean = isMouseConnected
    fun isKeyboardActive(): Boolean = isKeyboardConnected

    fun onInputDeviceAdded(device: InputDevice) {
        when {
            device.isVirtual -> return
            device.supportsSource(InputDevice.SOURCE_MOUSE) -> {
                isMouseConnected = true
            }
            device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC -> {
                isKeyboardConnected = true
            }
        }
    }

    fun onInputDeviceRemoved(device: InputDevice) {
        when {
            device.supportsSource(InputDevice.SOURCE_MOUSE) -> {
                isMouseConnected = false
            }
            device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC -> {
                isKeyboardConnected = false
            }
        }
    }

    fun shouldOptimizeForDesktop(): Boolean {
        return isMouseConnected || isKeyboardConnected
    }

    fun getContextMenu(view: View, x: Float, y: Float): PopupMenu {
        return PopupMenu(context, view).apply {
            menu.add(0, 1, 0, "App Info")
            menu.add(0, 2, 1, "Uninstall")
            menu.add(0, 3, 2, "Remove")
            menu.add(0, 4, 3, "Edit")
            menu.add(0, 5, 4, "Resize Widget")
            menu.add(0, 6, 5, "Add to Folder")
        }
    }

    fun onHoverEvent(event: MotionEvent): Boolean {
        cursorX = event.x
        cursorY = event.y
        callback?.onMouseHover(
            event.source as? View ?: return false,
            event.x, event.y
        )
        return true
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_MOUSE != 0) {
            when (event.action) {
                MotionEvent.ACTION_HOVER_MOVE -> {
                    callback?.onMouseHover(
                        event.source as? View ?: return false,
                        event.x, event.y
                    )
                    return true
                }
                MotionEvent.ACTION_SCROLL -> {
                    val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                    handleScroll(vScroll, hScroll)
                    return true
                }
            }
        }
        return false
    }

    private fun handleScroll(vertical: Float, horizontal: Float) {
        // delegate to workspace scrolling
    }

    fun drawCursor(canvas: Canvas, x: Float, y: Float) {
        if (!isMouseConnected) return

        cursorPath.reset()
        cursorPath.moveTo(x, y)
        cursorPath.lineTo(x, y + 18)
        cursorPath.lineTo(x + 5, y + 14)
        cursorPath.lineTo(x + 10, y + 22)
        cursorPath.lineTo(x + 13, y + 20)
        cursorPath.lineTo(x + 8, y + 12)
        cursorPath.lineTo(x + 14, y + 12)
        cursorPath.close()

        canvas.drawPath(cursorPath, cursorPaint)
    }
}
