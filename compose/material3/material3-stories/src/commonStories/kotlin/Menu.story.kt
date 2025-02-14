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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `DropdownMenu Story` by story {
    // Menu customization parameters
    val numItems by parameter(4) // Values between 1-4
    val menuWidth by parameter(180f)
    val useHighlightedBackground by parameter(false)

    // State to control menu visibility
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .width(250.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "DropdownMenu Demo",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Container with fixed height that holds both the button and menu
            Box(
                modifier = Modifier.height(300.dp),
                contentAlignment = Alignment.TopStart
            ) {
                // Menu button
                IconButton(
                    onClick = { showMenu = !showMenu }
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Menu"
                    )
                }

                // Custom menu implementation using ElevatedCard
                if (showMenu) {
                    ElevatedCard(
                        modifier = Modifier
                            .padding(top = 40.dp)
                            .width(menuWidth.dp),
                        shape = RoundedCornerShape(4.dp),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 8.dp, // Increased elevation for better visibility in dark theme
                            pressedElevation = 12.dp
                        ),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (useHighlightedBackground)
                                MaterialTheme.colorScheme.surfaceContainerLow
                            else 
                                MaterialTheme.colorScheme.surfaceContainerHigh // Using surfaceContainerHigh for better contrast in dark theme
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            // Menu items - show based on numItems parameter (values 1-4)
                            if (numItems >= 1) {
                                DropdownMenuItem(
                                    text = { Text("Create New Item", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = { showMenu = false },
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        leadingIconColor = MaterialTheme.colorScheme.onSurface,
                                        trailingIconColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            if (numItems >= 2) {
                                DropdownMenuItem(
                                    text = { Text("Edit Profile", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = { showMenu = false },
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        leadingIconColor = MaterialTheme.colorScheme.onSurface,
                                        trailingIconColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            if (numItems >= 3) {
                                DropdownMenuItem(
                                    text = { Text("Add to Favorites", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = { showMenu = false },
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        leadingIconColor = MaterialTheme.colorScheme.onSurface,
                                        trailingIconColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            if (numItems >= 4) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                DropdownMenuItem(
                                    text = { Text("Share with Friends", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = { showMenu = false },
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        leadingIconColor = MaterialTheme.colorScheme.onSurface,
                                        trailingIconColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Click the Menu button to open the dropdown. " +
                    "Valid values for the numItems parameter " +
                    "in the current implementation range from 0 to 4.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

val `DropdownMenuItem Story` by story {
    // Parameters for customization
    val itemText by parameter("Add to Favorites")
    val enabled by parameter(true)
    val showLeadingIcon by parameter(true)
    val showTrailingIcon by parameter(false)

    MenuDefaults.DropdownMenuItemContentPadding

    DropdownMenuItem(
        text = { Text(itemText, color = MaterialTheme.colorScheme.onSurface) },
        onClick = { /* Action on click */ },
        modifier = Modifier.width(200.dp),
        leadingIcon = if (showLeadingIcon) {
            {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Add to favorites",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        } else null,
        trailingIcon = if (showTrailingIcon) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        } else null,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurface,
            trailingIconColor = MaterialTheme.colorScheme.onSurface,
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    )
}
