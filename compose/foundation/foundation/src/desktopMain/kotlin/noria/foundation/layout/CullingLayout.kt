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

package noria.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect

@Composable
fun CullingLayout(
    modifier: Modifier = Modifier,
    measurePolicy: CullingLayoutMeasureScope.(Constraints) -> MeasureResult,
) {
    //if (DEBUG) println("[CullingLayout] Composing")
    var visibleBounds by remember { mutableStateOf<IntRect?>(null) }
    SubcomposeLayout(
        modifier.extractLayoutCoordinates { visibleBounds = it.recursivelyClippedBounds().roundToIntRect() }
    ) { constraints ->
        //if (DEBUG) println("[CullingLayout] Evaluating subcomposition measurePolicy with $constraints")
        val cullingLayoutMeasureScope = object : CullingLayoutMeasureScope, MeasureScope by this {
            override fun layout(
                width: Int,
                height: Int,
                alignmentLines: Map<AlignmentLine, Int>,
                content: @Composable (visibleBounds: IntRect) -> Unit
            ): MeasureResult {
                return this@SubcomposeLayout.layout(width, height, alignmentLines) {
                    //if (DEBUG) println("[CullingLayout] Placing inside subcomposition")
                    if (visibleBounds?.isEmpty == false) {
                        this@SubcomposeLayout.subcompose(Unit) {
                            //if (DEBUG) println("[CullingLayout] Composing content with visibleBounds $visibleBounds")
                            content.invoke(visibleBounds!!)
                        }.forEach {
                            it.measure(constraints).place(0, 0)
                        }
                    }
                }
            }
        }
        cullingLayoutMeasureScope.measurePolicy(constraints)
    }
}

interface CullingLayoutMeasureScope : MeasureScope {
    fun layout(
        width: Int,
        height: Int,
        alignmentLines: Map<AlignmentLine, Int> = emptyMap(),
        content: @Composable (visibleBounds: IntRect) -> Unit
    ): MeasureResult
}

fun LayoutCoordinates.recursivelyClippedBounds(): Rect {
    var aggregatedOffset = Offset.Zero
    val clippedBounds = MutableRect(0f, 0f, size.width.toFloat(), size.height.toFloat())
    var currentCoordinates = this
    while (currentCoordinates.parentCoordinates != null && !clippedBounds.isEmpty) {
        val parentCoordinates = currentCoordinates.parentCoordinates!!
        val localBoundingBox = parentCoordinates.localBoundingBoxOf(currentCoordinates)
        aggregatedOffset += localBoundingBox.topLeft
        clippedBounds.translate(localBoundingBox.topLeft)
        clippedBounds.intersect(localBoundingBox)
        currentCoordinates = parentCoordinates
    }
    clippedBounds.translate(-aggregatedOffset)
    return clippedBounds.toRect()
}

private fun MutableRect.translate(offset: Offset) {
    left += offset.x
    top += offset.y
    right += offset.x
    bottom += offset.y
}

private fun MutableRect.intersect(other: Rect) {
    intersect(other.left, other.top, other.right, other.bottom)
}

@OptIn(ExperimentalComposeUiApi::class)
// Workaround for https://issuetracker.google.com/issues/279891775
fun Modifier.extractLayoutCoordinates(
    onExtracted: (layoutCoordinates: LayoutCoordinates) ->
    Unit
): Modifier {
    return this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val coordinates = coordinates
            if (coordinates != null) {
                //if (DEBUG) println("[extractLayoutCoordinates] Extracted $coordinates (${coordinates.boundsInRoot()})")
                onExtracted(coordinates)
            }
            //else if (DEBUG) println("[extractLayoutCoordinates] No coordinates given")
            placeable.place(0, 0)
        }
    }
}
