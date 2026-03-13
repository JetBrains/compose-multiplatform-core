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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.awt.toAwtRectangleRounded
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.util.ComponentUpdater
import androidx.compose.ui.util.componentListenerRef
import androidx.compose.ui.util.setBoundsSafely
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
import androidx.compose.ui.window.roundToDimension
import androidx.compose.ui.window.resizerThickness
import androidx.compose.ui.window.toDpSize
import androidx.compose.ui.window.v2.Screen
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowGeometryProviderScope
import androidx.compose.ui.window.v2.WindowScreenProvider
import androidx.compose.ui.window.v2.WindowScreenProviderScope
import androidx.compose.ui.window.v2.WindowState
import androidx.compose.ui.window.v2.rememberWindowState
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame


/**
 * Similar to the corresponding [Window] function, but additionally allows configuring the
 * underlying AWT window before it has been made displayable by providing an [init] block.
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
    initialScreenProvider: WindowScreenProvider = WindowScreenProvider.Default,
    initialBoundsProvider: WindowBoundsProvider = WindowBoundsProvider.Default,
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    decoration: WindowDecoration = WindowDecoration.SystemDefault,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    onBoundsChanged: (DpRect) -> Unit = { },
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
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentOnBoundsChanged by rememberUpdatedState(onBoundsChanged)

    val updater = remember(::ComponentUpdater)

    // the state applied to the window. exist to avoid races between WindowState changes and the state stored inside the native window
    val appliedState = remember {
        object {
            var bounds: DpRect? = null
            var placement: WindowPlacement? = null
            var isMinimized: Boolean? = null
        }
    }

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

    SwingWindow(
        visible = visible,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        create = {
            val initialScreen = currentState.screen ?: initialScreenProvider.getInitialScreen()

            ComposeWindow(
                graphicsConfiguration = initialScreen.device.defaultConfiguration,
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
                    currentState.placement = placement
                    currentState.isMinimized = isMinimized
                    appliedState.placement = currentState.placement
                    appliedState.isMinimized = currentState.isMinimized
                }
                listeners.componentListenerRef.registerWithAndSet(
                    this,
                    object : ComponentAdapter() {
                        fun applyStateChanges() {
                            val bounds = DpRect(x.dp, y.dp, (x + width).dp, (y + height).dp)
                            currentState.setBoundsDirect(bounds)
                            appliedState.bounds = bounds
                            currentOnBoundsChanged(bounds)
                            if (currentState.screen?.device != graphicsConfiguration.device) {
                                currentState.screen = Screen(graphicsConfiguration.device)
                            }
                        }

                        override fun componentShown(e: ComponentEvent?) {
                            applyStateChanges()
                        }

                        override fun componentResized(e: ComponentEvent) {
                            // we check placement here and in windowStateChanged,
                            // because fullscreen changing doesn't
                            // fire windowStateChanged, only componentResized
                            currentState.placement = placement
                            appliedState.placement = currentState.placement
                            applyStateChanges()
                        }

                        override fun componentMoved(e: ComponentEvent) {
                            applyStateChanges()
                        }
                    }
                )
                WindowLocationTracker.onWindowCreated(this)

                init(this)
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
                window.initializeBounds(currentState.bounds, initialBoundsProvider)
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
                set(currentDecoration.resizerThickness, window::undecoratedResizerThickness::set)
            }
            if (currentState.placement != appliedState.placement) {
                window.placement = currentState.placement
                appliedState.placement = currentState.placement
            }
            if (currentState.isMinimized != appliedState.isMinimized) {
                window.isMinimized = currentState.isMinimized
                appliedState.isMinimized = currentState.isMinimized
            }
            currentState.bounds?.let { stateBounds ->
                if (stateBounds != appliedState.bounds) {
                    window.setBoundsSafely(stateBounds, currentState.placement)
                    appliedState.bounds = stateBounds
                }
            }
        },
        content = content
    )
}

private fun WindowScreenProvider.getInitialScreen(): Screen {
    val lastActiveConfig = WindowLocationTracker.lastActiveGraphicsConfiguration
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val allScreens = env.screenDevices.map(::Screen)
    val defaultScreen =
        allScreens.firstOrNull { it.device === lastActiveConfig?.device } ?:
        allScreens.firstOrNull { it.device == env.defaultScreenDevice } ?:
        Screen(env.defaultScreenDevice)
    return with(WindowScreenProviderScope(defaultScreen, allScreens)) {
        getScreen()
    }
}

private fun Window.initializeBounds(
    currentBounds: DpRect?,
    initialBoundsProvider: WindowBoundsProvider
) {
    if (currentBounds != null) {
        val boundsRect = currentBounds.toAwtRectangleRounded()
        preferredSize = boundsRect.size
        pack()
    } else {
        val screen = Screen(graphicsConfiguration.device)
        preferredSize = screen.availableBounds.size.roundToDimension()
        pack()
        preferredSize = null
        val intrinsicSize = preferredSize.toDpSize()

        val scope = WindowGeometryProviderScope(
            screen = screen,
            intrinsicWindowSize = intrinsicSize
        )
        bounds = with(scope) {
            initialBoundsProvider.getBounds().toAwtRectangleRounded()
        }
    }
}
