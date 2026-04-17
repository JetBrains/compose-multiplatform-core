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

package androidx.compose.ui.kdt.macos

import androidx.annotation.MainThread
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SnapshotMutableStateImpl
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.kdt.draganddrop.DragAndDropImage
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.LocalDragAndDropManager
import androidx.compose.ui.focus.FocusOwnerImpl
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.renderWithLayerScope
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.InternalKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PositionCalculator
import androidx.compose.ui.kdt.InteractiveMoveInitiator
import androidx.compose.ui.kdt.KdtDragAndDropManager
import androidx.compose.ui.kdt.KdtDragAndDropTransferable
import androidx.compose.ui.kdt.LightweightWindowId
import androidx.compose.ui.kdt.PositionAwareWindow
import androidx.compose.ui.kdt.Scene
import androidx.compose.ui.kdt.Window
import androidx.compose.ui.kdt.WindowScope
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.platform.DefaultTextToolbar
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextInputContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.text.input.TextInputContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.width
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.kdt.logging.logger
import androidx.compose.ui.node.DragAndDropOwner
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import noria.CallbackInterceptorCompositionLocal
import noria.ID
import noria.activeCell
import noria.cell
import noria.impl.NoriaState
import noria.memo
import noria.ui.core.LocalWindow
import noria.ui.core.WindowData
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
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

