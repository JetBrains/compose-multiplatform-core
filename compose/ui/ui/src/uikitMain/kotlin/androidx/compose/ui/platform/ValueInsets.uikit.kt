/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.platform

internal value class ValueInsets internal constructor(
    val packedValue: Long
): PlatformInsets {
    override val left: Int
        inline get() = ((packedValue ushr 48) and 0xFFFF).toInt()

    override val top: Int
        inline get() = ((packedValue ushr 32) and 0xFFFF).toInt()

    override val right: Int
        inline get() = ((packedValue ushr 16) and 0xFFFF).toInt()

    override val bottom: Int
        inline get() = (packedValue and 0xFFFF).toInt()

    override fun toString(): String {
        return "ValueInsets($left, $top, $right, $bottom)"
    }

    companion object {
        val ZERO = ValueInsets(0L)
    }
}

internal inline fun ValueInsets(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0
): ValueInsets = ValueInsets(
    (left.toLong() shl 48) or
        (top.toLong() shl 32) or
        (right.toLong() shl 16) or
        bottom.toLong()
)