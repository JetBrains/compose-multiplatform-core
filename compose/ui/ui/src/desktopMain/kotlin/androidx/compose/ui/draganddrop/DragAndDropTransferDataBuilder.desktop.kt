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
import androidx.compose.ui.desktop.KdtDragAndDropTransferable
import androidx.compose.ui.desktop.clipboardEntry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ClipEntry

actual fun dragAndDropTransferData(
    vararg items: ClipboardItem,
    supportedActions: List<DragAndDropAction>,
    dragDecorationOffset: Offset,
    dragDecorationSize: Size?,
    onTransferCompleted: ((Boolean) -> Unit)?,
): DragAndDropTransferData {
    return DragAndDropTransferData(
        transferable = KdtDragAndDropTransferable(ClipEntry(clipboardEntry(*items))),
        supportedActions = supportedActions.map { it.toTransferAction() },
        dragDecorationOffset = dragDecorationOffset,
        dragDecorationSize = dragDecorationSize,
        onTransferCompleted = onTransferCompleted?.let { callback -> { action -> callback(action != null) } },
    )
}

private fun DragAndDropAction.toTransferAction(): DragAndDropTransferAction = when (this) {
    DragAndDropAction.Copy -> DragAndDropTransferAction.Copy
    DragAndDropAction.Move -> DragAndDropTransferAction.Move
    DragAndDropAction.Link -> DragAndDropTransferAction.Link
}
