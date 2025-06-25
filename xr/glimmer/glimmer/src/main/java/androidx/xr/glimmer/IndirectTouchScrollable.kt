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

package androidx.xr.glimmer

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.Orientation.Horizontal
import androidx.compose.foundation.gestures.Orientation.Vertical
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.ExperimentalIndirectTouchTypeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.indirect.IndirectTouchEvent
import androidx.compose.ui.input.indirect.IndirectTouchEventType
import androidx.compose.ui.input.indirect.IndirectTouchInputModifierNode
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastMap
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configure indirect touch scrolling and flinging for the UI element in a single [Orientation].
 *
 * TODO(levima) Remove once upstreamed
 */
internal fun Modifier.indirectTouchScrollable(
    state: ScrollableState,
    orientation: Orientation,
    enabled: Boolean = true,
    reverseDirection: Boolean = false,
    flingBehavior: FlingBehavior? = null,
    interactionSource: MutableInteractionSource? = null,
) =
    this then
        IndirectTouchScrollableElement(
            state,
            orientation,
            enabled,
            reverseDirection,
            flingBehavior,
            interactionSource,
        )

private class IndirectTouchScrollableElement(
    val state: ScrollableState,
    val orientation: Orientation,
    val enabled: Boolean,
    val reverseDirection: Boolean,
    val flingBehavior: FlingBehavior?,
    val interactionSource: MutableInteractionSource?,
) : ModifierNodeElement<IndirectTouchScrollableNode>() {
    override fun create(): IndirectTouchScrollableNode {
        return IndirectTouchScrollableNode(
            state,
            flingBehavior,
            orientation,
            enabled,
            reverseDirection,
            interactionSource,
        )
    }

    override fun update(node: IndirectTouchScrollableNode) {
        node.update(state, orientation, enabled, reverseDirection, flingBehavior, interactionSource)
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + orientation.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + reverseDirection.hashCode()
        result = 31 * result + flingBehavior.hashCode()
        result = 31 * result + interactionSource.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is IndirectTouchScrollableElement) return false

        if (state != other.state) return false
        if (orientation != other.orientation) return false
        if (enabled != other.enabled) return false
        if (reverseDirection != other.reverseDirection) return false
        if (flingBehavior != other.flingBehavior) return false
        if (interactionSource != other.interactionSource) return false

        return true
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "indirectTouchScrollable"
        properties["orientation"] = orientation
        properties["state"] = state
        properties["enabled"] = enabled
        properties["reverseDirection"] = reverseDirection
        properties["flingBehavior"] = flingBehavior
        properties["interactionSource"] = interactionSource
    }
}

