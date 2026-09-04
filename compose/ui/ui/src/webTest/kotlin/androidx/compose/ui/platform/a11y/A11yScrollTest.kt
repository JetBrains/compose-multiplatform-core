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

package androidx.compose.ui.platform.a11y

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLElement

/**
 * Tests for scrolling initiated by assistive technologies (VoiceOver & co).
 *
 * On the web, an AT never sends an explicit "scroll" command to the page. It asks the browser to
 * scroll an element exposed as a scroll container (or to bring one of its descendants into view),
 * and the page only observes the resulting `scrollTop`/`scrollLeft` change and "scroll" event.
 * So the contract under test is:
 * - a semantics node with a ScrollAxisRange + ScrollBy action becomes a real CSS scroll container
 *   (non-visible overflow + a sizer stretching it to the full content extent);
 * - changing its DOM scroll offset (what an AT effectively does) drives the Compose scroll state;
 * - the Compose scroll state is mirrored back to the DOM scroll offset.
 */
class A11yScrollTest : OnCanvasTests {

    private val density: Float
        get() = window.devicePixelRatio.toFloat()

    private suspend fun awaitCondition(
        message: String,
        timeout: Duration = 5.seconds,
        condition: () -> Boolean
    ) {
        val startTime = currentTimeMillis()
        suspendCancellableCoroutine { continuation ->
            fun check() {
                when {
                    condition() -> continuation.resumeWith(Result.success(Unit))
                    currentTimeMillis() - startTime > timeout.inWholeMilliseconds ->
                        continuation.resumeWith(
                            Result.failure(AssertionError("Timed out waiting for: $message"))
                        )
                    else -> window.requestAnimationFrame { check() }
                }
            }
            check()
        }
    }

    private fun getScrollableElement(tag: String = "scrollable"): HTMLElement =
        assertNotNull(
            getShadowRoot().getElementById(tag) as? HTMLElement,
            "Scrollable node must be present in the a11y tree"
        )

    @Test
    fun verticalScrollableBecomesScrollContainer() = runApplicationTest {
        val scrollState = ScrollState(0)

        createComposeWindow {
            Column(
                modifier = Modifier
                    .testTag("scrollable")
                    .size(100.dp)
                    .verticalScroll(scrollState)
            ) {
                repeat(10) {
                    Text("Item $it", modifier = Modifier.height(50.dp))
                }
            }
        }

        awaitA11YChanges()

        val element = getScrollableElement()
        assertEquals("scroll", element.style.overflowY, "Vertical scrollable must have overflow-y: scroll")
        assertEquals("hidden", element.style.overflowX, "Vertical-only scrollable must hide the horizontal overflow")
        assertEquals("vertical", element.getAttribute("aria-orientation"))

        // The browser derives scrollability (as exposed to ATs) from a real scrollable overflow
        assertTrue(
            element.scrollHeight > element.clientHeight,
            "Scroll container must have scrollable overflow: " +
                "scrollHeight=${element.scrollHeight}, clientHeight=${element.clientHeight}"
        )

        // Content: 10 items x 50dp in a 100dp viewport => 500dp total content extent
        val expectedContentHeightCssPx = 500
        assertTrue(
            abs(element.scrollHeight - expectedContentHeightCssPx) <= 2,
            "Scrollable extent must match the content size reported by Compose, " +
                "expected ~$expectedContentHeightCssPx, got ${element.scrollHeight}"
        )

        val sizer = element.firstElementChild as? HTMLElement
        assertNotNull(sizer, "Scroll container must have a sizer element")
        assertEquals("true", sizer.getAttribute("aria-hidden"), "The sizer must be hidden from ATs")

        assertEquals(0.0, element.scrollTop, "DOM scroll offset must match the initial Compose scroll offset")
    }

