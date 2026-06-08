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

package androidx.compose.ui.desktop

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

fun clipboardEntry(vararg items: ClipboardItem): ClipboardEntry =
    ClipboardItemsEntry(items.toList())


fun clipboardEntry(block: ClipboardEntryBuilderScope.() -> Unit): ClipboardEntry {
    val builder = ClipboardEntryBuilderImpl().apply(block)
    return clipboardEntry(*builder.build().toTypedArray())
}

interface ClipboardEntry {
    suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T>
    fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T>
    fun <T : Any> getFirstOrNullForFormatSync(format: ClipboardFormat<T>): T? =
        getForFormatSync(format).firstOrNull()

    suspend fun <T : Any> getFirstOrNullForFormat(format: ClipboardFormat<T>): T? =
        getForFormat(format).firstOrNull()
}

class ClipboardItemsEntry(val items: List<ClipboardItem>) : ClipboardEntry {
    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> =
        getForFormatSync(format)

    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return items
            .flatMap { it.elements }
            .filter { it.format == format }
            .map { it.value as T }
    }
}

sealed interface ClipboardFormat<T : Any> {
    data object Utf8PlainText : ClipboardFormat<String>
    data object Html : ClipboardFormat<String>
    data object Png : ClipboardFormat<ByteArray>
    data object File : ClipboardFormat<String>
    data object WindowLocalDrag : ClipboardFormat<LightweightWindowId>
    data class CustomSerializable<T : Any>(
        val mimeType: String,
        val serializer: KSerializer<T>,
    ) : ClipboardFormat<T> {
        fun decode(serializedForm: String): T = json.decodeFromString(serializer, serializedForm)
        fun encode(value: T): String = json.encodeToString(serializer, value)
    }
}

data class ClipboardItem(val elements: List<ClipboardElement<*>>) {
    constructor(vararg elements: ClipboardElement<*>) : this(elements.toList())

    companion object {
        operator fun <T : Any> invoke(value: T, format: ClipboardFormat<T>): ClipboardItem =
            ClipboardItem(listOf(ClipboardElement(value, format)))
    }
}
data class ClipboardElement<T : Any>(val value: T, val format: ClipboardFormat<T>)

interface ClipboardEntryBuilderScope {
    fun add(element: ClipboardElement<*>)
    fun <T: Any> add(value: T, format: ClipboardFormat<T>)
    fun <T: Any> add(values: List<T>, format: ClipboardFormat<T>)
    fun add(elements: List<ClipboardElement<*>>)
}

private class ClipboardEntryBuilderImpl : ClipboardEntryBuilderScope {
    private val sources = mutableListOf<List<ClipboardElement<*>>>()

    override fun add(element: ClipboardElement<*>) {
        sources.add(listOf(element))
    }

    override fun <T : Any> add(
        value: T,
        format: ClipboardFormat<T>,
    ) {
        sources.add(listOf(ClipboardElement(value, format)))
    }


    override fun <T : Any> add(
        values: List<T>,
        format: ClipboardFormat<T>,
    ) {
        sources.add(values.map { value -> ClipboardElement(value, format) })
    }

    override fun add(elements: List<ClipboardElement<*>>) {
        sources.add(elements)
    }

    fun build(): List<ClipboardItem> {
        if (sources.isEmpty()) return emptyList()

        val maxSize = sources.maxOf { it.size }
        return (0 until maxSize).map { index ->
            val elements = sources.mapNotNull { source ->
                source.getOrNull(index)
            }
            ClipboardItem(elements)
        }
    }
}

private val json by lazy { Json { ignoreUnknownKeys = true } }
