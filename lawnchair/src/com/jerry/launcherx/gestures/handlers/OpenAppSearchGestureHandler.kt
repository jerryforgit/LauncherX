package com.jerry.launcherx.gestures.handlers

import android.content.Context
import com.jerry.launcherx.LauncherXLauncher
import com.jerry.launcherx.animateToAllApps

class OpenAppSearchGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LauncherXLauncher) {
        val searchUiManager = launcher.appsView.searchUiManager
        searchUiManager.setDirectFocus(true)
        searchUiManager.editText?.showKeyboard()
        launcher.animateToAllApps()
    }
}
