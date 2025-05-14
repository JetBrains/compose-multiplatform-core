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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `Button Story` by story {
    // Basic parameters
    val buttonText by parameter("Button")
    val enabled by parameter(true)

    // Icon
    val showIcon by parameter(false)
    val iconPosition by parameter(listOf("start", "end"), 0)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(MaterialTheme.colorScheme.primary)
    val contentColor by parameter(MaterialTheme.colorScheme.onPrimary)
    val disabledContainerColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    val disabledContentColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))

    // Shape
    val useCustomShape by parameter(false)
    val cornerSize by parameter(4f)

    // Elevation
    val useCustomElevation by parameter(false)
    val defaultElevation by parameter(1f)
    val pressedElevation by parameter(0f)
    val focusedElevation by parameter(0f)
    val hoveredElevation by parameter(1f)
    val disabledElevation by parameter(0f)

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(MaterialTheme.colorScheme.primary)

    // Padding
    val useCustomPadding by parameter(false)
    val horizontalPadding by parameter(24f)
    val verticalPadding by parameter(8f)

    // Set up all parameters
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerSize.dp)
    } else {
        ButtonDefaults.shape
    }

    val colors = if (useCustomColors) {
        ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        ButtonDefaults.buttonColors()
    }

    val elevation = if (useCustomElevation) {
        ButtonDefaults.buttonElevation(
            defaultElevation = defaultElevation.dp,
            pressedElevation = pressedElevation.dp,
            focusedElevation = focusedElevation.dp,
            hoveredElevation = hoveredElevation.dp,
            disabledElevation = disabledElevation.dp
        )
    } else {
        ButtonDefaults.buttonElevation()
    }

    val border = if (useCustomBorder) {
        BorderStroke(width = borderWidth.dp, color = borderColor)
    } else {
        null
    }

    val contentPadding = if (useCustomPadding) {
        PaddingValues(
            start = horizontalPadding.dp,
            top = verticalPadding.dp,
            end = horizontalPadding.dp,
            bottom = verticalPadding.dp
        )
    } else {
        ButtonDefaults.ContentPadding
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Button(
            onClick = {},
            modifier = Modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource
        ) {
            if (showIcon && iconPosition == "start") {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(18.dp).padding(end = 8.dp)
                )
            }
            Text(buttonText)
            if (showIcon && iconPosition == "end") {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(18.dp).padding(start = 8.dp)
                )
            }
        }
    }
}

val `ElevatedButton Story` by story {
    // Basic parameters
    val buttonText by parameter("Elevated Button")
    val enabled by parameter(true)

    // Icon
    val showIcon by parameter(false)
    val iconPosition by parameter(listOf("start", "end"), 0)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFF6650a4))
    val contentColor by parameter(Color.White)
    val disabledContainerColor by parameter(Color.Gray.copy(alpha = 0.12f))
    val disabledContentColor by parameter(Color.Gray.copy(alpha = 0.38f))

    // Shape
    val useCustomShape by parameter(false)
    val cornerSize by parameter(4f)

    // Elevation
    val useCustomElevation by parameter(false)
    val defaultElevation by parameter(1f)
    val pressedElevation by parameter(6f)
    val focusedElevation by parameter(2f)
    val hoveredElevation by parameter(3f)
    val disabledElevation by parameter(0f)

    // Set up all parameters
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerSize.dp)
    } else {
        ButtonDefaults.elevatedShape
    }

    val colors = if (useCustomColors) {
        ButtonDefaults.elevatedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        ButtonDefaults.elevatedButtonColors()
    }

    val elevation = if (useCustomElevation) {
        ButtonDefaults.elevatedButtonElevation(
            defaultElevation = defaultElevation.dp,
            pressedElevation = pressedElevation.dp,
            focusedElevation = focusedElevation.dp,
            hoveredElevation = hoveredElevation.dp,
            disabledElevation = disabledElevation.dp
        )
    } else {
        ButtonDefaults.elevatedButtonElevation()
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        ElevatedButton(
            onClick = {},
            modifier = Modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            interactionSource = interactionSource
        ) {
            if (showIcon && iconPosition == "start") {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(18.dp).padding(end = 8.dp)
                )
            }
            Text(buttonText)
            if (showIcon && iconPosition == "end") {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(18.dp).padding(start = 8.dp)
                )
            }
        }
    }
}

