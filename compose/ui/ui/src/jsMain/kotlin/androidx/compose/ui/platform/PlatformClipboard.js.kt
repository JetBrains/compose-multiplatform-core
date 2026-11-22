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
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import kotlin.js.Promise
import kotlinx.coroutines.await
import org.w3c.files.Blob

private const val MIME_TYPE_PLAIN_TEXT = "text/plain"

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
private class JsPlatformClipboardManager : ClipboardManager {
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

private class JsPlatformClipboard : Clipboard {
    init {
        if (!isSecureContext) {
            console.warn("Clipboard API is not available in insecure contexts.")
        } else if (!isFallbackWriteTextApiAvailable) {
            console.warn("The browser doesn't support Clipboard.read(), Clipboard.write() and Clipboard.writeText()")
        } else if (!isFullClipboardApiSupported) {
            console.warn("The browser doesn't support Clipboard.read() and Clipboard.write()")
        }
    }

    private val emptyClipboardItems = emptyArray<ClipboardItem>()

    override suspend fun getClipEntry(): ClipEntry? {
        if (!isFullClipboardApiSupported) {
            console.warn("The browser doesn't support Clipboard.read()")
            return null
        }

        val items = nativeClipboard.read().catch {
            // The most common reason is that the permission was denied
            println("Failed to read from Clipboard: $it")
            emptyClipboardItems
        }.await()
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
            isFallbackWriteTextApiAvailable() -> {
                val text = clipEntry?.fallbackPlainText ?: ""
                nativeClipboard.writeText(text).await<Any?>()
            }
            else -> console.warn("The browser doesn't support Clipboard.write() and Clipboard.writeText()")
        }
    }

    override val nativeClipboard: NativeClipboard
        get() = browserClipboard
}

@Suppress("DEPRECATION")
internal actual fun createPlatformClipboardManager(): ClipboardManager = JsPlatformClipboardManager()

internal actual fun createPlatformClipboard(): Clipboard = JsPlatformClipboard()

actual class ClipEntry
@ExperimentalComposeUiApi
constructor(
    @property:ExperimentalComposeUiApi
    val clipboardItems: Array<ClipboardItem>
) {

    // TODO: https://youtrack.jetbrains.com/issue/CMP-1260
    actual val clipMetadata: ClipMetadata
        get() = ClipMetadata()

    @InternalComposeUiApi
    var fallbackPlainText: String? = null

    // TODO(Merge) Major, Implement after merging 437520c6f81b48618204661cefa664e599147763
    // Implement clipboard metadata inspection and HTML/URL support for JavaScript.
    // Until then metadata is false, HTML/URL reads return null, and factories create empty entries.
    actual suspend fun readText(): String? = readPlainText()

    @OptIn(InternalComposeUiApi::class)
    actual suspend fun readPlainText(): String? {
        fallbackPlainText?.let { return it }
        if (clipboardItems.isEmpty()) return null

        return try {
            val blob = clipboardItems[0].getType(MIME_TYPE_PLAIN_TEXT).await<Blob>()
            readClipboardTextFromBlob(blob).await<String>()
        } catch (_: Exception) {
            null
        }
    }

    actual suspend fun readHtml(): String? = null

    actual suspend fun readUrl(): String? = null

    actual companion object {
        @Suppress("UNUSED_PARAMETER")
        actual fun withText(plainText: String, html: String?): ClipEntry = ClipEntry(emptyArray())

        @Suppress("UNUSED_PARAMETER")
        actual fun withUrl(url: String, plainText: String, html: String?): ClipEntry =
            ClipEntry(emptyArray())

        fun withPlainText(text: String): ClipEntry {
            return when {
                isFullClipboardApiSupported -> ClipEntry(
                    if (isSecureContext) {
                        createClipboardItemWithPlainText(text)
                    } else {
                        emptyClipboardItems()
                    }
                )
                else -> ClipEntry(emptyArray())
                    .apply { fallbackPlainText = text }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun createClipboardItemWithPlainText(text: String): Array<ClipboardItem> =
    js("[new ClipboardItem({'text/plain': new Blob([text], { type: 'text/plain' })})]")

@Suppress("UNUSED_PARAMETER")
private fun readClipboardTextFromBlob(blob: Blob): Promise<String> =
    js("blob.text()")

// Can't truly clear the clipboard, so setting the empty text
private fun emptyClipboardItems(): Array<ClipboardItem> =
    js("[new ClipboardItem({'text/plain': new Blob([''], { type: 'text/plain' })})]")
