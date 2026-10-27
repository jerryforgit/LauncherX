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

package com.jerry.launcherx.preferences2

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jerry.launcherx.data.Converters
import com.jerry.launcherx.font.FontCache
import com.jerry.launcherx.gestures.config.GestureHandlerConfig
import com.jerry.launcherx.gestures.handlers.SleepMode
import com.jerry.launcherx.gestures.type.GestureType
import com.jerry.launcherx.hotseat.HotseatMode
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import com.jerry.launcherx.predictions.PredictionMode
import com.jerry.launcherx.preferences.PreferenceManager as LauncherXPreferenceManager
import com.jerry.launcherx.qsb.providers.QsbSearchProvider
import com.jerry.launcherx.search.algorithms.LauncherXSearchAlgorithm
import com.jerry.launcherx.search.algorithms.engine.provider.web.WebSearchProvider
import com.jerry.launcherx.smartspace.model.SmartspaceCalendar
import com.jerry.launcherx.smartspace.model.SmartspaceMode
import com.jerry.launcherx.smartspace.model.SmartspaceTimeFormat
import com.jerry.launcherx.theme.color.ColorMode
import com.jerry.launcherx.theme.color.ColorOption
import com.jerry.launcherx.theme.color.ColorStyle
import com.jerry.launcherx.ui.popup.LauncherOptionsPopup
import com.jerry.launcherx.ui.popup.toOptionOrderString
import com.jerry.launcherx.ui.preferences.components.HiddenAppsInSearch
import com.jerry.launcherx.ui.preferences.data.liveinfo.LiveInformationManager
import com.jerry.launcherx.util.kotlinxJson
import com.jerry.launcherx.views.overlay.FullScreenOverlayMode
import com.android.launcher3.BuildConfig
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.ENABLE_TWOLINE_ALLAPPS_TOGGLE
import com.android.launcher3.R
import com.android.launcher3.Workspace
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager as L3ThemeManager
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DynamicResource
import com.android.launcher3.util.SafeCloseable
import com.patrykmichalik.opto.core.PreferenceManager
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

