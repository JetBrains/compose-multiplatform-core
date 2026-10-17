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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGFloat
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSNumber
import platform.Foundation.NSSelectorFromString
import platform.Foundation.numberWithBool
import platform.UIKit.NSDirectionalRectEdgeAll
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIEvent
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UIScrollViewDecelerationRateNormal
import platform.UIKit.UIScrollViewDelegateProtocol
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The extra height of the [NavigationProxyScrollView] content beyond the height of the view itself,
 * in points. It defines the range of the content offset which can be reported to UIKit, see
 * `NavigationOverscrollEffect.approximateContentOffset`.
 */
private const val EXTRA_SCROLLABLE_HEIGHT: CGFloat = 100_000.0

/** Stiffness of the spring which pulls the content back from the overscroll area. */
private const val SPRING_BACK_STIFFNESS = 300f

/** Stiffness of the softer spring which settles the content once a fling is over. */
private const val SETTLE_SPRING_STIFFNESS = 120f

/** Resistance of the rubber banding, the same value the Cupertino overscroll effect uses. */
private const val RUBBER_BAND_COEFFICIENT = 0.55f

/**
 * Post-drag inertia with a velocity below this value is consumed entirely and doesn't trigger any
 * fling at all. The value is approximate and reverse engineered from the iOS 16 `UIScrollView`.
 */
private const val FLING_VELOCITY_THRESHOLD = 500f

/** Half a pixel: offsets and velocities below that are not worth animating or reacting to. */
private const val HALF_PIXEL = 0.5f

private const val NANOS_PER_SECOND: Long = 1_000_000_000L

/**
 * An iOS specific [OverscrollEffect] which keeps the UIKit navigation chrome around a Compose
 * scrollable - a large navigation title and a minimizable tab bar - in sync with the content.
 *
 * UIKit drives that chrome from a `UIScrollView` the hosting view controller is told about via
 * `setContentScrollView`: the large title collapses as the content offset of that scroll view
 * grows, and the title area stretches while it bounces past its top edge. A Compose scrollable is
 * not a `UIScrollView`, so this effect creates an empty non-interactive one
 * ([NavigationProxyScrollView]), registers it with the hosting view controller and mirrors the
 * Compose scroll into its content offset.
 *
 * On top of that it reproduces the native overscroll feel:
 * - any offset within the safe area insets is a valid resting position, so the content can stay
 *   pulled down with the large title fully expanded, meeting no resistance and not springing back;
 * - beyond the insets the offset is rubber banded and springs back, the way the Cupertino
 *   overscroll effect of the foundation module does it;
 * - a status bar tap brings both the content and the overscroll area back to the very top.
 *
 * The effect is vertical only, and has to be passed to the scrollable both as an overscroll effect
 * and as a [FlingBehavior], because the fling has to cooperate with the extended resting range:
 *
 * ```
 * val effect = remember(density, state) { NavigationOverscrollEffect(density, state) }
 * LazyColumn(state = state, overscrollEffect = effect, flingBehavior = effect)
 * ```
 *
 * @param density used to convert between the raw pixels the scroll machinery operates on and the
 *   points UIKit and the Cupertino formulas are defined in.
 * @param scrollableState the state of the scrollable this effect is attached to. It is only needed
 *   to animate the content on a status bar tap; without it such a tap only resets the overscroll
 *   area.
 */
