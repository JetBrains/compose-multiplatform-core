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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `DockedSearchBar Story` by story {
    // Common parameters
    val placeholder by parameter("Search...")

    // State management
    var query by remember { mutableStateOf("") }

    // Shape and color parameters
    val cornerRadius by parameter(16f)
    val customContainerColor by parameter(false)
    val containerColor by parameter(MaterialTheme.colorScheme.primaryContainer)
    val dividerColor by parameter(MaterialTheme.colorScheme.outline)
    val tonalElevation by parameter(4f)
    val shadowElevation by parameter(8f)

    val shape = RoundedCornerShape(cornerRadius.dp)

    // State management
    var expanded by remember { mutableStateOf(false) }

    val filteredResults = remember(query) {
        emptyList<String>()
    }

    val effectiveContainerColor = if (customContainerColor) containerColor else MaterialTheme.colorScheme.surface

    val colors = SearchBarDefaults.colors(
        containerColor = effectiveContainerColor,
        dividerColor = dividerColor
    )

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The DockedSearchBar component
        DockedSearchBar(
            inputField = {
                // Custom input field
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.isNotEmpty() && !expanded) {
                            expanded = true
                        }
                    },
                    placeholder = { Text(placeholder) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Icon"
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    query = ""
                                    expanded = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            shape = shape,
            colors = colors,
            tonalElevation = tonalElevation.dp,
            shadowElevation = shadowElevation.dp,
            modifier = Modifier.fillMaxWidth(),
            content = {
                LazyColumn {
                    if (filteredResults.isEmpty() && query.isNotEmpty()) {
                        item {
                            ListItem(
                                headlineContent = { Text("No results found for \"$query\"") }
                            )
                        }
                    } else {
                        items(filteredResults) { result ->
                            ListItem(
                                headlineContent = { Text(result) },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status information
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Search for something",
                style = MaterialTheme.typography.bodySmall
            )
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