@Suppress("IllegalExperimentalApiUsage")
@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal class IndirectTouchScrollableNode(
    private var scrollableState: ScrollableState,
    private var flingBehavior: FlingBehavior?,
    private var orientation: Orientation,
    private var enabled: Boolean,
    private var reverseDirection: Boolean,
    private var interactionSource: MutableInteractionSource?,
) : DelegatingNode(), IndirectTouchInputModifierNode, CompositionLocalConsumerModifierNode {

    override val shouldAutoInvalidate: Boolean = false

    // Place holder fling behavior, we'll initialize it when the density is available.
    private val defaultFlingBehavior = DefaultFlingBehavior(splineBasedDecay(UnitDensity))
    private var indirectDragInteraction: DragInteraction.Start? = null
    private var indirectTouchEventProcessor: IndirectTouchEventProcessor? = null
    private var touchInputEventSmoother: TouchInputEventSmoother? = null

    fun update(
        scrollableState: ScrollableState,
        orientation: Orientation,
        enabled: Boolean,
        reverseDirection: Boolean,
        flingBehavior: FlingBehavior?,
        interactionSource: MutableInteractionSource?,
    ) {
        var resetInputHandling = false
        if (this.enabled != enabled) { // enabled changed
            this.enabled = enabled
            resetInputHandling = true
        }

        if (this.scrollableState != scrollableState) {
            this.scrollableState = scrollableState
            resetInputHandling = true
        }

        if (this.orientation != orientation) {
            this.orientation = orientation
            resetInputHandling = true
        }
        if (this.reverseDirection != reverseDirection) {
            this.reverseDirection = reverseDirection
            resetInputHandling = true
        }
        this.flingBehavior = flingBehavior
        this.interactionSource = interactionSource

        if (resetInputHandling) {
            indirectTouchEventProcessor?.resetProcessor()
        }
    }

    override fun onAttach() {
        updateDefaultFlingBehavior()
    }

    private fun updateDefaultFlingBehavior() {
        if (!isAttached) return
        val density = requireDensity()
        defaultFlingBehavior.flingDecay = splineBasedDecay(density)
    }

    override fun onDensityChange() {
        indirectTouchEventProcessor?.resetProcessor()
        updateDefaultFlingBehavior()
    }

    private fun onTouchEventRelease(velocity: Velocity) {
        coroutineScope.launch { doFlingAnimation(velocity) }
    }

    private fun startEventsListenerIfNeeded() {
        if (indirectTouchEventProcessor == null) {
            indirectTouchEventProcessor = IndirectTouchEventProcessor()
            touchInputEventSmoother = TouchInputEventSmoother()
            // start listening to events
            coroutineScope.launch {
                while (isActive) {
                    var event = indirectTouchEventProcessor?.scrollEvents?.receive()
                    if (event !is ScrollEvent.ScrollStarted) continue
                    processScrollStart()
                    try {
                        scroll(scrollPriority = MutatePriority.UserInput) {
                            while (
                                event !is ScrollEvent.ScrollStopped &&
                                    event !is ScrollEvent.ScrollCancelled
                            ) {
                                val deltaEvent = (event as? ScrollEvent.ScrollDelta)
                                deltaEvent?.let {
                                    scrollBy(deltaEvent.delta.toMeaningfulAxisOffset().toFloat())
                                }
                                event = indirectTouchEventProcessor?.scrollEvents?.receive()
                            }
                        }
                        if (event is ScrollEvent.ScrollStopped) {
                            processScrollStop(event as ScrollEvent.ScrollStopped)
                        } else if (event is ScrollEvent.ScrollCancelled) {
                            processScrollCancel()
                        }
                    } catch (c: CancellationException) {
                        processScrollCancel()
                    }
                }
            }
        }
    }

    override fun onIndirectTouchEvent(event: IndirectTouchEvent): Boolean {
        if (!enabled) return false

        startEventsListenerIfNeeded()

        return indirectTouchEventProcessor?.processIndirectTouchEvent(
            event.type,
            event.uptimeMillis,
            touchInputEventSmoother!!.smoothEventPosition(event),
            orientation,
            currentValueOf(LocalViewConfiguration),
        ) ?: false
    }

    override fun onPreIndirectTouchEvent(event: IndirectTouchEvent): Boolean = false

    private suspend fun processScrollStart() {
        indirectDragInteraction?.let { oldInteraction ->
            interactionSource?.emit(DragInteraction.Cancel(oldInteraction))
        }
        val interaction = DragInteraction.Start()
        interactionSource?.emit(interaction)
        indirectDragInteraction = interaction
    }

    private suspend fun processScrollStop(event: ScrollEvent.ScrollStopped) {
        indirectDragInteraction?.let { interaction ->
            interactionSource?.emit(DragInteraction.Stop(interaction))
            indirectDragInteraction = null
        }
        onTouchEventRelease(event.velocity)
    }

    private suspend fun processScrollCancel() {
        indirectDragInteraction?.let { interaction ->
            interactionSource?.emit(DragInteraction.Cancel(interaction))
            indirectDragInteraction = null
        }
        onTouchEventRelease(Velocity.Zero)
    }

    fun disposeInteractionSource() {
        indirectDragInteraction?.let { interaction ->
            interactionSource?.tryEmit(DragInteraction.Cancel(interaction))
            indirectDragInteraction = null
        }
    }

    // specifies if this scrollable node is currently flinging
    var isFlinging = false
        private set

    fun Float.toOffset(): Offset =
        when {
            this == 0f -> Offset.Zero
            orientation == Horizontal -> Offset(this, 0f)
            else -> Offset(0f, this)
        }

    fun Offset.singleAxisOffset(): Offset =
        if (orientation == Horizontal) copy(y = 0f) else copy(x = 0f)

    fun Offset.toFloat(): Float = if (orientation == Horizontal) this.x else this.y

    fun Float.toVelocity(): Velocity =
        when {
            this == 0f -> Velocity.Zero
            orientation == Horizontal -> Velocity(this, 0f)
            else -> Velocity(0f, this)
        }

    private fun Velocity.toFloat(): Float = if (orientation == Horizontal) this.x else this.y

    private fun Velocity.singleAxisVelocity(): Velocity =
        if (orientation == Horizontal) copy(y = 0f) else copy(x = 0f)

    private fun Velocity.update(newValue: Float): Velocity =
        if (orientation == Horizontal) copy(x = newValue) else copy(y = newValue)

    fun Float.reverseIfNeeded(): Float = if (reverseDirection) this * -1 else this

    fun Offset.reverseIfNeeded(): Offset = if (reverseDirection) this * -1f else this

    /**
     * Converts deltas from the "relevant" axis in the input event velocity to the "meaningful"
     * axis, that is, the axis that this scrollable consumes events.
     */
    fun Offset.toMeaningfulAxisOffset(): Offset {
        val offset = this.toRelevantAxis()
        return if (orientation == Horizontal) Offset(offset, 0f) else Offset(0f, offset)
    }

    private var outerStateScope = NoOpScrollScope

    private var dispatchingScope =
        object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                return with(outerStateScope) { performScroll(pixels.toOffset()).toFloat() }
            }
        }

    private fun ScrollScope.performScroll(delta: Offset): Offset {
        val singleAxisDeltaForSelfScroll = delta.singleAxisOffset().reverseIfNeeded().toFloat()

        // Consume on a single axis.
        val consumedBySelfScroll =
            scrollBy(singleAxisDeltaForSelfScroll).toOffset().reverseIfNeeded()

        return consumedBySelfScroll
    }

    fun performRawScroll(scroll: Offset): Offset {
        return if (scrollableState.isScrollInProgress) {
            Offset.Zero
        } else {
            dispatchRawDelta(scroll)
        }
    }

    private fun dispatchRawDelta(scroll: Offset): Offset {
        return scrollableState
            .dispatchRawDelta(scroll.toFloat().reverseIfNeeded())
            .reverseIfNeeded()
            .toOffset()
    }

    private suspend fun doFlingAnimation(available: Velocity): Velocity {
        var result: Velocity = available
        isFlinging = true
        try {
            scroll(scrollPriority = MutatePriority.Default) {
                val outerScrollScope = this
                val reverseScope =
                    object : ScrollScope {
                        override fun scrollBy(pixels: Float): Float {
                            // Fling has hit the bounds or node left composition,
                            // cancel it to allow continuation. This will conclude this node's
                            // fling,
                            // allowing the onPostFling signal to be called
                            // with the leftover velocity from the fling animation. Any nested
                            // scroll
                            // node above will be able to pick up the left over velocity and
                            // continue
                            // the fling.
                            if (pixels.absoluteValue != 0.0f && !isAttached) {
                                throw FlingCancellationException()
                            }

                            return outerScrollScope
                                .scrollBy(pixels.toOffset().reverseIfNeeded().toFloat())
                                .toFloat()
                                .reverseIfNeeded()
                        }
                    }
                with(reverseScope) {
                    val resolvedFling = flingBehavior ?: defaultFlingBehavior
                    with(resolvedFling) {
                        result =
                            result.update(
                                performFling(available.toFloat().reverseIfNeeded())
                                    .reverseIfNeeded()
                            )
                    }
                }
            }
        } finally {
            isFlinging = false
        }

        return result
    }

    fun shouldScrollImmediately(): Boolean {
        return scrollableState.isScrollInProgress
    }

    /** Opens a scrolling session with nested scrolling and overscroll support. */
    private suspend fun scroll(
        scrollPriority: MutatePriority = MutatePriority.Default,
        block: suspend ScrollScope.() -> Unit,
    ) {
        scrollableState.scroll(scrollPriority) {
            outerStateScope = this
            block.invoke(dispatchingScope)
        }
    }

    fun isVertical(): Boolean = orientation == Vertical
}

