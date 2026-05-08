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

package noria.foundation.layout.internal

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastForEachIndexed
import kotlin.math.max
import noria.foundation.layout.OverlayState

internal class OverlayBoxMeasurePolicy(
    private val overlayState: OverlayState,
    private val contentAlignment: Alignment = Alignment.TopStart,
    private val propagateMinConstraints: Boolean = false,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        if (measurables.isEmpty()) {
            return layout(constraints.minWidth, constraints.minHeight) {}
        }

        val contentConstraints =
            if (propagateMinConstraints) {
                constraints
            } else {
                constraints.copy(minWidth = 0, minHeight = 0)
            }

        if (measurables.size == 1) {
            val measurable = measurables[0]
            val boxWidth: Int
            val boxHeight: Int
            val placeable: Placeable
            if (!measurable.matchesParentSize) {
                placeable = measurable.measure(contentConstraints)
                boxWidth = max(constraints.minWidth, placeable.width)
                boxHeight = max(constraints.minHeight, placeable.height)
            } else {
                boxWidth = constraints.minWidth
                boxHeight = constraints.minHeight
                placeable =
                    measurable.measure(
                        Constraints.fixed(constraints.minWidth, constraints.minHeight)
                    )
            }
            return layout(boxWidth, boxHeight) {
                placeInBox(
                    placeable,
                    measurable,
                    layoutDirection,
                    boxWidth,
                    boxHeight,
                    contentAlignment,
                    overlayState.anchorBounds!!,
                )
            }
        }

        val placeables = arrayOfNulls<Placeable>(measurables.size)
        // First measure non match parent size children to get the size of the Box.
        var hasMatchParentSizeChildren = false
        var boxWidth = constraints.minWidth
        var boxHeight = constraints.minHeight
        measurables.fastForEachIndexed { index, measurable ->
            if (!measurable.matchesParentSize) {
                val placeable = measurable.measure(contentConstraints)
                placeables[index] = placeable
                boxWidth = max(boxWidth, placeable.width)
                boxHeight = max(boxHeight, placeable.height)
            } else {
                hasMatchParentSizeChildren = true
            }
        }

        // Now measure match parent size children, if any.
        if (hasMatchParentSizeChildren) {
            // The infinity check is needed for default intrinsic measurements.
            val matchParentSizeConstraints =
                Constraints(
                    minWidth = if (boxWidth != Constraints.Infinity) boxWidth else 0,
                    minHeight = if (boxHeight != Constraints.Infinity) boxHeight else 0,
                    maxWidth = boxWidth,
                    maxHeight = boxHeight,
                )
            measurables.fastForEachIndexed { index, measurable ->
                if (measurable.matchesParentSize) {
                    placeables[index] = measurable.measure(matchParentSizeConstraints)
                }
            }
        }

        // Specify the size of the Box and position its children.
        return layout(boxWidth, boxHeight) {
            placeables.forEachIndexed { index, placeable ->
                placeable as Placeable
                val measurable = measurables[index]
                placeInBox(
                    placeable,
                    measurable,
                    layoutDirection,
                    boxWidth,
                    boxHeight,
                    contentAlignment,
                    overlayState.anchorBounds!!,
                )
            }
        }
    }
}

internal sealed interface OverlayChildData {
    object MatchParentSize : OverlayChildData

    data class Align(val alignment: Alignment) : OverlayChildData

    data class AlignInAnchor(val alignment: Alignment) : OverlayChildData

    data class AlignByAnchor(val alignment: Alignment) : OverlayChildData

    data class AlignByAnchorHorizontally(
        val anchor: Alignment.Horizontal,
        val side: Alignment.Vertical,
    ) : OverlayChildData

    data class AlignByAnchorVertically(
        val anchor: Alignment.Vertical,
        val side: Alignment.Horizontal,
    ) : OverlayChildData
}

private val Measurable.matchesParentSize: Boolean
    get() = parentData is OverlayChildData.MatchParentSize

private val Measurable.boxChildData: OverlayChildData?
    get() = parentData as? OverlayChildData

private fun Placeable.PlacementScope.placeInBox(
    placeable: Placeable,
    measurable: Measurable,
    layoutDirection: LayoutDirection,
    boxWidth: Int,
    boxHeight: Int,
    alignment: Alignment,
    anchorBounds: IntRect,
) {
    when (val childBoxData = measurable.boxChildData) {
        is OverlayChildData.Align ->
            placeWithAlignment(
                childBoxData.alignment,
                placeable,
                boxWidth,
                boxHeight,
                layoutDirection,
            )
        is OverlayChildData.MatchParentSize ->
            placeWithAlignment(Alignment.Center, placeable, boxWidth, boxHeight, layoutDirection)
        null -> placeWithAlignment(alignment, placeable, boxWidth, boxHeight, layoutDirection)
        is OverlayChildData.AlignInAnchor -> {
            val anchorOffset = anchorBounds.topLeft
            val anchorSize = anchorBounds.size
            val offset =
                alignment.align(
                    IntSize(placeable.width, placeable.height),
                    anchorSize,
                    layoutDirection,
                )
            placeable.place(offset.x + anchorOffset.x, offset.y + anchorOffset.y)
        }
        is OverlayChildData.AlignByAnchor -> {
            val contentSize = IntSize(placeable.width, placeable.height)
            val anchorOffset = anchorBounds.topLeft
            val anchorSize = anchorBounds.size
            val spaceSize =
                IntSize(
                    anchorSize.width + 2 * contentSize.width,
                    anchorSize.height + 2 * contentSize.height,
                )
            val offset = alignment.align(contentSize, spaceSize, layoutDirection)
            placeable.place(
                offset.x + anchorOffset.x - contentSize.width,
                offset.y + anchorOffset.y - contentSize.height,
            )
        }
        is OverlayChildData.AlignByAnchorHorizontally -> {
            val contentSize = IntSize(placeable.width, placeable.height)
            val anchorOffset = anchorBounds.topLeft
            val anchorSize = anchorBounds.size
            val space = anchorSize.width + 2 * contentSize.width

            val xOffset = childBoxData.anchor.align(contentSize.width, space, layoutDirection)
            val yOffset = childBoxData.side.align(contentSize.height, anchorSize.height)
            placeable.place(xOffset + anchorOffset.x - contentSize.width, yOffset + anchorOffset.y)
        }
        is OverlayChildData.AlignByAnchorVertically -> {
            val contentSize = IntSize(placeable.width, placeable.height)
            val anchorOffset = anchorBounds.topLeft
            val anchorSize = anchorBounds.size
            val space = anchorSize.height + 2 * contentSize.height

            val yOffset = childBoxData.anchor.align(contentSize.height, space)
            val xOffset =
                childBoxData.side.align(contentSize.width, anchorSize.width, layoutDirection)
            placeable.place(xOffset + anchorOffset.x, yOffset + anchorOffset.y - contentSize.height)
        }
    }
}

private fun Placeable.PlacementScope.placeWithAlignment(
    childAlignment: Alignment,
    placeable: Placeable,
    boxWidth: Int,
    boxHeight: Int,
    layoutDirection: LayoutDirection,
) {
    val position =
        childAlignment.align(
            IntSize(placeable.width, placeable.height),
            IntSize(boxWidth, boxHeight),
            layoutDirection,
        )
    placeable.place(position)
}
