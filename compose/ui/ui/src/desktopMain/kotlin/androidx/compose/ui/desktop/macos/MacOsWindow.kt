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

@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class,
    ExperimentalAtomicApi::class
)

package androidx.compose.ui.desktop.macos

import androidx.annotation.MainThread
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.desktop.LocalTextInputSessionOwner
import androidx.compose.ui.desktop.draganddrop.DragAndDropImage
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.InternalKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.InteractiveMoveInitiator
import androidx.compose.ui.desktop.KdtDragAndDropManager
import androidx.compose.ui.desktop.KdtDragAndDropTransferable
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.PositionAwareWindow
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.WindowScope
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.platform.DefaultTextToolbar
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalTextInputContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformDragAndDropManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.text.input.TextInputContext
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.width
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.desktop.logging.logger
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import noria.CallbackInterceptor
import noria.ui.core.LocalWindow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.desktop.macos.AppMenuManager
import org.jetbrains.desktop.macos.Appearance
import org.jetbrains.desktop.macos.DisplayLink
import org.jetbrains.desktop.macos.DraggingItem
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.EventHandlerResult
import org.jetbrains.desktop.macos.FileDialog
import org.jetbrains.desktop.macos.GrandCentralDispatch
import org.jetbrains.desktop.macos.Image
import org.jetbrains.desktop.macos.LogicalPoint
import org.jetbrains.desktop.macos.LogicalRect
import org.jetbrains.desktop.macos.LogicalSize
import org.jetbrains.desktop.macos.MouseButton
import org.jetbrains.desktop.macos.Pasteboard
import org.jetbrains.desktop.macos.Screen
import org.jetbrains.desktop.macos.TextDirection
import org.jetbrains.desktop.macos.TextInputClient
import org.jetbrains.desktop.macos.TitlebarConfiguration
import org.jetbrains.desktop.macos.WindowEvent
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

