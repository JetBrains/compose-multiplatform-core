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

package androidx.compose.mpp.demo.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val Palette = listOf(
    Color(0xFF0000AA),
    Color(0xFF00AA00),
    Color(0xFFAA0000),
    Color(0xFFAA00AA),
    Color(0xFF00AAAA),
    Color(0xFFAA5500),
    Color(0xFF5555FF),
    Color(0xFF55AA55),
)

private data class TouchPointer(
    val position: Offset,
    val type: PointerType,
)

@Composable
fun MultitouchExample() {
    val pointers = remember { mutableStateMapOf<PointerId, TouchPointer>() }
    var lastInfo by remember { mutableStateOf("Touch the area below with one or more pointers") }

    Column(
        Modifier.fillMaxSize()
    ) {
        Text(
            text = lastInfo,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color(0xFFF5F5F5))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.pressed) {
                                    pointers[change.id] = TouchPointer(change.position, change.type)
                                } else {
                                    pointers.remove(change.id)
                                }
                                change.consume()
                            }
                            if (pointers.isNotEmpty()) {
                                lastInfo = "pointers=${pointers.size}, " +
                                    "types=${pointers.values.map { it.type.name() }.toSet().joinToString()}"
                            }
                        }
                    }
                }
        ) {
            for ((id, pointer) in pointers) {
                val color = Palette[(id.value % Palette.size).toInt()]
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = 32.dp.toPx(),
                    center = pointer.position,
                )
                drawCircle(
                    color = color,
                    radius = 32.dp.toPx(),
                    center = pointer.position,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

private fun PointerType.name(): String = when (this) {
    PointerType.Touch -> "Touch"
    PointerType.Mouse -> "Mouse"
    PointerType.Stylus -> "Stylus"
    PointerType.Eraser -> "Eraser"
    else -> "Unknown"
}
