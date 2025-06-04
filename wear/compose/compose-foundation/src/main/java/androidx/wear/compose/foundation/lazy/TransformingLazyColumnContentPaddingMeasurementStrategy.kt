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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastSumBy
import androidx.wear.compose.foundation.lazy.layout.LazyLayoutItemAnimator
import androidx.wear.compose.foundation.lazy.layout.LazyLayoutKeyIndexMap
import androidx.wear.compose.foundation.lazy.layout.hasAnimations
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

private val DEBUG_TLC_LAYOUT = false

internal class TransformingLazyColumnContentPaddingMeasurementStrategy(
    contentPadding: PaddingValues,
    density: Density,
    layoutDirection: LayoutDirection,
    private val graphicsContext: GraphicsContext,
    private val itemAnimator: LazyLayoutItemAnimator<TransformingLazyColumnMeasuredItem>,
) : TransformingLazyColumnMeasurementStrategy {
    override val rightContentPadding: Int =
        with(density) { contentPadding.calculateRightPadding(layoutDirection).roundToPx() }

    override val leftContentPadding: Int =
        with(density) { contentPadding.calculateLeftPadding(layoutDirection).roundToPx() }

    class MeasurementScope(
        var visibleItems: ArrayDeque<TransformingLazyColumnMeasuredItem>,
        var itemSpacing: Int,
        var beforeContentPadding: Int,
        var afterContentPadding: Int,
        var itemsCount: Int,
        var maxHeight: Int,
    ) {
        val overscrolledBackwards: Boolean
            get() = with(visibleItems.first()) { index == 0 && offset > beforeContentPadding }

        val overscrolledForward: Boolean
            get() =
                with(visibleItems.last()) {
                    index == itemsCount - 1 &&
                        offset + transformedHeight <
                            containerConstraints.maxHeight - afterContentPadding
                }

        fun addVisibleItemsBefore(measuredItemProvider: MeasuredItemProvider) =
            with(visibleItems) {
                val minOffset = 0
                val minIndex = 0
                val item = first()
                var topOffset = item.offset - itemSpacing
                var topPassIndex = item.index - 1

                while (topOffset >= minOffset && topPassIndex >= minIndex) {
                    val additionalItem =
                        measuredItemProvider.upwardMeasuredItem(
                            topPassIndex,
                            topOffset,
                            maxHeight = maxHeight,
                        )
                    addFirst(additionalItem)
                    topOffset -= additionalItem.transformedHeight + itemSpacing
                    topPassIndex -= 1 // Indexes must be incremental.
                }
            }

        fun addVisibleItemsAfter(measuredItemProvider: MeasuredItemProvider) =
            with(visibleItems) {
                val maxOffset: Int = maxHeight
                val maxIndex: Int = itemsCount - 1
                val item = last()
                var bottomOffset = item.offset + item.transformedHeight + itemSpacing
                var bottomPassIndex = item.index + 1

                while (bottomOffset < maxOffset && bottomPassIndex <= maxIndex) {
                    val additionalItem =
                        measuredItemProvider.downwardMeasuredItem(
                            bottomPassIndex,
                            bottomOffset,
                            maxHeight = maxHeight,
                        )
                    bottomOffset += additionalItem.transformedHeight + itemSpacing
                    add(additionalItem)
                    bottomPassIndex += 1 // Indexes must be incremental.
                }
            }

        fun correctLayout(anchorItem: TransformingLazyColumnMeasuredItem) =
            with(visibleItems) {
                // Correct items below the new anchor item.
                var itemIndex = anchorItem.index - first().index + 1
                var previousOffset =
                    anchorItem.let { it.offset + it.transformedHeight } + itemSpacing
                while (itemIndex < count()) {
                    this[itemIndex].moveBelow(previousOffset)
                    previousOffset =
                        this[itemIndex].let { it.offset + it.transformedHeight } + itemSpacing
                    itemIndex += 1
                }

                // Correct items above the new anchor item.
                itemIndex = anchorItem.index - first().index - 1
                previousOffset = anchorItem.offset - itemSpacing
                while (itemIndex >= 0) {
                    this[itemIndex].moveAbove(previousOffset)
                    previousOffset = this[itemIndex].offset - itemSpacing
                    itemIndex -= 1
                }
            }

        fun anchorItem(): TransformingLazyColumnMeasuredItem? =
            with(visibleItems) {
                if (isEmpty()) return null
                val maxHeight = maxHeight
                fastForEach {
                    // Item covers the center of the container.
                    if (
                        it.offset < maxHeight / 2 &&
                            it.offset + it.transformedHeight > maxHeight / 2
                    )
                        return it
                }

                return minBy { abs(it.offset + it.transformedHeight / 2 - maxHeight / 2) }
            }

        fun restoreLayoutTopToBottom() =
            with(visibleItems) {
                if (isEmpty()) {
                    return
                }
                var delta = first().offset - beforeContentPadding
                var repetitions = 0
                while (abs(delta) > 1 && repetitions < 3) {
                    val anchorItem = anchorItem() ?: return
                    anchorItem.offset -= delta
                    correctLayout(anchorItem)
                    delta = first().offset - beforeContentPadding
                    repetitions += 1
                }
            }

        fun restoreLayoutBottomToTop() =
            with(visibleItems) {
                if (isEmpty()) {
                    return
                }
                repeat(2) {
                    val anchorItem = anchorItem() ?: return
                    anchorItem.offset -=
                        last().offset + last().transformedHeight - maxHeight + afterContentPadding
                    correctLayout(anchorItem)
                }
            }
    }

    private var measurementScope = MeasurementScope(ArrayDeque(), 0, 0, 0, 0, 0)

    override fun measure(
        itemsCount: Int,
        measuredItemProvider: MeasuredItemProvider,
        keyIndexMap: LazyLayoutKeyIndexMap,
        itemSpacing: Int,
        containerConstraints: Constraints,
        anchorItemIndex: Int,
        anchorItemScrollOffset: Int,
        lastMeasuredAnchorItemHeight: Int,
        coroutineScope: CoroutineScope,
        density: Density,
        scrollToBeConsumed: Float,
        layout: (Int, Int, Placeable.PlacementScope.() -> Unit) -> MeasureResult,
    ): TransformingLazyColumnMeasureResult {

        if (itemsCount == 0) {
            return emptyMeasureResult(
                containerConstraints = containerConstraints,
                beforeContentPadding = beforeContentPadding,
                afterContentPadding = afterContentPadding,
                layout = layout,
            )
        }

        // Restore the position of anchor item from the previous measurement.
        val previousAnchorItem =
            if (lastMeasuredAnchorItemHeight > 0) {
                measuredItemProvider.downwardMeasuredItem(
                    anchorItemIndex,
                    anchorItemScrollOffset - lastMeasuredAnchorItemHeight / 2 +
                        containerConstraints.maxHeight / 2,
                    maxHeight = containerConstraints.maxHeight,
                )
            } else {
                measuredItemProvider
                    .upwardMeasuredItem(
                        anchorItemIndex,
                        anchorItemScrollOffset + containerConstraints.maxHeight / 2,
                        maxHeight = containerConstraints.maxHeight,
                    )
                    .also { it.offset += it.transformedHeight / 2 }
            }

        var canScrollForward = true
        var canScrollBackward = true
        var anchorItem: TransformingLazyColumnMeasuredItem
        var actuallyVisibleItems: List<TransformingLazyColumnMeasuredItem>
        // Operate on assumption that we either scroll or animate.
        val shouldAnimate = abs(scrollToBeConsumed) < 0.5f

        with(measurementScope) {
            this.itemsCount = itemsCount
            this.itemSpacing = itemSpacing
            this.maxHeight = containerConstraints.maxHeight
            this.beforeContentPadding =
                this@TransformingLazyColumnContentPaddingMeasurementStrategy.beforeContentPadding
            this.afterContentPadding =
                this@TransformingLazyColumnContentPaddingMeasurementStrategy.afterContentPadding
            this.visibleItems.clear()

            fun TransformingLazyColumnMeasuredItem.isVisible(): Boolean =
                offset + transformedHeight > 0 && offset < containerConstraints.maxHeight

            visibleItems.add(previousAnchorItem)

            // Move previous anchor item to the new position.
            // This is done to make sure we only apply scroll to the items that are not scaled and
            // therefore it visually looks like content is following user's finger as it gets
            // scrolled.
            previousAnchorItem.offset += scrollToBeConsumed.roundToInt()

            // Add the rest of the items.
            addVisibleItemsAfter(measuredItemProvider)
            addVisibleItemsBefore(measuredItemProvider)

            val totalHeight =
                visibleItems.fastSumBy { it.transformedHeight } +
                    itemSpacing * (itemsCount - 1) +
                    beforeContentPadding +
                    afterContentPadding

            // List is shorter than container.
            if (
                totalHeight < containerConstraints.maxHeight &&
                    visibleItems.first().index == 0 &&
                    visibleItems.last().index == itemsCount - 1
            ) {
                // Pinning top item to the top most position.
                restoreLayoutTopToBottom()
                canScrollBackward = false
                canScrollForward = false
            } else if (overscrolledBackwards) { // Top item moved where it is not supposed to be.
                // Pinning top item to the top most position.
                restoreLayoutTopToBottom()
                addVisibleItemsAfter(measuredItemProvider)
                canScrollBackward = false
            } else if (overscrolledForward) { // Bottom item moved where it is not supposed to be.
                // Pinning top item to the bottom most position.
                restoreLayoutBottomToTop()
                addVisibleItemsBefore(measuredItemProvider)
                canScrollForward = false
            }

            // Calculate new anchor item.
            anchorItem =
                anchorItem()
                    ?: return emptyMeasureResult(
                        containerConstraints = containerConstraints,
                        beforeContentPadding = beforeContentPadding,
                        afterContentPadding = afterContentPadding,
                        layout = layout,
                    )

            if (anchorItem.index != anchorItemIndex) {
                // Anchor item was updated.
                correctLayout(anchorItem)

                // Most probably previous anchor item is smaller now, might need to add items before
                // or
                // after.
                addVisibleItemsAfter(measuredItemProvider)
                addVisibleItemsBefore(measuredItemProvider)

                if (overscrolledBackwards) {
                    restoreLayoutTopToBottom()
                    canScrollBackward = false
                }

                if (overscrolledForward) {
                    restoreLayoutBottomToTop()
                    canScrollForward = false
                }
            }
            actuallyVisibleItems =
                visibleItems.fastFilter { it.isVisible() || (shouldAnimate && it.hasAnimations()) }
        }

        itemAnimator.onMeasured(
            shouldAnimate = shouldAnimate,
            positionedItems = actuallyVisibleItems,
            keyIndexMap = keyIndexMap,
            layoutMinOffset = 0,
            layoutMaxOffset = containerConstraints.maxHeight,
            coroutineScope = coroutineScope,
            graphicsContext = graphicsContext,
        )

        val childConstraints =
            Constraints(
                maxHeight = Constraints.Infinity,
                maxWidth = containerConstraints.maxWidth - leftContentPadding - rightContentPadding,
            )

        actuallyVisibleItems.fastForEach { it.markMeasured() }

        return TransformingLazyColumnMeasureResult(
                anchorItemIndex = anchorItem.index,
                anchorItemScrollOffset =
                    anchorItem.let {
                        it.offset + it.transformedHeight / 2 - containerConstraints.maxHeight / 2
                    },
                visibleItems = actuallyVisibleItems,
                totalItemsCount = itemsCount,
                lastMeasuredItemHeight = anchorItem.transformedHeight,
                canScrollForward = canScrollForward,
                canScrollBackward = canScrollBackward,
                coroutineScope = coroutineScope,
                density = density,
                itemSpacing = itemSpacing,
                beforeContentPadding = beforeContentPadding,
                afterContentPadding = afterContentPadding,
                childConstraints = childConstraints,
                measureResult =
                    layout(containerConstraints.maxWidth, containerConstraints.maxHeight) {
                        actuallyVisibleItems.fastForEach { it.place(this) }
                    },
            )
            .also {
                if (DEBUG_TLC_LAYOUT) {
                    it.checkLayoutIsCorrect()
                }
            }
    }

    private val beforeContentPadding: Int =
        with(density) { contentPadding.calculateTopPadding().roundToPx() }

    private val afterContentPadding: Int =
        with(density) { contentPadding.calculateBottomPadding().roundToPx() }
}
