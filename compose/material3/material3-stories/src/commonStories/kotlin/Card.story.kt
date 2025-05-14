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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `Card Story` by story {
    val cardText by parameter("Card content")
    val enabled by parameter(true)
    val cornerRadius by parameter(8f)
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFFE8DEF8))
    val contentColor by parameter(Color(0xFF1D192B))
    val elevationValue by parameter(4f)
    val useBorder by parameter(false)
    val borderWidth by parameter(2f)
    val borderColor by parameter(Color(0xFF6750A4))
    val paddingValue by parameter(0f)
    val displayText = cardText
    var isToggled by remember { mutableStateOf(false) }

    Card(
        onClick = { isToggled = !isToggled },
        modifier = Modifier.size(width = 180.dp, height = 100.dp),
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = if (useCustomColors) {
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevationValue.dp
        ),
        border = if (useBorder) {
            BorderStroke(width = borderWidth.dp, color = borderColor)
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValue.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(displayText)
        }
    }
}

val `ElevatedCard Story` by story {
    val cardText by parameter("Elevated Card content")
    val cornerRadius by parameter(8f)
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color(0xFFE8DEF8))
    val contentColor by parameter(Color(0xFF1D192B))
    val useCustomElevation by parameter(false)
    val defaultElevation by parameter(1f)
    val hoveredElevation by parameter(4f)
    val focusedElevation by parameter(2f)
    val pressedElevation by parameter(6f)
    val draggedElevation by parameter(8f)
    val paddingValue by parameter(16f)

    ElevatedCard(
        modifier = Modifier.size(width = 180.dp, height = 100.dp),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = if (useCustomColors) {
            CardDefaults.elevatedCardColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
        elevation = if (useCustomElevation) {
            CardDefaults.elevatedCardElevation(
                defaultElevation = defaultElevation.dp,
                hoveredElevation = hoveredElevation.dp,
                focusedElevation = focusedElevation.dp,
                pressedElevation = pressedElevation.dp,
                draggedElevation = draggedElevation.dp
            )
        } else {
            CardDefaults.elevatedCardElevation()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValue.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(cardText)
        }
    }
}

val `OutlinedCard Story` by story {
    val cardText by parameter("Outlined Card content")
    val cornerRadius by parameter(8f)

    // Colors
    val useCustomColors by parameter(false)
    val containerColor by parameter(Color.Transparent)
    val contentColor by parameter(Color(0xFF1D192B))

    // Elevation
    val useCustomElevation by parameter(false)
    val defaultElevation by parameter(0f)
    val hoveredElevation by parameter(2f)
    val focusedElevation by parameter(1f)
    val pressedElevation by parameter(3f)
    val draggedElevation by parameter(4f)

    // Border
    val useCustomBorder by parameter(false)
    val borderWidth by parameter(1f)
    val borderColor by parameter(Color(0xFF6750A4))

    // Padding
    val paddingValue by parameter(16f)

    // State for tracking interactions
    var isToggled by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    OutlinedCard(
        onClick = { isToggled = !isToggled },
        modifier = Modifier.size(width = 180.dp, height = 100.dp),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = if (useCustomColors) {
            CardDefaults.outlinedCardColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        } else {
            CardDefaults.outlinedCardColors()
        },
        elevation = if (useCustomElevation) {
            CardDefaults.outlinedCardElevation(
                defaultElevation = defaultElevation.dp,
                hoveredElevation = hoveredElevation.dp,
                focusedElevation = focusedElevation.dp,
                pressedElevation = pressedElevation.dp,
                draggedElevation = draggedElevation.dp
            )
        } else {
            CardDefaults.outlinedCardElevation()
        },
        border = if (useCustomBorder) {
            BorderStroke(width = borderWidth.dp, color = borderColor)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValue.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(cardText + (if (isToggled) " (Clicked)" else ""))
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
