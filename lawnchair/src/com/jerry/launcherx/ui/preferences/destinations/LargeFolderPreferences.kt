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
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jerry.launcherx.modern.folder.LargeFolderBinder
import com.jerry.launcherx.modern.folder.LargeFolderGrid
import com.jerry.launcherx.preferences.PreferenceAdapter
import com.jerry.launcherx.preferences.customPreferenceAdapter
import com.jerry.launcherx.ui.preferences.LocalIsExpandedScreen
import com.jerry.launcherx.ui.preferences.components.colorpreference.ColorDot
import com.jerry.launcherx.ui.preferences.components.colorpreference.ColorPreferenceEntry
import com.jerry.launcherx.ui.preferences.components.colorpreference.pickers.CustomColorPicker
import com.jerry.launcherx.ui.preferences.components.colorpreference.pickers.SwatchGrid
import com.jerry.launcherx.ui.preferences.components.controls.ListPreference
import com.jerry.launcherx.ui.preferences.components.controls.ListPreferenceEntry
import com.jerry.launcherx.ui.preferences.components.controls.SliderPreference
import com.jerry.launcherx.ui.preferences.components.controls.SwitchPreference
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceGroup
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceLayout
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R

internal const val PREFS_NAME = "modern_folder_prefs"
private const val KEY_BG_OPACITY = "large_folder_bg_opacity"
private const val KEY_ICON_SCALE = "large_folder_icon_scale"
private const val KEY_BG_PADDING = "large_folder_bg_padding"
private const val KEY_BG_INSET = "large_folder_bg_inset"
private const val KEY_BG_COLOR = "large_folder_bg_color"
internal const val KEY_FROSTED_GLASS = "large_folder_frosted_glass"
internal const val KEY_LIQUID_GLASS = "large_folder_liquid_glass"
internal const val KEY_GLASS_BLUR = "large_folder_glass_blur"
private const val KEY_ICON_GAP = "large_folder_icon_gap"

private const val DEFAULT_BG_OPACITY = 0.9F
private const val DEFAULT_ICON_SCALE = LargeFolderBinder.DEFAULT_ICON_SCALE
private const val DEFAULT_BG_PADDING = 12F
private const val DEFAULT_BG_INSET = 0F
private const val DEFAULT_BG_COLOR = 0xFFFFFFFF.toInt()
private const val DEFAULT_ICON_GAP = 4F
private const val DEFAULT_ALIGN_H = "flex-start"
private const val DEFAULT_ALIGN_V = "flex-start"
private const val DEFAULT_SHOW_NAME = true

private const val MAX_BG_PADDING = 24F
private const val MAX_BG_INSET = 16F
private const val MAX_ICON_GAP = 16F

