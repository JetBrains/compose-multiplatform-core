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

package androidx.compose.foundation.gestures

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class MouseWheelScrollingLogic(
    scrollingLogic: ScrollingLogic,
    private val mouseWheelScrollConfig: ScrollConfig,
    onScrollStopped: suspend (velocity: Velocity) -> Unit,
    density: Density,
    private val layoutCoordinates: () -> LayoutCoordinates?,
) : NonTouchScrollingLogic(scrollingLogic, onScrollStopped, density) {
    /** Identifies this scrollable in [MouseWheelScrollCaptureManager]. */
    private val captureToken = Any()

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pointerEvent.type != PointerEventType.Scroll) {
            if (pass == PointerEventPass.Initial) {
                MouseWheelScrollCaptureManager.onNonScrollEvent(
                    captureToken,
                    layoutCoordinates(),
                    pointerEvent,
                )
            }
            return
        }
        if (pointerEvent.isConsumed) {
            // An ancestor took this scroll during the initial pass, so it owns the wheel now.
            if (pass == PointerEventPass.Initial) {
                MouseWheelScrollCaptureManager.release(captureToken)
            }
            return
        }
        /**
         * If this scrollable owns the mouse wheel (it consumed the latest scroll and the pointer
         * didn't move since), or it is already scrolling from a previous interaction, consume
         * immediately to give it priority over nested scrollables that moved under the pointer.
         */
        if (pass == PointerEventPass.Initial) {
            val isOwner =
                MouseWheelScrollCaptureManager.isOwner(
                    captureToken,
                    layoutCoordinates(),
                    pointerEvent,
                )
            if (isOwner || isScrolling) {
                val consumed = onMouseWheel(pointerEvent, bounds)
                if (consumed || isScrolling) {
                    pointerEvent.consume()
                }
            }
        }

        /**
         * During the main pass. If this scrollable is not scrolling, decide if it should based on
         * the consumption. If the scrollable is scrolling we don't need to worry because it
         * consumed during the initial pass.
         */
        if (pass == PointerEventPass.Main && !isScrolling) {
            val consumed = onMouseWheel(pointerEvent, bounds)
            if (consumed) {
                pointerEvent.consume()
            }
        }
    }

    override fun onDetach() {
        MouseWheelScrollCaptureManager.release(captureToken)
    }

    private data class MouseWheelScrollDelta(
        val value: Offset,
        val timeMillis: Long,
        val shouldApplyImmediately: Boolean,
    ) {
        operator fun plus(other: MouseWheelScrollDelta) =
            MouseWheelScrollDelta(
                value = value + other.value,

                // Pick time from last one
                timeMillis = maxOf(timeMillis, other.timeMillis),

                // Ignore [other.shouldApplyImmediately] to avoid false-positive
                // [isPreciseWheelScroll]
                // detection during animation
                shouldApplyImmediately = shouldApplyImmediately,
            )
    }

    private val channel = Channel<MouseWheelScrollDelta>(capacity = Channel.UNLIMITED)

    private var receivingMouseWheelEventsJob: Job? = null

    override fun startReceivingEvents(coroutineScope: CoroutineScope) {
        if (receivingMouseWheelEventsJob == null) {
            receivingMouseWheelEventsJob =
                coroutineScope.launch {
                    try {
                        while (coroutineContext.isActive) {
                            val scrollDelta = channel.receive()
                            val threshold = with(density) { AnimationThreshold.toPx() }
                            val speed = with(density) { AnimationSpeed.toPx() }
                            scrollingLogic.dispatchMouseWheelScroll(scrollDelta, threshold, speed)
                        }
                    } finally {
                        receivingMouseWheelEventsJob = null
                    }
                }
        }
    }

    private fun onMouseWheel(pointerEvent: PointerEvent, bounds: IntSize): Boolean {
        val scrollDelta =
            with(mouseWheelScrollConfig) {
                with(density) { calculateMouseWheelScroll(pointerEvent, bounds) }
            }
        val accepted =
            scrollingLogic.canConsumeDelta(scrollDelta) &&
                channel
                    .trySend(
                        MouseWheelScrollDelta(
                            value = scrollDelta,
                            timeMillis = pointerEvent.changes.first().uptimeMillis,
                            shouldApplyImmediately =
                                !mouseWheelScrollConfig.isSmoothScrollingEnabled

                                    // In case of high-resolution wheel, such as a freely rotating
                                    // wheel with no notches or trackpads, delta should apply
                                    // immediately, without any delays.
                                    || mouseWheelScrollConfig.isPreciseWheelScroll(pointerEvent),
                        )
                    )
                    .isSuccess
        if (accepted && scrollingLogic.isMainAxisDominant(scrollDelta)) {
            MouseWheelScrollCaptureManager.capture(captureToken, layoutCoordinates(), pointerEvent)
        } else {
            MouseWheelScrollCaptureManager.release(captureToken)
        }
        return accepted
    }

    /**
     * Only a scroll mostly along the scrollable's axis grants the ownership, so that a diagonal or
     * cross-axis scroll doesn't lock nested scrollables of the other orientation out.
     */
    private fun ScrollingLogic.isMainAxisDominant(scrollDelta: Offset): Boolean {
        val mainAxisDelta = abs(scrollDelta.toFloat())
        val crossAxisDelta = abs(scrollDelta.x) + abs(scrollDelta.y) - mainAxisDelta
        return mainAxisDelta != 0f && mainAxisDelta > crossAxisDelta
    }

    private fun Channel<MouseWheelScrollDelta>.sumOrNull(): MouseWheelScrollDelta? {
        var sum: MouseWheelScrollDelta? = null
        for (i in untilNull { tryReceive().getOrNull() }) {
            sum = if (sum == null) i else sum + i
        }
        return sum
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun ScrollingLogic.canConsumeDelta(scrollDelta: Offset): Boolean {
        /**
         * Mouse wheel scroll deltas may come as 2 dimensional values. We use the angle to decide
         * which axis in the delta is more important and should be triggered.
         */
        val delta = scrollDelta.reverseIfNeeded().toSingleAxisDeltaFromAngle()
        return if (delta == 0f) {
            false // It means that it's for another axis and cannot be consumed
        } else if (delta > 0f) {
            scrollableState.canScrollForward
        } else {
            scrollableState.canScrollBackward
        }
    }

    private fun trackVelocity(scrollDelta: MouseWheelScrollDelta) {
        velocityTracker.addDelta(scrollDelta.timeMillis, scrollDelta.value)
    }

    @OptIn(ExperimentalFoundationApi::class)
    private suspend fun ScrollingLogic.dispatchMouseWheelScroll(
        scrollDelta: MouseWheelScrollDelta,
        threshold: Float, // px
        speed: Float, // px / ms
    ) {
        var targetScrollDelta = scrollDelta
        trackVelocity(scrollDelta)
        // Sum delta from all pending events to avoid multiple animation restarts.
        channel.sumOrNull()?.let {
            trackVelocity(it)
            targetScrollDelta += it
        }
        var targetValue = targetScrollDelta.value.reverseIfNeeded().toFloat()
        if (targetValue.isLowScrollingDelta()) {
            return
        }
        var animationState = AnimationState(0f)

        /*
         * TODO Handle real down/up events from touchpad to set isScrollInProgress correctly.
         *  Touchpads emit just multiple mouse wheel events, so detecting start and end of this
         *  "gesture" is not straight forward.
         *  Ideally it should be resolved by catching real touches from input device instead of
         *  waiting the next event with timeout before resetting progress flag.
         */
        suspend fun waitNextScrollDelta(timeoutMillis: Long): Boolean {
            if (timeoutMillis < 0) return false
            return withTimeoutOrNull(timeoutMillis) { channel.busyReceive() }
                ?.let {
                    // Keep this value unchanged during animation
                    // Currently, [isPreciseWheelScroll] might be unstable in case if
                    // a precise value is almost equal regular one.
                    val previousDeltaShouldApplyImmediately =
                        targetScrollDelta.shouldApplyImmediately
                    targetScrollDelta =
                        it.copy(shouldApplyImmediately = previousDeltaShouldApplyImmediately)
                    targetValue =
                        targetScrollDelta.value.reverseIfNeeded().toSingleAxisDeltaFromAngle()
                    animationState = AnimationState(0f) // Reset previous animation leftover
                    trackVelocity(it)

                    !targetValue.isLowScrollingDelta()
                } ?: false
        }

        userScroll {
            var requiredAnimation = true
            while (requiredAnimation) {
                requiredAnimation = false
                val targetValueLeftover = targetValue - animationState.value
                if (
                    targetScrollDelta.shouldApplyImmediately || abs(targetValueLeftover) < threshold
                ) {
                    dispatchMouseWheelScroll(targetValueLeftover)
                    requiredAnimation = waitNextScrollDelta(ScrollProgressTimeout)
                } else {
                    // Animation will start only on the next frame,
                    // so apply threshold immediately to avoid delays.
                    val instantDelta = sign(targetValueLeftover) * threshold
                    dispatchMouseWheelScroll(instantDelta)
                    animationState =
                        animationState.copy(value = animationState.value + instantDelta)

                    val durationMillis =
                        (abs(targetValue - animationState.value) / speed)
                            .roundToInt()
                            .coerceAtMost(MaxAnimationDuration)
                    animateMouseWheelScroll(animationState, targetValue, durationMillis) { lastValue
                        ->
                        // Sum delta from all pending events to avoid multiple animation restarts.
                        val nextScrollDelta = channel.sumOrNull()
                        if (nextScrollDelta != null) {
                            trackVelocity(nextScrollDelta)
                            targetScrollDelta += nextScrollDelta
                            targetValue =
                                targetScrollDelta.value
                                    .reverseIfNeeded()
                                    .toSingleAxisDeltaFromAngle()

                            requiredAnimation = !(targetValue - lastValue).isLowScrollingDelta()
                        }
                        nextScrollDelta != null
                    }
                    if (!requiredAnimation) {
                        // If it's completed, wait the next event with timeout before resetting
                        // progress flag
                        requiredAnimation =
                            waitNextScrollDelta(ScrollProgressTimeout - durationMillis)
                    }
                }
            }
        }

        var velocity = velocityTracker.calculateVelocity()
        if (velocity == Velocity.Zero) {
            // In case of single data point use animation speed and delta direction
            val velocityPxInMs = minOf(abs(targetValue) / MaxAnimationDuration, speed)
            velocity = (sign(targetValue).reverseIfNeeded() * velocityPxInMs * 1000).toVelocity()
        }
        onScrollStopped(velocity)
    }

    private suspend fun NestedScrollScope.animateMouseWheelScroll(
        animationState: AnimationState<Float, AnimationVector1D>,
        targetValue: Float,
        durationMillis: Int,
        shouldCancelAnimation: (lastValue: Float) -> Boolean,
    ) {
        var lastValue = animationState.value
        animationState.animateTo(
            targetValue,
            animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
            sequentialAnimation = true,
        ) {
            val delta = value - lastValue
            if (!delta.isLowScrollingDelta()) {
                val consumedDelta = dispatchMouseWheelScroll(delta)
                if (!(delta - consumedDelta).isLowScrollingDelta()) {
                    cancelAnimation()
                    return@animateTo
                }
                lastValue += delta
            }
            if (shouldCancelAnimation(lastValue)) {
                cancelAnimation()
            }
        }
    }

    private fun NestedScrollScope.dispatchMouseWheelScroll(delta: Float) =
        with(scrollingLogic) {
            val offset = delta.reverseIfNeeded().toOffset()
            val consumed = scrollBy(offset, NestedScrollSource.UserInput)
            consumed.reverseIfNeeded().toFloat()
        }
}

