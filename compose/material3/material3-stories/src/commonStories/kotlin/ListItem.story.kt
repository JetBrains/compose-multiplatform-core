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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One-line list item */
@Composable
fun OneLineListItemExample() {
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text("One-line list item") },
            leadingContent = {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Star Icon"
                )
            }
        )
    }
}

/** Two-line list item with leading and trailing content */
@Composable
fun TwoLineListItemExample() {
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text("Two-line list item") },
            supportingContent = { Text("Secondary text") },
            leadingContent = {
                Icon(
                    Icons.Filled.Face,
                    contentDescription = "Face Icon"
                )
            },
            trailingContent = {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "Info Icon"
                )
            }
        )
    }
}

/** Three-line list item with overline and supporting text */
@Composable
fun ThreeLineListItemWithOverlineAndSupportingExample() {
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text("Three-line list item") },
            overlineContent = { Text("OVERLINE") },
            supportingContent = { Text("Secondary text") },
            leadingContent = {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favorite Icon"
                )
            },
            trailingContent = {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "Info Icon"
                )
            }
        )
    }
}

/** Three-line list item with extended supporting text */
@Composable
fun ThreeLineListItemWithExtendedSupportingExample() {
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text("Three-line list item") },
            supportingContent = { 
                Text("This is a longer supporting text that demonstrates how a three-line list item with extended supporting content looks like") 
            },
            leadingContent = {
                Icon(
                    Icons.Filled.Face,
                    contentDescription = "Face Icon"
                )
            }
        )
    }
}

/** ListItem with Checkbox */
@Composable
fun ListItemWithCheckboxExample() {
    var checked by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text("List item with checkbox") },
            supportingContent = { Text("Supporting text") },
            leadingContent = {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Star Icon"
                )
            },
            trailingContent = {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it }
                )
            }
        )
    }
}

/** ListItem with Switch */
@Composable
fun ListItemWithSwitchExample() {
    var checked by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text("List item with switch") },
            supportingContent = { Text("Supporting text") },
            leadingContent = {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Star Icon"
                )
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it }
                )
            }
        )
    }
}

/** ListItem with custom colors */
@Composable
fun ListItemWithCustomColorsExample() {
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text("Custom colored list item") },
            supportingContent = { Text("Supporting text") },
            leadingContent = {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Star Icon"
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color(0xFFE8F5E9),
                headlineColor = Color(0xFF2E7D32),
                leadingIconColor = Color(0xFF2E7D32),
                supportingColor = Color(0xFF66BB6A)
            )
        )
    }
} 