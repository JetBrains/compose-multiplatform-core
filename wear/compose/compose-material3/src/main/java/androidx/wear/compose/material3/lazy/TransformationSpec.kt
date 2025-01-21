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

package androidx.wear.compose.material3.lazy

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.util.lerp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.inverseLerp

/**
 * This class contains all parameters needed to configure the transformations for a single item.
 * This is used with [Modifier#scrollTransform] inside items of a [TransformingLazyColumn]
 */
internal data class TransformationSpec(
    /**
     * The minimum element height, as a ratio of the viewport height, to use for determining the
     * transition area height within the range [minTransitionArea]..[maxTransitionArea]. Given a
     * content item, this defines the start and end points for transitioning the item. Item heights
     * lower than [minElementHeight] will be treated as if [minElementHeight]. Must be smaller than
     * or equal to [maxElementHeight].
     */
    val minElementHeight: Float,

    /**
     * The maximum element height, as a ratio of the viewport height, to use for determining the
     * transition area height within the range [minTransitionArea]..[maxTransitionArea]. Given a
     * content item, this defines the start and end points for transitioning the item. Item heights
     * higher than [maxElementHeight] will be treated as if [maxElementHeight]. Must be greater than
     * or equal to [minElementHeight].
     */
    val maxElementHeight: Float,

    /**
     * The lower bound of the range of heights for the transition area, i.e. how tall the transition
     * area is for items of height [minElementHeight] or shorter. Taller items will have taller
     * transition areas, up to [maxTransitionArea]. This is defined as a fraction (value between
     * 0f..1f) of the viewport height. Must be less than or equal to [maxTransitionArea].
     *
     * Note that the transition area is the same for all variables, but each variable can define a
     * transformation zone inside it in which the transformations will actually occur. See
     * [TransformVariableSpec].
     */
    val minTransitionArea: Float,

    /**
     * The upper bound of the range of heights for the transition area, i.e. how tall the transition
     * area is for items of height [maxElementHeight] or taller. Shorter items will have shorter
     * transition areas, down to [minTransitionArea]. This is defined as a fraction (value between
     * 0f..1f) of the viewport height. Must be greater than or equal to [minTransitionArea].
     *
     * Note that the transition area is the same for all variables, but each variable can define a
     * transformation zone inside it in which the transformations will actually occur. See
     * [TransformVariableSpec].
     */
    val maxTransitionArea: Float,

    /**
     * An interpolator to use to determine how to apply transformations as the item transitions
     * across the transformation zones.
     */
    val easing: Easing,

    /** Configuration for how the container (background) of the item will fade in/out. */
    val containerAlpha: TransformVariableSpec,

    /** Configuration for how the content of the item will fade in/out. */
    val contentAlpha: TransformVariableSpec,

    /** Configuration for scaling the whole item (container and content). */
    val scale: TransformVariableSpec,

    /** Configuration for the width of the container. */
    val containerWidth: TransformVariableSpec,

    // TBD
    val morphHeight: Float,
    val morphHeightDriftFactor: Float,
    val morphPivotX: Float,
    val morphPivotY: Float,
) {
    init {
        // The element height range must be non-empty.
        require(minElementHeight < maxElementHeight) {
            "minElementHeight must be smaller than maxElementHeight"
        }
    }
}

/**
 * This class represents the configuration parameters for one variable that changes as the item
 * moves on the screen and will be used to apply the corresponding transformation - for example:
 * container alpha. When an item enters from the top of the screen the value of this variable will
 * be [topValue]. As the item's bottom edge moves through the top transformation zone (inside the
 * top transition area), this variable will change from [topValue] to target value (target value is
 * the nominal value of this variable, with no transformation applied, such as 1f for alpha). When
 * the item's top edge moves through the bottom transformation zone (inside the bottom transition
 * area), this variable will change from the target value into [bottomValue], and keep that value
 * until it leaves the screen. The same process happens in reverse, entering from the bottom of the
 * screen and leaving at the top.
 */
