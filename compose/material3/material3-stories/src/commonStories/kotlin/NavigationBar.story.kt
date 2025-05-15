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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `NavigationBar Story` by story {
    // Basic parameters
    val itemCount by parameter(listOf("Three", "Four", "Five"), 0)
    val alwaysShowLabel by parameter(true)
    val useDifferentIconsForStates by parameter(false)

    // NavigationBar parameters
    val containerColor by parameter(NavigationBarDefaults.containerColor)
    val contentColor by parameter(MaterialTheme.colorScheme.contentColorFor(NavigationBarDefaults.containerColor))
    val tonalElevation by parameter(3f)
    val windowInsets by parameter(NavigationBarDefaults.windowInsets)

    // Item color parameters
    val useCustomItemColors by parameter(false)
    val selectedIconColor by parameter(MaterialTheme.colorScheme.onSecondaryContainer)
    val selectedTextColor by parameter(MaterialTheme.colorScheme.onSecondaryContainer)
    val indicatorColor by parameter(MaterialTheme.colorScheme.secondaryContainer)
    val unselectedIconColor by parameter(MaterialTheme.colorScheme.onSurfaceVariant)
    val unselectedTextColor by parameter(MaterialTheme.colorScheme.onSurfaceVariant)

    // State
    var selectedItem by remember { mutableIntStateOf(0) }

    // Define items based on the selected count
    val items = when (itemCount) {
        "Five" -> listOf("Home", "Messages", "Favorites", "Profile", "Settings")
        "Four" -> listOf("Home", "Messages", "Favorites", "Profile")
        else -> listOf("Home", "Favorites", "Profile")
    }

    // Define selected icons
    val selectedIcons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Email,
        Icons.Filled.Favorite,
        Icons.Filled.Person,
        Icons.Filled.Settings
    )

    // Define unselected icons (used only if useDifferentIconsForStates is true)
    val unselectedIcons = listOf(
        Icons.Outlined.Home,
        Icons.Outlined.Email,
        Icons.Outlined.Favorite,
        Icons.Outlined.Person,
        Icons.Outlined.Settings
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.width(400.dp).height(500.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Content area (takes available space)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = selectedIcons[selectedItem.coerceIn(
                            0,
                            selectedIcons.size - 1
                        )],
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${items[selectedItem.coerceIn(0, items.size - 1)]} Screen",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Selected item: ${selectedItem + 1} of ${items.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Navigation bar at the bottom (outside the Box)
            NavigationBar(
                modifier = Modifier.fillMaxWidth(),
                containerColor = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation.dp,
                windowInsets = windowInsets
            ) {
                // Use RowScope to properly layout items
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedItem == index,
                        onClick = { selectedItem = index },
                        icon = {
                            Icon(
                                imageVector = if (useDifferentIconsForStates) {
                                    if (selectedItem == index) selectedIcons[index] else unselectedIcons[index]
                                } else {
                                    selectedIcons[index]
                                },
                                contentDescription = item
                            )
                        },
                        label = { Text(item) },
                        alwaysShowLabel = alwaysShowLabel,
                        colors = if (useCustomItemColors) {
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = selectedIconColor,
                                selectedTextColor = selectedTextColor,
                                indicatorColor = indicatorColor,
                                unselectedIconColor = unselectedIconColor,
                                unselectedTextColor = unselectedTextColor
                            )
                        } else {
                            NavigationBarItemDefaults.colors()
                        }
                    )
                }
            }
        }
    }
}

val `NavigationBarItem Story` by story {
    // Basic parameters
    val selectedParam by parameter(false)
    // Use a separate mutable state that's initialized with the parameter value
    var selected by remember { mutableStateOf(selectedParam) }

    // Update the state when the parameter changes
    if (selected != selectedParam) {
        selected = selectedParam
    }

    val enabled by parameter(true)
    val alwaysShowLabel by parameter(true)

    // Icon parameters
    val iconOptions = listOf("Home", "Messages", "Favorites", "Profile", "Settings")
    val iconType by parameter(iconOptions, 0)

    // Label parameters
    val showLabel by parameter(true)
    val labelText by parameter("Home")

    // Color parameters
    val selectedIconColor by parameter(MaterialTheme.colorScheme.onSecondaryContainer)
    val selectedTextColor by parameter(MaterialTheme.colorScheme.onSecondaryContainer)
    val indicatorColor by parameter(MaterialTheme.colorScheme.secondaryContainer)
    val unselectedIconColor by parameter(MaterialTheme.colorScheme.onSurfaceVariant)
    val unselectedTextColor by parameter(MaterialTheme.colorScheme.onSurfaceVariant)
    val disabledIconColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    val disabledTextColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))

    // Size
    val iconSize by parameter(24f)

    // Interaction source for showing interaction states
    val interactionSource = remember { MutableInteractionSource() }

    // Get the appropriate icon
    val icon = when (iconOptions.indexOf(iconType)) {
        1 -> Icons.Filled.Email
        2 -> Icons.Filled.Favorite
        3 -> Icons.Filled.Person
        4 -> Icons.Filled.Settings
        else -> Icons.Filled.Home
    }

    // Create colors
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = selectedIconColor,
        selectedTextColor = selectedTextColor,
        indicatorColor = indicatorColor,
        unselectedIconColor = unselectedIconColor,
        unselectedTextColor = unselectedTextColor,
        disabledIconColor = disabledIconColor,
        disabledTextColor = disabledTextColor
    )

    Box(
        modifier = Modifier.width(400.dp).height(500.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.width(400.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                // Status information
                Text(
                    text = "Status: ${if (selected) "Selected" else "Not Selected"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "State: ${if (enabled) "Enabled" else "Disabled"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // The NavigationBar with a single item
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    // Single NavigationBarItem in a RowScope
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selected = !selected },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = iconType,
                                modifier = Modifier.size(iconSize.dp)
                            )
                        },
                        label = if (showLabel) {
                            { Text(labelText) }
                        } else null,
                        alwaysShowLabel = alwaysShowLabel,
                        enabled = enabled,
                        colors = colors,
                        interactionSource = interactionSource
                    )
                }
            }
        }
    }
}
