package com.jerry.launcherx.modern.profile

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.model.data.ItemInfo

class DesktopProfileManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "desktop_profiles_prefs", Context.MODE_PRIVATE
    )

    data class DesktopProfile(
        val id: String,
        val name: String,
        val gridColumns: Int,
        val gridRows: Int,
        val hotseatColumns: Int,
        val dockMode: String,
        val showSearchBar: Boolean,
        val iconSizeFactor: Float,
        val density: Int,
        val workspaceItems: List<WorkspaceItem> = emptyList()
    ) {
        data class WorkspaceItem(
            val itemId: Long,
            val cellX: Int,
            val cellY: Int,
            val spanX: Int,
            val spanY: Int,
            val screen: Int
        )
    }

    private val profiles = mutableMapOf<String, DesktopProfile>()
    private var activeProfileId: String = "default"

    init {
        loadProfiles()
    }

    fun createProfile(
        name: String,
        gridColumns: Int = 5,
        gridRows: Int = 6,
        hotseatColumns: Int = 5,
        dockMode: String = "PHONE",
        showSearchBar: Boolean = true,
        iconSizeFactor: Float = 1.0f,
        density: Int = 0
    ): DesktopProfile {
        val id = "profile_${System.currentTimeMillis()}"
        val profile = DesktopProfile(
            id = id,
            name = name,
            gridColumns = gridColumns,
            gridRows = gridRows,
            hotseatColumns = hotseatColumns,
            dockMode = dockMode,
            showSearchBar = showSearchBar,
            iconSizeFactor = iconSizeFactor,
            density = density
        )
        profiles[id] = profile
        saveProfiles()
        return profile
    }

    fun updateProfile(profile: DesktopProfile) {
        profiles[profile.id] = profile
        saveProfiles()
    }

    fun deleteProfile(id: String) {
        if (id == "default") return
        profiles.remove(id)
        if (activeProfileId == id) {
            activeProfileId = "default"
        }
        saveProfiles()
    }

    fun getProfile(id: String): DesktopProfile? = profiles[id]

    fun getAllProfiles(): List<DesktopProfile> = profiles.values.toList()

    fun getActiveProfile(): DesktopProfile = profiles[activeProfileId]
        ?: profiles["default"]
        ?: createDefaultProfile()

    fun setActiveProfile(id: String) {
        if (profiles.containsKey(id)) {
            activeProfileId = id
            prefs.edit().putString("active_profile_id", id).apply()
        }
    }

    fun createOfficeProfile(): DesktopProfile {
        return createProfile(
            name = "Office Mode",
            gridColumns = 7,
            gridRows = 8,
            hotseatColumns = 8,
            dockMode = "TABLET",
            showSearchBar = true,
            iconSizeFactor = 0.85f
        )
    }

    fun createEntertainmentProfile(): DesktopProfile {
        return createProfile(
            name = "Entertainment Mode",
            gridColumns = 4,
            gridRows = 5,
            hotseatColumns = 4,
            dockMode = "PHONE",
            showSearchBar = false,
            iconSizeFactor = 1.15f
        )
    }

    fun createMinimalProfile(): DesktopProfile {
        return createProfile(
            name = "Minimal Mode",
            gridColumns = 3,
            gridRows = 4,
            hotseatColumns = 4,
            dockMode = "PHONE",
            showSearchBar = false,
            iconSizeFactor = 1.2f
        )
    }

    private fun createDefaultProfile(): DesktopProfile {
        val profile = DesktopProfile(
            id = "default",
            name = "Default",
            gridColumns = 5,
            gridRows = 6,
            hotseatColumns = 5,
            dockMode = "PHONE",
            showSearchBar = true,
            iconSizeFactor = 1.0f,
            density = 0
        )
        profiles["default"] = profile
        saveProfiles()
        return profile
    }

    fun copyProfile(sourceId: String, newName: String): DesktopProfile? {
        val source = profiles[sourceId] ?: return null
        val newId = "profile_${System.currentTimeMillis()}"
        val copy = source.copy(id = newId, name = newName)
        profiles[newId] = copy
        saveProfiles()
        return copy
    }

    fun renameProfile(id: String, newName: String) {
        val profile = profiles[id] ?: return
        profiles[id] = profile.copy(name = newName)
        saveProfiles()
    }

    fun exportProfile(id: String): Map<String, Any?> {
        val profile = profiles[id] ?: return emptyMap()
        return mapOf(
            "id" to profile.id,
            "name" to profile.name,
            "gridColumns" to profile.gridColumns,
            "gridRows" to profile.gridRows,
            "hotseatColumns" to profile.hotseatColumns,
            "dockMode" to profile.dockMode,
            "showSearchBar" to profile.showSearchBar,
            "iconSizeFactor" to profile.iconSizeFactor,
            "density" to profile.density
        )
    }

    fun importProfile(data: Map<String, Any?>): DesktopProfile? {
        val name = data["name"] as? String ?: return null
        return createProfile(
            name = name,
            gridColumns = (data["gridColumns"] as? Int) ?: 5,
            gridRows = (data["gridRows"] as? Int) ?: 6,
            hotseatColumns = (data["hotseatColumns"] as? Int) ?: 5,
            dockMode = (data["dockMode"] as? String) ?: "PHONE",
            showSearchBar = (data["showSearchBar"] as? Boolean) ?: true,
            iconSizeFactor = (data["iconSizeFactor"] as? Float) ?: 1.0f,
            density = (data["density"] as? Int) ?: 0
        )
    }

    private fun saveProfiles() {
        val editor = prefs.edit()
        editor.putString("active_profile_id", activeProfileId)

        for ((id, profile) in profiles) {
            editor.putString("${id}_name", profile.name)
            editor.putInt("${id}_gridColumns", profile.gridColumns)
            editor.putInt("${id}_gridRows", profile.gridRows)
            editor.putInt("${id}_hotseatColumns", profile.hotseatColumns)
            editor.putString("${id}_dockMode", profile.dockMode)
            editor.putBoolean("${id}_showSearchBar", profile.showSearchBar)
            editor.putFloat("${id}_iconSizeFactor", profile.iconSizeFactor)
            editor.putInt("${id}_density", profile.density)
        }

        editor.putStringSet("profile_ids", profiles.keys)
        editor.apply()
    }

    private fun loadProfiles() {
        val profileIds = prefs.getStringSet("profile_ids", emptySet()) ?: emptySet()
        activeProfileId = prefs.getString("active_profile_id", "default") ?: "default"

        for (id in profileIds) {
            val name = prefs.getString("${id}_name", null) ?: continue
            profiles[id] = DesktopProfile(
                id = id,
                name = name,
                gridColumns = prefs.getInt("${id}_gridColumns", 5),
                gridRows = prefs.getInt("${id}_gridRows", 6),
                hotseatColumns = prefs.getInt("${id}_hotseatColumns", 5),
                dockMode = prefs.getString("${id}_dockMode", "PHONE") ?: "PHONE",
                showSearchBar = prefs.getBoolean("${id}_showSearchBar", true),
                iconSizeFactor = prefs.getFloat("${id}_iconSizeFactor", 1.0f),
                density = prefs.getInt("${id}_density", 0)
            )
        }

        if (profiles.isEmpty()) {
            createDefaultProfile()
        }
    }
}
