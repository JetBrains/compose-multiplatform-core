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
import androidx.compose.ui.noriaComposed
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

data class ScrollState(
    val position: DpOffset,
    val contentSize: DpSize?,
    val scrollSize: DpSize?,
    val cookie: Any,
    val latestAuthor: Any,
) {
    companion object {
        fun empty(): ScrollState {
            return ScrollState(DpOffset.Zero, null, null, Any(), Any())
        }
    }

    fun updatePosition(dx: Dp = 0.dp, dy: Dp = 0.dp): ScrollState {
        return this.copy(position = this.position + DpOffset(dx, dy))
    }

    fun clampedScrollPosition(dx: Dp, dy: Dp): DpOffset {
        val maxPositionX: Dp
        val maxPositionY: Dp
        if (contentSize == null || scrollSize == null) {
            maxPositionX = Float.POSITIVE_INFINITY.dp
            maxPositionY = Float.POSITIVE_INFINITY.dp
        } else {
            maxPositionX = (contentSize.width - scrollSize.width).coerceAtLeast(0.dp)
            maxPositionY = (contentSize.height - scrollSize.height).coerceAtLeast(0.dp)
        }
        return DpOffset(
            (position.x + dx).coerceIn(0.dp, maxPositionX),
            (position.y + dy).coerceIn(0.dp, maxPositionY)
        )
    }
}

data class ScrollAnchor(
    val id: Any,
    val point: DpOffset,
    val coordinates: State<LayoutCoordinates?>,
)

@Composable
fun NoriaContext.scrollAnchorPreserver(
    scrollState: MutableState<ScrollState>,
    builder: @Composable NoriaContext.(MutableState<ScrollAnchor?>) -> Unit,
) {
    // TODO
    builder(remember { mutableStateOf(null) })
}

@Composable
fun NoriaContext.scrollCore(
    direction: ScrollDirection = ScrollDirection.BOTH,
    scrollHandlingPolicy: ScrollHandlingPolicy = ScrollHandlingPolicy.DEFAULT,
    propagationEnabled: Boolean = true,
    scrollState: MutableState<ScrollState>,
    fadeOptions: FadeOptions? = null,
    suppressOppositeDirection: Boolean = false,
    enabled: Boolean = true,
    builder: @Composable NoriaContext.() -> Unit,
) {
    val density = LocalDensity.current
    val horizontalScrollState = remember(scrollState, direction, density, propagationEnabled) {
        if (direction != ScrollDirection.VERTICAL) {
            NoriaOneDimensionalScrollState(
                scrollState,
                Orientation.Horizontal,
                density,
                propagationEnabled
            )
        } else {
            null
        }
    }
    val verticalScrollState = remember(scrollState, direction, density, propagationEnabled) {
        if (direction != ScrollDirection.HORIZONTAL) {
            NoriaOneDimensionalScrollState(
                scrollState,
                Orientation.Vertical,
                density,
                propagationEnabled
            )
        } else {
            null
        }
    }
    Layout(
        { builder() },
        Modifier
            .let {
                when (direction) {
                    ScrollDirection.BOTH -> it
                        .noriaOneDimensionalScroll(
                            horizontalScrollState!!,
                            Orientation.Horizontal,
                            enabled
                        )
                        .noriaOneDimensionalScroll(
                            verticalScrollState!!,
                            Orientation.Vertical,
                            enabled
                        )

                    ScrollDirection.VERTICAL -> it.noriaOneDimensionalScroll(
                        verticalScrollState!!,
                        Orientation.Vertical,
                        enabled,
                    )

                    ScrollDirection.HORIZONTAL -> it.noriaOneDimensionalScroll(
                        horizontalScrollState!!,
                        Orientation.Horizontal,
                        enabled,
                    )
                }
            }
            .let {
                if (fadeOptions != null) {
                    it.scrollFadeEdges(fadeOptions.color, fadeOptions.width, scrollState)
                } else {
                    it
                }
            },
    ) { measurables, constraints ->
        val placeable = measurables.singleOrNull()?.measure(constraints)
        layout(
            placeable?.width ?: constraints.minWidth,
            placeable?.height ?: constraints.minHeight
        ) {
            placeable?.place(IntOffset.Zero)
        }
    }
}

