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

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `IconButton Story` by story {
    // Common parameters
    val enabled by parameter(true)

    // Icon parameters
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Icon Button")

    // Size parameters
    val iconSizeFloat by parameter(24f)

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color(0xFFE8DEF8))
    val customContentColor by parameter(Color(0xFF6750A4))

    // State for tracking clicks
    var clickCount by remember { mutableStateOf(0) }

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    val icon = when (iconTypeInt) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }

    IconButton(
        onClick = { clickCount++ },
        enabled = enabled,
        colors = if (useCustomColors) {
            IconButtonDefaults.iconButtonColors(
                containerColor = customContainerColor,
                contentColor = customContentColor
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        },
        modifier = Modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(iconSizeFloat.dp)
        )
    }
}

// A hack for development needs:
// If we need to customize the parameters controller UI, for example for a missing parameter type,
// then we can do it here.
// This relies on the fact that Storytale compile plugin will invoke the initialization of all properties in any file with stories.
private val initialization: Int = initializationForParameters()
private fun initializationForParameters(): Int {
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    org.jetbrains.compose.storytale.gallery.material3.parameterUiControllerCustomizer = null
    // org.jetbrains.compose.storytale.gallery.material3.ParameterUiControllerCustomizer { { Text(it.name) } }

    return 1
}
