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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Basic Navigation Bar with three items */
@Composable
fun BasicNavigationBarExample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Favorites", "Profile")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Favorite,
        Icons.Filled.Person
    )
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        NavigationBar {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = { Icon(icons[index], contentDescription = item) },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index }
                )
            }
        }
    }
}

/** Navigation Bar with four items */
@Composable
fun NavigationBarWithFourItemsExample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Messages", "Favorites", "Profile")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Email,
        Icons.Filled.Favorite,
        Icons.Filled.Person
    )
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        NavigationBar {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = { Icon(icons[index], contentDescription = item) },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index }
                )
            }
        }
    }
}

/** Navigation Bar with five items */
@Composable
fun NavigationBarWithFiveItemsExample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Messages", "Favorites", "Profile", "Settings")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Email,
        Icons.Filled.Favorite,
        Icons.Filled.Person,
        Icons.Filled.Settings
    )
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        NavigationBar {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = { Icon(icons[index], contentDescription = item) },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index }
                )
            }
        }
    }
}

/** Navigation Bar with selected/unselected icons */
@Composable
fun NavigationBarWithDifferentIconsExample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Messages", "Favorites", "Profile", "Settings")
    val selectedIcons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Email,
        Icons.Filled.Favorite,
        Icons.Filled.Person,
        Icons.Filled.Settings
    )
    val unselectedIcons = listOf(
        Icons.Outlined.Home,
        Icons.Outlined.Email,
        Icons.Outlined.Favorite,
        Icons.Outlined.Person,
        Icons.Outlined.Settings
    )
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        NavigationBar {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = { 
                        Icon(
                            if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                            contentDescription = item
                        ) 
                    },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index }
                )
            }
        }
    }
}

/** Navigation Bar with custom colors */
@Composable
fun NavigationBarWithCustomColorsExample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Favorites", "Profile")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Favorite,
        Icons.Filled.Person
    )
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = { Icon(icons[index], contentDescription = item) },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

/** Navigation Bar with alwaysShowLabel = false */
@Composable
fun NavigationBarWithHiddenLabelsExample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Favorites", "Profile")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Favorite,
        Icons.Filled.Person
    )
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        NavigationBar {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = { Icon(icons[index], contentDescription = item) },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index },
                    alwaysShowLabel = false
                )
            }
        }
    }
} 