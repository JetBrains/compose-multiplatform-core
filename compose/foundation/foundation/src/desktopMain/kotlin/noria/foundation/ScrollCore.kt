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

package noria.foundation

import androidx.compose.foundation.checkScrollableContainerConstraints
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollConfig
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.fastCoerceIn
import noria.*
import kotlin.ranges.coerceAtMost

enum class ScrollDirection {
    HORIZONTAL,
    VERTICAL,
    BOTH
}

enum class ScrollHandlingPolicy {
    DEFAULT,
    ALWAYS_HORIZONTAL,
    ALWAYS_VERTICAL;

    fun isCompatibleWith(direction: ScrollDirection): Boolean = let { policy ->
        when (policy) {
            DEFAULT -> true
            ALWAYS_HORIZONTAL -> direction == ScrollDirection.HORIZONTAL
            ALWAYS_VERTICAL -> direction == ScrollDirection.VERTICAL
        }
    }

    fun actualDelta(dx: Float, dy: Float): Offset = let { policy ->
        when {
            dx == 0f && dy == 0f -> Offset.Zero
            policy == DEFAULT -> Offset(dx, dy)
            policy == ALWAYS_HORIZONTAL && dx == 0f -> Offset(dy, 0f)
            policy == ALWAYS_VERTICAL && dy == 0f -> Offset(0f, dx)
            else -> Offset(dx, dy)
        }
    }
}

data class ScrollAnchor(
    val id: Any,
    val point: DpOffset,
    val coordinates: State<LayoutCoordinates?>,
)

@NoriaOnly
fun Modifier.mouseWheelInput(
    scrollConfig: ScrollConfig,
    scrollHandlingPolicy: ScrollHandlingPolicy = ScrollHandlingPolicy.DEFAULT,
    resistDirection: ScrollDirection? = null,
    onMouseWheel: Density.(Offset) -> Boolean,
): Modifier = composed {
    val currentOnMouseWheel by rememberUpdatedState(onMouseWheel)
    pointerInput(scrollHandlingPolicy, scrollConfig) {
        awaitPointerEventScope {
            while (true) {
                val scrollEvent = awaitScrollEvent()
                val change = scrollEvent.changes[0]
                val transformedScrollDelta =
                    change.scrollDelta.run { scrollHandlingPolicy.actualDelta(x, y) }
                val shouldConsume =
                    currentOnMouseWheel(transformedScrollDelta)
                if (shouldConsume) {
                    scrollEvent.changes[0].consume()
                }
            }
        }
    }
}


@Composable
fun scrollPropagationBlocker(builder: @Composable () -> Unit) {
    // TODO
}

private suspend fun AwaitPointerEventScope.awaitScrollEvent(
    pass: PointerEventPass = PointerEventPass.Main,
    requireUnconsumed: Boolean = true,
): PointerEvent {
    var event: PointerEvent
    do {
        event = awaitPointerEvent(pass)
    } while (!event.isScroll(requireUnconsumed))
    return event
}

private fun PointerEvent.isScroll(requireUnconsumed: Boolean = true): Boolean {
    return type == PointerEventType.Scroll && (!requireUnconsumed || changes.none { it.isConsumed })
}