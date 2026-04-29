@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.linux

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
import androidx.compose.ui.draganddrop.DragAndDropImage
import androidx.compose.ui.draganddrop.DragAndDropOwner
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferDataJvm
import androidx.compose.ui.draganddrop.LocalDragAndDropManager
import androidx.compose.ui.focus.FocusOwnerImpl
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.renderWithLayerScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.InputMode
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
import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.ComposeTextInputSession
import androidx.compose.ui.desktop.InteractiveMoveInitiator
import androidx.compose.ui.desktop.InteractiveResizeInitiator
import androidx.compose.ui.desktop.KdtDragAndDropManager
import androidx.compose.ui.desktop.KdtDragAndDropTransferable
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.MimeTransferClipboardEntry
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.WindowResizeHandle
import androidx.compose.ui.desktop.WindowScope
import androidx.compose.ui.desktop.encodeClipboardItemsToMimeData
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.platform.DefaultTextToolbar
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalPointerIconService
import androidx.compose.ui.platform.LocalTextInputContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextInputContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import fleet.fastutil.ints.Int2ObjectOpenHashMap
import fleet.reporting.shared.tracing.span
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import noria.CallbackInterceptorCompositionLocal
import noria.ID
import noria.activeCell
import noria.cell
import noria.currentNoriaContext
import noria.impl.NoriaState
import noria.memo
import noria.ui.core.LocalWindow
import noria.ui.core.WindowData
import noria.ui.core.uiRoot
import noria.ui.draw.internal.RenderContext
import noria.ui.input.pointer.NoriaPointerInputEventProcessor
import noria.ui.input.pointer.PositionCalculator
import noria.ui.input.pointer.ProcessResult
import noria.ui.layout.internal.DebugLocation
import noria.ui.layout.internal.LayoutNode
import noria.ui.layout.internal.LayoutOwner
import noria.ui.layout.internal.LayoutScope
import noria.ui.layout.internal.rememberEmptyLayoutBuilder
import noria.ui.layout.internal.withLayoutBuilderStack
import org.jetbrains.annotations.ApiStatus
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
import org.jetbrains.desktop.linux.TextInputContentHint
import org.jetbrains.desktop.linux.TextInputContentPurpose
import org.jetbrains.desktop.linux.TextInputContext as LinuxTextInputContext
import org.jetbrains.desktop.linux.WindowCapabilities
import org.jetbrains.desktop.linux.WindowDecorationMode
import org.jetbrains.desktop.linux.WindowParams
import org.jetbrains.desktop.linux.WindowResizeEdge
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
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
) : InteractiveMoveInitiator, InteractiveResizeInitiator, LayoutOwner {
    private val nativeWindowId = application.allocateNativeWindowId()

    override val nativeWindow: org.jetbrains.desktop.linux.Window =
        application.nativeApplication.createWindow(
            WindowParams(
                windowId = nativeWindowId,
                appId = application.identifier,
                title = "",
                size = LogicalSize(800U, 600U),
                preferClientSideDecoration = true,
                renderingMode = RenderingMode.Auto,
            ),
        )

    override val id: LightweightWindowId = LightweightWindowId(nativeWindow.windowId)

    @Volatile
    private var isDisposed = false

    @Volatile
    internal var isFrameRequested = false

    private var drawContent: (Canvas.() -> Unit)? = null
    private val fileDialogResponses =
        ConcurrentHashMap<RequestId, CancellableContinuation<List<Path>?>>()

    private var titleField by mutableStateOf("")
    private var overriddenSystemTheme by mutableStateOf<SystemTheme?>(null)
    private var textInputAvailable by mutableStateOf(false)
    private var windowCapabilities by mutableStateOf(
        WindowCapabilities(
            windowMenu = true,
            maximize = true,
            fullscreen = true,
            minimize = true,
        ),
    )

    @Volatile
    private var currentTextInputSession: ComposeTextInputSession? = null

    @Volatile
    private var nativeTextInputEnabled = false
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
                        minSize.width.takeOrElse { this@LinuxWindow.minSize.width }.value.roundToInt().toUInt(),
                        minSize.height.takeOrElse { this@LinuxWindow.minSize.height }.value.roundToInt()
                            .toUInt(),
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
                    maxSize.width.takeOrElse { this@LinuxWindow.maxSize.width }.value.roundToInt().toUInt(),
                    maxSize.height.takeOrElse { this@LinuxWindow.maxSize.height }.value.roundToInt().toUInt(),
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
    }
    private val dragAndDropOwner = DragAndDropOwner(KdtDragAndDropManager(this))
    internal var dragAndDropManager: LinuxDragAndDropManager? = null

    private var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)

    val viewConfiguration: ViewConfiguration = object :
        ViewConfiguration {
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

        override fun requestWindowFocus() {
            requestFocus()
        }

        @OptIn(InternalCoreApi::class)
        override val keyboardModifiers: PointerKeyboardModifiers
            get() = inputStateTracker.keyboardModifiers

        @ExperimentalComposeUiApi
        override val containerSize: IntSize
            get() = contentSize.run {
                density.run {
                    IntSize(width.roundToPx(), height.roundToPx())
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
            drawContent?.let { content ->
                surface.canvas.clear(Color.TRANSPARENT)
                surface.canvas.content()
            }
            surface.makeImageSnapshot().toComposeImageBitmap()
        }
    }

    override fun dispose() {
        onNativeWindowAsync { close() }
    }

    internal fun onClosed() {
        isDisposed = true
        fileDialogResponses.values.forEach { it.cancel() }
        fileDialogResponses.clear()
        if (application.windows.isEmpty()) {
            application.finishStructuredQuitIfNeeded()
        }
    }

    internal fun requestFrame() = Unit

    internal fun activate(token: String) {
        onNativeWindowAsync { activate(token) }
    }

    internal fun queryDragAndDropTarget(query: DragAndDropQueryData): DragAndDropQueryResponse {
        return dragAndDropManager?.onQuery(query) ?: DragAndDropQueryResponse(emptyList())
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

    @OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
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
                EventHandlerResult.Stop
            }

            is Event.WindowScaleChanged -> {
                density = Density(event.newScale.toFloat())
                dragAndDropManager?.updateDensity(density)
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

            is Event.TextInputAvailability -> {
                textInputAvailable = event.available
                updateNativeTextInputState()
                EventHandlerResult.Stop
            }

            is Event.TextInput -> {
                handleTextInput(event)
                EventHandlerResult.Stop
            }

            is Event.WindowDraw -> {
                if (isDisposed) {
                    return EventHandlerResult.Continue
                }
                isFrameRequested = false
                scene.withPreparedMainThread {
                    application.withoutReentrancy {
                        scene.reconcile()
                    }
                }
                drawContent?.let { content ->
                    draw(event, content)
                }
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
                dragAndDropManager?.onDrop(event)
                incomingDragMimeTypes = emptyList()
                incomingDragMimeData.clear()
                EventHandlerResult.Stop
            }

            is Event.DragAndDropLeave -> {
                dragAndDropManager?.onLeave()
                incomingDragMimeTypes = emptyList()
                incomingDragMimeData.clear()
                EventHandlerResult.Stop
            }

            else -> EventHandlerResult.Continue
        }
    }

    @OptIn(InternalCoreApi::class)
    private val textInputContext: TextInputContext = object : TextInputContext {
        override fun handleKeyEvent(event: KeyEvent): Boolean {
            val nativeEvent =
                (event.nativeKeyEvent as? InternalKeyEvent)?.nativeEvent as? Event.KeyDown
            val characters = nativeEvent?.characters?.takeIf { it.isNotEmpty() }
            val session = currentTextInputSession ?: return false
            return if (
                event.type == KeyEventType.KeyDown &&
                characters != null &&
                !event.isMetaPressed &&
                (!event.isCtrlPressed && !event.isAltPressed || event.isAltPressed && event.isCtrlPressed)
            ) {
                session.commitText(characters)
                true
            } else {
                false
            }
        }
    }

    private fun scheduleNativeTextInputStateUpdate() {
        if (application.nativeApplication.isEventLoopThread()) {
            updateNativeTextInputState()
        } else {
            application.nativeApplication.runOnEventLoopAsync {
                updateNativeTextInputState()
            }
        }
    }

    private fun updateNativeTextInputState() {
        scene.withPreparedMainThread {
            val session = currentTextInputSession
            val shouldEnable = !isDisposed && session != null && textInputAvailable
            when {
                shouldEnable && !nativeTextInputEnabled -> {
                    application.nativeApplication.textInputEnable(session.toNativeContext())
                    nativeTextInputEnabled = true
                }

                shouldEnable -> {
                    application.nativeApplication.textInputUpdate(session.toNativeContext())
                }

                nativeTextInputEnabled -> {
                    application.nativeApplication.textInputDisable()
                    nativeTextInputEnabled = false
                }
            }
        }
    }

    private fun handleTextInput(event: Event.TextInput) {
        val session = currentTextInputSession ?: return
        scene.withPreparedMainThread {
            val preeditStringData = event.preeditStringData
            val composingText = preeditStringData?.text
            if (!composingText.isNullOrEmpty()) {
                val selection = preeditStringData.cursorBeginBytePos
                    .takeIf { it >= 0 }
                    ?.let { TextRange(composingText.offset8to16(it)) }
                session.setComposingText(composingText, selection)
            } else {
                session.finishComposingText()
            }
            event.commitStringData?.text
                ?.takeIf(String::isNotEmpty)
                ?.let(session::commitText)
        }
    }

    private fun ComposeTextInputSession.toNativeContext(): LinuxTextInputContext {
        val value = value
        val cursorOffset = value.selection.start.coerceIn(0, value.text.length)
        val selectionOffset = value.selection.end.coerceIn(0, value.text.length)
        val focusedRect = focusedRectInRoot ?: Rect.Zero
        return LinuxTextInputContext(
            surroundingText = value.text,
            cursorCodepointOffset = value.text.codePointCount(0, cursorOffset).toUShort(),
            selectionStartCodepointOffset = value.text.codePointCount(0, selectionOffset).toUShort(),
            hints = buildSet {
              if (!isSingleLine) {
                add(TextInputContentHint.Multiline)
              }
            },
            contentPurpose = keyboardType.toTextInputContentPurpose(),
            cursorRectangle = focusedRect.toLogicalRect(density),
            changeCausedByInputMethod = false,
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private val platformTextInputInterceptor = PlatformTextInputInterceptor { request, _ ->
        val session = ComposeTextInputSession(request, scene)
        currentTextInputSession = session
        scheduleNativeTextInputStateUpdate()
        try {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    if (currentTextInputSession === session) {
                        currentTextInputSession = null
                    }
                    scheduleNativeTextInputStateUpdate()
                }
            }
        } finally {
            if (currentTextInputSession === session) {
                currentTextInputSession = null
            }
            scheduleNativeTextInputStateUpdate()
        }
    }

    internal var latestRootLayoutNode: LayoutNode? = null
        set(value) {
            field?.let {
                if (it.owner != null && value?.id != it.id) {
                    it.detach()
                }
            }
            if (value != null && value.owner != this) {
                value.attach(this)
            }
            field = value
        }

    override fun onAttach(node: LayoutNode) {
        if (node.id != ID.NULL) {
            latestLayoutNodes[node.id.id] = node
        }
    }

    override fun get(id: ID): LayoutNode? = latestLayoutNodes[id.id]

    override fun onDetach(node: LayoutNode) {
        latestLayoutNodes.remove(node.id.id)
    }

    override fun calculatePositionInWindow(localPosition: Offset): Offset = localPosition

    override fun calculateLocalPosition(positionInWindow: Offset): Offset = positionInWindow

    @OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
    @Composable
    @ApiStatus.Internal
    override fun Content(onLayout: (WindowData) -> Unit) {
        CompositionLocalProvider(
            LocalSystemTheme provides systemTheme,
            LocalDensity provides density,
            LocalFocusManager provides focusOwner,
            LocalLayoutDirection provides layoutDirection,
            LocalPointerIconService provides pointerIconService,
            LocalInputModeManager provides inputModeManager,
            LocalTextInputContext provides textInputContext,
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
                                    val windowScope = remember(this@LinuxWindow) {
                                        object : WindowScope {
                                            override val window: Window
                                                get() = this@LinuxWindow
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
                                            node.place(IntOffset.Zero)
                                        }
                                        .node
                                }
                            }.also {
                                dragAndDropOwner.updateCoordinates(it.coordinates)
                            }

                            /**
                             * Refresh hit testing under a stationary pointer after relayout. We queue this
                             * onto the effect dispatcher so new [SuspendingPointerInputFilter]s are already
                             * waiting for events, and cancel stale refreshes as soon as a real input event
                             * arrives.
                             */
                            rememberCoroutineScope().launch {
                                inputStateTracker
                                    .prepareSyntheticPointerEventAfterRelayoutIfNecessary()
                                    ?.let(inputStateTracker::sendSyntheticPointerEventAfterRelayoutIfCurrent)
                            }

                            onLayout(WindowData(id, uiRootCell.read(), latestRootLayoutNode!!))

                            val callbackInterceptor = CallbackInterceptorCompositionLocal.current
                            DisposableEffect(dragAndDropOwner, density, callbackInterceptor) {
                                dragAndDropManager = LinuxDragAndDropManager(
                                    rootDragAndDropNode = ComposeSceneDragAndDropNode { dragAndDropOwner },
                                    density = density,
                                    callbackInterceptor = callbackInterceptor,
                                    currentDragClipboardEntry = ::currentDragClipboardEntry,
                                    currentMimeTypes = ::currentDragMimeTypes,
                                )

                                onDispose {
                                    dragAndDropManager = null
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

    private fun currentDragMimeTypes(): List<String> {
        return if (incomingDragMimeTypes.isNotEmpty()) {
            incomingDragMimeTypes
        } else {
            application.activeDragSource?.mimeData?.keys?.toList().orEmpty()
        }
    }

    private fun currentDragClipboardEntry(): ClipboardEntry {
        return MimeTransferClipboardEntry {
            buildMap {
                application.activeDragSource?.mimeData?.let(::putAll)
                putAll(incomingDragMimeData)
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
    private val inputStateTracker: InputStateTracker = InputStateTracker(
        inputModeManager = inputModeManager,
        sendPointerInputEvent = { pointerInputEvent ->
            latestRootLayoutNode?.let { rootLayoutNode ->
                scene.withPreparedMainThread {
                    pointerInputEventProcessor.process(
                        pointerInputEvent,
                        rootLayoutNode,
                        positionCalculator,
                    )
                }
            } ?: ProcessResult(0)
        },
        sendKeyEvent = { keyEvent ->
            scene.withPreparedMainThread {
                val previewHandled = onPreviewKeyEventState.value(keyEvent)
                val focusHandled = !previewHandled && focusOwner.dispatchKeyEvent(keyEvent)
                val keyEventHandled =
                    !previewHandled && !focusHandled && onKeyEventState.value(keyEvent)
                previewHandled || focusHandled || keyEventHandled
            }
        },
    )

    internal fun startDragSession(
        offset: Offset,
        transferData: DragAndDropTransferDataJvm,
        decorationSize: Size,
        drawDragDecoration: DrawScope.() -> Unit,
    ) {
        val clipEntry =
            (transferData.transferable as? KdtDragAndDropTransferable)?.clipboardEntry ?: return
        val itemsEntry = clipEntry.nativeClipEntry as? ClipboardItemsEntry ?: return
        val mimeData = encodeClipboardItemsToMimeData(itemsEntry.items)
        val supportedActions = transferData.supportedActions.toSet()
        val dragImageBytes = DragAndDropImage(
            size = decorationSize,
            density = density,
            layoutDirection = layoutDirection,
            drawDragDecoration = drawDragDecoration,
        ).encodeToPngBytes()

        application.activeDragSource = LinuxApplication.ActiveDragSource(
            windowId = id,
            mimeData = mimeData,
            supportedActions = supportedActions,
            onTransferCompleted = { action -> transferData.onTransferCompleted?.invoke(action) },
            dragIconPngBytes = dragImageBytes,
        )
        onNativeWindowAsync {
            startDragAndDrop(
                StartDragAndDropParams(
                    mimeTypes = mimeData.keys.toList(),
                    actions = supportedActions.toLinuxActions(),
                    dragIconParams = DragIconParams(
                        renderingMode = RenderingMode.Software,
                        size = decorationSize.toLogicalSize(density),
                    ),
                ),
            )
        }
    }

    private fun Iterable<DragAndDropTransferAction>.toLinuxActions(): Set<DragAndDropAction> {
        val supportedActions = toSet()
        return buildSet {
            if (DragAndDropTransferAction.Copy in supportedActions || supportedActions.isEmpty()) {
                add(DragAndDropAction.Copy)
            }
            if (DragAndDropTransferAction.Move in supportedActions) {
                add(DragAndDropAction.Move)
            }
        }
    }

    private fun draw(event: Event.WindowDraw, content: Canvas.() -> Unit) {
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
                surface.canvas.content()
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
                surface.canvas.content()
                surface.flushAndSubmit()
            }
        }
    }

    override fun setContent(
        onPreviewKeyEvent: (KeyEvent) -> Boolean,
        onKeyEvent: (KeyEvent) -> Boolean,
        noriaState: NoriaState?,
        content: @Composable WindowScope.() -> Unit,
    ) {
        onPreviewKeyEventState.setValueAndScheduleDependantsRightAway(noriaState, onPreviewKeyEvent)
        onKeyEventState.setValueAndScheduleDependantsRightAway(noriaState, onKeyEvent)
        contentState.setValueAndScheduleDependantsRightAway(noriaState, content)
    }

    override fun startInteractiveMove(pointerEvent: PointerEvent) {
        onNativeWindowAsync {
            startMove()
        }
    }

    override fun startInteractiveResize(handle: WindowResizeHandle, pointerEvent: PointerEvent) {
        onNativeWindowAsync {
            startResize(handle.toWindowResizeEdge())
        }
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

    private val latestLayoutNodes = Int2ObjectOpenHashMap<noria.ui.node.LayoutNode>()
    private val pointerInputEventProcessor =
        NoriaPointerInputEventProcessor { latestLayoutNodes[it.id] }
    private val positionCalculator = object : PositionCalculator {
        override fun screenToLocal(positionOnScreen: Offset): Offset = positionOnScreen
        override fun localToScreen(localPosition: Offset): Offset = localPosition
        override val rootPositionOnScreen: Offset
            get() = Offset.Zero
    }
    private val focusOwner =
        FocusOwnerImpl({ scene.coroutineScope }, DebugLocation(this::class)) { isFocused }
    private val onPreviewKeyEventState = SnapshotMutableStateImpl<(KeyEvent) -> Boolean>({ false })
    private val onKeyEventState = SnapshotMutableStateImpl<(KeyEvent) -> Boolean>({ false })
    private var contentState = SnapshotMutableStateImpl<(@Composable WindowScope.() -> Unit)?>(null)

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

private fun String.offset8to16(offset8: Int): Int {
    val utf8Bytes = toByteArray(Charsets.UTF_8)
    return utf8Bytes
        .copyOfRange(0, offset8.coerceIn(0, utf8Bytes.size))
        .decodeToString()
        .length
}

private fun KeyboardType.toTextInputContentPurpose(): TextInputContentPurpose {
    return when (this) {
        KeyboardType.Unspecified -> TextInputContentPurpose.Normal
        KeyboardType.Text -> TextInputContentPurpose.Normal
        KeyboardType.Uri -> TextInputContentPurpose.Url
        KeyboardType.Email -> TextInputContentPurpose.Email
        KeyboardType.Number -> TextInputContentPurpose.Digits
        KeyboardType.Decimal -> TextInputContentPurpose.Number
        KeyboardType.Phone -> TextInputContentPurpose.Phone
        KeyboardType.Password -> TextInputContentPurpose.Password
        KeyboardType.NumberPassword -> TextInputContentPurpose.Pin
        else -> TextInputContentPurpose.Normal
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
    capabilities: WindowCapabilities,
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
private fun WindowDecoration.TitleBarElement.isAvailableIn(capabilities: WindowCapabilities): Boolean =
    when (this) {
        WindowDecoration.TitleBarElement.MinimizeButton -> capabilities.minimize
        WindowDecoration.TitleBarElement.MaximizeButton -> capabilities.maximize
        else -> true
    }