/*
 * Returns true, if the value is too low for visible change in scroll (consumed delta, animation-based change, etc),
 * false otherwise
 */
private fun Float.isLowScrollingDelta(): Boolean = isNaN() || abs(this) < 0.5f

private val AnimationThreshold = 6.dp // (AnimationSpeed * MaxAnimationDuration) / (1000ms / 60Hz)
private val AnimationSpeed = 1.dp // dp / ms
private const val MaxAnimationDuration = 100 // ms
private const val ScrollProgressTimeout = 50L // ms

/**
 * Tracks which scrollable owns the mouse wheel.
 *
 * The scrollable that consumed the latest scroll event keeps receiving the following ones, even
 * when a nested scrollable moves under the (unmoved) pointer as the content scrolls. The ownership
 * is released when the pointer moves, the scroll pauses for [ScrollCaptureTimeout], or the owner
 * can't consume the scroll anymore (e.g. it reached its bounds).
 */
private object MouseWheelScrollCaptureManager {
    private class Capture(
        val ownerToken: Any,
        val rootCoordinates: LayoutCoordinates,
        val pointerPositionInWindow: Offset,
        val uptimeMillis: Long,
    )

    private var activeCapture: Capture? = null

    fun capture(ownerToken: Any, layoutCoordinates: LayoutCoordinates?, pointerEvent: PointerEvent) {
        val coordinates = layoutCoordinates?.takeIf { it.isAttached }
        val change = pointerEvent.changes.firstOrNull()
        if (coordinates == null || change == null) {
            release(ownerToken)
            return
        }
        activeCapture =
            Capture(
                ownerToken = ownerToken,
                rootCoordinates = coordinates.findRootCoordinates(),
                pointerPositionInWindow = coordinates.localToWindow(change.position),
                uptimeMillis = change.uptimeMillis,
            )
    }

