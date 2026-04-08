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

package androidx.compose.ui.awt.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableOpenTarget
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.awt.toAwtRectangleRounded
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.ComponentUpdater
import androidx.compose.ui.util.componentListenerRef
import androidx.compose.ui.util.setIcon
import androidx.compose.ui.util.setUndecoratedSafely
import androidx.compose.ui.util.windowListenerRef
import androidx.compose.ui.util.windowStateListenerRef
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.UndecoratedWindowDecoration
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowLocationTracker
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.density
import androidx.compose.ui.window.requireReal
import androidx.compose.ui.window.resizerThickness
import androidx.compose.ui.window.roundToDimensionOrNull
import androidx.compose.ui.window.v2.Screen
import androidx.compose.ui.window.v2.Window
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowGeometryProviderScope
import androidx.compose.ui.window.v2.WindowScreenProvider
import androidx.compose.ui.window.v2.WindowScreenProviderScope
import androidx.compose.ui.window.v2.WindowSizeLimits
import androidx.compose.ui.window.v2.WindowState
import androidx.compose.ui.window.v2.rememberWindowState
import androidx.compose.ui.window.v2.toDpInsets
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


/**
 * Similar to the corresponding [androidx.compose.ui.window.v2.Window] function, but additionally
 * allows configuring the underlying AWT window before it has been made displayable by providing an
 * [init] block.
 *
 * This is useful to:
 * - Set window properties which cannot be changed after it has been made displayable, such as
 *   [java.awt.Window.setType].
 * - Adding listeners for events that can occur when the window becomes displayable/visible.
 *
 * IMPORTANT: this function should not be used to set properties which can be changed after the
 * window has been made displayable. Doing so can cause your code to stop working in the future if
 * a parameter that controls this property is added to this function.
 * For example, if you set the window's minimum size in [init] and later a `minimumSize` parameter
 * is added to this function, it will override your setting of the minimum size in [init].
 *
 * To set these kinds of properties, use this pattern instead:
 * ```
 * Window( ... ) {
 *     // Window content here
 *     LaunchedEffect(window) {
 *         // Configure window here
 *     }
 * }
 * ```
 *
 * @see Window
 */