private val NoOpScrollScope: ScrollScope =
    object : ScrollScope {
        override fun scrollBy(pixels: Float): Float = pixels
    }

/** A scroll scope for nested scrolling and overscroll support. */
private interface NestedScrollScope {
    fun scrollBy(offset: Offset, source: NestedScrollSource): Offset

    fun scrollByWithOverscroll(offset: Offset, source: NestedScrollSource): Offset
}

internal val UnitDensity =
    object : Density {
        override val density: Float
            get() = 1f

        override val fontScale: Float
            get() = 1f
    }

internal class DefaultFlingBehavior(
    var flingDecay: DecayAnimationSpec<Float>,
    private val motionDurationScale: MotionDurationScale = DefaultScrollMotionDurationScale,
) : FlingBehavior {

    // For Testing
    var lastAnimationCycleCount = 0

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        lastAnimationCycleCount = 0
        // come up with the better threshold, but we need it since spline curve gives us NaNs
        return withContext(motionDurationScale) {
            if (abs(initialVelocity) > 1f) {
                var velocityLeft = initialVelocity
                var lastValue = 0f
                val animationState =
                    AnimationState(initialValue = 0f, initialVelocity = initialVelocity)
                try {
                    animationState.animateDecay(flingDecay) {
                        val delta = value - lastValue
                        val consumed = scrollBy(delta)
                        lastValue = value
                        velocityLeft = this.velocity
                        // avoid rounding errors and stop if anything is unconsumed
                        if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
                        lastAnimationCycleCount++
                    }
                } catch (e: CancellationException) {
                    velocityLeft = animationState.velocity
                }
                velocityLeft
            } else {
                initialVelocity
            }
        }
    }
}

