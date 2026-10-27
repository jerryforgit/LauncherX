/*
 * Copyright 2026, Lawnchair
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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jerry.launcherx.modern.folder.GlassBackdrop
import com.jerry.launcherx.preferences.getAdapter
import com.jerry.launcherx.preferences.preferenceManager
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.jerry.launcherx.preferences2.preferenceManager2
import com.jerry.launcherx.ui.preferences.LocalIsExpandedScreen
import com.jerry.launcherx.ui.preferences.components.WallpaperAccessPermissionDialog
import com.jerry.launcherx.ui.preferences.components.controls.ClickablePreference
import com.jerry.launcherx.ui.preferences.components.controls.ListPreference
import com.jerry.launcherx.ui.preferences.components.controls.ListPreferenceEntry
import com.jerry.launcherx.ui.preferences.components.controls.SliderPreference
import com.jerry.launcherx.ui.preferences.components.controls.SwitchPreference
import com.jerry.launcherx.ui.preferences.components.layout.ExpandAndShrink
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceGroup
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceLayout
import com.jerry.launcherx.util.FileAccessManager
import com.jerry.launcherx.util.FileAccessState
import com.android.launcher3.R
import kotlinx.coroutines.launch

private const val LARGE_FOLDER_BLUR_DEFAULT = 28F

@Composable
fun LiquidGlassPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val scope = rememberCoroutineScope()
    val largeFolderPrefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    val liquidEnabledAdapter = prefs2.liquidGlassEnabled.getAdapter()

    val fileAccessManager = remember { FileAccessManager.getInstance(context) }
    val wallpaperAccessState by fileAccessManager.wallpaperAccessState.collectAsStateWithLifecycle()
    val allFilesAccessState by fileAccessManager.allFilesAccessState.collectAsStateWithLifecycle()
    val hasWallpaperAccess = wallpaperAccessState == FileAccessState.Full
    var showWallpaperPermissionDialog by remember { mutableStateOf(false) }

    if (showWallpaperPermissionDialog) {
        WallpaperAccessPermissionDialog(
            managedFilesChecked = allFilesAccessState != FileAccessState.Denied,
            onDismiss = { showWallpaperPermissionDialog = false },
            onPermissionRequest = {
                fileAccessManager.refresh()
                GlassBackdrop.invalidate()
            },
        )
    }

    LifecycleResumeEffect(Unit) {
        showWallpaperPermissionDialog = false
        fileAccessManager.refresh()
        GlassBackdrop.invalidate()
        onPauseOrDispose { }
    }

    PreferenceLayout(
        label = stringResource(id = R.string.liquid_glass_group),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.liquid_glass_group)) {
            SwitchPreference(
                adapter = liquidEnabledAdapter,
                label = stringResource(id = R.string.liquid_glass_master_label),
            )
            if (liquidEnabledAdapter.state.value && !hasWallpaperAccess) {
                ClickablePreference(
                    label = stringResource(id = R.string.liquid_glass_wallpaper_permission_label),
                    subtitle = stringResource(id = R.string.liquid_glass_wallpaper_permission_desc),
                    onClick = { showWallpaperPermissionDialog = true },
                )
            }
            ExpandAndShrink(modifier = Modifier.fillMaxWidth(), visible = liquidEnabledAdapter.state.value) {
                Column {
                    SliderPreference(
                        label = stringResource(id = R.string.liquid_glass_refraction_label),
                        adapter = prefs2.liquidGlassRefraction.getAdapter(),
                        step = 0.05F,
                        valueRange = 0F..2F,
                        showAsPercentage = true,
                    )
                    SliderPreference(
                        label = stringResource(id = R.string.liquid_glass_dispersion_label),
                        adapter = prefs2.liquidGlassDispersion.getAdapter(),
                        step = 0.1F,
                        valueRange = 0F..3F,
                        showAsPercentage = true,
                    )
                    SwitchPreference(
                        adapter = prefs2.liquidGlassSpecularFlow.getAdapter(),
                        label = stringResource(id = R.string.liquid_glass_specular_flow_label),
                    )
                }
            }
        }

        ExpandAndShrink(modifier = Modifier.fillMaxWidth(), visible = liquidEnabledAdapter.state.value) {
            PreferenceGroup(heading = stringResource(id = R.string.glass_apply_to)) {
                SwitchPreference(
                    label = stringResource(id = R.string.folder_group_small_folder),
                    adapter = prefs2.folderLiquidGlass.getAdapter(),
                )
                SwitchPreference(
                    label = stringResource(id = R.string.folder_group_popup_folder),
                    adapter = prefs2.folderPopupLiquidGlass.getAdapter(),
                )
                SwitchPreference(
                    label = stringResource(id = R.string.large_folder_label),
                    adapter = rememberBooleanPreference(
                        largeFolderPrefs,
                        KEY_LIQUID_GLASS,
                        false,
                    ),
                )
                SwitchPreference(
                    label = stringResource(id = R.string.hotseat_capsule_bg),
                    adapter = prefs.hotseatCapsuleLiquidGlass.getAdapter(),
                )
                SwitchPreference(
                    label = stringResource(id = R.string.hotseat_background),
                    adapter = prefs.hotseatBGLiquidGlass.getAdapter(),
                )
            }
        }

        ExpandAndShrink(modifier = Modifier.fillMaxWidth(), visible = liquidEnabledAdapter.state.value) {
            val qualityAdapter = prefs2.liquidGlassQuality.getAdapter()
            val qualityEntries = listOf(
                ListPreferenceEntry(
                    value = PreferenceManager2.LIQUID_GLASS_QUALITY_MAX,
                    label = { stringResource(id = R.string.liquid_glass_quality_max) },
                ),
                ListPreferenceEntry(
                    value = PreferenceManager2.LIQUID_GLASS_QUALITY_HIGH,
                    label = { stringResource(id = R.string.liquid_glass_quality_high) },
                ),
                ListPreferenceEntry(
                    value = PreferenceManager2.LIQUID_GLASS_QUALITY_BALANCED,
                    label = { stringResource(id = R.string.liquid_glass_quality_balanced) },
                ),
                ListPreferenceEntry(
                    value = PreferenceManager2.LIQUID_GLASS_QUALITY_SAVER,
                    label = { stringResource(id = R.string.liquid_glass_quality_saver) },
                ),
            )
            PreferenceGroup(heading = stringResource(id = R.string.liquid_glass_quality_group)) {
                val current = qualityEntries
                    .firstOrNull { it.value == qualityAdapter.state.value }
                    ?.label?.invoke()
                    .orEmpty()
                ListPreference(
                    value = qualityAdapter.state.value,
                    onValueChange = {
                        qualityAdapter.onChange(it)
                        GlassBackdrop.invalidateQuality()
                    },
                    label = stringResource(id = R.string.liquid_glass_quality_label),
                    description = current + "\n" +
                        stringResource(id = R.string.liquid_glass_quality_desc),
                    entries = qualityEntries,
                )
            }
        }

        PreferenceGroup {
            ClickablePreference(
                label = stringResource(id = R.string.restore_defaults),
                confirmationText = stringResource(id = R.string.restore_defaults_confirmation),
                onClick = {
                    scope.launch {
                        prefs2.liquidGlassEnabled.set(true)
                        prefs2.liquidGlassRefraction.set(
                            PreferenceManager2.LIQUID_GLASS_DEFAULT_REFRACTION,
                        )
                        prefs2.liquidGlassDispersion.set(
                            PreferenceManager2.LIQUID_GLASS_DEFAULT_DISPERSION,
                        )
                        prefs2.liquidGlassSpecularFlow.set(
                            PreferenceManager2.LIQUID_GLASS_DEFAULT_SPECULAR_FLOW,
                        )
                        prefs2.liquidGlassQuality.set(
                            PreferenceManager2.LIQUID_GLASS_QUALITY_MAX,
                        )
                        GlassBackdrop.invalidate()
                    }
                },
            )
        }
    }
}

@Composable
fun FrostedGlassPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val largeFolderPrefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    PreferenceLayout(
        label = stringResource(id = R.string.frosted_glass_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val smallFolderFrosted = prefs2.folderFrostedGlass.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.folder_group_small_folder)) {
            SwitchPreference(
                label = stringResource(id = R.string.frosted_glass_label),
                adapter = smallFolderFrosted,
            )
            ExpandAndShrink(modifier = Modifier.fillMaxWidth(), visible = smallFolderFrosted.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.glass_blur_intensity_label),
                    adapter = prefs2.folderGlassBlur.getAdapter(),
                    step = 2F,
                    valueRange = 4F..48F,
                    showUnit = "dp",
                )
            }
        }

        val popupFolderFrosted = prefs2.folderPopupFrostedGlass.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.folder_group_popup_folder)) {
            SwitchPreference(
                label = stringResource(id = R.string.frosted_glass_label),
                adapter = popupFolderFrosted,
            )
            ExpandAndShrink(modifier = Modifier.fillMaxWidth(), visible = popupFolderFrosted.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.glass_blur_intensity_label),
                    adapter = prefs2.folderPopupGlassBlur.getAdapter(),
                    step = 2F,
                    valueRange = 4F..48F,
                    showUnit = "dp",
                )
            }
        }

        val largeFolderFrosted = rememberBooleanPreference(
            largeFolderPrefs,
            KEY_FROSTED_GLASS,
            false,
        )
        PreferenceGroup(heading = stringResource(id = R.string.large_folder_label)) {
            SwitchPreference(
                label = stringResource(id = R.string.frosted_glass_label),
                adapter = largeFolderFrosted,
            )
            ExpandAndShrink(modifier = Modifier.fillMaxWidth(), visible = largeFolderFrosted.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.glass_blur_intensity_label),
                    adapter = rememberFloatPreference(
                        largeFolderPrefs,
                        KEY_GLASS_BLUR,
                        LARGE_FOLDER_BLUR_DEFAULT,
                    ),
                    step = 2F,
                    valueRange = 4F..48F,
                    showUnit = "dp",
                )
            }
        }

        PreferenceGroup(heading = stringResource(id = R.string.dock_label)) {
            val capsuleFrosted = prefs.hotseatCapsuleFrostedGlass.getAdapter()
            SwitchPreference(
                label = stringResource(id = R.string.hotseat_capsule_bg),
                adapter = capsuleFrosted,
            )
            ExpandAndShrink(modifier = Modifier.fillMaxWidth(), visible = capsuleFrosted.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.glass_blur_intensity_label),
                    adapter = prefs.hotseatCapsuleGlassBlur.getAdapter(),
                    step = 2f,
                    valueRange = 4f..48f,
                    showUnit = "dp",
                )
            }

            val hotseatFrosted = prefs.hotseatBGFrostedGlass.getAdapter()
            SwitchPreference(
                label = stringResource(id = R.string.hotseat_background),
                adapter = hotseatFrosted,
            )
            ExpandAndShrink(modifier = Modifier.fillMaxWidth(), visible = hotseatFrosted.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.glass_blur_intensity_label),
                    adapter = prefs.hotseatBGGlassBlur.getAdapter(),
                    step = 2f,
                    valueRange = 4f..48f,
                    showUnit = "dp",
                )
            }
        }
    }
}
