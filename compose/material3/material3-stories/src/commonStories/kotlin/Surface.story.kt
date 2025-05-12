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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `Surface Story` by story {
    // Size parameters
    val width by parameter(200f)
    val height by parameter(120f)

    // Shape parameters
    val shapeType by parameter("Rounded")
    val cornerSize by parameter(8f)

    // Color parameters
    val useThemeColors by parameter(true)
    val customColor by parameter(Color.LightGray)
    val customContentColor by parameter(Color.Black)

    // Elevation parameters
    val shadowElevation by parameter(0f)

    // Border parameters
    val showBorder by parameter(false)
    val borderWidth by parameter(2f)
    val borderColor by parameter(Color.Blue)

    // Determine shape based on selection
    val shape: Shape = when (shapeType) {
        "Rectangle" -> RectangleShape
        "Circle" -> CircleShape
        else -> RoundedCornerShape(cornerSize.dp)
    }

    // Determine colors based on selection
    val color = if (useThemeColors) MaterialTheme.colorScheme.surface else customColor
    val contentColor = if (useThemeColors) contentColorFor(color) else customContentColor

    // Determine border
    val border = if (showBorder) BorderStroke(borderWidth.dp, borderColor) else null

    // Create the surface with all parameters
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Surface Demonstration",
            style = MaterialTheme.typography.titleMedium
        )

        Surface(
            modifier = Modifier.size(width.dp, height.dp),
            shape = shape,
            color = color,
            contentColor = contentColor,
            shadowElevation = shadowElevation.dp,
            border = border
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Surface Content")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Shape: $shapeType",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (shadowElevation > 0f) {
                        Text(
                            "Elevation: Shadow=${shadowElevation}dp",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
