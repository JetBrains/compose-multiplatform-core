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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `TabRow Story` by story {
    // State for tab selection
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Size parameters
    val containerWidth by parameter(300f)

    // Display parameters
    val showIcons by parameter(true)

    // Hardcoded tab data
    val tabs = listOf(
        "Home" to Icons.Filled.Home,
        "Favorites" to Icons.Filled.Favorite,
        "Settings" to Icons.Filled.Settings
    )

    // Fixed height container to prevent layout shifts
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .height(120.dp)
            .width(containerWidth.dp)
    ) {
        // Fixed height for the tab row
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .height(56.dp)     // Standard Material3 tab height
                .fillMaxWidth()     // Fill the width of the parent
        ) {
            // Multiple hardcoded tabs
            tabs.forEachIndexed { index, (title, icon) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier
                        .height(56.dp)  // Standard Material3 tab height
                        .weight(1f),    // Equal width for all tabs
                    text = { 
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    icon = if (showIcons) {
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}
