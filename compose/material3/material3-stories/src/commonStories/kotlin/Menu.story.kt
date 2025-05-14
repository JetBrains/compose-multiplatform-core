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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `DropdownMenu Story` by story {
    // Menu customization parameters
    val numItems by parameter(4) // Values between 1-4
    val menuWidth by parameter(180f)
    val useLightBackground by parameter(false)
    
    // State to control menu visibility
    var showMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.background
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
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (useLightBackground) 
                                MaterialTheme.colorScheme.surfaceContainerLow
                            else 
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            // Menu items - show based on numItems parameter (values 1-4)
                            if (numItems >= 1) {
                                DropdownMenuItem(
                                    text = { Text("Create New Item") },
                                    onClick = { showMenu = false },
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            
                            if (numItems >= 2) {
                                DropdownMenuItem(
                                    text = { Text("Edit Profile") },
                                    onClick = { showMenu = false },
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            
                            if (numItems >= 3) {
                                DropdownMenuItem(
                                    text = { Text("Add to Favorites") },
                                    onClick = { showMenu = false },
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            
                            if (numItems >= 4) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                DropdownMenuItem(
                                    text = { Text("Share with Friends") },
                                    onClick = { showMenu = false },
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = null
                                        )
                                    }
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
        text = { Text(itemText) },
        onClick = { /* Action on click */ },
        modifier = Modifier.width(200.dp),
        leadingIcon = if (showLeadingIcon) {
            {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Add to favorites"
                )
            }
        } else null,
        trailingIcon = if (showTrailingIcon) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected"
                )
            }
        } else null,
        enabled = enabled
    )
}
