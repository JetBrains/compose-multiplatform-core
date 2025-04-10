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

package androidx.wear.compose.foundation.lazy

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScrollProgress.Companion.bottomItemScrollProgress
import androidx.wear.compose.foundation.lazy.layout.LazyLayoutItemAnimation
import androidx.wear.compose.foundation.lazy.layout.LazyLayoutMeasuredItem

/** Represents a placeable item in the [TransformingLazyColumn] layout. */
internal data class TransformingLazyColumnMeasuredItem(
    /** The index of the item in the list. */
    override val index: Int,
    /** The [Placeable] representing the content of the item, or null if no composable is inside. */
    val placeable: Placeable?,
    /** The constraints of the container holding the item. */
    val containerConstraints: Constraints,
    /** The vertical offset of the item from the top of the list after transformations applied. */
    override var offset: Int,
    /**
     * The horizontal padding before the item. This doesn't affect vertical calculations, but needs
     * to be added to during final placement.
     */
    val leftPadding: Int,
    /**
     * The horizontal padding after the item. This doesn't affect vertical calculations, but needs
     * to be added to during final placement.
     */
    val rightPadding: Int,

    /** The scroll progress computed a the end of the measure pass. */
    var measureScrollProgress: TransformingLazyColumnItemScrollProgress,

    /** The horizontal alignment to apply during placement. */
    val horizontalAlignment: Alignment.Horizontal,
    /** The [LayoutDirection] of the `Layout`. */
    private val layoutDirection: LayoutDirection,
    val animation: LazyLayoutItemAnimation? = null,
    override val key: Any,
    override val contentType: Any?,
    /**
     * Whether the item is currently being measured. Must be set to false before returning as a
     * measured result.
     */
    var isInMeasure: Boolean = true,
) : TransformingLazyColumnVisibleItemInfo, LazyLayoutMeasuredItem {
    // This is the value of the ScrollProgress, either the one set at the end of the measure pass
    // if there are no animations configured or the one computed by the animation, updated each
    // frame.
    override val scrollProgress
        get() =
            animation?.animatedScrollProgress.let {
                if (it == TransformingLazyColumnItemScrollProgress.Unspecified)
                    measureScrollProgress
                else it
            } ?: measureScrollProgress

    override val isVertical: Boolean = true
    override val mainAxisSizeWithSpacings: Int
        // TODO: needs to add the spacing between items?
        get() = transformedHeight

    override val placeablesCount = 1
    override var nonScrollableItem: Boolean = false
    override val constraints = containerConstraints

    override fun getOffset(index: Int): IntOffset = IntOffset(leftPadding, offset)

    override fun getParentData(index: Int): Any? =
        placeable?.parentData?.let {
            if (it is TransformingLazyColumnParentData) {
                it.animationSpecs
            } else {
                it
            }
        }

    private var lastMeasuredTransformedHeight = placeable?.height ?: 0

    /** The height of the item after transformations applied. */
    override val transformedHeight: Int
        get() {
            if (isInMeasure) {
                lastMeasuredTransformedHeight =
                    placeable?.let { p ->
                        (p.parentData as? TransformingLazyColumnParentData)?.let {
                            it.heightProvider?.invoke(p.height, measureScrollProgress)
                        } ?: p.height
                    } ?: 0
            }
            return lastMeasuredTransformedHeight
        }

    override val measuredHeight = placeable?.height ?: 0

    fun place(scope: Placeable.PlacementScope) =
        with(scope) {
            placeable?.let { placeable ->
                var intOffset =
                    IntOffset(
                        x =
                            leftPadding +
                                horizontalAlignment.align(
                                    space =
                                        containerConstraints.maxWidth - rightPadding - leftPadding,
                                    size = placeable.width,
                                    layoutDirection = layoutDirection
                                ),
                        y = offset
                    )
                if (animation == null) {
                    placeable.placeWithLayer(intOffset)
                } else {
                    intOffset += animation.placementDelta
                    animation.layer?.let { placeable.placeWithLayer(intOffset, it) }
                        ?: placeable.placeWithLayer(intOffset)
                    animation.finalOffset = intOffset
                }
            }
        }

    fun pinToCenter() {
        measureScrollProgress =
            bottomItemScrollProgress(
                containerConstraints.maxHeight / 2 - measuredHeight / 2,
                measuredHeight,
                containerConstraints.maxHeight
            )
        offset = containerConstraints.maxHeight / 2 - transformedHeight / 2
    }
}