val `OutlinedButton Story` by story {
    // Basic parameters
    val buttonText by parameter("Outlined Button")
    val enabled by parameter(true)

    // Icon
    val showIcon by parameter(false)
    val iconPosition by parameter(listOf("start", "end"), 0)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color.Transparent)
    val contentColor by parameter(MaterialTheme.colorScheme.primary)
    val disabledContainerColor by parameter(Color.Transparent)
    val disabledContentColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))

    // Shape
    val useCustomShape by parameter(false)
    val cornerSize by parameter(4f)

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(MaterialTheme.colorScheme.primary)
    val disabledBorderColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

    // Padding
    val useCustomPadding by parameter(false)
    val horizontalPadding by parameter(24f)
    val verticalPadding by parameter(8f)

    // Set up all parameters
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerSize.dp)
    } else {
        ButtonDefaults.outlinedShape
    }

    val colors = if (useCustomColors) {
        ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        ButtonDefaults.outlinedButtonColors()
    }

    val border = if (useCustomBorder) {
        if (enabled) {
            BorderStroke(width = borderWidth.dp, color = borderColor)
        } else {
            BorderStroke(width = borderWidth.dp, color = disabledBorderColor)
        }
    } else {
        ButtonDefaults.outlinedButtonBorder(enabled)
    }

    val contentPadding = if (useCustomPadding) {
        PaddingValues(
            start = horizontalPadding.dp,
            top = verticalPadding.dp,
            end = horizontalPadding.dp,
            bottom = verticalPadding.dp
        )
    } else {
        ButtonDefaults.ContentPadding
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        OutlinedButton(
            onClick = {},
            modifier = Modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource
        ) {
            if (showIcon && iconPosition == "start") {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(18.dp).padding(end = 8.dp)
                )
            }
            Text(buttonText)
            if (showIcon && iconPosition == "end") {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(18.dp).padding(start = 8.dp)
                )
            }
        }
    }
}

val `FilledTonalButton Story` by story {
    // Basic parameters
    val buttonText by parameter("Tonal Button")
    val enabled by parameter(true)

    // Icon
    val showIcon by parameter(false)
    val iconPosition by parameter(listOf("start", "end"), 0)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFFE8DEF8))
    val contentColor by parameter(Color(0xFF6750A4))
    val disabledContainerColor by parameter(Color.Gray.copy(alpha = 0.12f))
    val disabledContentColor by parameter(Color.Gray.copy(alpha = 0.38f))

    // Shape
    val useCustomShape by parameter(false)
    val cornerSize by parameter(4f)

    // Elevation
    val useCustomElevation by parameter(false)
    val defaultElevation by parameter(1f)
    val pressedElevation by parameter(6f)
    val focusedElevation by parameter(2f)
    val hoveredElevation by parameter(3f)
    val disabledElevation by parameter(0f)

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(Color(0xFF6750A4))

    // Padding
    val useCustomPadding by parameter(false)
    val horizontalPadding by parameter(24f)
    val verticalPadding by parameter(8f)

    // Set up all parameters
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerSize.dp)
    } else {
        ButtonDefaults.filledTonalShape
    }

    val colors = if (useCustomColors) {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }

    val elevation = if (useCustomElevation) {
        ButtonDefaults.filledTonalButtonElevation(
            defaultElevation = defaultElevation.dp,
            pressedElevation = pressedElevation.dp,
            focusedElevation = focusedElevation.dp,
            hoveredElevation = hoveredElevation.dp,
            disabledElevation = disabledElevation.dp
        )
    } else {
        ButtonDefaults.filledTonalButtonElevation()
    }

    val border = if (useCustomBorder) {
        BorderStroke(width = borderWidth.dp, color = borderColor)
    } else {
        null
    }

    val contentPadding = if (useCustomPadding) {
        PaddingValues(
            start = horizontalPadding.dp,
            top = verticalPadding.dp,
            end = horizontalPadding.dp,
            bottom = verticalPadding.dp
        )
    } else {
        ButtonDefaults.ContentPadding
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        FilledTonalButton(
            onClick = {},
            modifier = Modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource
        ) {
            if (showIcon && iconPosition == "start") {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(18.dp).padding(end = 8.dp)
                )
            }
            Text(buttonText)
            if (showIcon && iconPosition == "end") {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(18.dp).padding(start = 8.dp)
                )
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
