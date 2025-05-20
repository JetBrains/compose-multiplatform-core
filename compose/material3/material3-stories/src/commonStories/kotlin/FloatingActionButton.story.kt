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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `FloatingActionButton Story` by story {
    // Icon parameters
    val iconOptions = listOf("Add", "Edit", "Home")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Action")

    // Shape parameters
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(16f)

    // Size parameters
    val fabSize by parameter(56f)
    val iconSize by parameter(24f)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFF6200EE))
    val contentColor by parameter(Color.White)

    // Elevation parameters
    val useCustomElevation by parameter(false)
    val defaultElevation by parameter(3f)
    val pressedElevation by parameter(6f)
    val focusedElevation by parameter(6f)
    val hoveredElevation by parameter(8f)

    // Determine which icon to use based on the parameter
    val icon = when (iconOptions.indexOf(iconType)) {
        1 -> Icons.Filled.Edit
        2 -> Icons.Filled.Home
        else -> Icons.Filled.Add
    }

    // Create shape
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        FloatingActionButtonDefaults.shape
    }

    // Create elevation
    val elevation = if (useCustomElevation) {
        FloatingActionButtonDefaults.elevation(
            defaultElevation = defaultElevation.dp,
            pressedElevation = pressedElevation.dp,
            focusedElevation = focusedElevation.dp,
            hoveredElevation = hoveredElevation.dp
        )
    } else {
        FloatingActionButtonDefaults.elevation()
    }

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier.width(380.dp)
    ) {
        Box(
            modifier = Modifier.padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = { /* Handle FAB click */ },
                modifier = Modifier.size(fabSize.dp),
                shape = shape,
                containerColor = if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor,
                contentColor = if (useCustomColors) contentColor else contentColorFor(if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor),
                elevation = elevation,
                interactionSource = interactionSource
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(iconSize.dp)
                )
            }
        }
    }
}

val `LargeFloatingActionButton Story` by story {
    // Icon parameters
    val iconOptions = listOf("Add", "Edit", "Home")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Action")

    // Shape parameters
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(16f)

    // Size parameters
    val iconSize by parameter(36f)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFF6200EE))
    val contentColor by parameter(Color.White)

    // Elevation parameters
    val useCustomElevation by parameter(false)
    val defaultElevation by parameter(6f)
    val pressedElevation by parameter(12f)
    val focusedElevation by parameter(10f)
    val hoveredElevation by parameter(8f)

    // Determine which icon to use based on the parameter
    val icon = when (iconOptions.indexOf(iconType)) {
        1 -> Icons.Filled.Edit
        2 -> Icons.Filled.Home
        else -> Icons.Filled.Add
    }

    // Create shape
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        FloatingActionButtonDefaults.largeShape
    }

    // Create elevation
    val elevation = if (useCustomElevation) {
        FloatingActionButtonDefaults.elevation(
            defaultElevation = defaultElevation.dp,
            pressedElevation = pressedElevation.dp,
            focusedElevation = focusedElevation.dp,
            hoveredElevation = hoveredElevation.dp
        )
    } else {
        FloatingActionButtonDefaults.elevation()
    }

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier.width(380.dp)
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            LargeFloatingActionButton(
                onClick = { /* Handle FAB click */ },
                shape = shape,
                containerColor = if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor,
                contentColor = if (useCustomColors) contentColor else contentColorFor(if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor),
                elevation = elevation,
                interactionSource = interactionSource
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(iconSize.dp)
                )
            }
        }
    }
}

val `ExtendedFloatingActionButton Story` by story {
    // Basic parameters
    val buttonText by parameter("Extended")
    val showIcon by parameter(true)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFF6200EE))
    val contentColor by parameter(Color.White)

    Surface(
        modifier = Modifier.width(380.dp)
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showIcon) {
                ExtendedFloatingActionButton(
                    text = { Text(buttonText) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                    onClick = { /* Handle FAB click */ },
                    containerColor = if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor,
                    contentColor = if (useCustomColors) contentColor else contentColorFor(if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor)
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { /* Handle FAB click */ },
                    containerColor = if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor,
                    contentColor = if (useCustomColors) contentColor else contentColorFor(if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor)
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}

val `SmallFloatingActionButton Story` by story {
    // Icon parameters
    val iconOptions = listOf("Add", "Edit", "Home")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Action")

    // Shape parameters
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(16f)

    // Size parameters
    val iconSize by parameter(16f)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFF6200EE))
    val contentColor by parameter(Color.White)

    // Elevation parameters
    val useCustomElevation by parameter(false)
    val defaultElevation by parameter(3f)
    val pressedElevation by parameter(6f)
    val focusedElevation by parameter(6f)
    val hoveredElevation by parameter(8f)

    // Determine which icon to use based on the parameter
    val icon = when (iconOptions.indexOf(iconType)) {
        1 -> Icons.Filled.Edit
        2 -> Icons.Filled.Home
        else -> Icons.Filled.Add
    }

    // Create shape
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        FloatingActionButtonDefaults.smallShape
    }

    // Create elevation
    val elevation = if (useCustomElevation) {
        FloatingActionButtonDefaults.elevation(
            defaultElevation = defaultElevation.dp,
            pressedElevation = pressedElevation.dp,
            focusedElevation = focusedElevation.dp,
            hoveredElevation = hoveredElevation.dp
        )
    } else {
        FloatingActionButtonDefaults.elevation()
    }

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier.width(380.dp)
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            SmallFloatingActionButton(
                onClick = { /* Handle FAB click */ },
                shape = shape,
                containerColor = if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor,
                contentColor = if (useCustomColors) contentColor else contentColorFor(if (useCustomColors) containerColor else FloatingActionButtonDefaults.containerColor),
                elevation = elevation,
                interactionSource = interactionSource
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(iconSize.dp)
                )
            }
        }
    }
}
