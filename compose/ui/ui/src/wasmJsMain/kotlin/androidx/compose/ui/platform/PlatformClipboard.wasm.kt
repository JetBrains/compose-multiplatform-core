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
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.clipboardEntry
import androidx.compose.ui.desktop.wasm.WasmJsClipboardEntry
import androidx.compose.ui.desktop.wasm.toJsClipboardItems
import androidx.compose.ui.text.AnnotatedString
import kotlin.getValue
import kotlin.js.Promise
import kotlinx.coroutines.await
import org.w3c.files.Blob

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
        return ClipEntry(WasmJsClipboardEntry(items)).apply { clipboardItems = items }
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        when {
            isFullClipboardApiSupported -> {
                // Writing the empty text is the closest thing to clearing the clipboard.
                val items = clipEntry?.let { resolveJsClipboardItems(it) }
                nativeClipboard.write(items ?: emptyClipboardItems()).await<Any?>()
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

    private fun resolveJsClipboardItems(clipEntry: ClipEntry): JsArray<ClipboardItem>? =
        clipEntry.clipboardItems
            ?: (clipEntry.nativeClipEntry as? ClipboardItemsEntry)?.toJsClipboardItems()
}

@Suppress("DEPRECATION")
internal actual fun createPlatformClipboardManager(): ClipboardManager = WasmPlatformClipboardManager()

internal actual fun createPlatformClipboard(): Clipboard = WasmPlatformClipboard()

/**
 * Aligned with the fleet desktop actual: carries an arbitrary payload — for product code a
 * [androidx.compose.ui.desktop.ClipboardEntry] — instead of upstream's raw W3C clipboard items.
 * When the payload was produced from (or already converted to) native items, they travel along in
 * [clipboardItems] so a write does not have to re-convert.
 */
actual class ClipEntry
@ExperimentalComposeUiApi
constructor(
    @property:ExperimentalComposeUiApi
    val nativeClipEntry: Any
) {

    // TODO: https://youtrack.jetbrains.com/issue/CMP-1260
    actual val clipMetadata: ClipMetadata
        get() = TODO("ClipMetadata is not implemented. Consider using nativeClipboard")

    @InternalComposeUiApi
    var fallbackPlainText: String? = null

    @property:ExperimentalComposeUiApi
    var clipboardItems: JsArray<ClipboardItem>? = null
        internal set

    companion object {
        fun withPlainText(text: String): ClipEntry {
            val entry = ClipEntry(clipboardEntry(androidx.compose.ui.desktop.ClipboardItem(text, ClipboardFormat.Utf8PlainText)))
            return when {
                isFullClipboardApiSupported -> entry.apply {
                    clipboardItems = if (isSecureContext) {
                        createClipboardItemWithPlainText(text)
                    } else {
                        emptyClipboardItems()
                    }
                }
                else -> entry.apply { fallbackPlainText = text }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun createClipboardItemWithPlainText(text: String): JsArray<ClipboardItem> =
    js("[new ClipboardItem({'text/plain': new Blob([text], { type: 'text/plain' })})]")

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