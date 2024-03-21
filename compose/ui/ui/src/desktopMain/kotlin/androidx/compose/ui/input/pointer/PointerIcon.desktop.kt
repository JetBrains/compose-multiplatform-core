/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.input.pointer

import java.awt.Cursor

internal class AwtCursor(val cursor: Cursor) : PointerIcon {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AwtCursor

        // AwtCursor doesn't implement equals
        if (cursor.type != other.cursor.type) return false

        return true
    }

    override fun hashCode(): Int {
        // AwtCursor doesn't implement hashCode
        return cursor.type
    }

    override fun toString(): String {
        return "AwtCursor(cursor=$cursor)"
    }
}

/**
 * Creates [PointerIcon] from [Cursor]
 */
fun PointerIcon(cursor: Cursor): PointerIcon = AwtCursor(cursor)

internal actual val pointerIconDefault: PointerIcon = AwtCursor(Cursor(Cursor.DEFAULT_CURSOR))
internal actual val pointerIconCrosshair: PointerIcon = AwtCursor(Cursor(Cursor.CROSSHAIR_CURSOR))
internal actual val pointerIconText: PointerIcon = AwtCursor(Cursor(Cursor.TEXT_CURSOR))
internal actual val pointerIconHand: PointerIcon = AwtCursor(Cursor(Cursor.HAND_CURSOR))

internal actual val pointerIconMove: PointerIcon = AwtCursor(Cursor(Cursor.MOVE_CURSOR))
internal actual val pointerIconWait: PointerIcon = AwtCursor(Cursor(Cursor.WAIT_CURSOR))
// todo[unterhofer] These aren't actually correct, I think
internal actual val pointerIconColResize: PointerIcon = AwtCursor(Cursor(Cursor.E_RESIZE_CURSOR))
internal actual val pointerIconRowResize: PointerIcon = AwtCursor(Cursor(Cursor.S_RESIZE_CURSOR))

internal actual val pointerIconNeResize: PointerIcon = AwtCursor(Cursor(Cursor.NE_RESIZE_CURSOR))
internal actual val pointerIconNwResize: PointerIcon = AwtCursor(Cursor(Cursor.NW_RESIZE_CURSOR))
internal actual val pointerIconSeResize: PointerIcon = AwtCursor(Cursor(Cursor.SE_RESIZE_CURSOR))
internal actual val pointerIconSwResize: PointerIcon = AwtCursor(Cursor(Cursor.SW_RESIZE_CURSOR))
