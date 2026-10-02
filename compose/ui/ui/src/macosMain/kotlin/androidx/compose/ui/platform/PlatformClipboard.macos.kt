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
import platform.AppKit.NSPasteboardItem
import platform.AppKit.NSPasteboardTypeHTML
import platform.AppKit.NSPasteboardTypeString
import platform.AppKit.NSPasteboardTypeURL

private val PASTEBOARD_TYPE_STRING = checkNotNull(NSPasteboardTypeString)
private val PASTEBOARD_TYPE_HTML = checkNotNull(NSPasteboardTypeHTML)
private val PASTEBOARD_TYPE_URL = checkNotNull(NSPasteboardTypeURL)

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

internal class NSPasteboardPlatformClipboard(
    override val nativeClipboard: NativeClipboard = NSPasteboard.generalPasteboard
) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? {
        val pasteboardItems =
            nativeClipboard.pasteboardItems?.mapNotNull { it as? NSPasteboardItem } ?: return null
        if (pasteboardItems.isEmpty()) return null

        return ClipEntry(pasteboardItems)
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val pasteboardItems = clipEntry?.pasteboardItems?.map { it.copyForClipboard() }.orEmpty()
        nativeClipboard.clearContents()
        if (pasteboardItems.isNotEmpty()) {
            nativeClipboard.writeObjects(pasteboardItems)
        }
    }
}

@Suppress("DEPRECATION")
internal actual fun createPlatformClipboardManager(): ClipboardManager = NSPasteboardPlatformClipboardManager()

internal actual fun createPlatformClipboard(): Clipboard = NSPasteboardPlatformClipboard()

/**
 * A wrapper for [platform.AppKit.NSPasteboardItem]s.
 * The text APIs support plain text, HTML, and URLs. To access other representations, use
 * [Clipboard.nativeClipboard].
 */
actual class ClipEntry internal constructor(
    internal val pasteboardItems: List<NSPasteboardItem> = emptyList()
) {
    actual val clipMetadata: ClipMetadata
        get() = createClipMetadata()

    actual suspend fun readText(): String? = readPlainText() ?: readHtml() ?: readUrl()

    actual suspend fun readPlainText(): String? = readRepresentation(PASTEBOARD_TYPE_STRING)

    actual suspend fun readHtml(): String? = readRepresentation(PASTEBOARD_TYPE_HTML)

    actual suspend fun readUrl(): String? = readRepresentation(PASTEBOARD_TYPE_URL)

    private fun readRepresentation(type: String): String? {
        val item = pasteboardItems.firstOrNull() ?: return null
        return try {
            item.stringForType(type)
        } catch (_: Throwable) {
            null
        }
    }

    private fun createClipMetadata(): ClipMetadata {
        val hasPlainText = pasteboardItems.any { it.types.contains(PASTEBOARD_TYPE_STRING) }
        val hasHtml = pasteboardItems.any { it.types.contains(PASTEBOARD_TYPE_HTML) }
        val hasUrl = pasteboardItems.any { it.types.contains(PASTEBOARD_TYPE_URL) }
        return ClipMetadata(
            hasTextValue = hasPlainText || hasHtml || hasUrl,
            hasPlainTextValue = hasPlainText,
            hasHtmlValue = hasHtml,
            hasUrlValue = hasUrl,
        )
    }

    @ExperimentalComposeUiApi
    fun getPlainText(): String? = readRepresentation(PASTEBOARD_TYPE_STRING)

    actual companion object {
        actual fun withText(plainText: String, html: String?): ClipEntry =
            ClipEntry(listOf(createPasteboardItem(plainText, html, null)))

        actual fun withUrl(url: String, plainText: String?, html: String?): ClipEntry =
            ClipEntry(listOf(createPasteboardItem(plainText, html, url)))

        @ExperimentalComposeUiApi
        fun withPlainText(text: String): ClipEntry = withText(text, null)
    }
}

private fun createPasteboardItem(
    plainText: String?,
    html: String?,
    url: String?,
): NSPasteboardItem = NSPasteboardItem().apply {
    plainText?.let { setString(it, PASTEBOARD_TYPE_STRING) }
    html?.let { setString(it, PASTEBOARD_TYPE_HTML) }
    url?.let { setString(it, PASTEBOARD_TYPE_URL) }
}

private fun NSPasteboardItem.copyForClipboard(): NSPasteboardItem {
    val copy = NSPasteboardItem()
    types.forEach { type ->
        val pasteboardType = type as? String ?: return@forEach
        val stringValue =
            when (pasteboardType) {
                PASTEBOARD_TYPE_STRING, PASTEBOARD_TYPE_HTML, PASTEBOARD_TYPE_URL ->
                    stringForType(pasteboardType)
                else -> null
            }
        if (stringValue != null) {
            copy.setString(stringValue, pasteboardType)
        } else {
            val data = dataForType(pasteboardType)
            if (data != null) {
                copy.setData(data, pasteboardType)
            } else {
                propertyListForType(pasteboardType)?.let {
                    copy.setPropertyList(it, pasteboardType)
                }
            }
        }
    }
    return copy
}
