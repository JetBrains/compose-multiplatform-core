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
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.toIntOffset
import androidx.compose.ui.awt.v2.SwingWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.locationAlignedToScreen
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.SingleWindowApplicationScope
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowLocationTracker
import androidx.compose.ui.window.application

@ExperimentalComposeUiApi
@Composable
@ComposableOpenTarget(-1)
fun Window(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
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
    content: @Composable FrameWindowScope.() -> Unit
) {
    SwingWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        initialBounds = initialBounds,
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
        initialBounds = initialBounds,
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

interface WindowGeometryProviderScope {
    val window: ComposeWindow
}

fun interface WindowBoundsProvider {
    fun WindowGeometryProviderScope.getBounds(): IntRect

    companion object {
        val Default = WindowPositionProvider.PlatformDefault + WindowSizeProvider.Default
        fun AlignedToScreen(
            alignment: Alignment,
            sizeProvider: WindowSizeProvider
        ) = WindowBoundsProvider {
            val scope = this
            val size = with(sizeProvider) { scope.getSize() }
            val position = window.locationAlignedToScreen(size, alignment).toIntOffset()
            IntRect(position, size)
        }
    }
}

fun interface WindowPositionProvider {
    fun WindowGeometryProviderScope.getPosition(): IntOffset
    companion object {
        val PlatformDefault = WindowPositionProvider {
            WindowLocationTracker.getCascadeLocationFor(window).toIntOffset()
        }

        fun Absolute(position: IntOffset) = WindowPositionProvider { position }
    }
}

fun interface WindowSizeProvider {
    fun WindowGeometryProviderScope.getSize(): IntSize
    companion object {
        val Default = WindowSizeProvider { IntSize(800, 600) }

        fun Exact(size: IntSize) = WindowSizeProvider { size }

        private fun Preferred(width: Int?, height: Int?) = WindowSizeProvider {
            window.pack()
            IntSize(width ?: window.width, height ?: window.height)
        }
    }
}

operator fun WindowPositionProvider.plus(sizeProvider: WindowSizeProvider) =
    WindowBoundsProvider {
        val scope = this
        val position = scope.getPosition()
        val size = with(sizeProvider) { scope.getSize() }
        IntRect(
            left = position.x,
            top = position.y,
            right = position.x + size.width,
            bottom = position.y + size.height
        )
    }