internal class NavigationOverscrollEffect(
    private val density: Density,
    private val scrollableState: ScrollableState? = null,
) : OverscrollEffect, FlingBehavior {

    /** Where the delta being distributed comes from. */
    private enum class ScrollSource {
        /** A finger currently dragging the content. */
        DRAG,

        /** An ongoing fling, see [performFling]. */
        FLING,
    }

    /** Why a spring animation is being played, which defines its stiffness and its result. */
    private enum class SpringReason {
        /**
         * A fling has started while the content was already in the overscroll area: the spring
         * pulls the content back to the edge and hands the leftover velocity over to the fling.
         */
        FLING_FROM_OVERSCROLL,

        /**
         * A fling is over, either because it ran out of velocity or because it hit the overscroll
         * area: the spring settles the content at its resting offset and consumes the velocity.
         */
        SETTLE_AFTER_FLING,
    }

    /** Size of the scrollable, taken into account when computing the rubber banding. */
    private var scrollSize: Size = Size.Zero

    /**
     * Current vertical offset of the content in the overscroll area, in pixels: positive above the
     * top edge, negative below the bottom one, zero within the scrollable range.
     *
     * Everything within the safe area insets is a resting position (see [restingOverscrollOffset]),
     * while the part beyond them is rubber banded into [visibleOverscrollOffset], which is the
     * offset the content is actually drawn at.
     */
    private val overscrollOffsetState = mutableStateOf(0f)
    private var overscrollOffset: Float
        get() = overscrollOffsetState.value
        set(value) {
            overscrollOffsetState.value = value
            isDrawScheduledByOffsetChange = true

            // A non-zero overscroll means the content has refused to scroll any further, so the
            // edge it rests against is known and the approximation can be snapped to it
            if (value != 0f) {
                snapApproximateContentOffsetToEdge(value)
            }
            syncProxyScrollViewOffset()
        }

    /** [overscrollOffset] mapped to the offset the content is actually drawn at. */
    private val visibleOverscrollOffset: Float
        get() = overscrollOffset.rubberBandedBeyondSafeInsets()

    /**
     * Whether the draw pass which is about to happen was scheduled by an [overscrollOffset] change.
     * A draw pass which wasn't means that nothing drives the offset anymore, see [onDraw].
     */
    private var isDrawScheduledByOffsetChange = true

    /**
     * The part of the last fling delta which fit neither into the content nor into the safe area
     * insets, i.e. the delta which has to be turned into an actual overscroll by [applyToFling].
     */
    private var lastFlingUnconsumedDelta = 0f

    /**
     * The largest safe area insets observed while the scrollable keeps its current geometry, in
     * pixels. They define the resting range of [overscrollOffset]: the insets shrink while the
     * large title collapses, and the content has to be able to rest against the expanded one.
     */
    private var maxTopSafeInset = 0f
    private var maxBottomSafeInset = 0f

    /**
     * Whether the scrollable has been scrolled at least once. Until it has, every remeasure parks
     * the content at the top resting offset, i.e. with the large title fully expanded.
     */
    private var hasScrolled = false

    /** Safe area insets of the hosting view controller, in pixels. */
    private val currentTopSafeInset: Float
        get() = overscrollNode.topSafeInset.toFloat().inPixels
    private val currentBottomSafeInset: Float
        get() = overscrollNode.bottomSafeInset.toFloat().inPixels

    private val flingDecaySpec: DecayAnimationSpec<Float> =
        CupertinoScrollDecaySpec().generateDecayAnimationSpec()

    /** The scope of the spring which currently animates [overscrollOffset], if any. */
    private var springAnimationScope: CoroutineScope? = null

    /** The two animations a status bar tap starts, see [scrollToTop]. */
    private var restingOffsetAnimationJob: Job? = null
    private var scrollToTopJob: Job? = null

    private val overscrollNode = NavigationOverscrollNode(
        offset = { IntOffset(0, visibleOverscrollOffset.roundToInt()) },
        onNodeRemeasured = { size ->
            scrollSize = size.toSize()
            if (!hasScrolled) {
                resetMaxSafeInsets()
                overscrollOffset = maxTopSafeInset
            }
        },
        // Safe area insets observed at another size or position on screen have nothing to do with
        // the ones the content rests against now
        onNodeGeometryChanged = ::resetMaxSafeInsets,
        onDraw = ::onDraw,
        onScrollToTop = ::scrollToTop,
    )

    override val node: DelegatableNode
        get() = overscrollNode

    override val isInProgress: Boolean
        // The effect is in progress as long as it visibly offsets the content
        get() = abs(visibleOverscrollOffset) > HALF_PIXEL

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        onScrollActivity()
        cancelOffsetAnimations()

        val scrollSource = when (source) {
            NestedScrollSource.UserInput -> ScrollSource.DRAG
            NestedScrollSource.SideEffect -> ScrollSource.FLING
            else -> return performScroll(delta)
        }
        return applyToScroll(delta, scrollSource, performScroll)
    }

    private fun applyToScroll(
        delta: Offset,
        source: ScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        // The overscroll area takes its share of the delta first
        val deltaLeftForContent = Offset(delta.x, consumeIntoOverscroll(delta.y, source))

        val deltaConsumedByContent = performScroll(deltaLeftForContent)
        val unconsumedDelta = deltaLeftForContent - deltaConsumedByContent

        // The content has moved by the consumed delta, so the approximation follows it
        onContentScrolled(
            consumedDelta = deltaConsumedByContent.y,
            unconsumedDelta = unconsumedDelta.y
        )

        return when (source) {
            ScrollSource.DRAG -> {
                // What the content leaves goes back into the overscroll area: a single frame of a
                // drag can cross an edge in either direction
                overscrollOffset += unconsumedDelta.y
                lastFlingUnconsumedDelta = 0f
                delta - unconsumedDelta
            }

            ScrollSource.FLING -> {
                // Within the safe area insets a fling meets no resistance: the unconsumed delta
                // moves the offset and is reported back as consumed, so the deceleration continues
                // seamlessly. Whatever is left after that is an actual overscroll: it is reported
                // as unconsumed to stop the fling, and [applyToFling] plays a spring instead
                val deltaWithinSafeInsets = unconsumedDelta.y.limitedBySafeInsets()
                overscrollOffset += deltaWithinSafeInsets
                lastFlingUnconsumedDelta = unconsumedDelta.y - deltaWithinSafeInsets

                delta - Offset(unconsumedDelta.x, lastFlingUnconsumedDelta)
            }
        }
    }

    /**
     * Gives [delta] to the overscroll area first and returns what is left of it for the content.
     *
     * A delta which pulls the content back towards the scrollable range unwinds the overscroll
     * offset, and only the part which crosses the edge reaches the content. A delta which pulls
     * further out is taken by the overscroll area entirely - unless it comes from a fling, which is
     * never allowed to grow the overscroll on its own: it is offered to the content first and only
     * what the content leaves may go into the safe area insets, see [applyToScroll].
     */
    private fun consumeIntoOverscroll(delta: Float, source: ScrollSource): Float {
        val overscroll = overscrollOffset
        val newOverscroll = overscroll + delta
        val isPullingTowardsContent = if (delta >= 0f) overscroll <= 0f else overscroll >= 0f

        return when {
            isPullingTowardsContent -> {
                val hasCrossedEdge = if (delta >= 0f) newOverscroll > 0f else newOverscroll < 0f
                overscrollOffset = if (hasCrossedEdge) 0f else newOverscroll
                if (hasCrossedEdge) newOverscroll else 0f
            }

            source == ScrollSource.FLING -> delta

            else -> {
                overscrollOffset = newOverscroll
                0f
            }
        }
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        onScrollActivity()

        val availableVelocity = playSpringBackBeforeFlingIfNeeded(velocity)
        val velocityConsumedByFling = performFling(availableVelocity)
        val postFlingVelocity = availableVelocity.y - velocityConsumedByFling.y

        if (lastFlingUnconsumedDelta == 0f && overscrollOffset.overscrollBeyondSafeInsets == 0f) {
            return
        }

        playSpringAnimation(
            unconsumedDelta = lastFlingUnconsumedDelta,
            initialVelocity = postFlingVelocity,
            reason = SpringReason.SETTLE_AFTER_FLING
        )
    }

    /**
     * If a fling starts while the content is already beyond the safe area insets, the spring back
     * plays first and only the velocity it doesn't spend is left for the fling itself.
     */
    private suspend fun playSpringBackBeforeFlingIfNeeded(initialVelocity: Velocity): Velocity {
        val velocity = initialVelocity.y
        val overscroll = overscrollOffset.overscrollBeyondSafeInsets
        val isFlingingBack =
            (velocity <= 0f && overscroll > 0f) || (velocity >= 0f && overscroll < 0f)

        return if (isFlingingBack) {
            val velocityLeft = playSpringAnimation(
                unconsumedDelta = 0f,
                initialVelocity = velocity,
                reason = SpringReason.FLING_FROM_OVERSCROLL
            )
            Velocity(0f, velocityLeft)
        } else {
            initialVelocity
        }
    }

    /**
     * Animates [overscrollOffset] from its current value plus [unconsumedDelta] back to the nearest
     * resting offset, and returns the velocity which is left once the animation is over.
     *
     * The animation runs in points, the units the Cupertino formulas are defined in, and its values
     * are converted back to raw pixels.
     */
    private suspend fun playSpringAnimation(
        unconsumedDelta: Float,
        initialVelocity: Float,
        reason: SpringReason
    ): Float {
        val initialValue = overscrollOffset + unconsumedDelta
        val targetValue = initialValue.restingOverscrollOffset()
        val initialSign = sign(initialValue - targetValue)
        val targetValueInPoints = targetValue.inPoints
        var currentVelocity = initialVelocity

        val spec = spring<Float>(
            stiffness = when (reason) {
                SpringReason.FLING_FROM_OVERSCROLL -> SPRING_BACK_STIFFNESS
                SpringReason.SETTLE_AFTER_FLING -> SETTLE_SPRING_STIFFNESS
            },
            visibilityThreshold = HALF_PIXEL.inPoints
        )

        springAnimationScope?.cancel()
        springAnimationScope = CoroutineScope(currentCoroutineContext())
        springAnimationScope?.run {
            AnimationState(
                Float.VectorConverter,
                initialValue.inPoints,
                initialVelocity.inPoints
            ).animateTo(
                targetValue = targetValueInPoints,
                animationSpec = spec
            ) {
                overscrollOffset = value.inPixels
                currentVelocity = velocity.inPixels

                // The spring back only has to bring the content to the edge, the rest of the
                // velocity belongs to the fling which follows
                if (reason == SpringReason.FLING_FROM_OVERSCROLL &&
                    initialSign != 0f &&
                    sign(value - targetValueInPoints) != initialSign
                ) {
                    cancelAnimation()
                }
            }
            springAnimationScope = null
        }

        if (currentCoroutineContext().isActive) {
            // The spring is critically damped, so in case a spring-fling-spring sequence is
            // slightly offset and the velocity is of the opposite sign, it ends up with no
            // animation at all
            overscrollOffset = targetValue
        }

        return if (reason == SpringReason.SETTLE_AFTER_FLING) 0f else currentVelocity
    }

    /**
     * Reproduces the default iOS fling behavior (see `CupertinoFlingBehavior`, which is internal to
     * the foundation module) and relies on [applyToScroll] to keep the deceleration going while the
     * offset is still within the safe area insets.
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
            onScrollActivity()

            val delta = value - lastValue
            val consumed = try {
                scrollBy(delta)
            } catch (_: CancellationException) {
                0f
            }
            lastValue = value
            velocityLeft = velocity

            // Stop as soon as something is left unconsumed, i.e. an actual overscroll has started
            // and the spring animation has to take the rest of the velocity over. The half pixel of
            // slack keeps rounding errors from ending the fling early
            if (abs(delta - consumed) > HALF_PIXEL) {
                cancelAnimation()
            }
        }

        return velocityLeft
    }

    /**
     * Brings both the content and the overscroll area back to the very top, the way a status bar
     * tap does on a native `UIScrollView`.
     */
    private fun scrollToTop() {
        onScrollActivity()
        cancelOffsetAnimations()

        animateOverscrollOffsetToTop()

        val scrollableState = scrollableState ?: return
        scrollToTopJob = overscrollNode.coroutineScope.launch {
            scrollableState.animateScrollToStart()
        }
    }

    /**
     * Animates the overscroll area to [maxTopSafeInset], the resting offset the content sits at
     * when the large navigation title is fully expanded.
     *
     * A status bar tap moves the content through [ScrollableState], which bypasses [applyToScroll]
     * entirely, so the overscroll offset has to be brought back to the top edge separately.
     */
    private fun animateOverscrollOffsetToTop() {
        val initialValue = overscrollOffset
        val targetValue = maxTopSafeInset
        if (initialValue == targetValue) {
            return
        }

        restingOffsetAnimationJob = overscrollNode.coroutineScope.launch {
            AnimationState(
                Float.VectorConverter,
                initialValue.inPoints,
                0f
            ).animateTo(
                targetValue = targetValue.inPoints,
                animationSpec = spring(
                    stiffness = SPRING_BACK_STIFFNESS,
                    visibilityThreshold = HALF_PIXEL.inPoints
                )
            ) {
                overscrollOffset = value.inPixels
            }

            overscrollOffset = targetValue
        }
    }

    /** Stops everything which animates [overscrollOffset] on its own. */
    private fun cancelOffsetAnimations() {
        springAnimationScope?.cancel()
        springAnimationScope = null

        restingOffsetAnimationJob?.cancel()
        restingOffsetAnimationJob = null

        scrollToTopJob?.cancel()
        scrollToTopJob = null
    }

    private fun onDraw() {
        // Fix an issue where the scroll was cancelled midway and the overscroll was left hanging:
        // a draw pass which wasn't scheduled by an offset change and has no pointer behind it means
        // that neither a drag nor an animation drives the offset anymore, so settle it right away.
        // The offset is written through the backing state to skip the side effects of the setter:
        // pushing a new content offset to UIKit from within a draw pass is not safe
        if (!isDrawScheduledByOffsetChange && isInProgress && overscrollNode.pointersDown == 0) {
            overscrollOffsetState.value = overscrollOffsetState.value.restingOverscrollOffset()
        }

        isDrawScheduledByOffsetChange = false
    }

    /**
     * Called whenever the scrollable is about to move the content, be it a drag, a fling or a
     * status bar tap. Grows the resting range to the largest safe area insets seen so far: the
     * insets shrink as the large title collapses, and the content still has to be able to rest
     * against the fully expanded one.
     */
    private fun onScrollActivity() {
        hasScrolled = true
        maxTopSafeInset = max(maxTopSafeInset, currentTopSafeInset)
        maxBottomSafeInset = max(maxBottomSafeInset, currentBottomSafeInset)
    }

    /** Drops the safe area insets observed so far and starts over from the current ones. */
    private fun resetMaxSafeInsets() {
        maxTopSafeInset = currentTopSafeInset
        maxBottomSafeInset = currentBottomSafeInset
    }

    /**
     * Approximation of how far the content is scrolled from its start, in points, reported to the
     * proxy scroll view so that UIKit collapses and expands the navigation chrome at the right
     * moment.
     *
     * A [ScrollableState] has no common way of telling its absolute offset, so the value is
     * accumulated from the consumed deltas and snapped back to a known edge whenever the content
     * refuses to scroll any further. Its range is the extra scrollable height of the proxy scroll
     * view, see [EXTRA_SCROLLABLE_HEIGHT].
     */
    private var approximateContentOffset: CGFloat = 0.0
        set(value) {
            field = value.coerceIn(0.0, EXTRA_SCROLLABLE_HEIGHT)
            syncProxyScrollViewOffset()
        }

    /** Moves [approximateContentOffset] along with the content the scrollable has just scrolled. */
    private fun onContentScrolled(consumedDelta: Float, unconsumedDelta: Float) {
        if (unconsumedDelta != 0f) {
            snapApproximateContentOffsetToEdge(unconsumedDelta)
        } else {
            approximateContentOffset -= consumedDelta.inPoints.toDouble()
        }
    }

    /**
     * Snaps [approximateContentOffset] to the content edge which refused to take
     * [deltaTowardsEdge], a delta pointing down for the top edge and up for the bottom one.
     */
    private fun snapApproximateContentOffsetToEdge(deltaTowardsEdge: Float) {
        approximateContentOffset = if (deltaTowardsEdge > 0f) 0.0 else EXTRA_SCROLLABLE_HEIGHT
    }

    /**
     * Pushes the current state of the effect to the proxy scroll view: while the content is offset
     * from its resting position, UIKit has to see the very same bounce, and while it is not, it has
     * to see how far the content is scrolled.
     */
    private fun syncProxyScrollViewOffset() {
        val bounceOffset = -visibleOverscrollOffset.inPoints
        val isBouncing = bounceOffset != 0f

        overscrollNode.updateScrollViewContentOffset(
            contentOffset = if (isBouncing) bounceOffset.toDouble() else approximateContentOffset,
            // UIKit lays the navigation bar out in response to a bounce, which is not safe to do in
            // the middle of a Compose scroll or draw pass
            deferred = isBouncing
        )
    }

    /**
     * The nearest offset the content may rest at. An overscroll only starts beyond the safe area
     * insets, so any offset within `-maxBottomSafeInset..maxTopSafeInset` is a valid resting
     * position: it meets no resistance and doesn't spring back.
     */
    private fun Float.restingOverscrollOffset(): Float =
        coerceIn(-maxBottomSafeInset, maxTopSafeInset)

    /** The part of the offset which is an actual overscroll, i.e. the one beyond the insets. */
    private val Float.overscrollBeyondSafeInsets: Float
        get() = this - restingOverscrollOffset()

    /**
     * The part of this delta which still fits into the safe area insets when applied to the current
     * [overscrollOffset]. Never moves the offset backwards or beyond the insets.
     */
    private fun Float.limitedBySafeInsets(): Float = when {
        this > 0f -> (maxTopSafeInset - overscrollOffset).coerceIn(0f, this)
        this < 0f -> (-maxBottomSafeInset - overscrollOffset).coerceIn(this, 0f)
        else -> 0f
    }

    /** Rubber bands only the part of the offset which goes beyond the safe area insets. */
    private fun Float.rubberBandedBeyondSafeInsets(): Float {
        if (scrollSize.height == 0f) {
            return 0f
        }

        return restingOverscrollOffset() + overscrollBeyondSafeInsets.rubberBanded()
    }

    private fun Float.rubberBanded(): Float =
        rubberBandedValue(
            value = inPoints,
            dimension = scrollSize.height.inPoints,
            coefficient = RUBBER_BAND_COEFFICIENT
        ).inPixels

    private val Float.inPoints: Float
        get() = this / density.density

    private val Float.inPixels: Float
        get() = this * density.density
}

