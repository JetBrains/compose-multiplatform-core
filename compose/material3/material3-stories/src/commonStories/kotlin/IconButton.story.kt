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

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment

val `IconButton Story` by story {
    // Common parameters
    val enabled by parameter(true)

    // Icon parameters
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Icon Button")

    // Size parameters
    val iconSizeFloat by parameter(24f)

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color(0xFFE8DEF8))
    val customContentColor by parameter(Color(0xFF6750A4))

    // State for tracking clicks
    var clickCount by remember { mutableStateOf(0) }

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    val icon = when (iconTypeInt) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }

    IconButton(
        onClick = { clickCount++ },
        enabled = enabled,
        colors = if (useCustomColors) {
            IconButtonDefaults.iconButtonColors(
                containerColor = customContainerColor,
                contentColor = customContentColor
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        },
        modifier = Modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(iconSizeFloat.dp)
        )
    }
}

val `IconToggleButton Story` by story {
    // Common parameters
    var checked by parameter(false)
    val enabled by parameter(true)

    // Icon parameters
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Toggle Icon Button")

    // Size parameters
    val iconSizeFloat by parameter(24f)

    // Color parameters
    val useCustomColors by parameter(false)
    val contentColor by parameter(Color(0xFF6750A4))
    val checkedContentColor by parameter(Color(0xFFD0BCFF))
    val disabledContentColor by parameter(Color.Gray.copy(alpha = 0.38f))

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    val icon = when (iconTypeInt) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }

    // Create colors
    val colors = if (useCustomColors) {
        IconButtonDefaults.iconToggleButtonColors(
            contentColor = contentColor,
            checkedContentColor = checkedContentColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        IconButtonDefaults.iconToggleButtonColors()
    }

    val interactionSource = remember { MutableInteractionSource() }

    IconToggleButton(
        checked = checked,
        onCheckedChange = { checked = it },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        modifier = Modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(iconSizeFloat.dp)
        )
    }
}

val `FilledIconToggleButton Story` by story {
    // Common parameters
    var checked by parameter(false)
    val enabled by parameter(true)

    // Icon parameters
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Toggle Icon Button")

    // Size parameters
    val iconSizeFloat by parameter(24f)

    // Shape parameters
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(8f)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFFE8DEF8))
    val contentColor by parameter(Color(0xFF6750A4))
    val checkedContainerColor by parameter(Color(0xFF6750A4))
    val checkedContentColor by parameter(Color.White)
    
    // Only show parameters for disabled states if the button can be disabled
    val disabledContainerColor = if (enabled) Color.Gray.copy(alpha = 0.12f) else Color.Gray.copy(alpha = 0.12f)
    val disabledContentColor = if (enabled) Color.Gray.copy(alpha = 0.38f) else Color.Gray.copy(alpha = 0.38f)

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    val icon = when (iconTypeInt) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }

    // Create shape
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        IconButtonDefaults.filledShape
    }

    // Create colors
    val colors = if (useCustomColors) {
        IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            checkedContainerColor = checkedContainerColor, 
            checkedContentColor = checkedContentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        IconButtonDefaults.filledIconToggleButtonColors()
    }

    val interactionSource = remember { MutableInteractionSource() }

    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = { checked = it },
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource,
        modifier = Modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(iconSizeFloat.dp)
        )
    }
}

