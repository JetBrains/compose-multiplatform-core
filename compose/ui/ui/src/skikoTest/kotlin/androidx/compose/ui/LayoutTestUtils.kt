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

package androidx.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

// A copy from androidInstrumentedTest/kotlin/androidx/compose/ui/AndroidLayoutDrawTest.kt

@Composable
fun AtLeastSize(size: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Layout(
        measurePolicy = { measurables, constraints ->
            val newConstraints =
                Constraints(
                    minWidth = max(size, constraints.minWidth),
                    maxWidth =
                        if (constraints.hasBoundedWidth) {
                            max(size, constraints.maxWidth)
                        } else {
                            Constraints.Infinity
                        },
                    minHeight = max(size, constraints.minHeight),
                    maxHeight =
                        if (constraints.hasBoundedHeight) {
                            max(size, constraints.maxHeight)
                        } else {
                            Constraints.Infinity
                        },
                )
            val placeables = measurables.map { m -> m.measure(newConstraints) }
            var maxWidth = size
            var maxHeight = size
            placeables.forEach { child ->
                maxHeight = max(child.height, maxHeight)
                maxWidth = max(child.width, maxWidth)
            }
            layout(maxWidth, maxHeight) { placeables.forEach { child -> child.place(0, 0) } }
        },
        modifier = modifier,
        content = content,
    )
}

@Composable
fun FixedSize(size: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Layout(content = content, modifier = modifier) { measurables, _ ->
        val newConstraints = Constraints.fixed(size, size)
        val placeables = measurables.map { m -> m.measure(newConstraints) }
        layout(size, size) { placeables.forEach { child -> child.placeRelative(0, 0) } }
    }
}

@Composable
fun Align(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val newConstraints =
                Constraints(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight,
                )
            val placeables = measurables.map { m -> m.measure(newConstraints) }
            var maxWidth = constraints.minWidth
            var maxHeight = constraints.minHeight
            placeables.forEach { child ->
                maxHeight = max(child.height, maxHeight)
                maxWidth = max(child.width, maxWidth)
            }
            layout(maxWidth, maxHeight) {
                placeables.forEach { child -> child.placeRelative(0, 0) }
            }
        },
        content = content,
    )
}

@Composable
internal fun Padding(size: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val totalDiff = size * 2
            val targetMinWidth = constraints.minWidth - totalDiff
            val targetMaxWidth =
                if (constraints.hasBoundedWidth) {
                    constraints.maxWidth - totalDiff
                } else {
                    Constraints.Infinity
                }
            val targetMinHeight = constraints.minHeight - totalDiff
            val targetMaxHeight =
                if (constraints.hasBoundedHeight) {
                    constraints.maxHeight - totalDiff
                } else {
                    Constraints.Infinity
                }
            val newConstraints =
                Constraints(
                    minWidth = targetMinWidth.coerceAtLeast(0),
                    maxWidth = targetMaxWidth.coerceAtLeast(0),
                    minHeight = targetMinHeight.coerceAtLeast(0),
                    maxHeight = targetMaxHeight.coerceAtLeast(0),
                )
            val placeables = measurables.map { m -> m.measure(newConstraints) }
            var maxWidth = size
            var maxHeight = size
            placeables.forEach { child ->
                maxHeight = max(child.height + totalDiff, maxHeight)
                maxWidth = max(child.width + totalDiff, maxWidth)
            }
            layout(maxWidth, maxHeight) {
                placeables.forEach { child -> child.placeRelative(size, size) }
            }
        },
        content = content,
    )
}

@Composable
fun Wrap(
    modifier: Modifier = Modifier,
    minWidth: Int = 0,
    minHeight: Int = 0,
    content: @Composable () -> Unit = {},
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = max(placeables.maxByOrNull { it.width }?.width ?: 0, minWidth)
        val height = max(placeables.maxByOrNull { it.height }?.height ?: 0, minHeight)
        layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
    }
}

