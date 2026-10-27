package com.jerry.launcherx.ui.preferences.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jerry.launcherx.preferences.getAdapter
import com.jerry.launcherx.preferences2.preferenceManager2
import com.jerry.launcherx.ui.preferences.components.controls.ListPreference
import com.jerry.launcherx.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

object HiddenAppsInSearch {
    const val NEVER = "off"
    const val IF_NAME_TYPED = "if_name_typed"
    const val ALWAYS = "on"
}

val hiddenAppsInSearchEntries = sequenceOf(
    ListPreferenceEntry(HiddenAppsInSearch.NEVER) { stringResource(R.string.never_choice) },
    ListPreferenceEntry(HiddenAppsInSearch.IF_NAME_TYPED) { stringResource(R.string.hidden_apps_show_name_typed) },
    ListPreferenceEntry(HiddenAppsInSearch.ALWAYS) { stringResource(R.string.always_choice) },
)

@Composable
fun HiddenAppsInSearchPreference() {
    ListPreference(
        adapter = preferenceManager2().hiddenAppsInSearch.getAdapter(),
        entries = hiddenAppsInSearchEntries.toList(),
        label = stringResource(R.string.show_hidden_apps_in_search_results),
    )
}
