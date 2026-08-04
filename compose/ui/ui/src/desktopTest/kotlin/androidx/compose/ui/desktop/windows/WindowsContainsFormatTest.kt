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
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.jetbrains.desktop.win32.DragDropEffect
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Pure-JVM coverage for the Windows DnD pieces that never touch natives: the
 * action-to-effect mappers and [WindowsDragAndDropClipboardEntry.containsFormat]'s
 * allowed-effect gate, which short-circuits BEFORE the (native, FFI-registering)
 * `toWin32DataFormat` lookup. Format-availability checks against a real DataObject and the
 * public `DragAndDropEvent.containsFormat` extension (which dispatches on the HOST platform,
 * so its Windows branch is unreachable off-Windows — same limitation the Linux entry test
 * documents) are Windows-VM concerns.
 */
@Category(HeadlessTest::class)
class WindowsContainsFormatTest {

    @Test
    fun actionToEffectMapping() {
        assertEquals(DragDropEffect.Copy, DragAndDropTransferAction.Copy.toDragDropEffect())
        assertEquals(DragDropEffect.Move, DragAndDropTransferAction.Move.toDragDropEffect())
        assertEquals(DragDropEffect.Link, DragAndDropTransferAction.Link.toDragDropEffect())
    }

    @Test
    fun effectToActionMappingPrefersMoveThenCopyThenLink() {
        assertEquals(
            DragAndDropTransferAction.Move,
            (DragDropEffect.Move or DragDropEffect.Copy or DragDropEffect.Link).toDragAndDropTransferAction(),
        )
        assertEquals(
            DragAndDropTransferAction.Copy,
            (DragDropEffect.Copy or DragDropEffect.Link).toDragAndDropTransferAction(),
        )
        assertEquals(DragAndDropTransferAction.Link, DragDropEffect.Link.toDragAndDropTransferAction())
        assertNull(DragDropEffect.None.toDragAndDropTransferAction())
    }

    @Test
    fun containsFormatIsGatedOnTheAllowedEffectBeforeAnyNativeLookup() {
        // allowedEffect = None can never intersect the requested actions, so containsFormat
        // must return false without reaching the native format registration.
        val noneAllowed = WindowsDragAndDropClipboardEntry(
            WindowsDragAndDropData.Formats(emptySet()),
            allowedEffect = DragDropEffect.None,
        )
        assertFalse(
            noneAllowed.containsFormat(
                ClipboardFormat.Utf8PlainText,
                listOf(DragAndDropTransferAction.Copy, DragAndDropTransferAction.Move),
            ),
        )

        // An empty action list can never match any allowed effect either.
        val copyAllowed = WindowsDragAndDropClipboardEntry(
            WindowsDragAndDropData.Formats(emptySet()),
            allowedEffect = DragDropEffect.Copy,
        )
        assertFalse(copyAllowed.containsFormat(ClipboardFormat.Utf8PlainText, emptyList()))

        // Actions outside the allowed effect are rejected by the gate.
        assertFalse(
            copyAllowed.containsFormat(
                ClipboardFormat.Utf8PlainText,
                listOf(DragAndDropTransferAction.Move),
            ),
        )
    }
}
