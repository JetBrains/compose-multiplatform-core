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

package androidx.compose.ui.scroll

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.CUPERTINO_TOUCH_SLOP
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.DpRectZero
import androidx.compose.ui.test.utils.center
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.test.utils.wait
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import platform.UIKit.UITraitEnvironmentLayoutDirectionRightToLeft

/**
 * Gesture coverage for standalone [HorizontalPager] and [VerticalPager].
 *
 * The test content mirrors `PagerExample` from `mpp/demo` (components/Pager.kt): a [Column] with
 * a [HorizontalPager] in the top half and a [VerticalPager] in the bottom half, 10 pages each.
 */
internal class PagerTest {

    // region HorizontalPager

    @Test
    fun testHorizontalPagerSwipeLeftMovesToNextPage() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        val verticalState = PagerState { PageCount }
        setContent { PagerExample(horizontalState, verticalState) }

        findNodeWithTag(HorizontalPagerTag).swipeLeft().up()
        waitForSettle(horizontalState)

        assertSettledAt(1, horizontalState)
        assertSettledAt(0, verticalState)
    }

    @Test
    fun testHorizontalPagerSwipeRightMovesToPreviousPage() = runUIKitInstrumentedTest {
        val horizontalState = PagerState(currentPage = 3) { PageCount }
        setContent { PagerExample(horizontalState, PagerState { PageCount }) }

        findNodeWithTag(HorizontalPagerTag).swipeRight().up()
        waitForSettle(horizontalState)

        assertSettledAt(2, horizontalState)
    }

    @Test
    fun testHorizontalPagerConsecutiveSwipes() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        setContent { PagerExample(horizontalState, PagerState { PageCount }) }

        repeat(3) {
            findNodeWithTag(HorizontalPagerTag).swipeLeft().up()
            waitForSettle(horizontalState)
        }
        assertSettledAt(3, horizontalState)

        findNodeWithTag(HorizontalPagerTag).swipeRight().up()
        waitForSettle(horizontalState)
        assertSettledAt(2, horizontalState)
    }

    @Test
    fun testHorizontalPagerDragBelowTouchSlopDoesNotMovePage() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        setContent { PagerExample(horizontalState, PagerState { PageCount }) }

        val dxSmall = 5.dp
        assertTrue(dxSmall.value < CUPERTINO_TOUCH_SLOP)

        val touch = findNodeWithTag(HorizontalPagerTag).touchDown()
            .dragBy(dx = -dxSmall)
        waitForIdle()

        assertEquals(0f, horizontalState.currentPageOffsetFraction)
        assertTrue(!horizontalState.isScrollInProgress)

        touch.up()
        waitForSettle(horizontalState)
        assertSettledAt(0, horizontalState)
    }

    @Test
    fun testHorizontalPagerFollowsFingerDuringDrag() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        var pageRect = DpRectZero()
        setContent {
            PagerExample(
                horizontalState = horizontalState,
                verticalState = PagerState { PageCount },
                onHorizontalPageRect = { page, rect -> if (page == 0) pageRect = rect }
            )
        }
        val initialPageRect = pageRect

        val dragDistance = 100.dp
        val touch = findNodeWithTag(HorizontalPagerTag).touchDown()
            .dragBy(dx = -(CUPERTINO_TOUCH_SLOP.dp + dragDistance), duration = 0.5.seconds)
        waitForIdle()

        assertTrue(horizontalState.isScrollInProgress)
        assertEquals(initialPageRect.left - dragDistance, pageRect.left, "Page should follow the finger")
        assertEquals(0, horizontalState.currentPage)

        touch.wait(200.milliseconds).up()
        waitForSettle(horizontalState)

        assertSettledAt(0, horizontalState)
        assertEquals(initialPageRect, pageRect)
    }

    @Test
    fun testHorizontalPagerSlowShortDragSnapsBack() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        setContent { PagerExample(horizontalState, PagerState { PageCount }) }

        findNodeWithTag(HorizontalPagerTag).touchDown()
            .dragBy(dx = -(screenSize.width * 0.3f), duration = 1.seconds)
            .wait(200.milliseconds)
            .up()
        waitForSettle(horizontalState)

        assertSettledAt(0, horizontalState)
    }

    @Test
    fun testHorizontalPagerSlowLongDragMovesToNextPage() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        setContent { PagerExample(horizontalState, PagerState { PageCount }) }

        findNodeWithTag(HorizontalPagerTag).touchDown()
            .dragBy(dx = -(screenSize.width * 0.3f), duration = 0.5.seconds)
            .dragBy(dx = -(screenSize.width * 0.3f), duration = 0.5.seconds)
            .wait(200.milliseconds)
            .up()
        waitForSettle(horizontalState)

        assertSettledAt(1, horizontalState)
    }

    @Test
    fun testHorizontalPagerShortFlingMovesToNextPage() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        setContent { PagerExample(horizontalState, PagerState { PageCount }) }

        findNodeWithTag(HorizontalPagerTag).touchDown()
            .dragBy(dx = -(CUPERTINO_TOUCH_SLOP + 80).dp, duration = 100.milliseconds)
            .up()
        waitForSettle(horizontalState)

        assertSettledAt(1, horizontalState)
    }

    @Test
    fun testHorizontalPagerFastFlingMovesAtMostOnePage() = runUIKitInstrumentedTest {
        val horizontalState = PagerState(currentPage = 2) { PageCount }
        setContent { PagerExample(horizontalState, PagerState { PageCount }) }

        findNodeWithTag(HorizontalPagerTag).swipeLeft(duration = 50.milliseconds).up()
        waitForSettle(horizontalState)
        assertSettledAt(3, horizontalState)

        findNodeWithTag(HorizontalPagerTag).swipeRight(duration = 50.milliseconds).up()
        waitForSettle(horizontalState)
        assertSettledAt(2, horizontalState)
    }

    @Test
    fun testHorizontalPagerOverscrollOnFirstPage() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        var pageRect = DpRectZero()
        setContent {
            PagerExample(
                horizontalState = horizontalState,
                verticalState = PagerState { PageCount },
                onHorizontalPageRect = { page, rect -> if (page == 0) pageRect = rect }
            )
        }
        val initialPageRect = pageRect

        val touch = findNodeWithTag(HorizontalPagerTag).touchDown()
            .dragBy(dx = (CUPERTINO_TOUCH_SLOP + 100).dp, duration = 0.5.seconds)
        waitForIdle()

        // Rubber band effect is applied, but the page does not follow the finger 1:1
        assertTrue(pageRect.left > initialPageRect.left, "Overscroll should be visible")
        assertTrue(pageRect.left < initialPageRect.left + 100.dp, "Overscroll should resist")
        assertEquals(0, horizontalState.currentPage)
        assertEquals(0f, horizontalState.currentPageOffsetFraction)

        touch.up()
        waitForSettle(horizontalState)

        assertSettledAt(0, horizontalState)
        assertEquals(initialPageRect, pageRect)
    }

    @Test
    fun testHorizontalPagerSwipeLeftOnLastPageStaysOnLastPage() = runUIKitInstrumentedTest {
        val horizontalState = PagerState(currentPage = PageCount - 1) { PageCount }
        setContent { PagerExample(horizontalState, PagerState { PageCount }) }

        findNodeWithTag(HorizontalPagerTag).swipeLeft().up()
        waitForSettle(horizontalState)

        assertSettledAt(PageCount - 1, horizontalState)
    }

    @Test
    fun testHorizontalPagerIgnoresVerticalDrag() = runUIKitInstrumentedTest {
        val horizontalState = PagerState(currentPage = 1) { PageCount }
        val verticalState = PagerState { PageCount }
        setContent { PagerExample(horizontalState, verticalState) }

        findNodeWithTag(HorizontalPagerTag).touchDown()
            .dragBy(dy = -(CUPERTINO_TOUCH_SLOP + 150).dp, duration = 0.5.seconds)
            .up()
        waitForSettle(horizontalState)

        assertSettledAt(1, horizontalState)
        assertSettledAt(0, verticalState)
    }

    @Test
    fun testHorizontalPagerSwipeInRtl() = runUIKitInstrumentedTest {
        val horizontalState = PagerState(currentPage = 1) { PageCount }
        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            PagerExample(horizontalState, PagerState { PageCount })
        }

        // In RTL the next page is on the left side, so swiping right moves forward
        findNodeWithTag(HorizontalPagerTag).swipeRight().up()
        waitForSettle(horizontalState)
        assertSettledAt(2, horizontalState)

        findNodeWithTag(HorizontalPagerTag).swipeLeft().up()
        waitForSettle(horizontalState)
        assertSettledAt(1, horizontalState)
    }

    @Test
    fun testHorizontalPagerTapOnPageClicksAndSwipeDoesNot() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        val clickedPages = mutableListOf<Int>()
        setContent {
            PagerExample(
                horizontalState = horizontalState,
                verticalState = PagerState { PageCount },
                onHorizontalPageClick = { clickedPages.add(it) }
            )
        }

        findNodeWithTag(HorizontalPagerTag).tap()
        waitForIdle()
        assertEquals(listOf(0), clickedPages)

        findNodeWithTag(HorizontalPagerTag).swipeLeft().up()
        waitForSettle(horizontalState)
        assertSettledAt(1, horizontalState)
        assertEquals(listOf(0), clickedPages, "Swipe should not be recognized as a click")

        findNodeWithTag(HorizontalPagerTag).tap()
        waitForIdle()
        assertEquals(listOf(0, 1), clickedPages)
    }

    @Test
    fun testHorizontalPagerWithVerticalListInside() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        val listStates = List(PageCount) { LazyListState() }
        setContent {
            HorizontalPager(
                state = horizontalState,
                modifier = Modifier.fillMaxSize().testTag(HorizontalPagerTag)
            ) { page ->
                LazyColumn(state = listStates[page], modifier = Modifier.fillMaxSize()) {
                    items(50) { item ->
                        Text(
                            text = "Page $page, item $item",
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )
                    }
                }
            }
        }

        // Vertical drag scrolls the list on the current page and doesn't change the page
        touchDown(screenSize.center)
            .dragBy(dy = -(CUPERTINO_TOUCH_SLOP + 100).dp, duration = 0.5.seconds)
            .wait(200.milliseconds)
            .up()
        waitForSettle(horizontalState)

        assertSettledAt(0, horizontalState)
        assertEquals(2, listStates[0].firstVisibleItemIndex)

        // Horizontal swipe changes the page and doesn't scroll the list
        findNodeWithTag(HorizontalPagerTag).swipeLeft().up()
        waitForSettle(horizontalState)

        assertSettledAt(1, horizontalState)
        assertEquals(2, listStates[0].firstVisibleItemIndex)
        assertEquals(0, listStates[1].firstVisibleItemIndex)
        assertEquals(0, listStates[1].firstVisibleItemScrollOffset)
    }

    // endregion

    // region VerticalPager

    @Test
    fun testVerticalPagerSwipeUpMovesToNextPage() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        val verticalState = PagerState { PageCount }
        setContent { PagerExample(horizontalState, verticalState) }

        findNodeWithTag(VerticalPagerTag).swipeUp().up()
        waitForSettle(verticalState)

        assertSettledAt(1, verticalState)
        assertSettledAt(0, horizontalState)
    }

    @Test
    fun testVerticalPagerSwipeDownMovesToPreviousPage() = runUIKitInstrumentedTest {
        val verticalState = PagerState(currentPage = 3) { PageCount }
        setContent { PagerExample(PagerState { PageCount }, verticalState) }

        findNodeWithTag(VerticalPagerTag).swipeDown().up()
        waitForSettle(verticalState)

        assertSettledAt(2, verticalState)
    }

    @Test
    fun testVerticalPagerConsecutiveSwipes() = runUIKitInstrumentedTest {
        val verticalState = PagerState { PageCount }
        setContent { PagerExample(PagerState { PageCount }, verticalState) }

        repeat(3) {
            findNodeWithTag(VerticalPagerTag).swipeUp().up()
            waitForSettle(verticalState)
        }
        assertSettledAt(3, verticalState)

        findNodeWithTag(VerticalPagerTag).swipeDown().up()
        waitForSettle(verticalState)
        assertSettledAt(2, verticalState)
    }

    @Test
    fun testVerticalPagerDragBelowTouchSlopDoesNotMovePage() = runUIKitInstrumentedTest {
        val verticalState = PagerState { PageCount }
        setContent { PagerExample(PagerState { PageCount }, verticalState) }

        val dySmall = 5.dp
        assertTrue(dySmall.value < CUPERTINO_TOUCH_SLOP)

        val touch = findNodeWithTag(VerticalPagerTag).touchDown()
            .dragBy(dy = -dySmall)
        waitForIdle()

        assertEquals(0f, verticalState.currentPageOffsetFraction)
        assertTrue(!verticalState.isScrollInProgress)

        touch.up()
        waitForSettle(verticalState)
        assertSettledAt(0, verticalState)
    }

    @Test
    fun testVerticalPagerFollowsFingerDuringDrag() = runUIKitInstrumentedTest {
        val verticalState = PagerState { PageCount }
        var pageRect = DpRectZero()
        setContent {
            PagerExample(
                horizontalState = PagerState { PageCount },
                verticalState = verticalState,
                onVerticalPageRect = { page, rect -> if (page == 0) pageRect = rect }
            )
        }
        val initialPageRect = pageRect

        val dragDistance = 100.dp
        val touch = findNodeWithTag(VerticalPagerTag).touchDown()
            .dragBy(dy = -(CUPERTINO_TOUCH_SLOP.dp + dragDistance), duration = 0.5.seconds)
        waitForIdle()

        assertTrue(verticalState.isScrollInProgress)
        assertEquals(initialPageRect.top - dragDistance, pageRect.top, "Page should follow the finger")
        assertEquals(0, verticalState.currentPage)

        touch.wait(200.milliseconds).up()
        waitForSettle(verticalState)

        assertSettledAt(0, verticalState)
        assertEquals(initialPageRect, pageRect)
    }

    @Test
    fun testVerticalPagerSlowShortDragSnapsBack() = runUIKitInstrumentedTest {
        val verticalState = PagerState { PageCount }
        setContent { PagerExample(PagerState { PageCount }, verticalState) }

        val pageHeight = findNodeWithTag(VerticalPagerTag).frame!!.height
        findNodeWithTag(VerticalPagerTag).touchDown()
            .dragBy(dy = -(pageHeight * 0.3f), duration = 1.seconds)
            .wait(200.milliseconds)
            .up()
        waitForSettle(verticalState)

        assertSettledAt(0, verticalState)
    }

    @Test
    fun testVerticalPagerSlowLongDragMovesToNextPage() = runUIKitInstrumentedTest {
        val verticalState = PagerState { PageCount }
        setContent { PagerExample(PagerState { PageCount }, verticalState) }

        val pageHeight = findNodeWithTag(VerticalPagerTag).frame!!.height
        // Start close to the bottom edge to leave enough room for a drag beyond half of the page
        touchDown(findNodeWithTag(VerticalPagerTag).frame!!.let {
            it.center().copy(y = it.bottom - 16.dp)
        })
            .dragBy(dy = -(pageHeight * 0.35f), duration = 0.5.seconds)
            .dragBy(dy = -(pageHeight * 0.35f), duration = 0.5.seconds)
            .wait(200.milliseconds)
            .up()
        waitForSettle(verticalState)

        assertSettledAt(1, verticalState)
    }

    @Test
    fun testVerticalPagerShortFlingMovesToNextPage() = runUIKitInstrumentedTest {
        val verticalState = PagerState { PageCount }
        setContent { PagerExample(PagerState { PageCount }, verticalState) }

        findNodeWithTag(VerticalPagerTag).touchDown()
            .dragBy(dy = -(CUPERTINO_TOUCH_SLOP + 80).dp, duration = 100.milliseconds)
            .up()
        waitForSettle(verticalState)

        assertSettledAt(1, verticalState)
    }

    @Test
    fun testVerticalPagerFastFlingMovesAtMostOnePage() = runUIKitInstrumentedTest {
        val verticalState = PagerState(currentPage = 2) { PageCount }
        setContent { PagerExample(PagerState { PageCount }, verticalState) }

        findNodeWithTag(VerticalPagerTag).swipeUp(duration = 50.milliseconds).up()
        waitForSettle(verticalState)
        assertSettledAt(3, verticalState)

        findNodeWithTag(VerticalPagerTag).swipeDown(duration = 50.milliseconds).up()
        waitForSettle(verticalState)
        assertSettledAt(2, verticalState)
    }

    @Test
    fun testVerticalPagerOverscrollOnFirstPage() = runUIKitInstrumentedTest {
        val verticalState = PagerState { PageCount }
        var pageRect = DpRectZero()
        setContent {
            PagerExample(
                horizontalState = PagerState { PageCount },
                verticalState = verticalState,
                onVerticalPageRect = { page, rect -> if (page == 0) pageRect = rect }
            )
        }
        val initialPageRect = pageRect

        val touch = findNodeWithTag(VerticalPagerTag).touchDown()
            .dragBy(dy = (CUPERTINO_TOUCH_SLOP + 100).dp, duration = 0.5.seconds)
        waitForIdle()

        // Rubber band effect is applied, but the page does not follow the finger 1:1
        assertTrue(pageRect.top > initialPageRect.top, "Overscroll should be visible")
        assertTrue(pageRect.top < initialPageRect.top + 100.dp, "Overscroll should resist")
        assertEquals(0, verticalState.currentPage)
        assertEquals(0f, verticalState.currentPageOffsetFraction)

        touch.up()
        waitForSettle(verticalState)

        assertSettledAt(0, verticalState)
        assertEquals(initialPageRect, pageRect)
    }

    @Test
    fun testVerticalPagerSwipeUpOnLastPageStaysOnLastPage() = runUIKitInstrumentedTest {
        val verticalState = PagerState(currentPage = PageCount - 1) { PageCount }
        setContent { PagerExample(PagerState { PageCount }, verticalState) }

        findNodeWithTag(VerticalPagerTag).swipeUp().up()
        waitForSettle(verticalState)

        assertSettledAt(PageCount - 1, verticalState)
    }

    @Test
    fun testVerticalPagerIgnoresHorizontalDrag() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        val verticalState = PagerState(currentPage = 1) { PageCount }
        setContent { PagerExample(horizontalState, verticalState) }

        findNodeWithTag(VerticalPagerTag).swipeLeft().up()
        waitForSettle(verticalState)

        assertSettledAt(1, verticalState)
        assertSettledAt(0, horizontalState)
    }

    @Test
    fun testVerticalPagerTapOnPageClicksAndSwipeDoesNot() = runUIKitInstrumentedTest {
        val verticalState = PagerState { PageCount }
        val clickedPages = mutableListOf<Int>()
        setContent {
            PagerExample(
                horizontalState = PagerState { PageCount },
                verticalState = verticalState,
                onVerticalPageClick = { clickedPages.add(it) }
            )
        }

        findNodeWithTag(VerticalPagerTag).tap()
        waitForIdle()
        assertEquals(listOf(0), clickedPages)

        findNodeWithTag(VerticalPagerTag).swipeUp().up()
        waitForSettle(verticalState)
        assertSettledAt(1, verticalState)
        assertEquals(listOf(0), clickedPages, "Swipe should not be recognized as a click")

        findNodeWithTag(VerticalPagerTag).tap()
        waitForIdle()
        assertEquals(listOf(0, 1), clickedPages)
    }

    // endregion

    // region Nested pagers

    @Test
    fun testVerticalPagerNestedInHorizontalPager() = runUIKitInstrumentedTest {
        val horizontalState = PagerState { PageCount }
        val verticalStates = List(PageCount) { PagerState { PageCount } }
        setContent {
            HorizontalPager(
                state = horizontalState,
                modifier = Modifier.fillMaxSize().testTag(HorizontalPagerTag)
            ) { horizontalPage ->
                VerticalPager(
                    state = verticalStates[horizontalPage],
                    modifier = Modifier.fillMaxSize()
                ) { verticalPage ->
                    PageContent(name = "Page $horizontalPage:$verticalPage", page = verticalPage)
                }
            }
        }

        // Vertical swipe changes only the inner pager
        findNodeWithTag(HorizontalPagerTag).swipeUp().up()
        waitForSettle(verticalStates[0])
        assertSettledAt(1, verticalStates[0])
        assertSettledAt(0, horizontalState)

        // Horizontal swipe changes only the outer pager
        findNodeWithTag(HorizontalPagerTag).swipeLeft().up()
        waitForSettle(horizontalState)
        assertSettledAt(1, horizontalState)
        assertSettledAt(1, verticalStates[0])
        assertSettledAt(0, verticalStates[1])

        // Vertical swipe on the new page changes its own inner pager
        findNodeWithTag(HorizontalPagerTag).swipeUp().up()
        waitForSettle(verticalStates[1])
        assertSettledAt(1, verticalStates[1])
        assertSettledAt(1, horizontalState)
    }

    // endregion

    private fun UIKitInstrumentedTest.waitForSettle(state: PagerState) {
        waitForIdle()
        waitUntil("Pager should settle") { !state.isScrollInProgress }
        waitForIdle()
    }

    private fun assertSettledAt(page: Int, state: PagerState) {
        assertEquals(page, state.currentPage, "Unexpected current page")
        assertEquals(page, state.settledPage, "Unexpected settled page")
        assertTrue(
            abs(state.currentPageOffsetFraction) < 1e-3f,
            "Pager should be settled, offset fraction: ${state.currentPageOffsetFraction}"
        )
    }
}

