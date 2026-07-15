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

package androidx.compose.ui.desktop

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import kotlinx.coroutines.awaitCancellation
import kotlinx.io.files.Path

interface WindowScope {
    val application: Application
        get() = Application.current
    val window: Window
}

enum class WindowCloseRequestReason {
    UserRequest,
    ApplicationQuit,
}

interface Window {
    val id: LightweightWindowId

    var title: String
        @MainThread set

    val size: DpSize
    val contentSize: DpSize
    fun requestSize(size: DpSize)

    val minSize: DpSize
    val maxSize: DpSize
    fun requestMinSize(minSize: DpSize)
    fun requestMaxSize(maxSize: DpSize)

    val isUserResizable: Boolean
    fun requestUserResizable(userResizable: Boolean)

    val screen: Screen
    val density: Density

    //    val hasActiveAppearance: Boolean
    val isFocused: Boolean

    @MainThread
    fun requestFocus()

    @MainThread
    fun requestBringToFront()

    @MainThread
    fun requestFocusAndBringToFront()

    @ExperimentalComposeUiApi
    val decoration: WindowDecoration

    @MainThread
    @ExperimentalComposeUiApi
    fun requestDecoration(vararg decorations: WindowDecoration)

    val customTitleBarInsets: Pair<Dp, Dp>?

    @ExperimentalComposeUiApi
    val customTitleBarLayout: Pair<List<WindowDecoration.TitleBarElement>, List<WindowDecoration.TitleBarElement>>?
        get() = null

    val systemTheme: SystemTheme

    @MainThread
    fun requestSystemTheme(systemTheme: SystemTheme?)

    @MainThread
    fun requestMinimized(minimized: Boolean)
    val placement: WindowPlacement

    @MainThread
    fun requestPlacement(placement: WindowPlacement)

    @MainThread
    fun showOpenSingleDialog(
        title: String = "Open",
        prompt: String = "Open",
        message: String? = null,
        nameFieldStringValue: String? = null,
        directoryPath: Path? = null,
        canCreateDirectories: Boolean = true,
        canSelectHiddenExtensions: Boolean = false,
        showsHiddenFiles: Boolean = false,
        isExtensionHidden: Boolean = true,
        canChooseFiles: Boolean = true,
        canChooseDirectories: Boolean = true,
        resolvesAliases: Boolean = true,
    ): Path?

    @MainThread
    fun showOpenMultipleDialog(
        title: String = "Open",
        prompt: String = "Open",
        message: String? = null,
        nameFieldStringValue: String? = null,
        directoryPath: Path? = null,
        canCreateDirectories: Boolean = true,
        canSelectHiddenExtensions: Boolean = false,
        showsHiddenFiles: Boolean = false,
        isExtensionHidden: Boolean = true,
        canChooseFiles: Boolean = true,
        canChooseDirectories: Boolean = true,
        resolvesAliases: Boolean = true,
    ): List<Path>

    @MainThread
    fun showSaveDialog(
        title: String = "Save",
        prompt: String = "Save",
        message: String? = null,
        nameFieldLabel: String = "Save As:",
        nameFieldStringValue: String? = null,
        directoryPath: Path? = null,
        canCreateDirectories: Boolean = true,
        canSelectHiddenExtensions: Boolean = false,
        showsHiddenFiles: Boolean = false,
        isExtensionHidden: Boolean = true,
    ): Path?

    @MainThread
    fun captureScreenshot(): ImageBitmap

    //    fun showWindowMenu(position: DpOffset)

    val nativeWindow: Any

    @MainThread
    fun requestClose(reason: WindowCloseRequestReason = WindowCloseRequestReason.UserRequest) {
        dispose()
    }

    @MainThread
    fun requestTitleBarDoubleClickAction(pointerEvent: PointerEvent) {
        when (placement) {
            WindowPlacement.Floating, WindowPlacement.Fullscreen -> {
                requestPlacement(WindowPlacement.Maximized)
            }
            WindowPlacement.Maximized -> requestPlacement(WindowPlacement.Floating)
        }
    }

    @MainThread
    fun requestTitleBarTertiaryClickAction(pointerEvent: PointerEvent) {}

