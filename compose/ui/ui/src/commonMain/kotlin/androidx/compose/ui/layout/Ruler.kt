/*
 * Copyright 2022 The Android Open Source Project
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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Placeable.PlacementScope

/**
 * A line that can be used to align layout children inside a [Placeable.PlacementScope].
 *
 * @see Placeable.PlacementScope.current
 * @see MeasureScope.layout
 * @see RulerScope.provides
 * @see RulerScope.providesRelative
 */
sealed class Ruler {
    /**
     * Returns the coordinate for the [Ruler], defined with the [coordinate] value at
     * [sourceCoordinates] and read at [targetCoordinates].
     */
    internal abstract fun calculateCoordinate(
        coordinate: Float,
        sourceCoordinates: LayoutCoordinates,
        targetCoordinates: LayoutCoordinates
    ): Float
}

/**
 * A vertical [Ruler]. Defines a line that can be used by parent layouts to align or position their
 * children horizontally. The position of the ruler can be retrieved with
 * [Placeable.PlacementScope.current] and can be set with [MeasureScope.layout] using
 * [RulerScope.provides] or [RulerScope.providesRelative].
 */
open class VerticalRuler() : Ruler() {
    override fun calculateCoordinate(
        coordinate: Float,
        sourceCoordinates: LayoutCoordinates,
        targetCoordinates: LayoutCoordinates
    ): Float {
        val offset = Offset(coordinate, sourceCoordinates.size.height / 2f)
        return targetCoordinates.localPositionOf(sourceCoordinates, offset).x
    }
}

/**
 * A horizontal [Ruler]. Defines a line that can be used by parent layouts to align or position
 * their children vertically. The position of the ruler can be retrieved with
 * [Placeable.PlacementScope.current] and can be set with [MeasureScope.layout] using
 * [RulerScope.provides].
 */
open class HorizontalRuler : Ruler() {
    override fun calculateCoordinate(
        coordinate: Float,
        sourceCoordinates: LayoutCoordinates,
        targetCoordinates: LayoutCoordinates
    ): Float {
        val offset = Offset(sourceCoordinates.size.width / 2f, coordinate)
        return targetCoordinates.localPositionOf(sourceCoordinates, offset).y
    }
}

/**
 * A class that allows deriving a [Ruler]'s value from other information.
 *
 * @sample androidx.compose.ui.samples.DerivedRulerUsage
 */
interface DerivedRuler {
    /**
     * Calculates the [Ruler]'s value if it is available or returns [defaultValue] if the value has
     * not been defined.
     */
    fun PlacementScope.calculate(defaultValue: Float): Float
}

/**
 * Merges several [VerticalRuler]s from [rulers] into one ruler value. It will choose the greater of
 * the values if [shouldUseGreater] is `true` or the smaller value if it is `false`.
 */
class MergedVerticalRuler(
    private val shouldUseGreater: Boolean,
    private vararg val rulers: VerticalRuler
) : VerticalRuler(), DerivedRuler {
    override fun PlacementScope.calculate(defaultValue: Float): Float =
        mergeRulerValues(shouldUseGreater, rulers, defaultValue)
}

/**
 * Merges several [HorizontalRuler]s from [rulers] into one ruler value. It will choose the greater
 * of the values if [shouldUseGreater] is `true` or the smaller value if it is `false`.
 */
class MergedHorizontalRuler(
    private val shouldUseGreater: Boolean,
    private vararg val rulers: HorizontalRuler
) : HorizontalRuler(), DerivedRuler {
    override fun PlacementScope.calculate(defaultValue: Float): Float =
        mergeRulerValues(shouldUseGreater, rulers, defaultValue)
}

private fun <R : Ruler> PlacementScope.mergeRulerValues(
    useGreater: Boolean,
    rulers: Array<out R>,
    defaultValue: Float
): Float {
    var value = Float.NaN
    rulers.forEach {
        val nextValue = it.current(Float.NaN)
        if (value.isNaN() || (useGreater == (nextValue > value))) {
            value = nextValue
        }
    }
    return if (value.isNaN()) defaultValue else value
}
