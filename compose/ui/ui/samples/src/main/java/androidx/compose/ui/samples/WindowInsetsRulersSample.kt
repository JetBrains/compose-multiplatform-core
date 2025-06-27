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
package androidx.compose.ui.samples

import androidx.annotation.Sampled
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.InsetsRulers
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.fontscaling.MathUtils.lerp
import kotlin.math.roundToInt

@Composable
@Sampled
fun WindowInsetsRulersSample() {
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(Modifier.background(Color.Blue)) // top area (e.g. status bar)
            Box(Modifier.background(Color.Yellow)) // bottom area (e.g. navigation bar)
            Box(Modifier.background(Color.White)) // content between top and bottom
        },
        measurePolicy = { measurables, constraints ->
            if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                layout(width, height) {
                    val top = maxOf(0, InsetsRulers.SystemBars.top.current(0f).roundToInt())
                    val topArea = measurables[0].measure(Constraints.fixed(width, top))
                    topArea.place(0, 0)

                    val bottom =
                        minOf(height, InsetsRulers.SystemBars.bottom.current(0f).roundToInt())
                    val bottomArea =
                        measurables[1].measure(Constraints.fixed(width, height - bottom))
                    bottomArea.place(0, bottom)

                    val contentArea = measurables[2].measure(Constraints.fixed(width, bottom - top))
                    contentArea.place(0, top)
                }
            } else {
                // It should only get here if inside scrollable content or trying to align
                // to an alignment line. Only place the content.
                val placeable = measurables[2].measure(constraints) // content
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        }
    )
}

@Composable
@Sampled
fun InsetsRulersAlphaSample() {
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(Modifier.background(Color.Blue)) // status bar background
            Box(Modifier.background(Color.Yellow)) // navigation bar background on bottom
            Box(Modifier.background(Color.White)) // content between top and bottom
        },
        measurePolicy = { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            layout(width, height) {
                val top = InsetsRulers.StatusBars.top.current(0f).roundToInt()
                val bottom = InsetsRulers.NavigationBars.bottom.current(0f).roundToInt()
                measurables[0].measure(Constraints.fixed(width, top)).placeWithLayer(0, 0) {
                    alpha = InsetsRulers.StatusBars.alpha(this@layout)
                }
                measurables[2].measure(Constraints.fixed(width, height - bottom)).place(0, bottom)
                measurables[1].measure(Constraints.fixed(width, bottom - top)).placeWithLayer(
                    0,
                    top
                ) {
                    alpha = InsetsRulers.NavigationBars.alpha(this@layout)
                }
            }
        }
    )
}

@Composable
@Sampled
fun AnimatableInsetsRulersSample() {
    class LandOnImeModifierNode : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints
        ): MeasureResult {
            return if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                layout(width, height) {
                    val node = this@LandOnImeModifierNode
                    val placeable = measurable.measure(constraints)
                    val bottom =
                        with(node) {
                            if (!InsetsRulers.Ime.isAnimating(node)) {
                                if (InsetsRulers.Ime.isVisible(node)) {
                                    InsetsRulers.Ime.bottom.current(Float.NaN)
                                } else {
                                    Float.NaN
                                }
                            } else {
                                val start = InsetsRulers.Ime.source.bottom.current(Float.NaN)
                                val end = InsetsRulers.Ime.target.bottom.current(Float.NaN)
                                val fraction = InsetsRulers.Ime.interpolatedFraction(node)
                                if (start.isNaN() || end.isNaN()) {
                                    Float.NaN // don't know where it is animating
                                } else if (start > end) { // animate IME up
                                    lerp(placeable.height.toFloat(), end, fraction)
                                } else { // animating down
                                    lerp(start, placeable.height.toFloat(), fraction)
                                }
                            }
                        }
                    val y =
                        if (bottom.isNaN()) {
                            0 // place at the top
                        } else if (bottom > height) {
                            height - placeable.height // place at the bottom
                        } else { // place somewhere in the middle
                            bottom.roundToInt() - placeable.height
                        }
                    placeable.place(0, y)
                }
            } else {
                // Can't work with the rulers if we can't take the full size
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        }
    }

    class LandOnImeElement : ModifierNodeElement<LandOnImeModifierNode>() {
        override fun create(): LandOnImeModifierNode = LandOnImeModifierNode()

        override fun hashCode(): Int = 0

        override fun equals(other: Any?): Boolean = other is LandOnImeElement

        override fun update(node: LandOnImeModifierNode) {}
    }

    Box(Modifier.fillMaxSize()) {
        Box(LandOnImeElement()) {
            // This content will rest at the top, but then animate to land on the IME when it is
            // animated in.
            Box(Modifier.size(100.dp).background(Color.Blue))
        }
        TextField(
            "Hello World",
            onValueChange = {},
            Modifier.safeDrawingPadding().align(Alignment.BottomEnd)
        )
    }
}
