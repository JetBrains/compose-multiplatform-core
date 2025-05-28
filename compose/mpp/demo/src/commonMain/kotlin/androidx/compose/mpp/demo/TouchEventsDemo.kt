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

package androidx.compose.mpp.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TouchEventsDemo() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Touch Events Demo",
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

//        // Simple tap detection
//        TapDemo()
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Drag gesture demo
//        DragDemo()
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Multi-touch transform demo
//        TransformDemo()
//
//        Spacer(modifier = Modifier.height(16.dp))

        // Five-finger multitouch demo
        FiveFingerTouchDemo()
    }
}

@Composable
fun TapDemo() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tap Detection", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tap, double-tap, or long press the colored box below:")

            var tapInfo by remember { mutableStateOf("No taps yet") }
            var boxColor by remember { mutableStateOf(Color.LightGray) }

            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(200.dp, 100.dp)
                    .background(boxColor)
                    .border(2.dp, Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { 
                                tapInfo = "Single tap at (${it.x.toInt()}, ${it.y.toInt()})"
                                boxColor = Color.Green
                            },
                            onDoubleTap = { 
                                tapInfo = "Double tap at (${it.x.toInt()}, ${it.y.toInt()})"
                                boxColor = Color.Blue
                            },
                            onLongPress = { 
                                tapInfo = "Long press at (${it.x.toInt()}, ${it.y.toInt()})"
                                boxColor = Color.Red
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tapInfo,
                    color = Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun DragDemo() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Drag Gesture", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Drag the circle around:")

            var position by remember { mutableStateOf(Offset(100f, 50f)) }
            var dragInfo by remember { mutableStateOf("Drag the circle") }

            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(2.dp, Color.Black)
            ) {
                // Display drag info text
                Text(
                    text = dragInfo,
                    modifier = Modifier.padding(8.dp),
                    color = Color.Black
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragInfo = "Started drag at (${offset.x.toInt()}, ${offset.y.toInt()})"
                                },
                                onDragEnd = {
                                    dragInfo = "Ended drag at (${position.x.toInt()}, ${position.y.toInt()})"
                                },
                                onDragCancel = {
                                    dragInfo = "Drag canceled"
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    position = Offset(
                                        (position.x + dragAmount.x).coerceIn(0f, size.width - 50f),
                                        (position.y + dragAmount.y).coerceIn(0f, size.height - 50f)
                                    )
                                    dragInfo = "Dragging to (${position.x.toInt()}, ${position.y.toInt()})"
                                }
                            )
                        }
                ) {
                    // Draw the draggable circle
                    drawCircle(
                        color = Color.Blue,
                        radius = 50f,
                        center = position
                    )
                }
            }
        }
    }
}

@Composable
fun TransformDemo() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Multi-touch Transform", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Use two fingers to pan, zoom, and rotate:")

            var offset by remember { mutableStateOf(Offset.Zero) }
            var zoom by remember { mutableStateOf(1f) }
            var angle by remember { mutableStateOf(0f) }
            var transformInfo by remember { mutableStateOf("Use two fingers to transform") }

            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(300.dp)
                    .border(2.dp, Color.Black)
            ) {
                // Display transform info text
                Text(
                    text = transformInfo,
                    modifier = Modifier.padding(8.dp),
                    color = Color.Black
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoomChange, rotationChange ->
                                offset += pan
                                zoom *= zoomChange
                                angle += rotationChange
                                transformInfo = "Pan: $pan, Zoom: ${(zoom * 100).toInt() / 100f}, Rotation: ${(angle * 10).toInt() / 10f}°"
                            }
                        }
                ) {
                    val canvasCenter = Offset(size.width / 2, size.height / 2)
                    val rectSize = 100f * zoom

                    // Apply transformations sequentially
                    translate(canvasCenter.x + offset.x, canvasCenter.y + offset.y) {
                        rotate(angle) {
                            scale(zoom) {
                                // Draw a rectangle
                                drawRect(
                                    color = Color.Red,
                                    topLeft = Offset(-rectSize / 2, -rectSize / 2),
                                    size = Size(rectSize, rectSize),
                                    style = Stroke(width = 5f)
                                )

                                // Draw diagonals
                                drawLine(
                                    color = Color.Blue,
                                    start = Offset(-rectSize / 2, -rectSize / 2),
                                    end = Offset(rectSize / 2, rectSize / 2),
                                    strokeWidth = 3f
                                )
                                drawLine(
                                    color = Color.Blue,
                                    start = Offset(rectSize / 2, -rectSize / 2),
                                    end = Offset(-rectSize / 2, rectSize / 2),
                                    strokeWidth = 3f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FiveFingerTouchDemo() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Five-Finger Multitouch", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Touch the area below with up to five fingers:")

            // Track the offset for every pointer ID that is currently "down"
            val touchPoints = remember { mutableStateOf(mapOf<Int, Offset>()) }
            var touchInfo by remember { mutableStateOf("No touches yet") }

            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(300.dp)
                    .border(2.dp, Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                // Get all touch points from the pointer event
                                val pointerEvent = awaitPointerEvent()

                                // Convert the changes to a map of index to position
                                val currentTouches = pointerEvent.changes
                                    .filter { it.pressed }
                                    .mapIndexed { index, change -> 
                                        index to change.position
                                    }
                                    .toMap()

                                // Update the touch points
                                touchPoints.value = currentTouches

                                // Update the touch info text
                                touchInfo = if (currentTouches.isEmpty()) {
                                    "No touches"
                                } else {
                                    "Detected ${currentTouches.size} finger${if (currentTouches.size > 1) "s" else ""}"
                                }

                                // Consume all changes
                                pointerEvent.changes.forEach { it.consume() }
                            }
                        }
                    }
            ) {
                // Display touch info text
                Text(
                    text = touchInfo,
                    modifier = Modifier.padding(8.dp),
                    color = Color.Black
                )

                val radius = remember { Animatable(50f) }

                LaunchedEffect(radius) {
                    radius.animateTo(
                        targetValue = 150f,
                        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
                    )
                }

                // Draw circles for each touch point
                Canvas(modifier = Modifier.fillMaxSize()) {
                    touchPoints.value.forEach { (index, position) ->
                        // Use different colors for each finger (up to 5)
                        val color = when (index) {
                            0 -> Color.Red
                            1 -> Color.Blue
                            2 -> Color.Green
                            3 -> Color.Yellow
                            4 -> Color.Magenta
                            else -> Color.Gray
                        }

                        // Draw a circle at the touch position
                        drawCircle(
                            color = color,
                            radius = radius.value,
                            center = position,
                            alpha = 0.7f
                        )

                        // Draw the finger number
                        drawCircle(
                            color = Color.White,
                            radius = 20f,
                            center = position
                        )

                        // Draw text for finger number (platform-agnostic approach)
                        drawCircle(
                            color = Color.Black,
                            radius = 20f,
                            center = position.copy(x = position.x, y = position.y),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }
        }
    }
}
