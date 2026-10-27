package com.jerry.launcherx.modern.dock

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.model.data.ItemInfo

class DockLayoutManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "modern_dock_prefs", Context.MODE_PRIVATE
    )

    enum class DockPosition {
        BOTTOM,
        LEFT,
        RIGHT
    }

    fun saveDockMode(mode: AdaptiveDockView.DockMode) {
        prefs.edit().putString("dock_mode", mode.name).apply()
    }

    fun getDockMode(): AdaptiveDockView.DockMode {
        val name = prefs.getString("dock_mode", null)
        return if (name != null) {
            try {
                AdaptiveDockView.DockMode.valueOf(name)
            } catch (e: IllegalArgumentException) {
                AdaptiveDockView.DockMode.PHONE
            }
        } else {
            AdaptiveDockView.DockMode.PHONE
        }
    }

    fun saveDockPosition(position: DockPosition) {
        prefs.edit().putString("dock_position", position.name).apply()
    }

    fun getDockPosition(): DockPosition {
        val name = prefs.getString("dock_position", null)
        return if (name != null) {
            try {
                DockPosition.valueOf(name)
            } catch (e: IllegalArgumentException) {
                DockPosition.BOTTOM
            }
        } else {
            DockPosition.BOTTOM
        }
    }

    fun savePinnedItems(itemIds: List<Long>) {
        prefs.edit().putString("pinned_items", itemIds.joinToString(",")).apply()
    }

    fun getPinnedItems(): List<Long> {
        val str = prefs.getString("pinned_items", "") ?: ""
        return if (str.isNotEmpty()) {
            str.split(",").mapNotNull { it.toLongOrNull() }
        } else {
            emptyList()
        }
    }

    fun getEffectiveMode(screenWidthDp: Int, isTablet: Boolean): AdaptiveDockView.DockMode {
        val savedMode = getDockMode()
        return when {
            savedMode == AdaptiveDockView.DockMode.DESKTOP -> AdaptiveDockView.DockMode.DESKTOP
            isTablet && screenWidthDp >= 600 -> AdaptiveDockView.DockMode.TABLET
            else -> AdaptiveDockView.DockMode.PHONE
        }
    }

    fun shouldShowRecents(): Boolean {
        return getDockMode() != AdaptiveDockView.DockMode.PHONE
    }

    fun shouldShowDrawerButton(): Boolean {
        return getDockMode() == AdaptiveDockView.DockMode.TABLET ||
               getDockMode() == AdaptiveDockView.DockMode.DESKTOP
    }

    fun getMaxPinnedCount(): Int = when (getDockMode()) {
        AdaptiveDockView.DockMode.PHONE -> 5
        AdaptiveDockView.DockMode.TABLET -> 8
        AdaptiveDockView.DockMode.DESKTOP -> 10
    }

    fun getMaxRecentCount(): Int = when (getDockMode()) {
        AdaptiveDockView.DockMode.PHONE -> 0
        AdaptiveDockView.DockMode.TABLET -> 5
        AdaptiveDockView.DockMode.DESKTOP -> 7
    }
}