@LauncherAppSingleton
class PreferenceManager2 @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceManager,
    SafeCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resourceProvider = DynamicResource.provider(context)
    private var liveInformationManager: LiveInformationManager =
        LiveInformationManager.getInstance(context)

    private fun idpPreference(
        key: Preferences.Key<Int>,
        defaultSelector: InvariantDeviceProfile.GridOption.() -> Int,
        onSet: (Int) -> Unit = {},
    ): IdpPreference = IdpPreference(
        key = key,
        defaultSelector = defaultSelector,
        preferencesDataStore = preferencesDataStore,
        onSet = onSet,
    )

    override val preferencesDataStore = context.preferencesDataStore

    @Volatile
    private var cachedPreferences: Preferences = runBlocking {
        preferencesDataStore.data.first()
    }

    fun getCachedPreferences(): Preferences = cachedPreferences

    private val reloadHelper = ReloadHelper(context)

    val darkStatusBar = preference(
        key = booleanPreferencesKey(name = "dark_status_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_dark_status_bar),
    )

    val hotseatMode = preference(
        key = stringPreferencesKey("hotseat_mode"),
        defaultValue = HotseatMode.fromString(context.getString(R.string.config_default_hotseat_mode)),
        parse = { HotseatMode.fromString(it) },
        save = { it.toString() },
    )

    val iconShape = preference(
        key = stringPreferencesKey(name = "icon_shape"),
        defaultValue = IconShape.fromString(
            value = context.getString(R.string.config_default_icon_shape),
            context = context,
        ) ?: IconShape.Circle,
        parse = {
            IconShape.fromString(value = it, context = context)
                ?: IconShapeManager.getSystemIconShape(context)
        },
        save = { it.toString() },
        onSet = {
            reloadHelper.reloadIcons()
        },
    )

    val folderShape = preference(
        key = stringPreferencesKey(name = "folder_shape"),
        defaultValue = IconShape.fromString(
            value = context.getString(R.string.config_default_folder_shape),
            context = context,
        ) ?: IconShape.Circle,
        parse = {
            IconShape.fromString(value = it, context = context)
                ?: IconShapeManager.getSystemIconShape(context)
        },
        save = { it.toString() },
        onSet = {
            reloadHelper.reloadIcons()
        },
    )

    val customIconShape = preference(
        key = stringPreferencesKey(name = "custom_icon_shape"),
        defaultValue = null,
        parse = {
            IconShape.CustomCornerBased.fromStringOrNull(value = it)
                ?: IconShape.CustomCornerBased(
                    IconShapeManager.getSystemIconShape(context).findNearestShape(),
                )
        },
        save = { it.toString() },
        onSet = { it?.let(iconShape::setBlocking) },
    )

    val customFolderShape = preference(
        key = stringPreferencesKey(name = "custom_folder_shape"),
        defaultValue = null,
        parse = {
            IconShape.CustomCornerBased.fromStringOrNull(value = it)
                ?: IconShape.CustomCornerBased(
                    IconShapeManager.getSystemIconShape(context).findNearestShape(),
                )
        },
        save = { it.toString() },
        onSet = { it?.let(folderShape::setBlocking) },
    )

    val alwaysReloadIcons = preference(
        key = booleanPreferencesKey(name = "always_reload_icons"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_always_reload_icons),
    )

    val colorStyle = preference(
        key = stringPreferencesKey("color_style"),
        defaultValue = ColorStyle.fromString("tonal_spot"),
        parse = ColorStyle::fromString,
        save = ColorStyle::toString,
        onSet = { reloadHelper.restart() },
    )

    val strokeColorStyle = preference(
        key = stringPreferencesKey(name = "stroke_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.restart() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_accent_color)),
    )

    val hotseatBackgroundColor = preference(
        key = stringPreferencesKey(name = "hotseat_bg_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_hotseat_bg_color)),
    )

    val hotseatBackgroundCornerRadius = preference(
        key = floatPreferencesKey(name = "hotseat_bg_corner_radius"),
        defaultValue = context.resources.getDimension(R.dimen.bg_round_rect_radius) /
            context.resources.displayMetrics.density, // This should hopefully match corner of all devices
        onSet = { reloadHelper.recreate() },
    )

    val hotseatRecentAppsDividerColor = preference(
        key = stringPreferencesKey(name = "hotseat_recent_divider_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.recreate() },
        defaultValue = ColorOption.CustomColor(0xFF888888),
    )

    val appDrawerBackgroundColor = preference(
        key = stringPreferencesKey(name = "app_drawer_bg_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.recreate() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_app_drawer_bg_color)),
    )

    val appDrawerSearchBarBackground = preference(
        key = booleanPreferencesKey(name = "all_apps_search_bar_background"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_search_bar_background),
        onSet = { reloadHelper.recreate() },
    )

    val workProfileTabBackgroundColor = preference(
        key = stringPreferencesKey(name = "work_profile_tab_background_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.recreate() },
        defaultValue = ColorOption.SystemAccent,
    )

    val workProfileTabContainerBackground = preference(
        key = booleanPreferencesKey(name = "work_profile_tab_container_background"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() },
    )

    val notificationDotColor = preference(
        key = stringPreferencesKey(name = "notification_dot_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_notification_dot_color)),
    )

    val notificationDotTextColor = preference(
        key = stringPreferencesKey(name = "notification_dot_text_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_notification_dot_text_color)),
    )

    val folderColor = preference(
        key = stringPreferencesKey(name = "folder_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_folder_color)),
    )

    val showNotificationCount = preference(
        key = booleanPreferencesKey(name = "show_notification_count"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_notification_count),
        onSet = { reloadHelper.reloadGrid() },
    )

    val themedHotseatQsb = preference(
        key = booleanPreferencesKey(name = "themed_hotseat_qsb"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_themed_hotseat_qsb),
    )

    val isHotseatEnabled = preference(
        key = booleanPreferencesKey(name = "pref_show_hotseat"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_hotseat),
    )

    val hotseatQsbProvider = preference(
        key = stringPreferencesKey(name = "dock_search_bar_provider"),
        defaultValue = getRemoteDefault("dock_search_bar_provider")?.let {
            QsbSearchProvider.fromId(it)
        } ?: QsbSearchProvider.resolveDefault(context),
        parse = { QsbSearchProvider.fromId(it) },
        save = { it.id },
    )

    val hotseatQsbForceWebsite = preference(
        key = booleanPreferencesKey(name = "dock_search_bar_force_website"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_dock_search_bar_force_website),
    )

    val accentColor = preference(
        key = stringPreferencesKey(name = "accent_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.recreate() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_accent_color)),
    )

    val hiddenApps = preference(
        key = stringSetPreferencesKey(name = "hidden_apps"),
        defaultValue = setOf(),
    )

    val roundedWidgets = preference(
        key = booleanPreferencesKey(name = "rounded_widgets"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_rounded_widgets),
        onSet = { reloadHelper.reloadGrid() },
    )

    val allowWidgetOverlap = preference(
        key = booleanPreferencesKey(name = "allow_widget_overlap"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_allow_widget_overlap),
        onSet = { reloadHelper.reloadGrid() },
    )

    val forceWidgetResize = preference(
        key = booleanPreferencesKey(name = "force_widget_resize"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_force_widget_resize),
    )

    val widgetUnlimitedSize = preference(
        key = booleanPreferencesKey(name = "widget_unlimited_size"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_widget_unlimited_size),
    )

    val showStatusBar = preference(
        key = booleanPreferencesKey(name = "show_status_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_status_bar),
    )

    val statusBarClock = preference(
        key = booleanPreferencesKey(name = "status_bar_clock"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_dynamic_hide_status_bar_clock),
        onSet = { reloadHelper.reloadGrid() },
    )

    val rememberPosition = preference(
        key = booleanPreferencesKey(name = "all_apps_remember_position"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_remember_position),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showScrollbar = preference(
        key = booleanPreferencesKey(name = "all_apps_show_scrollbar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_scrollbar),
        onSet = { reloadHelper.recreate() },
    )

    val showTopShadow = preference(
        key = booleanPreferencesKey(name = "show_top_shadow"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_top_shadow),
    )

    val lockHomeScreen = preference(
        key = booleanPreferencesKey(name = "lock_home_screen"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_lock_home_screen),
    )

    val editModeOnLongPress = preference(
        key = booleanPreferencesKey(name = "edit_mode_on_long_press"),
        defaultValue = context.resources.getBoolean(
            R.bool.config_default_edit_mode_on_long_press,
        ),
    )

    val editModeIconJiggle = preference(
        key = booleanPreferencesKey(name = "edit_mode_icon_jiggle"),
        defaultValue = context.resources.getBoolean(
            R.bool.config_default_edit_mode_icon_jiggle,
        ),
    )

    val editModeRemoveBadge = preference(
        key = booleanPreferencesKey(name = "edit_mode_remove_badge"),
        defaultValue = context.resources.getBoolean(
            R.bool.config_default_edit_mode_remove_badge,
        ),
    )

    val editModeMultiSelect = preference(
        key = booleanPreferencesKey(name = "edit_mode_multi_select"),
        defaultValue = context.resources.getBoolean(
            R.bool.config_default_edit_mode_multi_select,
        ),
    )

    val editModeSelectionFeedback = preference(
        key = booleanPreferencesKey(name = "edit_mode_selection_feedback"),
        defaultValue = context.resources.getBoolean(
            R.bool.config_default_edit_mode_selection_feedback,
        ),
    )

    /** Shows the page strip (page numbers + reorder + default page) in edit mode. */
    val editModePageStrip = preference(
        key = booleanPreferencesKey(name = "edit_mode_page_strip"),
        defaultValue = false,
    )

    val defaultHomePage = preference(
        key = intPreferencesKey(name = "default_home_page"),
        defaultValue = Workspace.DEFAULT_PAGE,
    )

    val legacyPopupOptionsMigrated = preference(
        key = booleanPreferencesKey(name = "legacy_popup_options_migrated"),
        defaultValue = false,
    )

    val enableGlobalPrediction = preference(
        key = booleanPreferencesKey(name = "enable_global_prediction"),
        defaultValue = true,
    )

    val predictionMode = preference(
        key = stringPreferencesKey(name = "prediction_mode"),
        defaultValue = PredictionMode.fromString(context.getString(R.string.config_default_prediction_mode)),
        parse = { PredictionMode.fromString(it) },
        save = { it.toString() },
    )

    val lawnchairPredictorUseWeightedUsageStats = preference(
        key = booleanPreferencesKey(name = "lawnchair_prediction_use_weighted_usage"),
        defaultValue = false,
    )

    val launcherPopupOrder = preference(
        key = stringPreferencesKey(name = "launcher_popup_order"),
        defaultValue = LauncherOptionsPopup.DEFAULT_ORDER.toOptionOrderString(),
        onSet = { reloadHelper.reloadGrid() },
    )

    val lockHomeScreenButtonOnPopUp = preference(
        key = booleanPreferencesKey(name = "lock_home_screen_on_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_lock_home_screen_on_popup),
        onSet = { reloadHelper.reloadGrid() },
    )

    val editHomeScreenButtonOnPopUp = preference(
        key = booleanPreferencesKey(name = "edit_home_screen_on_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_edit_home_screen_on_popup),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showSystemSettingsEntryOnPopUp = preference(
        key = booleanPreferencesKey(name = "show_system_settings_entry_on_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_system_settings_entry_on_popup),
        onSet = { reloadHelper.reloadGrid() },
    )

    val hideAppDrawerSearchBar = preference(
        key = booleanPreferencesKey(name = "hide_app_drawer_search_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_hide_app_drawer_search_bar),
        onSet = { reloadHelper.recreate() },
    )

    val appDrawerHapticFeedback = preference(
        key = booleanPreferencesKey(name = "app_drawer_haptic_feedback"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_app_drawer_haptic_feedback),
    )

    val hiddenAppsInSearch = preference(
        key = stringPreferencesKey(name = "hidden_apps_in_search"),
        defaultValue = HiddenAppsInSearch.NEVER,
        onSet = { reloadHelper.recreate() },
    )

    val searchAlgorithm = preference(
        key = stringPreferencesKey(name = "search_algorithm"),
        defaultValue = LauncherXSearchAlgorithm.LOCAL_SEARCH,
        onSet = { reloadHelper.recreate() },
    )

    val showSuggestedAppsInDrawer = preference(
        key = booleanPreferencesKey(name = "show_suggested_apps_at_drawer_top"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_suggested_apps_at_drawer_top),
        onSet = { reloadHelper.recreate() },
    )

    val enableFontSelection = preference(
        key = booleanPreferencesKey(name = "enable_font_selection"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_font_selection),
        onSet = { newValue ->
            if (!newValue) {
                val fontCache = FontCache.INSTANCE.get(context)
                LauncherXPreferenceManager.getInstance(context).fontWorkspace.set(newValue = fontCache.uiText)
            }
        },
    )

    val autoShowKeyboardInDrawer = preference(
        key = booleanPreferencesKey(name = "auto_show_keyboard_in_drawer"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_auto_show_keyboard_in_drawer),
    )

    val workspaceTextColor = preference(
        key = stringPreferencesKey(name = "workspace_text_color"),
        defaultValue = ColorMode.AUTO,
        parse = { ColorMode.fromString(it) ?: ColorMode.AUTO },
        save = { it.toString() },
        onSet = { reloadHelper.recreate() },
    )

    val homeIconSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderPreviewBackgroundOpacity = preference(
        key = floatPreferencesKey(name = "folder_preview_background_opacity"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_folder_preview_background_opacity),
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderPreviewMargin = preference(
        key = floatPreferencesKey(name = "folder_preview_margin"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_folder_preview_margin),
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderBackgroundOpacity = preference(
        key = floatPreferencesKey(name = "folder_background_opacity"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_folder_background_opacity),
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderFrostedGlass = preference(
        key = booleanPreferencesKey(name = "folder_frosted_glass"),
        defaultValue = false,
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderLiquidGlass = preference(
        key = booleanPreferencesKey(name = "folder_liquid_glass"),
        defaultValue = false,
        onSet = { reloadHelper.reloadGrid() },
    )

    /** Wallpaper blur intensity (screen dp) for the small folder preview's glass. */
    val folderGlassBlur = preference(
        key = floatPreferencesKey(name = "folder_glass_blur"),
        defaultValue = 28f,
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderPopupFrostedGlass = preference(
        key = booleanPreferencesKey(name = "folder_popup_frosted_glass"),
        defaultValue = false,
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderPopupLiquidGlass = preference(
        key = booleanPreferencesKey(name = "folder_popup_liquid_glass"),
        defaultValue = false,
        onSet = { reloadHelper.reloadGrid() },
    )

    /** Wallpaper blur intensity (screen dp) for the opened folder window's glass. */
    val folderPopupGlassBlur = preference(
        key = floatPreferencesKey(name = "folder_popup_glass_blur"),
        defaultValue = 28f,
        onSet = { reloadHelper.reloadGrid() },
    )

    /** When on, render a blurred-wallpaper backdrop behind an opened folder. */
    val folderPopupBackgroundBlur = preference(
        key = booleanPreferencesKey(name = "folder_popup_background_blur"),
        defaultValue = false,
        onSet = { reloadHelper.reloadGrid() },
    )

    /** Blur intensity (screen dp) for the folder background blur and the blurred desktop content. */
    val folderPopupBackgroundBlurStrength = preference(
        key = floatPreferencesKey(name = "folder_popup_background_blur_strength"),
        defaultValue = 6f,
        onSet = { reloadHelper.reloadGrid() },
    )

    /** When on, an opened folder is centred on the screen instead of centred on its icon. */
    val folderOpenCentered = preference(
        key = booleanPreferencesKey(name = "folder_open_centered"),
        defaultValue = false,
        onSet = { reloadHelper.reloadGrid() },
    )

    /** Master switch for every liquid-glass (edge refraction) effect. */
    val liquidGlassEnabled = preference(
        key = booleanPreferencesKey(name = "liquid_glass_enabled"),
        defaultValue = LIQUID_GLASS_DEFAULT_ENABLED,
        onSet = { reloadHelper.reloadGrid() },
    )

    /** Edge-refraction strength for liquid glass (0 = off, ~0.75 = iOS-like bevelled rim). */
    val liquidGlassRefraction = preference(
        key = floatPreferencesKey(name = "liquid_glass_refraction"),
        defaultValue = LIQUID_GLASS_DEFAULT_REFRACTION,
        onSet = { reloadHelper.reloadGrid() },
    )

    /** Chromatic dispersion at the glass rim (0 = off, 1 = subtle, 2 = strong). */
    val liquidGlassDispersion = preference(
        key = floatPreferencesKey(name = "liquid_glass_dispersion"),
        defaultValue = LIQUID_GLASS_DEFAULT_DISPERSION,
        onSet = { reloadHelper.reloadGrid() },
    )

    /** When on, the liquid specular keeps flowing on large folders and the dock as well. */
    val liquidGlassSpecularFlow = preference(
        key = booleanPreferencesKey(name = "liquid_glass_specular_flow"),
        defaultValue = LIQUID_GLASS_DEFAULT_SPECULAR_FLOW,
        onSet = { reloadHelper.reloadGrid() },
    )

    /**
     * Liquid glass backdrop quality: how large the cached wallpaper backdrop is and how sharply it
     * is sampled. Higher levels look crisper and retain more memory; the engine picks it up on the
     * next backdrop build.
     */
    val liquidGlassQuality = preference(
        key = intPreferencesKey(name = "liquid_glass_quality"),
        defaultValue = LIQUID_GLASS_QUALITY_MAX,
        onSet = { },
    )

    val showIconLabelsOnHomeScreen = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_on_home_screen"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_on_home_screen),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showIconLabelsOnHomeScreenFolder = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_on_home_screen_folder"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_on_home_screen),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showFolderDesktopLabel = preference(
        key = booleanPreferencesKey(name = "folder_show_desktop_label"),
        defaultValue = true,
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerIconSizeFactor = preference(
        key = floatPreferencesKey(name = "drawer_icon_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_icon_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showIconLabelsInDrawer = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_in_drawer"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_in_drawer),
        onSet = { reloadHelper.reloadGrid() },
    )

    val homeIconLabelSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_label_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_label_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val homeIconLabelFolderSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_label_folder_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_label_folder_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val pageIndicatorHeightFactor = preference(
        key = floatPreferencesKey(name = "page_indicator_height_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.page_indicator_height_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerIconLabelSizeFactor = preference(
        key = floatPreferencesKey(name = "drawer_icon_label_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_icon_label_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerCellHeightFactor = preference(
        key = floatPreferencesKey(name = "drawer_cell_height_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_cell_height_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerLeftRightMarginFactor = preference(
        key = floatPreferencesKey(name = "drawer_left_right_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_left_right_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val hotseatBottomFactor = preference(
        key = floatPreferencesKey(name = "hotseat_bottom_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_hotseat_bottom_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val workspacePaddingHorizontalFactor = preference(
        key = floatPreferencesKey(name = "workspace_padding_horizontal"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_workspace_padding_horizontal),
        onSet = { reloadHelper.reloadGrid() },
    )

    val workspacePaddingVerticalFactor = preference(
        key = floatPreferencesKey(name = "workspace_padding_vertical"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_workspace_padding_vertical),
        onSet = { reloadHelper.reloadGrid() },
    )

    val widgetPaddingFactor = preference(
        key = floatPreferencesKey(name = "widget_padding_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_widget_padding_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerPaddingTopFactor = preference(
        key = floatPreferencesKey(name = "drawer_padding_top"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_padding_top),
        onSet = { reloadHelper.reloadGrid() },
    )

    val enableFuzzySearch = preference(
        key = booleanPreferencesKey(name = "enable_fuzzy_search"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_fuzzy_search),
    )

    val closingAppOverlay = preference(
        key = stringPreferencesKey(name = "closing_app_overlay"),
        defaultValue = FullScreenOverlayMode.fromValue(context.resources.getString(R.string.config_default_overlay)),
        parse = { FullScreenOverlayMode.fromValue(it) },
        save = { it.value },
        onSet = { reloadHelper.reloadGrid() },
    )

    val openingAppOverlay = preference(
        key = stringPreferencesKey(name = "opening_app_overlay"),
        defaultValue = FullScreenOverlayMode.SUCK_IN,
        parse = { value ->
            FullScreenOverlayMode.entries.find { it.value == value } ?: FullScreenOverlayMode.SUCK_IN
        },
        save = { it.value },
    )

    val matchHotseatQsbStyle = preference(
        key = booleanPreferencesKey(name = "use_drawer_search_icon"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val webSuggestionProvider = preference(
        key = stringPreferencesKey(name = "web_suggestion_provider"),
        defaultValue = WebSearchProvider.fromString(
            getRemoteDefault("web_suggestion_provider")
                ?: context.resources.getString(R.string.config_default_web_suggestion_provider),
        ),
        parse = { WebSearchProvider.fromString(it) },
        save = { it.toString() },
        onSet = { reloadHelper.recreate() },
    )

    val webSuggestionProviderUrl = preference(
        key = stringPreferencesKey(name = "web_suggestion_provider_url"),
        defaultValue = "",
    )

    val webSuggestionProviderSuggestionsUrl = preference(
        key = stringPreferencesKey(name = "web_suggestions_provider_suggestions_url"),
        defaultValue = "",
    )

    val webSuggestionProviderName = preference(
        key = stringPreferencesKey(name = "web_suggestion_provider_name"),
        defaultValue = context.resources.getString(R.string.custom),
    )

    val maxAppSearchResultCount = preference(
        key = intPreferencesKey(name = "max_search_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_search_max_result_count),
    )

    val maxWebSuggestionResultCount = preference(
        key = intPreferencesKey(name = "max_suggestion_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_suggestion_max_result_count),
    )

    val maxFileResultCount = preference(
        key = intPreferencesKey(name = "max_files_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_files_max_result_count),
    )

    val maxPeopleResultCount = preference(
        key = intPreferencesKey(name = "max_people_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_people_max_result_count),
    )

    val maxWebSuggestionDelay = preference(
        key = intPreferencesKey(name = "max_web_suggestion_delay"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_max_web_suggestion_delay),
    )

    val maxSettingsEntryResultCount = preference(
        key = intPreferencesKey(name = "max_settings_entry_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_settings_entry_max_result_count),
    )

    val maxRecentResultCount = preference(
        key = intPreferencesKey(name = "max_recent_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_recent_max_result_count),
    )

    val enableSmartspace = preference(
        key = booleanPreferencesKey(name = "enable_smartspace"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_smartspace),
        onSet = { reloadHelper.restart() },
    )

    val twoLineAllApps = preference(
        key = booleanPreferencesKey(name = "two_line_all_apps"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_two_line_allapps),
        onSet = { value ->
            LauncherPrefs.get(context).put(ENABLE_TWOLINE_ALLAPPS_TOGGLE, value)
        },
    )

    val enableFeed = preference(
        key = booleanPreferencesKey(name = "enable_feed"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_feed),
    )

    val showComponentNames = preference(
        key = booleanPreferencesKey(name = "show_component_names"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_component_names),
    )

    val drawerColumns = idpPreference(
        key = intPreferencesKey(name = "drawer_columns"),
        defaultSelector = { numAllAppsColumns },
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerColumnsUnfolded = idpPreference(
        key = intPreferencesKey(name = "drawer_columns_unfolded"),
        defaultSelector = { numAllAppsColumns + 2 },
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderColumns = idpPreference(
        key = intPreferencesKey(name = "folder_columns"),
        defaultSelector = { numFolderColumns[INDEX_DEFAULT] },
        onSet = { reloadHelper.reloadGrid() },
    )

    val additionalFonts = preference(
        key = stringPreferencesKey(name = "additional_fonts"),
        defaultValue = "",
    )

    val enableTaskbarOnPhone = preference(
        key = booleanPreferencesKey("enable_taskbar_on_phone"),
        defaultValue = false,
        onSet = {
            reloadHelper.reloadGrid()
            reloadHelper.reloadTaskbar()
            reloadHelper.recreate()
        },
    )

    val smartspaceMode = preference(
        key = stringPreferencesKey("smartspace_mode"),
        defaultValue = SmartspaceMode.fromString(context.getString(R.string.config_default_smartspace_mode)),
        parse = { SmartspaceMode.fromString(it) },
        save = { it.toString() },
        onSet = { reloadHelper.recreate() },
    )

    val smartspaceAagWidget = preference(
        key = booleanPreferencesKey("enable_smartspace_aag_widget"),
        defaultValue = true,
    )

    val smartspaceBatteryStatus = preference(
        key = booleanPreferencesKey("enable_smartspace_battery_status"),
        defaultValue = true,
    )

    val smartspaceTorch = preference(
        key = booleanPreferencesKey("enable_smartspace_torch"),
        defaultValue = true,
    )

    val smartspaceNowPlaying = preference(
        key = booleanPreferencesKey("enable_smartspace_now_playing"),
        defaultValue = true,
    )

    val smartspaceOnboarding = preference(
        key = booleanPreferencesKey("enable_smartspace_onboarding"),
        defaultValue = true,
    )

    val smartspaceShowDate = preference(
        key = booleanPreferencesKey("smartspace_show_date"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_smartspace_show_date),
    )

    val smartspaceShowTime = preference(
        key = booleanPreferencesKey("smartspace_show_time"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_smartspace_show_time),
    )

    val smartspaceTimeFormat = preference(
        key = stringPreferencesKey("smartspace_time_format"),
        defaultValue = SmartspaceTimeFormat.fromString(context.getString(R.string.config_default_smartspace_time_format)),
        parse = { SmartspaceTimeFormat.fromString(it) },
        save = { it.toString() },
    )

    val smartspaceCalendar = preference(
        key = stringPreferencesKey(name = "smartspace_calendar"),
        defaultValue = SmartspaceCalendar.fromString(context.getString(R.string.config_default_smart_space_calendar)),
        parse = { SmartspaceCalendar.fromString(it) },
        save = { it.toString() },
    )

    val smartspacerMaxCount = preference(
        key = intPreferencesKey(name = "smartspace_max_count"),
        defaultValue = 5,
        onSet = { reloadHelper.recreate() },
    )

    val wallpaperDepthEffect = preference(
        key = booleanPreferencesKey(name = "enable_wallpaper_depth_effect"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() },
    )

    val deckLayout = preference(
        key = booleanPreferencesKey(name = "enable_lawn_deck"),
        defaultValue = false,
        onSet = { reloadHelper.reloadIcons() },
    )

    val showDeckLayout = preference(
        key = booleanPreferencesKey(name = "show_deck_layout"),
        defaultValue = false,
    )

    val enableLabelInDock = preference(
        key = booleanPreferencesKey(name = "enable_label_dock"),
        defaultValue = false,
    )

    val doubleTapGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("double_tap_gesture_handler"),
        defaultValue = GestureHandlerConfig.Sleep,
    )

    val sleepMode = preference(
        key = stringPreferencesKey(name = "sleep_mode"),
        defaultValue = SleepMode.AUTO,
        parse = { SleepMode.fromString(it) ?: SleepMode.AUTO },
        save = { it.toString() },
    )

    val swipeUpGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("swipe_up_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenAppDrawer,
    )

    val swipeDownGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("swipe_down_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenNotifications,
    )

    val twoFingerSwipeUpGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("two_finger_swipe_up_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp,
    )

    val twoFingerSwipeDownGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("two_finger_swipe_down_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenQuickSettings,
    )

    val homePressGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("home_press_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp,
    )

    val backPressGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("back_press_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp,
    )

    val autoUpdaterNightly = preference(
        key = booleanPreferencesKey(name = "enable_nightly_auto_updater"),
        defaultValue = if (BuildConfig.APPLICATION_ID.contains("nightly")) {
            context.resources.getBoolean(R.bool.config_default_enable_nightly_auto_updater)
        } else {
            false
        },
    )

    private inline fun <reified T> serializablePreference(
        key: Preferences.Key<String>,
        defaultValue: T,
    ) = preference(
        key = key,
        defaultValue = defaultValue,
        parse = { value ->
            runCatching { kotlinxJson.decodeFromString<T>(value) }.getOrDefault(defaultValue)
        },
        save = kotlinxJson::encodeToString,
    )

    init {
        preferencesDataStore.data
            .onEach { cachedPreferences = it }
            .launchIn(scope)

        initializeIconShape(iconShape.firstCached(this))
        iconShape.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach { shape ->
                initializeIconShape(shape)
                L3ThemeManager.INSTANCE.get(context)
                LauncherAppState.getInstance(context).model.reloadIfActive()
            }
            .launchIn(scope)

        hotseatMode.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                // The cached snapshot read by firstCached() is updated by a separate
                // collector and can still be stale when the restarted activity constructs
                // its views. Refresh it before restarting so the new activity sees the
                // mode that was just written (otherwise the dock may keep the wrong QSB
                // layout, e.g. an empty placeholder after re-enabling the search bar).
                cachedPreferences = preferencesDataStore.data.first()
                reloadHelper.restart()
            }
            .launchIn(scope)

        isHotseatEnabled.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                reloadHelper.recreate()
                reloadHelper.reloadGrid()
            }
            .launchIn(scope)

        hotseatQsbProvider.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach { reloadHelper.recreate() }
            .launchIn(scope)

        enableLabelInDock.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach { reloadHelper.reloadGrid() }
            .launchIn(scope)
    }

    suspend fun setGestureForApp(
        key: ComponentKey,
        gestureType: GestureType,
        gesture: GestureHandlerConfig,
    ) {
        val cmp = Converters().fromComponentKey(key)
        val key = stringPreferencesKey("$cmp:${gestureType.name}")
        preferencesDataStore.edit { prefs ->
            prefs[key] = kotlinxJson.encodeToString(gesture)
        }
    }

    fun getGestureForApp(key: ComponentKey, gestureType: GestureType): Flow<GestureHandlerConfig> {
        val cmp = Converters().fromComponentKey(key)
        val key = stringPreferencesKey("$cmp:${gestureType.name}")
        return preferencesDataStore.data.map { prefs ->
            prefs[key]?.let {
                runCatching { kotlinxJson.decodeFromString<GestureHandlerConfig>(it) }
                    .getOrDefault(GestureHandlerConfig.NoOp)
            } ?: GestureHandlerConfig.NoOp
        }
    }

    fun getGestureForAppCached(key: ComponentKey, gestureType: GestureType): GestureHandlerConfig {
        val cmp = Converters().fromComponentKey(key)
        val key = stringPreferencesKey("$cmp:${gestureType.name}")
        val prefs = getCachedPreferences()
        return prefs[key]?.let {
            runCatching { kotlinxJson.decodeFromString<GestureHandlerConfig>(it) }
                .getOrDefault(GestureHandlerConfig.NoOp)
        } ?: GestureHandlerConfig.NoOp
    }

    private fun initializeIconShape(shape: IconShape) {
        CustomAdaptiveIconDrawable.sInitialized = true
        CustomAdaptiveIconDrawable.sMaskId = shape.getHashString()
        CustomAdaptiveIconDrawable.sMask = shape.getMaskPath()
    }

    override fun close() {
        scope.cancel()
    }

    private fun getRemoteDefault(key: String): String? = liveInformationManager.liveInformation
        .firstBlocking()
        .features[key]
        .also { value ->
            if (value == null) {
                Log.d(TAG, "getRemoteDefault: $key -> no remote default")
            } else {
                Log.d(TAG, "getRemoteDefault: $key -> $value")
            }
        }

    companion object {
        private val Context.preferencesDataStore by preferencesDataStore(
            name = "preferences",
            produceMigrations = { listOf(SharedPreferencesMigration(context = it).produceMigration()) },
        )

        const val LIQUID_GLASS_DEFAULT_ENABLED = true
        const val LIQUID_GLASS_DEFAULT_REFRACTION = 0.75f
        const val LIQUID_GLASS_DEFAULT_DISPERSION = 1f
        const val LIQUID_GLASS_DEFAULT_SPECULAR_FLOW = false

        /** Liquid-glass backdrop quality levels, ordered by how much memory the cached backdrop uses. */
        const val LIQUID_GLASS_QUALITY_SAVER = 0
        const val LIQUID_GLASS_QUALITY_BALANCED = 1
        const val LIQUID_GLASS_QUALITY_HIGH = 2
        const val LIQUID_GLASS_QUALITY_MAX = 3

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getPreferenceManager2)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!

        private const val TAG = "PreferenceManager2"
    }
}

@Composable
fun preferenceManager2() = PreferenceManager2.getInstance(LocalContext.current)
