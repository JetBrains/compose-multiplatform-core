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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val PanPinchCircleExample = Screen.Example("Pan & Pinch Circle") {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            scale *= change.scaleFactor
                            offset += change.panOffset
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = scale
                    scaleY = scale
                }
                .size(120.dp)
                .background(Color(0xFF2979FF), shape = CircleShape)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Two-finger pan & pinch on a trackpad to move and scale the circle.")
            Text("The pan and pinch pipelines are trackpad-only on iOS.")
            Spacer(Modifier.height(8.dp))
            Text("scaleFactor (accumulated): ${(scale * 1000).roundToInt() / 1000.0}")
            Text("panOffset (accumulated): (${offset.x.roundToInt()}, ${offset.y.roundToInt()})")
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                offset = Offset.Zero
                scale = 1f
            }) {
                Text("Reset")
            }
        }
    }
}
