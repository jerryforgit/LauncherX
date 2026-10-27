package com.jerry.launcherx.ui.popup

import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.jerry.launcherx.preferences2.PreferenceManager2.Companion.getInstance
import com.jerry.launcherx.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.OptionsPopupView.OptionItem
import com.patrykmichalik.opto.core.setBlocking

object LauncherOptionsPopup {
    val DEFAULT_ORDER = listOf(
        LauncherOptionPopupItem("carousel", true),
        LauncherOptionPopupItem("lock", false),
        LauncherOptionPopupItem("edit_mode", false),
        LauncherOptionPopupItem("wallpaper", true),
        LauncherOptionPopupItem("widgets", true),
        LauncherOptionPopupItem("all_apps", true),
        LauncherOptionPopupItem("home_settings", true),
        LauncherOptionPopupItem("sys_settings", false),
        LauncherOptionPopupItem("default_page", false),
    )

    fun restoreMissingPopupOptions(
        launcher: Launcher,
    ) {
        val prefs2 = getInstance(launcher)

        val currentOrder = prefs2.launcherPopupOrder.firstCached()
        val currentOptions = currentOrder.toLauncherOptions()

        // check for missing items in current options; if so, add them
        val missingItems = DEFAULT_ORDER.filter { defaultItem ->
            defaultItem.identifier !in currentOptions.map { it.identifier }
        }

        if (missingItems.isNotEmpty()) {
            prefs2.launcherPopupOrder.setBlocking(
                (currentOptions + missingItems).toOptionOrderString(),
            )
        }
    }

    /**
     * Returns the list of supported actions
     */
    fun getLauncherOptions(
        launcher: Launcher?,
        onLockToggle: (View) -> Boolean,
        onStartSystemSettings: (View) -> Boolean,
        onStartEditMode: (View) -> Boolean,
        onStartAllApps: (View) -> Boolean,
        onStartWallpaperPicker: (View) -> Boolean,
        onStartWidgetsMenu: (View) -> Boolean,
        onStartHomeSettings: (View) -> Boolean,
    ): ArrayList<OptionItem> {
        return ArrayList(
            getLauncherOptionEntries(
                launcher,
                onLockToggle,
                onStartSystemSettings,
                onStartEditMode,
                onStartAllApps,
                onStartWallpaperPicker,
                onStartWidgetsMenu,
                onStartHomeSettings,
                forceDefaultPage = false,
            ).map { it.item },
        )
    }

    /**
     * Same list as [getLauncherOptions], but tagged with the preference identifier of every entry
     * so callers other than the long-press popup (the edit-mode action bar) can decide which of
     * them to show. The edit-mode bar is deliberately built from this single source of truth so a
     * popup entry toggled in the settings always shows up in both places.
     *
     * @param forceDefaultPage keeps the "set as default page" entry even when the user disabled it
     * in the popup settings, so the edit-mode bar can always offer it. It is still hidden while
     * the current page already is the default one.
     */
    fun getLauncherOptionEntries(
        launcher: Launcher?,
        onLockToggle: (View) -> Boolean,
        onStartSystemSettings: (View) -> Boolean,
        onStartEditMode: (View) -> Boolean,
        onStartAllApps: (View) -> Boolean,
        onStartWallpaperPicker: (View) -> Boolean,
        onStartWidgetsMenu: (View) -> Boolean,
        onStartHomeSettings: (View) -> Boolean,
        forceDefaultPage: Boolean,
    ): ArrayList<LauncherOptionEntry> {
        val prefs2 = getInstance(launcher!!)
        val lockHomeScreen = prefs2.lockHomeScreen.firstCached()
        val optionOrder = prefs2
            .launcherPopupOrder.firstCached().toLauncherOptions()

        val wallpaperResString =
            if (Utilities.existsStyleWallpapers(launcher)) R.string.styles_wallpaper_button_text else R.string.wallpapers
        val wallpaperResDrawable =
            if (Utilities.existsStyleWallpapers(launcher)) R.drawable.ic_palette else R.drawable.ic_wallpaper

        val optionsList = mapOf(
            "lock" to OptionItem(
                launcher,
                if (lockHomeScreen) R.string.home_screen_unlock else R.string.home_screen_lock,
                if (lockHomeScreen) R.drawable.ic_lock_open else R.drawable.ic_lock,
                LauncherEvent.IGNORE,
                onLockToggle,
            ),
            "sys_settings" to OptionItem(
                launcher,
                R.string.system_settings,
                R.drawable.ic_setting,
                LauncherEvent.IGNORE,
                onStartSystemSettings,
            ),
            "edit_mode" to OptionItem(
                launcher,
                R.string.edit_home_screen,
                R.drawable.enter_home_gardening_icon,
                LauncherEvent.LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
                onStartEditMode,
            ),
            "all_apps" to OptionItem(
                launcher,
                R.string.all_apps_button_label,
                R.drawable.ic_apps,
                LauncherEvent.LAUNCHER_ALL_APPS_TAP_OR_LONGPRESS,
                onStartAllApps,
            ),
            "wallpaper" to OptionItem(
                launcher,
                wallpaperResString,
                wallpaperResDrawable,
                LauncherEvent.IGNORE,
                onStartWallpaperPicker,
            ),
            "widgets" to OptionItem(
                launcher,
                R.string.widget_button_text,
                SystemShortcut.Widgets.getDrawableId(),
                LauncherEvent.LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS,
                onStartWidgetsMenu,
            ),
            "enterAllApps" to OptionItem(
                launcher,
                R.string.all_apps_button_label,
                R.drawable.ic_apps,
                LauncherEvent.LAUNCHER_ALL_APPS_TAP_OR_LONGPRESS,
                onStartAllApps,
            ),
            "home_settings" to OptionItem(
                launcher,
                R.string.settings_button_text,
                R.drawable.ic_home_screen,
                LauncherEvent.LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
                onStartHomeSettings,
            ),
            "default_page" to OptionItem(
                launcher,
                R.string.set_default_home_page,
                R.drawable.ic_home_pin,
                LauncherEvent.IGNORE,
                ::setAsDefaultHomePage,
            ),
        )

        val options = ArrayList<LauncherOptionEntry>()
        optionOrder
            .filter {
                (it.isEnabled && it.identifier != "carousel") ||
                    (forceDefaultPage && it.identifier == "default_page")
            }
            .filter {
                if (lockHomeScreen) {
                    it.identifier != "edit_mode" && it.identifier != "widgets"
                } else {
                    true
                }
            }
            .filter {
                // Never offer to set the current page as default - it already is one.
                it.identifier != "default_page" || !launcher.workspace.isCurrentPageDefault
            }
            .mapNotNull { item ->
                optionsList[item.identifier]?.let { LauncherOptionEntry(item.identifier, it) }
            }
            .forEach { options.add(it) }

        return options
    }

