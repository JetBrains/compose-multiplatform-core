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

package androidx.compose.ui.window

import androidx.compose.ui.OnCanvasTests
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.HTMLElement

class ComposeViewportSizeTest : OnCanvasTests {

    @Test
    fun backingStoreSizeDoesNotAffectCanvasCssSize() = runApplicationTest {
        val container = getContainer() as HTMLElement
        val originalContainerStyle = container.style.cssText

        container.style.apply {
            width = "801px"
            height = "600px"
            padding = "0"
            border = "0"
        }

        createComposeWindow { /* The content doesn't matter for this test */ }

        val canvas = getCanvas()
        val containerBounds = container.getBoundingClientRect()
        val before = canvas.getBoundingClientRect()
        val originalHeight = canvas.height

        assertEquals(containerBounds.width, before.width, absoluteTolerance = 0.01)
        assertEquals(containerBounds.height, before.height, absoluteTolerance = 0.01)

        // Change the canvas' backing store size.
        canvas.height = originalHeight + 10

        // Flush pending layout and read the resulting CSS dimensions.
        val after = canvas.getBoundingClientRect()

        assertEquals(before.width, after.width, absoluteTolerance = 0.01)
        assertEquals(before.height, after.height, absoluteTolerance = 0.01)
    }
}