@ExperimentalComposeUiApi
@Composable
@ComposableOpenTarget(-1)
fun SwingWindow(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    decoration: WindowDecoration = WindowDecoration.SystemDefault,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    sizeLimits: WindowSizeLimits = WindowSizeLimits.Unlimited,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    init: (ComposeWindow) -> Unit,
    content: @Composable FrameWindowScope.() -> Unit
) {
    val currentState by rememberUpdatedState(state)
    val currentTitle by rememberUpdatedState(title)
    val currentIcon by rememberUpdatedState(icon)
    val currentDecoration by rememberUpdatedState(decoration)
    val currentTransparent by rememberUpdatedState(transparent)
    val currentResizable by rememberUpdatedState(resizable)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentFocusable by rememberUpdatedState(focusable)
    val currentAlwaysOnTop by rememberUpdatedState(alwaysOnTop)
    val currentSizeLimits by rememberUpdatedState(sizeLimits)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)

    val updater = remember(::ComponentUpdater)

    val listeners = remember {
        object {
            var windowListenerRef = windowListenerRef()
            var windowStateListenerRef = windowStateListenerRef()
            var componentListenerRef = componentListenerRef()

            fun removeFromAndClear(window: ComposeWindow) {
                windowListenerRef.unregisterFromAndClear(window)
                windowStateListenerRef.unregisterFromAndClear(window)
                componentListenerRef.unregisterFromAndClear(window)
            }
        }
    }

    val coroutineContext = rememberCoroutineScope().coroutineContext

    var window: ComposeWindow? by remember { mutableStateOf(null) }
    SwingWindow(
        visible = visible,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        create = {
            val graphicsDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            val currentDevice = currentState._screenId?.let { screenId ->
                graphicsDevices.firstOrNull { it.iDstring == screenId }
            }
            val initialDevice = currentDevice
                ?: state.screenRequests.tryReceive().getOrNull()?.getInitialScreenDevice()
                ?: WindowScreenProvider.Default.getInitialScreenDevice()

            ComposeWindow(
                graphicsConfiguration = initialDevice.defaultConfiguration,
                coroutineContext = coroutineContext
            ).apply {
                // close state is controlled by WindowState.isOpen
                defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
                listeners.windowListenerRef.registerWithAndSet(
                    this,
                    object : WindowAdapter() {
                        override fun windowClosing(e: WindowEvent) {
                            currentOnCloseRequest()
                        }
                    }
                )
                listeners.windowStateListenerRef.registerWithAndSet(this) {
                    currentState._placement = placement
                    currentState._isMinimized = isMinimized
                }
                listeners.componentListenerRef.registerWithAndSet(
                    this,
                    object : ComponentAdapter() {
                        fun applyBoundsChanges() {
                            currentState._bounds = DpRect(x.dp, y.dp, (x + width).dp, (y + height).dp)
                            if (currentState._screenId != graphicsConfiguration.device.iDstring) {
                                currentState._screenId = graphicsConfiguration.device.iDstring
                            }
                        }

                        override fun componentShown(e: ComponentEvent) {
                            // Initialize all state properties
                            currentState._placement = placement
                            currentState._isMinimized = isMinimized
                            applyBoundsChanges()
                            currentState.isInitialized = true
                        }

                        override fun componentResized(e: ComponentEvent) {
                            // we check placement here and in windowStateChanged,
                            // because fullscreen changing doesn't
                            // fire windowStateChanged, only componentResized
                            currentState._placement = placement
                            applyBoundsChanges()
                        }

                        override fun componentMoved(e: ComponentEvent) {
                            applyBoundsChanges()
                        }
                    }
                )
                WindowLocationTracker.onWindowCreated(this)

                init(this)

                window = this
            }
        },
        dispose = {
            WindowLocationTracker.onWindowDisposed(it)
            // We need to remove them because AWT can still call them after dispose()
            listeners.removeFromAndClear(it)
            it.dispose()
        },
        update = { window ->
            if (!window.isDisplayable) {
                window.initializePlacement(currentState)
                window.initializeBounds(currentState)

                // Need to make the window displayable, to make awt.SwingWindow render the first
                // frame before the window is visible.
                // Check window.isDisplayable again because initializeBounds could have already
                // called pack(), and we don't need to do it twice
                if (!window.isDisplayable) {
                    window.preferredSize = window.size
                    window.pack()  // Sizes to preferred size
                }
            }

            updater.update {
                set(currentTitle, window::setTitle)
                set(currentIcon, window::setIcon)
                set(currentDecoration is UndecoratedWindowDecoration, window::setUndecoratedSafely)
                set(currentTransparent, window::isTransparent::set)
                set(currentResizable, window::setResizable)
                set(currentEnabled, window::setEnabled)
                set(currentFocusable, window::setFocusableWindowState)
                set(currentAlwaysOnTop, window::setAlwaysOnTop)
                set(currentSizeLimits.min) { window.minimumSize = it.roundToDimensionOrNull() }
                set(currentSizeLimits.max) { window.maximumSize = it.roundToDimensionOrNull() }
                set(currentDecoration.resizerThickness, window::undecoratedResizerThickness::set)
            }
        },
        content = content
    )

    LaunchedEffect(window, state) {
        val window = window ?: return@LaunchedEffect
        launch {
            while (isActive) {
                window.placement = state.placementRequests.receive()
            }
        }
        launch {
            while (isActive) {
                window.isMinimized = state.isMinimizedRequests.receive()
            }
        }
        launch {
            while (isActive) {
                window.setBoundsFrom(state.boundsRequests.receive())
            }
        }
    }
}

private fun WindowScreenProvider.getInitialScreenDevice(): GraphicsDevice {
    val lastActiveConfig = WindowLocationTracker.lastActiveGraphicsConfiguration
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val devices = env.screenDevices
    val defaultDevice =
        devices.firstOrNull { it.iDstring === lastActiveConfig?.device?.iDstring } ?:
        env.defaultScreenDevice
    return with(WindowScreenProviderScope(devices.toList(), defaultDevice)) {
        getScreen().device
    }
}

private fun ComposeWindow.initializePlacement(state: WindowState) {
    val placementRequest = state.placementRequests.tryReceive().getOrNull()
    val currentPlacement = state._placement

    placement = placementRequest ?: currentPlacement ?: WindowPlacement.Floating
}

private fun ComposeWindow.initializeBounds(state: WindowState) {
    val boundsRequest = state.boundsRequests.tryReceive().getOrNull()
    val currentBounds = state._bounds

    // Prioritize requests, then currentBounds
    if ((boundsRequest == null) && (currentBounds != null)) {
        bounds = currentBounds.toAwtRectangleRounded()
    } else {
        setBoundsFrom(boundsRequest ?: WindowBoundsProvider.Default)
    }
}

private fun ComposeWindow.setBoundsFrom(boundsProvider: WindowBoundsProvider) {
    fun ensureIsDisplayable() {
        if (!isDisplayable) {
            pack()
        }
    }

    val scope = WindowGeometryProviderScope(
        screen = screen(),
        windowInsets = {
            ensureIsDisplayable()
            insets.toDpInsets()
        },
        measuringContentWithConstraints = { constraints, block ->
            ensureIsDisplayable()
            measuringContentWithConstraints(constraints) {
                with(density) {
                    block(it)
                }
            }
        }
    )
    with(scope) {
        bounds = boundsProvider.getBounds().requireReal().toAwtRectangleRounded()
    }
}

private fun Window.screen() = Screen(graphicsConfiguration.device)