/**
 * Maps a raw offset [value] on an axis of a scroll container with the given [dimension] to the
 * offset the content is visibly moved by, so that pulling it further out gets progressively harder.
 */
private fun rubberBandedValue(value: Float, dimension: Float, coefficient: Float): Float =
    sign(value) * (1f - (1f / (abs(value) * coefficient / dimension + 1f))) * dimension

/**
 * Animates this state back to the very start of its content.
 *
 * [ScrollableState] itself has no notion of items or indices - its whole surface is a scroll
 * session, a raw delta and a few flags - so there is no single call which covers every subclass.
 * Each built-in state which does expose an index based API is therefore handled explicitly, and
 * anything else falls back to the one generic measure of "how far the content is from its start"
 * every state can report, [ScrollableState.scrollIndicatorState].
 */
private suspend fun ScrollableState.animateScrollToStart() {
    when (this) {
        is ScrollState -> animateScrollTo(0)
        is LazyListState -> animateScrollToItem(0)
        is LazyGridState -> animateScrollToItem(0)
        is LazyStaggeredGridState -> animateScrollToItem(0)
        is PagerState -> animateScrollToPage(0)
        else -> {
            // Lazy layouts only estimate this value, which is why they are special cased above.
            // Int.MAX_VALUE means the state doesn't know its offset yet, and a null indicator state
            // means it cannot express one at all - neither is scrollable to a start
            val offset = scrollIndicatorState?.scrollOffset ?: return
            if (offset != 0 && offset != Int.MAX_VALUE) {
                scroll { scrollBy(-offset.toFloat()) }
            }
        }
    }
}

