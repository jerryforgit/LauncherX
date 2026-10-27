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

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jerry.launcherx.preferences.PreferenceManager
import com.jerry.launcherx.preferences.getAdapter
import com.jerry.launcherx.preferences.preferenceManager
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.jerry.launcherx.preferences2.preferenceManager2
import com.jerry.launcherx.ui.preferences.LocalIsExpandedScreen
import com.jerry.launcherx.ui.preferences.components.DummyLauncherBox
import com.jerry.launcherx.ui.preferences.components.DummyLauncherLayout
import com.jerry.launcherx.ui.preferences.components.PermissionDialog
import com.jerry.launcherx.ui.preferences.components.WallpaperPreview
import com.jerry.launcherx.ui.preferences.components.WithWallpaper
import com.jerry.launcherx.ui.preferences.components.clipToBottomPercentage
import com.jerry.launcherx.ui.preferences.components.colorpreference.ColorPreference
import com.jerry.launcherx.ui.preferences.components.controls.ListPreference
import com.jerry.launcherx.ui.preferences.components.controls.ListPreferenceEntry
import com.jerry.launcherx.ui.preferences.components.controls.MainSwitchPreference
import com.jerry.launcherx.ui.preferences.components.controls.SliderPreference
import com.jerry.launcherx.ui.preferences.components.controls.SwitchPreference
import com.jerry.launcherx.ui.preferences.components.controls.WarningPreference
import com.jerry.launcherx.ui.preferences.components.createPreviewIdp
import com.jerry.launcherx.ui.preferences.components.layout.DividerColumn
import com.jerry.launcherx.ui.preferences.components.layout.ExpandAndShrink
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceGroup
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceGroupHeading
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return try {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

@Composable
fun DockPreferences(modifier: Modifier = Modifier) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    PreferenceLayout(
        label = stringResource(id = R.string.dock_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val hotseatBgAdapter = prefs.hotseatBG.getAdapter()
        val hotseatCapsuleAdapter = prefs.hotseatCapsuleBg.getAdapter()

        MainSwitchPreference(adapter = prefs2.isHotseatEnabled.getAdapter(), label = stringResource(id = R.string.show_hotseat_title)) {
            DockPreferencesPreview()
            PreferenceGroup(heading = stringResource(id = R.string.style)) {
                SwitchPreference(
                    adapter = hotseatCapsuleAdapter,
                    label = stringResource(id = R.string.hotseat_capsule_bg),
                )
                ExpandAndShrink(
                    visible = hotseatCapsuleAdapter.state.value,
                ) {
                    CapsuleBackgroundSettings(prefs, prefs2)
                }
                SwitchPreference(
                    adapter = hotseatBgAdapter,
                    label = stringResource(id = R.string.hotseat_background),
                )
                ExpandAndShrink(
                    visible = hotseatBgAdapter.state.value,
                ) {
                    HotseatBackgroundSettings(prefs, prefs2)
                }
            }
            SearchBarPreference(SearchRoute.DOCK_SEARCH)
            GridSettings(prefs, prefs2)
            PreferenceGroup(heading = stringResource(id = R.string.icons)) {
                SwitchPreference(
                    adapter = prefs2.enableLabelInDock.getAdapter(),
                    label = stringResource(id = R.string.show_labels),
                )
            }
            val hotseatRecentAppsAdapter = prefs.hotseatRecentAppsEnabled.getAdapter()
            val context = LocalContext.current
            var hasUsageAccess by remember { mutableStateOf(hasUsageStatsPermission(context)) }
            var showUsageAccessDialog by remember { mutableStateOf(false) }
            LifecycleResumeEffect(Unit) {
                hasUsageAccess = hasUsageStatsPermission(context)
                onPauseOrDispose { }
            }
            PreferenceGroup(heading = stringResource(id = R.string.hotseat_recent_apps_title)) {
                SwitchPreference(
                    checked = hotseatRecentAppsAdapter.state.value,
                    onCheckedChange = { newValue ->
                        if (!newValue || hasUsageAccess) {
                            hotseatRecentAppsAdapter.onChange(newValue)
                        } else {
                            showUsageAccessDialog = true
                        }
                    },
                    label = stringResource(id = R.string.hotseat_recent_apps_title),
                )
                if (hotseatRecentAppsAdapter.state.value && !hasUsageAccess) {
                    WarningPreference(
                        text = stringResource(
                            id = R.string.missing_usage_access_desc,
                            stringResource(id = R.string.derived_app_name),
                        ),
                    )
                }
                ExpandAndShrink(
                    visible = hotseatRecentAppsAdapter.state.value,
                ) {
                    DividerColumn {
                        SliderPreference(
                            label = stringResource(id = R.string.hotseat_recent_apps_max_count),
                            adapter = prefs.hotseatRecentAppsMaxCount.getAdapter(),
                            step = 1,
                            valueRange = 1..8,
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.hotseat_recent_apps_spacing),
                            adapter = prefs.hotseatRecentAppsSpacing.getAdapter(),
                            step = 1,
                            valueRange = 0..48,
                            showUnit = "dp",
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.hotseat_recent_apps_padding_left),
                            adapter = prefs.hotseatRecentAppsPaddingLeft.getAdapter(),
                            step = 1,
                            valueRange = 0..48,
                            showUnit = "dp",
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.hotseat_recent_apps_padding_right),
                            adapter = prefs.hotseatRecentAppsPaddingRight.getAdapter(),
                            step = 1,
                            valueRange = 0..48,
                            showUnit = "dp",
                        )
                        val dividerEnabledAdapter = prefs.hotseatRecentAppsDividerEnabled.getAdapter()
                        SwitchPreference(
                            label = stringResource(id = R.string.hotseat_recent_apps_divider_enabled),
                            adapter = dividerEnabledAdapter,
                        )
                        ExpandAndShrink(
                            visible = dividerEnabledAdapter.state.value,
                        ) {
                            androidx.compose.foundation.layout.Column {
                                ColorPreference(preference = prefs2.hotseatRecentAppsDividerColor)
                                SliderPreference(
                                    label = stringResource(id = R.string.hotseat_recent_apps_divider_alpha),
                                    adapter = prefs.hotseatRecentAppsDividerAlpha.getAdapter(),
                                    step = 1,
                                    valueRange = 0..100,
                                    showUnit = "%",
                                )
                                SliderPreference(
                                    label = stringResource(id = R.string.hotseat_recent_apps_divider_height),
                                    adapter = prefs.hotseatRecentAppsDividerHeightPercent.getAdapter(),
                                    step = 1,
                                    valueRange = 0..100,
                                    showUnit = "%",
                                )
                            }
                        }
                    }
                }
            }
            if (showUsageAccessDialog) {
                PermissionDialog(
                    title = stringResource(id = R.string.missing_usage_access_label),
                    text = stringResource(
                        id = R.string.missing_usage_access_desc,
                        stringResource(id = R.string.derived_app_name),
                    ),
                    isPermanentlyDenied = true,
                    onConfirm = {},
                    onDismiss = { showUsageAccessDialog = false },
                    onGoToSettings = {
                        showUsageAccessDialog = false
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun CapsuleBackgroundSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {
    DividerColumn {
        ColorPreference(preference = prefs2.hotseatBackgroundColor)
        SliderPreference(
            label = stringResource(id = R.string.hotseat_capsule_elevation),
            adapter = prefs.hotseatCapsuleElevation.getAdapter(),
            step = 1f,
            valueRange = 0f..30f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_capsule_margin_left),
            adapter = prefs.hotseatCapsuleMarginLeft.getAdapter(),
            step = 1f,
            valueRange = 0f..40f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_capsule_margin_right),
            adapter = prefs.hotseatCapsuleMarginRight.getAdapter(),
            step = 1f,
            valueRange = 0f..40f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_capsule_margin_top),
            adapter = prefs.hotseatCapsuleMarginTop.getAdapter(),
            step = 1f,
            valueRange = 0f..40f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_capsule_margin_bottom),
            adapter = prefs.hotseatCapsuleMarginBottom.getAdapter(),
            step = 1f,
            valueRange = 0f..40f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_capsule_inner_padding_top),
            adapter = prefs.hotseatCapsuleInnerPaddingTop.getAdapter(),
            step = 1f,
            valueRange = 0f..32f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_capsule_inner_padding_bottom),
            adapter = prefs.hotseatCapsuleInnerPaddingBottom.getAdapter(),
            step = 1f,
            valueRange = 0f..32f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_capsule_corner_radius),
            adapter = prefs.hotseatCapsuleCornerRadius.getAdapter(),
            step = 1f,
            valueRange = 0f..40f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_alpha),
            adapter = prefs.hotseatBGAlpha.getAdapter(),
            step = 5,
            valueRange = 5..100,
            showUnit = "%",
        )
    }
}

