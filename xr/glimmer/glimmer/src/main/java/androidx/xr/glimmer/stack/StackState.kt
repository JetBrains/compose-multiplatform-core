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

package androidx.xr.glimmer.stack

import androidx.annotation.IntRange
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize

/**
 * Creates and remembers a [StackState] for a [VerticalStack].
 *
 * The returned [StackState] is remembered across compositions and can be used to control or observe
 * the state of a [VerticalStack]. It's essential to pass this state to the `state` parameter of the
 * corresponding [VerticalStack] composable.
 *
 * Note: Properties of the state will only be correctly populated after the [VerticalStack] it is
 * associated with has been composed for the first time.
 *
 * Warning: A single [StackState] instance must not be shared across multiple [VerticalStack]
 * composables.
 *
 * @param initialTopItem The index of the item to show at the top of the stack initially. Must be
 *   non-negative. Defaults to 0.
 * @see StackState
 * @see VerticalStack
 */
@Composable
public fun rememberStackState(@IntRange(from = 0) initialTopItem: Int = 0): StackState =
    rememberSaveable(saver = StackState.Saver) { StackState(initialTopItem) }

/**
 * The [VerticalStack] state that allows programmatic control and observation of the stack's state.
 *
 * A [StackState] object can be created and remembered using [rememberStackState].
 *
 * Note: Properties of the state will only be correctly populated after the [VerticalStack] it is
 * associated with has been composed for the first time.
 *
 * Warning: A single [StackState] instance must not be shared across multiple [VerticalStack]
 * composables.
 *
 * @param initialTopItem The index of the item to show at the top of the stack initially. Must be
 *   non-negative. Defaults to 0.
 * @see rememberStackState
 * @see VerticalStack
 */
// TODO(b/413429531): add ScrollIndicatorState.
@Stable
public class StackState(@IntRange(from = 0) initialTopItem: Int = 0) : ScrollableState {

    init {
        require(initialTopItem >= 0) { "initialTopItem must be non-negative" }
    }

    internal var itemCount by mutableIntStateOf(0)

    internal val pagerState = PagerState(currentPage = initialTopItem, pageCount = { itemCount })

    /** The index of the item that's currently at the top of the stack, defaults to 0. */
    public val topItem: Int
        get() = pagerState.currentPage

    /**
     * The offset of the top item as a fraction of the stack item container size. This value ranges
     * between -0.5 and 0.5 and indicates how much the item is offset from the snapped position. A
     * value of 0.0 indicates that the item is perfectly snapped to the center.
     */
    public val topItemOffsetFraction: Float
        get() = pagerState.currentPageOffsetFraction

    /**
     * [InteractionSource] that's used to dispatch drag events when this stack is being dragged. To
     * know whether a fling (or animated scroll) is in progress, use [isScrollInProgress].
     */
    public val interactionSource: InteractionSource
        get() = pagerState.interactionSource

    /**
     * Contains useful information about the currently displayed layout of this stack. The
     * information is available after the first measure pass.
     */
    internal val layoutInfoInternal = StackLayoutInfoImpl(pagerState)

    /**
     * Scroll (jump immediately) to a given [item] index.
     *
     * @param item The index of the destination item
     */
    public suspend fun scrollToItem(item: Int) {
        if (itemCount == 0) return
        pagerState.scrollToPage(item.coerceIn(0, itemCount - 1))
    }

    /**
     * Scroll animate to a given [item]'s closest snap position. If the [item] is too far away from
     * [topItem], not all the items in the range will be composed. Instead, the stack will jump to a
     * nearer item, then compose and animate the rest of the items until the destination [item].
     *
     * @param item The index of the destination item
     * @param animationSpec An [AnimationSpec] to move between items
     */
    public suspend fun animateScrollToItem(
        item: Int,
        animationSpec: AnimationSpec<Float> = spring(),
    ) {
        if (itemCount == 0) return
        pagerState.animateScrollToPage(
            item.coerceIn(0, itemCount - 1),
            pageOffsetFraction = 0f,
            animationSpec,
        )
    }

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ) {
        if (itemCount == 0) return
        pagerState.scroll(scrollPriority, block)
    }

    override fun dispatchRawDelta(delta: Float): Float {
        if (itemCount == 0) return 0f
        return pagerState.dispatchRawDelta(delta)
    }

    override val isScrollInProgress: Boolean
        get() = pagerState.isScrollInProgress

    @get:Suppress("GetterSetterNames")
    override val canScrollForward: Boolean
        get() = pagerState.currentPage < pagerState.pageCount - 1

    @get:Suppress("GetterSetterNames")
    override val canScrollBackward: Boolean
        get() = pagerState.currentPage > 0

    @get:Suppress("GetterSetterNames")
    override val lastScrolledForward: Boolean
        get() = pagerState.lastScrolledForward

    @get:Suppress("GetterSetterNames")
    override val lastScrolledBackward: Boolean
        get() = pagerState.lastScrolledBackward

    public companion object {
        /** The default [Saver] implementation for [StackState]. */
        public val Saver: Saver<StackState, Int> =
            Saver(save = { it.topItem }, restore = { StackState(it) })
    }
}

/**
 * Contains useful information about the currently displayed layout of a [VerticalStack]. This
 * information is available after the first measure pass.
 *
 * Use [StackState.layoutInfoInternal] to retrieve this.
 */
@Stable
internal sealed interface StackLayoutInfo {
    // TODO(b/446933128): decide what properties should be exposed as public
}

/** The default implementation of [StackLayoutInfo]. */
internal class StackLayoutInfoImpl internal constructor(private val pagerState: PagerState) :
    StackLayoutInfo {

    internal val viewportSize: IntSize
        get() = viewportSizeState.value

    /** The backing state for [viewportSize]. */
    internal val viewportSizeState = mutableStateOf(IntSize.Zero)

    /**
     * The maximum size of a stack item in pixels, which is based on the viewport size of the
     * underlying pager.
     */
    internal val maxItemSize: IntSize
        get() = pagerState.layoutInfo.viewportSize
}
