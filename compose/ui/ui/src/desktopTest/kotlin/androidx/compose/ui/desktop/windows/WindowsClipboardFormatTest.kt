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

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.desktop.ClipboardElement
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.desktop.ClipboardItem
import androidx.compose.ui.desktop.LightweightWindowId
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * `toWindowsClipboardItems()` is the pure classification half of the OLE clipboard: it maps the
 * shared [ClipboardFormat]/[ClipboardItem] currency onto the [WindowsClipboardItem] the native
 * `DataObjectBuilder` consumes, with no native (OLE) access. The native half — `Clipboard.get/set`
 * and `DataObject` reads/writes, and `toWin32DataFormat()` which registers Win32 clipboard formats
 * through an FFI down-call — is only exercisable on Windows and is not covered here.
 */
@Category(HeadlessTest::class)
class WindowsClipboardFormatTest {

    @Test
    fun mapsTextHtmlFileAndPng() {
        val mapped = listOf(
            ClipboardItem("plain text", ClipboardFormat.Utf8PlainText),
            ClipboardItem("<b>bold</b>", ClipboardFormat.Html),
            ClipboardItem("/tmp/a.txt", ClipboardFormat.File),
            ClipboardItem(byteArrayOf(1, 2, 3), ClipboardFormat.Png),
        ).toWindowsClipboardItems()

        assertEquals(4, mapped.size)
        assertEquals(WindowsClipboardItem.Text("plain text"), mapped[0])
        assertEquals(WindowsClipboardItem.Html("<b>bold</b>"), mapped[1])
        assertEquals(WindowsClipboardItem.Files(listOf("/tmp/a.txt")), mapped[2])

        val png = mapped[3]
        assertTrue(png is WindowsClipboardItem.Png)
        assertContentEquals(byteArrayOf(1, 2, 3), png.bytes)
    }

    @Test
    fun mapsWindowLocalDragToCustomWithFleetWireMimeAndDecimalId() {
        val mapped = listOf(
            ClipboardItem(LightweightWindowId(42L), ClipboardFormat.WindowLocalDrag),
        ).toWindowsClipboardItems()

        val custom = mapped.single()
        assertTrue(custom is WindowsClipboardItem.Custom)
        // The wire MIME must match the cross-backend contract shared with macOS/Linux/GTK.
        assertEquals("org.jetbrains.fleet.window-local-drag", custom.mimeType)
        assertEquals("42", custom.data.decodeToString())
    }

    @Test
    fun mapsCustomSerializableToItsMimeWithJsonEncodedPayload() {
        val format = ClipboardFormat.CustomSerializable("application/x-answer", Int.serializer())

        val mapped = listOf(ClipboardItem(42, format)).toWindowsClipboardItems()

        val custom = mapped.single()
        assertTrue(custom is WindowsClipboardItem.Custom)
        assertEquals("application/x-answer", custom.mimeType)
        // CustomSerializable encodes via the format's own JSON serializer.
        assertEquals(format.encode(42), custom.data.decodeToString())
    }

    @Test
    fun flattensMultipleElementsOfOneItemIntoSeparateWindowsItems() {
        val item = ClipboardItem(
            ClipboardElement("t", ClipboardFormat.Utf8PlainText),
            ClipboardElement("<i>h</i>", ClipboardFormat.Html),
        )

        val mapped = listOf(item).toWindowsClipboardItems()

        assertEquals(
            listOf(
                WindowsClipboardItem.Text("t"),
                WindowsClipboardItem.Html("<i>h</i>"),
            ),
            mapped,
        )
    }
}