private const val DefaultScrollMotionDurationScaleFactor = 1f
private val DefaultScrollMotionDurationScale =
    object : MotionDurationScale {
        override val scaleFactor: Float
            get() = DefaultScrollMotionDurationScaleFactor
    }

private class FlingCancellationException :
    CancellationException("The fling animation was cancelled")

@Suppress("IllegalExperimentalApiUsage")
@OptIn(ExperimentalIndirectTouchTypeApi::class)
private class IndirectTouchEventProcessor() {
    private var velocityTracker: VelocityTracker? = null
    private var hasCrossedTouchSlop = false
    private var previousIndirectTouchPosition = Offset.Zero
    private var positionAccumulator = Offset.Zero

    val scrollEvents = Channel<ScrollEvent>(capacity = Channel.UNLIMITED)

    fun processIndirectTouchEvent(
        eventType: IndirectTouchEventType,
        eventTime: Long,
        eventPosition: Offset,
        orientation: Orientation,
        viewConfiguration: ViewConfiguration,
    ): Boolean {
        if (velocityTracker == null) velocityTracker = VelocityTracker()

        return when (eventType) {
            IndirectTouchEventType.Press -> {
                velocityTracker?.addPosition(eventTime, eventPosition)
                previousIndirectTouchPosition = eventPosition
                true
            }

            IndirectTouchEventType.Move -> {
                var delta = eventPosition - previousIndirectTouchPosition
                var consumed = false

                if (!hasCrossedTouchSlop) {
                    positionAccumulator += delta
                    hasCrossedTouchSlop =
                        abs(positionAccumulator.toRelevantAxis()) > viewConfiguration.touchSlop

                    if (hasCrossedTouchSlop) {
                        val newDelta =
                            (abs(positionAccumulator.toRelevantAxis()) -
                                viewConfiguration.touchSlop) *
                                positionAccumulator.toRelevantAxis().sign
                        delta = positionAccumulator.overrideRelevantAxis(newDelta)
                        scrollEvents.trySend(ScrollEvent.ScrollStarted)
                        scrollEvents.trySend(ScrollEvent.ScrollDelta(delta))
                        consumed = true
                    }
                }

                if (
                    hasCrossedTouchSlop && delta.toRelevantAxis().absoluteValue > PixelSensitivity
                ) {
                    velocityTracker?.addPosition(eventTime, eventPosition)
                    consumed = true
                    scrollEvents.trySend(ScrollEvent.ScrollDelta(delta))
                }
                previousIndirectTouchPosition = eventPosition
                consumed
            }
            IndirectTouchEventType.Release -> {
                velocityTracker?.let { tracker ->
                    val maxVelocity = viewConfiguration.maximumFlingVelocity
                    val event =
                        ScrollEvent.ScrollStopped(
                            tracker
                                .calculateVelocity(Velocity(maxVelocity, maxVelocity))
                                .toMeaningfulAxisVelocity(orientation)
                        )
                    scrollEvents.trySend(event)
                }

                resetProcessor()
                true
            }
            else -> {
                scrollEvents.trySend(ScrollEvent.ScrollCancelled)
                resetProcessor()
                false
            }
        }
    }

