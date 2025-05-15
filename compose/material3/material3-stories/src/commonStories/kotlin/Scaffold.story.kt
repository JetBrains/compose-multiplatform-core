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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story
import kotlinx.coroutines.launch

val `Scaffold Story` by story {
    // Main parameters
    val showTopBar by parameter(true)
    val showBottomBar by parameter(true)
    val showFAB by parameter(true)
    val showSnackbarHost by parameter(true)
    
    // Bottom bar type
    val bottomBarType by parameter(listOf("NavigationBar", "BottomAppBar"), 0)
    
    // FAB type
    val fabType by parameter(listOf("Regular", "Small", "Large"), 0)
    
    // FAB position
    val fabPosition by parameter(listOf("Center", "End"), 1)
    
    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(MaterialTheme.colorScheme.background)
    val contentColor by parameter(contentColorFor(MaterialTheme.colorScheme.background))
    
    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Selected navigation item state
    var selectedItem by remember { mutableIntStateOf(0) }
    
    // Define navigation icons
    val navigationItems = listOf(
        "Home" to Icons.Filled.Home,
        "Favorites" to Icons.Filled.Favorite,
        "Profile" to Icons.Filled.Person,
        "Settings" to Icons.Filled.Settings
    )
    
    // FAB position for Scaffold
    val scaffoldFabPosition = when (fabPosition) {
        "Center" -> FabPosition.Center
        else -> FabPosition.End
    }

    Scaffold(
        modifier = Modifier.width(380.dp),
        topBar = {
            if (showTopBar) {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text("Scaffold Demo") },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                when (bottomBarType) {
                    "BottomAppBar" -> {
                        BottomAppBar {
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Favorite, contentDescription = "Favorite")
                            }
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                    else -> {
                        NavigationBar {
                            navigationItems.forEachIndexed { index, (label, icon) ->
                                NavigationBarItem(
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    selected = index == selectedItem,
                                    onClick = { 
                                        selectedItem = index
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Selected $label")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = {
            if (showSnackbarHost) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
        floatingActionButton = {
            if (showFAB) {
                when (fabType) {
                    "Small" -> {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Small FAB clicked")
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
                    }
                    "Large" -> {
                        LargeFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Large FAB clicked")
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
                    }
                    else -> {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("FAB clicked")
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
                    }
                }
            }
        },
        floatingActionButtonPosition = scaffoldFabPosition,
        containerColor = if (useCustomColors) containerColor else MaterialTheme.colorScheme.background,
        contentColor = if (useCustomColors) contentColor else contentColorFor(
            if (useCustomColors) containerColor else MaterialTheme.colorScheme.background
        )
    ) { paddingValues ->
        // Main content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Text(
                    text = "Content Area",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                
                if (selectedItem >= 0 && selectedItem < navigationItems.size) {
                    Text(
                        text = "Selected tab: ${navigationItems[selectedItem].first}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}
