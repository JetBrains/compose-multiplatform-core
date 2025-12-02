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

package androidx.compose.ui.platform

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import java.awt.GraphicsEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.BeforeClass
import org.junit.Test


@OptIn(ExperimentalTestApi::class)
class HeadlessClipboardTest {

    companion object {

        // Typically this is set in Gradle, but also set it here to allow adhoc running the tests
        // from the IDE
        @JvmStatic
        @BeforeClass
        fun setUpHeadless() {
            System.setProperty("java.awt.headless", "true")
        }

    }

    @Test
    fun nativeClipboardDoesNotCrash() = runHeadlessComposeUiTest {
        lateinit var clipboard: Clipboard
        setContent {
            clipboard = LocalClipboard.current
        }

        clipboard.nativeClipboard  // Just check it doesn't crash
        assertEquals(null, clipboard.awtClipboard)
    }
}


@OptIn(ExperimentalTestApi::class)
internal fun runHeadlessComposeUiTest(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    assertTrue(GraphicsEnvironment.isHeadless(), "This is a headless test, but it's run not in headless mode")
    block()
}