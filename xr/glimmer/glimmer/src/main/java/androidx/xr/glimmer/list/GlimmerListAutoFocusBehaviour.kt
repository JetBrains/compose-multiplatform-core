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

package androidx.xr.glimmer.list

import androidx.compose.ui.focus.requestFocusForChildInRootBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt

/**
 * Auto focus controls how children receive focus as the list position changes. Conceptually, it's a
 * virtual line that moves along the list — from the topmost item to the bottommost — as the user
 * scrolls. The item located under this line is gained focus.
 *
 * In long lists, the focus line stays centered within the visible area, and only begins to shift
 * when the user scrolls close to the start or end of the list.
 *
 * We need to request focus after the layout pass, but the required focus area can only be
 * determined during the measure pass. This class calculates and stores that value during measuring,
 * and later, once all children are placed, it is invoked by [GlimmerListAutoFocusNode] to perform
 * the focus request.
 */
internal class GlimmerListAutoFocusBehaviour {

    private var pendingRequestFocus = false
    private var focusLinePosition: Float = NoFocusLine
    private var listLayoutProperties: ListLayoutProperties? = null

    // TODO: b/431258694 - Support reverse scrolling.
    internal fun applyMeasureResult(
        scrollToBeConsumed: Float,
        layoutProperties: ListLayoutProperties,
        measureResult: GlimmerListMeasureResult,
    ) {
        if (listLayoutProperties?.orientation != layoutProperties.orientation) {
            focusLinePosition = NoFocusLine
        }
        focusLinePosition =
            calculateAutoFocusLinePosition(
                prevAutoFocusLine = focusLinePosition,
                scrollToBeConsumed = scrollToBeConsumed,
                layoutProperties = layoutProperties,
                measureResult = measureResult,
            )
        listLayoutProperties = layoutProperties
        pendingRequestFocus = true
    }

    internal fun onAfterLayout(node: DelegatableNode) {
        val layoutProperties = listLayoutProperties
        if (pendingRequestFocus && focusLinePosition != NoFocusLine && layoutProperties != null) {
            val coordinates = node.requireLayoutCoordinates()

            val localLeftTop = getFocusLeftTopOffset(focusLinePosition, layoutProperties)
            val rootTopLeft = coordinates.localToRoot(localLeftTop)

            val rootLeft = rootTopLeft.x.fastRoundToInt()
            val rootTop = rootTopLeft.y.fastRoundToInt()
            val rootBottom = rootTop + getFocusHeight(layoutProperties)
            val rootRight = rootLeft + getFocusWidth(layoutProperties)

            node.requestFocusForChildInRootBounds(
                left = rootLeft,
                top = rootTop,
                right = rootRight,
                bottom = rootBottom,
            )

            pendingRequestFocus = false
        }
    }
}

/**
 * Calculates the position of the focus line for the list, based on the current scroll position. The
 * list item located under this line is considered the one that should receive focus.
 *
 * The example below represents vertical oriented list:
 *
 *         Full content
 *             size
 *        _____________  <- content start
 *       |             |
 *       |  threshold  |   Area where the focus lines moves
 *       |    area     | (between view port start and center).
 *       |    (top)    |
 *       |             |
 *       |◦◦◦◦◦◦◦◦◦◦◦◦◦| <- focusShiftThreshold
 *       |             |
 *      _|_____________|_  <- list view port start
 *     | |             | |
 *     | |    focus    | |
 *     | |    line     | | In the rest of the content area, the focus line
 *     | |    moves    | | is always in the center of the list viewport.
 *     | |    within   | |
 *     | |   viewport  | |
 *     |_|_____________|_| <- list view port end
 *       |             |
 *       |             |
 *       |◦◦◦◦◦◦◦◦◦◦◦◦◦| <- (content_end - focusShiftThreshold)
 *       |             |
 *       |  threshold  |   Area where the focus lines moves
 *       |    area     | (between view port center and bottom).
 *       |  (bottom)   |
 *       |             |
 *       |_____________| <- content end
 * * `virtualTopScrollDistance = list_view_port_start - content_start`
 * * `virtualBottomScrollDistance = content_end - list_view_port_end`
 *
 * @return the Y-coordinate of the focus line for vertical lists and the X-coordinate for horizontal
 *   ones. Returns [NoFocusLine] if there should be no focus.
 */
