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

package androidx.compose.ui.window.v2

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import java.awt.Insets


/**
 * Represents a set of insets in [Dp] units.
 *
 * @see Screen.insets
 */
@ExperimentalComposeUiApi
class DpInsets(
    val top: Dp,
    val left: Dp,
    val bottom: Dp,
    val right: Dp
) {

    /**
     * Returns the sum of the insets.
     */
    operator fun plus(other: DpInsets) = DpInsets(
        top = top + other.top,
        left = left + other.left,
        bottom = bottom + other.bottom,
        right = right + other.right
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DpInsets) return false

        if (top != other.top) return false
        if (left != other.left) return false
        if (bottom != other.bottom) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = top.hashCode()
        result = 31 * result + left.hashCode()
        result = 31 * result + bottom.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }
}

/**
 * Returns the rectangle remaining after applying the given insets.
 */
fun DpRect.withInsets(insets: DpInsets): DpRect =
    DpRect(
        top = top + insets.top,
        left = left + insets.left,
        bottom = bottom - insets.bottom,
        right = right - insets.right
    )

/**
 * Converts AWT [Insets] to [DpInsets].
 */
internal fun Insets.toDpInsets() = DpInsets(
    top = top.dp,
    left = left.dp,
    bottom = bottom.dp,
    right = right.dp
)
