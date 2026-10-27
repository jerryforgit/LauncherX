package com.jerry.launcherx.ui.preferences.destinations

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceLayout
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceTemplate

@Composable
fun DummyPreference(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = "",
        backArrowVisible = false,
        modifier = modifier,
    ) {
        PreferenceTemplate(title = {
            Text("Tap a preference in the left hand menu")
        })
    }
}
