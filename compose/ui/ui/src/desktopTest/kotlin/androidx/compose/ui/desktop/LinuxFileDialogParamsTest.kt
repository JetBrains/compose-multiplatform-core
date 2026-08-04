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
import androidx.compose.ui.desktop.linux.decodeFileChooserPath
import androidx.compose.ui.desktop.linux.mapCommonDialogParams
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.io.files.Path
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Pure-function coverage for the Linux/GTK file-dialog parameter mapping and the URL-decoding of
 * paths returned by KDT's file chooser. The GTK twin of `mapCommonDialogParams` is byte-identical
 * logic against its own package's [org.jetbrains.desktop.gtk.FileDialog] type, so this Linux-flavored
 * suite covers the shared behavior; [decodeFileChooserPath] is a single shared implementation.
 */
@Category(HeadlessTest::class)
class LinuxFileDialogParamsTest {

    @Test
    fun mapsAllProvidedParams() {
        val params = mapCommonDialogParams(
            title = "Open File",
            prompt = "Choose",
            message = "ignored on Linux",
            directoryPath = Path("/home/user"),
        )
        assertEquals(true, params.modal)
        assertEquals("Open File", params.title)
        assertEquals("Choose", params.acceptLabel)
        assertEquals("/home/user", params.currentFolder)
    }

    @Test
    fun blankPromptYieldsNullAcceptLabel() {
        assertNull(
            mapCommonDialogParams(
                title = "Open",
                prompt = "",
                message = null,
                directoryPath = null,
            ).acceptLabel,
        )
        assertNull(
            mapCommonDialogParams(
                title = "Open",
                prompt = "   ",
                message = null,
                directoryPath = null,
            ).acceptLabel,
        )
    }

    @Test
    fun nullDirectoryYieldsNullFolder() {
        assertNull(
            mapCommonDialogParams(
                title = "Open",
                prompt = "Open",
                message = null,
                directoryPath = null,
            ).currentFolder,
        )
    }

    @Test
    fun decodesPlainPath() {
        assertEquals(Path("/home/user/file.txt"), decodeFileChooserPath("/home/user/file.txt"))
    }

    @Test
    fun decodesPercentEncodedSpace() {
        assertEquals(Path("/home/user/my file.txt"), decodeFileChooserPath("/home/user/my%20file.txt"))
    }

    @Test
    fun stripsFileUriPrefix() {
        assertEquals(Path("/home/user/file.txt"), decodeFileChooserPath("file:///home/user/file.txt"))
    }

    @Test
    fun stripsFileUriPrefixAndDecodes() {
        assertEquals(
            Path("/home/user/my file.txt"),
            decodeFileChooserPath("file:///home/user/my%20file.txt"),
        )
    }

    @Test
    fun decodesPercentEncodedMultiByteUtf8() {
        // é is %C3%A9 — the decoder must accumulate both bytes before UTF-8 decoding.
        assertEquals(Path("/home/user/café"), decodeFileChooserPath("file:///home/user/caf%C3%A9"))
    }

    @Test
    fun preservesRawAstralPlaneCharactersMixedWithEscapes() {
        // A raw surrogate pair (🎉) next to a percent escape must survive re-encoding.
        assertEquals(Path("/home/user/🎉 party"), decodeFileChooserPath("/home/user/🎉%20party"))
    }
}
