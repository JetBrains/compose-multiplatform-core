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

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.runHeadlessComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.window.WindowTestScope
import androidx.compose.ui.window.runApplicationTest
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

@OptIn(ExperimentalTestApi::class)
class ClipboardTest {

    var clipboard: Clipboard? = null
    var awtClipboard: java.awt.datatransfer.Clipboard? = null

    @Before
    fun setup() {
        clipboard = null
        awtClipboard = null
    }

    @Test
    fun hasClipboard() = clipboardTest {
        assertNotNull(clipboard)
        assertNotNull(clipboard!!.nativeClipboard)
        assertTrue(clipboard!!.nativeClipboard is java.awt.datatransfer.Clipboard)
        assertNotNull(awtClipboard)
    }

    @Test
    fun makeClipboardEmpty() = clipboardTest {
        clipboard!!.setClipEntry(null)
        assertNull(clipboard!!.getClipEntry())
        assertTrue(awtClipboard!!.availableDataFlavors.isEmpty())
    }

    @Test
    fun setTextToClipboard() = clipboardTest {
        clipboard!!.setClipEntry(null)
        assertNull(clipboard!!.getClipEntry())
        assertTrue(awtClipboard!!.availableDataFlavors.isEmpty())

        clipboard!!.setClipEntry(ClipEntry(StringSelection("test")))
        assertEquals("test", awtClipboard!!.getData(DataFlavor.stringFlavor))

        val ce = clipboard!!.getClipEntry()
        assertNotNull(ce)
        assertEquals("test", ce.asAwtTransferable!!.getTransferData(DataFlavor.stringFlavor))
        assertTrue(ce.clipMetadata.hasPlainText())
        assertEquals("test", ce.readPlainText())

        clipboard!!.setClipEntry(null)
        assertNull(clipboard!!.getClipEntry())
        assertTrue(awtClipboard!!.availableDataFlavors.isEmpty())
    }

    @Test
    fun clipEntry_withText_readsPlainTextAndHtml() = runTest {
        val clipEntry = ClipEntry.withText("plain text", "<b>plain text</b>")

        assertEquals("plain text", clipEntry.readText())
        assertEquals("plain text", clipEntry.readPlainText())
        assertEquals("<b>plain text</b>", clipEntry.readHtml())
        assertNull(clipEntry.readUrl())
        val metadata = clipEntry.clipMetadata
        assertSame(metadata, clipEntry.clipMetadata)
        assertTrue(metadata.hasText())
        assertTrue(metadata.hasPlainText())
        assertTrue(metadata.hasHtml())
        assertFalse(metadata.hasUrl())
    }

    @Test
    fun clipEntry_withUrl_readsAllRepresentations() = runTest {
        val url = "https://example.com"
        val plainText = "Example"
        val html = "<a href=\"$url\">Example</a>"
        val clipEntry = ClipEntry.withUrl(url, plainText, html)

        assertEquals(plainText, clipEntry.readText())
        assertEquals(plainText, clipEntry.readPlainText())
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

    @Test
    fun clipEntry_fromStringSelection_readsPlainText() = runTest {
        val clipEntry = ClipEntry(StringSelection("test"))

        assertEquals("test", clipEntry.readText())
        assertEquals("test", clipEntry.readPlainText())
        assertNull(clipEntry.readHtml())
        assertNull(clipEntry.readUrl())
        assertTrue(clipEntry.clipMetadata.hasPlainText())
        assertFalse(clipEntry.clipMetadata.hasHtml())
        assertFalse(clipEntry.clipMetadata.hasUrl())
    }

    @Test
    fun clipboardRoundTripsClipEntry() = runTest {
        val awtClipboard = java.awt.datatransfer.Clipboard("test")
        val clipboard = AwtPlatformClipboard(awtClipboard)
        val url = "https://example.com"
        val plainText = "Example"
        val html = "<a href=\"$url\">Example</a>"

        clipboard.setClipEntry(ClipEntry.withUrl(url, plainText, html))
        val clipEntry = assertNotNull(clipboard.getClipEntry())
        assertEquals(plainText, clipEntry.readPlainText())
        assertEquals(html, clipEntry.readHtml())
        assertEquals(url, clipEntry.readUrl())

        clipboard.setClipEntry(clipEntry)
        val copiedClipEntry = assertNotNull(clipboard.getClipEntry())
        assertEquals(plainText, copiedClipEntry.readPlainText())
        assertEquals(html, copiedClipEntry.readHtml())
        assertEquals(url, copiedClipEntry.readUrl())

        clipboard.setClipEntry(null)
        assertNull(clipboard.getClipEntry())
    }

    private fun clipboardTest(block: suspend WindowTestScope.() -> Unit) = runApplicationTest {
        assertNull(clipboard)

        val window = ComposeWindow()
        try {
            window.size = Dimension(300, 400)
            window.setContent {
                clipboard = LocalClipboard.current
                awtClipboard = clipboard!!.nativeClipboard as java.awt.datatransfer.Clipboard
            }
            window.isUndecorated = true
            window.isVisible = true
            window.paint(window.graphics)
            awaitIdle()
            block()
        } finally {
            window.dispose()
        }
    }

    @Test
    @Category(HeadlessTest::class)
    fun nativeClipboardDoesNotCrash() = runHeadlessComposeUiTest {
        lateinit var clipboard: Clipboard
        setContent {
            clipboard = LocalClipboard.current
        }

        clipboard.nativeClipboard  // Just check it doesn't crash
        assertEquals(null, clipboard.awtClipboard)
    }
}