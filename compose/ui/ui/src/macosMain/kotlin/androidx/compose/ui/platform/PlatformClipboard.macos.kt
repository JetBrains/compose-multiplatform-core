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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardTypeString

actual typealias NativeClipboard = NSPasteboard

@Suppress("DEPRECATION")
private class NSPasteboardPlatformClipboardManager : ClipboardManager {
    override fun getText(): AnnotatedString? =
        getClipboardText()?.let { AnnotatedString(it) }

    override fun setText(annotatedString: AnnotatedString) {
        setClipboardText(annotatedString.text)
    }

    override fun hasText(): Boolean = !getClipboardText().isNullOrEmpty()

    override fun getClip(): ClipEntry? = null

    @Suppress("GetterSetterNames")
    override fun setClip(clipEntry: ClipEntry?) = Unit

    private fun setClipboardText(text: String) {
        NSPasteboard.generalPasteboard.clearContents()
        NSPasteboard.generalPasteboard.setString(string = text, forType = NSPasteboardTypeString)
    }

    private fun getClipboardText(): String? {
        return NSPasteboard.generalPasteboard.stringForType(dataType = NSPasteboardTypeString)
    }
}

internal class NSPasteboardPlatformClipboard : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? {
        if (nativeClipboard.pasteboardItems == null) return null
        if (nativeClipboard.pasteboardItems!!.isEmpty()) return null

        val str = nativeClipboard.stringForType(NSPasteboardTypeString)
        if (str.isNullOrEmpty()) return null

        return ClipEntry.withPlainText(str)
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        nativeClipboard.clearContents()
        val plainText = clipEntry?.plainText ?: return
        nativeClipboard.setString(plainText, NSPasteboardTypeString)
    }

    override val nativeClipboard: NativeClipboard
        get() = NSPasteboard.generalPasteboard
}

@Suppress("DEPRECATION")
internal actual fun createPlatformClipboardManager(): ClipboardManager = NSPasteboardPlatformClipboardManager()

internal actual fun createPlatformClipboard(): Clipboard = NSPasteboardPlatformClipboard()

/**
 * A wrapper for [platform.AppKit.NSPasteboard] items.
 * Currently, it operates only with string(s) - [platform.AppKit.NSPasteboardTypeString].
 * To access or set other data items, consider using [Clipboard.nativeClipboard].
 */
actual class ClipEntry internal constructor() {

    // TODO: https://youtrack.jetbrains.com/issue/CMP-1260
    actual val clipMetadata: ClipMetadata
        get() = ClipMetadata()

    // TODO(Merge) Major, Implement after merging 437520c6f81b48618204661cefa664e599147763
    // Implement clipboard metadata inspection and HTML/URL support for macOS.
    // Until then metadata is false, HTML/URL reads return null, and factories create empty entries.
    actual suspend fun readText(): String? = readPlainText()

    actual suspend fun readPlainText(): String? = getPlainText()

    actual suspend fun readHtml(): String? = null

    actual suspend fun readUrl(): String? = null

    internal var plainText: String? = null

    @ExperimentalComposeUiApi
    fun getPlainText(): String? = plainText

    actual companion object {
        @Suppress("UNUSED_PARAMETER")
        actual fun withText(plainText: String, html: String?): ClipEntry = ClipEntry()

        @Suppress("UNUSED_PARAMETER")
        actual fun withUrl(url: String, plainText: String, html: String?): ClipEntry = ClipEntry()

        @ExperimentalComposeUiApi
        fun withPlainText(text: String): ClipEntry = ClipEntry().apply {
            plainText = text
        }
    }
}