internal data class TransformVariableSpec(
    /**
     * The value this variable will have when the item's bottom edge is above the top transformation
     * zone, usually this happens when it is (or is about to be) partially outside of the screen on
     * the top side.
     */
    val topValue: Float,

    /**
     * The value this variable will have when the item's top edge is below the bottom transformation
     * zone, usually this happens when it is (or is about to be) partially outside of the screen on
     * the bottom side.
     */
    val bottomValue: Float = topValue,

    /**
     * Defines how far into the transition area the transformation zone starts. For example, a value
     * of 0.5f means that when the item enters the screen from the top, this variable will not start
     * to transform until the bottom of the item reaches the middle point of the transition area.
     * Should be less than [transformationZoneExitFraction].
     *
     * Visually, the top transition area and top transformation zone can be visualized as:
     * ```
     *    ˅  --------------------- <-- Top of the screen
     *    |  |                   |
     *    |  |-------------------| <- Enter fraction, item moving down
     * TA |  |        TZ         | <- Transformation Zone
     *    |  |-------------------| <- Exit fraction, item moving down
     *    |  |                   |
     *    ^  |-------------------|
     * ```
     *
     * On the bottom, it is the same mirrored vertically (exit fraction is above enter fraction). It
     * is also worth noting that in the bottom, it is the top of the item that triggers starting and
     * ending transformations.
     */
    val transformationZoneEnterFraction: Float = 0f,

    /**
     * Defines how far into the transition area the transformation zone ends. For example, a value
     * of 0.5f means that when the item is moving down, its bottom edge needs to reach the middle
     * point of the transition area for this variable to reach it's maximum/target value. Should be
     * greater than [transformationZoneEnterFraction].
     *
     * See also [transformationZoneEnterFraction]
     */
    val transformationZoneExitFraction: Float = 1f,
) {
    init {
        require(transformationZoneExitFraction > transformationZoneEnterFraction) {
            "transformationZoneExitFraction must be greater than transformationZoneEnterFraction"
        }
    }
}

/**
 * Computes and remembers the appropriate [TransformationSpec] for the current screen size, given
 * one or more [TransformationSpec]s for different screen sizes.
 */
@Composable
internal fun rememberResponsiveTransformationSpec(
    specs: List<Pair<Int, TransformationSpec>>
): TransformationSpec {
    val screenSizeDp = LocalConfiguration.current.screenHeightDp
    return remember(screenSizeDp) { responsiveTransformationSpec(screenSizeDp, specs) }
}

private fun lerp(start: TransformVariableSpec, stop: TransformVariableSpec, progress: Float) =
    TransformVariableSpec(
        lerp(start.bottomValue, stop.bottomValue, progress),
        lerp(start.topValue, stop.topValue, progress),
        lerp(start.transformationZoneEnterFraction, stop.transformationZoneEnterFraction, progress),
        lerp(start.transformationZoneExitFraction, stop.transformationZoneExitFraction, progress)
    )

private fun lerp(start: TransformationSpec, stop: TransformationSpec, progress: Float) =
    TransformationSpec(
        lerp(start.minElementHeight, stop.minElementHeight, progress),
        lerp(start.maxElementHeight, stop.maxElementHeight, progress),
        lerp(start.minTransitionArea, stop.minTransitionArea, progress),
        lerp(start.maxTransitionArea, stop.maxTransitionArea, progress),
        { fraction ->
            lerp(start.easing.transform(fraction), stop.easing.transform(fraction), progress)
        },
        lerp(start.containerAlpha, stop.containerAlpha, progress),
        lerp(start.contentAlpha, stop.contentAlpha, progress),
        lerp(start.scale, stop.scale, progress),
        lerp(start.containerWidth, stop.containerWidth, progress),
        lerp(start.morphHeight, stop.morphHeight, progress),
        lerp(start.morphHeightDriftFactor, stop.morphHeightDriftFactor, progress),
        lerp(start.morphPivotX, stop.morphPivotX, progress),
        lerp(start.morphPivotY, stop.morphPivotY, progress)
    )

/**
 * Computes the appropriate [TransformationSpec] for a given screen size, given one or more
 * [TransformationSpec]s for different screen sizes.
 */
private fun responsiveTransformationSpec(
    screenSizeDp: Int,
    specs: List<Pair<Int, TransformationSpec>>
): TransformationSpec {
    val sortedSpecs = specs.sortedBy { it.first }

    var ix = 0
    while (ix < sortedSpecs.size && screenSizeDp > sortedSpecs[ix].first) ix++

    if (ix == 0) return sortedSpecs.first().second
    if (ix == sortedSpecs.size) return sortedSpecs.last().second

    return lerp(
        sortedSpecs[ix - 1].second,
        sortedSpecs[ix].second,
        inverseLerp(
            sortedSpecs[ix - 1].first.toFloat(),
            sortedSpecs[ix].first.toFloat(),
            screenSizeDp.toFloat()
        )
    )
}
