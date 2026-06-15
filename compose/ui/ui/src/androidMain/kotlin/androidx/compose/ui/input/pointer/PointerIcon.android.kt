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

import android.view.PointerIcon as AndroidViewPointerIcon

internal class AndroidPointerIconType(val type: Int) : PointerIcon {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AndroidPointerIconType

        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        return type
    }

    override fun toString(): String {
        return "AndroidPointerIcon(type=$type)"
    }
}

internal class AndroidPointerIcon(val pointerIcon: android.view.PointerIcon) : PointerIcon {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AndroidPointerIcon

        return pointerIcon == other.pointerIcon
    }

    override fun hashCode(): Int {
        return pointerIcon.hashCode()
    }

    override fun toString(): String {
        return "AndroidPointerIcon(pointerIcon=$pointerIcon)"
    }
}

/** Creates [PointerIcon] from [android.view.PointerIcon] */
fun PointerIcon(pointerIcon: android.view.PointerIcon): PointerIcon =
    AndroidPointerIcon(pointerIcon)

/** Creates [PointerIcon] from pointer icon type (see [android.view.PointerIcon.getSystemIcon] */
fun PointerIcon(pointerIconType: Int): PointerIcon = AndroidPointerIconType(pointerIconType)

internal actual val pointerIconDefault: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_ARROW)
internal actual val pointerIconCrosshair: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_CROSSHAIR)
internal actual val pointerIconText: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_TEXT)
internal actual val pointerIconHand: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_HAND)
internal actual val pointerIconColResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
internal actual val pointerIconRowResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
internal actual val pointerIconNResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
internal actual val pointerIconEResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
internal actual val pointerIconSResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
internal actual val pointerIconWResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
internal actual val pointerIconNeResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW)
internal actual val pointerIconNwResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW)
internal actual val pointerIconSeResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW)
internal actual val pointerIconSwResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW)
internal actual val pointerIconNSResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
internal actual val pointerIconEWResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
internal actual val pointerIconNeSwResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW)
internal actual val pointerIconNwSeResize: PointerIcon = AndroidPointerIconType(AndroidViewPointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW)
