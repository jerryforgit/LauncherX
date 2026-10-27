/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jerry.launcherx.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jerry.launcherx.preferences.getAdapter
import com.jerry.launcherx.preferences2.preferenceManager2
import com.jerry.launcherx.ui.preferences.LocalIsExpandedScreen
import com.jerry.launcherx.ui.preferences.components.controls.SwitchPreference
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceGroup
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun HomeScreenEditingPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs2 = preferenceManager2()
    PreferenceLayout(
        label = stringResource(id = R.string.home_screen_editing_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            SwitchPreference(
                adapter = prefs2.editModeOnLongPress.getAdapter(),
                label = stringResource(id = R.string.edit_mode_on_long_press_label),
                description = stringResource(id = R.string.edit_mode_on_long_press_description),
            )
            SwitchPreference(
                adapter = prefs2.editModeIconJiggle.getAdapter(),
                label = stringResource(id = R.string.edit_mode_icon_jiggle_label),
                description = stringResource(id = R.string.edit_mode_icon_jiggle_description),
            )
            SwitchPreference(
                adapter = prefs2.editModeRemoveBadge.getAdapter(),
                label = stringResource(id = R.string.edit_mode_remove_badge_label),
                description = stringResource(id = R.string.edit_mode_remove_badge_description),
            )
            SwitchPreference(
                adapter = prefs2.editModeMultiSelect.getAdapter(),
                label = stringResource(id = R.string.edit_mode_multi_select_label),
                description = stringResource(id = R.string.edit_mode_multi_select_description),
            )
            SwitchPreference(
                adapter = prefs2.editModeSelectionFeedback.getAdapter(),
                label = stringResource(id = R.string.edit_mode_selection_feedback_label),
                description = stringResource(id = R.string.edit_mode_selection_feedback_description),
            )
            SwitchPreference(
                adapter = prefs2.editModePageStrip.getAdapter(),
                label = stringResource(id = R.string.edit_mode_page_strip_label),
                description = stringResource(id = R.string.edit_mode_page_strip_description),
            )
        }
    }
}
