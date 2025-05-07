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

package androidx.compose.ui.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.CUPERTINO_TOUCH_SLOP
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.DpRectZero
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.asDpOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UILabel
import platform.UIKit.UIScrollView

internal class ScrollTest {

    /**
     * Tests that a drag of the same value as the touch slop threshold will not trigger overscroll behavior
     * in a vertically scrollable Column.
     **/
    @Test
    fun testExactTouchSlopDrag() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Red)
                    .onGloballyPositioned { boxRect = it.boundsInWindow().toDpRect(density) }
                )
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(screenSize.height)
                    .background(Color.Blue)
                )
            }
        }

        val initialBoxRect = boxRect.copy()
        val dyExact = CUPERTINO_TOUCH_SLOP.dp

        touchDown(screenSize.center)
            .dragBy(dy = dyExact)

        waitForIdle()

        assertEquals(initialBoxRect, boxRect)
    }

    /**
     * Tests that a drag just over the touch slop threshold will trigger overscroll behavior
     * in a vertically scrollable Column.
     **/
    @Test
    fun testJustOverTouchSlopDrag() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Red)
                    .onGloballyPositioned { boxRect = it.boundsInWindow().toDpRect(density) }
                )
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(screenSize.height)
                    .background(Color.White)
                )
            }
        }

        val dyJustOver = CUPERTINO_TOUCH_SLOP.dp + 1.dp

        touchDown(screenSize.center)
            .dragBy(dy = dyJustOver)

        waitForIdle()

        assertTrue(boxRect.top > 0.dp)
        // Scroll state remains at 0 despite visual overscroll
        assertEquals(0 * density.density, state.value.toFloat())
    }

    @Test
    fun testTopOverscrollDragResistance() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Red)
                    .onGloballyPositioned {
                        boxRect = it.boundsInWindow().toDpRect(density)
                    }
                )
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(screenSize.height.times(2))
                    .background(Color.White)
                )
            }
        }

        val touch = touchDown(screenSize.center)
        var previousBoxTop = boxRect.top
        var previousDiff = 0f

        for (i in 1..11) {
            touch.dragBy(dy = 20.dp, duration = 0.1.seconds)
            waitForIdle()

            val currentBoxTop = boxRect.top
            val currentDiff = (currentBoxTop - previousBoxTop).value

            // skip the first two iterations as we don't want to take into account the first drag position
            if (i > 2) {
                val epsilon = 5e-5f
                val isDiffZero = abs(abs(currentDiff) - abs(previousDiff)) <= epsilon
                val isDiffDecreasing = abs(currentDiff) < abs(previousDiff)
                assertTrue(isDiffDecreasing || isDiffZero)
            }

            previousBoxTop = currentBoxTop
            previousDiff = currentDiff
        }

        waitForIdle()
        touch.up()
        waitForIdle()
        // stabilizes back at the original position
        assertEquals(DpRect(DpOffset.Zero, DpSize(screenSize.width, 100.dp)), boxRect)
    }

    @Test
    fun testBottomOverscrollDragResistance() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        val boxHeight = 100.0
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(screenSize.height)
                    .background(Color.White)
                )
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(boxHeight.dp)
                    .background(Color.Red)
                    .onGloballyPositioned {
                        boxRect = it.boundsInWindow().toDpRect(density)
                    }
                )
            }
        }

        val touch = touchDown(screenSize.center)
        var previousBoxTop = boxRect.top
        var previousDiff = 0f

        for (i in 1..10) {
            touch.dragBy(dy = -20.dp, duration = 0.1.seconds)
            waitForIdle()

            val currentBoxTop = boxRect.top
            val currentDiff = (currentBoxTop - previousBoxTop).value

            // skip the first two iterations as we don't want to take into account the first drag position
            if (i > 2) {
                val epsilon = 5e-5f
                val isDiffZero = abs(abs(currentDiff) - abs(previousDiff)) <= epsilon
                val isDiffDecreasing = abs(currentDiff) < abs(previousDiff)
                assertTrue(isDiffDecreasing || isDiffZero)
            }

            previousBoxTop = currentBoxTop
            previousDiff = currentDiff
        }

        waitForIdle()
        touch.up()
        waitForIdle()
        assertEquals(DpRect(DpOffset(x = 0.dp, y = screenSize.height - boxHeight.dp), DpSize(width = screenSize.width, height = boxHeight.dp)), boxRect)
    }

    @Test
    fun testOverscrollAndFlick() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        val boxHeight = 100.0
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                for (index in 0 until 10) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(boxHeight.dp)
                            .background(if (index % 2 == 0) Color.Blue else Color.Red)
                            .then(
                                if (index == 0) {
                                    Modifier.onGloballyPositioned {
                                        boxRect = it.boundsInWindow().toDpRect(density)
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
        }

        // overscroll
        val touch = touchDown(screenSize.center)
            .dragBy(dy = boxHeight.dp)

        // rubber band effect is applied
        assertTrue(0.dp < boxRect.top && boxRect.top < boxHeight.dp)
        // overscroll does not alter scroll state
        assertEquals(0 * density.density, state.value.toFloat())

        // flick up
        touch
            .dragBy(dy = -(boxHeight + 50).dp, duration = 100.milliseconds)
            .up()

        waitForIdle()

        // top box is out of visible bounds
        assertEquals(DpRectZero(), boxRect)
        // scroll state is updated
        assertTrue(state.value > boxHeight)
    }

    /**
     * Verifies that drag gestures smaller than the touch slop threshold
     * don't trigger scrolling behavior in a vertically scrollable Column.
     */
    @Test
    fun testNotScrollingForSmallDrag() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Red)
                    .onGloballyPositioned { boxRect = it.boundsInWindow().toDpRect(density) }
                )
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(screenSize.height)
                    .background(Color.White)
                )
            }
        }


        val dySmall = 5.dp
        assertTrue(dySmall.value < CUPERTINO_TOUCH_SLOP)

        // downward drag - overscroll
        touchDown(screenSize.center)
            .dragBy(dy = dySmall)
        waitForIdle()

        val initialBoxRect = DpRect(DpOffset.Zero, DpSize(screenSize.width, 100.dp))

        // expect no changes
        assertEquals(0 * density.density, state.value.toFloat())
        assertEquals(initialBoxRect, boxRect)

        // upward drag - scroll
        touchDown(screenSize.center)
            .dragBy(dy = -dySmall)
        waitForIdle()

        // expect no changes
        assertEquals(0 * density.density, state.value.toFloat())
        assertEquals(initialBoxRect, boxRect)
    }

    @Test
    fun testOverscrollForContentSmallerThanScreenSize() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Green)
                )
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Red)
                    .onGloballyPositioned { boxRect = it.boundsInWindow().toDpRect(density) }
                )
            }
        }

        val initialBoxRect = DpRect(DpOffset(x = 0.dp, y = 100.dp), DpSize(screenSize.width, 100.dp))

        waitForIdle()
        assertEquals(initialBoxRect, boxRect)

        touchDown(DpOffset(screenSize.center.x, 50.dp))
            .dragBy(dy = 50.dp)

        waitForIdle()
        assertEquals(initialBoxRect, boxRect)
    }

    @Test
    fun testOverscrollForContentSizeOfScreenSize() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(screenSize.height)
                    .background(Color.Green)
                    .onGloballyPositioned { boxRect = it.boundsInWindow().toDpRect(density) }
                )
            }
        }

        val initialBoxRect = DpRect(DpOffset.Zero, screenSize)

        assertEquals(initialBoxRect, boxRect)

        touchDown(DpOffset(screenSize.center.x, 50.dp))
            .dragBy(dy = 50.dp)

        waitForIdle()
        assertEquals(initialBoxRect, boxRect)
    }

    @Test
    fun testHorizontalScrollWithRTL() = runUIKitInstrumentedTest {
        val itemSize = 150
        val lazyRowState = LazyListState()
        val totalScrollOffset = { lazyRowState.firstVisibleItemIndex * itemSize + lazyRowState.firstVisibleItemScrollOffset }

        setContent {
            HorizontalScrollContent(
                itemSize = itemSize.dp,
                lazyRowState = lazyRowState,
                layoutDirection = LayoutDirection.Rtl
            )
        }

        touchDown(DpOffset(screenSize.center.x, 50.dp))
            .dragBy(dx = (150 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        assertEquals(150, totalScrollOffset())
    }

    @Test
    fun testHorizontalScrollWithLTR() = runUIKitInstrumentedTest {
        val itemSize = 150
        val lazyRowState = LazyListState()
        val totalScrollOffset = { lazyRowState.firstVisibleItemIndex * itemSize + lazyRowState.firstVisibleItemScrollOffset }

        setContent {
            HorizontalScrollContent(
                itemSize = itemSize.dp,
                lazyRowState = lazyRowState,
                layoutDirection = LayoutDirection.Ltr
            )
        }

        touchDown(DpOffset(screenSize.center.x, 50.dp))
            .dragBy(dx = -(150 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        assertEquals(150, totalScrollOffset())
    }

    @Test
    fun testHorizontalOverscrollWithRTL() = runUIKitInstrumentedTest {
        val itemSize = 150
        val itemCount = 20
        val lazyRowState = LazyListState()
        val totalScrollOffset = { lazyRowState.firstVisibleItemIndex * itemSize + lazyRowState.firstVisibleItemScrollOffset }
        var firstBoxRect = DpRectZero()

        setContent {
            HorizontalScrollContent(
                itemSize = itemSize.dp,
                itemCount = itemCount,
                lazyRowState = lazyRowState,
                layoutDirection = LayoutDirection.Rtl,
                onFirstBoxGloballyPositioned = { firstBoxRect = it.boundsInWindow().toDpRect(density) }
            )
        }

        val touch = touchDown(DpOffset(screenSize.center.x, 50.dp))
            .dragBy(dx = -(50 + CUPERTINO_TOUCH_SLOP).dp)

        assertTrue(firstBoxRect.right < screenSize.width)
        assertTrue(firstBoxRect.right > screenSize.width - 50.dp)

        touch.up()

        waitForIdle()

        assertEquals(screenSize.width, firstBoxRect.right)
        assertEquals(0, totalScrollOffset())
    }

    @Test
    fun testHorizontalOverscrollWithLTR() = runUIKitInstrumentedTest {
        val itemSize = 150
        val lazyRowState = LazyListState()
        val totalScrollOffset = { lazyRowState.firstVisibleItemIndex * itemSize + lazyRowState.firstVisibleItemScrollOffset }
        var firstBoxRect = DpRectZero()

        setContent {
            HorizontalScrollContent(
                itemSize = itemSize.dp,
                lazyRowState = lazyRowState,
                layoutDirection = LayoutDirection.Ltr,
                onFirstBoxGloballyPositioned = { firstBoxRect = it.boundsInWindow().toDpRect(density) }
            )
        }

        val touch = touchDown(DpOffset(screenSize.center.x, 50.dp))
            .dragBy(dx = (50 + CUPERTINO_TOUCH_SLOP).dp)

        assertTrue(firstBoxRect.left > 0.dp)
        assertTrue(firstBoxRect.left < 50.dp)

        touch.up()

        waitForIdle()

        assertEquals(0.dp, firstBoxRect.left)
        assertEquals(0, totalScrollOffset())
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testDragWithTouchStartInUIKitViewAndComposeView() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)
        var labelRect = DpRect(DpOffset.Zero, DpSize.Zero)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Blue)
                )

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Red)
                    .onGloballyPositioned { boxRect = it.boundsInWindow().toDpRect(density) }
                )

                UIKitView(
                    factory = {
                        val label = UILabel(frame = CGRectZero.readValue())
                        label.text = "UIKit.UILabel"
                        label.textColor = UIColor.blackColor
                        label.backgroundColor = UIColor.redColor
                        label
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .onGloballyPositioned { labelRect = it.boundsInWindow().toDpRect(density) },
                    onReset = { /* Just to make it reusable */ }
                )

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(screenSize.height)
                    .background(Color.White)
                )
            }
        }

        // start at center.y of the UILabel and drag upwards
        touchDown(DpOffset(screenSize.center.x, 250.dp))
            .dragBy(dy = -(100 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        assertEquals(DpRect(DpOffset(x = 0.dp, y = 0.dp), DpSize(screenSize.width, 100.dp)), boxRect)
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 100.dp), DpSize(screenSize.width, 200.dp)), labelRect)

        // start at center.y of the red box and drag downwards
        touchDown(DpOffset(screenSize.center.x, 50.dp))
            .dragBy(dy = (100 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        // frames should be at their initial positions
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 100.dp), DpSize(screenSize.width, 100.dp)), boxRect)
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 200.dp), DpSize(screenSize.width, 200.dp)), labelRect)

        waitForIdle()
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testUIKitScrollViewInsideComposeScrollViewInteractions() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var boxRect = DpRect(DpOffset.Zero, DpSize.Zero)
        var contentOffset: () -> DpOffset = { DpOffset.Zero }

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.Blue)
                )

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.Red)
                    .onGloballyPositioned { boxRect = it.boundsInWindow().toDpRect(density) }
                )

                var scrollViewSize by mutableStateOf(DpSize.Zero)
                UIKitView(
                    factory = {
                        val scrollView = UIScrollView()
                        scrollView.setContentSize(
                            CGSizeMake(scrollViewSize.width.value.toDouble(), 1000.0)
                        )
                        contentOffset = { scrollView.contentOffset.asDpOffset() }
                        scrollView.backgroundColor = UIColor.lightGrayColor
                        scrollView
                    },
                    update = { scrollView ->
                        scrollView.setContentSize(
                            CGSizeMake(scrollViewSize.width.value.toDouble(), 1000.0)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(400.dp).onSizeChanged {
                        scrollViewSize = with(density) {
                            DpSize(it.width.toDp(), it.height.toDp())
                        }
                    }
                )

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(screenSize.height)
                    .background(Color.White)
                )
            }
        }

        // start in red box and drag upwards
        touchDown(DpOffset(screenSize.center.x, 250.dp))
            .dragBy(dy = -(100 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        assertEquals(DpRect(DpOffset(x = 0.dp, y = 100.dp), DpSize(screenSize.width, 200.dp)), boxRect)

        // start in UIScrollView and drag upwards
        touchDown(DpOffset(screenSize.center.x, 400.dp))
            .dragBy(dy = -(100 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        // red box should remain at the same position
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 100.dp), DpSize(screenSize.width, 200.dp)), boxRect)
        assertEquals(DpOffset(x = 0.dp, y = 100.dp), contentOffset())

        // drag back to initial position
        touchDown(DpOffset(screenSize.center.x, 50.dp))
            .dragBy(dy = (100 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        // start in red box and flick upwards
        touchDown(DpOffset(screenSize.center.x, 150.dp))
            .dragBy(dy = -(100 + CUPERTINO_TOUCH_SLOP).dp, duration = 100.milliseconds)
            .up()

        // wait for the flick to be in the deceleration phase
        delay(800)

        // start in the UIScrollView and drag down while flick animation is still in progress
        touchDown(DpOffset(screenSize.center.x, 100.dp))
            .dragBy(dy = (100 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        // UIScrollView content offset should still be the same
        assertEquals(DpOffset(x = 0.dp, y = 100.dp), contentOffset())

        // drag up in UIScrollView from idle position
        touchDown(DpOffset(screenSize.center.x, 300.dp))
            .dragBy(dy = (100 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        assertEquals(DpOffset.Zero, contentOffset())
    }

    @Test
    fun testOverscrollWhenUIKitHorizontalScrollViewIsAtTop() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var uiKitViewRect = DpRect(DpOffset.Zero, DpSize.Zero)
        var contentOffset: () -> DpOffset = { DpOffset.Zero }

        setContent {
            VerticalScrollWithHorizontalUIKitScroll(
                state = state,
                screenSize = screenSize,
                topContentHeight = 0.dp,
                uiKitScrollViewHeight = 200.dp,
                onUIKitViewGloballyPositioned = { uiKitViewRect = it.boundsInWindow().toDpRect(density) },
                uiKitContentOffset = { contentOffset = it }
            )
        }

        val initialUIKitViewRect = uiKitViewRect.copy()
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 0.dp), DpSize(screenSize.width, 200.dp)), initialUIKitViewRect)

        val touch = touchDown(DpOffset(screenSize.center.x, 100.dp))
            .dragBy(dy = (50 + CUPERTINO_TOUCH_SLOP).dp)

        waitForIdle()

        assertTrue(uiKitViewRect.top > 0.dp)
        assertTrue(uiKitViewRect.top < (50 + CUPERTINO_TOUCH_SLOP).dp)

        assertEquals(DpOffset(x = 0.dp, y = 0.dp), contentOffset())

        touch.up()
        waitForIdle()

        assertEquals(initialUIKitViewRect, uiKitViewRect)
        assertEquals(0 * density.density, state.value.toFloat())

        // Horizontal drag to verify UIKit scroll reacts
        touchDown(DpOffset(screenSize.center.x, 100.dp))
            .dragBy(dx = -(50 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        // Horizontal content offset should change, but vertical position stays the same
        assertEquals(DpOffset(x = 50.dp, y = 0.dp), contentOffset())
        assertEquals(initialUIKitViewRect, uiKitViewRect)

        // Combined gesture - start with downward overscroll then horizontal
        touchDown(DpOffset(screenSize.center.x, 100.dp))
            .dragBy(dy = (50 + CUPERTINO_TOUCH_SLOP).dp)
            .dragBy(dx = -(50 + CUPERTINO_TOUCH_SLOP).dp, dy = 0.dp)
            .up()

        waitForIdle()

        // View should return to original position after overscroll
        assertEquals(initialUIKitViewRect, uiKitViewRect)
        // Horizontal content should not change
        assertEquals(DpOffset(x = 50.dp, y = 0.dp), contentOffset())
    }

    @Test
    fun testVerticalComposeScrollsWhenDraggingFromUIKitHorizontalScroll() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var uiKitViewRect = DpRect(DpOffset.Zero, DpSize.Zero)
        var contentOffset: () -> DpOffset = { DpOffset.Zero }

        setContent {
            VerticalScrollWithHorizontalUIKitScroll(
                state = state,
                screenSize = screenSize,
                topContentHeight = 200.dp,
                uiKitScrollViewHeight = 200.dp,
                onUIKitViewGloballyPositioned = { uiKitViewRect = it.boundsInWindow().toDpRect(density) },
                uiKitContentOffset = { contentOffset = it }
            )
        }

        touchDown(DpOffset(screenSize.center.x, 300.dp))
            .dragBy(dy = -(100 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        assertEquals(100 * density.density, state.value.toFloat())
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 100.dp), DpSize(screenSize.width, 200.dp)), uiKitViewRect)
        assertEquals(DpOffset(x = 0.dp, y = 0.dp), contentOffset())
    }

    /**
     * Tests horizontal UIScrollView scrolling behavior when:
     * - Touch interaction starts inside the UIScrollView
     * - Drag gesture continues horizontally
     */
    @Test
    fun testHorizontalUIScrollViewInComposeScroll_HorizontalDrag() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var uiKitViewRect = DpRect(DpOffset.Zero, DpSize.Zero)
        var contentOffset: () -> DpOffset = { DpOffset.Zero }

        setContent {
            VerticalScrollWithHorizontalUIKitScroll(
                state = state,
                screenSize = screenSize,
                topContentHeight = 200.dp,
                uiKitScrollViewHeight = 200.dp,
                onUIKitViewGloballyPositioned = { uiKitViewRect = it.boundsInWindow().toDpRect(density) },
                uiKitContentOffset = { contentOffset = it }
            )
        }

        touchDown(DpOffset(screenSize.center.x, 250.dp))
            .dragBy(dx = -(50 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        assertEquals(0 * density.density, state.value.toFloat())
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 200.dp), DpSize(screenSize.width, 200.dp)), uiKitViewRect)
        assertEquals(DpOffset(x = 50.dp, y = 0.dp), contentOffset())
    }

    /**
     * Tests horizontal UIScrollView scrolling behavior when:
     * - Touch interaction starts inside the UIScrollView
     * - Drag gesture continues vertically
     */
    @Test
    fun testHorizontalUIScrollViewInComposeVerticalScroll_VerticalDrag() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var uiKitViewRect = DpRect(DpOffset.Zero, DpSize.Zero)
        var contentOffset: () -> DpOffset = { DpOffset.Zero }

        setContent {
            VerticalScrollWithHorizontalUIKitScroll(
                state = state,
                screenSize = screenSize,
                topContentHeight = 200.dp,
                uiKitScrollViewHeight = 200.dp,
                onUIKitViewGloballyPositioned = { uiKitViewRect = it.boundsInWindow().toDpRect(density) },
                uiKitContentOffset = { contentOffset = it }
            )
        }

        touchDown(DpOffset(screenSize.center.x, 250.dp))
            .dragBy(dy = -(50 + CUPERTINO_TOUCH_SLOP).dp)
            .up()

        waitForIdle()

        assertEquals(50 * density.density, state.value.toFloat())
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 150.dp), DpSize(screenSize.width, 200.dp)), uiKitViewRect)
        assertEquals(DpOffset.Zero, contentOffset())
    }

    /**
     * Tests drag gestures that include both horizontal and vertical
     * components when interacting with a horizontal UIKit scroll view embedded in a vertical
     * Compose scroll container. Verifies proper gesture disambiguation and handling when:
     * 1. Drag gestures change direction mid-interaction
     * 2. Mixed horizontal and vertical movements occur simultaneously
     * 3. Drag gestures extend beyond the bounds of the UIKit view
     */
    @Test
    fun testHorizontalUIScrollViewInComposeVerticalScroll_MixedDrag() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var uiKitViewRect = DpRect(DpOffset.Zero, DpSize.Zero)
        var contentOffset: () -> DpOffset = { DpOffset.Zero }

        setContent {
            VerticalScrollWithHorizontalUIKitScroll(
                state = state,
                screenSize = screenSize,
                topContentHeight = 200.dp,
                uiKitScrollViewHeight = 200.dp,
                onUIKitViewGloballyPositioned = { uiKitViewRect = it.boundsInWindow().toDpRect(density) },
                uiKitContentOffset = { contentOffset = it }
            )
        }

        touchDown(DpOffset(screenSize.center.x, 250.dp))
            .dragBy(dx = -(50 + CUPERTINO_TOUCH_SLOP).dp, dy = -50.dp)
            .dragBy(dx = 20.dp, dy = -50.dp)
            .dragBy(dx = -70.dp, dy = 50.dp)
            .dragBy(dy = -150.dp)
            .up()

        waitForIdle()

        assertEquals(0 * density.density, state.value.toFloat())
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 200.dp), DpSize(screenSize.width, 200.dp)), uiKitViewRect)
        assertEquals(DpOffset(x = 100.dp, y = 0.dp), contentOffset())
    }

    /**
     * Tests the resolution of ambiguous drag gestures between a horizontal UIKit scroll view
     * and a vertical Compose scroll container. Specifically:
     * 1. When drag starts with primarily vertical movement, Compose scroll takes precedence
     * 2. Small horizontal movements during a primarily vertical drag don't trigger UIKit scroll
     * 3. Direction disambiguation happens early in the gesture
     */
    @Test
    fun testHorizontalUIScrollViewInComposeVerticalScroll_VerticalAndSmallHorizontalDrag() = runUIKitInstrumentedTest {
        val state = ScrollState(0)
        var uiKitViewRect = DpRect(DpOffset.Zero, DpSize.Zero)
        var contentOffset: () -> DpOffset = { DpOffset.Zero }

        setContent {
            VerticalScrollWithHorizontalUIKitScroll(
                state = state,
                screenSize = screenSize,
                topContentHeight = 200.dp,
                uiKitScrollViewHeight = 200.dp,
                onUIKitViewGloballyPositioned = { uiKitViewRect = it.boundsInWindow().toDpRect(density) },
                uiKitContentOffset = { contentOffset = it }
            )
        }

        // start inside UIScrollView and drag slightly to the side and upwards
        touchDown(DpOffset(screenSize.center.x, 250.dp))
            .dragBy(dx = -(5 + CUPERTINO_TOUCH_SLOP).dp, dy = -(50 + CUPERTINO_TOUCH_SLOP).dp)
            .dragBy(dx = -50.dp, dy = -50.dp)
            .up()

        waitForIdle()

        // verify that only Compose scroll state changed but UIScrollView content offset stayed the same
        assertEquals(100 * density.density, state.value.toFloat())
        assertEquals(DpRect(DpOffset(x = 0.dp, y = 100.dp), DpSize(screenSize.width, 200.dp)), uiKitViewRect)
        assertEquals(DpOffset.Zero, contentOffset())
    }
}