    @Test
    fun domScrollDrivesComposeScroll() = runApplicationTest {
        val scrollState = ScrollState(0)

        createComposeWindow {
            Column(
                modifier = Modifier
                    .testTag("scrollable")
                    .size(100.dp)
                    .verticalScroll(scrollState)
            ) {
                repeat(10) {
                    Text("Item $it", modifier = Modifier.height(50.dp))
                }
            }
        }

        awaitA11YChanges()
        val element = getScrollableElement()

        // This is what effectively happens when an AT scrolls: the browser changes the scroll
        // offset of the scroll container and fires a "scroll" event.
        element.scrollTop = 50.0

        val expectedComposePx = (50f * density).toInt()
        awaitCondition("Compose scroll state must follow the DOM scroll offset") {
            abs(scrollState.value - expectedComposePx) <= 1
        }

        // Scroll further: the second delta must be computed against the new offset (not doubled)
        element.scrollTop = 80.0
        val expectedComposePx2 = (80f * density).toInt()
        awaitCondition("Compose scroll state must follow the second DOM scroll") {
            abs(scrollState.value - expectedComposePx2) <= 1
        }
    }

    @Test
    fun composeScrollSyncsDomScrollOffset() = runApplicationTest {
        val scrollState = ScrollState(0)

        createComposeWindow {
            Column(
                modifier = Modifier
                    .testTag("scrollable")
                    .size(100.dp)
                    .verticalScroll(scrollState)
            ) {
                repeat(10) {
                    Text("Item $it", modifier = Modifier.height(50.dp))
                }
            }
        }

        awaitA11YChanges()
        val element = getScrollableElement()
        assertEquals(0.0, element.scrollTop)

        val targetComposePx = (70f * density).toInt()
        launch { scrollState.scrollTo(targetComposePx) }

        awaitCondition("DOM scroll offset must follow the Compose scroll state") {
            abs(element.scrollTop - 70.0) <= 1.0
        }
    }

    @Test
    fun reverseScrollingMapsDomOffsetsToCompose() = runApplicationTest {
        val scrollState = ScrollState(0)

        createComposeWindow {
            Column(
                modifier = Modifier
                    .testTag("scrollable")
                    .size(100.dp)
                    .verticalScroll(scrollState, reverseScrolling = true)
            ) {
                repeat(10) {
                    Text("Item $it", modifier = Modifier.height(50.dp))
                }
            }
        }

        awaitA11YChanges()
        val element = getScrollableElement()
        val maximumDomOffset = element.scrollHeight - element.clientHeight
        assertTrue(abs(element.scrollTop - maximumDomOffset) <= 1.0)

        element.scrollTop = maximumDomOffset - 50.0
        val expectedComposePx = (50f * density).toInt()
        awaitCondition("Reverse Compose scroll state must follow the inverse DOM offset") {
            abs(scrollState.value - expectedComposePx) <= 1
        }
    }

    @Test
    fun horizontalScrollableBecomesScrollContainer() = runApplicationTest {
        val scrollState = ScrollState(0)

        createComposeWindow {
            Row(
                modifier = Modifier
                    .testTag("scrollable")
                    .size(100.dp)
                    .horizontalScroll(scrollState)
            ) {
                repeat(10) {
                    Text("Item $it", modifier = Modifier.width(50.dp))
                }
            }
        }

        awaitA11YChanges()

        val element = getScrollableElement()
        assertEquals("scroll", element.style.overflowX)
        assertEquals("hidden", element.style.overflowY)
        assertEquals("horizontal", element.getAttribute("aria-orientation"))
        assertTrue(element.scrollWidth > element.clientWidth)

        element.scrollLeft = 40.0
        val expectedComposePx = (40f * density).toInt()
        awaitCondition("Compose scroll state must follow the DOM scrollLeft") {
            abs(scrollState.value - expectedComposePx) <= 1
        }
    }

