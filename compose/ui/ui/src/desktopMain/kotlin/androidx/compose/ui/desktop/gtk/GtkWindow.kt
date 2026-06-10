@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, InternalCoreApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.gtk

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
import androidx.compose.ui.desktop.DefaultCustomTitleBarHeightForAir
import androidx.compose.ui.desktop.InteractiveMoveInitiator
import androidx.compose.ui.desktop.KdtDragAndDropManager
import androidx.compose.ui.desktop.KdtDragAndDropTransferable
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
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
import kotlin.invoke
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import noria.CallbackInterceptor
import org.jetbrains.desktop.gtk.DataSource
import org.jetbrains.desktop.gtk.DragAndDropAction
import org.jetbrains.desktop.gtk.DragAndDropQueryData
import org.jetbrains.desktop.gtk.DragAndDropQueryResponse
import org.jetbrains.desktop.gtk.DragIconParams
import org.jetbrains.desktop.gtk.Event
import org.jetbrains.desktop.gtk.EventHandlerResult
import org.jetbrains.desktop.gtk.LogicalRect
import org.jetbrains.desktop.gtk.LogicalSize
import org.jetbrains.desktop.gtk.RenderingMode
import org.jetbrains.desktop.gtk.RequestId
import org.jetbrains.desktop.gtk.StartDragAndDropParams
import org.jetbrains.desktop.gtk.TextInputSurroundingText
import org.jetbrains.desktop.gtk.WindowDecorationMode
import org.jetbrains.desktop.gtk.WindowParams
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.GLAssembledInterface
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.makeGLWithInterface

