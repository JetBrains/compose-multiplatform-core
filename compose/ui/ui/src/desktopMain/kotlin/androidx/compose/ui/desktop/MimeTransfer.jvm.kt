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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import java.net.URI
import java.nio.file.Path
import kotlin.ByteArray
import kotlin.String

internal const val Utf8PlainTextMimeType = "text/plain;charset=utf-8"
internal const val Utf8PlainTextMimeTypeFallback = "text/plain"
internal const val HtmlMimeType = "text/html;charset=utf-8"
internal const val HtmlMimeTypeFallback = "text/html"
internal const val FileUriListMimeType = "text/uri-list"
internal const val PngMimeType = "image/png"
internal const val WindowLocalDragMimeType = "org.jetbrains.fleet.window-local-drag"

@OptIn(ExperimentalComposeUiApi::class)
internal class LinuxDragAndDropClipboardEntry(
    private val mimeTypes: List<String>,
    private val data: ByteArray?,
) : ClipboardEntry {
    private val acceptedMimeTypes = linkedMapOf(
        WindowLocalDragMimeType to listOf(DragAndDropTransferAction.Move, DragAndDropTransferAction.Copy)
    )

    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
        return getForFormatSync(format)
    }

    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        return data?.let { data ->
            val mimeType = mimeTypes.single()
            if (format.linuxMimeTypes().contains(mimeType)) {
                decodeMimeData(data, format)
            } else {
                emptyList()
            }
        }.orEmpty()
    }

    internal fun containsFormat(format: ClipboardFormat<*>, actions: List<DragAndDropTransferAction>): Boolean {
        val formatMimeTypes = format.linuxMimeTypes()
        return formatMimeTypes.any { it in mimeTypes }
    }

    internal fun acceptsFormat(format: ClipboardFormat<*>, actions: List<DragAndDropTransferAction>) {
        val formatMimeTypes = format.linuxMimeTypes()
        if (formatMimeTypes.any { it in mimeTypes }) {
            acceptedMimeTypes.putAll(formatMimeTypes.map { it to actions })
        }
    }

    internal fun acceptedMimeTypes(): Collection<Pair<String, List<DragAndDropTransferAction>>> = acceptedMimeTypes.map { it.key to it.value }
}

internal fun ClipboardItemsEntry.getDataForLinuxMimeType(mimeType: String): ByteArray? {
    val files = mutableListOf<String>()
    for (item in items) {
        for (element in item.elements) {
            if (element.format.linuxMimeTypes().contains(mimeType)) {
                when (element.format) {
                    ClipboardFormat.Utf8PlainText -> {
                        return (element.value as String).toByteArray(Charsets.UTF_8)
                    }
                    ClipboardFormat.Html -> {
                        return (element.value as String).toByteArray(Charsets.UTF_8)
                    }
                    ClipboardFormat.Png -> {
                        return element.value as ByteArray
                    }
                    ClipboardFormat.File -> {
                        files.add(element.value as String)
                    }
                    ClipboardFormat.WindowLocalDrag -> {
                        return (element.value as LightweightWindowId).value.toString()
                            .toByteArray(Charsets.UTF_8)
                    }
                    is ClipboardFormat.CustomSerializable<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        return (element.format as ClipboardFormat.CustomSerializable<Any>)
                            .encode(element.value)
                            .toByteArray(Charsets.UTF_8)
                    }
                }
            }
        }
    }

    return if (files.isEmpty()) {
        null
    } else {
        files.joinToString(separator = "\r\n", postfix = "\r\n") {
            Path.of(it).toUri().toString()
        }.toByteArray(Charsets.UTF_8)
    }
}

internal fun ClipboardItemsEntry.linuxMimeTypes(): List<String> {
    val mimeTypes = linkedSetOf<String>()
    for (item in items) {
        for (element in item.elements) {
            mimeTypes.addAll(element.format.linuxMimeTypes())
        }
    }
    return mimeTypes.toList()
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
    data: ByteArray,
    format: ClipboardFormat<T>,
): List<T> {
    val list: List<Any> = when (format) {
        ClipboardFormat.Utf8PlainText -> listOf(data.decodeToString())
        ClipboardFormat.Html -> listOf(data.decodeToString())
        ClipboardFormat.Png -> listOf(data)
        ClipboardFormat.File -> decodeUriList(data)
        ClipboardFormat.WindowLocalDrag -> listOfNotNull(data.decodeToString().toLongOrNull()
            ?.let { LightweightWindowId(it) })
        is ClipboardFormat.CustomSerializable<*> -> listOf(format.decode(data.decodeToString()))
    }
    @Suppress("UNCHECKED_CAST")
    return list as List<T>
}

private fun decodeUriList(data: ByteArray): List<String> {
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