class MacOsWindow internal constructor(
    private val application: MacOsApplication,
    internal val scene: Scene<*>,
    nativeWindow: org.jetbrains.desktop.macos.Window = org.jetbrains.desktop.macos.Window.create(),
    val viewContext: MetalViewContext = application.desktopGpuContext.createMetalViewContext(),
    private val onCloseRequest: () -> Unit,
) :
    PositionAwareWindow, InteractiveMoveInitiator {
    private var backingNativeWindow: org.jetbrains.desktop.macos.Window? = nativeWindow
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
    private var drawContent: (Canvas.() -> Unit)? = null
    private var displayLink: DisplayLink? = null
    private val displayLinkFrameStartTimeMark: AtomicReference<TimeMarkWrapper?> =
        AtomicReference(null)

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
        if (nativeWindow.isVisible) {
            setupDisplayLink()
        }
        application.windows += id to this
    }

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
            )
        }
    }

    override fun requestPlacement(placement: WindowPlacement) {
        if (!isDisposed) {
            when (placement) {
                WindowPlacement.Floating if nativeWindow.isMaximized -> nativeWindow.toggleMaximize()
                WindowPlacement.Floating if nativeWindow.isFullScreen -> nativeWindow.toggleFullScreen()
                WindowPlacement.Fullscreen if !nativeWindow.isFullScreen -> nativeWindow.toggleFullScreen()
                WindowPlacement.Maximized if !nativeWindow.isMaximized -> nativeWindow.toggleMaximize()
                else -> {}
            }
        }
    }

    override fun requestClose() {
        if (!isDisposed) {
            onCloseRequest()
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
        nativeWindow.isMaximized -> WindowPlacement.Maximized
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
    private val dragAndDropOwner = DragAndDropOwner(KdtDragAndDropManager(this))
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

        override fun requestWindowFocus() {
            nativeWindow.makeKeyAndOrderFront()
        }

        @OptIn(InternalCoreApi::class)
        override val keyboardModifiers: PointerKeyboardModifiers
            get() = inputStateTracker.keyboardModifiers

        @ExperimentalComposeUiApi
        override val containerSize: IntSize
            get() = density.run {
                contentSize.run {
                    IntSize(
                        width.roundToPx(),
                        height.roundToPx(),
                    )
                }
            }
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
        ).singleOrNullOrThrow()?.let { Path(it) }
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
        val data = (transferData.transferable as? KdtDragAndDropTransferable)
            ?.clipboardEntry
            ?.nativeClipEntry as? MacOsClipboardEntry.Items
            ?: return

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

        val draggingItems = data.items.mapIndexed { index, item ->
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
            val hadPendingFrame = isFrameRequested
            application.windows -= id
            // If this was the last window in the scene and a frame was pending,
            // schedule a reconcile as a replacement for the DisplayLink from here
            if (hadPendingFrame && application.windows.values.none { it.scene == scene }) {
                GrandCentralDispatch.dispatchOnMain(highPriority = false) {
                    scene.withPreparedMainThread {
                        application.withoutReentrancy {
                            scene.reconcile()
                        }
                    }
                }
            }
            if (id !in application.reusableNativeWindowResources) {
                nativeWindow.close()
                application.desktopGpuContext.destroyMetalViewContext(viewContext)
            }
            backingNativeWindow = null
        }
    }

    override fun calculatePositionInWindow(localPosition: Offset): Offset {
        return contentPosition() + localPosition
    }

    override fun calculateLocalPosition(positionInWindow: Offset): Offset {
        return positionInWindow - contentPosition()
    }

    /**
     * Relative to window
     */
    private fun contentPosition(): Offset = density().run {
        val windowOrigin = nativeWindow.origin.toDpOffset()
        val contentOrigin = nativeWindow.contentOrigin.toDpOffset()
        (contentOrigin - windowOrigin).toOffset()
    }

    private val textInputContext = object : TextInputContext {
        override fun handleKeyEvent(event: KeyEvent): Boolean {
            val nativeEvent = (event.nativeKeyEvent as? InternalKeyEvent)?.nativeEvent
            val isSyntheticKeyEvent = nativeEvent.let {
                it !is Event.KeyDown && it !is Event.KeyUp ||
                    // macOS doesn't send native key events for modifier keys, so it must be
                    // synthetic in this case
                    event.key.isModifier
            }
            logger.debug {
                "textInputContext.handleKeyEvent: key=${event.key}, type=${event.type}, " +
                    "isSyntheticKeyEvent=$isSyntheticKeyEvent, " +
                    "nativeEvent=${nativeEvent?.let { it::class.simpleName }}"
            }
            // We don't call through if we know that we're currently handling a synthetic key event.
            // Otherwise, the underlying KDT code would erroneously send the latest actual
            // event into the TextInputContext, potentially leading to duplicate insertions or otherwise
            // unexpected behavior.
            return if (isSyntheticKeyEvent) {
                logger.debug { "  -> skipping (synthetic key event), returning false" }
                false
            } else {
                currentTextInputClient?.armSilentConsumptionDetection()
                val result = nativeWindow.textInputContext.handleCurrentEvent()
                val consumed = result == EventHandlerResult.Stop
                currentTextInputClient?.evaluateSilentConsumption(consumed)
                logger.debug {
                    "  -> nativeWindow.textInputContext.handleCurrentEvent() returned $result" +
                        " (silentlyConsumedEvent=${currentTextInputClient?.silentlyConsumedEvent == true})"
                }
                consumed
            }
        }
    }

    private var currentTextInputClient: ComposeTextInputClient? = null

    @OptIn(ExperimentalComposeUiApi::class)
    private val platformTextInputInterceptor =
        PlatformTextInputInterceptor { request, _ ->
            logger.debug { "platformTextInputInterceptor: new text input session starting" }
            logger.debug { "  discarding marked text and invalidating character coordinates" }
            nativeWindow.textInputContext.discardMarkedText()
            nativeWindow.textInputContext.invalidateCharacterCoordinates()
            currentTextInputClient = ComposeTextInputClient(
                request,
                scene,
                { density },
                {
                    nativeWindow.contentOrigin + it
                },
            ).also {
                logger.debug { "  setting new ComposeTextInputClient on native window" }
                nativeWindow.setTextInputClient(it)
            }
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    logger.debug { "platformTextInputInterceptor: text input session cancelled, clearing client" }
                    currentTextInputClient = null
                    nativeWindow.setTextInputClient(TextInputClient.Noop)
                }
            }
        }

    @OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
    @Composable
    @ApiStatus.Internal
    override fun Content(onLayout: (WindowData) -> Unit) {
//        val renderPerfMetrics = LocalRenderPerfMetrics.current

        CompositionLocalProvider(
            LocalSystemTheme provides systemTheme,
            //        LocalAccessibilityManager provides owner.accessibilityManager,
            //        LocalAutofill provides owner.autofill,
            //        LocalAutofillTree provides owner.autofillTree,
            LocalDensity provides density,
            LocalFocusManager provides focusOwner,
            //        @Suppress("DEPRECATION") LocalFontLoader
            //            providesDefault @Suppress("DEPRECATION") owner.fontLoader,
            LocalLayoutDirection provides layoutDirection,
            //        LocalTextInputService provides owner.textInputService,
            LocalTextInputContext provides textInputContext,
            //        LocalPlatformTextInputPluginRegistry provides
            // owner.platformTextInputPluginRegistry,
            LocalTextToolbar provides remember { DefaultTextToolbar() },
            LocalViewConfiguration provides viewConfiguration,
            LocalWindowInfo provides windowInfo,
            LocalWindow provides this,
            LocalDragAndDropManager provides dragAndDropOwner,
        ) {
            InterceptPlatformTextInput(platformTextInputInterceptor) {
                cell {
                    withLayoutBuilderStack {
                        val uiRootCell = cell {
                            uiRoot(focusOwner.focusRoot) {
                                Box(
                                    Modifier.then(dragAndDropOwner.modifier),
                                    propagateMinConstraints = true,
                                ) {
                                    val windowScope = remember(this@MacOsWindow) {
                                        object : WindowScope {
                                            override val window: Window
                                                get() = this@MacOsWindow
                                        }
                                    }
                                    contentState.value?.let { windowScope.it() }
                                }
                            }
                        }

                        val rootLayoutThunkCell = cell {
                            val rootLayoutBuilder =
                                uiRootCell.read().layoutBuilder ?: rememberEmptyLayoutBuilder()
                            density.run {
                                rootLayoutBuilder.measure(
                                    Constraints.fixed(
                                        contentSize.width.roundToPx(),
                                        contentSize.height.roundToPx(),
                                    ),
                                )
                            }
                        }

                        activeCell {
                            //                            renderPerfMetrics.startLayout(window.windowId)
                            latestRootLayoutNode = memo {
                                LayoutScope(currentNoriaContext).run {
                                    rootLayoutThunkCell.read()
                                        .realize(
                                            density.run {
                                                IntRect(
                                                    0,
                                                    0,
                                                    contentSize.width.roundToPx(),
                                                    contentSize.height.roundToPx(),
                                                )
                                            },
                                        )
                                        .apply {
                                            // todo[unterhofer] Activate this when there is time to fix the offenders
                                            // check(overlays.read().isEmpty()) {
                                            // "Overlays were emitted into a non-existent OverlayHost: ${overlays.read()}\nWrap the subtree in an OverlayHost for each key"
                                            // }
                                            node.place(IntOffset.Zero)
                                        }.node
                                }
                            }.also {
                                dragAndDropOwner.updateCoordinates(it.coordinates)
                            }

                            // todo[unterhofer] make this synchronous again once we have the new version of
                            //  SuspendingPointerInputFilter:
                            //  https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/input/pointer/SuspendingPointerInputFilter.kt;l=575
                            /**
                             * Send [PointerEventType.Enter] events to [LayoutNode]s which appeared under the
                             * cursor, so that the user doesn't have to move their mouse to trigger hovering.
                             * This has to run in a coroutine dispatched on the effect dispatcher because new
                             * [SuspendingPointerInputFilter]s will schedule their blocks there as well, and
                             * otherwise they won't be waiting for events yet.
                             */
                            rememberCoroutineScope().launch {
                                inputStateTracker.sendPointerInputEventWithCurrentStateIfNecessary(
                                    if (application.inputModeManager.inputMode == InputMode.Touch) {
                                        PointerEventType.Move
                                    } else {
                                        PointerEventType.Exit
                                    },
                                )
                            }

                            //                            renderPerfMetrics.endLayout(window.windowId)

                            onLayout(WindowData(id, uiRootCell.read(), latestRootLayoutNode!!))

                            val callbackInterceptor = CallbackInterceptorCompositionLocal.current
                            DisposableEffect(
                                dragAndDropOwner,
                                density,
                                callbackInterceptor,
                            ) {
                                macOsDragAndDropManager = MacOsDragAndDropManager(
                                    ComposeSceneDragAndDropNode { dragAndDropOwner },
                                    density,
                                    callbackInterceptor,
                                )
                                onDispose {
                                    macOsDragAndDropManager = null
                                }
                            }

                            DisposableEffect(callbackInterceptor) {
                                drawContent = {
                                    span("drawing") {
                                        callbackInterceptor.execute {
                                            RenderContext(this).renderWithLayerScope(
                                                latestRootLayoutNode!!,
                                                density.run {
                                                    IntRect(
                                                        0,
                                                        0,
                                                        contentSize.width.roundToPx(),
                                                        contentSize.height.roundToPx(),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                                onDispose {
                                    drawContent = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun preparePicture(): PresentablePicture? {
        if (!isDisposed) {
            application.withoutReentrancy { scene.reconcile() }
            return drawContent?.let { drawContent ->
                val size = viewContext.view.size()
                val bounds = Rect.makeWH(size.width.toFloat(), size.height.toFloat())
                val canvas = pictureRecorder.beginRecording(bounds)
                canvas.clear(org.jetbrains.skia.Color.TRANSPARENT)
                canvas.drawContent()
                PresentablePicture(pictureRecorder.finishRecordingAsPicture(), size)
            }
        } else {
            return null
        }
    }

    private class TimeMarkWrapper(val timeMark: TimeSource.Monotonic.ValueTimeMark)

    private fun setupDisplayLink() {
        displayLink?.close()
        displayLink = DisplayLink.create(nativeWindow.screenId()) {
            if (
                !isDisposed &&
                isFrameRequested &&
                displayLinkFrameStartTimeMark.compareAndSet(
                    null,
                    TimeMarkWrapper(TimeSource.Monotonic.markNow()),
                )
            ) {
                GrandCentralDispatch.dispatchOnMain(highPriority = true) {
                    isFrameRequested = false
                    scene.withPreparedMainThread {
                        preparePicture()?.let { presentablePicture ->
                            viewContext.presentAsync(
                                presentablePicture, waitForCATransaction = false,
                                onComplete = {
                                    presentablePicture.close()
                                    val elapsedTime = displayLinkFrameStartTimeMark
                                        .exchange(null)!!
                                        .timeMark
                                        .elapsedNow()
                                    if (elapsedTime.inWholeMilliseconds > 10) {
                                        logger.debug("Long frame: ${elapsedTime}")
                                    }
                                },
                            )
                        } ?: run {
                            isFrameRequested = true
                        }
                    }
                }
            }
        }
        displayLink!!.setRunning(true)
    }

    private fun repaintSynchronously() {
        displayLink?.setRunning(false)
        isFrameRequested = false
        scene.withPreparedMainThread {
            preparePicture()?.use { picture ->
                viewContext.presentSync(picture, waitForCATransaction = true)
            } ?: run {
                logger.debug { "No picture was produced before display layer" }
                isFrameRequested = true
            }
        }
        displayLink?.setRunning(true)
    }

    @OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
    private val inputStateTracker = InputStateTracker(
        inputModeManager = application.inputModeManager,
        sendPointerInputEvent = { pointerInputEvent ->
            // The IME popups respond to the mouse event by committing/discarding composition and
            // dismissing the popup, which is why we need to give it priority if it's active
            val textInputContextHandlingResult = if (
                currentTextInputClient?.hasMarkedText() == true ||
                currentTextInputClient?.silentlyConsumedEvent == true
            ) {
                logger.debug {
                    "handleEvent: forwarding MouseDown to IME " +
                        "(hasMarkedText=${currentTextInputClient?.hasMarkedText() == true}, " +
                        "silentlyConsumedEvent=${currentTextInputClient?.silentlyConsumedEvent == true})"
                }
                currentTextInputClient?.armSilentConsumptionDetection()
                val result = nativeWindow.textInputContext.handleCurrentEvent()
                currentTextInputClient?.evaluateSilentConsumption(result == EventHandlerResult.Stop)
                logger.debug {
                    "  IME result=$result" +
                        " (silentlyConsumedEvent=${currentTextInputClient?.silentlyConsumedEvent == true})"
                }
                when (result) {
                    EventHandlerResult.Stop -> ProcessResult(true, true, true, true, true)
                    EventHandlerResult.Continue -> null
                }
            } else {
                null
            }

            textInputContextHandlingResult
                ?: latestRootLayoutNode?.let { rootLayoutNode ->
                    scene.withPreparedMainThread {
                        pointerInputEventProcessor.process(
                            pointerInputEvent,
                            rootLayoutNode,
                            positionCalculator,
                        )
                    }
                }
                ?: ProcessResult(0)
        },
        sendKeyEvent =
            { keyEvent ->
                logger.debug {
                    "sendKeyEvent: key=${keyEvent.key}, type=${keyEvent.type}, " +
                        "hasMarkedText=${currentTextInputClient?.hasMarkedText() == true}, " +
                        "silentlyConsumedEvent=${currentTextInputClient?.silentlyConsumedEvent == true}"
                }

                val textInputContextConsumedEvent =
                    if (
                        currentTextInputClient?.hasMarkedText() == true ||
                        (currentTextInputClient?.silentlyConsumedEvent == true &&
                            keyEvent.type == KeyEventType.KeyDown)
                    ) {
                        val result = textInputContext.handleKeyEvent(keyEvent)
                        logger.debug {
                            "  textInputContextConsumedEvent=$result " +
                                "(hasMarkedText=${currentTextInputClient?.hasMarkedText() == true}, " +
                                "silentlyConsumedEvent=${currentTextInputClient?.silentlyConsumedEvent == true})"
                        }
                        result
                    } else {
                        false
                    }

                val finalResult =
                    textInputContextConsumedEvent || scene.withPreparedMainThread {
                        val previewHandled = onPreviewKeyEventState.value(keyEvent)
                        val focusHandled =
                            !previewHandled && focusOwner.dispatchKeyEvent(keyEvent)
                        val keyEventHandled =
                            !previewHandled && !focusHandled && onKeyEventState.value(keyEvent)
                        previewHandled || focusHandled || keyEventHandled
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
                EventHandlerResult.Stop
            }
            is Event.WindowMove -> {
                position = nativeWindow.origin.toDpOffset()
                EventHandlerResult.Stop
            }
            is Event.WindowResize -> {
                size = nativeWindow.size.toDpSize()
                contentSize = nativeWindow.contentSize.toDpSize()
                EventHandlerResult.Stop
            }
            is Event.WindowFocusChange -> {
                isFocused = event.isKeyWindow
                // todo[unterhofer]
//                isActive = event.isActiveWindow
//                isMain = event.isMainWindow
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
                    onCloseRequest()
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

    private val positionCalculator = object : PositionCalculator {
        override fun screenToLocal(positionOnScreen: Offset): Offset {
            return positionOnScreen - density().run {
                nativeWindow.origin.run {
                    Offset(
                        x.dp.toPx(),
                        y.dp.toPx(),
                    )
                }
            }
        }

        override fun localToScreen(localPosition: Offset): Offset {
            return localPosition + density().run {
                nativeWindow.origin.run {
                    Offset(
                        x.dp.toPx(),
                        y.dp.toPx(),
                    )
                }
            }
        }

        override val rootPositionOnScreen: Offset
            get() = density().run {
                nativeWindow.origin.run {
                    Offset(
                        x.dp.toPx(),
                        y.dp.toPx(),
                    )
                }
            }
    }

    private val focusOwner = FocusOwnerImpl(
        { scene.coroutineScope },
        DebugLocation(this::class),
    ) { isFocused }

    private val onPreviewKeyEventState =
        SnapshotMutableStateImpl<(KeyEvent) -> Boolean>({ false })
    private val onKeyEventState = SnapshotMutableStateImpl<(KeyEvent) -> Boolean>({ false })

    private var contentState =
        SnapshotMutableStateImpl<(@Composable WindowScope.() -> Unit)?>(null)

    override fun setContent(
        onPreviewKeyEvent: (KeyEvent) -> Boolean,
        onKeyEvent: (KeyEvent) -> Boolean,
        noriaState: NoriaState?,
        content: @Composable WindowScope.() -> Unit,
    ) {
        onPreviewKeyEventState.setValueAndScheduleDependantsRightAway(
            noriaState,
            onPreviewKeyEvent,
        )
        onKeyEventState.setValueAndScheduleDependantsRightAway(noriaState, onKeyEvent)
        contentState.setValueAndScheduleDependantsRightAway(noriaState, content)
    }

    override fun startInteractiveMove(pointerEvent: PointerEvent) {
        nativeWindow.startDragWindow()
    }
}

private
val logger = logger<MacOsWindow>()
