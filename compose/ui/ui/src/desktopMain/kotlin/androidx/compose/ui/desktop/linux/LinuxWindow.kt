@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, InternalCoreApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.linux

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.LocalTextInputSessionOwner
import androidx.compose.ui.desktop.InteractiveMoveInitiator
import androidx.compose.ui.desktop.InteractiveResizeInitiator
import androidx.compose.ui.desktop.KdtDragAndDropManager
import androidx.compose.ui.desktop.KdtDragAndDropTransferable
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.WindowResizeHandle
import androidx.compose.ui.desktop.WindowScope
import androidx.compose.ui.desktop.draganddrop.DragAndDropImage
import androidx.compose.ui.desktop.linuxMimeTypes
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.key.InternalKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.DefaultTextToolbar
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalTextInputContext
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformDragAndDropManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextInputContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import noria.CallbackInterceptor
import org.jetbrains.desktop.linux.DataSource
import org.jetbrains.desktop.linux.DesktopTitlebarAction
import org.jetbrains.desktop.linux.DragAndDropAction
import org.jetbrains.desktop.linux.DragAndDropQueryData
import org.jetbrains.desktop.linux.DragAndDropQueryResponse
import org.jetbrains.desktop.linux.DragIconParams
import org.jetbrains.desktop.linux.Event
import org.jetbrains.desktop.linux.EventHandlerResult
import org.jetbrains.desktop.linux.LogicalSize
import org.jetbrains.desktop.linux.RenderingMode
import org.jetbrains.desktop.linux.RequestId
import org.jetbrains.desktop.linux.StartDragAndDropParams
import org.jetbrains.desktop.linux.WindowCapabilities
import org.jetbrains.desktop.linux.WindowDecorationMode
import org.jetbrains.desktop.linux.WindowParams
import org.jetbrains.desktop.linux.WindowResizeEdge
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.GLAssembledInterface
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.makeGLWithInterface

