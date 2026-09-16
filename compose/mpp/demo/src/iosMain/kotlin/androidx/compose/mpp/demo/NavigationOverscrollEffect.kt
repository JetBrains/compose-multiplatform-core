/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.mpp.demo

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.uikit.LocalTaskScheduleProvider
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import platform.CoreGraphics.CGFloat
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSDirectionalRectEdgeAll
import platform.UIKit.NSDirectionalRectEdgeTop
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UIScrollViewDecelerationRateNormal
import platform.UIKit.UIScrollViewDelegateProtocol
import platform.UIKit.UIView

private enum class NavigationScrollSource {
    DRAG, FLING
}

private enum class NavigationSpringAnimationReason {
    FLING_FROM_OVERSCROLL, POSSIBLE_SPRING_IN_THE_END
}

/*
 * Encapsulates internal calculation data representing per-dimension change after drag delta is consumed (or not)
 * by [NavigationOverscrollEffect]
 */
private data class NavigationOverscrollAvailableDelta(
    // delta which will be used to perform actual content scroll
    val availableDelta: Float,

    // new overscroll value for dimension in context of which calculation returning
    // instance of this type was returned
    val newOverscrollValue: Float
)

internal class NavigationOverscrollEffect(
    private val density: Density,
    private val onScrollToTop: () -> Unit,
) : OverscrollEffect, FlingBehavior {
    /*
     * Size of container is taking into consideration when computing rubber banding
     */
    private var scrollSize: Size = Size.Zero

    /*
     * Current vertical offset in overscroll area
     * Negative for bottom
     * Positive for top
     * Zero if within the scrollable range
     * It will be mapped to the actual visible offset using the rubber banding rule inside
     * [Modifier.offset] within [effectModifier]
     */
    private var overscrollOffsetState = mutableStateOf(0f)
    private var overscrollOffset: Float
        get () = overscrollOffsetState.value
        set(value) {
            overscrollOffsetState.value = value
            drawCallScheduledByOffsetChange = true

            overscrollNode.updateAdjustedOffset((-visibleOverscrollOffset / density.density).toDouble(), force = !insetsAdjusted)
        }

    /*
     * Edges in pixels where the actual overscroll starts, i.e. where the content starts to meet
     * resistance and bounces back from.
     * The maximum observed insets are used instead of the current ones, because the latter change
     * while the navigation bar is collapsing/expanding, which would move the edge during
     * a single fling or spring animation
     */
    private var topMaxSafeInset: Float = 0f
    private var bottonMaxSafeInset: Float = 0f

    private var insetsAdjusted: Boolean = false

    private fun adjustMaxInsets() {
        insetsAdjusted = true
        topMaxSafeInset = max(topMaxSafeInset, currentTopSafeInsets)
        bottonMaxSafeInset = max(bottonMaxSafeInset, currentBottomSafeInsets)
    }

    private fun resetMaxInsets() {
        // insetsAdjusted = false
        topMaxSafeInset = 0f
        bottonMaxSafeInset = 0f
        topMaxSafeInset = max(topMaxSafeInset, currentTopSafeInsets)
        bottonMaxSafeInset = max(bottonMaxSafeInset, currentBottomSafeInsets)
    }

    /*
     * Safe area insets in pixels. Overscroll within these insets is not rubber banded.
     */
    private val currentTopSafeInsets: Float
        get() = overscrollNode.scrollView.safeAreaInsets.useContents { top.toFloat() } * density.density
    private val currentBottomSafeInsets: Float
        get() = overscrollNode.scrollView.safeAreaInsets.useContents { bottom.toFloat() } * density.density

    private val flingDecaySpec: DecayAnimationSpec<Float> =
        CupertinoScrollDecaySpec().generateDecayAnimationSpec()

    private var drawCallScheduledByOffsetChange = true


    private var lastFlingUnconsumedDelta: Float = 0f
    private val visibleOverscrollOffset: Float
        get() = overscrollOffset.rubberBandedBeyondSafeInsets()

    override val isInProgress: Boolean
        get() =
            // If visible overscroll offset has at least one pixel
            // this effect is considered to be in progress
            abs(visibleOverscrollOffset) > 0.5f

    private val overscrollNode = NavigationOverscrollNode(
        offset = { IntOffset(0, visibleOverscrollOffset.roundToInt()) },
        onNodeRemeasured = {
            scrollSize = it.toSize()
            if (!insetsAdjusted) {
                resetMaxInsets()
                overscrollOffset = max(topMaxSafeInset, currentTopSafeInsets)
            }
       },
        // Safe area insets of a node with a changed size or position on screen have nothing to do
        // with the previously observed ones
        onNodeGeometryChanged = {
            resetMaxInsets()
        },
        onDraw = ::onDraw,
        onScrollToTop = onScrollToTop
    )
    override val node: DelegatableNode get() = overscrollNode

    private fun onDraw() {
        // Fix an issue where scrolling was cancelled but the overscroll effect was not completed.
        // Reset the overscroll effect when no ongoing animation or interaction is applied.
        if (!drawCallScheduledByOffsetChange && isInProgress && overscrollNode.pointersDown == 0) {
            overscrollOffsetState.value = overscrollOffsetState.value.restingOverscrollOffset()
        }

        drawCallScheduledByOffsetChange = false
    }

    private fun NestedScrollSource.toNavigationScrollSource(): NavigationScrollSource? =
        when (this) {
            NestedScrollSource.UserInput -> NavigationScrollSource.DRAG
            NestedScrollSource.SideEffect -> NavigationScrollSource.FLING
            else -> null
        }

    /*
     * Takes input scroll delta, current overscroll value, and scroll source, return [NavigationOverscrollAvailableDelta]
     */
    @Stable
    private fun availableDelta(
        delta: Float,
        overscroll: Float,
        source: NavigationScrollSource
    ): NavigationOverscrollAvailableDelta {
        val newOverscroll = overscroll + delta

        return if (delta >= 0f && overscroll <= 0f) {
            if (newOverscroll > 0f) {
                NavigationOverscrollAvailableDelta(newOverscroll, 0f)
            } else {
                NavigationOverscrollAvailableDelta(0f, newOverscroll)
            }
        } else if (delta <= 0f && overscroll >= 0f) {
            if (newOverscroll < 0f) {
                NavigationOverscrollAvailableDelta(newOverscroll, 0f)
            } else {
                NavigationOverscrollAvailableDelta(0f, newOverscroll)
            }
        } else if (source == NavigationScrollSource.FLING) {
            // The delta goes in the same direction as the current overscroll.
            // A fling is not allowed to grow the overscroll on its own: the delta is offered to the
            // content first and only what is left of it goes into the overscroll area
            // within the safe area insets (see [applyToScroll])
            NavigationOverscrollAvailableDelta(delta, overscroll)
        } else {
            NavigationOverscrollAvailableDelta(0f, newOverscroll)
        }
    }

    /*
     * Returns the amount of scroll delta available after user performed scroll inside overscroll area
     * It will update [overscroll] resulting in visual change because of [Modifier.offset] depending on it
     */
    private fun availableDelta(delta: Offset, source: NavigationScrollSource): Offset {
        val (y, overscrollY) = availableDelta(delta.y, overscrollOffset, source)

        overscrollOffset = overscrollY

        return Offset(delta.x, y)
    }

    /*
     * Semantics of this method match the [OverscrollEffect.applyToScroll] one,
     * The only difference is NestedScrollSource being remapped to NavigationScrollSource to narrow
     * processed states invariant
     */
    private fun applyToScroll(
        delta: Offset,
        source: NavigationScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        // Calculate how much delta is available after being consumed by scrolling inside overscroll area
        val deltaLeftForPerformScroll = availableDelta(delta, source)

        // Then pass remaining delta to scroll closure
        val deltaConsumedByPerformScroll = performScroll(deltaLeftForPerformScroll)

        // Delta which is left after `performScroll` was invoked with availableDelta
        val unconsumedDelta = deltaLeftForPerformScroll - deltaConsumedByPerformScroll

        return when (source) {
            NavigationScrollSource.DRAG -> {
                // [unconsumedDelta] is going into overscroll again in case a user drags and hits the
                // overscroll->content->overscroll or content->overscroll scenario within single frame
                overscrollOffset += unconsumedDelta.y
                lastFlingUnconsumedDelta = 0f
                delta - unconsumedDelta
            }

            NavigationScrollSource.FLING -> {
                // Within the safe area insets the fling meets no resistance: [unconsumedDelta] moves
                // the overscroll offset and is reported back as consumed, so the deceleration
                // continues seamlessly.
                // If something is left after that, the actual overscroll starts: it is reported as
                // unconsumed to stop the fling, and [applyToFling] plays the spring animation instead
                val deltaWithinSafeInsets = unconsumedDelta.y.limitedBySafeInsets()
                overscrollOffset += deltaWithinSafeInsets
                lastFlingUnconsumedDelta = unconsumedDelta.y - deltaWithinSafeInsets

                delta - Offset(unconsumedDelta.x, lastFlingUnconsumedDelta)
            }
        }
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        adjustMaxInsets()

        springAnimationScope?.cancel()
        springAnimationScope = null

        return source.toNavigationScrollSource()?.let {
            applyToScroll(delta, it, performScroll)
        } ?: performScroll(delta)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        adjustMaxInsets()

        val availableFlingVelocity = playInitialSpringAnimationIfNeeded(velocity)
        val velocityConsumedByFling = performFling(availableFlingVelocity)
        val postFlingVelocity = availableFlingVelocity - velocityConsumedByFling

        val unconsumedDelta = lastFlingUnconsumedDelta
        if (unconsumedDelta == 0f && overscrollOffset.overscrollBeyondSafeInsets == 0f) {
            return
        }

        playSpringAnimation(
            unconsumedDelta,
            postFlingVelocity.toFloat(),
            NavigationSpringAnimationReason.POSSIBLE_SPRING_IN_THE_END
        )
    }

    private fun Velocity.toFloat(): Float = y

    private fun Float.toVelocity(): Velocity = Velocity(0f, this)

    private suspend fun playInitialSpringAnimationIfNeeded(initialVelocity: Velocity): Velocity {
        val velocity = initialVelocity.toFloat()
        val overscroll = overscrollOffset.overscrollBeyondSafeInsets

        return if ((velocity <= 0f && overscroll > 0f) || (velocity >= 0f && overscroll < 0f)) {
            playSpringAnimation(
                unconsumedDelta = 0f,
                velocity,
                NavigationSpringAnimationReason.FLING_FROM_OVERSCROLL
            ).toVelocity()
        } else {
            initialVelocity
        }
    }

    private var springAnimationScope: CoroutineScope? = null

    private suspend fun playSpringAnimation(
        unconsumedDelta: Float,
        initialVelocity: Float,
        reason: NavigationSpringAnimationReason
    ): Float {
        val initialValue = overscrollOffset + unconsumedDelta
        val targetValue = initialValue.restingOverscrollOffset()
        val initialSign = sign(initialValue - targetValue)
        var currentVelocity = initialVelocity

        // All input values are divided by density so all internal calculations are performed as if
        // they operated on DPs. Callback value is then scaled back to raw pixels.
        val visibilityThreshold = 0.5f / density.density

        val spec = when (reason) {
            NavigationSpringAnimationReason.FLING_FROM_OVERSCROLL -> {
                spring(
                    stiffness = 300f,
                    visibilityThreshold = visibilityThreshold
                )
            }

            NavigationSpringAnimationReason.POSSIBLE_SPRING_IN_THE_END -> {
                spring(
                    stiffness = 120f,
                    visibilityThreshold = visibilityThreshold
                )
            }
        }

        val targetDpValue = targetValue / density.density

        springAnimationScope?.cancel()
        springAnimationScope = CoroutineScope(currentCoroutineContext())
        springAnimationScope?.run {
            AnimationState(
                Float.VectorConverter,
                initialValue / density.density,
                initialVelocity / density.density
            ).animateTo(
                targetValue = targetDpValue,
                animationSpec = spec
            ) {
                overscrollOffset = value * density.density
                currentVelocity = velocity * density.density

                // If it was fling from overscroll, cancel animation and return velocity
                if (reason == NavigationSpringAnimationReason.FLING_FROM_OVERSCROLL &&
                    initialSign != 0f &&
                    sign(value - targetDpValue) != initialSign
                ) {
                    this.cancelAnimation()
                }
            }
            springAnimationScope = null
        }

        if (currentCoroutineContext().isActive) {
            // The spring is critically damped, so in case spring-fling-spring sequence is slightly
            // offset and velocity is of the opposite sign, it will end up with no animation
            overscrollOffset = targetValue
        }

        if (reason == NavigationSpringAnimationReason.POSSIBLE_SPRING_IN_THE_END) {
            currentVelocity = 0f
        }

        return currentVelocity
    }

    /*
     * Repeats the default iOS fling behavior (see CupertinoFlingBehavior, which is internal to the
     * foundation module) and relies on [applyToScroll] to keep the deceleration going while the
     * overscroll offset is still within the safe area insets
     */
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) < FLING_VELOCITY_THRESHOLD) {
            return 0f
        }

        var velocityLeft = initialVelocity
        var lastValue = 0f

        AnimationState(
            initialValue = 0f,
            initialVelocity = initialVelocity
        ).animateDecay(flingDecaySpec) {
            adjustMaxInsets()
            
            val delta = value - lastValue
            val consumed = try {
                scrollBy(delta)
            } catch (_: CancellationException) {
                0f
            }
            lastValue = value
            velocityLeft = this.velocity

            // Avoid rounding errors and stop if anything is unconsumed, i.e. the actual overscroll
            // has started and the spring animation has to take the rest of the velocity over
            if (abs(delta - consumed) > 0.5f) {
                cancelAnimation()
            }
        }

        return velocityLeft
    }

    /*
     * Overscroll starts beyond the safe area insets, so any offset within
     * [-bottonMaxSafeInset, topMaxSafeInset] range is a valid resting position:
     * it doesn't meet resistance and doesn't spring back.
     */
    private fun Float.restingOverscrollOffset(): Float =
        coerceIn(-bottonMaxSafeInset, topMaxSafeInset)

    /*
     * The part of the offset which is an actual overscroll, i.e. the one beyond the safe area insets
     */
    private val Float.overscrollBeyondSafeInsets: Float
        get() = this - restingOverscrollOffset()

    /*
     * The part of this delta which still fits into the safe area insets when applied to the current
     * [overscrollOffset]. Never moves the offset backwards or beyond the insets
     */
    private fun Float.limitedBySafeInsets(): Float = when {
        this > 0f -> (topMaxSafeInset - overscrollOffset).coerceIn(0f, this)
        this < 0f -> (-bottonMaxSafeInset - overscrollOffset).coerceIn(this, 0f)
        else -> 0f
    }

    /*
     * Rubber bands only the part of the offset that goes beyond the safe area insets
     */
    private fun Float.rubberBandedBeyondSafeInsets(): Float {
        if (scrollSize.height == 0f) {
            return 0f
        }

        return restingOverscrollOffset() + overscrollBeyondSafeInsets.rubberBanded()
    }

    private fun Float.rubberBanded(): Float {
        val dpValue = this / density.density
        val dpHeight = scrollSize.height / density.density
        return rubberBandedValue(dpValue, dpHeight, RUBBER_BAND_COEFFICIENT) * density.density
    }

    /*
     * Maps raw delta offset [value] on an axis within scroll container with [dimension]
     * to actual visible offset
     */
    private fun rubberBandedValue(value: Float, dimension: Float, coefficient: Float) =
        sign(value) * (1f - (1f / (abs(value) * coefficient / dimension + 1f))) * dimension

    companion object Companion {
        private const val RUBBER_BAND_COEFFICIENT = 0.55f

        /*
         * Post-drag inertia with velocity below this value will be consumed entirely and not trigger
         * any fling at all, value is approx and reverse-engineered from iOS 16 UIScrollView blackbox
         */
        private const val FLING_VELOCITY_THRESHOLD = 500f
    }
}

