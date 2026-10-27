package com.jerry.launcherx.gestures.handlers

import android.content.Context
import com.jerry.launcherx.LauncherXLauncher
import com.jerry.launcherx.animateToAllApps

class OpenAppDrawerGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LauncherXLauncher) {
        launcher.animateToAllApps()
    }
}