/**
 * Offsets and clips the content of the scrollable, counts the pointers which are down on it, and
 * owns the [NavigationProxyScrollView] the hosting view controller derives its navigation chrome
 * from.
 */
private class NavigationOverscrollNode(
    private val offset: () -> IntOffset,
    private val onNodeRemeasured: (IntSize) -> Unit,
    private val onNodeGeometryChanged: () -> Unit,
    private val onDraw: () -> Unit,
    onScrollToTop: () -> Unit,
) : Modifier.Node(),
    LayoutModifierNode,
    LayoutAwareModifierNode,
    GlobalPositionAwareModifierNode,
    DrawModifierNode,
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode {

    private val scrollView = NavigationProxyScrollView(onScrollToTop)

    private var lastSize: IntSize? = null
    private var lastPosition: Offset? = null
    private var lastContentOffset: CGFloat = 0.0
    private var releaseHostViewController: () -> Unit = {}

    /** Number of pointers which are currently down on the scrollable. */
    var pointersDown: Int by mutableStateOf(0)
        private set

    /** Safe area insets of the hosting view controller, in points. */
    val topSafeInset: CGFloat
        get() = scrollView.safeAreaInsets.useContents { top }
    val bottomSafeInset: CGFloat
        get() = scrollView.safeAreaInsets.useContents { bottom }

    /**
     * Reports [contentOffset] to the proxy scroll view, which is what UIKit adjusts the navigation
     * chrome to. When [deferred] is set, the update happens on the next main queue turn instead of
     * right away.
     */
    fun updateScrollViewContentOffset(contentOffset: CGFloat, deferred: Boolean) {
        if (lastContentOffset == contentOffset) {
            return
        }
        lastContentOffset = contentOffset

        if (deferred) {
            dispatch_async(dispatch_get_main_queue()) {
                scrollView.contentOffset = CGPointMake(0.0, contentOffset)
            }
        } else {
            scrollView.contentOffset = CGPointMake(0.0, contentOffset)
        }
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

    override fun ContentDrawScope.draw() {
        onDraw()

        // The content is offset, but the bounds of the scrollable are not, so whatever the offset
        // has moved outside of them has to be clipped away
        val visibleBounds = size.toRect().intersect(Rect(-offset().toOffset(), size))
        clipRect(
            left = visibleBounds.left,
            top = visibleBounds.top,
            right = visibleBounds.right,
            bottom = visibleBounds.bottom,
        ) {
            this@draw.drawContent()
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
        scrollView.isDraggingContent = pointersDown > 0
    }

    override fun onCancelPointerInput() {
        pointersDown = 0
        scrollView.isDraggingContent = false
    }

    override fun onAttach() {
        attachToHostViewController()
    }

    override fun onObservedReadsChanged() {
        attachToHostViewController()
    }

    override fun onDetach() {
        detachFromHostViewController()
    }

    /**
     * Embeds the proxy scroll view into the hosting view controller and makes it the content scroll
     * view of that controller, so that UIKit starts deriving the navigation chrome from it.
     *
     * [LocalUIViewController] is read through [observeReads], so the proxy follows the content if
     * it ends up hosted by another view controller.
     */
    private fun attachToHostViewController() {
        detachFromHostViewController()

        observeReads {
            val viewController = currentValueOf(LocalUIViewController)
            viewController.view.embedSubview(scrollView)
            viewController.setContentScrollView(scrollView, forEdge = NSDirectionalRectEdgeAll)

            // This node may outlive the view controller, which doesn't have to be alive for its
            // registration to be dropped
            val weakViewController = WeakReference(viewController)
            releaseHostViewController = {
                weakViewController.get()
                    ?.setContentScrollView(null, forEdge = NSDirectionalRectEdgeAll)
            }
        }
    }

    private fun detachFromHostViewController() {
        scrollView.removeFromSuperview()
        releaseHostViewController()
        releaseHostViewController = {}
    }
}

/**
 * An empty non-interactive `UIScrollView` whose only purpose is to be the content scroll view of
 * the hosting view controller: UIKit collapses the large navigation title, stretches the title area
 * and minimizes the tab bar in response to what this scroll view reports, while the scrolling
 * itself is done by Compose and mirrored here by [NavigationOverscrollNode].
 *
 * @param onScrollToTop called when the user taps the status bar. The proxy never scrolls itself,
 *   the callback brings the Compose content to the top instead.
 */
private class NavigationProxyScrollView(
    private val onScrollToTop: () -> Unit,
) : UIScrollView(frame = CGRectZero.readValue()), UIScrollViewDelegateProtocol {

    init {
        // The proxy stays interactive so that UIKit treats it as a candidate for the status bar
        // tap, while [hitTest] makes sure it never receives any touch
        userInteractionEnabled = true
        showsVerticalScrollIndicator = false
        contentInsetAdjustmentBehavior =
            UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentAlways
        delegate = this

        updateContentSize()
    }

    /**
     * Whether the Compose content is currently being dragged. UIKit animates the transitions of the
     * navigation bar and of the tab bar differently depending on it, see [isTracking], [isDragging]
     * and [notifyDraggingChanged].
     */
    var isDraggingContent: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            notifyDraggingChanged(value)
        }

    // UIKit decides how to move the navigation chrome from these flags, so they have to report the
    // state of the Compose scroll and not the one of the proxy, which never scrolls on its own
    override fun isTracking(): Boolean = isDraggingContent

    override fun isDragging(): Boolean = isDraggingContent

    override fun isDecelerating(): Boolean = true

    /** The proxy takes no touches: all of them belong to the Compose content. */
    override fun hitTest(point: CValue<CGPoint>, withEvent: UIEvent?): UIView? = null

    override fun scrollsToTop(): Boolean = true

    override fun scrollViewShouldScrollToTop(scrollView: UIScrollView): Boolean {
        onScrollToTop()
        // The proxy itself must not move, the Compose content is animated instead
        return false
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        updateContentSize()
    }

    override fun safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        updateContentSize()
    }

    /**
     * Keeps the content of the proxy taller than the proxy itself, so that any content offset a
     * Compose scrollable approximates can be reported to it.
     */
    private fun updateContentSize() {
        val safeInset = safeAreaInsets.useContents { max(top, bottom) }
        setContentSize(
            CGSizeMake(
                width = CGRectGetWidth(bounds),
                height = CGRectGetHeight(bounds) + safeInset + EXTRA_SCROLLABLE_HEIGHT
            )
        )
    }

    /**
     * Tells UIKit that a drag has started or ended, which is what makes the navigation bar and the
     * tab bar animate their transitions instead of jumping to the new state.
     *
     * There is no public way of doing that for a scroll view which UIKit doesn't scroll itself,
     * hence the private selectors, guarded by `respondsToSelector`.
     */
    private fun notifyDraggingChanged(isDragging: Boolean) {
        if (isDragging) {
            performSelectorIfPresent("_scrollViewWillBeginDragging")
        } else {
            // The argument is `decelerate`: the proxy never decelerates on its own
            performSelectorIfPresent(
                name = "_scrollViewDidEndDragging:",
                argument = NSNumber.numberWithBool(false)
            )
        }
    }

    private fun performSelectorIfPresent(name: String, argument: Any? = null) {
        val selector = NSSelectorFromString(name)
        if (!respondsToSelector(selector)) {
            return
        }

        if (argument == null) {
            performSelector(selector)
        } else {
            performSelector(selector, argument)
        }
    }
}

