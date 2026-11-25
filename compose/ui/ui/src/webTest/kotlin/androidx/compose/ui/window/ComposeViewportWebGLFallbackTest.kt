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
import kotlin.js.js
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ComposeViewportWebGLFallbackTest : OnCanvasTests {

    @Test
    fun displaysMessageWhenCanvasCannotCreateWebGLContext() = runApplicationTest {
        val viewportContainer = getContainer()
        assertEquals("", viewportContainer.innerHTML)

        mockCanvasGetContextReturningNull()
        try {
            var called = false

            ComposeViewport(viewportContainer = viewportContainer) {
                called = true
            }

            awaitCondition("WebGL2 unsupported message") {
                getContainer().textContent == WEBGL2_NOT_SUPPORTED_MSG
            }

            assertEquals(WEBGL2_NOT_SUPPORTED_MSG, getContainer().textContent)
            assertFalse(called, "composable content call is unexpected")
        } finally {
            restoreCanvasGetContext()
        }
    }
}

// language=js
private fun mockCanvasGetContextReturningNull(): Unit = js(
    """
        (function() {
            const prototype = HTMLCanvasElement.prototype;
            prototype.__composeWindowTestOriginalGetContext = prototype.getContext;
            prototype.getContext = function() { return null; };
        })()
    """
)

// language=js
private fun restoreCanvasGetContext(): Unit = js(
    """
        (function() {
            const prototype = HTMLCanvasElement.prototype;
            prototype.getContext = prototype.__composeWindowTestOriginalGetContext;
            prototype.__composeWindowTestOriginalGetContext = undefined;
        })()
    """
)
