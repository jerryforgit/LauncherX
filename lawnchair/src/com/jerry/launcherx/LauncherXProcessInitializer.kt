package com.jerry.launcherx

import android.content.Context
import androidx.annotation.Keep
import com.jerry.launcherx.bugreport.LauncherXBugReporter
import com.jerry.launcherx.theme.color.tokens.ColorTokens
import com.android.launcher3.Utilities
import com.android.launcher3.icons.mono.ThemedIconDrawable
import com.android.quickstep.QuickstepProcessInitializer

@Keep
class LauncherXProcessInitializer(context: Context) : QuickstepProcessInitializer(context) {

    override fun init(context: Context) {
        LauncherXBugReporter.INSTANCE.get(context)
        ThemedIconDrawable.COLORS_LOADER = {
            if (Utilities.isDarkTheme(it)) {
                intArrayOf(
                    ColorTokens.Accent2_800.resolveColor(it),
                    ColorTokens.Accent1_200.resolveColor(it),
                )
            } else {
                intArrayOf(
                    ColorTokens.Accent1_100.resolveColor(it),
                    ColorTokens.Accent1_700.resolveColor(it),
                )
            }
        }
        super.init(context)
    }
}
