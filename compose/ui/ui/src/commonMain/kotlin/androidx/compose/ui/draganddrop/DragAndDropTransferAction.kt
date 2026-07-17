/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.jvm.JvmInline

/**
 * The possible actions on the transferred object in a drag-and-drop session.
 */
@ExperimentalComposeUiApi
@JvmInline
value class DragAndDropTransferAction private constructor(private val id: Int) {
    override fun toString(): String {
        return when (this) {
            Copy -> "Copy"
            Move -> "Move"
            Link -> "Link"
            else -> "Unknown"
        }
    }

    companion object {
        /**
         * Indicates the dragged object should be copied into the target.
         */
        @ExperimentalComposeUiApi
        val Copy: DragAndDropTransferAction
            get() = DragAndDropTransferAction(0)

        /**
         * Indicates the dragged object should be moved ("cut" and "pasted") into the target.
         */
        @ExperimentalComposeUiApi
        val Move: DragAndDropTransferAction
            get() = DragAndDropTransferAction(1)

        /**
         * Indicates the dragged object should be linked to at the target.
         */
        @ExperimentalComposeUiApi
        val Link: DragAndDropTransferAction
            get() = DragAndDropTransferAction(2)
    }
}