private fun calculateAutoFocusLinePosition(
    prevAutoFocusLine: Float,
    scrollToBeConsumed: Float,
    layoutProperties: ListLayoutProperties,
    measureResult: GlimmerListMeasureResult,
): Float {
    if (measureResult.visibleItemsInfo.isEmpty()) {
        return NoFocusLine
    }
    // Defines how far the user needs to be from the edges so the focus line is centered.
    //
    // If the user has scrolled less than [focusShiftThreshold] pixels from the top of the page, the
    // focus line will be placed somewhere between the top and the center of the page, proportional
    // to how far they’ve scrolled. If the user has scrolled exactly [focusShiftThreshold] pixels,
    // the focus should be at the center.
    //
    // Alternatively, if the user is within [focusShiftThreshold] pixels from the bottom of the
    // page, the focus line will be shifted to somewhere between the center and the bottom of the
    // page, depending on how close they are to the end. When the user reaches the end of the
    // content, the focus should be at the bottom of the viewport.
    //
    // In all other cases the focus line rests in the center.
    //
    // Example with focusShiftThreshold = 200.dp, viewport = 500.dp, content = 1500.dp:
    //
    // Scroll      ->    Focus position  (viewport, %)
    // 0 dp        ->    0 dp            (0%)
    // 100 dp      ->    125 dp          (25%)
    // 200..800 dp ->    250 dp          (50% centered)
    // 900 dp      ->    375 dp          (75%)
    // 1000 dp     ->    500 dp          (100%)
    val focusShiftThreshold = layoutProperties.mainAxisAvailableSize * ProportionalThresholdFactor

    val topVirtualScroll = getVirtualTopScrollDistance(measureResult)
    val bottomVirtualScroll = getVirtualBottomScrollDistance(measureResult)

    // Calculates the anchor points within which the focus line can exist:
    // - `start` is the start position of the first visible item. This represents the minimum
    //   possible position where the focus line can appear.
    // - `end` is the bottom edge of the last visible item, or the end of the viewport if the item
    //   doesn't fit into it. This represents the maximum possible position for the focus line.
    // - `center` is the resting position for the focus line when we are in the middle of the list.
    // TODO: b/427979497 - Figure out why visibleItemsInfo.first() != firstVisibleItem.
    val firstItem = measureResult.firstVisibleItem ?: return NoFocusLine
    val lastItem = measureResult.visibleItemsInfo.last()
    // This offset helps to respect arrangements of short lists.
    val listOffset =
        measureResult.beforeContentPadding + measureResult.firstVisibleItemScrollOffset.toFloat()
    val rawStart = listOffset + firstItem.offset
    val lastItemBottom = listOffset + lastItem.offset + lastItem.size
    val rawEnd = minOf(measureResult.viewportEndOffset.toFloat(), lastItemBottom)
    val center = (rawStart + rawEnd) / 2f
    // If the focus line lies exactly on the edge of an item, they are considered non-overlapping.
    // This breaks the behavior at the very beginning and end of the list. To avoid this, we shrink
    // the area where the focus line can exist by one pixel on both sides.
    val start = rawStart + 1f
    val end = rawEnd - 1f

    val focusLine =
        if (topVirtualScroll >= focusShiftThreshold && bottomVirtualScroll >= focusShiftThreshold) {
            // List edges are too far from the current position. Keep the focus in the center.
            center
        } else {
            // The remaining space to the edge is less than the threshold — start moving the focus.
            val prevFocusLine = if (prevAutoFocusLine != NoFocusLine) prevAutoFocusLine else 0f
            // TODO: b/433239564 - Figure out a proper behaviour for the cases when a list can't
            //  be scrolled.
            val scrollMultiplier =
                when {
                    // Normally, near the edges, the focus moves faster relative to the content,
                    // because both the focus line and the content are moving. This is the expected
                    // behavior defined by the spec.
                    // However, there's a corner case when the list is short and doesn't have enough
                    // content to scroll. In that case, the content stays still, and the focus line
                    // appears to move much slower. To compensate for this visual effect, we speed
                    // up the focus so that its movement relative to the content stays consistent.
                    scrollToBeConsumed > 0f && !measureResult.canScrollBackward -> 1f
                    scrollToBeConsumed < 0f && !measureResult.canScrollForward -> 1f
                    // We select the multiplier such that scrolling [focusShiftThreshold] pixels
                    // shifts the focus line by precisely half the list viewport size.
                    else -> (end - start) / 2f / focusShiftThreshold
                }
            (prevFocusLine - scrollToBeConsumed * scrollMultiplier).fastCoerceIn(start, end)
        }

    return focusLine
}

