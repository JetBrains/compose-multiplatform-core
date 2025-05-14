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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `ListItem Story` by story {
    // Text content
    val headlineText by parameter("One-line list item")
    
    // Leading content options
    val showLeadingContent by parameter(true)
    val leadingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val leadingIconType by parameter(leadingIconOptions, 0)
    
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text(headlineText) },
            leadingContent = if (showLeadingContent) {
                {
                    Icon(
                        when (leadingIconType) {
                            "Face" -> Icons.Filled.Face
                            "Favorite" -> Icons.Filled.Favorite
                            "Info" -> Icons.Filled.Info
                            else -> Icons.Filled.Star
                        },
                        contentDescription = "$leadingIconType Icon"
                    )
                }
            } else null
        )
    }
}

val `Two Line ListItem Story` by story {
    // Text content
    val headlineText by parameter("Two-line list item")
    val supportingText by parameter("Secondary text")
    
    // Leading content options
    val showLeadingContent by parameter(true)
    val leadingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val leadingIconType by parameter(leadingIconOptions, 1)
    
    // Trailing content options
    val showTrailingContent by parameter(true)
    val trailingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val trailingIconType by parameter(trailingIconOptions, 3)
    
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text(headlineText) },
            supportingContent = { Text(supportingText) },
            leadingContent = if (showLeadingContent) {
                {
                    Icon(
                        when (leadingIconType) {
                            "Face" -> Icons.Filled.Face
                            "Favorite" -> Icons.Filled.Favorite
                            "Info" -> Icons.Filled.Info
                            else -> Icons.Filled.Star
                        },
                        contentDescription = "$leadingIconType Icon"
                    )
                }
            } else null,
            trailingContent = if (showTrailingContent) {
                {
                    Icon(
                        when (trailingIconType) {
                            "Face" -> Icons.Filled.Face
                            "Favorite" -> Icons.Filled.Favorite
                            "Star" -> Icons.Filled.Star
                            else -> Icons.Filled.Info
                        },
                        contentDescription = "$trailingIconType Icon"
                    )
                }
            } else null
        )
    }
}

val `Three Line ListItem With Overline Story` by story {
    // Text content
    val headlineText by parameter("Three-line list item")
    val overlineText by parameter("OVERLINE")
    val supportingText by parameter("Secondary text")
    
    // Leading content options
    val showLeadingContent by parameter(true)
    val leadingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val leadingIconType by parameter(leadingIconOptions, 2)
    
    // Trailing content options
    val showTrailingContent by parameter(true)
    val trailingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val trailingIconType by parameter(trailingIconOptions, 3)
    
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text(headlineText) },
            overlineContent = { Text(overlineText) },
            supportingContent = { Text(supportingText) },
            leadingContent = if (showLeadingContent) {
                {
                    Icon(
                        when (leadingIconType) {
                            "Face" -> Icons.Filled.Face
                            "Star" -> Icons.Filled.Star
                            "Info" -> Icons.Filled.Info
                            else -> Icons.Filled.Favorite
                        },
                        contentDescription = "$leadingIconType Icon"
                    )
                }
            } else null,
            trailingContent = if (showTrailingContent) {
                {
                    Icon(
                        when (trailingIconType) {
                            "Face" -> Icons.Filled.Face
                            "Favorite" -> Icons.Filled.Favorite
                            "Star" -> Icons.Filled.Star
                            else -> Icons.Filled.Info
                        },
                        contentDescription = "$trailingIconType Icon"
                    )
                }
            } else null
        )
    }
}

val `Three Line ListItem With Extended Supporting Story` by story {
    // Text content
    val headlineText by parameter("Three-line list item")
    val supportingText by parameter("This is a longer supporting text that demonstrates how a three-line list item with extended supporting content looks like")
    
    // Leading content options
    val showLeadingContent by parameter(true)
    val leadingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val leadingIconType by parameter(leadingIconOptions, 1)
    
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text(headlineText) },
            supportingContent = { Text(supportingText) },
            leadingContent = if (showLeadingContent) {
                {
                    Icon(
                        when (leadingIconType) {
                            "Star" -> Icons.Filled.Star
                            "Favorite" -> Icons.Filled.Favorite
                            "Info" -> Icons.Filled.Info
                            else -> Icons.Filled.Face
                        },
                        contentDescription = "$leadingIconType Icon"
                    )
                }
            } else null
        )
    }
}

val `ListItem With Checkbox Story` by story {
    // Text content
    val headlineText by parameter("List item with checkbox")
    val supportingText by parameter("Supporting text")
    val initialChecked by parameter(false)
    
    // Leading content options
    val showLeadingContent by parameter(true)
    val leadingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val leadingIconType by parameter(leadingIconOptions, 0)
    
    var checked by remember { mutableStateOf(initialChecked) }
    
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text(headlineText) },
            supportingContent = { Text(supportingText) },
            leadingContent = if (showLeadingContent) {
                {
                    Icon(
                        when (leadingIconType) {
                            "Face" -> Icons.Filled.Face
                            "Favorite" -> Icons.Filled.Favorite
                            "Info" -> Icons.Filled.Info
                            else -> Icons.Filled.Star
                        },
                        contentDescription = "$leadingIconType Icon"
                    )
                }
            } else null,
            trailingContent = {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it }
                )
            }
        )
    }
}

val `ListItem With Switch Story` by story {
    // Text content
    val headlineText by parameter("List item with switch")
    val supportingText by parameter("Supporting text")
    val initialChecked by parameter(false)
    
    // Leading content options
    val showLeadingContent by parameter(true)
    val leadingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val leadingIconType by parameter(leadingIconOptions, 0)
    
    var checked by remember { mutableStateOf(initialChecked) }
    
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text(headlineText) },
            supportingContent = { Text(supportingText) },
            leadingContent = if (showLeadingContent) {
                {
                    Icon(
                        when (leadingIconType) {
                            "Face" -> Icons.Filled.Face
                            "Favorite" -> Icons.Filled.Favorite
                            "Info" -> Icons.Filled.Info
                            else -> Icons.Filled.Star
                        },
                        contentDescription = "$leadingIconType Icon"
                    )
                }
            } else null,
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it }
                )
            }
        )
    }
}

val `ListItem With Custom Colors Story` by story {
    // Text content
    val headlineText by parameter("Custom colored list item")
    val supportingText by parameter("Supporting text")
    
    // Leading content options
    val showLeadingContent by parameter(true)
    val leadingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val leadingIconType by parameter(leadingIconOptions, 0)
    
    // Color options
    val containerColor by parameter(Color(0xFFE8F5E9))
    val headlineColor by parameter(Color(0xFF2E7D32))
    val supportingColor by parameter(Color(0xFF66BB6A))
    val leadingIconColor by parameter(Color(0xFF2E7D32))
    
    Column(modifier = Modifier.padding(8.dp)) {
        ListItem(
            headlineContent = { Text(headlineText) },
            supportingContent = { Text(supportingText) },
            leadingContent = if (showLeadingContent) {
                {
                    Icon(
                        when (leadingIconType) {
                            "Face" -> Icons.Filled.Face
                            "Favorite" -> Icons.Filled.Favorite
                            "Info" -> Icons.Filled.Info
                            else -> Icons.Filled.Star
                        },
                        contentDescription = "$leadingIconType Icon"
                    )
                }
            } else null,
            colors = ListItemDefaults.colors(
                containerColor = containerColor,
                headlineColor = headlineColor,
                leadingIconColor = leadingIconColor,
                supportingColor = supportingColor
            )
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