@Composable
fun LargeFolderPreferences(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(id = R.string.large_folder_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val sharedPrefs = remember {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            SwitchPreference(
                label = stringResource(id = R.string.large_folder_show_name_label),
                adapter = rememberBooleanPreference(
                    sharedPrefs, LargeFolderBinder.KEY_SHOW_NAME, DEFAULT_SHOW_NAME,
                ),
            )
            SliderPreference(
                label = stringResource(id = R.string.large_folder_bg_opacity_label),
                adapter = rememberFloatPreference(sharedPrefs, KEY_BG_OPACITY, DEFAULT_BG_OPACITY),
                step = 0.1F,
                valueRange = 0.3F..1.0F,
                showAsPercentage = true,
            )
            LargeFolderColorPreference(
                label = stringResource(id = R.string.large_folder_bg_color_label),
                prefs = sharedPrefs,
                key = KEY_BG_COLOR,
                defaultColor = DEFAULT_BG_COLOR,
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.large_folder_spacing_label)) {
            SliderPreference(
                label = stringResource(id = R.string.large_folder_bg_padding_label),
                adapter = rememberFloatPreference(sharedPrefs, KEY_BG_PADDING, DEFAULT_BG_PADDING),
                step = 1F,
                valueRange = 0F..MAX_BG_PADDING,
            )
            SliderPreference(
                label = stringResource(id = R.string.large_folder_bg_inset_label),
                adapter = rememberFloatPreference(sharedPrefs, KEY_BG_INSET, DEFAULT_BG_INSET),
                step = 1F,
                valueRange = 0F..MAX_BG_INSET,
            )
            SliderPreference(
                label = stringResource(id = R.string.large_folder_icon_gap_label),
                adapter = rememberFloatPreference(sharedPrefs, KEY_ICON_GAP, DEFAULT_ICON_GAP),
                step = 1F,
                valueRange = 0F..MAX_ICON_GAP,
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.large_folder_icon_scale_label),
                adapter = rememberFloatPreference(sharedPrefs, KEY_ICON_SCALE, DEFAULT_ICON_SCALE),
                step = 0.1F,
                valueRange = 0.6F..1.4F,
                showAsPercentage = true,
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.large_folder_layout_label)) {
            ListPreference(
                label = stringResource(id = R.string.large_folder_align_h_label),
                adapter = rememberStringPreference(
                    sharedPrefs, LargeFolderBinder.KEY_ALIGN_H, DEFAULT_ALIGN_H,
                ),
                entries = alignEntries(),
            )
            ListPreference(
                label = stringResource(id = R.string.large_folder_align_v_label),
                adapter = rememberStringPreference(
                    sharedPrefs, LargeFolderBinder.KEY_ALIGN_V, DEFAULT_ALIGN_V,
                ),
                entries = alignEntries(),
            )
            ListPreference(
                label = stringResource(id = R.string.large_folder_max_span_label),
                adapter = rememberIntPreference(
                    sharedPrefs,
                    LargeFolderBinder.KEY_MAX_SPAN,
                    LargeFolderGrid.DEFAULT_MAX_SPAN,
                ),
                entries = maxSpanEntries(),
            )
            ListPreference(
                label = stringResource(id = R.string.large_folder_swiper_label),
                adapter = rememberStringPreference(
                    sharedPrefs,
                    LargeFolderBinder.KEY_SWIPER_MODE,
                    LargeFolderBinder.SWIPER_OFF,
                ),
                entries = swiperEntries(),
            )
        }
    }
}

private val largeFolderSwatches = listOf(
    0xFFFFFFFF.toInt(), // white
    0xFFF5F5F5.toInt(), // light gray
    0xFFE8EAF6.toInt(), // light indigo
    0xFFE3F2FD.toInt(), // light blue
    0xFFE8F5E9.toInt(), // light green
    0xFFFFF3E0.toInt(), // light orange
    0xFFFFEBEE.toInt(), // light red
    0xFFF3E5F5.toInt(), // light purple
    0xFF263238.toInt(), // dark blue-gray
    0xFF37474F.toInt(), // darker blue-gray
    0xFF1A237E.toInt(), // indigo
    0xFF0D47A1.toInt(), // blue
    0xFF1B5E20.toInt(), // green
    0xFFE65100.toInt(), // orange
    0xFFB71C1C.toInt(), // red
    0xFF4A148C.toInt(), // purple
)

