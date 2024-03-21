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
import androidx.compose.ui.platform.DesktopPlatform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Defines the options for window decoration.
 */
@ExperimentalComposeUiApi
sealed interface WindowDecoration {

    val isDecorated: Boolean

    /**
     * Specifies that the default system decoration should be used.
     */
    data object Decorated : WindowDecoration {
        override val isDecorated: Boolean = true
    }

    /**
     * Specifies that the window should be undecorated.
     *
     * If it is resizable, the given thickness will be used for the edge resizers.
     */
    @Immutable
    class Undecorated(val resizerThickness: Dp = WindowDecorationDefaults.ResizerThickness) :
        WindowDecoration {
        override val isDecorated: Boolean = false

        override fun equals(other: Any?): Boolean {
            if (other !is Undecorated) return false
            return other.resizerThickness == resizerThickness
        }

        override fun hashCode(): Int {
            return resizerThickness.hashCode()
        }
    }

    /**
     * Specifies that the window should be decorated with a custom title bar.
     *
     * If it is resizable, the given thickness will be used for the edge resizers.
     */
    @Immutable
    class CustomTitleBar(val height: Dp) : WindowDecoration {
        override val isDecorated: Boolean = true

        override fun equals(other: Any?): Boolean {
            if (other !is CustomTitleBar) return false
            return other.height == height
        }

        override fun hashCode(): Int {
            return height.hashCode()
        }
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

    /**
     * The default height of the region at the top of an undecorated window
     * where it can be drag-moved.
     */
    val CustomTitleBarHeight: Dp = when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> 28.dp
        DesktopPlatform.Windows -> 32.dp
        else -> 24.dp
    }
}

/**
 * Returns the resizer thickness of the given [WindowDecoration].
 */
internal val WindowDecoration.resizerThickness: Dp
    get() = when {
        this is WindowDecoration.Undecorated -> resizerThickness
        else -> WindowDecorationDefaults.ResizerThickness
    }

/**
 * Returns [WindowDecoration.Decorated] if [undecorated] is `false`, or
 * [WindowDecoration.Undecorated] with default resizer thickness, if `true`.
 */
internal fun windowDecorationFromFlag(undecorated: Boolean): WindowDecoration =
    if (undecorated) WindowDecoration.Undecorated() else WindowDecoration.Decorated
