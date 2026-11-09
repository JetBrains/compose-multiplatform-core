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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

class A11yTraversalOrderTest : OnCanvasTests {

    @Test
    fun siblingsAreSortedByGeometry() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().testTag("parent")) {
                Box(Modifier.absoluteOffset(100.dp, 0.dp).size(20.dp).testTag("right"))
                Box(Modifier.absoluteOffset(0.dp, 0.dp).size(20.dp).testTag("left"))
                Box(Modifier.absoluteOffset(0.dp, 40.dp).size(20.dp).testTag("bottom"))
            }
        }
        awaitA11YChanges()

        assertChildOrder("parent", "left", "right", "bottom")
    }

    @Test
    fun traversalIndexOverridesGeometry() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().testTag("parent")) {
                Box(
                    Modifier.absoluteOffset(0.dp, 0.dp)
                        .size(20.dp)
                        .semantics { traversalIndex = 1f }
                        .testTag("firstByGeometry")
                )
                Box(
                    Modifier.absoluteOffset(100.dp, 0.dp)
                        .size(20.dp)
                        .semantics { traversalIndex = -1f }
                        .testTag("firstByTraversalIndex")
                )
            }
        }
        awaitA11YChanges()

        assertChildOrder("parent", "firstByTraversalIndex", "firstByGeometry")
    }

    @Test
    fun rtlSiblingsAreSortedRightToLeft() = runApplicationTest {
        val coordinates = mutableMapOf<String, LayoutCoordinates>()
        fun Modifier.recordCoordinates(tag: String) = onGloballyPositioned {
            coordinates[tag] = it
        }
        createComposeWindow {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                // Absolute offsets need a physical left origin; TopStart would align these
                // children to the right in RTL and push the offset child outside the viewport.
                Box(
                    Modifier.fillMaxSize().testTag("parent"),
                    contentAlignment = AbsoluteAlignment.TopLeft,
                ) {
                    Box(Modifier.absoluteOffset(0.dp, 0.dp).size(20.dp).testTag("left").recordCoordinates("left"))
                    Box(Modifier.absoluteOffset(100.dp, 0.dp).size(20.dp).testTag("right").recordCoordinates("right"))
                }
            }
        }
        awaitA11YChanges()

        val (leftBounds, rightBounds) = listOf("left", "right").map { tag ->
            val nodeCoordinates = coordinates.getValue(tag)
            nodeCoordinates.boundsInWindow().also { bounds ->
                assertFalse(bounds.isEmpty, "$tag must have nonempty bounds")
                assertEquals(nodeCoordinates.boundsInWindow(clipBounds = false), bounds, "$tag must not be clipped")
                assertFalse(element(tag).hasAttribute("inert"), "$tag must be accessibility reachable")
            }
        }
        assertTrue(leftBounds.right <= rightBounds.left, "The left child must be physically left")
        assertEquals(leftBounds.top, rightBounds.top, "Both children must occupy the same row")
        assertEquals(leftBounds.bottom, rightBounds.bottom)
        assertChildOrder("parent", "right", "left")
    }

    @Test
    fun traversalGroupsKeepTheirChildrenTogetherAndSortWithinEachGroup() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().testTag("parent")) {
                Box(
                    Modifier.absoluteOffset(100.dp, 0.dp)
                        .size(100.dp)
                        .semantics { isTraversalGroup = true }
                        .testTag("rightGroup")
                ) {
                    Box(Modifier.absoluteOffset(40.dp, 0.dp).size(20.dp).testTag("rightGroupRight"))
                    Box(Modifier.absoluteOffset(0.dp, 0.dp).size(20.dp).testTag("rightGroupLeft"))
                }
                Box(
                    Modifier.absoluteOffset(0.dp, 0.dp)
                        .size(100.dp)
                        .semantics { isTraversalGroup = true }
                        .testTag("leftGroup")
                ) {
                    Box(Modifier.absoluteOffset(40.dp, 0.dp).size(20.dp).testTag("leftGroupRight"))
                    Box(Modifier.absoluteOffset(0.dp, 0.dp).size(20.dp).testTag("leftGroupLeft"))
                }
            }
        }
        awaitA11YChanges()

        assertChildOrder("parent", "leftGroup", "rightGroup")
        assertChildOrder("leftGroup", "leftGroupLeft", "leftGroupRight")
        assertChildOrder("rightGroup", "rightGroupLeft", "rightGroupRight")
    }

    @Test
    fun geometryChangesReorderChildrenWithoutReplacingTheirElements() = runApplicationTest {
        var swapped by mutableStateOf(false)
        createComposeWindow {
            Box(Modifier.fillMaxSize().testTag("parent")) {
                Box(
                    Modifier.absoluteOffset(if (swapped) 100.dp else 0.dp, 0.dp)
                        .size(20.dp)
                        .testTag("first")
                )
                Box(
                    Modifier.absoluteOffset(if (swapped) 0.dp else 100.dp, 0.dp)
                        .size(20.dp)
                        .testTag("second")
                )
            }
        }
        awaitA11YChanges()

        val first = element("first")
        val second = element("second")
        assertChildOrder("parent", "first", "second")

        swapped = true
        awaitA11YChanges()

        assertChildOrder("parent", "second", "first")
        assertSame(first, element("first"))
        assertSame(second, element("second"))
    }

    private fun assertChildOrder(parentTag: String, vararg childTags: String) {
        val parent = element(parentTag)
        assertEquals(
            childTags.toList(),
            (0 until parent.children.length).mapNotNull { parent.children[it]?.id },
        )
    }

    private fun element(tag: String): HTMLElement = assertNotNull(
        getShadowRoot().getElementById(tag) as? HTMLElement,
        "No A11Y element for '$tag'",
    )
}