    @Test
    fun nestedScrollContainersSyncIndependently() = runApplicationTest {
        val outerState = ScrollState(0)
        val innerState = ScrollState(0)

        createComposeWindow {
            Column(
                modifier = Modifier
                    .testTag("outer")
                    .size(150.dp)
                    .verticalScroll(outerState)
            ) {
                Spacer(Modifier.height(100.dp))
                Column(
                    modifier = Modifier
                        .testTag("inner")
                        .size(100.dp)
                        .verticalScroll(innerState)
                ) {
                    repeat(10) {
                        Text("Inner $it", modifier = Modifier.height(50.dp))
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }

        awaitA11YChanges()
        val outer = getScrollableElement("outer")
        val inner = getScrollableElement("inner")

        outer.scrollTop = 40.0
        awaitCondition("Outer DOM offset must update only the outer Compose state") {
            abs(outerState.value - (40f * density).toInt()) <= 1
        }
        assertEquals(0, innerState.value)

        inner.scrollTop = 30.0
        awaitCondition("Inner DOM offset must update only the inner Compose state") {
            abs(innerState.value - (30f * density).toInt()) <= 1
        }
        assertTrue(abs(outerState.value - (40f * density).toInt()) <= 1)
    }

    @Test
    fun childrenArePositionedInScrolledContentCoordinates() = runApplicationTest {
        // The browser computes AT-initiated "scroll into view" from the DOM layout, so the
        // children of a scroll container must keep their content-space position (independent of
        // the scroll offset), while the container's scroll offset provides the shift.
        val scrollState = ScrollState(0)

        createComposeWindow {
            Column(
                modifier = Modifier
                    .testTag("scrollable")
                    .size(100.dp)
                    .verticalScroll(scrollState)
            ) {
                repeat(10) {
                    Text("Item $it", modifier = Modifier.testTag("item$it").height(50.dp))
                }
            }
        }

        awaitA11YChanges()
        val element = getScrollableElement()
        val item3 = assertNotNull(getShadowRoot().getElementById("item3") as? HTMLElement)

        // Item 3 lives at 150dp in the content coordinate space
        assertTrue(
            abs(item3.offsetTop - 150) <= 2,
            "Item must be positioned at its content-space offset, got ${item3.offsetTop}"
        )

        // After scrolling, the content-space position must not change...
        element.scrollTop = 100.0
        awaitCondition("Compose scroll state must follow the DOM scroll offset") {
            abs(scrollState.value - (100f * density).toInt()) <= 1
        }
        // ...(await the debounced a11y sync applying the new geometry)
        awaitCondition("Item content-space position must be scroll-invariant") {
            abs(item3.offsetTop - 150) <= 2
        }
        // ...so its position visible on the screen is shifted by the scroll offset
        val visibleTop = item3.getBoundingClientRect().top - element.getBoundingClientRect().top
        assertTrue(
            abs(visibleTop - 50.0) <= 2.0,
            "Item visible position must be shifted by the scroll offset, got $visibleTop"
        )
    }

    @Test
    fun lazyListItemIsScrolledIntoViewByBrowser() = runApplicationTest {
        val state = LazyListState()

        createComposeWindow {
            LazyColumn(
                modifier = Modifier.testTag("scrollable").width(100.dp).height(150.dp),
                state = state,
            ) {
                items(100) { index ->
                    Box(Modifier.testTag("item$index").size(100.dp)) {
                        Text(
                            "$index",
                            Modifier.testTag("label$index").align(Alignment.Center),
                        )
                    }
                }
            }
        }

        awaitA11YChanges()
        val container = getScrollableElement()
        val firstBeyondBoundsItem = assertNotNull(
            getShadowRoot().getElementById("item2") as? HTMLElement,
            "The first fully offscreen item must be retained in the A11Y tree",
        )
        val firstBeyondBoundsLabel =
            assertNotNull(getShadowRoot().getElementById("label2") as? HTMLElement)
        assertTrue(
            abs(firstBeyondBoundsItem.offsetTop - 200) <= 2,
            "The retained item must keep its content-space position, " +
                "got offsetTop=${firstBeyondBoundsItem.offsetTop}",
        )
        assertTrue(
            firstBeyondBoundsLabel.offsetTop > 0,
            "The retained item's child must keep its position inside the item, " +
                "got offsetTop=${firstBeyondBoundsLabel.offsetTop}",
        )
        assertNull(
            getShadowRoot().getElementById("item5"),
            "Only the configured number of beyond-bounds items may be retained",
        )

        // Approximates VoiceOver moving to the first fully offscreen retained item.
        scrollIntoView(firstBeyondBoundsItem)
        awaitCondition("Browser scrolling must update the LazyList state") {
            container.scrollTop > 0.0 &&
                (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
        }
        awaitA11YChanges()

        val nextBeyondBoundsItem = assertNotNull(
            getShadowRoot().getElementById("item5") as? HTMLElement,
            "The beyond-bounds window must advance after scrolling",
        )
        assertTrue(
            nextBeyondBoundsItem.offsetTop > container.scrollTop,
            "The next retained item must have meaningful forward content-space geometry, " +
                "itemTop=${nextBeyondBoundsItem.offsetTop}, scrollTop=${container.scrollTop}",
        )

        scrollIntoView(nextBeyondBoundsItem)
        awaitCondition("The next retained item must become visible") {
            val containerBounds = container.getBoundingClientRect()
            val itemBounds = nextBeyondBoundsItem.getBoundingClientRect()
            itemBounds.bottom > containerBounds.top && itemBounds.top < containerBounds.bottom
        }
    }

    @Test
    @Ignore // need something like defaultLazyListBeyondBoundsItemCount but for LazyGrid
    fun lazyGridItemIsScrolledIntoViewByBrowser() = runApplicationTest {
        val state = LazyGridState()

        createComposeWindow {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.testTag("scrollable").width(300.dp).height(150.dp),
                state = state,
            ) {
                items(100) { index ->
                    Box(Modifier.testTag("item$index").size(100.dp)) {
                        Text(
                            "$index",
                            Modifier.testTag("label$index").align(Alignment.Center),
                        )
                    }
                }
            }
        }

        awaitA11YChanges()
        val container = getScrollableElement()
        assertNull(
            getShadowRoot().getElementById("item6"),
            "The third row must not be in the initial A11Y tree",
        )
        val secondRowItem =
            assertNotNull(getShadowRoot().getElementById("item3") as? HTMLElement)

        // Approximates VoiceOver moving to the partially visible second row.
        scrollIntoView(secondRowItem)
        awaitCondition("Browser scrolling must update the LazyGrid state") {
            container.scrollTop > 0.0 &&
                (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
        }
        awaitA11YChanges()

        val thirdRowItem = assertNotNull(
            getShadowRoot().getElementById("item6") as? HTMLElement,
            "Scrolling to the second row must add the next row to the A11Y tree",
        )
        val thirdRowLabel =
            assertNotNull(getShadowRoot().getElementById("label6") as? HTMLElement)
        assertTrue(
            abs(thirdRowItem.offsetTop - 200) <= 2,
            "The newly added item must keep its content-space position, " +
                "got offsetTop=${thirdRowItem.offsetTop}",
        )
        assertTrue(
            thirdRowLabel.offsetTop > 0,
            "The newly added item's child must retain its position inside the item, " +
                "got offsetTop=${thirdRowLabel.offsetTop}",
        )

        // Approximates VoiceOver moving to the newly exposed third row.
        scrollIntoView(thirdRowItem)
        awaitCondition("The third row must become visible") {
            val containerBounds = container.getBoundingClientRect()
            val itemBounds = thirdRowItem.getBoundingClientRect()
            itemBounds.bottom > containerBounds.top && itemBounds.top < containerBounds.bottom
        }
    }

    @Test
    fun lazyGridItemsRemainAlignedWithComposeAfterRepeatedScrolls() = runApplicationTest {
        val state = LazyGridState()

        createComposeWindow {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.testTag("scrollable").size(300.dp),
                state = state,
            ) {
                items(100) { index ->
                    Box(Modifier.testTag("item$index").size(100.dp))
                }
            }
        }

        awaitA11YChanges()
        state.scrollToItem(30)
        awaitA11YChanges()
        state.scrollToItem(60)
        awaitA11YChanges()

        awaitCondition("the second target item must be present in the a11y tree") {
            getShadowRoot().getElementById("item60") != null
        }

        val container = getScrollableElement()
        val item = assertNotNull(getShadowRoot().getElementById("item60") as? HTMLElement)
        val containerBounds = container.getBoundingClientRect()
        val itemBounds = item.getBoundingClientRect()

        assertTrue(
            itemBounds.bottom > containerBounds.top && itemBounds.top < containerBounds.bottom,
            "A visible Compose item must also be inside the a11y scroll container: " +
                "container=[${containerBounds.top}, ${containerBounds.bottom}], " +
                "item=[${itemBounds.top}, ${itemBounds.bottom}], " +
                "scrollTop=${container.scrollTop}, offsetTop=${item.offsetTop}"
        )
    }

    @Test
    fun scrollSizerIsNotDetachedDuringTextUpdates() = runApplicationTest {
        var prefix by mutableStateOf("Before ")
        val scrollState = ScrollState(0)

        createComposeWindow {
            Text(
                text = buildAnnotatedString {
                    append(prefix)
                    withLink(LinkAnnotation.Url("https://www.example.com")) {
                        append("link")
                    }
                    append(" after")
                },
                modifier = Modifier
                    .testTag("scrollableText")
                    .size(100.dp)
                    .verticalScroll(scrollState),
            )
        }

        awaitA11YChanges()
        val textElement = getScrollableElement("scrollableText")
        val sizer = assertNotNull(textElement.firstElementChild as? HTMLElement)
        val link = assertNotNull(textElement.querySelector("[role=link]") as? HTMLElement)
        var sizerWasRemoved = false
        var linkWasRemoved = false
        val observer = createMutationObserver { removedNode ->
            if (removedNode === sizer) sizerWasRemoved = true
            if (removedNode === link) linkWasRemoved = true
        }
        observeChildListMutations(observer, textElement)

        try {
            prefix = "Updated before "
            awaitA11YChanges()
            awaitAnimationFrame()

            assertSame(sizer, textElement.firstElementChild)
            assertSame(link, textElement.querySelector("[role=link]"))
            assertFalse(sizerWasRemoved, "The scroll sizer must remain attached during text updates")
            assertFalse(linkWasRemoved, "The link must remain attached during text updates")
        } finally {
            disconnectMutationObserver(observer)
        }
    }

    @Test
    fun scrollContainerIsCleanedUpWhenScrollabilityIsRemoved() = runApplicationTest {
        val scrollState = ScrollState(0)
        var scrollable by mutableStateOf(true)

        createComposeWindow {
            Column(
                modifier = Modifier
                    .testTag("scrollable")
                    .size(100.dp)
                    .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
            ) {
                repeat(10) {
                    Text("Item $it", modifier = Modifier.height(50.dp))
                }
            }
        }

        awaitA11YChanges()
        val element = getScrollableElement()
        assertEquals("scroll", element.style.overflowY)
        assertNotNull(element.firstElementChild?.getAttribute("aria-hidden"))

        scrollable = false
        awaitA11YChanges()

        assertEquals("", element.style.overflowY, "overflow must be reset when the node stops being scrollable")
        assertEquals("", element.style.overflowX)
        assertNull(element.getAttribute("aria-orientation"))
        assertNull(
            element.querySelector("[aria-hidden]"),
            "The sizer must be removed when the node stops being scrollable"
        )
    }
}

private fun scrollIntoView(element: HTMLElement) {
    js("element.scrollIntoView({ block: 'nearest', inline: 'nearest' })")
}
