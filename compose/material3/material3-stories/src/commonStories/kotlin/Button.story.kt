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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import org.jetbrains.compose.storytale.story

val `Button Story` by story {
    // Define extension properties and functions inside the story
    val squareShape: Shape = RoundedCornerShape(0.dp)

    // Common parameters
    val buttonText by parameter("Button")
    val enabled by parameter(true)

    // Icon parameters
    val showIcon by parameter(false)
    val iconContentDescription by parameter("Favorite")

    // Shape parameters
    val useSquareShape by parameter(false)

    // Size parameters
    val buttonSizeFloat by parameter(48f)

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color.Magenta)
    val customContentColor by parameter(Color.White)

    // Border parameters
    val showBorder by parameter(false)
    val borderWidthFloat by parameter(2f)
    val borderColor by parameter(Color.Black)

    Button(
        onClick = {},
        enabled = enabled,
        shape = if (useSquareShape) squareShape else ButtonDefaults.shape,
        contentPadding = if (showIcon) {
            ButtonDefaults.ButtonWithIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        },
        colors = if (useCustomColors) ButtonDefaults.buttonColors(
            containerColor = customContainerColor,
            contentColor = customContentColor
        ) else ButtonDefaults.buttonColors(),
        border = if (showBorder) BorderStroke(borderWidthFloat.dp, borderColor) else null,
        modifier = Modifier.heightIn(min = buttonSizeFloat.dp)
    ) {
        if (showIcon) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }
        Text(
            text = buttonText,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

val `ElevatedButton Story` by story {
    // Define square shape
    val squareShape: Shape = RoundedCornerShape(0.dp)

    // Common parameters
    val buttonText by parameter("Elevated Button")
    val enabled by parameter(true)

    // Icon parameters
    val showIcon by parameter(false)
    val iconContentDescription by parameter("Favorite")

    // Shape parameters
    val useSquareShape by parameter(false)

    // Size parameters
    val buttonSizeFloat by parameter(48f)

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color.LightGray)
    val customContentColor by parameter(Color.Black)

    // Border parameters
    val showBorder by parameter(false)
    val borderWidthFloat by parameter(1f) // Equivalent to 1.dp
    val borderColor by parameter(Color.Black)

    ElevatedButton(
        onClick = {},
        enabled = enabled,
        shape = if (useSquareShape) squareShape else ButtonDefaults.elevatedShape,
        contentPadding = if (showIcon) {
            ButtonDefaults.ButtonWithIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        },
        colors = if (useCustomColors) ButtonDefaults.elevatedButtonColors(
            containerColor = customContainerColor,
            contentColor = customContentColor
        ) else ButtonDefaults.elevatedButtonColors(),
        elevation = ButtonDefaults.elevatedButtonElevation(),
        border = if (showBorder) BorderStroke(borderWidthFloat.dp, borderColor) else null,
        modifier = Modifier.heightIn(min = buttonSizeFloat.dp)
    ) {
        if (showIcon) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }
        Text(
            text = buttonText,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

val `FilledTonalButton Story` by story {
    // Define square shape
    val squareShape: Shape = RoundedCornerShape(0.dp)
    // Common parameters
    val buttonText by parameter("Filled Tonal Button")
    val enabled by parameter(true)

    // Icon parameters
    val showIcon by parameter(false)
    val iconContentDescription by parameter("Favorite")

    // Shape parameters
    val useSquareShape by parameter(false)

    // Size parameters
    val buttonSizeFloat by parameter(48f)

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color(0xff000000))
    val customContentColor by parameter(Color(0xFF1D192B))

    // Border parameters
    val showBorder by parameter(false)
    val borderWidthFloat by parameter(1f) // Equivalent to 1.dp
    val borderColor by parameter(Color.Black)

    FilledTonalButton(
        onClick = {},
        enabled = enabled,
        shape = if (useSquareShape) squareShape else ButtonDefaults.filledTonalShape,
        contentPadding = if (showIcon) {
            ButtonDefaults.ButtonWithIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        },
        colors = if (useCustomColors) ButtonDefaults.filledTonalButtonColors(
            containerColor = customContainerColor,
            contentColor = customContentColor
        ) else ButtonDefaults.filledTonalButtonColors(),
        elevation = ButtonDefaults.filledTonalButtonElevation(),
        border = if (showBorder) BorderStroke(borderWidthFloat.dp, borderColor) else null,
        modifier = Modifier.heightIn(min = buttonSizeFloat.dp)
    ) {
        if (showIcon) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }
        Text(
            text = buttonText,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

val `OutlinedButton Story` by story {
    // Define square shape
    val squareShape: Shape = RoundedCornerShape(0.dp)

    // Common parameters
    val buttonText by parameter("Outlined Button")
    val enabled by parameter(true)

    // Icon parameters
    val showIcon by parameter(false)
    val iconContentDescription by parameter("Favorite")

    // Shape parameters
    val useSquareShape by parameter(false)

    // Size parameters
    val buttonSizeFloat by parameter(48f)

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color.Transparent)
    val customContentColor by parameter(Color(0xFF6750A4))

    // Border parameters
    val customBorder by parameter(false)
    val borderWidthFloat by parameter(1f) // Equivalent to 1.dp
    val borderColor by parameter(Color(0xFF79747E))

    // Create the button with all parameters
    OutlinedButton(
        onClick = {},
        enabled = enabled,
        shape = if (useSquareShape) squareShape else ButtonDefaults.outlinedShape,
        contentPadding = if (showIcon) {
            ButtonDefaults.ButtonWithIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        },
        colors = if (useCustomColors) ButtonDefaults.outlinedButtonColors(
            containerColor = customContainerColor,
            contentColor = customContentColor
        ) else ButtonDefaults.outlinedButtonColors(),
        border = if (customBorder) BorderStroke(borderWidthFloat.dp, borderColor) else ButtonDefaults.outlinedButtonBorder(enabled),
        modifier = Modifier.heightIn(min = buttonSizeFloat.dp)
    ) {
        if (showIcon) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }
        Text(
            text = buttonText,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

val `TextButton Story` by story {
    // Define square shape
    val squareShape: Shape = RoundedCornerShape(0.dp)

    // Common parameters
    val buttonText by parameter("Text Button")
    val enabled by parameter(true)

    // Icon parameters
    val showIcon by parameter(false)
    val iconContentDescription by parameter("Favorite")

    // Shape parameters
    val useSquareShape by parameter(false)

    // Size parameters
    val buttonSizeFloat by parameter(48f)

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color.Transparent)
    val customContentColor by parameter(Color(0xFF6750A4))

    // Border parameters
    val showBorder by parameter(false)
    val borderWidthFloat by parameter(1f) // Equivalent to 1.dp
    val borderColor by parameter(Color.Black)

    // Create the button with all parameters
    TextButton(
        onClick = {},
        enabled = enabled,
        shape = if (useSquareShape) squareShape else ButtonDefaults.textShape,
        contentPadding = if (showIcon) {
            ButtonDefaults.ButtonWithIconContentPadding
        } else {
            ButtonDefaults.TextButtonContentPadding
        },
        colors = if (useCustomColors) ButtonDefaults.textButtonColors(
            containerColor = customContainerColor,
            contentColor = customContentColor
        ) else ButtonDefaults.textButtonColors(),
        border = if (showBorder) BorderStroke(borderWidthFloat.dp, borderColor) else null,
        modifier = Modifier.heightIn(min = buttonSizeFloat.dp)
    ) {
        if (showIcon) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }
        Text(
            text = buttonText,
            style = MaterialTheme.typography.labelLarge
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
