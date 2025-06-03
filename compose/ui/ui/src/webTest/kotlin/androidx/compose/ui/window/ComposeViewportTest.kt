/*
 * Copyright 2025 The Android Open Source Project
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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.containerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.browser.document
import org.w3c.dom.get

class ComposeViewportTest: OnCanvasTests {

    @Test
    fun canCreateAndDispose() = runApplicationTest {
        val disposeCallback = ComposeViewport(containerId, content = {
            Box(modifier = Modifier.fillMaxSize()) {
                Text("Hello, World!")
            }
        })
        awaitIdle()

        val container = document.getElementById(containerId)!!
        val canvas = container.children.get(0)
        assertNotNull(canvas, "Canvas is not created")
        assertEquals("CANVAS", canvas.tagName)

        disposeCallback.dispose()
        awaitIdle()

        assertEquals(0, container.childElementCount)
    }
}