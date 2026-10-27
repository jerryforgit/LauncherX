package com.jerry.launcherx.modern

import android.content.Context
import kotlin.math.max
import kotlin.math.min

class TabletDeviceProfileHelper(private val context: Context) {

    data class TabletGridConfig(
        val columns: Int,
        val rows: Int,
        val hotseatColumns: Int,
        val iconSizeDp: Float,
        val cellSpacingDp: Float,
        val dockHeightDp: Float
    )

    fun getConfigForScreen(screenWidthDp: Int, screenHeightDp: Int): TabletGridConfig {
        val minDim = min(screenWidthDp, screenHeightDp)
        val maxDim = max(screenWidthDp, screenHeightDp)
        val isLandscape = screenWidthDp > screenHeightDp

        return when {
            minDim >= 600 && maxDim >= 900 -> {
                if (isLandscape) {
                    TabletGridConfig(10, 6, 10, 48f, 8f, 72f)
                } else {
                    TabletGridConfig(6, 10, 6, 52f, 8f, 72f)
                }
            }
            minDim >= 480 -> {
                if (isLandscape) {
                    TabletGridConfig(8, 5, 8, 44f, 6f, 64f)
                } else {
                    TabletGridConfig(5, 8, 5, 48f, 6f, 64f)
                }
            }
            else -> {
                if (isLandscape) {
                    TabletGridConfig(7, 4, 7, 40f, 4f, 56f)
                } else {
                    TabletGridConfig(4, 7, 4, 44f, 4f, 56f)
                }
            }
        }
    }

    fun shouldUseTabletLayout(screenWidthDp: Int): Boolean = screenWidthDp >= 600
    fun shouldUseDesktopLayout(screenWidthDp: Int): Boolean = screenWidthDp >= 900

    fun getHotseatColumnsForWidth(screenWidthDp: Int, isLandscape: Boolean): Int {
        return when {
            screenWidthDp >= 900 -> if (isLandscape) 10 else 6
            screenWidthDp >= 600 -> if (isLandscape) 8 else 5
            else -> if (isLandscape) 7 else 4
        }
    }
}
