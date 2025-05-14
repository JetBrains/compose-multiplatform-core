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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `CenterAlignedTopAppBar Story` by story {
    // Title parameters
    val titleText by parameter("Centered Title")

    // Navigation icon parameters
    val showNavigationIcon by parameter(true)

    // Color parameters
    val containerColor by parameter(MaterialTheme.colorScheme.surface)
    val contentColor by parameter(MaterialTheme.colorScheme.onSurface)
    val iconContentColor by parameter(MaterialTheme.colorScheme.onSurface)

    // Other parameters
    val expandedHeight by parameter(64f)

    Row(modifier = Modifier.width(400.dp)) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = titleText,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (showNavigationIcon) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Menu",
                            tint = iconContentColor
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = iconContentColor
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = iconContentColor
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = containerColor,
                titleContentColor = contentColor,
                navigationIconContentColor = iconContentColor,
                actionIconContentColor = iconContentColor
            ),
            expandedHeight = expandedHeight.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

val `BottomAppBar Story` by story {
    val containerColor by parameter(MaterialTheme.colorScheme.primaryContainer)
    val contentColor by parameter(MaterialTheme.colorScheme.onPrimaryContainer)
    val fabColor by parameter(MaterialTheme.colorScheme.secondary)
    val hasFab by parameter(true)

    BottomAppBar(
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = Modifier.width(400.dp)
    ) {
        // Actions in the left part
        IconButton(onClick = {}) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu")
        }
        IconButton(onClick = {}) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit")
        }

        Spacer(modifier = Modifier.weight(1f))

        // Actions in the right part
        IconButton(onClick = {}) {
            Icon(Icons.Filled.Favorite, contentDescription = "Favorite")
        }

        // FAB, if enabled
        if (hasFab) {
            Spacer(modifier = Modifier.width(16.dp))
            FloatingActionButton(
                onClick = {},
                containerColor = fabColor
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `LargeTopAppBar Story` by story {
    // Title parameters
    val titleText by parameter("Large App Bar Title")
    val titleMaxLines by parameter(1)

    // Navigation icon parameters
    val showNavigationIcon by parameter(true)
    val navigationIconType by parameter(listOf("Menu", "Back"), 0)

    // Action icons parameters
    val actionIconsCount by parameter(listOf("None", "One", "Two", "Three"), 2)

    // Color parameters
    val containerColor by parameter(MaterialTheme.colorScheme.surface)
    val titleContentColor by parameter(MaterialTheme.colorScheme.onSurface)
    val iconsContentColor by parameter(MaterialTheme.colorScheme.onSurface)

    // Height parameters
    val collapsedHeight by parameter(64f)
    val expandedHeight by parameter(152f)

    // Create scroll behavior
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Determine navigation icon
    val navigationIcon = when (navigationIconType) {
        "Back" -> Icons.Filled.ArrowBack
        else -> Icons.Filled.Menu
    }

    Row(modifier = Modifier.width(400.dp)) {
        LargeTopAppBar(
            title = {
                Text(
                    text = titleText,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (showNavigationIcon) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = navigationIcon,
                            contentDescription = navigationIconType,
                            tint = iconsContentColor
                        )
                    }
                }
            },
            actions = {
                when (actionIconsCount) {
                    "One" -> {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = iconsContentColor
                            )
                        }
                    }
                    "Two" -> {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = iconsContentColor
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = iconsContentColor
                            )
                        }
                    }
                    "Three" -> {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = iconsContentColor
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = iconsContentColor
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = iconsContentColor
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = containerColor,
                titleContentColor = titleContentColor,
                navigationIconContentColor = iconsContentColor,
                actionIconContentColor = iconsContentColor
            ),
            collapsedHeight = collapsedHeight.dp,
            expandedHeight = expandedHeight.dp,
            scrollBehavior = scrollBehavior,
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `MediumTopAppBar Story` by story {
    // Title parameters
    val titleText by parameter("Medium App Bar Title")
    val titleMaxLines by parameter(1)

    // Navigation icon parameters
    val showNavigationIcon by parameter(true)
    val navigationIconType by parameter(listOf("Menu", "Back"), 0)

    // Action icons parameters
    val actionIconsCount by parameter(listOf("None", "One", "Two", "Three"), 2)

    // Color parameters
    val containerColor by parameter(MaterialTheme.colorScheme.surface)
    val titleContentColor by parameter(MaterialTheme.colorScheme.onSurface)
    val iconContentColor by parameter(MaterialTheme.colorScheme.onSurface)

    // Create scroll behavior
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Determine navigation icon
    val navigationIcon = when (navigationIconType) {
        "Back" -> Icons.Filled.ArrowBack
        else -> Icons.Filled.Menu
    }

    Row(modifier = Modifier.width(400.dp)) {
        MediumTopAppBar(
            title = {
                Text(
                    text = titleText,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (showNavigationIcon) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = navigationIcon,
                            contentDescription = navigationIconType,
                            tint = iconContentColor
                        )
                    }
                }
            },
            actions = {
                when (actionIconsCount) {
                    "One" -> {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = iconContentColor
                            )
                        }
                    }
                    "Two" -> {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = iconContentColor
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = iconContentColor
                            )
                        }
                    }
                    "Three" -> {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = iconContentColor
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = iconContentColor
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = iconContentColor
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.mediumTopAppBarColors(
                containerColor = containerColor,
                titleContentColor = titleContentColor,
                navigationIconContentColor = iconContentColor,
                actionIconContentColor = iconContentColor
            ),
            scrollBehavior = scrollBehavior,
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `TopAppBar Story` by story {
    // Title parameters
    val titleText by parameter("App Bar Title")
    val titleMaxLines by parameter(1)

    // Navigation icon parameters
    val showNavigationIcon by parameter(true)
    val navigationIconType by parameter(listOf("Menu", "Back"), 0)

    // Action icons parameters
    val actionIconsCount by parameter(listOf("None", "One", "Two", "Three"), 2)

    // Color parameters
    val containerColor by parameter(MaterialTheme.colorScheme.surface)
    val titleContentColor by parameter(MaterialTheme.colorScheme.onSurface)
    val iconContentColor by parameter(MaterialTheme.colorScheme.onSurface)

    // Height parameters
    val expandedHeight by parameter(64f)
    
    // Scroll behavior parameters
    val useScrollBehavior by parameter(false)

    // Create scroll behavior
    val scrollBehavior = if (useScrollBehavior) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        null
    }

    // Determine navigation icon
    val navigationIcon = when (navigationIconType) {
        "Back" -> Icons.Filled.ArrowBack
        else -> Icons.Filled.Menu
    }

    Row(modifier = Modifier.width(400.dp)) {
        TopAppBar(
            title = {
                Text(
                    text = titleText,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (showNavigationIcon) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = navigationIcon,
                            contentDescription = navigationIconType,
                            tint = iconContentColor
                        )
                    }
                }
            },
            actions = {
                when (actionIconsCount) {
                    "One" -> {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = iconContentColor
                            )
                        }
                    }
                    "Two" -> {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = iconContentColor
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = iconContentColor
                            )
                        }
                    }
                    "Three" -> {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = iconContentColor
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = iconContentColor
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = iconContentColor
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = containerColor,
                titleContentColor = titleContentColor,
                navigationIconContentColor = iconContentColor,
                actionIconContentColor = iconContentColor
            ),
            expandedHeight = expandedHeight.dp,
            scrollBehavior = scrollBehavior,
            modifier = Modifier
                .fillMaxWidth()
                .run { 
                    if (useScrollBehavior && scrollBehavior != null) {
                        nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        this
                    }
                }
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
