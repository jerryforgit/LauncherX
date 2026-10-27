package com.jerry.launcherx.modern.profile

import android.content.Context
import android.content.SharedPreferences

class LayoutLockManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "layout_lock_prefs", Context.MODE_PRIVATE
    )

    fun setLayoutLocked(locked: Boolean) {
        prefs.edit().putBoolean("layout_locked", locked).apply()
    }

    fun isLayoutLocked(): Boolean {
        return prefs.getBoolean("layout_locked", false)
    }

    fun toggleLock() {
        setLayoutLocked(!isLayoutLocked())
    }

    fun allowItemMove(): Boolean = !isLayoutLocked()

    fun allowItemRemove(): Boolean = !isLayoutLocked()

    fun allowWidgetResize(): Boolean = !isLayoutLocked()

    fun allowFolderEdit(): Boolean = !isLayoutLocked()

    fun getLockPassword(): String? {
        return prefs.getString("lock_password", null)
    }

    fun setLockPassword(password: String) {
        prefs.edit().putString("lock_password", password).apply()
    }

    fun clearLockPassword() {
        prefs.edit().remove("lock_password").apply()
    }

    fun isPasswordProtected(): Boolean {
        return getLockPassword() != null
    }
}
