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

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

/**
 * This story demonstrates the Label component, which appends a label to content.
 * 
 * Labels are similar to tooltips but can be configured to be persistent or to appear
 * only on interaction. They are useful for providing additional information or context
 * for UI elements like sliders, buttons, or icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
val `Label Story` by story {
    // Parameters for the label
    val labelText by parameter("This is a label")
    val isPersistent by parameter(false)
    val contentType by parameter(0) // 0 = Box, 1 = Icon, 2 = Card
    val contentColor by parameter(Color(0xFF6200EE))

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // Description text
            Text(
                text = if (isPersistent) {
                    "Persistent Label (always visible)"
                } else {
                    "Interactive Label (hover or click to show)"
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Create an interaction source to track interactions
            val interactionSource = remember { MutableInteractionSource() }

            // The Label component with the specified parameters
            Label(
                label = {
                    // The label content
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = TooltipDefaults.plainTooltipContainerShape
                    ) {
                        Text(
                            text = labelText,
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 8.dp
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                interactionSource = interactionSource,
                isPersistent = isPersistent
            ) {
                // The content that the label is attached to
                when (contentType) {
                    0 -> {
                        // Simple colored box
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(contentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Content",
                                color = Color.White
                            )
                        }
                    }
                    1 -> {
                        // Icon
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Star",
                            modifier = Modifier.size(80.dp),
                            tint = contentColor
                        )
                    }
                    else -> {
                        // Card
                        Card(
                            modifier = Modifier.size(width = 120.dp, height = 80.dp),
                            onClick = {}
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Card Content",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Instructions
            Text(
                text = if (isPersistent) {
                    "The label is always visible above the content"
                } else {
                    "Hover over or click the content to see the label"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
