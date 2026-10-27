/*
 * Copyright 2021, Lawnchair
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

package com.jerry.launcherx.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.jerry.launcherx.preferences.observeAsState
import com.jerry.launcherx.preferences.preferenceManager
import com.jerry.launcherx.preferences2.asState
import com.jerry.launcherx.preferences2.preferenceManager2
import com.jerry.launcherx.theme.ThemeProvider
import com.jerry.launcherx.theme.toComposeColorScheme
import com.jerry.launcherx.ui.preferences.components.ThemeChoice
import com.jerry.launcherx.wallpaper.WallpaperManagerCompat
import com.android.launcher3.Utilities

@Composable
fun LauncherXTheme(
    darkTheme: Boolean = isSelectedThemeDark,
    content: @Composable () -> Unit,
) {
    val colorScheme = getColorScheme(darkTheme = darkTheme)
    val context = LocalContext.current
    // Google Sans Flex has no CJK glyphs; fall back to the platform font on non-English systems so
    // Latin and CJK text share one family and weight.
    val typography = if (Utilities.isEnglishLanguage(context)) Typography else SystemTypography
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
        shapes = Shapes,
        motionScheme = MotionScheme.expressive(),
    )
}

@Composable
fun ComponentActivity.EdgeToEdge() {
    val darkTheme = isSelectedThemeDark
    val scrimColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f).toArgb()
    val contentColor = MaterialTheme.colorScheme.onSurface.toArgb()

    LaunchedEffect(darkTheme) {
        val statusBarStyle = SystemBarStyle.auto(
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            detectDarkMode = { darkTheme },
        )

        val navigationBarStyle = if (!darkTheme) {
            SystemBarStyle.light(scrimColor, contentColor)
        } else {
            SystemBarStyle.dark(scrimColor)
        }

        enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle,
        )
    }
}

@Composable
fun getColorScheme(darkTheme: Boolean): ColorScheme {
    if (LocalInspectionMode.current) return getPreviewColorScheme(darkTheme)

    val context = LocalContext.current
    val preferenceManager2 = preferenceManager2()
    val accentColor by preferenceManager2.accentColor.asState()
    val colorStyle by preferenceManager2.colorStyle.asState()

    val colorScheme = remember(accentColor, colorStyle.style) {
        ThemeProvider.INSTANCE.get(context).colorScheme
    }

    return colorScheme.toComposeColorScheme(isDark = darkTheme)
}

private fun getPreviewColorScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme()
} else {
    expressiveLightColorScheme()
}

val isSelectedThemeDark: Boolean
    @Composable get() {
        if (LocalInspectionMode.current) return isAutoThemeDark

        val themeChoice by preferenceManager().launcherTheme.observeAsState()
        return when (themeChoice) {
            ThemeChoice.LIGHT -> false
            ThemeChoice.DARK -> true
            else -> isAutoThemeDark
        }
    }

val isAutoThemeDark: Boolean @Composable get() =
    if (LocalInspectionMode.current || Utilities.ATLEAST_P) {
        isSystemInDarkTheme()
    } else {
        wallpaperSupportsDarkTheme
    }

val wallpaperSupportsDarkTheme: Boolean
    @Composable get() {
        val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(LocalContext.current)
        var supportsDarkTheme by remember { mutableStateOf(wallpaperManager.supportsDarkTheme) }

        DisposableEffect(wallpaperManager) {
            val listener = object : WallpaperManagerCompat.OnColorsChangedListener {
                override fun onColorsChanged() {
                    supportsDarkTheme = wallpaperManager.supportsDarkTheme
                }
            }
            wallpaperManager.addOnChangeListener(listener)
            onDispose { wallpaperManager.removeOnChangeListener(listener) }
        }
        return supportsDarkTheme
    }
