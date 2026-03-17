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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.toAwtRectangleRounded
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.requireReal
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlinx.coroutines.channels.Channel


/**
 * Creates a [WindowState] that is remembered across compositions.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param initialPosition The initial position of the window; default if `null`.
 * @param initialSize The initial size of the window; default if `null`.
 * @param initiallyMinimized Whether the window is initially minimized.
 */
@Composable
fun rememberWindowStateWithBounds(
    initialPosition: DpOffset? = null,
    initialSize: DpSize? = null,
    initiallyMinimized: Boolean = false,
): WindowState {
    val sizeProvider =
        initialSize?.let { WindowSizeProvider.Exact(it) } ?: WindowSizeProvider.Default
    val positionProvider =
        initialPosition?.let { WindowPositionProvider.Absolute(it) } ?: WindowPositionProvider.Default
    return rememberWindowState(
        initialBoundsProvider = WindowBoundsProvider(sizeProvider, positionProvider),
        initiallyMinimized = initiallyMinimized
    )
}

/**
 * Creates a [WindowState] that is remembered across compositions.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param initialScreenProvider Provides the initial screen on which the window will be placed.
 * @param initialPlacement The initial placement of the window.
 * @param initialBoundsProvider Provides the initial bounds of the window.
 * @param initiallyMinimized Whether the window is initially minimized.
 */
@Composable
fun rememberWindowState(
    initialScreenProvider: WindowScreenProvider = WindowScreenProvider.Default,
    initialPlacement: WindowPlacement = WindowPlacement.Floating,
    initialBoundsProvider: WindowBoundsProvider = WindowBoundsProvider.Default,
    initiallyMinimized: Boolean = false,
): WindowState = rememberSaveable(saver = WindowState.Saver) {
    WindowState(
        initialScreenProvider = initialScreenProvider,
        initialPlacement = initialPlacement,
        initialBoundsProvider = initialBoundsProvider,
        initiallyMinimized = initiallyMinimized
    )
}

/**
 * Creates a [WindowState] with the specified initial values.
 *
 * @param initialScreenProvider Provides the initial screen on which the window will be placed.
 * @param initialPlacement The initial placement of the window.
 * @param initialBoundsProvider Provides the initial bounds of the window.
 * @param initiallyMinimized Whether the window is initially minimized.
 */
fun WindowState(
    initialScreenProvider: WindowScreenProvider = WindowScreenProvider.Default,
    initialPlacement: WindowPlacement = WindowPlacement.Floating,
    initialBoundsProvider: WindowBoundsProvider = WindowBoundsProvider.Default,
    initiallyMinimized: Boolean = false,
): WindowState = WindowState().apply {
    setScreen(initialScreenProvider)
    setPlacement(initialPlacement)
    setBounds(initialBoundsProvider)
    setMinimized(initiallyMinimized)
}

/**
 * A state object that can be hoisted to control and observe window attributes
 * (size/position/state).
 *
 * @param screen the initial value for [WindowState.screen]
 * @param placement the initial value for [WindowState.placement]
 * @param isMinimized the initial value for [WindowState.isMinimized]
 * @param bounds the initial value for [WindowState.bounds]
 */
@Stable
class WindowState internal constructor(
    screen: Screen?,
    placement: WindowPlacement?,
    isMinimized: Boolean?,
    bounds: DpRect?,
) {
    constructor() : this(
        screen = null,
        placement = null,
        isMinimized = null,
        bounds = null
    )

    init {
        bounds?.requireReal()
    }

    /**
     * The screen with which the window is currently associated; `null` if the window is not yet
     * visible.
     */
    var screen: Screen? by mutableStateOf(screen)
        internal set

    internal val screenRequests = Channel<WindowScreenProvider>(Channel.CONFLATED)

    fun setScreen(screenProvider: WindowScreenProvider) {
        screenRequests.trySend(screenProvider)
    }

    /**
     * The placement of the window on the screen; `null` if the window is not yet visible.
     */
    var placement: WindowPlacement? by mutableStateOf(placement)
        internal set

    internal val placementRequests = Channel<WindowPlacement>(Channel.CONFLATED)

    fun setPlacement(placement: WindowPlacement) {
        placementRequests.trySend(placement)
    }

    /**
     * Whether the window is minimized; `null` if the window is not yet visible.
     */
    var isMinimized: Boolean? by mutableStateOf(isMinimized)
        internal set

    internal val isMinimizedRequests = Channel<Boolean>(Channel.CONFLATED)

    fun setMinimized(value: Boolean) {
        isMinimizedRequests.trySend(value)
    }

    /**
     * The current bounds of the window; `null` if the window is not yet visible.
     */
    var bounds: DpRect? by mutableStateOf(bounds)
        internal set

    internal val boundsRequests = Channel<WindowBoundsProvider>(Channel.CONFLATED)

    /**
     * Set the bounds of the window.
     */
    fun setBounds(boundsProvider: WindowBoundsProvider) {
        boundsRequests.trySend(boundsProvider)
    }

    /**
     * Set the bounds of the window.
     *
     * Setting the bounds when the window placement is not [WindowPlacement.Floating] will change
     * the placement to floating.
     *
     * All the parameters of [bounds] must be specified and finite.
     */
    fun setBounds(bounds: DpRect) {
        boundsRequests.trySend(
            WindowBoundsProvider.Absolute(bounds)
        )
    }

    companion object {
        /**
         * A [Saver] implementation for [WindowState].
         */
        val Saver: Saver<WindowState, Any> = listSaver(
            save = {
                val bounds = it.bounds
                arrayListOf(
                    it.screen?.device?.iDstring ?: "",
                    it.placement?.ordinal ?: -1,
                    it.isMinimized,
                    bounds != null,
                    bounds?.top?.value ?: 0f,
                    bounds?.left?.value ?: 0f,
                    bounds?.right?.value ?: 0f,
                    bounds?.bottom?.value ?: 0f,
                )
            },
            restore = { state ->
                WindowState(
                    screen = (state[0] as String).let { idString ->
                        if (idString.isEmpty()) return@let null
                        val device = GraphicsEnvironment
                            .getLocalGraphicsEnvironment()
                            .screenDevices
                            .firstOrNull { it.iDstring == idString }
                        if (device != null) Screen(device) else null
                    },
                    placement = (state[1] as Int).let { ordinal ->
                        if (ordinal >= 0) WindowPlacement.entries[ordinal] else null
                    },
                    isMinimized = state[2] as Boolean?,
                    bounds = if (state[3] as Boolean) {
                        DpRect(
                            top = Dp(state[4] as Float),
                            left = Dp(state[5] as Float),
                            right = Dp(state[6] as Float),
                            bottom = Dp(state[7] as Float)
                        )
                    } else null,
                )
            }
        )
    }
}

/**
 * Returns the bounds of the window, as an AWT [Rectangle].
 */
fun WindowState.awtBounds(): Rectangle? = bounds?.toAwtRectangleRounded()
