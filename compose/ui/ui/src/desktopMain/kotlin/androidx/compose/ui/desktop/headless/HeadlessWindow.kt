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

@file:OptIn(
    ExperimentalComposeUiApi::class,
    InternalComposeUiApi::class,
    InternalCoreApi::class,
    ExperimentalAtomicApi::class,
)

package androidx.compose.ui.desktop.headless

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.LocalTextInputSessionOwner
import androidx.compose.ui.desktop.Screen
import androidx.compose.ui.desktop.TextInputSessionOwner
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.WindowScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.withFrameTransaction
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlinx.coroutines.awaitCancellation
import kotlinx.io.files.Path
import noria.ui.core.LocalWindow
import noria.ui.core.TestDataMode
import noria.ui.core.UIRoot
import noria.ui.core.WindowData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.skia.Color
import org.jetbrains.skia.Surface

private val nextWindowId = AtomicLong(0)

/**
 * A windowless [Window] that hosts a real [CanvasLayersComposeScene] and renders it into an
 * in-memory raster surface. Fleet reads its (all `mutableStateOf`-backed) properties per frame,
 * drives it via [handleEvent]/[render], and reads back frames via [captureScreenshot].
 *
 * Modeled on `desktop/macos/MacOsWindow.kt`, minus the native window, display link, IME and app
 * menu: there is no OS surface, so rendering targets a Skia raster [Surface] instead.
 */
