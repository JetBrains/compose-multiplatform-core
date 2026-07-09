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

package androidx.compose.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.AnnotatedString

actual typealias NativeClipboard = Any

private class LinuxPlatformClipboardManager : ClipboardManager {
    override fun getText(): AnnotatedString? =
        X11Clipboard.getText()?.let { AnnotatedString(it) }

    override fun setText(annotatedString: AnnotatedString) {
        X11Clipboard.setText(annotatedString.text)
    }

    override fun hasText(): Boolean = X11Clipboard.hasText()

    override fun getClip(): ClipEntry? = X11Clipboard.getText()?.let { ClipEntry.withPlainText(it) }

    override fun setClip(clipEntry: ClipEntry?) {
        X11Clipboard.setText(clipEntry?.plainText)
    }
}

internal class LinuxPlatformClipboard : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? =
        X11Clipboard.getText()?.takeIf { it.isNotEmpty() }?.let { ClipEntry.withPlainText(it) }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        X11Clipboard.setText(clipEntry?.plainText)
    }

    override val nativeClipboard: NativeClipboard
        get() = X11Clipboard
}

internal actual fun createPlatformClipboardManager(): ClipboardManager = LinuxPlatformClipboardManager()

internal actual fun createPlatformClipboard(): Clipboard = LinuxPlatformClipboard()

actual class ClipEntry internal constructor() {
    actual val clipMetadata: ClipMetadata
        get() = TODO("ClipMetadata is not implemented.")

    internal var plainText: String? = null

    @ExperimentalComposeUiApi
    fun getPlainText(): String? = plainText

    companion object {
        @ExperimentalComposeUiApi
        fun withPlainText(text: String): ClipEntry = ClipEntry().apply {
            plainText = text
        }
    }
}
