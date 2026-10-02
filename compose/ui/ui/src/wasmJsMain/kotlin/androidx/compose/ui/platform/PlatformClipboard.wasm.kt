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

@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.getValue
import kotlinx.coroutines.await

private val browserClipboard by lazy {
    getW3CClipboard()
}

private val isSecureContext: Boolean by lazy {
    isSecureContext()
}

// We don't expect the availability of browser APIs to change at runtime, so detect it and save
// It's necessary for https://youtrack.jetbrains.com/issue/CMP-8631
private val isFullClipboardApiSupported: Boolean by lazy {
    isSecureContext && isFullClipboardApiSupported()
}

private val isFallbackWriteTextApiAvailable: Boolean by lazy {
    isSecureContext && isFallbackWriteTextApiAvailable()
}

@Suppress("DEPRECATION")
private class WasmPlatformClipboardManager : ClipboardManager {
    // Clipboard.readText() is async; no synchronous access on Web.
    override fun getText(): AnnotatedString? = null

    override fun setText(annotatedString: AnnotatedString) {
         if (isFallbackWriteTextApiAvailable) {
             browserClipboard.writeText(annotatedString.text)
        }
    }

    // Clipboard.readText() is async; no synchronous access on Web.
    override fun hasText(): Boolean = false

    override fun getClip(): ClipEntry? = null

    @Suppress("GetterSetterNames")
    override fun setClip(clipEntry: ClipEntry?) = Unit
}

private class WasmPlatformClipboard : Clipboard {
    init {
        if (!isSecureContext) {
            warn("Clipboard API is not available in insecure contexts.")
        } else if (!isFallbackWriteTextApiAvailable) {
            warn("The browser doesn't support Clipboard.read(), Clipboard.write() and Clipboard.writeText()")
        } else if (!isFullClipboardApiSupported) {
            warn("The browser doesn't support Clipboard.read() and Clipboard.write()")
        }
    }

    private val emptyClipboardItems = emptyArray<ClipboardItem>().toJsArray()

    override suspend fun getClipEntry(): ClipEntry? {
        if (!isFullClipboardApiSupported) {
            warn("The browser doesn't support Clipboard.read()")
            return null
        }

        val items = nativeClipboard.read().catch {
            // The most common reason is that the permission was denied
            println("Failed to read from Clipboard: $it")
            emptyClipboardItems
        }.await<JsArray<ClipboardItem>>()
        return ClipEntry(items)
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        when {
            isFullClipboardApiSupported -> if (clipEntry == null) {
                // clear the clipboard
                nativeClipboard.write(emptyClipboardItems()).await<Any?>()
            } else {
                nativeClipboard.write(clipEntry.clipboardItems).await<Any?>()
            }
            isFallbackWriteTextApiAvailable -> {
                val text = clipEntry?.fallbackPlainText ?: ""
                nativeClipboard.writeText(text).await<Any?>()
            }
            else -> warn("The browser doesn't support Clipboard.write() and Clipboard.writeText()")
        }
    }

    override val nativeClipboard: NativeClipboard
        get() = browserClipboard
}

@Suppress("DEPRECATION")
internal actual fun createPlatformClipboardManager(): ClipboardManager = WasmPlatformClipboardManager()

internal actual fun createPlatformClipboard(): Clipboard = WasmPlatformClipboard()

