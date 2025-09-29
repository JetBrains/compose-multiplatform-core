/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.xr.compose.material3

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.LocalSpatialConfiguration

/**
 * A composable button that toggles between "Full Space Mode" and "Home Space Mode" in an Android XR
 * application.
 *
 * It adapts to the current spatial mode, visually representing it with either a default icon or
 * provided custom content.
 *
 * @param modifier The modifier to be applied to the button layout.
 * @param colors [IconToggleButtonColors] that will be used to resolve the colors for this button.
 * @param content The content to be displayed inside the button.
 */
@Composable
public fun SpaceModeToggleButton(
    modifier: Modifier = Modifier,
    colors: IconToggleButtonColors = IconButtonDefaults.iconToggleButtonColors(),
    content: @Composable (isFullSpace: Boolean) -> Unit = { isFullSpace ->
        if (isFullSpace) {
            SpaceModeToggleButtonDefaults.CollapsedIcon()
        } else {
            SpaceModeToggleButtonDefaults.ExpandedIcon()
        }
    },
) {
    // Get the current system state for spatial UI
    val isSpatialUiEnabled = LocalSpatialCapabilities.current.isSpatialUiEnabled
    val config = LocalSpatialConfiguration.current

    IconToggleButton(
        colors = colors,
        modifier = modifier,
        checked = isSpatialUiEnabled,
        onCheckedChange = {
            if (isSpatialUiEnabled) {
                config.requestHomeSpaceMode()
            } else {
                config.requestFullSpaceMode()
            }
        },
    ) {
        content(isSpatialUiEnabled)
    }
}

/** Contains the default values used by [SpaceModeToggleButton]. */
public object SpaceModeToggleButtonDefaults {
    /**
     * The default icon displayed when the button is in FullSpaceMode.
     *
     * This icon visually represents the action of switching *out* of FullSpaceMode and collapsing
     * *into* HomeSpaceMode.
     */
    @Composable
    public fun CollapsedIcon() {
        val painter = painterResource(R.drawable.collapse_content_24dp)
        Icon(
            painter = painter,
            contentDescription =
                stringResource(R.string.xr_compose_material3_space_mode_switch_collapse),
        )
    }

    /**
     * The default icon displayed when the button is in HomeSpaceMode.
     *
     * This icon visually represents the action of switching *out* of HomeSpaceMode and expanding
     * *into* FullSpaceMode.
     */
    @Composable
    public fun ExpandedIcon() {
        val painter = painterResource(R.drawable.expand_content_24dp)
        Icon(
            painter = painter,
            contentDescription =
                stringResource(R.string.xr_compose_material3_space_mode_switch_expand),
        )
    }
}
