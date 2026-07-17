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

package androidx.compose.ui.draganddrop

import androidx.compose.ui.desktop.ClipboardElement
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.desktop.LightweightWindowId
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal const val FLEET_CLIPBOARD_MIME_TYPE = "application/x-fleet-clipboard"

internal val ClipboardFormat<*>.mimeTypeOrNull: String?
    get() = when (this) {
        ClipboardFormat.Utf8PlainText -> "text/plain"
        ClipboardFormat.Html -> "text/html"
        ClipboardFormat.File -> "text/uri-list"
        ClipboardFormat.Png -> "image/png"
        ClipboardFormat.WindowLocalDrag -> "org.jetbrains.fleet.windowLocalDrag"
        is ClipboardFormat.CustomSerializable<*> -> mimeType
    }

@OptIn(ExperimentalEncodingApi::class)
@Suppress("UNCHECKED_CAST")
internal fun ClipboardElement<*>.serializeToStringOrNull(): String? = when (format) {
    ClipboardFormat.Utf8PlainText -> value as String
    ClipboardFormat.Html -> value as String
    ClipboardFormat.File -> value as String
    ClipboardFormat.Png -> Base64.encode(value as ByteArray)
    ClipboardFormat.WindowLocalDrag -> (value as LightweightWindowId).value.toString()
    is ClipboardFormat.CustomSerializable<*> -> {
        (format as ClipboardFormat.CustomSerializable<Any>).encode(value!!)
    }
}

/**
 * Deserializes a string from a `DataTransfer` back to the value type of [ClipboardFormat].
 */
@OptIn(ExperimentalEncodingApi::class)
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> ClipboardFormat<T>.deserializeFromString(data: String): T? = when (this) {
    ClipboardFormat.Utf8PlainText -> data as T
    ClipboardFormat.Html -> data as T
    ClipboardFormat.File -> data as T
    ClipboardFormat.Png -> Base64.decode(data) as T
    ClipboardFormat.WindowLocalDrag -> data.trim().toLongOrNull()?.let(::LightweightWindowId) as T?
    is ClipboardFormat.CustomSerializable<*> -> {
        (this as ClipboardFormat.CustomSerializable<T>).decode(data)
    }
}
