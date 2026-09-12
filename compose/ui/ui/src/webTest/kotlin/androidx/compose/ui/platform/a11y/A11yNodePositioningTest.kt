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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.HTMLElement

/**
 * A11Y elements are positioned with absolute (root-relative) coordinates while being nested
 * according to the semantics tree, so a node's rendered rect must not depend on its depth.
 * Positioning them with `transform` breaks this: a transformed element becomes the containing
 * block for its `position: fixed` descendants, so every level re-applies its ancestors' offsets.
 */
class A11yNodePositioningTest : OnCanvasTests {

    @Test
    fun nestedNodeIsRenderedAtTheSameRectAsAnEquivalentFlatNode() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.offset(40.dp, 40.dp).testTag("outer")) {
                    Box(Modifier.offset(30.dp, 30.dp).testTag("middle")) {
                        Box(Modifier.offset(20.dp, 20.dp).size(50.dp).testTag("nested"))
                    }
                }
                Box(Modifier.offset(90.dp, 90.dp).size(50.dp).testTag("flat"))
            }
        }
        awaitA11YChanges()

        assertSameRect("nested", "flat")
    }

    @Test
    fun childOfAnOffsetNodeIsNotShiftedByItsParentOffsetTwice() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.offset(40.dp, 40.dp).size(50.dp).testTag("parent")) {
                    Box(Modifier.size(50.dp).testTag("child"))
                }
            }
        }
        awaitA11YChanges()

        assertSameRect("child", "parent")
    }

    /**
     * Asserts that two nodes occupying the same Compose bounds are rendered at the same rect.
     * Comparing two elements keeps the assert independent of density and page offset.
     */
    private fun assertSameRect(testTag: String, expectedTestTag: String) {
        // Sub-pixel tolerance for device pixel ratio rounding.
        val epsilon = 1.0
        val actual = a11yElement(testTag).getBoundingClientRect()
        val expected = a11yElement(expectedTestTag).getBoundingClientRect()

        assertTrue(expected.width > 0.0 && expected.height > 0.0, "'$expectedTestTag' has no rect")

        assertEquals(expected.left, actual.left, epsilon, "'$testTag' left")
        assertEquals(expected.top, actual.top, epsilon, "'$testTag' top")
        assertEquals(expected.width, actual.width, epsilon, "'$testTag' width")
        assertEquals(expected.height, actual.height, epsilon, "'$testTag' height")
    }

    // A11Y elements get their TestTag as `id`.
    private fun a11yElement(testTag: String): HTMLElement = assertNotNull(
        getShadowRoot().getElementById(testTag) as? HTMLElement,
        "no a11y element for '$testTag' in ${getA11YContainer()?.innerHTML}"
    )
}