val `FilledIconButton Story` by story {
    // Common parameters
    val enabled by parameter(true)

    // Icon parameters
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Filled Icon Button")

    // Size parameters
    val iconSizeFloat by parameter(24f)

    // Shape parameters
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(8f)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFF6750A4))
    val contentColor by parameter(Color.White)
    val disabledContainerColor by parameter(Color.Gray.copy(alpha = 0.12f))
    val disabledContentColor by parameter(Color.Gray.copy(alpha = 0.38f))

    // State for tracking clicks
    var clickCount by remember { mutableStateOf(0) }

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    val icon = when (iconTypeInt) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }

    // Create shape
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        IconButtonDefaults.filledShape
    }

    // Create colors
    val colors = if (useCustomColors) {
        IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        IconButtonDefaults.filledIconButtonColors()
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                FilledIconButton(
                    onClick = { clickCount++ },
                    enabled = enabled,
                    shape = shape,
                    colors = colors,
                    interactionSource = interactionSource,
                    modifier = Modifier
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        modifier = Modifier.size(iconSizeFloat.dp)
                    )
                }
                
                // Always show click counter
                Text(
                    text = "Clicked: $clickCount",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

val `FilledTonalIconButton Story` by story {
    // Common parameters
    val enabled by parameter(true)

    // Icon parameters
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Tonal Icon Button")

    // Size parameters
    val iconSizeFloat by parameter(24f)

    // Shape parameters
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(8f)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFFE8DEF8))
    val contentColor by parameter(Color(0xFF6750A4))
    val disabledContainerColor by parameter(Color.Gray.copy(alpha = 0.12f))
    val disabledContentColor by parameter(Color.Gray.copy(alpha = 0.38f))

    // State for tracking clicks
    var clickCount by remember { mutableStateOf(0) }

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    val icon = when (iconTypeInt) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }

    // Create shape
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        IconButtonDefaults.filledShape
    }

    // Create colors
    val colors = if (useCustomColors) {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        IconButtonDefaults.filledTonalIconButtonColors()
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { clickCount++ },
                    enabled = enabled,
                    shape = shape,
                    colors = colors,
                    interactionSource = interactionSource,
                    modifier = Modifier
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        modifier = Modifier.size(iconSizeFloat.dp)
                    )
                }
                
                // Always show click counter
                Text(
                    text = "Clicked: $clickCount",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                // Display current icon size for clarity
                Text(
                    text = "Icon size: ${iconSizeFloat.toInt()}dp",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

val `FilledTonalIconToggleButton Story` by story {
    // Common parameters
    var checked by parameter(false)
    val enabled by parameter(true)

    // Icon parameters
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Tonal Toggle Icon Button")

    // Size parameters
    val iconSizeFloat by parameter(24f)

    // Shape parameters
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(8f)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFFE6E0EC))
    val contentColor by parameter(Color(0xFF6750A4))
    val checkedContainerColor by parameter(Color(0xFFD0BCFF))
    val checkedContentColor by parameter(Color(0xFF381E72))
    val disabledContainerColor by parameter(Color.Gray.copy(alpha = 0.12f))
    val disabledContentColor by parameter(Color.Gray.copy(alpha = 0.38f))

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    // Filled icons for checked state, Outlined icons for unchecked
    val checkedIcon = when (iconTypeInt) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }
    val uncheckedIcon = when (iconTypeInt) {
        1 -> Icons.Outlined.Settings
        2 -> Icons.Outlined.Star
        else -> Icons.Outlined.Favorite
    }

    // Create shape
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        IconButtonDefaults.filledShape
    }

    // Create colors
    val colors = if (useCustomColors) {
        IconButtonDefaults.filledTonalIconToggleButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            checkedContainerColor = checkedContainerColor, 
            checkedContentColor = checkedContentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        IconButtonDefaults.filledTonalIconToggleButtonColors()
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            FilledTonalIconToggleButton(
                checked = checked,
                onCheckedChange = { checked = it },
                enabled = enabled,
                shape = shape,
                colors = colors,
                interactionSource = interactionSource,
                modifier = Modifier
            ) {
                Icon(
                    imageVector = if (checked) checkedIcon else uncheckedIcon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(iconSizeFloat.dp)
                )
            }
            
            // Simple status indication
            Text(
                text = if (checked) "Checked" else "Unchecked",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

val `OutlinedIconButton Story` by story {
    // Basic parameters
    val enabled by parameter(true)

    // Icon
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Outlined Icon Button")
    val iconSize by parameter(24f)

    // Shape
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(8f)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color.Transparent)
    val contentColor by parameter(MaterialTheme.colorScheme.primary)
    val disabledContainerColor by parameter(Color.Transparent)
    val disabledContentColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(MaterialTheme.colorScheme.primary)
    val disabledBorderColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

    // Button size
    val buttonSize by parameter(40f)

    // Determine icon
    val icon = when (iconOptions.indexOf(iconType)) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }

    // Create shape
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        IconButtonDefaults.outlinedShape
    }

    // Create colors
    val colors = if (useCustomColors) {
        IconButtonDefaults.outlinedIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        IconButtonDefaults.outlinedIconButtonColors()
    }

    // Create border
    val border = if (useCustomBorder) {
        if (enabled) {
            BorderStroke(width = borderWidth.dp, color = borderColor)
        } else {
            BorderStroke(width = borderWidth.dp, color = disabledBorderColor)
        }
    } else {
        IconButtonDefaults.outlinedIconButtonBorder(enabled)
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            OutlinedIconButton(
                onClick = { /* handle click */ },
                enabled = enabled,
                shape = shape,
                colors = colors,
                border = border,
                interactionSource = interactionSource,
                modifier = Modifier.size(buttonSize.dp)
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

val `OutlinedIconToggleButton Story` by story {
    // Basic parameters
    var checked by parameter(false)
    val enabled by parameter(true)

    // Icon
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Outlined Icon Toggle Button")
    val iconSize by parameter(24f)
    
    // Show different icons for different states
    val useDifferentIcons by parameter(false)

    // Shape
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(8f)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color.Transparent)
    val contentColor by parameter(MaterialTheme.colorScheme.primary)
    val checkedContainerColor by parameter(MaterialTheme.colorScheme.primaryContainer)
    val checkedContentColor by parameter(MaterialTheme.colorScheme.primary)
    val disabledContainerColor by parameter(Color.Transparent)
    val disabledContentColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    val disabledCheckedContainerColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    val disabledCheckedContentColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(MaterialTheme.colorScheme.primary)
    val checkedBorderColor by parameter(MaterialTheme.colorScheme.primary)
    val disabledBorderColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    val disabledCheckedBorderColor by parameter(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

    // Button size
    val buttonSize by parameter(40f)

    // Determine icons
    val checkedIcon = when (iconOptions.indexOf(iconType)) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }
    
    val uncheckedIcon = if (useDifferentIcons) {
        when (iconOptions.indexOf(iconType)) {
            1 -> Icons.Outlined.Settings
            2 -> Icons.Outlined.Star
            else -> Icons.Outlined.Favorite
        }
    } else {
        checkedIcon
    }

    // Create shape
    val shape: Shape = if (useCustomShape) {
        RoundedCornerShape(cornerRadius.dp)
    } else {
        IconButtonDefaults.outlinedShape
    }

    // Create colors
    val colors = if (useCustomColors) {
        IconButtonDefaults.outlinedIconToggleButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            checkedContainerColor = checkedContainerColor,
            checkedContentColor = checkedContentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    } else {
        IconButtonDefaults.outlinedIconToggleButtonColors()
    }

    // Create border
    val border = if (useCustomBorder) {
        if (enabled) {
            if (checked) {
                BorderStroke(width = borderWidth.dp, color = checkedBorderColor)
            } else {
                BorderStroke(width = borderWidth.dp, color = borderColor)
            }
        } else {
            if (checked) {
                BorderStroke(width = borderWidth.dp, color = disabledCheckedBorderColor)
            } else {
                BorderStroke(width = borderWidth.dp, color = disabledBorderColor)
            }
        }
    } else {
        IconButtonDefaults.outlinedIconToggleButtonBorder(enabled, checked)
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedIconToggleButton(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    enabled = enabled,
                    shape = shape,
                    colors = colors,
                    border = border,
                    interactionSource = interactionSource,
                    modifier = Modifier.size(buttonSize.dp)
                ) {
                    Icon(
                        imageVector = if (checked) checkedIcon else uncheckedIcon,
                        contentDescription = iconContentDescription,
                        modifier = Modifier.size(iconSize.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Display button state
            Text(
                text = if (checked) "Selected" else "Not selected",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
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
