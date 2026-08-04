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

package androidx.compose.ui.desktop

import androidx.compose.ui.HeadlessTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * The public `DragAndDropEvent.containsFormat` extension dispatches on `DesktopPlatform.Current`
 * (the HOST platform), so its Linux branch is not reachable from tests running on macOS/CI.
 * These tests pin the entry-level format math that branch delegates to; the extension itself is
 * a one-line cast-and-call verified by inspection.
 */
@Category(HeadlessTest::class)
class LinuxContainsFormatTest {
    @Test
    fun containsFormatMatchesAnyLinuxMimeTypeOfTheFormat() {
        val entry = LinuxDragAndDropClipboardEntry(listOf(Utf8PlainTextMimeType), data = null)
        assertTrue(entry.containsFormat(ClipboardFormat.Utf8PlainText, emptyList()))
        assertFalse(entry.containsFormat(ClipboardFormat.Png, emptyList()))
    }

    @Test
    fun containsFormatMatchesFallbackMimeType() {
        // Utf8PlainText maps to both text/plain;charset=utf-8 and the bare text/plain fallback;
        // an entry advertising only the fallback must still match the format.
        val entry = LinuxDragAndDropClipboardEntry(listOf(Utf8PlainTextMimeTypeFallback), data = null)
        assertTrue(entry.containsFormat(ClipboardFormat.Utf8PlainText, emptyList()))
    }

    @Test
    fun containsFormatIsFalseForEmptyMimeTypes() {
        val entry = LinuxDragAndDropClipboardEntry(emptyList(), data = null)
        assertFalse(entry.containsFormat(ClipboardFormat.Utf8PlainText, emptyList()))
        assertFalse(entry.containsFormat(ClipboardFormat.File, emptyList()))
    }
}
