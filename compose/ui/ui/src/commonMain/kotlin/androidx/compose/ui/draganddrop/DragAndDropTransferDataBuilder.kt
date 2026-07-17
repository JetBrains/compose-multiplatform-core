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

import androidx.compose.ui.desktop.ClipboardItem
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Builds a [DragAndDropTransferData] that carries the given clipboard [items].
 *
 * On desktop the items are wrapped in a platform transferable; on web they are serialized into a
 * native `DataTransfer`. [supportedActions] declares which transfer actions the drag source offers.
 */
expect fun dragAndDropTransferData(
    vararg items: ClipboardItem,
    supportedActions: List<DragAndDropAction>,
    dragDecorationOffset: Offset = Offset.Zero,
    dragDecorationSize: Size? = null,
    onTransferCompleted: ((Boolean) -> Unit)? = null,
): DragAndDropTransferData

enum class DragAndDropAction {
    Copy,
    Move,
    Link,
}