/*
 * iOS-style scroll deceleration, a copy of [CupertinoScrollDecayAnimationSpec] which is internal
 * to the foundation module
 *
 * @property decelerationRate The rate at which the velocity decelerates over time.
 * Default value is equal to one used by default UIScrollView behavior.
 */
private class CupertinoScrollDecaySpec(
    private val decelerationRate: Float = UIScrollViewDecelerationRateNormal.toFloat()
) : FloatDecayAnimationSpec {
    private val coefficient: Float = 1000f * ln(decelerationRate)

    override val absVelocityThreshold: Float = 0.5f // Half pixel

    override fun getTargetValue(initialValue: Float, initialVelocity: Float): Float =
        initialValue - initialVelocity / coefficient

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        initialVelocity: Float
    ): Float {
        val playTimeSeconds = playTimeNanos.nanosToSeconds()
        val initialVelocityOverTimeIntegral =
            (decelerationRate.pow(1000f * playTimeSeconds) - 1f) / coefficient * initialVelocity
        return initialValue + initialVelocityOverTimeIntegral
    }

    override fun getDurationNanos(initialValue: Float, initialVelocity: Float): Long {
        val absVelocity = abs(initialVelocity)

        if (absVelocity < absVelocityThreshold) {
            return 0
        }

        val seconds = ln(-coefficient * absVelocityThreshold / absVelocity) / coefficient

        return seconds.secondsToNanos()
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        initialVelocity: Float
    ): Float = initialVelocity * decelerationRate.pow(1000f * playTimeNanos.nanosToSeconds())
}

