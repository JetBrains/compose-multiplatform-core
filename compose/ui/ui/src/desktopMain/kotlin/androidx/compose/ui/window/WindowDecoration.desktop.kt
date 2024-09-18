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

package androidx.compose.ui.window

import androidx.compose.runtime.Immutable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowDecoration.Companion.Undecorated
import androidx.compose.ui.window.WindowDecoration.SystemDefault

/**
 * Defines the options for window decoration.
 */
@ExperimentalComposeUiApi
sealed interface WindowDecoration {

    /**
     * Specifies that the default system decoration should be used.
     */
    data object SystemDefault : WindowDecoration

    companion object {
        /**
         * Specifies that the window should be undecorated.
         *
         * If it is resizable, the given thickness will be used for the edge resizers.
         */
        fun Undecorated(
            resizerThickness: Dp = WindowDecorationDefaults.ResizerThickness
        ) : WindowDecoration {
            return UndecoratedWindowDecoration(resizerThickness)
        }
    }
}

@Immutable
internal class UndecoratedWindowDecoration(val resizerThickness: Dp): WindowDecoration {
    override fun equals(other: Any?): Boolean {
        if (other !is UndecoratedWindowDecoration) return false
        return other.resizerThickness == resizerThickness
    }

    override fun hashCode(): Int {
        return resizerThickness.hashCode()
    }
}

/**
 * Default values for window decoration.
 */
@ExperimentalComposeUiApi
object WindowDecorationDefaults {
    /**
     * The default thickness of the resizers in an undecorated window.
     */
    val ResizerThickness: Dp = 8.dp
}

/**
 * Returns the resizer thickness of the given [WindowDecoration].
 */
internal val WindowDecoration.resizerThickness: Dp
    get() = when {
        this is UndecoratedWindowDecoration -> resizerThickness
        else -> WindowDecorationDefaults.ResizerThickness
    }

/**
 * Returns [WindowDecoration.SystemDefault] if [undecorated] is `false`, or
 * [UndecoratedWindowDecoration] with default resizer thickness, if `true`.
 */
internal fun windowDecorationFromFlag(undecorated: Boolean): WindowDecoration =
    if (undecorated) Undecorated() else SystemDefault
