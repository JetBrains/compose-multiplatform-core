/*
 * Copyright 2024 The Android Open Source Project
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
import androidx.compose.ui.sendFromScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.browser.window
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.w3c.dom.events.Event

class ComposeWindowLifecycleTest : OnCanvasTests {
    @Test
    fun allEvents() = runTest {
        val eventsChannel = Channel<Lifecycle.Event>(10)
        createComposeWindow {
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            lifecycle.addObserver(object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    eventsChannel.sendFromScope(event)
                }
            })
        }

        assertEquals(Lifecycle.State.CREATED, eventsChannel.receive().targetState)
        assertEquals(Lifecycle.State.STARTED, eventsChannel.receive().targetState)

        // Dispatch artificial events that would be sent when the window gains and loses focus.
        // Starting with a focus event before checking for the initial RESUMED makes this test
        // robust in the face of both an already-focused window and a non-focused window. Then,
        // a blur plus focus cycle simulates losing focus and regaining it.
        window.dispatchEvent(Event("focus"))
        assertEquals(Lifecycle.State.RESUMED, eventsChannel.receive().targetState)
        window.dispatchEvent(Event("blur"))
        assertEquals(Lifecycle.State.STARTED, eventsChannel.receive().targetState)
        window.dispatchEvent(Event("focus"))
        assertEquals(Lifecycle.State.RESUMED, eventsChannel.receive().targetState)

        // Destroy the ComposeWindow by removing its host container from the DOM.
        val host = getShadowRoot().host
        host.parentNode?.removeChild(host)
        assertEquals(Lifecycle.State.STARTED, eventsChannel.receive().targetState)
        assertEquals(Lifecycle.State.CREATED, eventsChannel.receive().targetState)
        assertEquals(Lifecycle.State.DESTROYED, eventsChannel.receive().targetState)
    }
}
