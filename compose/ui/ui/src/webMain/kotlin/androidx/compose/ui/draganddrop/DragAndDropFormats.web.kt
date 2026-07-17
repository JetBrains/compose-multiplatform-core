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
import androidx.compose.ui.desktop.ClipboardFormat
import kotlinx.serialization.json.Json
import org.w3c.dom.DataTransfer

/**
 * Returns the transfer action for this drag-and-drop [DragAndDropEvent].
 *
 * The web platform does not expose a per-event transfer action, so a move is always reported,
 * matching the behavior of the Fleet drag-and-drop helpers.
 */
fun DragAndDropEvent.action(): DragAndDropTransferAction? = DragAndDropTransferAction.Move

/**
 * Returns whether this drag-and-drop [DragAndDropEvent] carries data in the given [format].
 *
 * Native formats are matched against the `DataTransfer` type list; Fleet-specific formats are packed
 * inside a single [FLEET_CLIPBOARD_MIME_TYPE] blob whose keys are inspected when the payload is
 * readable (i.e. on drop). [actions] is accepted for signature parity with the desktop helper.
 */
fun DragAndDropEvent.containsFormat(
    format: ClipboardFormat<*>,
    actions: List<DragAndDropTransferAction>,
): Boolean {
    val mime = format.mimeTypeOrNull ?: return false
    val transfer = transferData?.domDataTransferOrNull ?: return false
    if (dataTransferHasType(transfer, mime)) return true
    if (dataTransferHasType(transfer, FLEET_CLIPBOARD_MIME_TYPE)) {
        val raw = transfer.getData(FLEET_CLIPBOARD_MIME_TYPE)
        if (raw.isNotEmpty()) {
            val keys = runCatching {
                Json.decodeFromString<Map<String, List<String>>>(raw).keys
            }.getOrNull()
            if (keys != null && mime in keys) return true
        }
    }
    return false
}

/**
 * Marks the given [format] as accepted for this drag-and-drop [DragAndDropEvent].
 *
 * No-op on web: drop acceptance is managed by the browser drag-and-drop lifecycle. Provided for
 * signature parity with the desktop helper.
 */
fun DragAndDropEvent.acceptsFormat(format: ClipboardFormat<*>, actions: List<DragAndDropTransferAction>) {
}

private fun dataTransferHasType(dataTransfer: DataTransfer, type: String): Boolean =
    js("dataTransfer.types != null && Array.from(dataTransfer.types).includes(type)")
