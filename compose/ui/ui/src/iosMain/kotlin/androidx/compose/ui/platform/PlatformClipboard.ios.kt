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
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURL.Companion.URLWithString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardTypeListString
import platform.UIKit.UIPasteboardTypeListURL

private const val PASTEBOARD_TYPE_STRING = "public.utf8-plain-text"
private const val PASTEBOARD_TYPE_HTML = "public.html"
private const val PASTEBOARD_TYPE_URL = "public.url"

private val PASTEBOARD_TYPE_STRINGS =
    (UIPasteboardTypeListString.filterIsInstance<String>() + PASTEBOARD_TYPE_STRING).distinct()
private val PASTEBOARD_TYPE_URLS =
    (UIPasteboardTypeListURL.filterIsInstance<String>() + PASTEBOARD_TYPE_URL).distinct()

actual typealias NativeClipboard = UIPasteboard

private class IosClipboard : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? {
        val clipboard = nativeClipboard
        if (clipboard.numberOfItems() == 0L) return null
        val readPlainTextLambda: (() -> String?)? =
            if (clipboard.containsPasteboardTypes(PASTEBOARD_TYPE_STRINGS, inItemSet = null)) {
                { readPasteboardValue(clipboard, PASTEBOARD_TYPE_STRINGS) ?: clipboard.string }
            } else {
                null
            }
        val readHtmlLambda: (() -> String?)? =
            if (clipboard.containsPasteboardTypes(listOf(PASTEBOARD_TYPE_HTML), inItemSet = null)) {
                { readPasteboardValue(clipboard, listOf(PASTEBOARD_TYPE_HTML)) }
            } else {
                null
            }
        val readUrlLambda: (() -> String?)? =
            if (clipboard.containsPasteboardTypes(PASTEBOARD_TYPE_URLS, inItemSet = null)) {
                {
                    readPasteboardValue(clipboard, PASTEBOARD_TYPE_URLS)
                        ?: clipboard.URL?.absoluteString
                }
            } else {
                null
            }
        return ClipEntry(
            readPlainTextLambda = readPlainTextLambda,
            readHtmlLambda = readHtmlLambda,
            readUrlLambda = readUrlLambda,
            pasteboardItemsLambda = { clipboard.items },
        )
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        nativeClipboard.items = clipEntry?.pasteboardItemsForClipboard() ?: emptyList<Any>()
    }

    /**
     * Provides the [platform.UIKit.UIPasteboard] instance.
     */
    override val nativeClipboard: NativeClipboard
        get() = UIPasteboard.generalPasteboard
}

@Suppress("DEPRECATION")
private class IosClipboardManager : ClipboardManager {
    override fun getText(): AnnotatedString? =
        UIPasteboard.generalPasteboard.string?.let { AnnotatedString(it) }

    override fun setText(annotatedString: AnnotatedString) {
        UIPasteboard.generalPasteboard.string = annotatedString.text
    }

    override fun hasText(): Boolean = !UIPasteboard.generalPasteboard.string.isNullOrEmpty()

    override fun getClip(): ClipEntry? = null

    @Suppress("GetterSetterNames")
    override fun setClip(clipEntry: ClipEntry?) = Unit
}

@Suppress("DEPRECATION")
internal actual fun createPlatformClipboardManager(): ClipboardManager = IosClipboardManager()

internal actual fun createPlatformClipboard(): Clipboard = IosClipboard()

/**
 * A wrapper for [UIPasteboard] items.
 * The text APIs support plain text, HTML, and URLs. To access other representations, use
 * [Clipboard.nativeClipboard].
 */
actual class ClipEntry internal constructor(
    private val readPlainTextLambda: (() -> String?)? = null,
    private val readHtmlLambda: (() -> String?)? = null,
    private val readUrlLambda: (() -> String?)? = null,
    private val pasteboardItemsLambda: (() -> List<*>)? = null,
) {
    actual val clipMetadata: ClipMetadata
        get() = createClipMetadata()

    actual suspend fun readText(): String? = readPlainText() ?: readHtml() ?: readUrl()

    actual suspend fun readPlainText(): String? = readPlainTextLambda?.invoke()

    actual suspend fun readHtml(): String? = readHtmlLambda?.invoke()

    actual suspend fun readUrl(): String? = readUrlLambda?.invoke()

    private fun createClipMetadata(): ClipMetadata {
        val hasPlainText = readPlainTextLambda != null
        val hasHtml = readHtmlLambda != null
        val hasUrl = readUrlLambda != null
        return ClipMetadata(
            hasTextValue = hasPlainText || hasHtml || hasUrl,
            hasPlainTextValue = hasPlainText,
            hasHtmlValue = hasHtml,
            hasUrlValue = hasUrl,
        )
    }

    internal fun pasteboardItemsForClipboard(): List<*> =
        pasteboardItemsLambda?.invoke() ?: emptyList<Any>()

    @ExperimentalComposeUiApi
    fun getPlainText(): String? = readPlainTextLambda?.invoke()

    @ExperimentalComposeUiApi
    fun hasPlainText(): Boolean = clipMetadata.hasPlainText()

    actual companion object {
        actual fun withText(plainText: String, html: String?): ClipEntry {
            val pasteboardItems = listOf(createPasteboardItem(plainText, html, null))
            return ClipEntry(
                readPlainTextLambda = { plainText },
                readHtmlLambda = html?.let { htmlValue -> { htmlValue } },
                pasteboardItemsLambda = { pasteboardItems },
            )
        }

        actual fun withUrl(url: String, plainText: String?, html: String?): ClipEntry {
            val pasteboardItems = listOf(createPasteboardItem(plainText, html, url))
            return ClipEntry(
                readPlainTextLambda = plainText?.let { plainTextValue -> { plainTextValue } },
                readHtmlLambda = html?.let { htmlValue -> { htmlValue } },
                readUrlLambda = { url },
                pasteboardItemsLambda = { pasteboardItems },
            )
        }

        @ExperimentalComposeUiApi
        fun withPlainText(text: String): ClipEntry = withText(text)
    }
}

private fun readPasteboardValue(clipboard: UIPasteboard, types: List<String>): String? {
    return try {
        types.forEach { type ->
            clipboard.valueForPasteboardType(type)?.toClipboardString()?.let { return it }
        }
        null
    } catch (_: Throwable) {
        null
    }
}

private fun createPasteboardItem(
    plainText: String?,
    html: String?,
    url: String?,
): Map<String, Any> {
    val item = mutableMapOf<String, Any>()
    plainText?.let { item[PASTEBOARD_TYPE_STRING] = it }
    html?.let { item[PASTEBOARD_TYPE_HTML] = it }
    url?.let { item[PASTEBOARD_TYPE_URL] = URLWithString(it) ?: it }
    return item
}

private fun Any.toClipboardString(): String? =
    when (this) {
        is NSURL -> absoluteString
        is NSData -> NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString()
        else -> toString()
    }
