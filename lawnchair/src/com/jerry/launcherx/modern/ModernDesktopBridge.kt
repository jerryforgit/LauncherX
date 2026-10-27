package com.jerry.launcherx.modern

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.jerry.launcherx.LauncherXLauncher
import com.jerry.launcherx.modern.dock.AdaptiveDockView
import com.jerry.launcherx.modern.dock.DockLayoutManager
import com.jerry.launcherx.modern.folder.LargeFolderView
import com.jerry.launcherx.modern.folder.SmartFolderManager
import com.jerry.launcherx.modern.gesture.MultiTouchGestureView
import com.jerry.launcherx.modern.input.DesktopInputAdapter
import com.jerry.launcherx.modern.search.UniversalSearchEngine
import com.jerry.launcherx.modern.search.UniversalSearchOverlay
import com.android.launcher3.Launcher
import com.android.launcher3.R

class ModernDesktopBridge(private val launcher: LauncherXLauncher) {

    private val manager = ModernDesktopManager.getInstance(launcher)
    private var searchOverlay: UniversalSearchOverlay? = null
    private var gestureView: MultiTouchGestureView? = null

    fun onLauncherCreated() {
        injectSearchOverlay()
        injectGestureView()
        setupDesktopInput()
    }

    fun onLauncherResumed() {
        updateDockMode()
    }

    private fun injectSearchOverlay() {
        val rootView = launcher.findViewById<ViewGroup>(android.R.id.content) ?: return
        searchOverlay = UniversalSearchOverlay(launcher).apply {
            id = View.generateViewId()
            onResultClicked = { result ->
                manager.universalSearchEngine.launchResult(result, launcher)
                searchOverlay?.dismiss()
            }
        }
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        rootView.addView(searchOverlay, params)
    }

    private fun injectGestureView() {
        val rootView = launcher.findViewById<ViewGroup>(android.R.id.content) ?: return
        gestureView = MultiTouchGestureView(launcher).apply {
            id = View.generateViewId()
            isClickable = false
            isFocusable = false
            setGestureCallback(object : MultiTouchGestureView.GestureCallback {
                override fun onThreeFingerSwipeLeft() {
                    val profiles = manager.desktopProfileManager.getAllProfiles()
                    val current = manager.desktopProfileManager.getActiveProfile()
                    val idx = profiles.indexOfFirst { it.id == current.id }
                    if (idx > 0) {
                        launcher.runOnUiThread {
                            manager.desktopProfileManager.setActiveProfile(profiles[idx - 1].id)
                        }
                    }
                }

                override fun onThreeFingerSwipeRight() {
                    val profiles = manager.desktopProfileManager.getAllProfiles()
                    val current = manager.desktopProfileManager.getActiveProfile()
                    val idx = profiles.indexOfFirst { it.id == current.id }
                    if (idx < profiles.size - 1) {
                        launcher.runOnUiThread {
                            manager.desktopProfileManager.setActiveProfile(profiles[idx + 1].id)
                        }
                    }
                }

                override fun onFourFingerSwipeUp() {
                    launcher.runOnUiThread {
                        launcher.rootView.performHapticFeedback(
                            android.view.HapticFeedbackConstants.LONG_PRESS
                        )
                    }
                }

                override fun onThreeFingerTap() {
                    launcher.runOnUiThread {
                        showSearch()
                    }
                }
            })
        }
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        gestureView?.visibility = View.GONE
        rootView.addView(gestureView, params)
    }

    private fun setupDesktopInput() {
        manager.desktopInputAdapter.setCallback(object : com.jerry.launcherx.modern.input.DesktopInputAdapter.DesktopInputCallback {
            override fun onRightClick(view: View, x: Float, y: Float) {
                launcher.runOnUiThread {
                    val popup = manager.desktopInputAdapter.getContextMenu(view, x, y)
                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> { /* App Info */ true }
                            2 -> { /* Uninstall */ true }
                            3 -> { /* Remove */ true }
                            else -> false
                        }
                    }
                    popup.show()
                }
            }
        })
    }

    fun showSearch() {
        searchOverlay?.show()
    }

    fun hideSearch() {
        searchOverlay?.dismiss()
    }

    fun isSearchShowing(): Boolean = searchOverlay?.isShowing() == true

    fun updateDockMode() {
        val dm = launcher.resources.displayMetrics
        val widthDp = (dm.widthPixels / dm.density).toInt()
        val isTablet = widthDp >= 600
        val mode = manager.detectDeviceMode(widthDp, isTablet)
        manager.dockLayoutManager.saveDockMode(mode)
    }
}