@Composable
fun Scroller(
    modifier: Modifier = Modifier,
    onScrollPositionChanged: (position: Int, maxPosition: Int) -> Unit,
    offset: State<Int>,
    content: @Composable () -> Unit,
) {
    val maxPosition = remember { mutableStateOf(Constraints.Infinity) }
    ScrollerLayout(
        modifier = modifier,
        maxPosition = maxPosition.value,
        onMaxPositionChanged = {
            maxPosition.value = 0
            onScrollPositionChanged(offset.value, 0)
        },
        content = content,
    )
}

@Composable
private fun ScrollerLayout(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") maxPosition: Int,
    onMaxPositionChanged: () -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val childConstraints =
            constraints.copy(maxHeight = constraints.maxHeight, maxWidth = Constraints.Infinity)
        val childMeasurable = measurables.first()
        val placeable = childMeasurable.measure(childConstraints)
        val width = min(placeable.width, constraints.maxWidth)
        layout(width, placeable.height) {
            onMaxPositionChanged()
            placeable.placeRelative(0, 0)
        }
    }
}

@Composable
fun WrapForceRelayout(
    model: State<Int>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxByOrNull { it.width }?.width ?: 0
        val height = placeables.maxByOrNull { it.height }?.height ?: 0
        layout(width, height) {
            model.value
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

@Composable
fun SimpleRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        var width = 0
        var height = 0
        val placeables =
            measurables.map {
                it.measure(constraints.copy(maxWidth = constraints.maxWidth - width)).also {
                    width += it.width
                    height = max(height, it.height)
                }
            }
        layout(width, height) {
            var currentWidth = 0
            placeables.forEach {
                it.placeRelative(currentWidth, 0)
                currentWidth += it.width
            }
        }
    }
}

@Composable
fun JustConstraints(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {}
    }
}

fun Modifier.padding(padding: Int) = this.then(PaddingModifier(padding, padding, padding, padding))

private data class PaddingModifier(val left: Int, val top: Int, val right: Int, val bottom: Int) :
    LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable =
            measurable.measure(
                constraints.offset(horizontal = -left - right, vertical = -top - bottom)
            )
        return layout(
            constraints.constrainWidth(left + placeable.width + right),
            constraints.constrainHeight(top + placeable.height + bottom),
        ) {
            placeable.placeRelative(left, top)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int =
        measurable.minIntrinsicWidth((height - (top + bottom)).coerceAtLeast(0)) + (left + right)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int =
        measurable.maxIntrinsicWidth((height - (top + bottom)).coerceAtLeast(0)) + (left + right)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int =
        measurable.minIntrinsicHeight((width - (left + right)).coerceAtLeast(0)) + (top + bottom)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int =
        measurable.maxIntrinsicHeight((width - (left + right)).coerceAtLeast(0)) + (top + bottom)
}

internal val AlignTopLeft =
    object : LayoutModifier {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints.copyMaxDimensions())
            return layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.placeRelative(0, 0)
            }
        }
    }

@Stable
class SquareModel(
    size: Int = 10,
    outerColor: Color = Color(0xFF000080),
    innerColor: Color = Color(0xFFFFFFFF),
) {
    var size: Int by mutableStateOf(size)
    var outerColor: Color by mutableStateOf(outerColor)
    var innerColor: Color by mutableStateOf(innerColor)
}

fun Modifier.background(color: Color) = drawBehind { drawRect(color) }

fun Modifier.background(model: SquareModel, isInner: Boolean) = drawBehind {
    drawRect(if (isInner) model.innerColor else model.outerColor)
}

class LayoutAndDrawModifier(val color: Color) : LayoutModifier, DrawModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(Constraints.fixed(10, 10))
        return layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(
                (constraints.maxWidth - placeable.width) / 2,
                (constraints.maxHeight - placeable.height) / 2,
            )
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(color)
    }
}
