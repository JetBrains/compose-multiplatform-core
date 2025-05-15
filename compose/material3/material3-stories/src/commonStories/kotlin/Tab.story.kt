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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `Tab Story` by story {
    // State for tab selection
    var selected by remember { mutableStateOf(false) }

    // Common parameters
    val enabled by parameter(true)
    val tabText by parameter("Tab")

    // Icon parameters
    val showIcon by parameter(true)
    val iconOptions = listOf("Home", "Favorite", "Settings")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Icon")

    // Color parameters
    val useCustomColors by parameter(false)
    val selectedContentColor by parameter(Color(0xFF6750A4))
    val unselectedContentColor by parameter(Color(0xFF79747E))

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    val icon = when (iconTypeInt) {
        1 -> Icons.Filled.Favorite
        2 -> Icons.Filled.Settings
        else -> Icons.Filled.Home
    }

    // Create a surface to provide a background for the tab
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .width(200.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display the current state
            Text(
                text = "Selected: ${if (selected) "Yes" else "No"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // The Tab component
            Tab(
                selected = selected,
                onClick = { selected = !selected },
                enabled = enabled,
                text = {
                    Text(
                        text = tabText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                icon = if (showIcon) {
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = iconContentDescription,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else null,
                selectedContentColor = if (useCustomColors) selectedContentColor else MaterialTheme.colorScheme.primary,
                unselectedContentColor = if (useCustomColors) unselectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

val `LeadingIconTab Story` by story {
    // Tab selection state as a parameter
    var selected by parameter(false)

    // Common parameters
    val enabled by parameter(true)
    val tabText by parameter("Tab")

    // Icon parameters
    val iconOptions = listOf("Home", "Favorite", "Settings")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Icon")

    // Color parameters
    val useCustomColors by parameter(false)
    val selectedContentColor by parameter(Color(0xFF6750A4))
    val unselectedContentColor by parameter(Color(0xFF79747E))

    // Show the current content color based on selection
    val currentContentColor = if (selected) {
        if (useCustomColors) selectedContentColor else MaterialTheme.colorScheme.primary
    } else {
        if (useCustomColors) unselectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    val icon = when (iconTypeInt) {
        1 -> Icons.Filled.Favorite
        2 -> Icons.Filled.Settings
        else -> Icons.Filled.Home
    }

    // Create a surface to provide a background for the tab
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .width(200.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display the current state
            Text(
                text = "Selected: ${if (selected) "Yes" else "No"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Status text showing current content color
            Text(
                text = "Current color: ${if (selected) "Primary" else "OnSurfaceVariant"}",
                style = MaterialTheme.typography.bodySmall,
                color = currentContentColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // The LeadingIconTab component
            androidx.compose.material3.LeadingIconTab(
                selected = selected,
                onClick = { selected = !selected },
                enabled = enabled,
                text = {
                    Text(
                        text = tabText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        modifier = Modifier.size(24.dp)
                    )
                },
                selectedContentColor = if (useCustomColors) selectedContentColor else MaterialTheme.colorScheme.primary,
                unselectedContentColor = if (useCustomColors) unselectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
