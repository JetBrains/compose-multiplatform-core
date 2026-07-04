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

private var memoryClipboardText: String? = null

private class LinuxPlatformClipboardManager : ClipboardManager {
    override fun getText(): AnnotatedString? =
        memoryClipboardText?.let { AnnotatedString(it) }

    override fun setText(annotatedString: AnnotatedString) {
        memoryClipboardText = annotatedString.text
    }

    override fun hasText(): Boolean = !memoryClipboardText.isNullOrEmpty()

    override fun getClip(): ClipEntry? = memoryClipboardText?.let { ClipEntry.withPlainText(it) }

    override fun setClip(clipEntry: ClipEntry?) {
        memoryClipboardText = clipEntry?.plainText
    }
}

internal class LinuxPlatformClipboard : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? {
        val str = memoryClipboardText
        if (str.isNullOrEmpty()) return null
        return ClipEntry.withPlainText(str)
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        memoryClipboardText = clipEntry?.plainText
    }

    override val nativeClipboard: NativeClipboard
        get() = Any()
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
