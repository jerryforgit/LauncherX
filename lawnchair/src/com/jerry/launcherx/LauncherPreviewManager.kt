package com.jerry.launcherx

import android.content.Context
import androidx.compose.runtime.RememberObserver
import com.jerry.launcherx.views.LauncherPreviewView
import com.android.launcher3.InvariantDeviceProfile

class LauncherPreviewManager(private val context: Context) : RememberObserver {

    private var activePreview: LauncherPreviewView? = null

    fun createPreviewView(idp: InvariantDeviceProfile): LauncherPreviewView {
        destroyActivePreview()
        activePreview = LauncherPreviewView(context, idp)
        return activePreview!!
    }

    private fun destroyActivePreview() {
        activePreview?.destroy()
    }

    override fun onRemembered() {
    }

    override fun onForgotten() {
        destroyActivePreview()
    }

    override fun onAbandoned() {
        destroyActivePreview()
    }
}
