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
import androidx.compose.runtime.ComposableOpenTarget
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.v2.SwingWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.SingleWindowApplicationScope
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowLocationTracker
import androidx.compose.ui.window.application
import androidx.compose.ui.window.roundToDimension
import androidx.compose.ui.window.roundToIntSize
import androidx.compose.ui.window.toDpOffset
import androidx.compose.ui.window.toDpRect
import java.awt.GraphicsDevice
import java.awt.Insets
import java.awt.Toolkit

@ExperimentalComposeUiApi
@Composable
@ComposableOpenTarget(-1)
fun Window(
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
    onBoundsChanged: (IntRect) -> Unit = { },
    content: @Composable FrameWindowScope.() -> Unit
) {
    SwingWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        initialScreenProvider = initialScreenProvider,
        initialBoundsProvider = initialBoundsProvider,
        visible = visible,
        title = title,
        icon = icon,
        decoration = decoration,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTop,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        onBoundsChanged = onBoundsChanged,
        init = { },
        content = content,
    )
}

@ExperimentalComposeUiApi
fun singleWindowApplication(
    state: WindowState = WindowState(),
    initialBounds: WindowBoundsProvider = WindowBoundsProvider.Default,
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
    onBoundsChanged: (IntRect) -> Unit = { },
    exitProcessOnExit: Boolean = true,
    content: @Composable SingleWindowApplicationScope.() -> Unit
) = application(exitProcessOnExit = exitProcessOnExit) {
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        initialBoundsProvider = initialBounds,
        visible = visible,
        title = title,
        icon = icon,
        decoration = decoration,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTop,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        onBoundsChanged = onBoundsChanged,
        content = {
            with(SingleWindowApplicationScope(this@application, this@Window)) {
                content()
            }
        }
    )
}

class DpInsets(
    val top: Dp,
    val left: Dp,
    val bottom: Dp,
    val right: Dp
)

private fun Insets.toDpInsets() = DpInsets(
    top = top.dp,
    left = left.dp,
    bottom = bottom.dp,
    right = right.dp
)

class Screen internal constructor(
    internal val device: GraphicsDevice
) {
    private val configuration
        get() = device.defaultConfiguration

    val bounds: DpRect
        get() = configuration.bounds.toDpRect()

    val insets: DpInsets
        get() = Toolkit.getDefaultToolkit().getScreenInsets(configuration).toDpInsets()

    val availableBounds: DpRect
        get() = run {
            val insets = insets
            DpRect(
                left = bounds.left + insets.left,
                top = bounds.top + insets.top,
                right = bounds.right - insets.right,
                bottom = bounds.bottom - insets.bottom
            )
        }

    override fun toString(): String = device.iDstring
}

class WindowGeometryProviderScope internal constructor(
    val screen: Screen,
    val intrinsicWindowSize: DpSize,
) {
    internal fun WindowSizeProvider.getSize(): DpSize = with(this) {
        this@WindowGeometryProviderScope.getSize()
    }

    internal fun WindowPositionProvider.getPosition(): DpOffset = with(this) {
        this@WindowGeometryProviderScope.getPosition()
    }

    internal fun WindowBoundsProvider.getBounds(): DpRect = with(this) {
        this@WindowGeometryProviderScope.getBounds()
    }
}

fun interface WindowBoundsProvider {
    fun WindowGeometryProviderScope.getBounds(): DpRect

    companion object {
        val Default = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Default,
            positionProvider = WindowPositionProvider.Default(WindowSizeProvider.Default)
        )

        fun AlignedToScreen(
            alignment: Alignment,
            sizeProvider: WindowSizeProvider = WindowSizeProvider.Default
        ): WindowBoundsProvider = WindowBoundsProvider {
            val size = sizeProvider.getSize()
            val availableBounds = screen.availableBounds

            val offsetInAvailable = alignment.align(
                size = size.roundToIntSize(),
                space = availableBounds.size.roundToIntSize(),
                layoutDirection = LayoutDirection.Ltr
            )
            DpRect(
                left = availableBounds.left + offsetInAvailable.x.dp,
                top = availableBounds.top + offsetInAvailable.y.dp,
                right = availableBounds.left + offsetInAvailable.x.dp + size.width,
                bottom = availableBounds.top + offsetInAvailable.y.dp + size.height
            )
        }
    }
}

/**
 * Combines a [WindowSizeProvider] and [WindowPositionProvider] into a [WindowBoundsProvider].
 */
fun WindowBoundsProvider(
    sizeProvider: WindowSizeProvider = WindowSizeProvider.Default,
    positionProvider: WindowPositionProvider = WindowPositionProvider.Default(sizeProvider),
    // Disables trailing lambda syntax; without it, `WindowBoundsProvider { ... }` is ambiguous.
    @Suppress("unused") unused: Unit = Unit
): WindowBoundsProvider = WindowBoundsProvider {
    val size = sizeProvider.getSize()
    val position = positionProvider.getPosition()
    DpRect(position, size)
}

fun interface WindowPositionProvider {
    fun WindowGeometryProviderScope.getPosition(): DpOffset
    companion object {
        fun Default(
            sizeProvider: WindowSizeProvider = WindowSizeProvider.Default,
        ) = WindowPositionProvider {
            val size = sizeProvider.getSize()
            WindowLocationTracker.getCascadeLocationFor(
                graphicsDevice = screen.device,
                windowSize = size.roundToDimension()
            ).toDpOffset()
        }

        fun Absolute(position: DpOffset) = WindowPositionProvider { position }
    }
}

fun interface WindowSizeProvider {
    fun WindowGeometryProviderScope.getSize(): DpSize
    companion object {
        val Default = WindowSizeProvider { DpSize(800.dp, 600.dp) }

        fun Exact(size: DpSize) = WindowSizeProvider { size }

        fun Exact(width: Dp, height: Dp) = Exact(DpSize(width, height))

        val Intrinsic = WindowSizeProvider { intrinsicWindowSize }

        fun IntrinsicWidth(height: Dp) = WindowSizeProvider {
            DpSize(intrinsicWindowSize.width, height)
        }

        fun IntrinsicHeight(width: Dp) = WindowSizeProvider {
            DpSize(width, intrinsicWindowSize.height)
        }
    }
}

class WindowScreenProviderScope(
    val defaultScreen: Screen,
    val screens: List<Screen>,
)

fun interface WindowScreenProvider {
    fun WindowScreenProviderScope.getScreen(): Screen
    companion object {
        val Default = WindowScreenProvider { defaultScreen }
    }
}