    @MainThread
    fun requestTitleBarSecondaryClickAction(pointerEvent: PointerEvent) {}

    @MainThread
    fun dispose()

    // todo[ps] this functions actually shouldn't be a part of the api
    @MainThread
    fun setContent(
        onPreviewKeyEvent: (KeyEvent) -> Boolean,
        onKeyEvent: (KeyEvent) -> Boolean,
        content: @Composable WindowScope.() -> Unit,
    )

    @Composable
    fun Content(onLayout: (LightweightWindowId) -> Unit)
}

interface PositionAwareWindow : Window {
    val position: DpOffset
    fun requestPosition(position: DpOffset)

    val bounds: DpRect
    fun requestBounds(bounds: DpRect)
}

interface InteractiveMoveInitiator : Window {
    /**
     * Expected to be called within a pointer event handler so that the operating system will take
     * over and perform its native move operation for the window
     */
    @MainThread
    fun startInteractiveMove(pointerEvent: PointerEvent)
}

interface InteractiveResizeInitiator : Window {
    /**
     * Expected to be called within a pointer event handler so that the operating system will take
     * over and perform its native resize operation for the window and the given handle
     */
    @MainThread
    fun startInteractiveResize(handle: WindowResizeHandle, pointerEvent: PointerEvent)
}

enum class WindowResizeHandle {
    LeftBorder,
    TopBorder,
    RightBorder,
    BottomBorder,

    TopLeftCorner,
    TopRightCorner,
    BottomRightCorner,
    BottomLeftCorner,
}

interface IconDecoratedWindow : Window {
    @MainThread
    fun setIcon(icon: ByteArray)
}

enum class CaptionButtonKind { Minimize, Maximize, Close }

@ExperimentalComposeUiApi
interface CaptionButtonsHostWindow : Window {
    /**
     * Reports where a caption button is laid out, in logical client coordinates, so the platform
     * can route native non-client hit-testing (Snap Layouts, system minimize/maximize/close) to it.
     * Pass `null` to clear (e.g. the button is disabled or left the composition).
     */
    @MainThread
    fun setCaptionButtonBounds(kind: CaptionButtonKind, bounds: DpRect?)
}

@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Window(
    onCloseRequested: (WindowCloseRequestReason) -> Unit,
    configure: Window.() -> Unit = {},
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    onLayout: (LightweightWindowId) -> Unit = {},
    content: @Composable WindowScope.() -> Unit,
) {
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequested)
    val application = Application.current
    val session = LocalApplicationSession.current
    val window = remember {
        object : RememberObserver {
            val window = application.createWindow(
                session,
                { reason -> currentOnCloseRequest(reason) },
            )

            override fun onRemembered() {}

            override fun onAbandoned() {
                window.dispose()
            }

            override fun onForgotten() {
                window.dispose()
            }
        }
    }.window

    SideEffect { window.configure() }

    val parentLocals by rememberUpdatedState(currentCompositionLocalContext)

    // Keep the composition — and therefore the host Recomposer — alive while the window is live.
    // setContent must also happen inside a LaunchedEffect so that the suspending child coroutine
    // can await cancellation until the window is removed from the composition.
    LaunchedEffect(window) {
        window.setContent(onPreviewKeyEvent, onKeyEvent) {
            CompositionLocalProvider(parentLocals) {
                content()
            }
        }
        awaitCancellation()
    }

    window.Content(onLayout)
}

@Composable
fun Window(
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    create: () -> Window,
    dispose: (Window) -> Unit,
    update: (Window) -> Unit,
//    onLayout: (WindowData) -> Unit,
    onLayout: (LightweightWindowId) -> Unit,
    content: @Composable WindowScope.() -> Unit,
) {
    val window = remember { create() }
    val parentLocals by rememberUpdatedState(currentCompositionLocalContext)
    SideEffect {
        window.setContent(onPreviewKeyEvent, onKeyEvent) {
            CompositionLocalProvider(parentLocals) {
                content()
            }
        }
    }
    DisposableEffect(window) {
        onDispose {
            dispose(window)
        }
    }
    SideEffect {
        update(window)
    }
    window.Content(onLayout)
}

internal val DefaultCustomTitleBarHeightForAir = 44.dp
