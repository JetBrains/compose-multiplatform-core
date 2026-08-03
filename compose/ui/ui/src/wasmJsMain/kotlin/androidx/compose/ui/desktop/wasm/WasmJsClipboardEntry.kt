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

@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalComposeUiApi::class)

package androidx.compose.ui.desktop.wasm

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.desktop.ClipboardElement
import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.platform.ClipboardItem as JsClipboardItem
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.js
import kotlin.js.toJsString
import kotlin.js.toList
import kotlinx.coroutines.await
import org.w3c.files.Blob

/**
 * Adapts a W3C clipboard read to the desktop [ClipboardEntry] model, decoding blobs on demand by
 * their MIME type. The MIME mapping matches Noria's implementation of the same facade.
 */
internal class WasmJsClipboardEntry(internal val items: JsArray<JsClipboardItem>) : ClipboardEntry {

    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
        val mimeType = format.toMimeType()
        val mimeTypeAsJsString = mimeType.toJsString()
        return items
            .toList()
            .mapNotNull { item ->
                if (item.types.toList().any { it == mimeTypeAsJsString }) {
                    item.getType(mimeTypeAsJsString).await<Blob>()
                } else {
                    null
                }
            }.flatMap { blob ->
                decodeBlob(format, blob)
            }
    }

    // The browser clipboard has no synchronous access.
    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> = emptyList()
}

private suspend fun <T : Any> decodeBlob(format: ClipboardFormat<T>, blob: Blob): List<T> {
    @Suppress("UNCHECKED_CAST")
    return when (format) {
        ClipboardFormat.Utf8PlainText -> listOf(blob.readText() as T)
        ClipboardFormat.Html -> listOf(blob.readText() as T)
        ClipboardFormat.Png -> listOf(blob.readBytes() as T)
        ClipboardFormat.File -> parseUriList(blob.readText()) as List<T>
        ClipboardFormat.WindowLocalDrag ->
            listOfNotNull(blob.readText().trim().toLongOrNull()?.let(::LightweightWindowId) as T?)
        is ClipboardFormat.CustomSerializable<*> -> {
            @Suppress("UNCHECKED_CAST")
            val customFormat = format as ClipboardFormat.CustomSerializable<T>
            listOf(customFormat.decode(blob.readText()))
        }
    }
}

private fun parseUriList(text: String): List<String> {
    return text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { if (it.startsWith("file://")) it.removePrefix("file://") else it }
        .toList()
}

internal fun ClipboardItemsEntry.toJsClipboardItems(): JsArray<JsClipboardItem> {
    val result = newJsClipboardItemArray()
    items.forEach { item ->
        item.toJsClipboardItemOrNull()?.let { appendJsClipboardItem(result, it) }
    }
    return result
}

private fun androidx.compose.ui.desktop.ClipboardItem.toJsClipboardItemOrNull(): JsClipboardItem? {
    val supportedElements = elements.filter { element ->
        clipboardItemSupports(element.format.toMimeType())
    }
    if (supportedElements.isEmpty()) return null
    val record = newRecord()
    supportedElements.forEach { element ->
        setRecordValue(record, element.format.toMimeType(), element.toBlob())
    }
    return newJsClipboardItem(record)
}

private fun ClipboardElement<*>.toBlob(): Blob {
    @Suppress("UNCHECKED_CAST")
    return when (val format = format) {
        ClipboardFormat.Utf8PlainText -> stringBlob(value as String, MimeTypes.utf8PlainText)
        ClipboardFormat.Html -> stringBlob(value as String, MimeTypes.html)
        ClipboardFormat.File -> stringBlob(value as String, MimeTypes.file)
        ClipboardFormat.Png -> bytesBlob(value as ByteArray, MimeTypes.png)
        ClipboardFormat.WindowLocalDrag -> stringBlob(
            (value as LightweightWindowId).value.toString(),
            MimeTypes.windowLocalDrag,
        )
        is ClipboardFormat.CustomSerializable<*> -> {
            val serializer = format as ClipboardFormat.CustomSerializable<Any>
            stringBlob(serializer.encode(value as Any), format.toMimeType())
        }
    }
}

private fun ClipboardFormat<*>.toMimeType(): String {
    return when (this) {
        ClipboardFormat.Utf8PlainText -> MimeTypes.utf8PlainText
        ClipboardFormat.Html -> MimeTypes.html
        ClipboardFormat.File -> MimeTypes.file
        ClipboardFormat.Png -> MimeTypes.png
        ClipboardFormat.WindowLocalDrag -> MimeTypes.windowLocalDrag
        // Chromium requires custom formats on the async clipboard to carry the "web " prefix.
        is ClipboardFormat.CustomSerializable<*> -> "web $mimeType"
    }
}

private object MimeTypes {
    const val utf8PlainText = "text/plain"
    const val html = "text/html"
    const val file = "text/uri-list"
    const val png = "image/png"
    const val windowLocalDrag = "org.jetbrains.fleet.window-local-drag"
}

private suspend fun Blob.readText(): String = blobTextPromise(this).await<JsString>().toString()

private suspend fun Blob.readBytes(): ByteArray {
    val jsBytes = blobBytesPromise(this).await<JsAny>()
    val length = jsBytesLength(jsBytes)
    return ByteArray(length) { i -> jsByteAt(jsBytes, i).toByte() }
}

private fun stringBlob(value: String, mimeType: String): Blob =
    newStringBlob(value, mimeType)

private fun bytesBlob(value: ByteArray, mimeType: String): Blob {
    val jsBytes = newJsBytes(value.size)
    value.forEachIndexed { i, byte -> setJsByte(jsBytes, i, byte.toInt()) }
    return newBytesBlob(jsBytes, mimeType)
}

private fun blobTextPromise(blob: Blob): Promise<JsString> = js("blob.text()")

private fun blobBytesPromise(blob: Blob): Promise<JsAny> =
    js("blob.bytes ? blob.bytes() : blob.arrayBuffer().then(function(b){ return new Uint8Array(b); })")

private fun jsBytesLength(bytes: JsAny): Int = js("bytes.length")

private fun jsByteAt(bytes: JsAny, i: Int): Int = js("bytes[i]")

private fun newJsBytes(size: Int): JsAny = js("new Uint8Array(size)")

private fun setJsByte(bytes: JsAny, i: Int, v: Int): Unit = js("bytes[i] = v")

private fun newStringBlob(value: String, mimeType: String): Blob =
    js("new Blob([value], { type: mimeType })")

private fun newBytesBlob(bytes: JsAny, mimeType: String): Blob =
    js("new Blob([bytes], { type: mimeType })")

private fun newRecord(): JsAny = js("({})")

private fun setRecordValue(record: JsAny, key: String, value: Blob): Unit = js("record[key] = value")

private fun newJsClipboardItem(record: JsAny): JsClipboardItem = js("new ClipboardItem(record)")

private fun clipboardItemSupports(mimeType: String): Boolean =
    js("typeof ClipboardItem !== 'undefined' && (!ClipboardItem.supports || ClipboardItem.supports(mimeType))")

private fun newJsClipboardItemArray(): JsArray<JsClipboardItem> = js("[]")

private fun appendJsClipboardItem(array: JsArray<JsClipboardItem>, item: JsClipboardItem): Unit =
    js("array.push(item)")
