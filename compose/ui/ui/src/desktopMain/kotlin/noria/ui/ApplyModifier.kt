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

package noria.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastForEachIndexed
import kotlin.math.max
import noria.NoriaContext

@Composable
fun NoriaContext.withModifier(modifier: Modifier, content: @Composable NoriaContext.() -> Unit) {
    Layout(
        content = { content() },
        measurePolicy = WrapperMeasurePolicy,
        modifier = modifier
    )
}

/**
 * A simplified version of BoxMeasurePolicy
 */
private object WrapperMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        if (measurables.isEmpty()) {
            return layout(constraints.minWidth, constraints.minHeight) {}
        }

        if (measurables.size == 1) {
            val placeable = measurables.single().measure(constraints)
            val boxWidth = max(constraints.minWidth, placeable.width)
            val boxHeight = max(constraints.minHeight, placeable.height)

            return layout(boxWidth, boxHeight) {
                placeInWrapper(placeable, layoutDirection, boxWidth, boxHeight)
            }
        }

        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var boxWidth = constraints.minWidth
        var boxHeight = constraints.minHeight
        measurables.fastForEachIndexed { index, measurable ->
            val placeable = measurable.measure(constraints)
            placeables[index] = placeable
            boxWidth = max(boxWidth, placeable.width)
            boxHeight = max(boxHeight, placeable.height)
        }

        // Specify the size of the Box and position its children.
        return layout(boxWidth, boxHeight) {
            placeables.forEach { placeable ->
                placeable as Placeable
                placeInWrapper(placeable, layoutDirection, boxWidth, boxHeight)
            }
        }
    }
}

private fun Placeable.PlacementScope.placeInWrapper(
    placeable: Placeable,
    layoutDirection: LayoutDirection,
    boxWidth: Int,
    boxHeight: Int,
) {
    val position =
        Alignment.TopStart.align(
            IntSize(placeable.width, placeable.height),
            IntSize(boxWidth, boxHeight),
            layoutDirection
        )
    placeable.place(position)
}