actual class ClipEntry
@ExperimentalComposeUiApi
constructor(
    @property:ExperimentalComposeUiApi
    val clipboardItems: JsArray<ClipboardItem>
) {
    actual val clipMetadata: ClipMetadata
        get() = createClipMetadata()

    @InternalComposeUiApi
    var fallbackPlainText: String? = null

    internal var fallbackPlainTextContent: String? = null
    internal var fallbackHtml: String? = null
    internal var fallbackUrl: String? = null

    actual suspend fun readText(): String? = readPlainText() ?: readHtml() ?: readUrl()

    actual suspend fun readPlainText(): String? =
        fallbackPlainTextContent ?: readRepresentation(MIME_TYPE_PLAIN_TEXT)

    actual suspend fun readHtml(): String? =
        fallbackHtml ?: readRepresentation(MIME_TYPE_HTML)

    actual suspend fun readUrl(): String? =
        fallbackUrl ?: readRepresentation(MIME_TYPE_URL)

    actual companion object {
        actual fun withText(plainText: String, html: String?): ClipEntry {
            return if (isFullClipboardApiSupported) {
                ClipEntry(arrayOf(createClipboardItem(plainText, html, null)).toJsArray())
            } else {
                ClipEntry(invalidClipboardItems()).apply {
                    fallbackPlainText = plainText
                    fallbackPlainTextContent = plainText
                    fallbackHtml = html
                }
            }
        }

        actual fun withUrl(url: String, plainText: String?, html: String?): ClipEntry {
            return if (isFullClipboardApiSupported) {
                ClipEntry(arrayOf(createClipboardItem(plainText, html, url)).toJsArray())
            } else {
                ClipEntry(invalidClipboardItems()).apply {
                    fallbackPlainText = plainText ?: url
                    fallbackPlainTextContent = plainText
                    fallbackHtml = html
                    fallbackUrl = url
                }
            }
        }
    }
}

private const val MIME_TYPE_PLAIN_TEXT = "text/plain"
private const val MIME_TYPE_HTML = "text/html"
private const val MIME_TYPE_URL = "text/uri-list"

private fun ClipEntry.createClipMetadata(): ClipMetadata {
    var hasText =
        fallbackPlainTextContent != null || fallbackHtml != null || fallbackUrl != null
    var hasPlainText = fallbackPlainTextContent != null
    var hasHtml = fallbackHtml != null
    var hasUrl = fallbackUrl != null

    var index = 0
    while (index < clipboardItems.length) {
        val item = clipboardItems[index]!!
        hasText = hasText || item.hasTextMimeType()
        hasPlainText = hasPlainText || item.hasMimeType(MIME_TYPE_PLAIN_TEXT)
        hasHtml = hasHtml || item.hasMimeType(MIME_TYPE_HTML)
        hasUrl = hasUrl || item.hasMimeType(MIME_TYPE_URL)
        index++
    }

    return ClipMetadata(hasText, hasPlainText, hasHtml, hasUrl)
}

private suspend fun ClipEntry.readRepresentation(mimeType: String): String? {
    if (clipboardItems.length == 0) return null
    val item = clipboardItems[0] ?: return null
    if (!item.hasMimeType(mimeType)) return null

    return try {
        val blob = item.getType(mimeType.toJsString()).await<W3CTemporaryBlob>()
        blob.text().await<JsString>().toString()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

private fun ClipboardItem.hasMimeType(mimeType: String): Boolean =
    clipboardItemHasMimeType(this, mimeType.toJsString())

private fun ClipboardItem.hasTextMimeType(): Boolean = clipboardItemHasTextMimeType(this)

@Suppress("UNUSED_PARAMETER")
private fun clipboardItemHasMimeType(item: ClipboardItem, mimeType: JsString): Boolean =
    js("item.types.includes(mimeType)")

@Suppress("UNUSED_PARAMETER")
private fun clipboardItemHasTextMimeType(item: ClipboardItem): Boolean =
    js("item.types.some(type => type.toLowerCase().startsWith('text/'))")

@Suppress("UNUSED_PARAMETER")
private fun createClipboardItem(
    plainText: String?,
    html: String?,
    url: String?,
): ClipboardItem =
    js(
        """(function() {
            const data = {};
            if (plainText !== null) {
                data['text/plain'] = new Blob([plainText], { type: 'text/plain' });
            }
            if (html !== null) {
                data['text/html'] = new Blob([html], { type: 'text/html' });
            }
            if (url !== null) {
                data['text/uri-list'] = new Blob([url], { type: 'text/uri-list' });
            }
            return new ClipboardItem(data);
        })()"""
    )

// Can't truly clear the clipboard, so setting the empty text
private fun emptyClipboardItems(): JsArray<ClipboardItem> =
    js("[new ClipboardItem({'text/plain': new Blob([''], { type: 'text/plain' })})]")

// We use it when we detect isSecureContext() != true,
// because we can't call ClipboardItem constructor - it's undefined.
private fun invalidClipboardItems(): JsArray<ClipboardItem> =
    js("[]")

private fun warn(text: String) {
    js("console.warn(text)")
}