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

package androidx.compose.foundation.gestures

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** A shared base class for [TrackpadScrollingLogicImpl] and [MouseWheelScrollingLogicImpl]. */
internal abstract class NonTouchScrollingLogic(
    protected val scrollLogic: ScrollLogic,
    protected val onScrollStopped: suspend (velocity: Velocity) -> Unit,
    protected var density: Density,
) {
    fun updateDensity(density: Density) {
        this.density = density
    }

    internal inline val PointerEvent.isConsumed: Boolean
        get() = changes.fastAny { it.isConsumed }

    internal fun PointerEvent.consume() = changes.fastForEach { it.consume() }

    internal var isScrolling = false

    internal suspend fun userScroll(block: suspend NestedScrollScope.() -> Unit) {
        isScrolling = true
        // Run it in supervisorScope to ignore cancellations from scrolls with higher MutatePriority
        supervisorScope { scrollLogic.scroll(MutatePriority.UserInput, block) }
        isScrolling = false
    }

    internal val velocityTracker = DifferentialVelocityTracker()

    protected abstract fun isScrollingEvent(pointerEvent: PointerEvent): Boolean

    protected abstract fun onScrollingEvent(pointerEvent: PointerEvent, bounds: IntSize): Boolean

    fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pointerEvent.isConsumed) return
        if (!isScrollingEvent(pointerEvent)) return

        // If this scrollable is already scrolling from a previous interaction, consume immediately
        // to give it priority.
        if (pass == PointerEventPass.Initial && isScrolling) {
            onScrollingEvent(pointerEvent, bounds)
            pointerEvent.consume()
        }

        // During the main pass. If this scrollable is not scrolling, decide whether it should be
        // based on whether the event was consumed. If the scrollable is scrolling, we don't need
        // to worry because it was consumed during the initial pass.
        if (pass == PointerEventPass.Main && !isScrolling) {
            val consumed = onScrollingEvent(pointerEvent, bounds)
            if (consumed) {
                pointerEvent.consume()
            }
        }
    }

    /** Begins processing of events sent to [onPointerEvent] using the given [coroutineScope]. */
    abstract fun startReceivingEvents(coroutineScope: CoroutineScope)
}

/**
 * Replacement of regular [Channel.receive] that schedules an invalidation each frame. It avoids
 * entering an idle state while waiting for [ScrollProgressTimeout]. It's important for tests that
 * attempt to trigger another scroll after a mouse wheel event.
 */
internal suspend fun <T> Channel<T>.busyReceive(): T = coroutineScope {
    val job = launch {
        while (coroutineContext.isActive) {
            withFrameNanos {}
        }
    }
    try {
        receive()
    } finally {
        job.cancel()
    }
}

internal fun <E> untilNull(builderAction: () -> E?) =
    sequence<E> {
        do {
            val element = builderAction()?.also { yield(it) }
        } while (element != null)
    }

internal fun ScrollingLogic.canConsumeDelta(delta: Float): Boolean {
    val directionalDelta = delta.reverseIfNeeded()
    return when {
        directionalDelta < 0f -> scrollableState.canScrollBackward
        directionalDelta > 0f -> scrollableState.canScrollForward
        // Nothing to scroll on our axis; let something else handle the other axis.
        else -> false
    }
}

/**
 * Adapter between [Offset] and the value being changed during scrolling.
 *
 * Either [OneDimensionalScrollValueAdapter] or [TwoDimensionalScrollValueAdapter].
 */
internal interface ScrollValueAdapter<T> {
    fun T.toOffset(): Offset

    fun T.toVelocity(): Velocity

    fun Offset.toScrollValue(): T

    /** A scrollable value of size 1, in the same direction as `this` */
    fun T.normalize(): T

    /** The magnitude of the scrollable value, in pixels; a non-negative value */
    fun T.size(): Float

    operator fun T.times(scale: Float): T

    operator fun T.plus(value: T): T

    operator fun T.minus(value: T): T

    fun newAnimationState(): AnimationState<T, *>

    /**
     * Returns whether the value is too low for visible change in scroll (consumed delta,
     * animation-based change, etc.)
     */
    fun T.isLowScrollingDelta(): Boolean
}

/**
 * [ScrollValueAdapter] for one-dimensional scrolling, where the scrollable value is a [Float].
 *
 * The axis ([isVertical]) is passed in as a lambda to avoid having to update it manually.
 */
internal class OneDimensionalScrollValueAdapter(
    val isVertical: () -> Boolean,
) : ScrollValueAdapter<Float> {

    /**
     * Converts this offset to a single axis delta based on the derived angle from the x and y
     * deltas.
     *
     * @return Returns a single axis delta based on the angle. If the angle is mostly horizontal,
     *   and we are in a horizontal scrollable, this will return the x component. If the angle is
     *   mostly vertical, and we are in a vertical scrollable, this will return the y component.
     *   Otherwise, this will return 0.
     */
    fun Offset.toSingleAxisDeltaFromAngle(): Float {
        val isVertical = isVertical()
        return if (abs(y) >= abs(x)) {
            if (isVertical) this.y else 0f
        } else {
            if (!isVertical) this.x else 0f
        }
    }

    override fun Float.toOffset() =
        (if (isVertical()) Offset(0f, this) else Offset(this, 0f))

    override fun Offset.toScrollValue() = toSingleAxisDeltaFromAngle()

    override fun Float.toVelocity() =
        when {
            this == 0f -> Velocity.Zero
            isVertical() -> Velocity(0f, this)
            else -> Velocity(this, 0f)
        }

    override fun Float.normalize() = sign(this)

    override fun Float.size() = abs(this)

    override fun Float.times(scale: Float) = this * scale

    override fun Float.plus(value: Float) = this + value

    override fun Float.minus(value: Float) = this - value

    override fun newAnimationState() = AnimationState(0f)

    override fun Float.isLowScrollingDelta() = abs(this) < 0.5f
}

/**
 * [ScrollValueAdapter] for two-dimensional scrolling, where the scrollable value is an [Offset].
 */
internal object TwoDimensionalScrollValueAdapter : ScrollValueAdapter<Offset> {
    override fun Offset.toOffset() = this

    override fun Offset.toScrollValue() = this

    override fun Offset.toVelocity() =
        when {
            this == Offset.Zero -> Velocity.Zero
            else -> Velocity(x, y)
        }

    override fun Offset.normalize() =
        if ((this.x == 0f) && (this.y == 0f)) Offset.Zero else this / getDistance()

    override fun Offset.size() = this.getDistance()

    override fun Offset.times(scale: Float) = this * scale

    override fun Offset.plus(value: Offset) = this + value

    override fun Offset.minus(value: Offset) = this - value

    override fun newAnimationState() =
        AnimationState(Offset.VectorConverter, Offset.Zero, Offset.Zero)

    override fun Offset.isLowScrollingDelta() = (abs(x) < 0.5f) && (abs(y) < 0.5f)
}
