package com.jerry.launcherx.modern

import android.content.Context
import com.jerry.launcherx.modern.backup.LayoutBackupManager
import com.jerry.launcherx.modern.dock.AdaptiveDockView
import com.jerry.launcherx.modern.dock.DockLayoutManager
import com.jerry.launcherx.modern.folder.SmartFolderManager
import com.jerry.launcherx.modern.gesture.MultiTouchGestureView
import com.jerry.launcherx.modern.input.DesktopInputAdapter
import com.jerry.launcherx.modern.profile.DesktopProfileManager
import com.jerry.launcherx.modern.profile.LayoutLockManager
import com.jerry.launcherx.modern.search.UniversalSearchEngine
import com.jerry.launcherx.modern.window.FreeformWindowManager
import com.jerry.launcherx.modern.widget.SmartStackManager

class ModernDesktopManager private constructor(private val context: Context) {

    val smartFolderManager = SmartFolderManager(context)
    val dockLayoutManager = DockLayoutManager(context)
    val desktopProfileManager = DesktopProfileManager(context)
    val layoutLockManager = LayoutLockManager(context)
    val smartStackManager = SmartStackManager(context)
    val universalSearchEngine = UniversalSearchEngine(context)
    val freeformWindowManager = FreeformWindowManager(context)
    val desktopInputAdapter = DesktopInputAdapter(context)
    val layoutBackupManager = LayoutBackupManager(context)

    private val gestureCallback = object : MultiTouchGestureView.GestureCallback {
        override fun onThreeFingerSwipeLeft() {
            // Switch to previous desktop profile
            val profiles = desktopProfileManager.getAllProfiles()
            val current = desktopProfileManager.getActiveProfile()
            val idx = profiles.indexOfFirst { it.id == current.id }
            if (idx > 0) {
                desktopProfileManager.setActiveProfile(profiles[idx - 1].id)
                onProfileChanged?.invoke(profiles[idx - 1])
            }
        }

        override fun onThreeFingerSwipeRight() {
            // Switch to next desktop profile
            val profiles = desktopProfileManager.getAllProfiles()
            val current = desktopProfileManager.getActiveProfile()
            val idx = profiles.indexOfFirst { it.id == current.id }
            if (idx < profiles.size - 1) {
                desktopProfileManager.setActiveProfile(profiles[idx + 1].id)
                onProfileChanged?.invoke(profiles[idx + 1])
            }
        }

        override fun onFourFingerSwipeUp() {
            // Show recents
            onShowRecents?.invoke()
        }

        override fun onFourFingerSwipeDown() {
            // Show all apps
            onShowAllApps?.invoke()
        }

        override fun onThreeFingerTap() {
            // Show universal search
            onShowSearch?.invoke()
        }
    }

    var onProfileChanged: ((DesktopProfileManager.DesktopProfile) -> Unit)? = null
    var onShowRecents: (() -> Unit)? = null
    var onShowAllApps: (() -> Unit)? = null
    var onShowSearch: (() -> Unit)? = null

    init {
        smartStackManager.loadAllStacks()
    }

    fun getGestureCallback(): MultiTouchGestureView.GestureCallback = gestureCallback

    fun detectDeviceMode(screenWidthDp: Int, isTablet: Boolean): AdaptiveDockView.DockMode {
        return dockLayoutManager.getEffectiveMode(screenWidthDp, isTablet)
    }

    fun onAppLaunched(packageName: String) {
        val recentItem = AdaptiveDockView.DockItem(
            id = System.currentTimeMillis(),
            packageName = packageName,
            label = packageName,
            icon = null,
            isRecent = true,
            isPinned = false
        )
    }

    fun applyProfile(profile: DesktopProfileManager.DesktopProfile) {
        desktopProfileManager.setActiveProfile(profile.id)
    }

    fun createDefaultPresets() {
        desktopProfileManager.createOfficeProfile()
        desktopProfileManager.createEntertainmentProfile()
        desktopProfileManager.createMinimalProfile()
    }

    companion object {
        @Volatile
        private var instance: ModernDesktopManager? = null

        fun getInstance(context: Context): ModernDesktopManager {
            return instance ?: synchronized(this) {
                instance ?: ModernDesktopManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
