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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.io.files.Path
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Pure-function coverage for the win32 file-dialog parameter mapping. `FileDialog.FileDialogOptions`
 * is a plain KDT data class (public constructor, no natives), so the mapping is unit-testable off
 * Windows. The win32 counterpart of the Linux/GTK `LinuxFileDialogParamsTest`; unlike Linux/GTK,
 * win32 returns plain filesystem paths (no `file://` URI decoding), so there is no decode half to
 * cover here.
 */
@Category(HeadlessTest::class)
class WindowsFileDialogParamsTest {

    @Test
    fun carriesTitlePromptAndNameField() {
        val options = mapWindowsFileDialogOptions(
            title = "Open File",
            prompt = "Choose",
            nameFieldStringValue = "draft.txt",
            directoryPath = null,
        )
        assertEquals("Open File", options.title)
        assertEquals("Choose", options.prompt)
        assertEquals("draft.txt", options.nameFieldStringValue)
    }

    @Test
    fun nullNameFieldStaysNull() {
        assertNull(
            mapWindowsFileDialogOptions(
                title = "Save",
                prompt = "Save",
                nameFieldStringValue = null,
                directoryPath = null,
            ).nameFieldStringValue,
        )
    }

    @Test
    fun existingDirectoryIsForwarded() {
        // java.io.tmpdir is guaranteed to exist, so the SystemFileSystem.exists guard keeps it.
        val existingDir = Path(System.getProperty("java.io.tmpdir"))
        val options = mapWindowsFileDialogOptions(
            title = "Open",
            prompt = "Open",
            nameFieldStringValue = null,
            directoryPath = existingDir,
        )
        assertEquals(existingDir.toString(), options.directoryPath)
    }

    @Test
    fun nonExistentDirectoryIsDropped() {
        // win32's picker errors on a missing initial folder rather than defaulting, so a directory
        // that does not exist must be dropped (matching Noria's SystemFileSystem.exists guard).
        assertNull(
            mapWindowsFileDialogOptions(
                title = "Open",
                prompt = "Open",
                nameFieldStringValue = null,
                directoryPath = Path("/this/path/should/not/exist/air6085-ws4"),
            ).directoryPath,
        )
    }

    @Test
    fun nullDirectoryIsDropped() {
        assertNull(
            mapWindowsFileDialogOptions(
                title = "Open",
                prompt = "Open",
                nameFieldStringValue = null,
                directoryPath = null,
            ).directoryPath,
        )
    }
}
