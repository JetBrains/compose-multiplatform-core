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

package androidx.compose.ui.layout

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * The interface through which composable content can be queried for its size preferences, such as
 * its intrinsic size.
 *
 * The methods of this interface may only be called from the main UI thread.
 */
@ExperimentalComposeUiApi
interface MeasurableRootContent : IntrinsicMeasurable {
    /**
     * The density of the root content.
     */
    val density: Density

    /**
     * Measures the content with the given constraints and calls [block] on the resulting
     * [Measured].
     *
     * Returns the result of [block].
     *
     * It is recommended to not hold onto the [Measured] instance beyond the lifetime of the call
     * to [block].
     */
    fun <T> measuringIn(constraints: Constraints, block: (Measured) -> T): T
}

/**
 * Returns the measured size of the content in infinite constraints.
 *
 * Note that certain layouts, such as `scrollable`, cannot be measured in infinite constraints.
 */
@ExperimentalComposeUiApi
fun MeasurableRootContent.unconstrainedSize(): DpSize {
    return measuringIn(Constraints()) {
        with(density) {
            DpSize(it.measuredWidth.toDp(), it.measuredHeight.toDp())
        }
    }
}

/**
 * Computes the intrinsic size of the content, given a fixed size on one dimension.
 */
internal fun MeasurableRootContent.intrinsicDimensionSize(
    isWidth: Boolean,
    intrinsicSizeKind: IntrinsicSizeKind,
    otherDimensionSize: Dp,
): DpSize {
    val otherDimensionSizePx = with(density) { otherDimensionSize.roundToPx() }

    val width: Int
    val height: Int
    if (isWidth) {
        height = otherDimensionSizePx
        width = intrinsicSizeKind.widthOf(this, height)
    } else {
        width = otherDimensionSizePx
        height = intrinsicSizeKind.heightOf(this, width)
    }

    return with(density) {
        DpSize(width.toDp(), height.toDp())
    }
}

/**
 * The returned width is the minimum intrinsic width at the given [height].
 * The returned height is [height].
 *
 * @see [IntrinsicMeasurable.minIntrinsicWidth]
 */
@ExperimentalComposeUiApi
fun MeasurableRootContent.minIntrinsicWidthSize(height: Dp) =
    intrinsicDimensionSize(
        isWidth = true,
        intrinsicSizeKind = IntrinsicSizeKind.Min,
        otherDimensionSize = height,
    )

/**
 * The returned width is the maximum intrinsic width at the given [height].
 * The returned height is [height].
 *
 * @see [IntrinsicMeasurable.maxIntrinsicWidth]
 */
@ExperimentalComposeUiApi
fun MeasurableRootContent.maxIntrinsicWidthSize(height: Dp) =
    intrinsicDimensionSize(
        isWidth = true,
        intrinsicSizeKind = IntrinsicSizeKind.Max,
        otherDimensionSize = height,
    )

/**
 * The returned height is the minimum intrinsic height at the given [width].
 * The returned width is [width].
 *
 * @see [IntrinsicMeasurable.minIntrinsicHeight]
 */
@ExperimentalComposeUiApi
fun MeasurableRootContent.minIntrinsicHeightSize(width: Dp) =
    intrinsicDimensionSize(
        isWidth = false,
        intrinsicSizeKind = IntrinsicSizeKind.Min,
        otherDimensionSize = width,
    )

/**
 * The returned width is the maximum intrinsic height at the given [width].
 * The returned width is [width].
 *
 * @see [IntrinsicMeasurable.maxIntrinsicHeight]
 */
@ExperimentalComposeUiApi
fun MeasurableRootContent.maxIntrinsicHeightSize(width: Dp) =
    intrinsicDimensionSize(
        isWidth = false,
        intrinsicSizeKind = IntrinsicSizeKind.Max,
        otherDimensionSize = width,
    )

/**
 * The two kinds of intrinsic sizes: [IntrinsicSizeKind.Min] and [IntrinsicSizeKind.Max].
 *
 * Note: we can't simply use `androidx.compose.foundation.layout.IntrinsicSize` here, because that's
 * in `foundation`, and this is in `ui`.
 */
@ExperimentalComposeUiApi
abstract class IntrinsicSizeKind internal constructor() {

    /**
     * Returns the intrinsic width (min or max) of the given [measurable] at the given [height].
     */
    abstract fun widthOf(measurable: IntrinsicMeasurable, height: Int): Int

    /**
     * Returns the intrinsic height (min or max) of the given [measurable] at the given [width].
     */
    abstract fun heightOf(measurable: IntrinsicMeasurable, width: Int): Int

    /**
     * Measures minimum intrinsic size.
     */
    @ExperimentalComposeUiApi
    data object Min: IntrinsicSizeKind() {
        override fun widthOf(measurable: IntrinsicMeasurable, height: Int): Int {
            return measurable.minIntrinsicWidth(height)
        }

        override fun heightOf(measurable: IntrinsicMeasurable, width: Int): Int {
            return measurable.minIntrinsicHeight(width)
        }
    }

    /**
     * Measures maximum intrinsic size.
     */
    @ExperimentalComposeUiApi
    data object Max: IntrinsicSizeKind() {
        override fun widthOf(measurable: IntrinsicMeasurable, height: Int): Int {
            return measurable.maxIntrinsicWidth(height)
        }

        override fun heightOf(measurable: IntrinsicMeasurable, width: Int): Int {
            return measurable.maxIntrinsicHeight(width)
        }
    }
}