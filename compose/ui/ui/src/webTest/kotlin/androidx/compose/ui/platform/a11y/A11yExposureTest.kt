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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.WebApplicationScope
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.w3c.dom.HTMLElement

/** Regression coverage for basic Web A11Y exposure independently from DOM geometry. */
class A11yExposureTest : OnCanvasTests {
    private fun element(tag: String): HTMLElement =
        assertNotNull(
            getShadowRoot().getElementById(tag) as? HTMLElement,
            "No A11Y element for '$tag'",
        )

    private suspend fun WebApplicationScope.awaitCondition(
        message: String,
        condition: () -> Boolean,
    ) {
        val start = currentTimeMillis()
        while (!condition()) {
            assertTrue(currentTimeMillis() - start < 5_000, "Timed out waiting for: $message")
            awaitAnimationFrame()
        }
    }

    @Test
    fun fullyClippedNonScrollNodeIsInertButRetainsMeasuredSize() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Box(Modifier.offset(200.dp, 0.dp).size(40.dp).testTag("panel"))
            }
        }
        awaitA11YChanges()

        val panel = element("panel")
        assertTrue(panel.hasAttribute("inert"))
        assertTrue(panel.getBoundingClientRect().width > 0.0)
        assertTrue(panel.getBoundingClientRect().height > 0.0)
    }

    @Test
    fun clippingTransitionTogglesInertnessWithoutReplacingElement() = runApplicationTest {
        var hidden by mutableStateOf(true)
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Box(
                    Modifier.offset(if (hidden) 200.dp else 0.dp, 0.dp)
                        .size(40.dp)
                        .testTag("panel")
                )
            }
        }
        awaitA11YChanges()
        val panel = element("panel")
        assertTrue(panel.hasAttribute("inert"))

        hidden = false
        awaitA11YChanges()
        assertSame(panel, element("panel"))
        assertFalse(panel.hasAttribute("inert"))

        hidden = true
        awaitA11YChanges()
        assertSame(panel, element("panel"))
        assertTrue(panel.hasAttribute("inert"))
    }

    @Test
    fun descendantOfVisibleScrollContainerRemainsExposed() = runApplicationTest {
        val scrollState = ScrollState(0)
        createComposeWindow {
            Column(Modifier.size(100.dp).verticalScroll(scrollState).testTag("scroller")) {
                Box(Modifier.size(100.dp))
                Box(Modifier.size(40.dp).testTag("item"))
            }
        }
        awaitA11YChanges()

        val scroller = element("scroller")
        val item = element("item")
        assertFalse(scroller.hasAttribute("inert"))
        assertFalse(item.hasAttribute("inert"))
        assertTrue(item.getBoundingClientRect().height > 0.0)

        scrollIntoView(item)
        awaitCondition("Browser scrolling must reach the exposed offscreen item") {
            scrollState.value > 0
        }
        assertSame(item, element("item"))
    }

    @Test
    fun hiddenScrollableSubtreeCannotReactivateDescendants() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Column(
                    Modifier.offset(200.dp, 0.dp)
                        .size(100.dp)
                        .verticalScroll(ScrollState(0))
                        .testTag("hiddenScroller")
                ) {
                    Box(Modifier.size(40.dp).testTag("hiddenItem"))
                }
            }
        }
        awaitA11YChanges()

        assertTrue(element("hiddenScroller").hasAttribute("inert"))
        assertTrue(element("hiddenItem").hasAttribute("inert"))
    }

    @Test
    fun partiallyClippedAndZeroSizedNodesRemainExposed() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Box(Modifier.offset(80.dp, 0.dp).size(40.dp).testTag("partial"))
                Box(Modifier.size(0.dp).testTag("zero"))
                Box(Modifier.offset(200.dp, 0.dp).size(40.dp).testTag("hidden")) {
                    Box(Modifier.size(0.dp).testTag("hiddenZero"))
                }
            }
        }
        awaitA11YChanges()

        assertNull(element("partial").closest("[inert]"))
        assertEquals("40px", element("partial").style.width)
        assertEquals("40px", element("partial").style.height)
        assertNull(element("zero").closest("[inert]"))
        assertNotNull(element("hiddenZero").closest("[inert]"))
    }
}

private fun scrollIntoView(element: HTMLElement) {
    js("element.scrollIntoView({ block: 'nearest', inline: 'nearest' })")
}
