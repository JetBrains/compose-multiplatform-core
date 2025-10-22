/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.mpp.demo.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import org.w3c.dom.DataTransfer
import androidx.compose.ui.draganddrop.domDataTransferOrNull

@Composable
@OptIn(ExperimentalComposeUiApi::class)
actual fun DragAndDropExample() {
    val exportedText = "Hello, DnD!"
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val textMeasurer = rememberTextMeasurer()

        // Drag sources: red, orange, yellow, green, light blue, blue, violet
        val colorSources: List<Pair<String, Color>> = listOf(
            "red" to Color.Red,
            "orange" to Color(0xFFFFA500),
            "yellow" to Color.Yellow,
            "green" to Color.Green,
            "light blue" to Color(0xFFADD8E6),
            "blue" to Color.Blue,
            "violet" to Color(0xFF8A2BE2)
        )

        // Sources row pinned to the top, centered horizontally
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                colorSources.forEach { (name, color) ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(color, shape = CircleShape)
                            .clip(CircleShape)
                            .border(BorderStroke(1.dp, Color.Black), shape = CircleShape)
                            .dragAndDropSource(
                                drawDragDecoration = {
                                    // simple solid color preview
                                    drawRect(color = color, topLeft = Offset.Zero, size = Size(size.width, size.height))
                                    val textLayoutResult = textMeasurer.measure(
                                        text = AnnotatedString(name),
                                        layoutDirection = layoutDirection,
                                        density = this
                                    )
                                    drawText(
                                        textLayoutResult = textLayoutResult,
                                        topLeft = Offset(
                                            x = (size.width - textLayoutResult.size.width) / 2,
                                            y = (size.height - textLayoutResult.size.height) / 2,
                                        )
                                    )
                                }
                            ) { _ ->
                                val dataTransfer = createDataTransfer()
                                dataTransfer.setData("text/plain", "color:$name")
                                DragAndDropTransferData(dataTransfer)
                            }
                    ) {}
                }
            }
        }

        var showTargetBorder by remember { mutableStateOf(false) }
        var showHovered by remember { mutableStateOf(false) }
        var dragCounter by remember { mutableStateOf(0) }
        var targetText by remember { mutableStateOf("Drop Here") }
        val pieSlices = remember { mutableStateListOf<Color>() }

        val dragAndDropTarget = remember {
            object: DragAndDropTarget {
                override fun onStarted(event: DragAndDropEvent) {
                    showTargetBorder = true
                }

                override fun onEnded(event: DragAndDropEvent) {
                    showTargetBorder = false
                }

                override fun onEntered(event: DragAndDropEvent) {
                    showHovered = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    showHovered = false
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    showHovered = false
                    event.transferData?.domDataTransferOrNull?.let { dataTransfer ->
                        val dataText = dataTransfer?.getData("text/plain") ?: ""
                        val droppedColor = if (dataText.startsWith("color:")) {
                            val name = dataText.removePrefix("color:")
                            colorSources.firstOrNull { it.first == name }?.second
                        } else null
                        droppedColor?.let { pieSlices.add(it) }
                    }
                    dragCounter++
                    return true
                }
            }
        }

        // Drag target stays centered in the viewport independently of the sources above
        val glowPadding = 24.dp
        val glowColor = if (pieSlices.isEmpty()) {
            Color.Black
        } else {
            var r = 0f; var g = 0f; var b = 0f
            pieSlices.forEach { c -> r += c.red; g += c.green; b += c.blue }
            val n = pieSlices.size
            Color(r / n, g / n, b / n)
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(200.dp + glowPadding * 2)
                .drawBehind {
                    if (showTargetBorder) {
                        val glowPaddingPx = glowPadding.toPx()
                        val outerR = size.minDimension / 2f
                        val innerR = outerR - glowPaddingPx
                        if (pieSlices.isNotEmpty()) {
                            // Segmented glow: draw a colored ring mirroring the pie chart segments
                            val counts = pieSlices.groupingBy { it }.eachCount()
                            val total = pieSlices.size.toFloat()
                            var start = -90f
                            val arcSize = Size(outerR * 2f, outerR * 2f)
                            val topLeft = Offset(center.x - outerR, center.y - outerR)
                            counts.entries.forEach { (color, count) ->
                                val sweep = (count.toFloat() / total) * 360f
                                drawArc(
                                    color = color.copy(alpha = 0.6f),
                                    startAngle = start,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = glowPaddingPx)
                                )
                                start += sweep
                            }
                        } else {
                            // Fallback soft radial glow when there are no segments yet
                            val ratio = if (outerR > 0f) innerR / outerR else 0f
                            val brush = Brush.radialGradient(
                                (maxOf(0f, ratio - 0.05f)) to Color.Transparent,
                                ratio to glowColor.copy(alpha = 0.5f),
                                1f to Color.Transparent,
                                center = center,
                                radius = outerR
                            )
                            drawCircle(brush = brush, radius = outerR, center = center)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(if (showHovered) Color.Magenta else Color.LightGray, shape = CircleShape)
                    .clip(CircleShape)
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { true },
                        target = dragAndDropTarget
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Draw pie chart based on dropped colors
                Canvas(Modifier.fillMaxSize()) {
                    if (pieSlices.isNotEmpty()) {
                        val counts = pieSlices.groupingBy { it }.eachCount()
                        val total = pieSlices.size.toFloat()
                        var start = -90f
                        counts.entries.forEach { (color, count) ->
                            val sweep = (count.toFloat() / total) * 360f
                            drawArc(
                                color = color,
                                startAngle = start,
                                sweepAngle = sweep,
                                useCenter = true,
                                size = size
                            )
                            start += sweep
                        }
                    }
                }

                Text(targetText + " [" + dragCounter + "]", Modifier.align(Alignment.Center))
            }
        }
    }
}

private fun createDataTransfer(): DataTransfer =  js("new DataTransfer()")