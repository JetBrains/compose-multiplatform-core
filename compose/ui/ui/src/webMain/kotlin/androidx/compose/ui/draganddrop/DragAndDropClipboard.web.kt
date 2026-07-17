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

@file:OptIn(ExperimentalComposeUiApi::class)

package androidx.compose.ui.draganddrop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.ClipboardFormat
import kotlinx.serialization.json.Json
import org.w3c.dom.DataTransfer

val DragAndDropEvent.clipboardEntry: ClipboardEntry
    get() {
        val dataTransfer = transferData?.domDataTransferOrNull
            ?: error("DragAndDropEvent has no DataTransfer")
        return DragAndDropDataTransferClipboardEntry(dataTransfer)
    }

private class DragAndDropDataTransferClipboardEntry(
    private val dataTransfer: DataTransfer,
) : ClipboardEntry {

    private val fleetData: Map<String, List<String>>? by lazy {
        val json = dataTransfer.getData(FLEET_CLIPBOARD_MIME_TYPE)
        if (json.isNotEmpty()) {
            Json.decodeFromString<Map<String, List<String>>>(json)
        } else {
            null
        }
    }

    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
        return getForFormatSync(format)
    }

    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        val mimeType = format.mimeTypeOrNull ?: return emptyList()

        fleetData?.get(mimeType)?.let { values ->
            return values.mapNotNull { format.deserializeFromString(it) }
        }

        return emptyList()
    }
}
