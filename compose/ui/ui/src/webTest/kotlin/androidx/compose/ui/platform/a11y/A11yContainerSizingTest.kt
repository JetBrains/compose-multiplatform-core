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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Regression test for https://youtrack.jetbrains.com/issue/CMP-10172.
 *
 * The a11y root container (`cmp_a11y_root`) was created with `position: absolute` but never
 * given a width or height, leaving it as a 0×0 element in the DOM. Because hit-test-based
 * accessibility tools (Apple Accessibility Inspector, Appium) walk down from the parent's
 * bounding rect, they could not reach any Compose semantic node. VoiceOver was unaffected
 * because it traverses the DOM tree sequentially, which masked the bug.
 */
class A11yContainerSizingTest : OnCanvasTests {

    @Test
    fun a11yContainerHasNonZeroRenderedSizeAfterInit() = runTest {
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
}