private const val PageCount = 10
private const val HorizontalPagerTag = "HorizontalPager"
private const val VerticalPagerTag = "VerticalPager"

private val PageColors = listOf(
    Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta, Color.Gray
)

/**
 * Same layout as `PagerExample` in `mpp/demo`: a [HorizontalPager] on top and a [VerticalPager]
 * at the bottom, each taking half of the screen.
 */
@Composable
private fun PagerExample(
    horizontalState: PagerState,
    verticalState: PagerState,
    onHorizontalPageRect: (page: Int, rect: DpRect) -> Unit = { _, _ -> },
    onVerticalPageRect: (page: Int, rect: DpRect) -> Unit = { _, _ -> },
    onHorizontalPageClick: ((page: Int) -> Unit)? = null,
    onVerticalPageClick: ((page: Int) -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = horizontalState,
            modifier = Modifier.weight(1f).testTag(HorizontalPagerTag)
        ) { page ->
            PageContent(
                name = "HorizontalPager",
                page = page,
                onRect = { onHorizontalPageRect(page, it) },
                onClick = onHorizontalPageClick?.let { { it(page) } }
            )
        }
        VerticalPager(
            state = verticalState,
            modifier = Modifier.weight(1f).testTag(VerticalPagerTag)
        ) { page ->
            PageContent(
                name = "VerticalPager",
                page = page,
                onRect = { onVerticalPageRect(page, it) },
                onClick = onVerticalPageClick?.let { { it(page) } }
            )
        }
    }
}

@Composable
private fun PageContent(
    name: String,
    page: Int,
    onRect: (DpRect) -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageColors[page % PageColors.size])
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .onGloballyPositioned {
                // Not clipped by the pager bounds, unlike `boundsInWindow`
                onRect(Rect(it.positionInWindow(), it.size.toSize()).toDpRect(density))
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$name: $page",
            style = MaterialTheme.typography.h1
        )
    }
}