private const val SecondsToNanos: Long = 1_000_000_000L

private fun Float.secondsToNanos(): Long = (toDouble() * SecondsToNanos).roundToLong()

private fun Long.nanosToSeconds(): Float = (toDouble() / SecondsToNanos).toFloat()

private class NavigationOverscrollNode(
    val offset: Density.() -> IntOffset,
    val onNodeRemeasured: (IntSize) -> Unit,
    val onNodeGeometryChanged: () -> Unit,
    val onDraw: () -> Unit,
    onScrollToTop: () -> Unit,
) : Modifier.Node(),
    LayoutModifierNode,
    LayoutAwareModifierNode,
    GlobalPositionAwareModifierNode,
    DrawModifierNode,
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode {
    private var lastSize: IntSize? = null
    private var lastPosition: Offset? = null

    override fun onRemeasured(size: IntSize) {
        onNodeRemeasured(size)
        updateGeometry(size, lastPosition)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val positionOnScreen = coordinates.positionOnScreen()
        updateGeometry(
            size = coordinates.size,
            position = if (positionOnScreen.isSpecified) {
                positionOnScreen
            } else {
                coordinates.positionInWindow()
            }
        )
    }

    private fun updateGeometry(size: IntSize, position: Offset?) {
        if (size == lastSize && position == lastPosition) {
            return
        }

        lastSize = size
        lastPosition = position
        onNodeGeometryChanged()
    }

    val scrollView = CustomScrollView(onScrollToTop)

    private var detachCallback = {}

    var pointersDown by mutableStateOf(0)

    private var offsetValue by mutableStateOf(0.0)
    fun applyContentOffset() {
        if (scrollView.respondsToSelector(NSSelectorFromString("_scrollViewWillBeginDragging"))) {
            scrollView.performSelector(NSSelectorFromString("_scrollViewWillBeginDragging"))
        }

        //val topOffset = scrollView.safeAreaInsets.useContents { top }

        scrollView.contentOffset = CGPointMake(0.0, offsetValue)
    }

    @OptIn(InternalComposeUiApi::class)
    fun updateAdjustedOffset(value: CGFloat, force: Boolean) {
        offsetValue = value
        if (force) {
            applyContentOffset()
        } else {
            currentValueOf(LocalTaskScheduleProvider)!!.scheduleTask {
                applyContentOffset()
            }
        }
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        if (pass == PointerEventPass.Initial) {
            pointerEvent.changes.forEach { change ->
                if (change.changedToDownIgnoreConsumed()) {
                    pointersDown++
                } else if (change.changedToUpIgnoreConsumed()) {
                    pointersDown--
                }
            }
            assert(pointersDown >= 0) { "pointersDown cannot be negative" }
        }
    }

    override fun onCancelPointerInput() {
        pointersDown = 0
    }

    override fun ContentDrawScope.draw() {
        onDraw()
        val bounds = Rect(-offset().toOffset(), size)
        val rect = size.toRect().intersect(bounds)
        clipRect(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
        ) { this@draw.drawContent() }
    }

//    private val Density.safeAreaTopOffsetInPx: Int get() = (maxSafeAreaOffset * density).roundToInt()

    @OptIn(InternalComposeUiApi::class)
    override fun onObservedReadsChanged() {
        observeReads {
            val viewController = currentValueOf(LocalUIViewController)
            scrollView.removeFromSuperview()
            viewController.view.embedSubview(scrollView)
            viewController.setContentScrollView(scrollView, forEdge = NSDirectionalRectEdgeAll)
        }
    }

    override fun onAttach() {
        super.onAttach()

        val viewController = currentValueOf(LocalUIViewController)
        scrollView.removeFromSuperview()
        detachCallback()
        viewController.view.embedSubview(scrollView)
        viewController.setContentScrollView(scrollView, forEdge = NSDirectionalRectEdgeAll)

        val weakRef = WeakReference(viewController)
        detachCallback = {
            weakRef.get()?.setContentScrollView(null, forEdge = NSDirectionalRectEdgeAll)
        }
    }

    override fun onDetach() {
        super.onDetach()

        scrollView.removeFromSuperview()
        detachCallback()
        detachCallback = {}
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(offset())
        }
    }
}