@Composable
fun HotseatBackgroundSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {
    DividerColumn {
        ColorPreference(preference = prefs2.hotseatBackgroundColor)
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_corner_radius),
            adapter = prefs2.hotseatBackgroundCornerRadius.getAdapter(),
            step = 1f,
            valueRange = 0f..100f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_alpha),
            adapter = prefs.hotseatBGAlpha.getAdapter(),
            step = 5,
            valueRange = 5..100,
            showUnit = "%",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_left),
            adapter = prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_right),
            adapter = prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_top),
            adapter = prefs.hotseatBGVerticalInsetTop.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_bottom),
            adapter = prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
    }
}

@Composable
fun GridSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {
    val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
    val hotseatColumnsAdapter = prefs.hotseatColumns.getAdapter()
    val hotseatColumnsUnfoldedAdapter = prefs.hotseatColumnsUnfolded.getAdapter()
    val hotseatRowsAdapter = prefs.hotseatRows.getAdapter()
    val dockPagesAdapter = prefs.dockPages.getAdapter()
    val dockWidthModeAdapter = prefs.hotseatDockWidthMode.getAdapter()
    val configuration = LocalConfiguration.current
    val isPhone = configuration.smallestScreenWidthDp < 600
    // Keep the dock row to at most 20 slots. On a typical 2944px tablet that is still
    // 20 * iconSize + 19 * spacing wide; going beyond ~26 slots would overlap icons at the
    // default icon size, so 20 is the safe ceiling (and matches the grid-size maximum).
    val maxDockIcons = 20
    val fullWidthDock = dockWidthModeAdapter.state.value == "full" ||
        (dockWidthModeAdapter.state.value == "auto" && isPhone)

    PreferenceGroup(heading = stringResource(id = R.string.grid)) {
        ListPreference(
            label = stringResource(id = R.string.dock_width_mode),
            adapter = dockWidthModeAdapter,
            entries = dockWidthModeEntries(),
        )
        if (isFoldable) {
            SliderPreference(
                label = stringResource(id = R.string.state_folded, stringResource(id = R.string.dock_icons)),
                adapter = hotseatColumnsAdapter,
                step = 1,
                valueRange = 3..maxDockIcons,
            )
            SliderPreference(
                label = stringResource(id = R.string.state_unfolded, stringResource(id = R.string.dock_icons)),
                adapter = hotseatColumnsUnfoldedAdapter,
                step = 1,
                valueRange = 3..maxDockIcons,
            )
            ExpandAndShrink(
                visible = hotseatColumnsAdapter.state.value > hotseatColumnsUnfoldedAdapter.state.value,
            ) {
                WarningPreference(
                    text = stringResource(id = R.string.foldable_columns_error),
                )
            }
        } else {
            SliderPreference(
                label = stringResource(id = R.string.dock_icons),
                adapter = hotseatColumnsAdapter,
                step = 1,
                valueRange = 3..maxDockIcons,
            )
        }
        SliderPreference(
            label = stringResource(id = R.string.dock_rows),
            adapter = hotseatRowsAdapter,
            step = 1,
            valueRange = 1..2,
        )
        SliderPreference(
            label = stringResource(id = R.string.dock_pages),
            adapter = dockPagesAdapter,
            step = 1,
            valueRange = 1..5,
        )
        SliderPreference(
            label = stringResource(id = R.string.dock_bar_app_spacing),
            adapter = prefs.hotseatAppSpacing.getAdapter(),
            step = 1f,
            valueRange = 0f..32f,
            showUnit = "dp",
            enabled = !fullWidthDock,
        )
        SliderPreference(
            label = stringResource(id = R.string.dock_bar_left_padding),
            adapter = prefs.hotseatCapsuleInnerPaddingLeft.getAdapter(),
            step = 1f,
            valueRange = 0f..32f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.dock_bar_right_padding),
            adapter = prefs.hotseatCapsuleInnerPaddingRight.getAdapter(),
            step = 1f,
            valueRange = 0f..32f,
            showUnit = "dp",
        )
        SliderPreference(
            adapter = prefs2.hotseatBottomFactor.getAdapter(),
            label = stringResource(id = R.string.hotseat_bottom_space_label),
            valueRange = 0.0F..1.7F,
            step = 0.1F,
            showAsPercentage = true,
        )
        SliderPreference(
            adapter = prefs2.pageIndicatorHeightFactor.getAdapter(),
            label = stringResource(id = R.string.page_indicator_height),
            valueRange = 0.0F..1.0F,
            step = 0.1F,
            showAsPercentage = true,
        )
    }
}