/**
 * Return an approximate scroll distance needed to reach the top of the lazy list. See the docs for
 * [calculateAutoFocusLinePosition] for visualisation.
 */
private fun getVirtualTopScrollDistance(measureResult: GlimmerListMeasureResult): Int {
    val firstVisibleItem = measureResult.visibleItemsInfo.first()
    val nonRenderedItemSizes =
        safeMultiply(measureResult.visibleItemsAverageSize(), firstVisibleItem.index)
    return nonRenderedItemSizes - firstVisibleItem.offset
}

/**
 * Return an approximate scroll distance needed to reach the bottom of the lazy list. See the docs
 * for [calculateAutoFocusLinePosition] for visualisation.
 */
private fun getVirtualBottomScrollDistance(measureResult: GlimmerListMeasureResult): Int {
    val lastVisibleItem = measureResult.visibleItemsInfo.last()
    val nonRenderedItemsCount = measureResult.totalItemsCount - lastVisibleItem.index - 1
    val nonRenderedItemSizes =
        safeMultiply(measureResult.visibleItemsAverageSize(), nonRenderedItemsCount)
    return lastItemClippedSize(measureResult) + nonRenderedItemSizes
}

/**
 *      _________________
 *     |  _____________  |
 *     | |    first    | |
 *     | |   visible   | |
 *     | |    item     | |
 *     | |             | |
 *     | |_____________| |
 *     |  _____________  |
 *     | |    last     | |
 *     | |   visible   | |
 *     | |    item     | |
 *     |_|_____________|_|
 *       |   clipped   |
 *       |    size     |    <- This size is returned.
 *       |_____________|
 */
private fun lastItemClippedSize(measureResult: GlimmerListMeasureResult): Int {
    val lastItem = measureResult.visibleItemsInfo.last()
    val paddingAfterItem =
        if (lastItem.index != measureResult.totalItemsCount - 1) {
            measureResult.mainAxisItemSpacing
        } else {
            measureResult.afterContentPadding
        }
    val bottomLineOfLastItem = lastItem.offset + lastItem.size + paddingAfterItem
    val bottomLineOfViewPort = measureResult.viewportEndOffset
    val nonVisiblePartOfLastItem = bottomLineOfLastItem - bottomLineOfViewPort
    return nonVisiblePartOfLastItem
}

/** Prevents integer overflow. */
private fun safeMultiply(a: Int, b: Int): Int {
    if (a == 0 || b == 0) return 0

    val result = a * b
    if (result / b == a) {
        return result
    }

    return if ((a > 0) == (b > 0)) Int.MAX_VALUE else Int.MIN_VALUE
}

private fun getFocusLeftTopOffset(
    focusLine: Float,
    layoutProperties: ListLayoutProperties,
): Offset {
    return Offset(
        x = if (layoutProperties.isVertical) 0f else focusLine,
        y = if (layoutProperties.isVertical) focusLine else 0f,
    )
}

private fun getFocusWidth(layoutProperties: ListLayoutProperties): Int {
    return if (layoutProperties.isVertical) {
        layoutProperties.contentConstraints.maxWidth + layoutProperties.totalHorizontalPadding
    } else {
        0
    }
}

private fun getFocusHeight(layoutProperties: ListLayoutProperties): Int {
    return if (layoutProperties.isVertical) {
        0
    } else {
        layoutProperties.contentConstraints.maxHeight + layoutProperties.totalVerticalPadding
    }
}

/**
 * The scroll distance required to put the focus indicator in the center is calculated as a
 * proportion of the list's visible size, using [ProportionalThresholdFactor].
 *
 * For example, if the visible list height is 500dp and [ProportionalThresholdFactor] is 2.0f, the
 * focus will reach the center after scrolling 1000dp — that is, two full viewport heights.
 *
 * Note that this behaviour only applies to lists with enough content to scroll. If the list is too
 * short to scroll, the focus line moves at the same speed as the user’s finger.
 */
private const val ProportionalThresholdFactor = 0.6f

/** Placeholder for `null`. */
private const val NoFocusLine: Float = Float.MIN_VALUE