class LinuxWindow internal constructor(
    private val application: LinuxApplication,
    internal val scene: Scene<*>,
    private val onCloseRequest: (WindowCloseRequestReason) -> Unit,
) : InteractiveMoveInitiator, InteractiveResizeInitiator {
    private val nativeWindowId = application.allocateNativeWindowId()

    override val nativeWindow: org.jetbrains.desktop.linux.Window =
        application.nativeApplication.createWindow(
            WindowParams(
                windowId = nativeWindowId,
                appId = application.identifier,
                title = "",
                size = LogicalSize(800, 600),
                preferClientSideDecoration = true,
                renderingMode = RenderingMode.Auto,
            ),
        )

    override val id: LightweightWindowId = LightweightWindowId(nativeWindow.windowId)

    @Volatile
    private var isDisposed = false

    @Volatile
    internal var isFrameRequested = false

    private val fileDialogResponses =
        ConcurrentHashMap<RequestId, CancellableContinuation<List<Path>?>>()

    private var titleField by mutableStateOf("")
    private var overriddenSystemTheme by mutableStateOf<SystemTheme?>(null)
    private var windowCapabilities by mutableStateOf<WindowCapabilities?>(null)

    private var incomingDragMimeTypes: List<String> = emptyList()
    private val incomingDragMimeData = linkedMapOf<String, ByteArray>()

    override var title: String
        get() = titleField
        @MainThread
        set(value) {
            titleField = value
            if (!isDisposed) {
                onNativeWindowAsync { setTitle(value) }
            }
        }

    override var size: DpSize by mutableStateOf(DpSize(800.dp, 600.dp))
        private set

    override var contentSize: DpSize by mutableStateOf(DpSize(800.dp, 600.dp))
        private set

    override fun requestSize(size: DpSize): Unit = Unit

    private val minSizeState = mutableStateOf(DpSize.Zero)
    override val minSize: DpSize
        get() = minSizeState.value

    override fun requestMinSize(minSize: DpSize) {
        minSizeState.value = minSize
        if (!isDisposed) {
            onNativeWindowAsync {
                setMinSize(
                    LogicalSize(
                        minSize.width.takeOrElse { this@LinuxWindow.minSize.width }.value.roundToInt(),
                        minSize.height.takeOrElse { this@LinuxWindow.minSize.height }.value.roundToInt(),
                    ),
                )
            }
        }
    }

    private val maxSizeState = mutableStateOf(DpSize(7680.dp, 4320.dp))
    override val maxSize: DpSize
        get() = maxSizeState.value

    override fun requestMaxSize(maxSize: DpSize) {
        maxSizeState.value = maxSize
        onNativeWindowAsync {
            setMaxSize(
                LogicalSize(
                    maxSize.width.takeOrElse { this@LinuxWindow.maxSize.width }.value.roundToInt(),
                    maxSize.height.takeOrElse { this@LinuxWindow.maxSize.height }.value.roundToInt(),
                ),
            )
        }
    }

    private val isUserResizableState = mutableStateOf(true)
    override val isUserResizable: Boolean
        get() = isUserResizableState.value

    override fun requestUserResizable(userResizable: Boolean) {
        isUserResizableState.value = userResizable
    }

    override var isFocused: Boolean by mutableStateOf(false)
        private set

    override fun requestFocus() {
        application.requestWindowActivation(id)
    }

    override fun requestBringToFront() {
        application.requestWindowActivation(id)
    }

    override fun requestFocusAndBringToFront() {
        application.requestWindowActivation(id)
    }

    @ExperimentalComposeUiApi
    override var decoration: WindowDecoration by mutableStateOf(WindowDecoration.Undecorated())
        private set

    @ExperimentalComposeUiApi
    override fun requestDecoration(vararg decorations: WindowDecoration) {
        onNativeWindowAsync {
            for (decoration in decorations) {
                when (decoration) {
                    WindowDecoration.Decorated -> {
                        requestDecorationMode(WindowDecorationMode.Server)
                        return@onNativeWindowAsync
                    }
                    is WindowDecoration.Undecorated -> {
                        requestDecorationMode(WindowDecorationMode.Client)
                        return@onNativeWindowAsync
                    }
                    is WindowDecoration.CustomTitleBar -> continue
                }
            }
        }
    }

    override val customTitleBarInsets: Pair<Dp, Dp>?
        get() = null

    @ExperimentalComposeUiApi
    override val customTitleBarLayout:
        Pair<List<WindowDecoration.TitleBarElement>, List<WindowDecoration.TitleBarElement>>?
        get() = application.customTitleBarLayout?.forCapabilities(windowCapabilities)

    override val systemTheme: SystemTheme
        get() = overriddenSystemTheme ?: application.systemTheme

    override fun requestSystemTheme(systemTheme: SystemTheme?) {
        overriddenSystemTheme = systemTheme
    }

    override fun requestMinimized(minimized: Boolean) {
        if (minimized) {
            onNativeWindowAsync { minimize() }
        } else {
            requestFocusAndBringToFront()
        }
    }

    override var placement: WindowPlacement by mutableStateOf(WindowPlacement.Floating)
        private set

    override fun requestPlacement(placement: WindowPlacement) {
        onNativeWindowAsync {
            when (placement) {
                WindowPlacement.Floating -> {
                    unmaximize()
                    unsetFullScreen()
                }
                WindowPlacement.Maximized -> maximize()
                WindowPlacement.Fullscreen -> setFullScreen()
            }
        }
    }

    override fun requestClose(reason: WindowCloseRequestReason) {
        if (!isDisposed) {
            application.onEventLoopAsync {
                onCloseRequest(reason)
            }
        }
    }

    override fun requestTitleBarDoubleClickAction(pointerEvent: PointerEvent) {
        performTitleBarAction(application.titleBarDoubleClickAction, pointerEvent)
    }

    override fun requestTitleBarTertiaryClickAction(pointerEvent: PointerEvent) {
        performTitleBarAction(application.titleBarMiddleClickAction, pointerEvent)
    }

    override fun requestTitleBarSecondaryClickAction(pointerEvent: PointerEvent) {
        performTitleBarAction(application.titleBarRightClickAction, pointerEvent)
    }

    override var screen: LinuxScreen by mutableStateOf(
        application.screens.values.firstOrNull()
            ?: LinuxScreen(application.nativeApplication.allScreens().screens.first()),
    )
        private set

    override var density: Density by mutableStateOf(Density(1.0f))
        private set

    private val pointerIconService = LinuxPointerIconService(application, nativeWindow)
    private val inputModeManager = InputModeManagerImpl(InputMode.Touch) {
        pointerIconService.setHiddenUntilPointerMoves(it == InputMode.Keyboard)
        true
    }
    internal val dragAndDropManager: LinuxDragAndDropManager = LinuxDragAndDropManager(
        rootDragAndDropNode = { composeScene.rootDragAndDropNode },
        density = { density },
        callbackInterceptor = object : CallbackInterceptor {
            override fun <T> execute(f: () -> T): T {
                return scene.withPreparedMainThread {
                    f()
                }
            }
        }
    )

    private var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)

    val viewConfiguration: ViewConfiguration = object : ViewConfiguration {
        override val longPressTimeoutMillis: Long = 500
        override val doubleTapTimeoutMillis: Long
            get() = application.doubleClickIntervalMillis
        override val doubleTapMinTimeMillis: Long = 40
        override val touchSlop: Float
            get() = with(density) { 18.dp.toPx() }
    }

    val windowInfo: WindowInfo = object : WindowInfo {
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

    // ----- Compose scene wiring -----

    private val architectureComponentsOwner = DefaultArchitectureComponentsOwner().apply {
        enableSavedStateHandles()
        setLifecycleState(Lifecycle.State.RESUMED)
    }

    private val linuxTextInputSessionOwner = LinuxTextInputSessionOwner(
        startInputMethod = { context, surroundingText ->
            application.startTextInput(id, context, surroundingText)
        },
        stopInputMethod = { application.stopTextInput(id) },
        onDataChanged = { context, surroundingText ->
            application.updateTextInput(id, context, surroundingText)
        },
    )

    private val platformContext: PlatformContext = object : PlatformContext by PlatformContext.Empty() {
        override val windowInfo: WindowInfo
            get() = this@LinuxWindow.windowInfo
        override val viewConfiguration: ViewConfiguration
            get() = this@LinuxWindow.viewConfiguration
        override val inputModeManager: InputModeManager
            get() = this@LinuxWindow.inputModeManager
        override val architectureComponentsOwner = this@LinuxWindow.architectureComponentsOwner
        override val textToolbar = DefaultTextToolbar()
        override val dragAndDropManager: PlatformDragAndDropManager =
            KdtDragAndDropManager(this@LinuxWindow)

        override fun textInputSessionOwner() = linuxTextInputSessionOwner
    }

    private val composeScene: ComposeScene = CanvasLayersComposeScene(
        density = density,
        layoutDirection = layoutDirection,
        size = contentSizeInPx(),
        coroutineContext = scene.coroutineScope.coroutineContext +
            LinuxKdtMainDispatcher.INSTANCE.immediate,
        platformContext = platformContext,
        invalidate = { isFrameRequested = true },
    )

    init {
        application.windows[id] = this
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
        // TODO
        return null
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
        // TODO
        return emptyList()
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
        // TODO
        return null
    }

    override fun captureScreenshot(): ImageBitmap {
        val width = maxOf(1, density.run { contentSize.width.roundToPx() })
        val height = maxOf(1, density.run { contentSize.height.roundToPx() })
        return Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.clear(Color.TRANSPARENT)
            composeScene.render(surface.canvas.asComposeCanvas(), System.nanoTime())
            surface.makeImageSnapshot().toComposeImageBitmap()
        }
    }

    override fun dispose() {
        if (!isDisposed) {
            isDisposed = true
            application.windows.remove(id)
            composeScene.close()
            architectureComponentsOwner.setLifecycleState(Lifecycle.State.DESTROYED)
            onNativeWindowAsync { close() }
        }
    }

    internal fun onClosed() {
        isDisposed = true
        fileDialogResponses.values.forEach { it.cancel() }
        fileDialogResponses.clear()
        if (application.windows.isEmpty()) {
            application.finishStructuredQuitIfNeeded()
        }
    }

    internal fun activate(token: String) {
        onNativeWindowAsync { activate(token) }
    }

    internal fun queryDragAndDropTarget(query: DragAndDropQueryData): DragAndDropQueryResponse {
        return dragAndDropManager.onQuery(query)
    }

    internal fun onDataTransferAvailable(event: Event.DataTransferAvailable): Boolean {
        if (event.dataSource != DataSource.DragAndDrop) {
            return false
        }
        incomingDragMimeTypes = event.mimeTypes
        incomingDragMimeData.clear()
        return true
    }

    internal fun onDataTransfer(event: Event.DataTransfer): Boolean {
        if (incomingDragMimeTypes.isEmpty()) {
            return false
        }
        val content = event.content ?: return false
        incomingDragMimeData[content.mimeType] = content.data
        return true
    }

    internal fun handleEvent(event: Event): EventHandlerResult {
        return when (event) {
            is Event.WindowConfigure -> {
                size = event.size.toDpSize()
                contentSize = size
                isFocused = event.active
                placement = when {
                    event.fullscreen -> WindowPlacement.Fullscreen
                    event.maximized -> WindowPlacement.Maximized
                    else -> WindowPlacement.Floating
                }
                windowCapabilities = event.capabilities
                decoration = when (event.decorationMode) {
                    WindowDecorationMode.Server -> WindowDecoration.Decorated
                    WindowDecorationMode.Client -> WindowDecoration.Undecorated()
                }
                composeScene.size = contentSizeInPx()
                isFrameRequested = true
                EventHandlerResult.Stop
            }

            is Event.WindowScaleChanged -> {
                density = Density(event.newScale.toFloat())
                composeScene.density = density
                EventHandlerResult.Stop
            }

            is Event.WindowScreenChange -> {
                screen =
                    application.screens.values.firstOrNull { it.nativeScreen.screenId == event.newScreenId }
                        ?: screen
                EventHandlerResult.Stop
            }

            is Event.WindowKeyboardEnter -> {
                isFocused = true
                inputStateTracker.updateStateAndSendEvents(event, density)
            }

            is Event.WindowKeyboardLeave -> {
                isFocused = false
                inputStateTracker.updateStateAndSendEvents(event, density)
            }

            is Event.MouseDown,
            is Event.MouseUp,
            is Event.MouseMoved,
            is Event.MouseEntered,
            is Event.MouseExited,
            is Event.ScrollWheel,
            is Event.KeyDown,
            is Event.KeyUp,
            is Event.ModifiersChanged,
                -> inputStateTracker.updateStateAndSendEvents(event, density)

            is Event.WindowDraw -> {
                if (isDisposed) {
                    return EventHandlerResult.Continue
                }
                isFrameRequested = false
                draw(event)
                EventHandlerResult.Stop
            }

            is Event.WindowCloseRequest -> {
                scene.withPreparedMainThread {
                    onCloseRequest(WindowCloseRequestReason.UserRequest)
                }
                EventHandlerResult.Stop
            }

            is Event.FileChooserResponse -> {
                fileDialogResponses.remove(event.requestId)?.resume(event.files.map(::Path))
                EventHandlerResult.Stop
            }

            is Event.DropPerformed -> {
                event.content?.let { incomingDragMimeData[it.mimeType] = it.data }
                dragAndDropManager.onDrop(event)
                incomingDragMimeTypes = emptyList()
                incomingDragMimeData.clear()
                EventHandlerResult.Stop
            }

            is Event.DragAndDropLeave -> {
                dragAndDropManager.onLeave()
                incomingDragMimeTypes = emptyList()
                incomingDragMimeData.clear()
                EventHandlerResult.Stop
            }

            else -> EventHandlerResult.Continue
        }
    }

    internal fun handleTextInput(event: Event.TextInput): EventHandlerResult {
        linuxTextInputSessionOwner.handleTextInputEvent(
            event.preeditStringData,
            event.commitStringData,
            event.deleteSurroundingTextData,
        )
        return EventHandlerResult.Stop
    }

    internal fun hasPreeditString(): Boolean =
        linuxTextInputSessionOwner.isTextInputSessionActive() &&
            linuxTextInputSessionOwner.hasPreeditString

    internal fun discardActivePreedit() {
        linuxTextInputSessionOwner.handleTextInputEvent(
            preeditStringData = null,
            commitStringData = null,
            deleteSurroundingTextData = null,
        )
    }

    @Composable
    override fun Content(onLayout: (LightweightWindowId) -> Unit) {
        // ComposeScene drives its own composition; nothing to host here.
        onLayout(id)
    }

    private val inputStateTracker = InputStateTracker(
        inputModeManager = inputModeManager,
        sendPointerInputEvent = { pointerInputEvent ->
            val pointer = pointerInputEvent.pointers.firstOrNull()
            if (pointer == null) {
                PointerEventResult()
            } else {
                composeScene.sendPointerEvent(
                    eventType = pointerInputEvent.eventType,
                    position = pointer.position,
                    scrollDelta = pointer.scrollDelta,
                    timeMillis = pointerInputEvent.uptime,
                    type = pointer.type,
                    buttons = pointerInputEvent.buttons,
                    keyboardModifiers = pointerInputEvent.keyboardModifiers,
                    nativeEvent = pointerInputEvent.nativeEvent,
                    button = pointerInputEvent.button,
                )
            }
        },
        sendKeyEvent = { keyEvent ->
            // IME commit is driven by the editor's own bubble key handler
            // (TextInputSessionOwner.handleEventWithInputSession), so the window must not pre-empt
            // here: doing so would swallow keys before onPreviewKeyEvent and the focus dispatch.
            onPreviewKeyEvent(keyEvent) ||
                composeScene.sendKeyEvent(keyEvent) ||
                onKeyEvent(keyEvent)
        },
    )

    internal fun startDragSession(
        offset: Offset,
        transferData: DragAndDropTransferData,
        decorationSize: Size,
        drawDragDecoration: DrawScope.() -> Unit,
    ) {
        val clipEntry =
            (transferData.transferable as? KdtDragAndDropTransferable)?.clipboardEntry ?: return
        val itemsEntry = clipEntry.nativeClipEntry as? ClipboardItemsEntry ?: return
        val mimeTypes = itemsEntry.linuxMimeTypes()
        val supportedActions = transferData.supportedActions.toSet()
        val dragImageBytes = DragAndDropImage(
            size = decorationSize,
            density = density,
            layoutDirection = layoutDirection,
            drawDragDecoration = drawDragDecoration,
        ).encodeToPngBytes()

        application.activeDragSource = LinuxApplication.ActiveDragSource(
            windowId = id,
            itemsEntry = itemsEntry,
            supportedActions = supportedActions,
            onTransferCompleted = { action -> transferData.onTransferCompleted?.invoke(action) },
            dragIconPngBytes = dragImageBytes,
        )
        onNativeWindowAsync {
            // Native DnD takes over the pointer here, so the press that started the
            // drag will never get a matching release in this window. Clear the
            // pressed buttons so the follow-up exit event reports `down = false` - otherwise
            // the original press hit path keeps receiving pointer events
            inputStateTracker.clearPointerButtons()

            startDragAndDrop(
                StartDragAndDropParams(
                    mimeTypes = mimeTypes,
                    actions = supportedActions.mapNotNull(DragAndDropTransferAction::toLinuxAction).toSet(),
                    dragIconParams = DragIconParams(
                        renderingMode = RenderingMode.Software,
                        size = decorationSize.toLogicalSize(density),
                    ),
                ),
            )
        }
    }

    private fun Iterable<DragAndDropTransferAction>.toLinuxActions(): Set<DragAndDropAction> {
        val supported = toSet()
        return buildSet {
            if (DragAndDropTransferAction.Copy in supported || supported.isEmpty()) {
                add(DragAndDropAction.Copy)
            }
            if (DragAndDropTransferAction.Move in supported) {
                add(DragAndDropAction.Move)
            }
        }
    }

    private fun draw(event: Event.WindowDraw) {
        val now = System.nanoTime()

        val softwareDrawData = event.softwareDrawData
        if (softwareDrawData != null) {
            Surface.makeRasterDirect(
                imageInfo = ImageInfo.makeN32Premul(
                    event.size.width,
                    event.size.height,
                    ColorSpace.sRGB,
                ),
                pixelsPtr = softwareDrawData.canvas,
                rowBytes = softwareDrawData.stride,
            ).use { surface ->
                surface.canvas.clear(Color.TRANSPARENT)
                composeScene.render(surface.canvas.asComposeCanvas(), now)
                surface.flushAndSubmit()
            }
            return
        }

        BackendRenderTarget.makeGL(
            width = event.size.width,
            height = event.size.height,
            sampleCnt = 1,
            stencilBits = 8,
            fbId = 0,
            fbFormat = FramebufferFormat.GR_GL_RGBA8,
        ).use { renderTarget ->
            Surface.makeFromBackendRenderTarget(
                context = directContext,
                rt = renderTarget,
                origin = SurfaceOrigin.BOTTOM_LEFT,
                colorFormat = SurfaceColorFormat.RGBA_8888,
                colorSpace = ColorSpace.sRGB,
                surfaceProps = null,
            )!!.use { surface ->
                surface.canvas.clear(Color.TRANSPARENT)
                composeScene.render(surface.canvas.asComposeCanvas(), now)
                surface.flushAndSubmit()
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
        contentState = content
        installSceneContentIfNeeded()
        isFrameRequested = true
    }

    private fun installSceneContentIfNeeded() {
        if (sceneContentInstalled) return
        sceneContentInstalled = true
        val windowScope = object : WindowScope {
            override val window: Window get() = this@LinuxWindow
        }
        composeScene.setContent {
            CompositionLocalProvider(
                LocalSystemTheme provides systemTheme,
                LocalTextInputSessionOwner provides linuxTextInputSessionOwner,
            ) {
                contentState?.invoke(windowScope)
            }
        }
    }

    override fun startInteractiveMove(pointerEvent: PointerEvent) {
        onNativeWindowAsync { startMove() }
    }

    override fun startInteractiveResize(handle: WindowResizeHandle, pointerEvent: PointerEvent) {
        onNativeWindowAsync { startResize(handle.toWindowResizeEdge()) }
    }

    private fun performTitleBarAction(action: DesktopTitlebarAction, pointerEvent: PointerEvent) {
        when (action) {
            DesktopTitlebarAction.ToggleMaximize -> {
                when (placement) {
                    WindowPlacement.Floating,
                    WindowPlacement.Fullscreen,
                        -> requestPlacement(WindowPlacement.Maximized)

                    WindowPlacement.Maximized -> requestPlacement(WindowPlacement.Floating)
                }
            }
            DesktopTitlebarAction.Minimize -> requestMinimized(true)
            DesktopTitlebarAction.None -> Unit
            DesktopTitlebarAction.Menu -> {
                val position = pointerEvent.changes.firstOrNull()?.position ?: Offset.Zero
                onNativeWindowAsync {
                    showMenu(position.toLogicalPoint(density))
                }
            }
        }
    }

    private inline fun onNativeWindowAsync(crossinline block: org.jetbrains.desktop.linux.Window.() -> Unit) {
        if (isDisposed) return
        application.onEventLoopAsync {
            if (!isDisposed) {
                nativeWindow.block()
            }
        }
    }

    private val directContext: DirectContext by lazy {
        val eglProcFunc = checkNotNull(application.nativeApplication.getEglProcFunc()) {
            "Linux KDT did not provide an EGL function table"
        }
        val openGlInterface = GLAssembledInterface.createFromNativePointers(
            ctxPtr = eglProcFunc.ctxPtr,
            fPtr = eglProcFunc.fPtr,
        )
        DirectContext.makeGLWithInterface(openGlInterface)
    }

    private var onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }
    private var onKeyEvent: (KeyEvent) -> Boolean = { false }
    private var contentState by mutableStateOf<(@Composable WindowScope.() -> Unit)?>(null)
    private var sceneContentInstalled = false

    companion object {
        internal fun drawDragIcon(
            event: Event.DragIconDraw,
            dragIconPngBytes: ByteArray?,
        ) {
            val softwareDrawData = event.softwareDrawData ?: return
            val image = dragIconPngBytes?.let(Image::makeFromEncoded) ?: return
            Surface.makeRasterDirect(
                imageInfo = ImageInfo.makeN32Premul(
                    event.size.width,
                    event.size.height,
                    ColorSpace.sRGB,
                ),
                pixelsPtr = softwareDrawData.canvas,
                rowBytes = softwareDrawData.stride,
            ).use { surface ->
                surface.canvas.clear(Color.TRANSPARENT)
                surface.canvas.drawImageRect(
                    image,
                    org.jetbrains.skia.Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                    org.jetbrains.skia.Rect.makeWH(
                        event.size.width.toFloat(),
                        event.size.height.toFloat(),
                    ),
                )
                surface.flushAndSubmit()
            }
        }
    }
}

