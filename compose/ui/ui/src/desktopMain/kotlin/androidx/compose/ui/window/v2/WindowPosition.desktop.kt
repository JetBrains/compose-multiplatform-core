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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * Constructs a [WindowPosition.Absolute] from [x] and [y] values.
 */
fun WindowPosition(x: Int, y: Int) = WindowPosition.Absolute(x, y)

/**
 * Position of the window or dialog on the screen in [Dp].
 *
 * @see androidx.compose.ui.window.WindowState
 */
@Immutable
abstract class WindowPosition private constructor() {
    /**
     * Initial position of the window that depends on the platform.
     * Usually every new window will be positioned in a cascade mode,
     * on the same display where the previous focused window was.
     *
     * This value may be used only before the window is visible.
     * After the window is visible, it cannot change its position to [PlatformDefault].
     */
    data object PlatformDefault : WindowPosition()

    /**
     * Window will be aligned relative to the screen it is shown on.
     *
     * @param alignment Defines the alignment relative to the screen, ignoring insets (taskbar,
     * OS menu bar, etc.)
     */
    @Immutable
    class AlignedToScreen(val alignment: Alignment) : WindowPosition() {
        @Stable
        override fun toString() = "AlignedToScreen($alignment)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AlignedToScreen) return false

            if (alignment != other.alignment) return false

            return true
        }

        override fun hashCode(): Int {
            return alignment.hashCode()
        }
    }

    /**
     * Absolute position of the window on the current window screen
     *
     * @param offset Offset of the window from the top left corner of the screen.
     */
    @Immutable
    class Absolute(val offset: IntOffset) : WindowPosition() {
        /**
         * Constructs a [WindowPosition.Absolute] from [x] and [y] values.
         */
        constructor(x: Int, y: Int) : this(IntOffset(x, y))

        @Stable
        override fun toString() = "Absolute($offset)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Absolute) return false

            if (offset != other.offset) return false

            return true
        }

        override fun hashCode(): Int {
            return offset.hashCode()
        }
    }

    /**
     * Wraps a legacy [androidx.compose.ui.window.WindowPosition] value.
     */
    @Suppress("DEPRECATION")
    internal class Legacy(
        val position: androidx.compose.ui.window.WindowPosition
    ) : WindowPosition() {

        override fun toString() = "Legacy($position)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Legacy) return false

            if (position != other.position) return false

            return true
        }

        override fun hashCode(): Int {
            return position.hashCode()
        }
    }
}