    fun resetProcessor() {
        previousIndirectTouchPosition = Offset.Zero
        positionAccumulator = Offset.Zero
        velocityTracker?.resetTracking()
        hasCrossedTouchSlop = false
    }

    /**
     * Converts deltas from the "relevant" axis in the input event velocity to the "meaningful"
     * axis, that is, the axis that this scrollable consumes events.
     */
    private fun Velocity.toMeaningfulAxisVelocity(orientation: Orientation): Velocity {
        val offset = this.toRelevantAxis()
        return if (orientation == Horizontal) Velocity(offset, 0f) else Velocity(0f, offset)
    }

    companion object {
        private const val PixelSensitivity = 2
    }
}

private sealed class ScrollEvent {
    object ScrollStarted : ScrollEvent()

    object ScrollCancelled : ScrollEvent()

    class ScrollStopped(val velocity: Velocity) : ScrollEvent()

    class ScrollDelta(val delta: Offset) : ScrollEvent()
}

/** Smoothes touch input events that are too frequent and noisy */
@Suppress("IllegalExperimentalApiUsage")
@OptIn(ExperimentalIndirectTouchTypeApi::class)
private class TouchInputEventSmoother() {
    private var rotatingIndex = 0
    private var rotatingArray = mutableListOf<IndirectTouchEvent>()

    fun smoothEventPosition(event: IndirectTouchEvent): Offset {
        var xPosition = event.position.x
        var yPosition = event.position.y

        if (event.type == IndirectTouchEventType.Press) {
            rotatingIndex = 0
            rotatingArray.clear()
        }

        if (event.type == IndirectTouchEventType.Move) {
            if (rotatingArray.size == SmoothingFactor) {
                rotatingArray[rotatingIndex] = event
            } else {
                rotatingArray.add(event)
            }

            if (rotatingIndex == SmoothingFactor) {
                rotatingIndex = 0
            }
            xPosition = rotatingArray.fastMap { it.position.x }.average().toFloat()
            yPosition = rotatingArray.fastMap { it.position.y }.average().toFloat()
        }

        return Offset(xPosition, yPosition)
    }

    companion object {
        private const val SmoothingFactor = 3
    }
}

/** Converts to the axis that is most representative of motion */
private fun Offset.toRelevantAxis(): Float = x

private fun Velocity.toRelevantAxis(): Float = x

private fun Offset.overrideRelevantAxis(newValue: Float): Offset = Offset(x = newValue, y = this.y)
