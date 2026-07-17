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
import androidx.compose.ui.desktop.ClipboardItem
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.serialization.json.Json
import org.w3c.dom.DataTransfer

actual fun dragAndDropTransferData(
    vararg items: ClipboardItem,
    supportedActions: List<DragAndDropAction>,
    dragDecorationOffset: Offset,
    dragDecorationSize: Size?,
    onTransferCompleted: ((Boolean) -> Unit)?,
): DragAndDropTransferData {
    val dataTransfer = createDataTransfer()

    val multiMap = linkedMapOf<String, MutableList<String>>()
    for (item in items) {
        for (element in item.elements) {
            val mimeType = element.format.mimeTypeOrNull ?: continue
            val value = element.serializeToStringOrNull() ?: continue
            multiMap.getOrPut(mimeType) { mutableListOf() }.add(value)
        }
    }

    dataTransfer.setData(FLEET_CLIPBOARD_MIME_TYPE, Json.encodeToString<Map<String, List<String>>>(multiMap))

    return DragAndDropTransferData(
        nativeTransferData = dataTransfer,
        onTransferCompleted = onTransferCompleted,
        dragDecorationSize = dragDecorationSize,
    )
}

private fun createDataTransfer(): DataTransfer = js("new DataTransfer()")