@OptIn(ExperimentalForeignApi::class)
@Composable
private fun VerticalScrollWithHorizontalUIKitScroll(
    state: ScrollState,
    screenSize: DpSize,
    topContentHeight: Dp,
    uiKitScrollViewHeight: Dp,
    uiKitScrollViewContentWidth: Double = 1000.0,
    onUIKitViewGloballyPositioned: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {},
    uiKitContentOffset: (() -> DpOffset) -> Unit = { },
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
        if (topContentHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topContentHeight)
                    .background(Color.Red)
            )
        }

        UIKitView(
            factory = {
                val scrollView = UIScrollView()
                scrollView.setContentSize(CGSizeMake(uiKitScrollViewContentWidth, uiKitScrollViewHeight.value.toDouble()))
                scrollView.backgroundColor = UIColor.lightGrayColor
                uiKitContentOffset({ scrollView.contentOffset.asDpOffset() })
                scrollView
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(uiKitScrollViewHeight)
                .onGloballyPositioned(onUIKitViewGloballyPositioned),
            update = {}
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenSize.height)
                .background(Color.White)
        )
    }
}

@Composable
private fun HorizontalScrollContent(
    itemSize: Dp,
    itemCount: Int = 20,
    lazyRowState: LazyListState,
    layoutDirection: LayoutDirection,
    onFirstBoxGloballyPositioned: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {}
) {
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyRow(modifier = Modifier.height(itemSize), state = lazyRowState) {
                items(itemCount) {
                    Box(
                        Modifier
                            .size(itemSize, itemSize)
                            .background(remember { Color(Random.nextInt()) })
                            .then(
                                if (it == 0) Modifier.onGloballyPositioned(onFirstBoxGloballyPositioned) else Modifier
                            )
                    ) {
                        Text("Text ${it}")
                    }
                }
            }
        }
    }
}