class GtkWindow internal constructor(
    private val application: GtkApplication,
    internal val scene: Scene<*>,
    private val onCloseRequest: (WindowCloseRequestReason) -> Unit,
) : InteractiveMoveInitiator {
    private val nativeWindowId = application.allocateNativeWindowId()

    override val nativeWindow: org.jetbrains.desktop.gtk.Window =
        application.onEventLoopSync {
            createWindow(
                WindowParams(
                    windowId = nativeWindowId,
                    title = "",
                    size = LogicalSize(800, 600),
                    minSize = null,
                    decorationMode = WindowDecorationMode.CustomTitlebar(
                        DefaultCustomTitleBarHeightForAir.value.toInt(),
                    ),
                    renderingMode = RenderingMode.Auto,
                ),
            )
        }

    override val id: LightweightWindowId = LightweightWindowId(nativeWindow.windowId)

    @Volatile
    private var isDisposed = false

    private val nativeClosed = CompletableDeferred<Unit>()

    @Volatile
    internal var isFrameRequested = false

    private val fileDialogResponses =
        ConcurrentHashMap<RequestId, CancellableContinuation<List<Path>?>>()

    private var titleField by mutableStateOf("")
    private var overriddenSystemTheme by mutableStateOf<SystemTheme?>(null)

    private var incomingDragMimeTypes: List<String> = emptyList()
    private val incomingDragMimeData = linkedMapOf<String, ByteArray>()

    init {
        application.windows[id] = this
    }

    override var title: String
        get() = titleField
        @MainThread
        set(value) {
            titleField = value
            onNativeWindowAsync { setTitle(value) }
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
        onNativeWindowAsync {
            setMinSize(
                LogicalSize(
                    minSize.width.takeOrElse { this@GtkWindow.minSize.width }.value.toInt(),
                    minSize.height.takeOrElse { this@GtkWindow.minSize.height }.value.toInt(),
                ),
            )
        }
    }

    private val maxSizeState = mutableStateOf(DpSize(7680.dp, 4320.dp))
    override val maxSize: DpSize
        get() = maxSizeState.value

    override fun requestMaxSize(maxSize: DpSize) {
        maxSizeState.value = maxSize
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
        onNativeWindowAsync { activate(null) }
    }

    override fun requestBringToFront() {
        onNativeWindowAsync { activate(null) }
    }

    override fun requestFocusAndBringToFront() {
        onNativeWindowAsync { activate(null) }
    }

    @ExperimentalComposeUiApi
    override var decoration: WindowDecoration by mutableStateOf(
        WindowDecoration.CustomTitleBar(
            DefaultCustomTitleBarHeightForAir,
        ),
    )
        private set

    @ExperimentalComposeUiApi
    override fun requestDecoration(vararg decorations: WindowDecoration) {
        // TODO
    }

    override var customTitleBarInsets: Pair<Dp, Dp>? by mutableStateOf(null)
        private set

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

    override var screen: GtkScreen by mutableStateOf(
        application.screens.values.firstOrNull()
            ?: GtkScreen(application.onEventLoopSync { allScreens().screens.first() }),
    )
        private set

    override var density: Density by mutableStateOf(screen.density)
        private set

    private val pointerIconService = GtkPointerIconService(application, nativeWindow)
    private val inputModeManager = InputModeManagerImpl(InputMode.Touch) {
        pointerIconService.setHiddenUntilPointerMoves(it == InputMode.Keyboard)
        true
    }
    internal val dragAndDropManager: GtkDragAndDropManager = GtkDragAndDropManager(
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

    private val gtkTextInputSessionOwner = GtkTextInputSessionOwner(
        startInputMethod = { context -> onNativeWindowAsync { textInputEnable(context) } },
        stopInputMethod = { onNativeWindowAsync { textInputDisable() } },
        onDataChanged = { context -> onNativeWindowAsync { textInputUpdate(context) } },
    )

    private val platformContext: PlatformContext = object : PlatformContext by PlatformContext.Empty() {
        override val windowInfo: WindowInfo
            get() = this@GtkWindow.windowInfo
        override val viewConfiguration: ViewConfiguration
            get() = this@GtkWindow.viewConfiguration
        override val inputModeManager: InputModeManager
            get() = this@GtkWindow.inputModeManager
        override val architectureComponentsOwner = this@GtkWindow.architectureComponentsOwner
        override val textToolbar = DefaultTextToolbar()
        override val dragAndDropManager: PlatformDragAndDropManager =
            KdtDragAndDropManager(this@GtkWindow)

        override fun textInputSessionOwner() = gtkTextInputSessionOwner
    }

    private val composeScene: ComposeScene = CanvasLayersComposeScene(
        density = density,
        layoutDirection = layoutDirection,
        size = contentSizeInPx(),
        coroutineContext = scene.coroutineScope.coroutineContext +
            GtkKdtMainDispatcher.INSTANCE.immediate,
        platformContext = platformContext,
        invalidate = { isFrameRequested = true },
    )

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
            isFocused = false
            fileDialogResponses.values.forEach { it.cancel() }
            fileDialogResponses.clear()
            composeScene.close()
            architectureComponentsOwner.setLifecycleState(Lifecycle.State.DESTROYED)
            application.onEventLoopAsync {
                nativeWindow.close()
            }
        }
    }

    internal suspend fun awaitNativeClosed() {
        nativeClosed.await()
    }

    internal fun activate(token: String?) {
        onNativeWindowAsync { activate(token) }
    }

    override fun requestClose(reason: WindowCloseRequestReason) {
        if (!isDisposed) {
            onCloseRequest(reason)
        }
    }

    internal fun requestCloseFromSystem() {
        scene.withPreparedMainThread {
            onCloseRequest(WindowCloseRequestReason.UserRequest)
        }
    }

    internal fun currentTextInputSurroundingText(): TextInputSurroundingText? =
        gtkTextInputSessionOwner.currentTextInputSurroundingText

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
                customTitleBarInsets = if (
                    event.insetStart.width == 0 && event.insetEnd.width == 0
                ) {
                    null
                } else {
                    event.insetStart.width.dp to event.insetEnd.width.dp
                }
                decoration = when (val nativeDecoration = event.decorationMode) {
                    WindowDecorationMode.Server -> WindowDecoration.Decorated
                    is WindowDecorationMode.CustomTitlebar ->
                        WindowDecoration.CustomTitleBar(nativeDecoration.height.dp)
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
                // GTK text input is window-level; re-arm it when keyboard focus returns.
                if (gtkTextInputSessionOwner.isTextInputSessionActive()) {
                    gtkTextInputSessionOwner.currentContext?.let { nativeWindow.textInputEnable(it) }
                }
                inputStateTracker.updateStateAndSendEvents(event, density)
            }

            is Event.WindowKeyboardLeave -> {
                isFocused = false
                if (gtkTextInputSessionOwner.isTextInputSessionActive() &&
                    gtkTextInputSessionOwner.hasPreeditString
                ) {
                    nativeWindow.textInputDisable()
                }
                inputStateTracker.updateStateAndSendEvents(event, density)
            }

            is Event.MouseDown -> {
                // A mouse-down inside an active preedit must cancel composition and re-arm the IME.
                if (gtkTextInputSessionOwner.isTextInputSessionActive() &&
                    gtkTextInputSessionOwner.hasPreeditString
                ) {
                    gtkTextInputSessionOwner.currentContext?.let {
                        nativeWindow.textInputDisable()
                        nativeWindow.textInputEnable(it)
                    }
                }
                inputStateTracker.updateStateAndSendEvents(event, density)
            }

            is Event.TextInput -> {
                gtkTextInputSessionOwner.handleTextInputEvent(
                    event.preeditStringData,
                    event.commitStringData,
                    event.deleteSurroundingTextData,
                )
                EventHandlerResult.Stop
            }

            is Event.MouseUp,
            is Event.MouseMoved,
            is Event.MouseEntered,
            is Event.MouseExited,
            is Event.ScrollWheel,
            is Event.KeyDown,
            is Event.KeyUp,
            is Event.ModifiersChanged,
                -> inputStateTracker.updateStateAndSendEvents(event, density)

            is Event.WindowFrameTick -> {
                if (isFrameRequested) {
                    onNativeWindowAsync { requestRedraw() }
                }
                EventHandlerResult.Stop
            }

            is Event.WindowDraw -> {
                if (isDisposed) {
                    return EventHandlerResult.Continue
                }
                isFrameRequested = false
                draw(event)
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

            is Event.DragAndDropFeedbackFinished -> EventHandlerResult.Stop

            is Event.WindowClosed -> {
                if (!isDisposed) {
                    // When dispose() was called first, it already ran this cleanup; skip it here.
                    isDisposed = true
                    isFocused = false
                    fileDialogResponses.values.forEach { it.cancel() }
                    fileDialogResponses.clear()
                }
                application.windows.remove(id)
                nativeClosed.complete(Unit)
                if (application.windows.isEmpty()) {
                    application.finishStructuredQuitIfNeeded()
                }
                EventHandlerResult.Stop
            }

            else -> EventHandlerResult.Continue
        }
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
        val clipEntry = (transferData.transferable as? KdtDragAndDropTransferable)
            ?.clipboardEntry ?: return
        val itemsEntry = clipEntry.nativeClipEntry as? ClipboardItemsEntry ?: return
        val mimeTypes = itemsEntry.linuxMimeTypes()
        val supportedActions = transferData.supportedActions.toSet()
        val dragImageBytes = DragAndDropImage(
            size = decorationSize,
            density = density,
            layoutDirection = layoutDirection,
            drawDragDecoration = drawDragDecoration,
        ).encodeToPngBytes()

        application.activeDragSource = GtkApplication.ActiveDragSource(
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
                    actions = supportedActions.toGtkActions(),
                    dragIconParams = DragIconParams(
                        renderingMode = RenderingMode.Auto,
                        size = decorationSize.toLogicalSize(density),
                    ),
                ),
            )
        }
    }

    private fun Iterable<DragAndDropTransferAction>.toGtkActions(): Set<DragAndDropAction> {
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
        BackendRenderTarget.makeGL(
            width = event.size.width,
            height = event.size.height,
            sampleCnt = 1,
            stencilBits = 0,
            fbId = event.openGlDrawData.framebuffer,
            fbFormat = FramebufferFormat.GR_GL_RGBA8,
        ).use { renderTarget ->
            Surface.makeFromBackendRenderTarget(
                context = directContext,
                rt = renderTarget,
                origin = SurfaceOrigin.TOP_LEFT,
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
            override val window: Window get() = this@GtkWindow
        }
        composeScene.setContent {
            CompositionLocalProvider(
                LocalSystemTheme provides systemTheme,
                LocalTextInputSessionOwner provides gtkTextInputSessionOwner,
            ) {
                contentState?.invoke(windowScope)
            }
        }
    }

    override fun startInteractiveMove(pointerEvent: PointerEvent): Unit = Unit

    private inline fun onNativeWindowAsync(crossinline block: org.jetbrains.desktop.gtk.Window.() -> Unit) {
        if (isDisposed) return
        application.onEventLoopAsync {
            if (!isDisposed) {
                nativeWindow.block()
            }
        }
    }

    private val directContext: DirectContext by lazy {
        val openGlInterface = GLAssembledInterface.createFromNativePointers(
            ctxPtr = application.glProcFunc.ctxPtr,
            fPtr = application.glProcFunc.fPtr,
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
            glProcFunc: org.jetbrains.desktop.gtk.Application.GlProcFunc,
        ) {
            val image = dragIconPngBytes?.let(Image::makeFromEncoded) ?: return
            val openGlInterface = GLAssembledInterface.createFromNativePointers(
                ctxPtr = glProcFunc.ctxPtr,
                fPtr = glProcFunc.fPtr,
            )
            DirectContext.makeGLWithInterface(openGlInterface).use { directContext ->
                BackendRenderTarget.makeGL(
                    width = event.size.width,
                    height = event.size.height,
                    sampleCnt = 1,
                    stencilBits = 0,
                    fbId = event.openGlDrawData.framebuffer,
                    fbFormat = FramebufferFormat.GR_GL_RGBA8,
                ).use { renderTarget ->
                    Surface.makeFromBackendRenderTarget(
                        context = directContext,
                        rt = renderTarget,
                        origin = SurfaceOrigin.TOP_LEFT,
                        colorFormat = SurfaceColorFormat.RGBA_8888,
                        colorSpace = ColorSpace.sRGB,
                        surfaceProps = null,
                    )!!.use { surface ->
                        surface.canvas.clear(Color.TRANSPARENT)
                        surface.canvas.drawImageRect(
                            image,
                            org.jetbrains.skia.Rect.makeWH(
                                image.width.toFloat(),
                                image.height.toFloat(),
                            ),
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
    }
}

