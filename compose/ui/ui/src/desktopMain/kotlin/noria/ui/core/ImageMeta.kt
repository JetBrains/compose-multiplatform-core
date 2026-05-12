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

package noria.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Picture

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun Image(
    width: Dp?,
    height: Dp?,
    rotation: State<Float>? = null,
    paintModifier: Paint? = null, // dispose should be handled manually
    loader: (IntSize, Density) -> Deferred<Picture?>
) {
    val density = LocalDensity.current
    val specifiedPhysicalWidth = with(density) { width?.roundToPx() }
    val specifiedPhysicalHeight = with(density) { height?.roundToPx() }
    var picture by remember { mutableStateOf<Picture?>(null) }
    LaunchedEffect(loader, specifiedPhysicalWidth, specifiedPhysicalHeight, density) {
        val size = IntSize(specifiedPhysicalWidth ?: 0, specifiedPhysicalHeight ?: 0)
        picture = loader(size, density).await()
    }
    val painter = remember {
        object : Painter() {
            override val intrinsicSize: Size
                get() = Size(
                    specifiedPhysicalWidth?.toFloat() ?: picture?.cullRect?.width ?: 0f,
                    specifiedPhysicalHeight?.toFloat() ?: picture?.cullRect?.height ?: 0f
                )

            override fun DrawScope.onDraw() {
                rotate(rotation?.value ?: 0f) {
                    drawIntoCanvas { canvas ->
                        picture?.let {
                            canvas.nativeCanvas.drawPicture(it, paint = paintModifier)
                        }
                    }
                }
            }
        }
    }
    // Explicitly use a simple Layout implementation here as Spacer squashes any non-fixed
    // constraint with zero
    Layout(
        Modifier
            .clipToBounds()
            .paint(painter)
    ) { _, constraints ->
        val containerSize = constraints.constrain(
            IntSize(
                specifiedPhysicalWidth ?: 0,
                specifiedPhysicalHeight ?: 0
            )
        )

        layout(containerSize.width, containerSize.height) {}
    }
}