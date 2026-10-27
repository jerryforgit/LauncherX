package com.jerry.launcherx.modern.window

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper

class FreeformWindowManager(private val context: Context) {

    data class WindowConfig(
        val packageName: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val isMinimized: Boolean = false,
        val title: String = ""
    )

    interface WindowListener {
        fun onSplitScreenStarted(leftPkg: String, rightPkg: String)
        fun onSplitScreenEnded()
        fun onFreeformWindowCreated(config: WindowConfig)
        fun onFreeformWindowMoved(config: WindowConfig)
        fun onFreeformWindowResized(config: WindowConfig)
        fun onFreeformWindowClosed(packageName: String)
    }

    private var listener: WindowListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val activeWindows = mutableMapOf<String, WindowConfig>()

    fun setListener(l: WindowListener) {
        this.listener = l
    }

    fun startSplitScreen(leftPkg: String, rightPkg: String, horizontal: Boolean = true) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val intent = Intent("android.intent.action.LAZY_SPLIT_SCREEN")
                intent.putExtra("android.intent.extra.INTENT_LEFT", createLaunchIntent(leftPkg))
                intent.putExtra("android.intent.extra.INTENT_RIGHT", createLaunchIntent(rightPkg))
                intent.putExtra("android.intent.extra.HORIZONTAL", horizontal)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                listener?.onSplitScreenStarted(leftPkg, rightPkg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startFreeformWindow(packageName: String, bounds: Rect? = null) {
        val density = context.resources.displayMetrics.density
        val config = WindowConfig(
            packageName = packageName,
            left = bounds?.left ?: (100 * density).toInt(),
            top = bounds?.top ?: (100 * density).toInt(),
            width = bounds?.width() ?: (360 * density).toInt(),
            height = bounds?.height() ?: (540 * density).toInt(),
            title = getAppLabel(packageName)
        )
        try {
            val intent = createLaunchIntent(packageName)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK
            )
            context.startActivity(intent)
            activeWindows[packageName] = config
            listener?.onFreeformWindowCreated(config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun moveFreeformWindow(packageName: String, x: Int, y: Int) {
        val config = activeWindows[packageName] ?: return
        val updated = config.copy(left = x, top = y)
        activeWindows[packageName] = updated
        listener?.onFreeformWindowMoved(updated)
    }

    fun resizeFreeformWindow(packageName: String, width: Int, height: Int) {
        val config = activeWindows[packageName] ?: return
        val updated = config.copy(width = width, height = height)
        activeWindows[packageName] = updated
        listener?.onFreeformWindowResized(updated)
    }

    fun minimizeFreeformWindow(packageName: String) {
        val config = activeWindows[packageName] ?: return
        activeWindows[packageName] = config.copy(isMinimized = true)
    }

    fun closeFreeformWindow(packageName: String) {
        activeWindows.remove(packageName)
        listener?.onFreeformWindowClosed(packageName)
    }

    fun isFreeformActive(packageName: String): Boolean =
        activeWindows.containsKey(packageName)

    fun getActiveWindows(): Map<String, WindowConfig> = activeWindows.toMap()

    private fun createLaunchIntent(packageName: String): Intent {
        val pm = context.packageManager
        return pm.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
