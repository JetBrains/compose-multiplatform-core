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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `NavigationRail Story` by story {
    // Basic parameters
    val itemCount by parameter(listOf("Three", "Four", "Five"), 0)
    val alwaysShowLabel by parameter(true)
    val differentIconsForStates by parameter(false)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(MaterialTheme.colorScheme.surface)
    val contentColor by parameter(MaterialTheme.colorScheme.onSurface)

    // Item color parameters
    val useCustomItemColors by parameter(false)
    val selectedIconColor by parameter(MaterialTheme.colorScheme.primary)
    val selectedTextColor by parameter(MaterialTheme.colorScheme.primary)
    val unselectedIconColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    val unselectedTextColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

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

    Box(
        modifier = Modifier.width(380.dp)
    ) {
        Row {
            NavigationRail(
                containerColor = if (useCustomColors) containerColor else NavigationRailDefaults.ContainerColor,
                contentColor = if (useCustomColors) contentColor else contentColorFor(
                    if (useCustomColors) containerColor else NavigationRailDefaults.ContainerColor
                ),
                modifier = Modifier.height(500.dp).padding(top = 80.dp)
            ) {
                items.forEachIndexed { index, item ->
                    NavigationRailItem(
                        icon = { 
                            Icon(
                                imageVector = if (differentIconsForStates) {
                                    if (selectedItem == index) selectedIcons[index] else unselectedIcons[index]
                                } else {
                                    selectedIcons[index]
                                },
                                contentDescription = item,
                                modifier = Modifier.size(24.dp)
                            ) 
                        },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = { selectedItem = index },
                        alwaysShowLabel = alwaysShowLabel,
                        colors = if (useCustomItemColors) {
                            NavigationRailItemDefaults.colors(
                                selectedIconColor = selectedIconColor,
                                selectedTextColor = selectedTextColor,
                                unselectedIconColor = unselectedIconColor,
                                unselectedTextColor = unselectedTextColor
                            )
                        } else {
                            NavigationRailItemDefaults.colors()
                        }
                    )
                }
            }

            // Content area to show the NavigationRail in context
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Context area",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = selectedIcons[selectedItem.coerceIn(0, selectedIcons.size - 1)],
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

val `NavigationRailItem Story` by story {
    // Basic parameters
    var selected by parameter(false)
    val enabled by parameter(true)

    // Icon parameters
    val iconOptions = listOf("Home", "Messages", "Favorites", "Profile", "Settings")
    val iconType by parameter(iconOptions, 0)

    // Label parameters
    val showLabel by parameter(true)
    val labelText by parameter("Home")

    // Color parameters
    val useCustomColors by parameter(false)
    val selectedIconColor by parameter(MaterialTheme.colorScheme.primary)
    val selectedTextColor by parameter(MaterialTheme.colorScheme.primary)
    val indicatorColor by parameter(MaterialTheme.colorScheme.primaryContainer)
    val unselectedIconColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    val unselectedTextColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
    val colors = if (useCustomColors) {
        NavigationRailItemDefaults.colors(
            selectedIconColor = selectedIconColor,
            selectedTextColor = selectedTextColor,
            indicatorColor = indicatorColor,
            unselectedIconColor = unselectedIconColor,
            unselectedTextColor = unselectedTextColor,
            disabledIconColor = disabledIconColor,
            disabledTextColor = disabledTextColor
        )
    } else {
        NavigationRailItemDefaults.colors()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(72.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                NavigationRailItem(
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
                    enabled = enabled,
                    colors = colors,
                    interactionSource = interactionSource
                )
            }
        }
    }
}
