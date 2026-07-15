/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.window.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.runApplicationTest
import com.google.common.truth.Truth.assertThat
import java.awt.event.WindowEvent
import org.junit.Test

class WindowParameterTest {
    @Test
    fun `change title`() = runApplicationTest {
        var window: ComposeWindow? = null

        var title by mutableStateOf("Title1")

        launchTestApplication {
            Window(onCloseRequest = ::exitApplication, title = title) {
                window = this.window
                Box(Modifier.size(32.dp).background(Color.Red))
            }
        }

        awaitIdle()
        assertThat(window?.title).isEqualTo("Title1")

        title = "Title2"
        awaitIdle()
        assertThat(window?.title).isEqualTo("Title2")

        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }

    // `change icon` was removed: it asserted on Painter-based window icon rendering via
    // Window(icon = ...). That parameter was replaced by `icons: List<ByteArray>`, which is never
    // forwarded to the underlying SwingWindow (Window.desktop.kt hardcodes `icon = null`), so the
    // behaviour this test asserted does not exist in this fork. Restore this test once `icons` is
    // wired up.

    // Swing doesn't support changing isUndecorated
    @Test
    fun `set undecorated`() = runApplicationTest {
        var window: ComposeWindow? = null

        launchTestApplication {
            Window(onCloseRequest = ::exitApplication) {
                window = this.window
                Box(Modifier.size(32.dp).background(Color.Red))
            }
        }

        awaitIdle()
        assertThat(window?.isUndecorated).isFalse()

        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }

    @Test
    fun `change undecorated`() = runApplicationTest {
        var window: ComposeWindow? = null

        var resizable by mutableStateOf(false)

        launchTestApplication {
            Window(onCloseRequest = ::exitApplication, resizable = resizable) {
                window = this.window
                Box(Modifier.size(32.dp).background(Color.Red))
            }
        }

        awaitIdle()
        assertThat(window?.isResizable).isFalse()

        resizable = true
        awaitIdle()
        assertThat(window?.isResizable).isTrue()

        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }

    @Test
    fun `change enabled`() = runApplicationTest {
        var window: ComposeWindow? = null

        var enabled by mutableStateOf(false)

        launchTestApplication {
            Window(onCloseRequest = ::exitApplication, enabled = enabled) {
                window = this.window
                Box(Modifier.size(32.dp).background(Color.Red))
            }
        }

        awaitIdle()
        assertThat(window?.isEnabled).isFalse()

        enabled = true
        awaitIdle()
        assertThat(window?.isEnabled).isTrue()

        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }

    @Test
    fun `change focusable`() = runApplicationTest {
        var window: ComposeWindow? = null

        var focusable by mutableStateOf(false)

        launchTestApplication {
            Window(onCloseRequest = ::exitApplication, focusable = focusable) {
                window = this.window
                Box(Modifier.size(32.dp).background(Color.Red))
            }
        }

        awaitIdle()
        assertThat(window?.isFocusableWindow).isFalse()

        focusable = true
        awaitIdle()
        assertThat(window?.isFocusableWindow).isTrue()

        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }

    @Test
    fun `change alwaysOnTop`() = runApplicationTest {
        var window: ComposeWindow? = null

        var alwaysOnTop by mutableStateOf(false)

        launchTestApplication {
            Window(onCloseRequest = ::exitApplication, alwaysOnTop = alwaysOnTop) {
                window = this.window
                Box(Modifier.size(32.dp).background(Color.Red))
            }
        }

        awaitIdle()
        assertThat(window?.isAlwaysOnTop).isFalse()

        alwaysOnTop = true
        awaitIdle()
        assertThat(window?.isAlwaysOnTop).isTrue()

        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }
}
