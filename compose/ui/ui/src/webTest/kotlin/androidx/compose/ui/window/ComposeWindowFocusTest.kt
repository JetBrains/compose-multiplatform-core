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
import androidx.lifecycle.Lifecycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import org.w3c.dom.events.FocusEvent

class ComposeWindowFocusTest : OnCanvasTests {

    @Test
    fun isWindowFocusedUpdatesOnGlobalBlurAndFocus() = runTest {
        createComposeWindow {}

        val lifecycle = getComposeWindowOrNull()!!.archComponentsOwner.lifecycle

        // In headless browsers, document.hasFocus() may be false,
        // and we don't want to mimic this very basic behaviour - tests immediately can get unexpectedly flaky
        // so ensure we start from RESUMED by dispatching a focus event first.
        window.dispatchEvent(FocusEvent("focus"))
        assertEquals(Lifecycle.State.RESUMED, lifecycle.currentState)

        window.dispatchEvent(FocusEvent("blur"))
        assertEquals(Lifecycle.State.STARTED, lifecycle.currentState)

        window.dispatchEvent(FocusEvent("focus"))
        assertEquals(Lifecycle.State.RESUMED, lifecycle.currentState)
    }
}