/**
 * The iOS scroll deceleration, a copy of `CupertinoScrollDecayAnimationSpec` which is internal to
 * the foundation module.
 *
 * @param decelerationRate the rate at which the velocity decelerates over time. Defaults to the
 *   rate the default `UIScrollView` behavior uses.
 */
private class CupertinoScrollDecaySpec(
    private val decelerationRate: Float = UIScrollViewDecelerationRateNormal.toFloat()
) : FloatDecayAnimationSpec {
    private val coefficient: Float = 1000f * ln(decelerationRate)

    override val absVelocityThreshold: Float = HALF_PIXEL

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

private fun Float.secondsToNanos(): Long = (toDouble() * NANOS_PER_SECOND).roundToLong()

private fun Long.nanosToSeconds(): Float = (toDouble() / NANOS_PER_SECOND).toFloat()

/** Adds [subview] to this view and pins it to all four of its edges. */
private fun UIView.embedSubview(subview: UIView) {
    addSubview(subview)
    subview.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activateConstraints(
        listOf(
            subview.leftAnchor.constraintEqualToAnchor(leftAnchor),
            subview.rightAnchor.constraintEqualToAnchor(rightAnchor),
            subview.topAnchor.constraintEqualToAnchor(topAnchor),
            subview.bottomAnchor.constraintEqualToAnchor(bottomAnchor)
        )
    )
}
