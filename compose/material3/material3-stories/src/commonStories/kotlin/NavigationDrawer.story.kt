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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `PermanentNavigationDrawer Story` by story {
    // Parameters for customization
    val drawerWidth by parameter(240f)
    val drawerTitle by parameter("Permanent Drawer")
    val contentTitle by parameter("Content Area")
    val numItems by parameter(5)

    // State management
    var selectedItem by remember { mutableStateOf(0) }

    // Create items and icons lists
    val items = listOf("Inbox", "Sent", "Drafts", "Favorites", "Trash").take(numItems)
    val icons = listOf(
        Icons.Default.Home,
        Icons.Default.Email,
        Icons.Default.Favorite,
        Icons.Default.Settings,
        Icons.Default.Face
    ).take(numItems)

    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(Modifier.width(drawerWidth.dp)) {
                Spacer(Modifier.height(24.dp))
                Text(
                    drawerTitle,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(16.dp))
                items.forEachIndexed { index, item ->
                    NavigationDrawerItem(
                        icon = { Icon(icons[index], contentDescription = null) },
                        label = { Text(item) },
                        selected = index == selectedItem,
                        onClick = {
                            selectedItem = index
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    // Add a fixed height constraint to prevent "Size out of range" error
                    .height(600.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    contentTitle,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(20.dp))
                Text("Selected: ${items[selectedItem]}")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
val `DismissibleDrawerSheet Story` by story {
    // Shape parameters
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(8f)

    // Color parameters
    val useCustomColors by parameter(false)
    val drawerContainerColor by parameter(MaterialTheme.colorScheme.surface)
    val drawerContentColor by parameter(MaterialTheme.colorScheme.onSurface)

    // Elevation parameters
    val drawerTonalElevationValue by parameter(1f)

    // Content parameters
    val titleText by parameter("Drawer Content")
    val numItems by parameter(6)

    // Determine shape based on selection
    val drawerShape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        RectangleShape
    }

    // Create the DismissibleDrawerSheet
    DismissibleDrawerSheet(
        drawerShape = drawerShape,
        drawerContainerColor = drawerContainerColor,
        drawerContentColor = drawerContentColor,
        drawerTonalElevation = drawerTonalElevationValue.dp
    ) {
        Column(
            modifier = Modifier.width(300.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(8.dp))

            // Add navigation items
            repeat(numItems) { index ->
                NavigationDrawerItem(
                    label = { Text("Item ${index + 1}") },
                    selected = index == 0,
                    onClick = { /* Handle click */ },
                    icon = {
                        Icon(
                            imageVector = when (index % 5) {
                                0 -> Icons.Default.Home
                                1 -> Icons.Default.Favorite
                                2 -> Icons.Default.Settings
                                3 -> Icons.Default.Email
                                else -> Icons.Default.Face
                            },
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    }
}
