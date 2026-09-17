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
data class WindowFrame(
    val padding: Padding,
    val resizerThickness: ResizerThickness,
) {
    fun isEmpty(): Boolean {
        return padding.isEmpty() && resizerThickness.isEmpty()
    }

    @ExperimentalComposeUiApi
    @Immutable
    data class Padding(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp) {
        companion object {
            fun withAll(value: Dp): Padding {
                return Padding(left = value, top = value, right = value, bottom = value)
            }

            fun default(): Padding {
                return withAll(24.dp)
            }
        }

        fun isEmpty(): Boolean {
            return left == 0.dp && top == 0.dp && right == 0.dp && bottom == 0.dp
        }
    }

    @ExperimentalComposeUiApi
    @Immutable
    data class ResizerThickness(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp) {
        companion object {
            fun withAll(value: Dp): ResizerThickness {
                return ResizerThickness(left = value, top = value, right = value, bottom = value)
            }

            fun default(): ResizerThickness {
                return withAll(12.dp)
            }
        }

        fun isEmpty(): Boolean {
            return left == 0.dp && top == 0.dp && right == 0.dp && bottom == 0.dp
        }
    }
}

@ExperimentalComposeUiApi
@Immutable
data class WindowFrameTiling(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    fun isTiled(): Boolean {
        return left || right || top || bottom
    }
}

/**
 * Defines the options for window decoration.
 */
@ExperimentalComposeUiApi
sealed interface WindowDecoration {
    /**
     * Specifies that the default system decoration is used.
     */
    data object Decorated : WindowDecoration

    @ExperimentalComposeUiApi
    @Immutable
    data class CustomTitleBar(
        val height: Dp,
        val insetLeft: Dp,
        val insetRight: Dp,
    ) : WindowDecoration

    @ExperimentalComposeUiApi
    @Immutable
    data class Undecorated(
        val frame: WindowFrame?,
        val tiling: WindowFrameTiling?,
        val titleBarLayoutLeft: List<TitleBarElement>,
        val titleBarLayoutRight: List<TitleBarElement>,
    ) : WindowDecoration

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
 * The thickness of the resizers that the AWT windows use when no frame gives one.
 */
internal val DefaultUndecoratedResizerThickness: Dp = 8.dp

/**
 * Returns the resizer thickness of the given [WindowDecoration].
 */
internal val WindowDecoration.resizerThickness: Dp
    get() = when {
        this is WindowDecoration.Undecorated ->
            frame?.resizerThickness?.right ?: DefaultUndecoratedResizerThickness
        else -> DefaultUndecoratedResizerThickness
    }

/**
 * Returns [WindowDecoration.Decorated] if [undecorated] is `false`, or
 * [WindowDecoration.Undecorated] with the default frame, if `true`.
 */
internal fun windowDecorationFromFlag(undecorated: Boolean): WindowDecoration =
    if (undecorated) {
        WindowDecoration.Undecorated(
            frame = WindowFrame(
                padding = WindowFrame.Padding.default(),
                resizerThickness = WindowFrame.ResizerThickness.default(),
            ),
            tiling = null,
            titleBarLayoutLeft = emptyList(),
            titleBarLayoutRight = emptyList(),
        )
    } else {
        WindowDecoration.Decorated
    }