class HeadlessWindow internal constructor(
    private val application: HeadlessApplication,
    devicePixelRatio: Float,
    internal var session: ApplicationSession,
    internal var onCloseRequest: (WindowCloseRequestReason) -> Unit,
) : Window, TextInputSessionOwner {

    override val id: LightweightWindowId = LightweightWindowId(nextWindowId.incrementAndFetch())

    // Read by the loop-side content catch-up task (scheduleContentCatchUpFrameIfIsolated) while
    // resetState() can dispose windows on the calling thread; @Volatile closes the staleness window
    // so the catch-up task never renders into an already-disposed scene.
    @Volatile
    private var isDisposed = false

    private var isFrameRequestedState = mutableStateOf(false)
    internal val isFrameRequested: Boolean get() = isFrameRequestedState.value

    override var title: String = "Headless Window"

    private var sizeState: DpSize by mutableStateOf(DpSize(800.dp, 600.dp))
    override val size: DpSize get() = sizeState
    override val contentSize: DpSize get() = sizeState

    override fun requestSize(size: DpSize) {
        sizeState = size
    }

    private var minSizeState: DpSize by mutableStateOf(DpSize.Zero)
    override val minSize: DpSize get() = minSizeState
    override fun requestMinSize(minSize: DpSize) {
        minSizeState = minSize
    }

    private var maxSizeState: DpSize by mutableStateOf(DpSize(Float.MAX_VALUE.dp, Float.MAX_VALUE.dp))
    override val maxSize: DpSize get() = maxSizeState
    override fun requestMaxSize(maxSize: DpSize) {
        maxSizeState = maxSize
    }

    private var isUserResizableState: Boolean by mutableStateOf(true)
    override val isUserResizable: Boolean get() = isUserResizableState
    override fun requestUserResizable(userResizable: Boolean) {
        isUserResizableState = userResizable
    }

    override val screen: Screen = HeadlessScreen(devicePixelRatio = devicePixelRatio)
    override val density: Density = screen.density

    private var isFocusedState: Boolean by mutableStateOf(true)
    override val isFocused: Boolean get() = isFocusedState

    override fun requestFocus() {
        isFocusedState = true
    }

    override fun requestBringToFront() {
        // No-op for headless
    }

    override fun requestFocusAndBringToFront() {
        isFocusedState = true
    }

    override var decoration: WindowDecoration by mutableStateOf(WindowDecoration.Decorated)
        private set

    override fun requestDecoration(vararg decorations: WindowDecoration) {
        decorations.firstOrNull()?.let { decoration = it }
    }

    override val customTitleBarInsets: Pair<Dp, Dp>? = null

    override val systemTheme: SystemTheme get() = SystemTheme.Light

    override fun requestSystemTheme(systemTheme: SystemTheme?) {
        // No-op for headless
    }

    override fun requestMinimized(minimized: Boolean) {
        // No-op for headless
    }

    private var placementState: WindowPlacement by mutableStateOf(WindowPlacement.Floating)
    override val placement: WindowPlacement get() = placementState
    override fun requestPlacement(placement: WindowPlacement) {
        placementState = placement
    }

    private val viewConfiguration: ViewConfiguration = object : ViewConfiguration {
        override val longPressTimeoutMillis: Long = 500
        override val doubleTapTimeoutMillis: Long get() = 300
        override val doubleTapMinTimeMillis: Long = 40
        override val touchSlop: Float get() = density.run { 18.dp.toPx() }
    }

    private val windowInfo: WindowInfo = object : WindowInfo {
        override val isWindowFocused: Boolean
            get() = isFocused

        override val keyboardModifiers: PointerKeyboardModifiers
            get() = inputStateTracker.keyboardModifiers

        @ExperimentalComposeUiApi
        override val containerSize: IntSize
            get() = contentSizeInPx()
    }

    private fun contentSizeInPx(): IntSize = density.run {
        IntSize(contentSize.width.roundToPx(), contentSize.height.roundToPx())
    }

    private val semanticsOwners = mutableStateSetOf<SemanticsOwner>()

    private val uiRoot = UIRoot { semanticsOwners }

    private val platformContext: PlatformContext = object : PlatformContext by PlatformContext.Empty(),
        PlatformContext.SemanticsOwnerListener {
        override val windowInfo: WindowInfo
            get() = this@HeadlessWindow.windowInfo
        override val viewConfiguration: ViewConfiguration
            get() = this@HeadlessWindow.viewConfiguration
        override val inputModeManager: InputModeManager
            get() = application.inputModeManager

        override fun textInputSessionOwner() = this@HeadlessWindow

        override val semanticsOwnerListener: PlatformContext.SemanticsOwnerListener?
            get() = if (TestDataMode.isEnabled) this else null

        override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
            semanticsOwners.add(semanticsOwner)
        }

        override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
            semanticsOwners.remove(semanticsOwner)
        }

        override fun onSemanticsChange(semanticsOwner: SemanticsOwner) = Unit

        override fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsNodeId: Int) = Unit
    }

    private val composeScene: ComposeScene = CanvasLayersComposeScene(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        size = contentSizeInPx(),
        coroutineContext = session.coroutineScope.coroutineContext +
            HeadlessMainDispatcher(application.eventLoop), // NON-immediate, like every backend
        platformContext = platformContext,
        dataSourceContext = session.dataSourceContext,
        invalidate = { isFrameRequestedState.value = true },
    )

    // ----- Rendering into an in-memory raster surface -----

    private var surface: Surface? = null

    /** Returns a raster surface matching the current content size, recreating it on size changes. */
    private fun surfaceForCurrentSize(): Surface {
        val target = contentSizeInPx()
        val width = maxOf(1, target.width)
        val height = maxOf(1, target.height)
        val existing = surface
        if (existing == null || existing.width != width || existing.height != height) {
            existing?.close()
            if (composeScene.size != target) {
                composeScene.size = target
            }
            surface = Surface.makeRasterN32Premul(width, height)
        }
        return surface!!
    }

    /** Renders one scene frame into the raster surface. */
    fun render(nanoTime: Long = System.nanoTime()) {
        isFrameRequestedState.value = false
        val target = surfaceForCurrentSize()
        target.canvas.clear(Color.TRANSPARENT)
        composeScene.render(target.canvas.asComposeCanvas(), nanoTime)
    }

    override fun captureScreenshot(): ImageBitmap {
        if (surface == null) {
            render()
        }
        return surface!!.makeImageSnapshot().toComposeImageBitmap()
    }

    override suspend fun showOpenSingleDialog(
        title: String,
        prompt: String,
        message: String?,
        nameFieldStringValue: String?,
        directoryPath: Path?,
        canCreateDirectories: Boolean,
        canSelectHiddenExtensions: Boolean,
        showsHiddenFiles: Boolean,
        isExtensionHidden: Boolean,
        canChooseFiles: Boolean,
        canChooseDirectories: Boolean,
        resolvesAliases: Boolean,
    ): Path? = null

    override suspend fun showOpenMultipleDialog(
        title: String,
        prompt: String,
        message: String?,
        nameFieldStringValue: String?,
        directoryPath: Path?,
        canCreateDirectories: Boolean,
        canSelectHiddenExtensions: Boolean,
        showsHiddenFiles: Boolean,
        isExtensionHidden: Boolean,
        canChooseFiles: Boolean,
        canChooseDirectories: Boolean,
        resolvesAliases: Boolean,
    ): List<Path> = emptyList()

    override suspend fun showSaveDialog(
        title: String,
        prompt: String,
        message: String?,
        nameFieldLabel: String,
        nameFieldStringValue: String?,
        directoryPath: Path?,
        canCreateDirectories: Boolean,
        canSelectHiddenExtensions: Boolean,
        showsHiddenFiles: Boolean,
        isExtensionHidden: Boolean,
    ): Path? = null

    override val nativeWindow: Any = Unit

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        composeScene.close()
        surface?.close()
        surface = null
        application.removeWindow(id)
    }

    override fun requestClose(reason: WindowCloseRequestReason) {
        composeScene.withFrameTransaction { onCloseRequest(reason) }
    }

    // ----- Scene content -----

    private var onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }
    private var onKeyEvent: (KeyEvent) -> Boolean = { false }

    private var contentState = mutableStateOf<(@Composable WindowScope.() -> Unit)?>(null)
    private var sceneContentInstalled = false

    private fun installSceneContentIfNeeded() {
        if (sceneContentInstalled) return
        sceneContentInstalled = true
        val windowScope = object : WindowScope {
            override val window: Window get() = this@HeadlessWindow
        }
        composeScene.setContent {
            CompositionLocalProvider(
                LocalSystemTheme provides systemTheme,
                LocalWindow provides this,
                LocalTextInputSessionOwner provides this@HeadlessWindow,
            ) {
                contentState.value?.invoke(windowScope)
            }
        }
    }

    override fun setContent(
        onPreviewKeyEvent: (KeyEvent) -> Boolean,
        onKeyEvent: (KeyEvent) -> Boolean,
        content: @Composable WindowScope.() -> Unit,
    ) {
        this.onPreviewKeyEvent = onPreviewKeyEvent
        this.onKeyEvent = onKeyEvent
        contentState.value = content
        installSceneContentIfNeeded()
        isFrameRequestedState.value = true
        scheduleContentCatchUpFrameIfIsolated()
    }

    /**
     * With frame isolation ON, the scene's standing pin is taken at window construction, so the
     * [contentState] write above (and every app-side state the content reads, e.g. the declarative
     * `Window`'s `parentLocals`) only becomes visible to the scene at its next pin rotation - and
     * scenes rotate exclusively in [ComposeScene.render]. Platform backends get that frame from
     * their display link; headless has none, so schedule ONE catch-up frame on the event loop (it
     * runs after the enclosing application-domain slice has published, i.e. after the writes are
     * world-visible). Flag off, the content already composed synchronously in
     * [installSceneContentIfNeeded] and no frame is scheduled, keeping the test-driven render
     * cadence byte-stock.
     */
    private fun scheduleContentCatchUpFrameIfIsolated() {
        if (composeScene.currentFrameSnapshot == null) return // frame isolation off
        application.eventLoop.dispatch {
            if (!isDisposed) render()
        }
    }

    @Composable
    @ApiStatus.Internal
    override fun Content(onLayout: (WindowData) -> Unit) {
        // ComposeScene drives its own composition; nothing to host here.
        onLayout(WindowData(id, uiRoot))
    }

    // ----- Input -----

    private val inputStateTracker = InputStateTracker(
        inputModeManager = application.inputModeManager,
        sendPointerEvent = { eventType, position, scrollDelta, timeMillis, type, buttons, modifiers, nativeEvent, button ->
            composeScene.sendPointerEvent(
                eventType = eventType,
                position = position,
                scrollDelta = scrollDelta,
                timeMillis = timeMillis,
                type = type,
                buttons = buttons,
                keyboardModifiers = modifiers,
                nativeEvent = nativeEvent,
                button = button,
            )
        },
        sendKeyEvent = { keyEvent ->
            composeScene.withFrameTransaction {
                onPreviewKeyEvent(keyEvent) ||
                    composeScene.sendKeyEvent(keyEvent) ||
                    onKeyEvent(keyEvent)
            }
        },
    )

    internal fun handleEvent(event: WindowEvent) {
        inputStateTracker.updateStateAndSendEvents(event as Event, density)
    }

    // ----- TextInputSessionOwner (headless windows have no IME) -----

    override suspend fun textInputSession(
        session: suspend PlatformTextInputSessionScope<*>.() -> Nothing,
    ): Nothing {
        // Headless windows don't support text input
        awaitCancellation()
    }

    override fun isTextInputSessionActive(): Boolean = false

    override fun handleEventWithInputSession(keyEvent: KeyEvent): Boolean = false

    init {
        // Registered here so that `inputStateTracker` is guaranteed to be initialized before
        // any event dispatched by the application event loop can reach `handleEvent`.
        application.windows += id to this
    }
}