private fun Modifier.noriaOneDimensionalScroll(
    state: NoriaOneDimensionalScrollState,
    orientation: Orientation,
    enabled: Boolean,
): Modifier {
    val isVertical = orientation == Orientation.Vertical
    return composed(
        factory = {
            val reverseDirection =
                ScrollableDefaults.reverseDirection(
                    LocalLayoutDirection.current,
                    orientation,
                    reverseScrolling = false,
                )
            val overscrollEffect = ScrollableDefaults.overscrollEffect()
            Modifier
                .clipScrollableContainer(orientation)
                .overscroll(overscrollEffect)
                .scrollable(
                    state,
                    orientation,
                    overscrollEffect,
                    enabled,
                    reverseDirection,
                )
                .then(
                    NoriaOneDimensionalScrollingLayoutElement(
                        state,
                        reverseScrolling = false,
                        isVertical
                    )
                )
        },
        inspectorInfo =
            debugInspectorInfo {
                name = "scroll"
                properties["state"] = state
                properties["orientation"] = orientation
                properties["enabled"] = enabled
            }
    )
}

private class NoriaOneDimensionalScrollState(
    val wrappedState: MutableState<ScrollState>,
    private val orientation: Orientation,
    private val density: Density,
    propagationEnabled: Boolean,
) : ScrollableState by ScrollableState(consumeScrollDelta = { delta ->
    val currentState = wrappedState.value
    val toConsume = delta.coerceIn(
        currentState.minConsumableDelta(orientation, density).toFloat(),
        currentState.maxConsumableDelta(orientation, density).toFloat(),
    )
    val toConsumeInDp = density.run { toConsume.toDp() }
    wrappedState.value = wrappedState.value.run {
        copy(
            position = position.run {
                when (orientation) {
                    Orientation.Horizontal -> copy(x = x + toConsumeInDp)
                    Orientation.Vertical -> copy(y = y + toConsumeInDp)
                }
            }
        )
    }
    if (propagationEnabled) toConsume else delta
}) {
    val value: Int get() = wrappedState.value.value(orientation, density)

    val maxValue: Int
        get() = wrappedState.value.maxValue(orientation, density)

    val viewportSize: Int
        get() = wrappedState.value.viewportSize(orientation, density)

    override val canScrollForward: Boolean by derivedStateOf { value < maxValue }

    override val canScrollBackward: Boolean by derivedStateOf { value > 0 }
}

private fun ScrollState.minConsumableDelta(orientation: Orientation, density: Density): Int =
    value(orientation, density)

private fun ScrollState.maxConsumableDelta(orientation: Orientation, density: Density): Int =
    maxValue(orientation, density) - value(orientation, density)

private fun ScrollState.value(orientation: Orientation, density: Density): Int = density.run {
    when (orientation) {
        Orientation.Horizontal -> position.x
        Orientation.Vertical -> position.y
    }.roundToPx().coerceAtLeast(0)
}

private fun ScrollState.maxValue(orientation: Orientation, density: Density): Int = density.run {
    when (orientation) {
        Orientation.Horizontal -> ((contentSize?.width ?: 0.dp) - (scrollSize?.width ?: 0.dp))
        Orientation.Vertical -> ((contentSize?.height ?: 0.dp) - (scrollSize?.height ?: 0.dp))
    }.roundToPx().coerceAtLeast(0)
}

private fun ScrollState.viewportSize(orientation: Orientation, density: Density): Int =
    density.run {
        when (orientation) {
            Orientation.Horizontal -> scrollSize?.width ?: 0.dp
            Orientation.Vertical -> scrollSize?.height ?: 0.dp
        }.roundToPx().coerceAtLeast(0)
    }

private class NoriaOneDimensionalScrollingLayoutElement(
    val scrollState: NoriaOneDimensionalScrollState,
    val reverseScrolling: Boolean,
    val isVertical: Boolean
) : ModifierNodeElement<NoriaOneDimensionalScrollNode>() {
    override fun create(): NoriaOneDimensionalScrollNode {
        return NoriaOneDimensionalScrollNode(
            state = scrollState,
            reverseScrolling = reverseScrolling,
            isVertical = isVertical
        )
    }

    override fun update(node: NoriaOneDimensionalScrollNode) {
        node.state = scrollState
        node.reverseScrolling = reverseScrolling
        node.isVertical = isVertical
    }

    override fun hashCode(): Int {
        var result = scrollState.hashCode()
        result = 31 * result + reverseScrolling.hashCode()
        result = 31 * result + isVertical.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is NoriaOneDimensionalScrollingLayoutElement) return false
        return scrollState == other.scrollState &&
            reverseScrolling == other.reverseScrolling &&
            isVertical == other.isVertical
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scroll"
        properties["state"] = scrollState
        properties["reverseScrolling"] = reverseScrolling
        properties["isVertical"] = isVertical
    }
}