class MacOsWindow internal constructor(
    private val application: MacOsApplication,
    internal val scene: Scene<*>,
    nativeWindow: org.jetbrains.desktop.macos.Window = org.jetbrains.desktop.macos.Window.create(),
    val viewContext: MetalViewContext = application.desktopGpuContext.createMetalViewContext(),
    private val onCloseRequest: (WindowCloseRequestReason) -> Unit,
) :
    PositionAwareWindow, InteractiveMoveInitiator {
    private var backingNativeWindow: org.jetbrains.desktop.macos.Window? = nativeWindow
    internal val nativeWindowId: org.jetbrains.desktop.macos.WindowId = nativeWindow.windowId()
    override val nativeWindow: org.jetbrains.desktop.macos.Window
        get() = checkNotNull(backingNativeWindow) {
            "Cannot access the native Window of a disposed MacOsWindow"
        }

    override val id: LightweightWindowId = nativeWindow.assignNewLightweightWindowId()

    @Volatile
    private var isDisposed = false

    @Volatile
    internal var isFrameRequested = false

    private val pictureRecorder = PictureRecorder()
    private var displayLink: DisplayLink? = null
    private val displayLinkFrameStartTimeMark: AtomicReference<TimeMarkWrapper?> =
        AtomicReference(null)

    // todo[unterhofer] Make reactive
    override var title: String
        get() = nativeWindow.title
        set(value) {
            if (!isDisposed) {
                nativeWindow.title = value
            }
        }

    override var position: DpOffset by mutableStateOf(nativeWindow.origin.toDpOffset())
        private set
    override var size: DpSize by mutableStateOf(nativeWindow.size.toDpSize())
        private set
    override var contentSize: DpSize by mutableStateOf(nativeWindow.contentSize.toDpSize())
        private set
    override val bounds: DpRect
        get() = DpRect(position, size)

    override fun requestSize(size: DpSize) {
        if (!isDisposed) {
            nativeWindow.setRect(
                nativeWindow.origin,
                LogicalSize(
                    size.width.takeOrElse { nativeWindow.size.width.dp }.value.toDouble(),
                    size.height.takeOrElse { nativeWindow.size.height.dp }.value.toDouble(),
                ),
                animateTransition = false,
            )
        }
    }

    private val minSizeState = mutableStateOf(nativeWindow.minSize.toDpSize())
    override val minSize: DpSize
        get() = nativeWindow.minSize.toDpSize().also { minSizeState.value = it }

    override fun requestMinSize(minSize: DpSize) {
        if (!isDisposed) {
            nativeWindow.minSize =
                LogicalSize(
                    minSize.width.takeOrElse { nativeWindow.minSize.width.dp }.value.toDouble(),
                    minSize.height.takeOrElse { nativeWindow.minSize.height.dp }.value.toDouble(),
                )
        }
        minSizeState.value = minSize
    }

    private val maxSizeState = mutableStateOf(nativeWindow.maxSize.toDpSize())
    override val maxSize: DpSize
        get() = nativeWindow.maxSize.toDpSize().also { maxSizeState.value = it }

    override fun requestMaxSize(maxSize: DpSize) {
        if (!isDisposed) {
            nativeWindow.maxSize =
                LogicalSize(
                    maxSize.width.takeOrElse { nativeWindow.size.width.dp }.value.toDouble(),
                    maxSize.height.takeOrElse { nativeWindow.size.height.dp }.value.toDouble(),
                )
        }
        maxSizeState.value = maxSize
    }

    // todo[unterhofer] Make reactive with events
    private val isUserResizableState = mutableStateOf(nativeWindow.isResizable)
    override var isUserResizable: Boolean
        get() = isUserResizableState.value
        @MainThread
        private set(value) {
            if (!isDisposed) {
                nativeWindow.isResizable = value
            }
            isUserResizableState.value = value
        }

    override fun requestUserResizable(userResizable: Boolean) {
        isUserResizable = userResizable
    }

    override fun requestPosition(position: DpOffset) {
        if (!isDisposed) {
            nativeWindow.setRect(
                LogicalPoint(
                    position.x.takeOrElse { nativeWindow.origin.x.dp }.value.toDouble(),
                    position.y.takeOrElse { nativeWindow.origin.y.dp }.value.toDouble(),
                ),
                nativeWindow.size,
                animateTransition = false,
            )
        }
    }

    override fun requestBounds(bounds: DpRect) {
        if (!isDisposed) {
            nativeWindow.setRect(
                LogicalPoint(
                    bounds.left.takeOrElse { nativeWindow.origin.x.dp }.value.toDouble(),
                    bounds.top.takeOrElse { nativeWindow.origin.y.dp }.value.toDouble(),
                ),
                LogicalSize(
                    bounds.width.takeOrElse { nativeWindow.size.width.dp }.value.toDouble(),
                    bounds.height.takeOrElse { nativeWindow.size.height.dp }.value.toDouble(),
                ),
                animateTransition = false,
            )
        }
    }

    override fun requestPlacement(placement: WindowPlacement) {
        if (!isDisposed) {
            val isFullscreen = nativeWindow.isFullScreen
            val isMaximized = nativeWindow.isMaximizedButNotInFullScreen()
            when (placement) {
                WindowPlacement.Floating if isMaximized -> nativeWindow.toggleMaximize()
                WindowPlacement.Floating if isFullscreen -> nativeWindow.toggleFullScreen()
                WindowPlacement.Fullscreen if !isFullscreen -> nativeWindow.toggleFullScreen()
                WindowPlacement.Maximized if !isMaximized -> nativeWindow.toggleMaximize()
                else -> {}
            }
        }
    }

    override fun requestClose(reason: WindowCloseRequestReason) {
        if (!isDisposed) {
            onCloseRequest(reason)
        }
    }

    override var isFocused: Boolean by mutableStateOf(nativeWindow.isKey)
        private set

    override fun requestFocus() {
        if (!isDisposed) {
            // todo[unterhofer] This should only call makeKey, but that's not currently mapped
            nativeWindow.makeKeyAndOrderFront()
        }
    }

    override fun requestBringToFront() {
        if (!isDisposed) {
            nativeWindow.orderFront()
        }
    }

    override fun requestFocusAndBringToFront() {
        if (!isDisposed) {
            nativeWindow.makeKeyAndOrderFront()
        }
    }

    @ExperimentalComposeUiApi
    override var decoration: WindowDecoration by mutableStateOf(WindowDecoration.Decorated)
        private set

    @ExperimentalComposeUiApi
    override fun requestDecoration(vararg decorations: WindowDecoration) {
        for (decoration in decorations) {
            if (decoration == this.decoration) break
            if (!decoration.isDecorated) continue // Not supported for now

            val titlebarConfiguration = when (decoration) {
                WindowDecoration.Decorated -> TitlebarConfiguration.Regular
                is WindowDecoration.CustomTitleBar ->
                    TitlebarConfiguration.Custom(decoration.height.value.toDouble())
                is WindowDecoration.Undecorated ->
                    throw UnsupportedOperationException(
                        "Undecorated windows are not supported on macOS",
                    )
            }
            if (!isDisposed) {
                nativeWindow.setTitlebarConfiguration(titlebarConfiguration)
            }
            this.decoration = decoration
            break
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override val customTitleBarInsets: Pair<Dp, Dp>?
        get() = when (val decoration = decoration) {
            is WindowDecoration.CustomTitleBar if placement != WindowPlacement.Fullscreen ->
                (40.dp + decoration.height) to 0.dp
            else -> null
        }

    private var systemThemeBackingField by mutableStateOf(
        nativeWindow.overriddenAppearance?.toSystemTheme(),
    )
    override val systemTheme: SystemTheme
        get() = systemThemeBackingField ?: application.systemTheme

    override fun requestSystemTheme(systemTheme: SystemTheme?) {
        if (!isDisposed) {
            nativeWindow.overriddenAppearance = when (systemTheme) {
                null -> null
                SystemTheme.Light -> Appearance.Light
                SystemTheme.Dark -> Appearance.Dark
                else -> throw IllegalArgumentException("Unsupported system theme: $systemTheme")
            }
        }
        systemThemeBackingField = systemTheme
    }

    private fun placement(): WindowPlacement = when {
        nativeWindow.isFullScreen -> WindowPlacement.Fullscreen
        nativeWindow.isMaximizedButNotInFullScreen() -> WindowPlacement.Maximized
        else -> WindowPlacement.Floating
    }

    override var placement: WindowPlacement by mutableStateOf(placement())
        private set

    override fun requestMinimized(minimized: Boolean) {
        if (!isDisposed) {
            if (minimized) {
                nativeWindow.miniaturize()
            } else {
                nativeWindow.deminiaturize()
            }
        }
    }

    private fun density() = Density(nativeWindow.scaleFactor().toFloat())
    override var density: Density by mutableStateOf(density())
        private set

    private var mouseOffset = DpOffset.Zero
    internal var macOsDragAndDropManager: MacOsDragAndDropManager? = null

    override var screen: MacOsScreen by mutableStateOf(
        MacOsScreen(Screen.allScreens().findById(nativeWindow.screenId())),
    )
        private set

    private fun layoutDirection() = when (nativeWindow.textDirection) {
        TextDirection.LeftToRight -> LayoutDirection.Ltr
        TextDirection.RightToLeft -> LayoutDirection.Rtl
    }

    var layoutDirection: LayoutDirection by mutableStateOf(layoutDirection())
        private set

    val viewConfiguration: ViewConfiguration = object :
        ViewConfiguration {
        override val longPressTimeoutMillis: Long = 500
        override val doubleTapTimeoutMillis: Long get() = 300
        override val doubleTapMinTimeMillis: Long = 40
        override val touchSlop: Float get() = density.run { 18.dp.toPx() }
    }

    val windowInfo: WindowInfo = object : WindowInfo {
        override val isWindowFocused: Boolean
            get() = isFocused

        @OptIn(InternalCoreApi::class)
        override val keyboardModifiers: PointerKeyboardModifiers
            get() = inputStateTracker.keyboardModifiers

        @ExperimentalComposeUiApi
        override val containerSize: IntSize
            get() = contentSizeInPx()
    }

    private fun contentSizeInPx(): IntSize = density.run {
        IntSize(contentSize.width.roundToPx(), contentSize.height.roundToPx())
    }

    // ----- Compose scene wiring -----

    private val positionCalculator = object {
        fun screenToLocal(positionOnScreen: Offset): Offset {
            return positionOnScreen - density().run {
                nativeWindow.origin.run {
                    Offset(
                        x.dp.toPx(),
                        y.dp.toPx(),
                    )
                }
            }
        }

        fun localToScreen(localPosition: Offset): Offset {
            return localPosition + density().run {
                nativeWindow.origin.run {
                    Offset(
                        x.dp.toPx(),
                        y.dp.toPx(),
                    )
                }
            }
        }
    }

    private val architectureComponentsOwner = DefaultArchitectureComponentsOwner().apply {
        enableSavedStateHandles()
        setLifecycleState(Lifecycle.State.RESUMED)
    }

    private val macOsTextInputSessionOwner =
        MacOsTextInputSessionOwner(nativeWindow, scene, density = { density })

    private val platformContext: PlatformContext = object : PlatformContext by PlatformContext.Empty() {
        override val windowInfo: WindowInfo
            get() = this@MacOsWindow.windowInfo
        override val viewConfiguration: ViewConfiguration
            get() = this@MacOsWindow.viewConfiguration
        override val inputModeManager: InputModeManager
            get() = application.inputModeManager
        override val architectureComponentsOwner = this@MacOsWindow.architectureComponentsOwner
        override val textToolbar = DefaultTextToolbar()
        override val dragAndDropManager: PlatformDragAndDropManager =
            KdtDragAndDropManager(this@MacOsWindow)

        override fun convertLocalToWindowPosition(localPosition: Offset): Offset =
            calculatePositionInWindow(localPosition)
        override fun convertWindowToLocalPosition(positionInWindow: Offset): Offset =
            calculateLocalPosition(positionInWindow)
        override fun convertLocalToScreenPosition(localPosition: Offset): Offset =
            positionCalculator.localToScreen(calculatePositionInWindow(localPosition))
        override fun convertScreenToLocalPosition(positionOnScreen: Offset): Offset =
            calculateLocalPosition(positionCalculator.screenToLocal(positionOnScreen))

        override fun textInputSessionOwner() = macOsTextInputSessionOwner
    }

    private val composeScene: ComposeScene = CanvasLayersComposeScene(
        density = density,
        layoutDirection = layoutDirection,
        size = contentSizeInPx(),
        coroutineContext = scene.coroutineScope.coroutineContext +
            MacOsKdtMainDispatcher.INSTANCE.immediate,
        platformContext = this@MacOsWindow.platformContext,
        invalidate = { isFrameRequested = true },
    )

    init {
        nativeWindow.registerForDraggedTypes(
            listOf(
                Pasteboard.FILE_URL_TYPE,
                UniformTypeIdentifiers.windowLocalDrag,
            ),
        )
        nativeWindow.attachView(viewContext.view)
        viewContext.onDisplayLayer = {
            repaintSynchronously()
        }
        macOsDragAndDropManager = MacOsDragAndDropManager(
            rootDragAndDropNode = { composeScene.rootDragAndDropNode },
            density = { density },
            callbackInterceptor = object : CallbackInterceptor {
                override fun <T> execute(f: () -> T): T {
                    return scene.withPreparedMainThread {
                        f()
                    }
                }

            },
        )
        if (nativeWindow.isVisible) {
            setupDisplayLink()
        }
        // Registration with the application event loop must happen after inputStateTracker is
        // initialized; see the init block at the bottom of the class.
    }

    override fun showOpenSingleDialog(
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
    ): Path? {
        return FileDialog.showOpenFileDialog(
            FileDialog.CommonDialogParams(
                title = title,
                prompt = prompt,
                message = message,
                nameFieldStringValue = nameFieldStringValue,
                directoryUrl = directoryPath.toString(),
                canCreateDirectories = canCreateDirectories,
                canSelectHiddenExtensions = canSelectHiddenExtensions,
                showsHiddenFiles = showsHiddenFiles,
                extensionsHidden = isExtensionHidden,
            ),
            FileDialog.OpenDialogParams(
                canChooseFiles = canChooseFiles,
                canChooseDirectories = canChooseDirectories,
                resolveAliases = resolvesAliases,
                allowsMultipleSelections = false,
            ),
        ).let { results ->
            when (results.size) {
                0 -> null
                1 -> results.single()
                else -> throw IllegalStateException(
                    "Open-single dialog returned ${results.size} results",
                )
            }
        }?.let { Path(it) }
    }

    override fun showOpenMultipleDialog(
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
    ): List<Path> {
        return FileDialog.showOpenFileDialog(
            FileDialog.CommonDialogParams(
                title = title,
                prompt = prompt,
                message = message,
                nameFieldStringValue = nameFieldStringValue,
                directoryUrl = directoryPath.toString(),
                canCreateDirectories = canCreateDirectories,
                canSelectHiddenExtensions = canSelectHiddenExtensions,
                showsHiddenFiles = showsHiddenFiles,
                extensionsHidden = isExtensionHidden,
            ),
            FileDialog.OpenDialogParams(
                canChooseFiles = canChooseFiles,
                canChooseDirectories = canChooseDirectories,
                resolveAliases = resolvesAliases,
                allowsMultipleSelections = true,
            ),
        ).map { Path(it) }
    }

    override fun showSaveDialog(
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
    ): Path? {
        return FileDialog.showSaveFileDialog(
            FileDialog.CommonDialogParams(
                title = title,
                prompt = prompt,
                message = message,
                nameFieldLabel = nameFieldLabel,
                nameFieldStringValue = nameFieldStringValue,
                directoryUrl = directoryPath.toString(),
                canCreateDirectories = canCreateDirectories,
                canSelectHiddenExtensions = canSelectHiddenExtensions,
                showsHiddenFiles = showsHiddenFiles,
                extensionsHidden = isExtensionHidden,
            ),
        )?.let { Path(it) }
    }

    fun startDragSession(
        offset: Offset,
        transferData: DragAndDropTransferData,
        decorationSize: Size,
        drawDragDecoration: DrawScope.() -> Unit,
    ) {
        val itemsEntry = (transferData.transferable as? KdtDragAndDropTransferable)
            ?.clipboardEntry
            ?.nativeClipEntry as? ClipboardItemsEntry
            ?: return
        val pasteboardItems = itemsEntry.items.toPasteboardItems()

        val image = DragAndDropImage(
            decorationSize,
            density(),
            layoutDirection(),
            drawDragDecoration,
        ).encodeToPngBytes()

        val imageOffset = mouseOffset.toLogicalPoint() -
            offset.toLogicalPoint(density) +
            transferData.dragDecorationOffset.toLogicalPoint(density)

        // we want to have only 1 image, even when we have multiple items,
        // so we pass an empty image for other than the first item
        val emptyImageSize = Size(1f, 1f)
        val emptyImage = DragAndDropImage(
            emptyImageSize, density, layoutDirection,
        ) {}.encodeToPngBytes()!!
        val emptyImageRect = LogicalRect(
            imageOffset,
            emptyImageSize.toLogicalSize(density),
        )

        val draggingItems = pasteboardItems.mapIndexed { index, item ->
            DraggingItem(
                item,
                if (index == 0)
                    LogicalRect(
                        imageOffset,
                        decorationSize.toLogicalSize(density),
                    ) else emptyImageRect,
                Image(if (index == 0 && image != null) image else emptyImage),
            )
        }

        activeDragAndDropTransferData = transferData
        nativeWindow.startDragSession(mouseOffset.toLogicalPoint(), draggingItems)
    }

    override fun captureScreenshot(): ImageBitmap {
        TODO()
    }

    override fun dispose() {
        if (!isDisposed) {
            isDisposed = true
            viewContext.onDisplayLayer = {}
            displayLink?.run {
                setRunning(false)
                close()
            }
            displayLink = null
            application.windows -= id
            composeScene.close()
            architectureComponentsOwner.setLifecycleState(Lifecycle.State.DESTROYED)
            if (id !in application.reusableNativeWindowResources) {
                nativeWindow.close()
                nativeWindowId.destroyLightweightWindowId()
                application.desktopGpuContext.destroyMetalViewContext(viewContext)
            }
            backingNativeWindow = null
            if (application.windows.isEmpty()) {
                GrandCentralDispatch.dispatchOnMain(highPriority = false) {
                    application.finishStructuredQuitIfNeeded()
                }
            }
        }
    }

    fun calculatePositionInWindow(localPosition: Offset): Offset {
        return contentPosition() + localPosition
    }

    fun calculateLocalPosition(positionInWindow: Offset): Offset {
        return positionInWindow - contentPosition()
    }

    /**
     * Relative to window
     */
    private fun contentPosition(): Offset {
        val d = density()
        val windowOrigin = nativeWindow.origin.toDpOffset()
        val contentOrigin = nativeWindow.contentOrigin.toDpOffset()
        val deltaX = contentOrigin.x - windowOrigin.x
        val deltaY = contentOrigin.y - windowOrigin.y
        return with(d) { Offset(deltaX.toPx(), deltaY.toPx()) }
    }

    @Composable
    @ApiStatus.Internal
    override fun Content(onLayout: (LightweightWindowId) -> Unit) {
        // ComposeScene drives its own composition; nothing to host here.
        onLayout(id)
    }

    private fun preparePicture(): PresentablePicture? {
        if (isDisposed) return null
        val size = viewContext.view.size()
        val bounds = Rect.makeWH(size.width.toFloat(), size.height.toFloat())
        val canvas = pictureRecorder.beginRecording(bounds)
        canvas.clear(org.jetbrains.skia.Color.TRANSPARENT)
        val now = System.nanoTime()
        composeScene.render(canvas.asComposeCanvas(), now)
        return PresentablePicture(pictureRecorder.finishRecordingAsPicture(), size)
    }

    private class TimeMarkWrapper(val timeMark: TimeSource.Monotonic.ValueTimeMark)

    private fun setupDisplayLink() {
        if (!nativeWindow.isVisible) {
            return
        }
        displayLink?.close()
        displayLink = null
        displayLink = DisplayLink.create(nativeWindow.screenId()) {
            val frameStartTimeMarkWrapper = TimeMarkWrapper(TimeSource.Monotonic.markNow())
            if (
                !isDisposed &&
                isFrameRequested &&
                displayLinkFrameStartTimeMark.compareAndSet(null, frameStartTimeMarkWrapper)
            ) {
                GrandCentralDispatch.dispatchOnMain(highPriority = true) {
                    isFrameRequested = false
                    try {
                        scene.withPreparedMainThread {
                            preparePicture()?.let { presentablePicture ->
                                try {
                                    viewContext.presentAsync(
                                        presentablePicture, waitForCATransaction = false,
                                        onComplete = {
                                            presentablePicture.close()
                                            val elapsedTime = displayLinkFrameStartTimeMark
                                                .exchange(null)!!
                                                .timeMark
                                                .elapsedNow()
                                            if (elapsedTime.inWholeMilliseconds > 10) {
//                                                logger.debug("Long frame: ${elapsedTime}")
                                            }
                                        },
                                    )
                                } catch (throwable: Throwable) {
                                    logger.error(throwable) { "Could not schedule frame presentation" }
                                    isFrameRequested = true
                                    displayLinkFrameStartTimeMark.compareAndSet(
                                        frameStartTimeMarkWrapper,
                                        null,
                                    )
                                    presentablePicture.close()
                                }
                            } ?: run {
                                isFrameRequested = true
                                displayLinkFrameStartTimeMark.compareAndSet(
                                    frameStartTimeMarkWrapper,
                                    null,
                                )
                            }
                        }
                    } catch (throwable: Throwable) {
                        logger.error(throwable) { "Could not prepare frame" }
                        isFrameRequested = true
                        displayLinkFrameStartTimeMark.compareAndSet(
                            frameStartTimeMarkWrapper,
                            null,
                        )
                    }
                }
            }
        }
        displayLink!!.setRunning(true)
    }

    private fun repaintSynchronously() {
        displayLink?.setRunning(false)
        val wasFrameRequestedPreviously = isFrameRequested
        isFrameRequested = false
        try {
            scene.withPreparedMainThread {
                preparePicture()?.use { picture ->
                    viewContext.presentSync(picture, waitForCATransaction = true)
                } ?: run {
                    logger.debug { "No picture was produced before display layer" }
                    isFrameRequested = true
                }
            }
        } catch (throwable: Throwable) {
            logger.error(throwable) { "Could not prepare frame synchronously" }
            if (!isFrameRequested && wasFrameRequestedPreviously) {
                isFrameRequested = true
            }
        } finally {
            displayLink?.setRunning(true)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
    private val inputStateTracker = InputStateTracker(
        inputModeManager = application.inputModeManager,
        sendPointerEvent = { eventType, position, scrollDelta, timeMillis, type, buttons, modifiers, nativeEvent, button ->
            scene.withPreparedMainThread {
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
            }
        },
        sendKeyEvent =
            { keyEvent ->
                // Give the active IME session a chance to consume the event (marked text / IME
                // navigation keys) before dispatching it to Compose.
                val textInputConsumed =
                    macOsTextInputSessionOwner.offerEventBeforeSendingToApplication(keyEvent)

                val finalResult = textInputConsumed || scene.withPreparedMainThread {
                    onPreviewKeyEvent(keyEvent) ||
                        composeScene.sendKeyEvent(keyEvent) ||
                        onKeyEvent(keyEvent)
                }
                logger.debug { "  sendKeyEvent final result=$finalResult" }
                finalResult
            },
    )

    @OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
    internal fun handleEvent(event: WindowEvent): EventHandlerResult {
        return when (event) {
            is Event.WindowScreenChange -> {
                setupDisplayLink()
                screen = MacOsScreen(Screen.allScreens().findById(nativeWindow.screenId()))
                density = density()
                composeScene.density = density
                composeScene.size = contentSizeInPx()
                EventHandlerResult.Stop
            }
            is Event.WindowMove -> {
                position = nativeWindow.origin.toDpOffset()
                EventHandlerResult.Stop
            }
            is Event.WindowResize -> {
                // Resizing from the top/left edges also shifts the native origin.
                position = nativeWindow.origin.toDpOffset()
                size = nativeWindow.size.toDpSize()
                contentSize = nativeWindow.contentSize.toDpSize()
                composeScene.size = contentSizeInPx()
                isFrameRequested = true
                EventHandlerResult.Stop
            }
            is Event.WindowFocusChange -> {
                isFocused = event.isKeyWindow
                inputStateTracker.updateStateAndSendEvents(event, density)
                EventHandlerResult.Stop
            }
            is Event.WindowFullScreenToggle -> {
                placement = placement()
                EventHandlerResult.Stop
            }
            is Event.WindowChangedOcclusionState -> {
                if (event.isVisible) {
                    position = nativeWindow.origin.toDpOffset()
                }
                when {
                    event.isVisible && displayLink == null -> {
                        setupDisplayLink()
                    }
                    event.isVisible -> {
                        displayLink?.setRunning(true)
                    }
                    !event.isVisible -> {
                        displayLink?.setRunning(false)
                    }
                }
                EventHandlerResult.Stop
            }
            is Event.MouseDown,
            is Event.MouseUp,
            is Event.MouseMoved,
            is Event.MouseDragged,
            is Event.MouseEntered,
            is Event.MouseExited,
            is Event.KeyDown,
            is Event.KeyUp,
            is Event.ScrollWheel,
            is Event.ModifiersChanged,
                -> {
                val result = inputStateTracker.updateStateAndSendEvents(event, density)

                when (event) {
                    is Event.MouseDown if result == EventHandlerResult.Continue &&
                        decoration is WindowDecoration.CustomTitleBar -> {
                        sendEventIntoTitleBar(event)
                    }
                    is Event.MouseMoved -> {
                        mouseOffset = event.locationInWindow.toDpOffset()
                        result
                    }
                    is Event.KeyDown -> {
                        // 🚧Hacky code here:
                        // We send handled event to the Application menu to blink the action item
                        // It wouldn't lead to second action execution, because in the current menu
                        // implementation we do actual work only when the item was activated via
                        // the menu and not via shortcut
                        AppMenuManager.offerCurrentEvent()
                        result
                    }
                    else -> result
                }
            }
            is Event.WindowCloseRequest -> {
                scene.withPreparedMainThread {
                    onCloseRequest(WindowCloseRequestReason.UserRequest)
                }
                EventHandlerResult.Stop
            }
            else -> EventHandlerResult.Continue
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun sendEventIntoTitleBar(mouseDownEvent: Event.MouseDown): EventHandlerResult {
        return if (
            mouseDownEvent.button == MouseButton.LEFT &&
            mouseDownEvent.locationInWindow.y < (decoration as WindowDecoration.CustomTitleBar).height.value
        ) {
            when (mouseDownEvent.clickCount) {
                1L -> nativeWindow.startDragWindow()
                else -> nativeWindow.toggleMaximize()
            }
            EventHandlerResult.Stop
        } else {
            EventHandlerResult.Continue
        }
    }

    internal var activeDragAndDropTransferData: DragAndDropTransferData? = null

    private var onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }
    private var onKeyEvent: (KeyEvent) -> Boolean = { false }

    private var contentState = mutableStateOf<(@Composable WindowScope.() -> Unit)?>(null)
    private var sceneContentInstalled = false

    private fun installSceneContentIfNeeded() {
        if (sceneContentInstalled) return
        sceneContentInstalled = true
        val windowScope = object : WindowScope {
            override val window: Window get() = this@MacOsWindow
        }
        composeScene.setContent {
            CompositionLocalProvider(
                LocalSystemTheme provides systemTheme,
                LocalTextToolbar provides remember { DefaultTextToolbar() },
                LocalWindow provides this,
                LocalTextInputSessionOwner provides macOsTextInputSessionOwner,
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
        isFrameRequested = true
    }

    override fun startInteractiveMove(pointerEvent: PointerEvent) {
        nativeWindow.startDragWindow()
    }

    init {
        // Registered here so that `inputStateTracker` is guaranteed to be initialized before
        // any event dispatched by the application event loop can reach `handleEvent`.
        application.windows += id to this
    }
}

private fun org.jetbrains.desktop.macos.Window.isMaximizedButNotInFullScreen(): Boolean {
    return !isFullScreen && isMaximized
}

private
val logger = logger<MacOsWindow>()
