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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalTestApi::class)
class UiKitPlatformClipboardTest {

    @Test
    fun clipEntry_withText_readsPlainTextAndHtml() = runTest {
        val clipEntry = ClipEntry.withText("plain text", "<b>plain text</b>")

        assertEquals("plain text", clipEntry.readText())
        assertEquals("plain text", clipEntry.readPlainText())
        assertEquals("<b>plain text</b>", clipEntry.readHtml())
        assertNull(clipEntry.readUrl())
        assertTrue(clipEntry.clipMetadata.hasText())
        assertTrue(clipEntry.clipMetadata.hasPlainText())
        assertTrue(clipEntry.clipMetadata.hasHtml())
        assertFalse(clipEntry.clipMetadata.hasUrl())
    }

    @Test
    fun clipEntry_withUrl_readsAllRepresentations() = runTest {
        val url = "https://example.com"
        val html = "<a href=\"$url\">Example</a>"
        val clipEntry = ClipEntry.withUrl(url, "Example", html)

        assertEquals("Example", clipEntry.readText())
        assertEquals("Example", clipEntry.readPlainText())
        assertEquals(html, clipEntry.readHtml())
        assertEquals(url, clipEntry.readUrl())
        assertTrue(clipEntry.clipMetadata.hasText())
        assertTrue(clipEntry.clipMetadata.hasPlainText())
        assertTrue(clipEntry.clipMetadata.hasHtml())
        assertTrue(clipEntry.clipMetadata.hasUrl())
    }

    @Test
    fun clipEntry_withUrlWithoutPlainText_readsHtmlThenUrl() = runTest {
        val url = "https://example.com"
        val html = "<a href=\"$url\">Example</a>"
        val clipEntry = ClipEntry.withUrl(url, html = html)

        assertEquals(html, clipEntry.readText())
        assertNull(clipEntry.readPlainText())
        assertEquals(html, clipEntry.readHtml())
        assertEquals(url, clipEntry.readUrl())
    }

    @Test
    fun clipEntry_withUrlWithoutFallback_readsUrlAsText() = runTest {
        val url = "https://example.com"
        val clipEntry = ClipEntry.withUrl(url)

        assertEquals(url, clipEntry.readText())
        assertNull(clipEntry.readPlainText())
        assertNull(clipEntry.readHtml())
        assertEquals(url, clipEntry.readUrl())
        assertTrue(clipEntry.clipMetadata.hasText())
        assertFalse(clipEntry.clipMetadata.hasPlainText())
        assertFalse(clipEntry.clipMetadata.hasHtml())
        assertTrue(clipEntry.clipMetadata.hasUrl())
    }

    // TODO: consider writing instrumented tests for Clipboard
    // The unit tests can't use (copy/paste) the native UIPasteboard:
    // "Cannot connect to pasteboard server" - an error at runtime

    @Suppress("DEPRECATION")
    @Test
    fun canGetLocalClipboard() = runComposeUiTest {
        var clipboard: Clipboard? = null
        var nativeClipboard: NativeClipboard? = null

        setContent {
            clipboard = LocalClipboard.current
            nativeClipboard = clipboard?.nativeClipboard
        }

        waitForIdle()

        assertNotNull(clipboard)
        assertNotNull(nativeClipboard)
    }

    @Suppress("DEPRECATION")
    @Test
    fun smokeTestUseClipboardMethods() = runComposeUiTest {
        var clipboard: Clipboard? = null
        var nativeClipboard: NativeClipboard? = null

        setContent {
            clipboard = LocalClipboard.current
            nativeClipboard = clipboard?.nativeClipboard
        }

        waitForIdle()

        assertNotNull(clipboard)
        assertNotNull(nativeClipboard)

        runBlocking {
            // Can't do much more asserts in a unit test. For more checks use an instrumented test
            clipboard!!.setClipEntry(null)
            clipboard!!.setClipEntry(ClipEntry.withPlainText("test"))
            clipboard!!.getClipEntry()
        }
    }
}