@Composable
private fun LargeFolderColorPreference(
    label: String,
    prefs: SharedPreferences,
    key: String,
    defaultColor: Int,
) {
    val color = remember(key) { mutableIntStateOf(prefs.getInt(key, defaultColor)) }
    var showDialog = remember { mutableStateOf(false) }

    PreferenceTemplate(
        title = { Text(text = label) },
        endWidget = {
            ColorDot(
                ColorPreferenceEntry(
                    value = color.intValue,
                    label = { "" },
                    lightColor = { color.intValue },
                ),
            )
        },
        onClick = { showDialog.value = true },
    )

    if (showDialog.value) {
        var tempColor = remember { mutableIntStateOf(color.intValue) }
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text(text = label) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SwatchGrid(
                        entries = largeFolderSwatches.mapIndexed { index, argb ->
                            ColorPreferenceEntry(
                                value = argb,
                                label = { "Swatch ${index + 1}" },
                                lightColor = { argb },
                            )
                        },
                        onSwatchClick = { tempColor.intValue = it },
                        isSwatchSelected = { it == tempColor.intValue },
                    )
                    CustomColorPicker(
                        selectedColor = tempColor.intValue,
                        onSelect = { tempColor.intValue = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        color.intValue = tempColor.intValue
                        prefs.edit().putInt(key, tempColor.intValue).apply()
                        showDialog.value = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog.value = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun alignEntries(): List<ListPreferenceEntry<String>> = listOf(
    ListPreferenceEntry(
        value = "flex-start",
        label = { stringResource(R.string.large_folder_align_flex_start) },
    ),
    ListPreferenceEntry(
        value = "flex-end",
        label = { stringResource(R.string.large_folder_align_flex_end) },
    ),
    ListPreferenceEntry(
        value = "center",
        label = { stringResource(R.string.large_folder_align_center) },
    ),
    ListPreferenceEntry(
        value = "space-between",
        label = { stringResource(R.string.large_folder_align_space_between) },
    ),
    ListPreferenceEntry(
        value = "space-around",
        label = { stringResource(R.string.large_folder_align_space_around) },
    ),
)

@Composable
private fun maxSpanEntries(): List<ListPreferenceEntry<Int>> =
    (LargeFolderGrid.MIN_MAX_SPAN..LargeFolderGrid.MAX_MAX_SPAN).map { span ->
        ListPreferenceEntry(
            value = span,
            label = { "${span}x$span" },
        )
    }

@Composable
private fun swiperEntries(): List<ListPreferenceEntry<String>> = listOf(
    ListPreferenceEntry(
        value = LargeFolderBinder.SWIPER_OFF,
        label = { stringResource(R.string.large_folder_swiper_off) },
    ),
    ListPreferenceEntry(
        value = LargeFolderBinder.SWIPER_HORIZONTAL,
        label = { stringResource(R.string.large_folder_swiper_horizontal) },
    ),
    ListPreferenceEntry(
        value = LargeFolderBinder.SWIPER_VERTICAL,
        label = { stringResource(R.string.large_folder_swiper_vertical) },
    ),
)

@Composable
internal fun rememberFloatPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: Float,
): PreferenceAdapter<Float> {
    val value = remember(key) { mutableFloatStateOf(prefs.getFloat(key, defaultValue)) }
    return customPreferenceAdapter(
        value = value.floatValue,
        onValueChange = { newValue ->
            value.floatValue = newValue
            prefs.edit().putFloat(key, newValue).apply()
        },
    )
}

@Composable
private fun rememberStringPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: String,
): PreferenceAdapter<String> {
    val value = remember(key) {
        mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue)
    }
    return customPreferenceAdapter(
        value = value.value,
        onValueChange = { newValue ->
            value.value = newValue
            prefs.edit().putString(key, newValue).apply()
        },
    )
}

@Composable
internal fun rememberBooleanPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: Boolean,
): PreferenceAdapter<Boolean> {
    val value = remember(key) { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    return customPreferenceAdapter(
        value = value.value,
        onValueChange = { newValue ->
            value.value = newValue
            prefs.edit().putBoolean(key, newValue).apply()
        },
    )
}

@Composable
private fun rememberIntPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: Int,
): PreferenceAdapter<Int> {
    val value = remember(key) { mutableIntStateOf(prefs.getInt(key, defaultValue)) }
    return customPreferenceAdapter(
        value = value.intValue,
        onValueChange = { newValue ->
            value.intValue = newValue
            prefs.edit().putInt(key, newValue).apply()
        },
    )
}