private fun UIView.embedSubview(subview: UIView) {
    addSubview(subview)
    subview.addLayoutConstraintsToMatch(this)
}

private fun UIView.addLayoutConstraintsToMatch(other: UIView) {
    translatesAutoresizingMaskIntoConstraints = false
    listOf(
        leftAnchor.constraintEqualToAnchor(other.leftAnchor),
        rightAnchor.constraintEqualToAnchor(other.rightAnchor),
        topAnchor.constraintEqualToAnchor(other.topAnchor),
        bottomAnchor.constraintEqualToAnchor(other.bottomAnchor)
    ).also {
        NSLayoutConstraint.activateConstraints(it)
    }
}

class CustomScrollView(val onScrollToTop: () -> Unit): UIScrollView(frame = CGRectZero.readValue()), UIScrollViewDelegateProtocol {
    init {
        userInteractionEnabled = false
        showsVerticalScrollIndicator = false
        contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentAlways
    }

    override fun isTracking(): Boolean {
        return true
    }

    override fun isDecelerating(): Boolean {
        return true
    }
//    override var isTracking: Bool { true }
//    override var isDragging: Bool { true }

    override fun layoutSubviews() {
        super.layoutSubviews()
        updateContentSize()
    }

    private fun updateContentSize() {
        val topBottomInsets = safeAreaInsets.useContents { top to bottom }
        val width = CGRectGetWidth(bounds)
        val height = CGRectGetHeight(bounds)

        setContentSize(CGSizeMake(width, 1000000.0))

        setContentSize(
            CGSizeMake(
                width,
                height * 1000000.0 + max(topBottomInsets.first, topBottomInsets.second)
            )
        )
    }

    override fun scrollsToTop(): Boolean {
        return true
    }

    override fun scrollViewShouldScrollToTop(scrollView: UIScrollView): Boolean {
        onScrollToTop()
        return scrollsToTop()
    }

    override fun safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()

        updateContentSize()
    }
}