    private fun setAsDefaultHomePage(v: View): Boolean {
        val launcher = Launcher.getLauncher(v.context)
        val currentPage = launcher.workspace.getNextPage()
        launcher.workspace.setDefaultPage(currentPage)
        Toast.makeText(launcher, R.string.default_home_page_set, Toast.LENGTH_SHORT).show()
        // The entry is only offered while the current page is not the default one, so the bar
        // has to be rebuilt right after the click. Defer it until the tap has been dispatched so
        // tearing the clicked view down mid-gesture cannot fall through to the workspace.
        v.postDelayed({ launcher.refreshEditModeBars() }, 250)
        return true
    }

    fun getMetadataForOption(identifier: String): LauncherOptionMetadata {
        return when (identifier) {
            "carousel" -> LauncherOptionMetadata(
                label = R.string.wallpaper_quick_picker,
                icon = R.drawable.ic_wallpaper,
                isCarousel = true,
            )

            "lock" -> LauncherOptionMetadata(
                label = R.string.home_screen_lock,
                icon = R.drawable.ic_lock,
            )

            "sys_settings" -> LauncherOptionMetadata(
                label = R.string.system_settings,
                icon = R.drawable.ic_setting,
            )

            "edit_mode" -> LauncherOptionMetadata(
                label = R.string.edit_home_screen,
                icon = R.drawable.enter_home_gardening_icon,
            )

            "wallpaper" -> LauncherOptionMetadata(
                label = R.string.styles_wallpaper_button_text,
                icon = R.drawable.ic_palette,
            )

            "widgets" -> LauncherOptionMetadata(
                label = R.string.widget_button_text,
                icon = SystemShortcut.Widgets.getDrawableId(),
            )

            "all_apps" -> LauncherOptionMetadata(
                label = R.string.all_apps_button_label,
                icon = R.drawable.ic_apps,
            )

            "home_settings" -> LauncherOptionMetadata(
                label = R.string.settings_button_text,
                icon = R.drawable.ic_home_screen,
            )

            "default_page" -> LauncherOptionMetadata(
                label = R.string.set_default_home_page,
                icon = R.drawable.ic_home_pin,
            )

            else -> throw IllegalArgumentException("invalid popup option")
        }
    }

    fun migrateLegacyPreferences(
        launcher: Launcher,
    ) {
        val prefs2 = getInstance(launcher)

        val lockHomeScreenButtonOnPopUp = prefs2.lockHomeScreenButtonOnPopUp.firstCached()
        val editHomeScreenButtonOnPopUp = prefs2.editHomeScreenButtonOnPopUp.firstCached()
        val showSystemSettingsEntryOnPopUp = prefs2.showSystemSettingsEntryOnPopUp.firstCached()

        val optionOrder = prefs2.launcherPopupOrder
        val legacyPopupOptionsMigrated = prefs2.legacyPopupOptionsMigrated.firstCached()

        if (!legacyPopupOptionsMigrated) {
            prefs2.legacyPopupOptionsMigrated.setBlocking(true)

            val options = optionOrder.firstCached().toLauncherOptions()

            options.forEachIndexed { index, item ->
                if (item.identifier == "lock") {
                    options[index].isEnabled = lockHomeScreenButtonOnPopUp
                }
                if (item.identifier == "edit_mode") {
                    options[index].isEnabled = editHomeScreenButtonOnPopUp
                }
                if (item.identifier == "sys_settings") {
                    options[index].isEnabled = showSystemSettingsEntryOnPopUp
                }
            }

            optionOrder.setBlocking(options.toOptionOrderString())
        }
    }
}

data class LauncherOptionPopupItem(
    val identifier: String,
    var isEnabled: Boolean,
)

/** A long-press popup entry together with the preference identifier it was configured under. */
data class LauncherOptionEntry(
    val identifier: String,
    val item: OptionItem,
)

data class LauncherOptionMetadata(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val isCarousel: Boolean = false,
)

fun String.toLauncherOptions(): List<LauncherOptionPopupItem> {
    return this.split("|").map { item ->
        val (identifier, isEnabled) = when {
            item.startsWith("+") -> item.drop(1) to true
            item.startsWith("-") -> item.drop(1) to false
            else -> item to true // Default to enabled if no prefix
        }
        LauncherOptionPopupItem(identifier, isEnabled)
    }
}

fun List<LauncherOptionPopupItem>.toOptionOrderString(): String {
    return this.joinToString("|") {
        if (it.isEnabled) "+${it.identifier}" else "-${it.identifier}"
    }
}
