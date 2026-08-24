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

@ExperimentalComposeUiApi
@Immutable
class WindowFrameSide(
    val padding: Dp,
    val resizerThickness: Dp,
    val tiled: Boolean,
) {
    constructor(padding: Dp, resizerThickness: Dp) : this(padding, resizerThickness, tiled = false)

    fun isEmpty(): Boolean {
        return padding == 0.dp && resizerThickness == 0.dp
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowFrameSide) return false

        if (tiled != other.tiled) return false
        if (padding != other.padding) return false
        if (resizerThickness != other.resizerThickness) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tiled.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + resizerThickness.hashCode()
        return result
    }

    override fun toString(): String {
        return "WindowFrameSide(padding=$padding, resizerThickness=$resizerThickness, tiled=$tiled)"
    }
}

@ExperimentalComposeUiApi
@Immutable
class WindowFrame(
    val left: WindowFrameSide,
    val top: WindowFrameSide,
    val right: WindowFrameSide,
    val bottom: WindowFrameSide,
) {
    companion object {
        fun default(): WindowFrame {
            val frameSide = WindowFrameSide(padding = 24.dp, resizerThickness = 12.dp)
            return WindowFrame(left = frameSide, top = frameSide, right = frameSide, bottom = frameSide)
        }
    }

    fun isEmpty(): Boolean {
        return left.isEmpty() && top.isEmpty() && right.isEmpty() && bottom.isEmpty()
    }

    fun isTiled(): Boolean {
        return left.tiled || top.tiled || right.tiled || bottom.tiled
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowFrame) return false

        if (left != other.left) return false
        if (top != other.top) return false
        if (right != other.right) return false
        if (bottom != other.bottom) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + right.hashCode()
        result = 31 * result + bottom.hashCode()
        return result
    }

    override fun toString(): String {
        return "WindowFrame(left=$left, top=$top, right=$right, bottom=$bottom)"
    }
}


/**
 * Defines the options for window decoration.
 */
@ExperimentalComposeUiApi
sealed interface WindowDecoration {

    val isDecorated: Boolean

    val leftTitleBarElements: List<TitleBarElement>
    val rightTitleBarElements: List<TitleBarElement>

    /**
     * Specifies that the default system decoration should be used.
     */
    data object Decorated : WindowDecoration {
        override val isDecorated: Boolean = true
        override val leftTitleBarElements: List<TitleBarElement> =
            WindowDecorationDefaults.LeftTitleBarElements
        override val rightTitleBarElements: List<TitleBarElement> =
            WindowDecorationDefaults.RightTitleBarElements
    }

    /**
     * Specifies that the window should be undecorated.
     *
     * If it is resizable, the given thickness will be used for the edge resizers.
     */
    @Immutable
    class Undecorated(val frame: WindowFrame = WindowFrame.default()) : WindowDecoration {
        override val isDecorated: Boolean = false
        override val leftTitleBarElements: List<TitleBarElement> = emptyList()
        override val rightTitleBarElements: List<TitleBarElement> = emptyList()

        override fun equals(other: Any?): Boolean {
            if (other !is Undecorated) return false
            return other.frame == frame
        }

        override fun hashCode(): Int {
            return frame.hashCode()
        }
    }

    /**
     * Specifies that the window should be decorated with a custom title bar.
     *
     * If it is resizable, the given thickness will be used for the edge resizers.
     */
    @Immutable
    class CustomTitleBar(val height: Dp, val roundedWindowCorners: Boolean = false) : WindowDecoration {
        override val isDecorated: Boolean = true
        override val leftTitleBarElements: List<TitleBarElement> =
            WindowDecorationDefaults.LeftTitleBarElements
        override val rightTitleBarElements: List<TitleBarElement> =
            WindowDecorationDefaults.RightTitleBarElements

        override fun equals(other: Any?): Boolean {
            if (other !is CustomTitleBar) return false
            return other.height == height && other.roundedWindowCorners == roundedWindowCorners
        }

        override fun hashCode(): Int {
            return height.hashCode() * 31 + roundedWindowCorners.hashCode()
        }
    }

    enum class TitleBarElement {
        AppMenu,
        Icon,
        Spacer,
        MinimizeButton,
        MaximizeButton,
        FullscreenButton,
        CloseButton,
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
    val CustomTitleBarHeight: Dp = windowDecorationCustomTitleBarHeight()

    val LeftTitleBarElements: List<WindowDecoration.TitleBarElement> =
        windowDecorationLeftTitleBarElements()

    val RightTitleBarElements: List<WindowDecoration.TitleBarElement> =
        windowDecorationRightTitleBarElements()
}

/**
 * Returns the resizer thickness of the given [WindowDecoration].
 */
internal val WindowDecoration.resizerThickness: Dp
    get() = when {
        this is WindowDecoration.Undecorated -> frame.right.resizerThickness
        else -> WindowDecorationDefaults.ResizerThickness
    }

/**
 * Returns [WindowDecoration.Decorated] if [undecorated] is `false`, or
 * [WindowDecoration.Undecorated] with default resizer thickness, if `true`.
 */
internal fun windowDecorationFromFlag(undecorated: Boolean): WindowDecoration =
    if (undecorated) WindowDecoration.Undecorated(WindowFrame.default()) else WindowDecoration.Decorated

internal expect fun windowDecorationCustomTitleBarHeight(): Dp

internal expect fun windowDecorationLeftTitleBarElements(): List<WindowDecoration.TitleBarElement>

internal expect fun windowDecorationRightTitleBarElements(): List<WindowDecoration.TitleBarElement>
