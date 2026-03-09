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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.WindowPlacement

/**
 * Creates a [WindowState] that is remembered across compositions.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param placement the initial value for [WindowState.placement]
 * @param isMinimized the initial value for [WindowState.isMinimized]
 */
@Composable
fun rememberWindowState(
    placement: WindowPlacement = WindowPlacement.Floating,
    isMinimized: Boolean = false,
): WindowState = rememberSaveable(saver = WindowState.Saver) {
    WindowState(placement, isMinimized)
}

/**
 * Creates a [WindowState] with the specified initial values.
 *
 * @param placement the initial value for [WindowState.placement]
 * @param isMinimized the initial value for [WindowState.isMinimized]
 */
fun WindowState(
    placement: WindowPlacement = WindowPlacement.Floating,
    isMinimized: Boolean = false,
): WindowState = WindowState(
    placement = placement,
    isMinimized = isMinimized,
    bounds = null
)

/**
 * A state object that can be hoisted to control and observe window attributes
 * (size/position/state).
 *
 * @param placement the initial value for [WindowState.placement]
 * @param isMinimized the initial value for [WindowState.isMinimized]
 * @param bounds the initial value for [WindowState.bounds]
 */
@Stable
class WindowState internal constructor(
    placement: WindowPlacement,
    isMinimized: Boolean,
    bounds: IntRect? = null,
) {
    /**
     * Describes how the window is placed on the screen.
     */
    var placement: WindowPlacement by mutableStateOf(placement)

    /**
     * Whether the window is minimized.
     */
    var isMinimized: Boolean by mutableStateOf(isMinimized)

    /**
     * The backing property for the window bounds.
     */
    private var _bounds: IntRect? by mutableStateOf(bounds)

    /**
     * The current bounds of the window; `null` if unknown (e.g., the window is not yet visible).
     */
    val bounds: IntRect?
        get() = _bounds


    /**
     * Set the bounds of the window.
     *
     * Setting the bounds when the window placement is not [WindowPlacement.Floating] will change
     * the placement to floating.
     */
    fun setBounds(bounds: IntRect) {
        if (this.placement != WindowPlacement.Floating) {
            this.placement = WindowPlacement.Floating
        }
        _bounds = bounds
    }

    internal fun setBoundsDirect(bounds: IntRect) {
        _bounds = bounds
    }

    companion object {
        /**
         * A [Saver] implementation for [WindowState].
         */
        val Saver = listSaver(
            save = {
                listOf(
                    it.placement.ordinal,
                    it.isMinimized,
                    it.bounds
                )
            },
            restore = { state ->
                WindowState(
                    placement = WindowPlacement.entries[state[0] as Int],
                    isMinimized = state[1] as Boolean,
                    bounds = state[2] as IntRect?,
                )
            }
        )
    }
}
