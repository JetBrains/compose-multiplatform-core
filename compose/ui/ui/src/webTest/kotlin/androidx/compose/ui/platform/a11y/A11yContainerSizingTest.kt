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

import androidx.compose.material.Text
import androidx.compose.ui.OnCanvasTests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Regression tests for https://youtrack.jetbrains.com/issue/CMP-10172.
 *
 * The a11y root container (`cmp_a11y_root`) was created with `position: absolute` but never
 * given a width or height, leaving it as a 0×0 element in the DOM. Because hit-test-based
 * accessibility tools (Apple Accessibility Inspector, Appium) walk down from the parent's
 * bounding rect, they could not reach any Compose semantic node. VoiceOver was unaffected
 * because it traverses the DOM tree sequentially, which masked the bug.
 *
 * These tests guard the contract that the a11y container's CSS dimensions stay in sync with
 * the underlying `<canvas>`.
 */
class A11yContainerSizingTest : OnCanvasTests {

    @Test
    fun a11yContainerHasNonZeroDimensionsAfterInit() = runTest {
        createComposeWindow {
            Text("a11y sizing regression")
        }

        val a11yContainer = assertNotNull(
            getA11YContainer(),
            "A11Y container must exist when isA11YEnabled is true (default)"
        )

        val width = a11yContainer.style.width
        val height = a11yContainer.style.height

        assertTrue(
            width.isNotEmpty() && width != "0px",
            "a11y container width must be set to a non-zero pixel value, was '$width'"
        )
        assertTrue(
            height.isNotEmpty() && height != "0px",
            "a11y container height must be set to a non-zero pixel value, was '$height'"
        )
    }

    @Test
    fun a11yContainerMatchesCanvasCssDimensions() = runTest {
        createComposeWindow {
            Text("a11y sizing regression")
        }

        val canvas = getCanvas()
        val a11yContainer = assertNotNull(
            getA11YContainer(),
            "A11Y container must exist when isA11YEnabled is true (default)"
        )

        assertEquals(
            canvas.style.width,
            a11yContainer.style.width,
            "a11y container width must match the canvas's CSS width so hit-test-based " +
                "tools can reach Compose semantic nodes"
        )
        assertEquals(
            canvas.style.height,
            a11yContainer.style.height,
            "a11y container height must match the canvas's CSS height so hit-test-based " +
                "tools can reach Compose semantic nodes"
        )
    }
}
