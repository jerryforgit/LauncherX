package com.jerry.launcherx.qsb.providers

import com.jerry.launcherx.animateToAllApps
import com.jerry.launcherx.preferences.PreferenceManager
import com.jerry.launcherx.qsb.ThemingMethod
import com.android.launcher3.Launcher
import com.android.launcher3.R

data object StartpageEU : QsbSearchProvider(
    id = "startpage-eu",
    name = R.string.search_provider_startpage_eu,
    icon = R.drawable.ic_startpage,
    themingMethod = ThemingMethod.TINT,
    packageName = "",
    website = "https://eu.startpage.com/?segment=startpage.lawnchair",
    type = QsbSearchProviderType.LOCAL,
    sponsored = false,
) {
    override suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        val prefs = PreferenceManager.getInstance(launcher)
        val useWebSuggestions = prefs.searchResultStartPageSuggestion.get()

        if (useWebSuggestions) {
            launcher.animateToAllApps()
            launcher.appsView.searchUiManager.editText?.showKeyboard()
        } else {
            super.launch(launcher, forceWebsite)
        }
    }
}