private fun WindowResizeHandle.toWindowResizeEdge(): WindowResizeEdge = when (this) {
    WindowResizeHandle.LeftBorder -> WindowResizeEdge.Left
    WindowResizeHandle.TopBorder -> WindowResizeEdge.Top
    WindowResizeHandle.RightBorder -> WindowResizeEdge.Right
    WindowResizeHandle.BottomBorder -> WindowResizeEdge.Bottom
    WindowResizeHandle.TopLeftCorner -> WindowResizeEdge.TopLeft
    WindowResizeHandle.TopRightCorner -> WindowResizeEdge.TopRight
    WindowResizeHandle.BottomRightCorner -> WindowResizeEdge.BottomRight
    WindowResizeHandle.BottomLeftCorner -> WindowResizeEdge.BottomLeft
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Pair<List<WindowDecoration.TitleBarElement>, List<WindowDecoration.TitleBarElement>>.forCapabilities(
    capabilities: WindowCapabilities?,
): Pair<List<WindowDecoration.TitleBarElement>, List<WindowDecoration.TitleBarElement>> {
    var layoutLeft = first
    var layoutRight = second

    if (
        WindowDecoration.TitleBarElement.Icon !in layoutLeft &&
        WindowDecoration.TitleBarElement.Icon !in layoutRight
    ) {
        when (WindowDecoration.TitleBarElement.AppMenu) {
            in layoutLeft -> {
                layoutLeft = layoutLeft.map {
                    if (it == WindowDecoration.TitleBarElement.AppMenu) {
                        WindowDecoration.TitleBarElement.Icon
                    } else {
                        it
                    }
                }
            }
            in layoutRight -> {
                layoutRight = layoutRight.map {
                    if (it == WindowDecoration.TitleBarElement.AppMenu) {
                        WindowDecoration.TitleBarElement.Icon
                    } else {
                        it
                    }
                }
            }
            else -> {
                layoutLeft = listOf(WindowDecoration.TitleBarElement.Icon) + layoutLeft
            }
        }
    }

    return layoutLeft.filter { it.isAvailableIn(capabilities) } to
        layoutRight.filter { it.isAvailableIn(capabilities) }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun WindowDecoration.TitleBarElement.isAvailableIn(capabilities: WindowCapabilities?): Boolean =
    when (this) {
        WindowDecoration.TitleBarElement.MinimizeButton -> capabilities?.minimize ?: true
        WindowDecoration.TitleBarElement.MaximizeButton -> capabilities?.maximize ?: true
        else -> true
    }
