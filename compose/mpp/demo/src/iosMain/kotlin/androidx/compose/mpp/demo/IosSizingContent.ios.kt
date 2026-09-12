/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.mpp.demo

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun FixedWidthFittedHeightComposeContent() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Compose content", style = MaterialTheme.typography.h6)
            Text(
                "The cards wrap at the width proposed by SwiftUI.",
                style = MaterialTheme.typography.body2,
                color = Color.Gray,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SizingCardColors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .width(76.dp)
                            .height(56.dp)
                            .background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Card ${index + 1}", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FixedHeightFittedWidthComposeContent() {
    MaterialTheme {
        FlowColumn(
            modifier = Modifier
                .fillMaxHeight()
                .background(Color(0xFFF8FAFC))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SizingCardColors.forEachIndexed { index, color ->
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .height(56.dp)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Card ${index + 1}", color = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun NaturalSizeComposeContentChangeContent() {
    var wide by remember { mutableStateOf(false) }
    var tall by remember { mutableStateOf(false) }
    var animationEnabled by remember { mutableStateOf(true) }
    var controlsExpanded by remember { mutableStateOf(false) }
    val boxWidth by animateDpAsState(
        targetValue = if (wide) 300.dp else 180.dp,
        animationSpec = if (animationEnabled) tween(durationMillis = 500) else snap(),
        label = "boxWidth",
    )
    val boxHeight by animateDpAsState(
        targetValue = if (tall) 220.dp else 60.dp,
        animationSpec = if (animationEnabled) tween(durationMillis = 500) else snap(),
        label = "boxHeight",
    )

    MaterialTheme {
        Box(
            modifier = Modifier
                .width(boxWidth)
                .height(boxHeight)
                .background(Color(0xFFF8FAFC))
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Compose box\n${boxWidth.value.toInt()} × ${boxHeight.value.toInt()} dp",
                color = Color.Gray,
            )
            Button(
                onClick = { controlsExpanded = true },
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text("Controls")
            }
            DropdownMenu(
                expanded = controlsExpanded,
                onDismissRequest = { controlsExpanded = false },
            ) {
                DropdownMenuItem(onClick = { animationEnabled = !animationEnabled }) {
                    Text("Animation: ${if (animationEnabled) "on" else "off"}")
                }
                DropdownMenuItem(onClick = { wide = !wide }) {
                    Text("Toggle width")
                }
                DropdownMenuItem(onClick = { tall = !tall }) {
                    Text("Toggle height")
                }
                DropdownMenuItem(onClick = {
                    val expandBoth = !(wide && tall)
                    wide = expandBoth
                    tall = expandBoth
                }) {
                    Text("Toggle both")
                }
            }
        }
    }
}

@Composable
internal fun FillAvailableSpaceComposeContent() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1D4ED8))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Compose fills the bounds supplied by SwiftUI",
                color = Color.White,
                style = MaterialTheme.typography.h6,
            )
        }
    }
}

@Composable
internal fun FixedHeightComposeContent() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color(0xFFB45309))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Compose fills width, but has a fixed 120 dp height",
                color = Color.White,
                style = MaterialTheme.typography.h6,
            )
        }
    }
}

@Composable
internal fun FixedWidthComposeContent() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight()
                .background(Color(0xFF7C3AED))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Compose fills height, but has a fixed 180 dp width",
                color = Color.White,
                style = MaterialTheme.typography.h6,
            )
        }
    }
}

private val SizingCardColors = listOf(
    Color(0xFF7C3AED),
    Color(0xFF2563EB),
    Color(0xFF0891B2),
    Color(0xFF16A34A),
    Color(0xFFEA580C),
)
