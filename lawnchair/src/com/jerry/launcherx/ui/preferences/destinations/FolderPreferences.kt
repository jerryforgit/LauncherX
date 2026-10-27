/*
 * Copyright 2021, Lawnchair
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jerry.launcherx.preferences.getAdapter
import com.jerry.launcherx.preferences.preferenceManager
import com.jerry.launcherx.preferences2.preferenceManager2
import com.jerry.launcherx.ui.preferences.LocalIsExpandedScreen
import com.jerry.launcherx.ui.preferences.components.NavigationActionPreference
import com.jerry.launcherx.ui.preferences.components.colorpreference.ColorPreference
import com.jerry.launcherx.ui.preferences.components.controls.SliderPreference
import com.jerry.launcherx.ui.preferences.components.controls.SwitchPreference
import com.jerry.launcherx.ui.preferences.components.layout.ExpandAndShrink
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceGroup
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceLayout
import com.jerry.launcherx.ui.preferences.navigation.GeneralIconShape
import com.jerry.launcherx.ui.preferences.navigation.LargeFolders
import com.android.launcher3.R

@Composable
fun FolderPreferences(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(id = R.string.folders_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val prefs = preferenceManager()
        val prefs2 = preferenceManager2()
        val folderIconShapeAdapter = prefs2.folderShape.getAdapter()
        val folderIconShapeSubtitle = iconShapeEntries(context)
            .firstOrNull { it.value == folderIconShapeAdapter.state.value }
            ?.label?.invoke()
            ?: stringResource(id = R.string.custom)
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            NavigationActionPreference(
                label = stringResource(id = R.string.folder_shape_label),
                destination = GeneralIconShape(ShapeRoute.FOLDER_SHAPE),
                subtitle = folderIconShapeSubtitle,
                endWidget = {
                    IconShapePreview(iconShape = folderIconShapeAdapter.state.value)
                },
            )
            ColorPreference(preference = prefs2.folderColor)
            SliderPreference(
                label = stringResource(id = R.string.folder_preview_bg_opacity_label),
                adapter = prefs2.folderPreviewBackgroundOpacity.getAdapter(),
                step = 0.1F,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
            SliderPreference(
                label = stringResource(id = R.string.folder_preview_margin_label),
                adapter = prefs2.folderPreviewMargin.getAdapter(),
                step = 1F,
                valueRange = 0F..16F,
                showUnit = "dp",
            )
            SliderPreference(
                label = stringResource(id = R.string.folder_bg_opacity_label),
                adapter = prefs2.folderBackgroundOpacity.getAdapter(),
                step = 0.1F,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
        }

        val popupFolderBgBlurAdapter = prefs2.folderPopupBackgroundBlur.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.folder_group_popup_folder)) {
            SwitchPreference(
                adapter = popupFolderBgBlurAdapter,
                label = stringResource(id = R.string.folder_popup_bg_blur_label),
            )
            ExpandAndShrink(visible = popupFolderBgBlurAdapter.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.folder_popup_bg_blur_strength_label),
                    adapter = prefs2.folderPopupBackgroundBlurStrength.getAdapter(),
                    step = 1F,
                    valueRange = 2F..20F,
                    showUnit = "dp",
                )
            }
            SwitchPreference(
                adapter = prefs2.folderOpenCentered.getAdapter(),
                label = stringResource(id = R.string.folder_open_centered_label),
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            SliderPreference(
                label = stringResource(id = R.string.max_folder_columns),
                adapter = prefs2.folderColumns.getAdapter(),
                step = 1,
                valueRange = 2..5,
            )
            SliderPreference(
                label = stringResource(id = R.string.max_folder_rows),
                adapter = prefs.folderRows.getAdapter(),
                step = 1,
                valueRange = 2..5,
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SwitchPreference(
                adapter = prefs2.showFolderDesktopLabel.getAdapter(),
                label = stringResource(id = R.string.folder_desktop_label),
            )
            val homeScreenLabelsAdapter = prefs2.showIconLabelsOnHomeScreenFolder.getAdapter()
            SwitchPreference(
                adapter = homeScreenLabelsAdapter,
                label = stringResource(id = R.string.show_labels),
            )
            ExpandAndShrink(visible = homeScreenLabelsAdapter.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs2.homeIconLabelFolderSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
        }

        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(id = R.string.large_folder_label),
                destination = LargeFolders,
                subtitle = stringResource(id = R.string.large_folder_description),
            )
        }
    }
}
