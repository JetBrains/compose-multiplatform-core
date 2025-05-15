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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `SegmentedButton Story` by story {
    // Common parameters
    val enabled by parameter(true)
    val buttonText by parameter("Option")

    // Icon parameters
    val showIcon by parameter(true)
    val iconOptions = listOf("Favorite", "Settings", "Star")
    val iconType by parameter(iconOptions, 0)
    val iconContentDescription by parameter("Icon")

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color(0xFFE8DEF8))
    val customContentColor by parameter(Color(0xFF6750A4))
    val customBorderColor by parameter(Color(0xFF6750A4))

    // Border parameters
    val borderWidthFloat by parameter(1f)

    // State for tracking selection
    var checked by remember { mutableStateOf(false) }

    // Determine which icon to use based on the parameter
    val iconTypeInt = iconOptions.indexOf(iconType)
    val icon = when (iconTypeInt) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Star
        else -> Icons.Filled.Favorite
    }

    // Create a MultiChoiceSegmentedButtonRow with a single button
    MultiChoiceSegmentedButtonRow {
        SegmentedButton(
            checked = checked,
            onCheckedChange = { checked = it },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 1),
            enabled = enabled,
            colors = if (useCustomColors) {
                SegmentedButtonDefaults.colors(
                    activeContainerColor = customContainerColor,
                    activeContentColor = customContentColor,
                    activeBorderColor = customBorderColor,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = customContentColor,
                    inactiveBorderColor = customBorderColor
                )
            } else {
                SegmentedButtonDefaults.colors()
            },
            border = BorderStroke(width = borderWidthFloat.dp, color = customBorderColor),
            icon = if (showIcon) {
                {
                    SegmentedButtonDefaults.Icon(active = checked) {
                        Icon(
                            imageVector = icon,
                            contentDescription = iconContentDescription,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                        )
                    }
                }
            } else {
                { /* Empty icon */ }
            }
        ) {
            Text(buttonText)
        }
    }
}

val `MultiChoiceSegmentedButtonRow Story` by story {
    // Common parameters
    val enabled by parameter(true)
    val numButtons by parameter(3)

    // Button text parameters
    val option1Text by parameter("Option 1")
    val option2Text by parameter("Option 2")
    val option3Text by parameter("Option 3")

    // Icon parameters
    val showIcons by parameter(true)

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color(0xFFE8DEF8))
    val customContentColor by parameter(Color(0xFF6750A4))
    val customBorderColor by parameter(Color(0xFF6750A4))

    // Border parameters
    val borderWidthFloat by parameter(1f)

    // Create a list to store the checked state of each button
    val checkedList = remember { mutableStateListOf<Int>() }

    // Create a list of button texts
    val options = listOf(option1Text, option2Text, option3Text)

    // Create a list of icons
    val icons = listOf(
        Icons.Filled.Favorite,
        Icons.Filled.Star,
        Icons.Filled.Settings
    )

    // Create a MultiChoiceSegmentedButtonRow with multiple buttons
    MultiChoiceSegmentedButtonRow {
        // Only show the number of buttons specified by numButtons
        options.take(numButtons).forEachIndexed { index, label ->
            SegmentedButton(
                checked = index in checkedList,
                onCheckedChange = {
                    if (index in checkedList) {
                        checkedList.remove(index)
                    } else {
                        checkedList.add(index)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = numButtons),
                enabled = enabled,
                colors = if (useCustomColors) {
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = customContainerColor,
                        activeContentColor = customContentColor,
                        activeBorderColor = customBorderColor,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = customContentColor,
                        inactiveBorderColor = customBorderColor
                    )
                } else {
                    SegmentedButtonDefaults.colors()
                },
                border = BorderStroke(width = borderWidthFloat.dp, color = customBorderColor),
                icon = if (showIcons) {
                    {
                        SegmentedButtonDefaults.Icon(active = index in checkedList) {
                            Icon(
                                imageVector = icons[index],
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        }
                    }
                } else {
                    { /* Empty icon */ }
                }
            ) {
                Text(label)
            }
        }
    }
}
