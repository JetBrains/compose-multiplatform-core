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

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.OnCanvasTests
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

/**
 * Regression tests for https://youtrack.jetbrains.com/issue/CMP-10172.
 *
 * The a11y root container (`cmp_a11y_root`) was created with `position: absolute` but never
 * given a width or height, leaving it as a 0×0 element in the DOM. Hit-test-based accessibility
 * tools (Apple Accessibility Inspector, Appium) resolve elements by walking down from a
 * container's bounding rect, so a 0×0 container meant every Compose semantic node was
 * unreachable. VoiceOver was unaffected because it traverses the DOM tree sequentially, which
 * masked the bug.
 *
 * The container is intentionally invisible (`opacity: 0`) and non-interactive
 * (`pointer-events: none`) so it never intercepts real pointer input, which is why these tests
 * assert on layout geometry (`getBoundingClientRect()`) — the same information native
 * accessibility hit-testing relies on — rather than on CSS `elementFromPoint`.
 */
class A11yContainerSizingTest : OnCanvasTests {

    // Rendered geometry can differ from the canvas by sub-pixel amounts due to device pixel
    // ratio rounding, so comparisons allow a small tolerance.
    private val epsilon = 1.0

    @Test
    fun a11yContainerHasNonZeroRenderedSizeAfterInit() = runApplicationTest {
        createComposeWindow {
            Text("a11y sizing regression")
        }

        val a11yContainer = assertNotNull(
            getA11YContainer(),
            "A11Y container must exist when isA11YEnabled is true (default)"
        )

        val rect = a11yContainer.getBoundingClientRect()

        assertTrue(
            rect.width > 0.0,
            "a11y container rendered width must be non-zero, was ${rect.width}"
        )
        assertTrue(
            rect.height > 0.0,
            "a11y container rendered height must be non-zero, was ${rect.height}"
        )
    }

    /**
     * The core CMP-10172 contract: the a11y container must cover the same region as the canvas
     * so hit-test-based tools scanning the rendered content land inside the a11y subtree instead
     * of missing it entirely. Before the fix the container was 0×0 while the canvas had a real
     * size, so this comparison would fail.
     */
    @Test
    fun a11yContainerCoversCanvasForHitTesting() = runApplicationTest {
        createComposeWindow {
            Text("a11y hit region regression")
        }

        val canvasRect = getCanvas().getBoundingClientRect()
        val containerRect = assertNotNull(
            getA11YContainer(),
            "A11Y container must exist when isA11YEnabled is true (default)"
        ).getBoundingClientRect()

        assertTrue(
            canvasRect.width > 0.0 && canvasRect.height > 0.0,
            "canvas must have a real size for this test to be meaningful, was " +
                "${canvasRect.width}x${canvasRect.height}"
        )

        // Same footprint as the canvas (this is what breaks when the container is 0×0).
        assertTrue(
            kotlin.math.abs(containerRect.width - canvasRect.width) <= epsilon,
            "a11y container width (${containerRect.width}) must match canvas width " +
                "(${canvasRect.width})"
        )
        assertTrue(
            kotlin.math.abs(containerRect.height - canvasRect.height) <= epsilon,
            "a11y container height (${containerRect.height}) must match canvas height " +
                "(${canvasRect.height})"
        )

        // Fully overlaps the canvas region, so every point over the content is inside the
        // container's hit region.
        assertTrue(
            containerRect.left <= canvasRect.left + epsilon &&
                containerRect.top <= canvasRect.top + epsilon &&
                containerRect.right >= canvasRect.right - epsilon &&
                containerRect.bottom >= canvasRect.bottom - epsilon,
            "a11y container [${containerRect.left}, ${containerRect.top}, ${containerRect.right}, " +
                "${containerRect.bottom}] must cover canvas [${canvasRect.left}, ${canvasRect.top}, " +
                "${canvasRect.right}, ${canvasRect.bottom}]"
        )
    }

    /**
     * End-to-end sanity check: a real semantic node (a Button) is emitted into the a11y tree with
     * a non-zero rect that sits inside the container's now-sized hit region, i.e. a hit-test tool
     * scanning that area can reach the node.
     */
    @Test
    fun semanticNodeLiesWithinA11yHitRegion() = runApplicationTest {
        var showButton by mutableStateOf(false)

        createComposeWindow {
            if (showButton) {
                Button(onClick = {}) {
                    Text("Hittable")
                }
            }
        }

        val a11yContainer = assertNotNull(getA11YContainer())

        showButton = true
        awaitA11YChanges()

        val button = a11yContainer.children[0]?.children[0] as? HTMLElement
        assertNotNull(button, "expected a semantic node for the Button")

        val containerRect = a11yContainer.getBoundingClientRect()
        val buttonRect = button.getBoundingClientRect()

        assertTrue(
            buttonRect.width > 0.0 && buttonRect.height > 0.0,
            "semantic node must have a non-zero rect, was ${buttonRect.width}x${buttonRect.height}"
        )
        assertTrue(
            buttonRect.left >= containerRect.left - epsilon &&
                buttonRect.top >= containerRect.top - epsilon &&
                buttonRect.right <= containerRect.right + epsilon &&
                buttonRect.bottom <= containerRect.bottom + epsilon,
            "semantic node [${buttonRect.left}, ${buttonRect.top}, ${buttonRect.right}, " +
                "${buttonRect.bottom}] must lie within the a11y container hit region " +
                "[${containerRect.left}, ${containerRect.top}, ${containerRect.right}, " +
                "${containerRect.bottom}]"
        )
    }
}