@Composable
private fun dockWidthModeEntries(): List<ListPreferenceEntry<String>> = listOf(
    ListPreferenceEntry(value = "auto") {
        stringResource(R.string.dock_width_auto)
    },
    ListPreferenceEntry(value = "full") {
        stringResource(R.string.dock_width_full)
    },
    ListPreferenceEntry(value = "content") {
        stringResource(R.string.dock_width_content)
    },
)

@Composable
fun ColumnScope.DockPreferencesPreview(modifier: Modifier = Modifier) {
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
        val prefs = preferenceManager()
        val prefs2 = preferenceManager2()

        val hotseatRows = prefs.hotseatRows
        val dockPages = prefs.dockPages

        val adapters = listOf(
            prefs2.hotseatMode.getAdapter(),
            prefs.hotseatColumns.getAdapter(),
            prefs.hotseatColumnsUnfolded.getAdapter(),
            hotseatRows.getAdapter(),
            dockPages.getAdapter(),
            prefs2.themedHotseatQsb.getAdapter(),
            prefs.hotseatQsbCornerRadius.getAdapter(),
            prefs.hotseatQsbAlpha.getAdapter(),
            prefs.hotseatQsbStrokeWidth.getAdapter(),
            prefs2.hotseatBottomFactor.getAdapter(),
            prefs2.strokeColorStyle.getAdapter(),
            prefs2.enableLabelInDock.getAdapter(),
            prefs.hotseatBG.getAdapter(),
            prefs.hotseatCapsuleBg.getAdapter(),
            prefs.hotseatCapsuleElevation.getAdapter(),
            prefs.hotseatCapsuleMarginBottom.getAdapter(),
            prefs.hotseatCapsuleMarginLeft.getAdapter(),
            prefs.hotseatCapsuleMarginRight.getAdapter(),
            prefs.hotseatCapsuleMarginTop.getAdapter(),
            prefs.hotseatCapsuleInnerPaddingLeft.getAdapter(),
            prefs.hotseatCapsuleInnerPaddingRight.getAdapter(),
            prefs.hotseatCapsuleInnerPaddingTop.getAdapter(),
            prefs.hotseatCapsuleInnerPaddingBottom.getAdapter(),
            prefs.hotseatCapsuleCornerRadius.getAdapter(),
            prefs.hotseatCapsuleFrostedGlass.getAdapter(),
            prefs.hotseatCapsuleLiquidGlass.getAdapter(),
            prefs.hotseatCapsuleGlassBlur.getAdapter(),
            prefs.hotseatBGFrostedGlass.getAdapter(),
            prefs.hotseatBGLiquidGlass.getAdapter(),
            prefs.hotseatBGGlassBlur.getAdapter(),
            prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            prefs.hotseatBGVerticalInsetTop.getAdapter(),
            prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            prefs2.pageIndicatorHeightFactor.getAdapter(),
            prefs2.hotseatBackgroundColor.getAdapter(),
            prefs2.hotseatBackgroundCornerRadius.getAdapter(),
            prefs.hotseatBGAlpha.getAdapter(),
        )

        PreferenceGroupHeading(
            heading = stringResource(id = R.string.preview_label),
        )
        DividerColumn(
            modifier = modifier.padding(horizontal = 16.dp),
        ) {
            WithWallpaper { wallpaper ->
                DummyLauncherBox(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterHorizontally)
                        .clip(MaterialTheme.shapes.large)
                        .clipToBottomPercentage(if (hotseatRows.getAdapter().state.value >= 2) .4f else .3f),
                ) {
                    WallpaperPreview(
                        wallpaper = wallpaper,
                        modifier = Modifier.fillMaxSize(),
                    )
                    key(adapters.map { it.state.value }.toTypedArray()) {
                        DummyLauncherLayout(
                            idp = createPreviewIdp { copy(numHotseatColumns = prefs.hotseatColumns.get()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
