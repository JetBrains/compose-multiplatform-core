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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
