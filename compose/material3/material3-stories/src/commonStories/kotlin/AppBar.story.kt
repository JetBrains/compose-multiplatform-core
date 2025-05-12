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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
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
    val navigationIconContentColor by parameter(MaterialTheme.colorScheme.onSurface)
    val actionIconContentColor by parameter(MaterialTheme.colorScheme.onSurface)

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
                            tint = navigationIconContentColor
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = actionIconContentColor
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = actionIconContentColor
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = containerColor,
                titleContentColor = contentColor,
                navigationIconContentColor = navigationIconContentColor,
                actionIconContentColor = actionIconContentColor
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