    /** Whether [ownerToken] owns the mouse wheel for the given scroll [pointerEvent]. */
    fun isOwner(
        ownerToken: Any,
        layoutCoordinates: LayoutCoordinates?,
        pointerEvent: PointerEvent,
    ): Boolean {
        val capture = activeCapture?.takeIf { it.ownerToken === ownerToken } ?: return false
        val uptimeMillis = pointerEvent.changes.firstOrNull()?.uptimeMillis
        val isValid =
            isStillAttached(capture, layoutCoordinates) &&
                uptimeMillis != null &&
                uptimeMillis >= capture.uptimeMillis &&
                uptimeMillis - capture.uptimeMillis <= ScrollCaptureTimeout
        if (!isValid) release(ownerToken)
        return isValid
    }

    /** Releases the ownership of [ownerToken] if the pointer moved away since the last scroll. */
    fun onNonScrollEvent(
        ownerToken: Any,
        layoutCoordinates: LayoutCoordinates?,
        pointerEvent: PointerEvent,
    ) {
        val capture = activeCapture?.takeIf { it.ownerToken === ownerToken } ?: return
        val change = pointerEvent.changes.firstOrNull()
        val isValid =
            isStillAttached(capture, layoutCoordinates) &&
                change != null &&
                (capture.pointerPositionInWindow -
                        layoutCoordinates!!.localToWindow(change.position))
                    .getDistance() <= ScrollCapturePointerSlop
        if (!isValid) release(ownerToken)
    }

    fun release(ownerToken: Any) {
        if (activeCapture?.ownerToken === ownerToken) {
            activeCapture = null
        }
    }

    private fun isStillAttached(capture: Capture, layoutCoordinates: LayoutCoordinates?): Boolean =
        layoutCoordinates != null &&
            layoutCoordinates.isAttached &&
            layoutCoordinates.findRootCoordinates() === capture.rootCoordinates
}

private const val ScrollCaptureTimeout = 300L // ms
private const val ScrollCapturePointerSlop = 5f // px
