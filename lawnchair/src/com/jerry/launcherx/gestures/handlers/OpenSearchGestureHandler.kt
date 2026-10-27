package com.jerry.launcherx.gestures.handlers

import android.content.Context
import com.jerry.launcherx.LauncherXLauncher
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.jerry.launcherx.qsb.LawnQsbLayout

class OpenSearchGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LauncherXLauncher) {
        val prefs = PreferenceManager2.getInstance(launcher)
        val searchProvider = LawnQsbLayout.getSearchProvider(launcher, prefs)
        searchProvider.launch(launcher)
    }
}
