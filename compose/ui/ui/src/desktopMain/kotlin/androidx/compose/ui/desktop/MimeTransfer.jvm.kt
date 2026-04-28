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

package androidx.compose.ui.desktop

import java.net.URI
import java.nio.file.Path

internal const val Utf8PlainTextMimeType = "text/plain;charset=utf-8"
internal const val Utf8PlainTextMimeTypeFallback = "text/plain"
internal const val HtmlMimeType = "text/html;charset=utf-8"
internal const val HtmlMimeTypeFallback = "text/html"
internal const val FileUriListMimeType = "text/uri-list"
internal const val PngMimeType = "image/png"
internal const val WindowLocalDragMimeType = "org.jetbrains.fleet.window-local-drag"

internal interface FixedMimeTransferClipboardEntry : ClipboardEntry {
    val mimeData: Map<String, ByteArray>
}

internal class FixedMimeTransferItems(
    override val mimeData: Map<String, ByteArray>,
) : FixedMimeTransferClipboardEntry {
    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
        return getForFormatSync(format)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        return decodeMimeData(mimeData, format) as List<T>
    }
}

internal class MimeTransferClipboardEntry(
    private val mimeDataProvider: () -> Map<String, ByteArray>,
) : ClipboardEntry {
    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
        return getForFormatSync(format)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        return decodeMimeData(mimeDataProvider(), format) as List<T>
    }

    internal fun availableMimeTypes(): Set<String> = mimeDataProvider().keys
}

internal fun mimeTransferClipboardEntry(vararg items: ClipboardItem): FixedMimeTransferItems {
    return fixedMimeTransferClipboardEntry(*items)
}

internal fun fixedMimeTransferClipboardEntry(vararg items: ClipboardItem): FixedMimeTransferItems {
    return FixedMimeTransferItems(encodeClipboardItemsToMimeData(items.asList()))
}

internal fun encodeClipboardItemsToMimeData(items: Iterable<ClipboardItem>): Map<String, ByteArray> {
    val mimeData = linkedMapOf<String, ByteArray>()
    val files = mutableListOf<String>()

    for (item in items) {
        for (element in item.elements) {
            @Suppress("UNCHECKED_CAST")
            when (element.format) {
                ClipboardFormat.Utf8PlainText ->
                    mimeData.putIfAbsent(
                        Utf8PlainTextMimeType,
                        (element.value as String).toByteArray(Charsets.UTF_8),
                    )
                ClipboardFormat.Html ->
                    mimeData.putIfAbsent(
                        HtmlMimeType,
                        (element.value as String).toByteArray(Charsets.UTF_8),
                    )
                ClipboardFormat.Png ->
                    mimeData.putIfAbsent(PngMimeType, element.value as ByteArray)
                ClipboardFormat.File ->
                    files += element.value as String
                ClipboardFormat.WindowLocalDrag ->
                    mimeData.putIfAbsent(
                        WindowLocalDragMimeType,
                        (element.value as LightweightWindowId).value.toString().toByteArray(Charsets.UTF_8),
                    )
                is ClipboardFormat.CustomSerializable<*> ->
                    mimeData.putIfAbsent(
                        element.format.mimeType,
                        (element.format as ClipboardFormat.CustomSerializable<Any>)
                            .encode(element.value)
                            .toByteArray(Charsets.UTF_8),
                    )
            }
        }
    }

    if (files.isNotEmpty()) {
        mimeData[FileUriListMimeType] =
            files.joinToString(separator = "\r\n", postfix = "\r\n") { Path.of(it).toUri().toString() }
                .toByteArray(Charsets.UTF_8)
    }

    return mimeData
}

internal fun availableMimeTypesForClipboardItems(items: Iterable<ClipboardItem>): List<String> {
    return encodeClipboardItemsToMimeData(items).keys.toList()
}

internal fun ClipboardFormat<*>.linuxMimeTypes(): List<String> = when (this) {
    ClipboardFormat.Utf8PlainText -> listOf(Utf8PlainTextMimeType, Utf8PlainTextMimeTypeFallback)
    ClipboardFormat.Html -> listOf(HtmlMimeType, HtmlMimeTypeFallback)
    ClipboardFormat.Png -> listOf(PngMimeType)
    ClipboardFormat.File -> listOf(FileUriListMimeType)
    ClipboardFormat.WindowLocalDrag -> listOf(WindowLocalDragMimeType)
    is ClipboardFormat.CustomSerializable<*> -> listOf(mimeType)
}

internal fun <T : Any> decodeMimeData(
    mimeData: Map<String, ByteArray>,
    format: ClipboardFormat<T>,
): List<T> {
    @Suppress("UNCHECKED_CAST")
    return when (format) {
        ClipboardFormat.Utf8PlainText -> firstMimeBytes(
            mimeData,
            Utf8PlainTextMimeType,
            Utf8PlainTextMimeTypeFallback,
        )?.decodeToString()?.let(::listOf).orEmpty()
        ClipboardFormat.Html -> firstMimeBytes(
            mimeData,
            HtmlMimeType,
            HtmlMimeTypeFallback,
        )?.decodeToString()?.let(::listOf).orEmpty()
        ClipboardFormat.Png -> mimeData[PngMimeType]?.let(::listOf).orEmpty()
        ClipboardFormat.File -> decodeUriList(mimeData[FileUriListMimeType])
        ClipboardFormat.WindowLocalDrag -> mimeData[WindowLocalDragMimeType]
            ?.decodeToString()
            ?.toLongOrNull()
            ?.let { listOf(LightweightWindowId(it)) }
            .orEmpty()
        is ClipboardFormat.CustomSerializable<*> -> mimeData[format.mimeType]
            ?.decodeToString()
            ?.let { serialized ->
                listOf(format.decode(serialized))
            }
            .orEmpty()
    } as List<T>
}

private fun firstMimeBytes(
    mimeData: Map<String, ByteArray>,
    vararg candidates: String,
): ByteArray? {
    for (candidate in candidates) {
        mimeData[candidate]?.let { return it }
    }
    return null
}

private fun decodeUriList(data: ByteArray?): List<String> {
    if (data == null) {
        return emptyList()
    }

    return data.decodeToString()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { uriString ->
            runCatching { URI(uriString) }.getOrNull()
                ?.takeIf { it.scheme == "file" }
                ?.path
        }
        .toList()
}
