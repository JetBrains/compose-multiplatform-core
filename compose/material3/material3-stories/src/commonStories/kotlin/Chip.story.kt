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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.graphics.Shape

val `AssistChip Story` by story {
    // Basic parameters
    val labelText by parameter("Assist Chip")
    val enabled by parameter(true)

    // Icons
    val showLeadingIcon by parameter(true)
    val showTrailingIcon by parameter(false)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFFF5F0FF))
    val labelColor by parameter(Color(0xFF6650a4))
    val iconColor by parameter(Color(0xFF6650a4))

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(Color(0xFF6650a4))

    // Elevation
    val useCustomElevation by parameter(false)
    val elevationValue by parameter(0f)
    val pressedElevation by parameter(2f)
    val focusedElevation by parameter(0.5f)
    val hoveredElevation by parameter(1f)
    val draggedElevation by parameter(4f)

    Box(
        contentAlignment = Alignment.Center
    ) {
        AssistChip(
            onClick = {},
            label = { Text(labelText) },
            enabled = enabled,
            leadingIcon = if (showLeadingIcon) {
                {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "Info"
                    )
                }
            } else null,
            trailingIcon = if (showTrailingIcon) {
                {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings"
                    )
                }
            } else null,
            colors = if (useCustomColors) {
                AssistChipDefaults.assistChipColors(
                    containerColor = containerColor,
                    labelColor = labelColor,
                    leadingIconContentColor = iconColor,
                    trailingIconContentColor = iconColor
                )
            } else {
                AssistChipDefaults.assistChipColors()
            },
            elevation = if (useCustomElevation) {
                AssistChipDefaults.assistChipElevation(
                    elevation = elevationValue.dp,
                    pressedElevation = pressedElevation.dp,
                    focusedElevation = focusedElevation.dp,
                    hoveredElevation = hoveredElevation.dp,
                    draggedElevation = draggedElevation.dp
                )
            } else {
                AssistChipDefaults.assistChipElevation()
            },
            border = if (useCustomBorder) {
                BorderStroke(width = borderWidth.dp, color = borderColor)
            } else {
                AssistChipDefaults.assistChipBorder(enabled)
            },
            interactionSource = remember { MutableInteractionSource() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `InputChip Story` by story {
    // Basic parameters
    val labelText by parameter("Input Chip")
    var selected by parameter(false)
    val enabled by parameter(true)

    // Icons and avatar
    val showLeadingIcon by parameter(false)
    val showAvatar by parameter(false)
    val showTrailingIcon by parameter(false)

    // Colors
    val useCustomColors by parameter(false)
    val selectedContainerColor by parameter(Color(0xFFE8DEF8))
    val selectedLabelColor by parameter(Color(0xFF6750A4))
    val selectedLeadingIconColor by parameter(Color(0xFF6750A4))
    val selectedTrailingIconColor by parameter(Color(0xFF6750A4))

    // Shape
    val useCustomShape by parameter(false)
    val cornerSize by parameter(8f)

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(Color(0xFF6750A4))

    // Elevation
    val useCustomElevation by parameter(false)
    val elevationValue by parameter(1f)

    // Create a Box with padding that will contain the InputChip
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Create the InputChip with all parameters
        InputChip(
            selected = selected,
            onClick = { selected = !selected },
            label = { Text(labelText) },
            modifier = Modifier,
            enabled = enabled,
            leadingIcon = if (showLeadingIcon) {
                {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else null,
            avatar = if (showAvatar) {
                {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Person",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            } else null,
            trailingIcon = if (showTrailingIcon) {
                {
                    // Используем FilledIconToggleButton для управления иконкой с эффектом ripple
                    FilledIconToggleButton(
                        checked = false,
                        onCheckedChange = { selected = false },
                        modifier = Modifier.size(24.dp),
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = Color.Transparent, // Прозрачный фон
                            contentColor = if (useCustomColors && selected)
                                selectedTrailingIconColor
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            shape = if (useCustomShape) {
                RoundedCornerShape(cornerSize.dp)
            } else {
                InputChipDefaults.shape
            },
            colors = if (useCustomColors) {
                InputChipDefaults.inputChipColors(
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = Color.Transparent,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    selectedContainerColor = selectedContainerColor,
                    selectedLabelColor = selectedLabelColor,
                    selectedLeadingIconColor = selectedLeadingIconColor,
                    selectedTrailingIconColor = selectedTrailingIconColor
                )
            } else {
                InputChipDefaults.inputChipColors()
            },
            elevation = if (useCustomElevation) {
                InputChipDefaults.inputChipElevation(
                    elevation = elevationValue.dp
                )
            } else {
                InputChipDefaults.inputChipElevation()
            },
            border = if (useCustomBorder) {
                BorderStroke(width = borderWidth.dp, color = borderColor)
            } else {
                InputChipDefaults.inputChipBorder(enabled, selected)
            }
        )
    }
}

val `SuggestionChip Story` by story {
    // Basic parameters
    val labelText by parameter("Suggestion Chip")
    val enabled by parameter(true)

    // Icons
    val showIcon by parameter(true)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFFF5F0FF))
    val labelColor by parameter(Color(0xFF6650a4))
    val iconColor by parameter(Color(0xFF6650a4))

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(Color(0xFF6650a4))

    // Shape
    val useCustomShape by parameter(false)
    val cornerSize by parameter(8f)

    // Elevation
    val useCustomElevation by parameter(false)
    val elevationValue by parameter(0f)
    val pressedElevation by parameter(2f)
    val focusedElevation by parameter(0.5f)
    val hoveredElevation by parameter(1f)
    val draggedElevation by parameter(4f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        SuggestionChip(
            onClick = {},
            label = { Text(labelText) },
            enabled = enabled,
            icon = if (showIcon) {
                {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "Info"
                    )
                }
            } else null,
            shape = if (useCustomShape) {
                RoundedCornerShape(cornerSize.dp)
            } else {
                SuggestionChipDefaults.shape
            },
            colors = if (useCustomColors) {
                SuggestionChipDefaults.suggestionChipColors(
                    containerColor = containerColor,
                    labelColor = labelColor,
                    iconContentColor = iconColor
                )
            } else {
                SuggestionChipDefaults.suggestionChipColors()
            },
            elevation = if (useCustomElevation) {
                SuggestionChipDefaults.suggestionChipElevation(
                    elevation = elevationValue.dp,
                    pressedElevation = pressedElevation.dp,
                    focusedElevation = focusedElevation.dp,
                    hoveredElevation = hoveredElevation.dp,
                    draggedElevation = draggedElevation.dp
                )
            } else {
                SuggestionChipDefaults.suggestionChipElevation()
            },
            border = if (useCustomBorder) {
                BorderStroke(width = borderWidth.dp, color = borderColor)
            } else {
                SuggestionChipDefaults.suggestionChipBorder(enabled)
            },
            interactionSource = remember { MutableInteractionSource() }
        )
    }
}

val `ElevatedAssistChip Story` by story {
    // Basic parameters
    val labelText by parameter("Elevated Assist Chip")
    val enabled by parameter(true)

    // Icons
    val showLeadingIcon by parameter(true)
    val showTrailingIcon by parameter(false)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFFF5F0FF))
    val labelColor by parameter(Color(0xFF6650a4))
    val iconColor by parameter(Color(0xFF6650a4))

    // Shape
    val useCustomShape by parameter(false)
    val cornerSize by parameter(8f)

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(Color(0xFF6650a4))

    // Elevation
    val useCustomElevation by parameter(false)
    val elevationValue by parameter(1f)
    val pressedElevation by parameter(3f)
    val focusedElevation by parameter(1f)
    val hoveredElevation by parameter(2f)
    val draggedElevation by parameter(6f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        ElevatedAssistChip(
            onClick = {},
            label = { Text(labelText) },
            enabled = enabled,
            leadingIcon = if (showLeadingIcon) {
                {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "Info"
                    )
                }
            } else null,
            trailingIcon = if (showTrailingIcon) {
                {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings"
                    )
                }
            } else null,
            shape = if (useCustomShape) {
                RoundedCornerShape(cornerSize.dp)
            } else {
                AssistChipDefaults.shape
            },
            colors = if (useCustomColors) {
                AssistChipDefaults.elevatedAssistChipColors(
                    containerColor = containerColor,
                    labelColor = labelColor,
                    leadingIconContentColor = iconColor,
                    trailingIconContentColor = iconColor
                )
            } else {
                AssistChipDefaults.elevatedAssistChipColors()
            },
            elevation = if (useCustomElevation) {
                AssistChipDefaults.elevatedAssistChipElevation(
                    elevation = elevationValue.dp,
                    pressedElevation = pressedElevation.dp,
                    focusedElevation = focusedElevation.dp,
                    hoveredElevation = hoveredElevation.dp,
                    draggedElevation = draggedElevation.dp
                )
            } else {
                AssistChipDefaults.elevatedAssistChipElevation()
            },
            border = if (useCustomBorder) {
                BorderStroke(width = borderWidth.dp, color = borderColor)
            } else {
                null
            },
            interactionSource = remember { MutableInteractionSource() }
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
