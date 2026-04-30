@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.gtk

import androidx.annotation.MainThread
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.ComposeTextInputSession
import androidx.compose.ui.desktop.DefaultCustomTitleBarHeightForAir
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.InteractiveMoveInitiator
import androidx.compose.ui.desktop.KdtDragAndDropManager
import androidx.compose.ui.desktop.KdtDragAndDropTransferable
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.MimeTransferClipboardEntry
import androidx.compose.ui.desktop.encodeClipboardItemsToMimeData
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.WindowScope
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
import kotlinx.coroutines.CompletableDeferred
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
import org.jetbrains.desktop.gtk.TextInputContentPurpose
import org.jetbrains.desktop.gtk.TextInputContext as GtkTextInputContext
import org.jetbrains.desktop.gtk.TextInputSurroundingText
import org.jetbrains.desktop.gtk.WindowDecorationMode
import org.jetbrains.desktop.gtk.WindowParams
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
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
) : InteractiveMoveInitiator, LayoutOwner {
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

    private var drawContent: (Canvas.() -> Unit)? = null
    private val fileDialogResponses =
        ConcurrentHashMap<RequestId, CancellableContinuation<List<Path>?>>()

    private var titleField by mutableStateOf("")
    private var overriddenSystemTheme by mutableStateOf<SystemTheme?>(null)

    @Volatile
    private var currentTextInputSession: ComposeTextInputSession? = null

    @Volatile
    private var nativeTextInputEnabled = false
    private var incomingDragMimeTypes: List<String> = emptyList()
    private val incomingDragMimeData = linkedMapOf<String, ByteArray>()

    init {
        application.windows += id to this
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
    }
    private val dragAndDropOwner = DragAndDropOwner(KdtDragAndDropManager(this))
    internal var dragAndDropManager: GtkDragAndDropManager? = null

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
        if (!isDisposed) {
            isDisposed = true
            isFocused = false
            fileDialogResponses.values.forEach { it.cancel() }
            fileDialogResponses.clear()
            application.onEventLoopAsync {
                nativeWindow.close()
            }
        }
    }

    internal suspend fun awaitNativeClosed() {
        nativeClosed.await()
    }

    internal fun requestFrame() {
        onNativeWindowAsync { requestRedraw() }
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

    internal fun currentTextInputSurroundingText(): TextInputSurroundingText? {
        val session = currentTextInputSession ?: return null
        val value = session.value
        val cursorOffset = value.selection.start.coerceIn(0, value.text.length)
        val selectionOffset = value.selection.end.coerceIn(0, value.text.length)
        return TextInputSurroundingText(
            surroundingText = value.text,
            cursorCodepointOffset = value.text.codePointCount(0, cursorOffset).toUShort(),
            selectionStartCodepointOffset = value.text.codePointCount(0, selectionOffset)
                .toUShort(),
        )
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
                updateNativeTextInputState()
                inputStateTracker.updateStateAndSendEvents(event, density)
            }

            is Event.WindowKeyboardLeave -> {
                isFocused = false
                updateNativeTextInputState()
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

            is Event.TextInput -> {
                handleTextInput(event)
                EventHandlerResult.Stop
            }

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

            is Event.DragAndDropFeedbackFinished -> EventHandlerResult.Stop

            is Event.WindowClosed -> {
                if (!isDisposed) {
                    // When dispose() was called first, it already ran this cleanup; skip it here.
                    isDisposed = true
                    isFocused = false
                    fileDialogResponses.values.forEach { it.cancel() }
                    fileDialogResponses.clear()
                }
                application.windows -= id
                nativeClosed.complete(Unit)
                if (application.windows.isEmpty()) {
                    application.finishStructuredQuitIfNeeded()
                }
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
        if (application.isEventLoopThread()) {
            updateNativeTextInputState()
        } else {
            application.onEventLoopAsync {
                updateNativeTextInputState()
            }
        }
    }

    private fun updateNativeTextInputState() {
        val session = currentTextInputSession
        val shouldEnable = !isDisposed && session != null && isFocused
        when {
            shouldEnable && !nativeTextInputEnabled -> {
                nativeWindow.textInputEnable(session.toNativeContext())
                nativeTextInputEnabled = true
            }

            shouldEnable -> {
                nativeWindow.textInputUpdate(session.toNativeContext())
            }

            nativeTextInputEnabled -> {
                nativeWindow.textInputDisable()
                nativeTextInputEnabled = false
            }
        }
    }

    private fun handleTextInput(event: Event.TextInput) {
        val session = currentTextInputSession ?: return
        val preeditStringData = event.preeditStringData
        val composingText = preeditStringData?.text
        if (!composingText.isNullOrEmpty()) {
            val selection = preeditStringData.cursorBytePos
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

    private fun ComposeTextInputSession.toNativeContext(): GtkTextInputContext {
        val focusedRect = focusedRectInRoot ?: Rect.Zero
        val scale = density.density
        return GtkTextInputContext(
            hints = emptySet(),
            contentPurpose = TextInputContentPurpose.Normal,
            cursorRectangle = LogicalRect(
                (focusedRect.left / scale).roundToInt(),
                (focusedRect.top / scale).roundToInt(),
                (focusedRect.width / scale).roundToInt(),
                (focusedRect.height / scale).roundToInt(),
            ),
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
                                    val windowScope = remember(this@GtkWindow) {
                                        object : WindowScope {
                                            override val window: Window
                                                get() = this@GtkWindow
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

                            rememberCoroutineScope().launch {
                                inputStateTracker.sendPointerInputEventWithCurrentStateIfNecessary(
                                    if (inputModeManager.inputMode == InputMode.Touch) {
                                        PointerEventType.Move
                                    } else {
                                        PointerEventType.Exit
                                    },
                                )
                            }

                            onLayout(WindowData(id, uiRootCell.read(), latestRootLayoutNode!!))

                            val callbackInterceptor = CallbackInterceptorCompositionLocal.current
                            LaunchedEffect(dragAndDropOwner, density, callbackInterceptor) {
                                dragAndDropManager = GtkDragAndDropManager(
                                    rootDragAndDropNode = ComposeSceneDragAndDropNode { dragAndDropOwner },
                                    density = density,
                                    callbackInterceptor = callbackInterceptor,
                                    currentDragClipboardEntry = ::currentDragClipboardEntry,
                                    currentMimeTypes = ::currentDragMimeTypes,
                                )
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
        return incomingDragMimeTypes.ifEmpty {
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

        application.activeDragSource = GtkApplication.ActiveDragSource(
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

private fun String.offset8to16(offset8: Int): Int {
    val utf8Bytes = toByteArray(Charsets.UTF_8)
    return utf8Bytes
        .copyOfRange(0, offset8.coerceIn(0, utf8Bytes.size))
        .decodeToString()
        .length
}
