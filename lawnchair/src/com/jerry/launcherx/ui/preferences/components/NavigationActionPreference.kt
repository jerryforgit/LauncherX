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

package com.jerry.launcherx.ui.preferences.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jerry.launcherx.ui.preferences.LocalNavController
import com.jerry.launcherx.ui.preferences.components.layout.PreferenceTemplate
import com.jerry.launcherx.ui.preferences.navigation.PreferenceRoute
import com.jerry.launcherx.ui.theme.LauncherXTheme
import com.jerry.launcherx.ui.util.preview.PreferenceGroupPreviewContainer
import com.jerry.launcherx.ui.util.preview.PreviewLauncherX
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

@Composable
fun NavigationActionPreference(
    label: String,
    modifier: Modifier = Modifier,
    destination: PreferenceRoute? = null,
    subtitle: String? = null,
    endWidget: (@Composable () -> Unit)? = null,
) {
    val navController = if (destination != null) LocalNavController.current else null
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)

    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = modifier,
        description = subtitle?.let { { Text(text = it) } },
        endWidget = endWidget,
        onClick = if (destination != null) {
            {
                mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                navController?.navigate(
                    route = destination,
                )
            }
        } else {
            null
        },
    )
}

@PreviewLauncherX
@Composable
private fun SliderPreferencePreview() {
    LauncherXTheme {
        PreferenceGroupPreviewContainer {
            NavigationActionPreference(
                label = "Label",
                modifier = Modifier,
                destination = null,
                subtitle = "Subtitle",
                endWidget = { Text("End") },
            )
        }
    }
}