private class NoriaOneDimensionalScrollNode(
    var state: NoriaOneDimensionalScrollState,
    var reverseScrolling: Boolean,
    var isVertical: Boolean
) : LayoutModifierNode, SemanticsModifierNode, Modifier.Node() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        checkScrollableContainerConstraints(
            constraints,
            if (isVertical) Orientation.Vertical else Orientation.Horizontal
        )

        val childConstraints =
            constraints.copy(
                maxHeight = if (isVertical) Constraints.Infinity else constraints.maxHeight,
                maxWidth = if (isVertical) constraints.maxWidth else Constraints.Infinity
            )
        val placeable = measurable.measure(childConstraints)
        val width = placeable.width.coerceAtMost(constraints.maxWidth)
        val height = placeable.height.coerceAtMost(constraints.maxHeight)
        val scrollHeight = placeable.height - height
        val scrollWidth = placeable.width - width
        val side = if (isVertical) scrollHeight else scrollWidth
        // The max value must be updated before returning from the measure block so that any other
        // chained RemeasurementModifiers that try to perform scrolling based on the new
        // measurements inside onRemeasured are able to scroll to the new max based on the newly-
        // measured size.
        state.wrappedState.value = state.wrappedState.value.run {
            copy(
                scrollSize = DpSize(width.toDp(), height.toDp()),
                contentSize = DpSize(placeable.width.toDp(), placeable.height.toDp()),
                position = position.run {
                    copy(
                        x = x.coerceIn(0.dp, scrollWidth.toDp()),
                        y = y.coerceIn(0.dp, scrollHeight.toDp())
                    )
                }
            )
        }
//        state.maxValue = side
//        state.viewportSize = if (isVertical) height else width
        return layout(width, height) {
            val scroll = state.value.fastCoerceIn(0, side)
            val absScroll = if (reverseScrolling) scroll - side else -scroll
            val xOffset = if (isVertical) 0 else absScroll
            val yOffset = if (isVertical) absScroll else 0

            // Tagging as direct manipulation, such that consumers of this offset can decide whether
            // to exclude this offset on their coordinates calculation. Such as whether an
            // `approachLayout` will animate it or directly apply the offset without animation.
            withMotionFrameOfReferencePlacement {
                placeable.placeRelativeWithLayer(xOffset, yOffset)
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return if (isVertical) {
            measurable.minIntrinsicWidth(Constraints.Infinity)
        } else {
            measurable.minIntrinsicWidth(height)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        return if (isVertical) {
            measurable.minIntrinsicHeight(width)
        } else {
            measurable.minIntrinsicHeight(Constraints.Infinity)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return if (isVertical) {
            measurable.maxIntrinsicWidth(Constraints.Infinity)
        } else {
            measurable.maxIntrinsicWidth(height)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        return if (isVertical) {
            measurable.maxIntrinsicHeight(width)
        } else {
            measurable.maxIntrinsicHeight(Constraints.Infinity)
        }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        isTraversalGroup = true
        val accessibilityScrollState =
            ScrollAxisRange(
                value = { state.value.toFloat() },
                maxValue = { state.maxValue.toFloat() },
                reverseScrolling = reverseScrolling
            )
        if (isVertical) {
            this.verticalScrollAxisRange = accessibilityScrollState
        } else {
            this.horizontalScrollAxisRange = accessibilityScrollState
        }
    }
}

@NoriaOnly
fun Modifier.mouseWheelInput(
    scrollConfig: ScrollConfig,
    scrollHandlingPolicy: ScrollHandlingPolicy = ScrollHandlingPolicy.DEFAULT,
    resistDirection: ScrollDirection? = null,
    onMouseWheel: Density.(Offset) -> Boolean,
): Modifier = noriaComposed { noriaContext ->
    with(noriaContext) {
        val currentOnMouseWheel by rememberUpdatedState(onMouseWheel)
        pointerInput(scrollHandlingPolicy, scrollConfig) {
            awaitPointerEventScope {
                while (true) {
                    val scrollEvent = awaitScrollEvent()
                    val shouldConsume =
                        currentOnMouseWheel(scrollEvent.changes[0].scrollDelta)
                    if (shouldConsume) {
                        scrollEvent.changes[0].consume()
                    }
                }
            }
        }
    }
}

fun scrollByDelta(scrollState: MutableState<ScrollState>, dx: Dp, dy: Dp): DpOffset {
    val scrollStateValue = scrollState.value
    return when (val projected = scrollStateValue.clampedScrollPosition(dx, dy)) {
        scrollStateValue.position -> DpOffset.Zero
        else -> {
            scrollState.value = scrollStateValue.run {
                copy(position = clampedScrollPosition(dx, dy), latestAuthor = Any())
            }
            val consumed = scrollStateValue.position - projected
            consumed
        }
    }
}

@Composable
fun NoriaContext.scrollTo(
    scrollTarget: ScrollTarget,
    density: Density,
    target: DpRect? = null,
    xAxisKind: ScrollKind = ScrollKind.Smallest,
    yAxisKind: ScrollKind = ScrollKind.Smallest,
    animate: Boolean = true,
    cookie: Any = 0,
    author: Any? = null,
    expectedAuthor: Any? = null,
    onScrollFinished: (() -> Unit)? = null, // should be idempotent as it might be called twice
) {
    // TODO xAxisKind, yAxisKind, animate, cookie, author
    val currentOnScrollFinished by rememberUpdatedState(onScrollFinished)
    LaunchedEffect(
        scrollTarget,
        density,
        target,
        cookie,
    ) {
        val targetRect = target?.run {
            density.run {
                Rect(
                    left.toPx(),
                    top.toPx(),
                    right.toPx(),
                    bottom.toPx()
                )
            }
        }
        scrollTarget.bringIntoViewRequester.bringIntoView(targetRect)
        currentOnScrollFinished?.invoke()
    }
}

@Composable
fun NoriaContext.rememberReversedScrollState(
    scrollState: MutableState<ScrollState>,
    reverseVertically: Boolean,
    reverseHorizontally: Boolean,
): MutableState<ScrollState> {
    return remember(scrollState, reverseHorizontally, reverseVertically) {
        object : MutableState<ScrollState> {
            override var value: ScrollState
                get() {
                    return scrollState.value.run {
                        copy(
                            position = position
                                .let {
                                    if (reverseHorizontally) {
                                        it + DpOffset(
                                            contentSize?.width ?: 0.dp,
                                            0.dp
                                        ) + DpOffset(-(scrollSize?.width ?: 0.dp), 0.dp)
                                    } else {
                                        it
                                    }
                                }
                                .let {
                                    if (reverseVertically) {
                                        it + DpOffset(
                                            0.dp,
                                            contentSize?.height ?: 0.dp
                                        ) + DpOffset(0.dp, -(scrollSize?.height ?: 0.dp))
                                    } else {
                                        it
                                    }
                                }
                        )
                    }
                }
                set(value) {
                    val scrollStateValue = scrollState.value
                    scrollState.value = value.copy(
                        position = value.position
                            .let {
                                if (reverseHorizontally) {
                                    it + DpOffset(
                                        -(scrollStateValue.contentSize?.width ?: 0.dp),
                                        0.dp
                                    ) + DpOffset(
                                        scrollStateValue.scrollSize?.width
                                            ?: 0.dp, 0.dp
                                    )
                                } else {
                                    it
                                }
                            }
                            .let {
                                if (reverseVertically) {
                                    it + DpOffset(
                                        0.dp,
                                        -(scrollStateValue.contentSize?.height ?: 0.dp)
                                    ) + DpOffset(
                                        0.dp, scrollStateValue.scrollSize?.height
                                            ?: 0.dp
                                    )
                                } else {
                                    it
                                }
                            })
                }

            override fun component1(): ScrollState = value

            override fun component2(): (ScrollState) -> Unit = valueSetter

            private val valueSetter: (ScrollState) -> Unit = {
                value = it
            }
        }
    }
}

@Composable
fun NoriaContext.scrollPropagationBlocker(builder: @Composable NoriaContext.() -> Unit) {
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