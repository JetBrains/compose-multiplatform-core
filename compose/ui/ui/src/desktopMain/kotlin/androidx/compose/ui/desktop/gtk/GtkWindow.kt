@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, InternalCoreApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.desktop.KdtMainDispatcher
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.desktop.LocalWindow
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.WindowData
import androidx.compose.ui.desktop.WindowScope
import androidx.compose.ui.desktop.draganddrop.DragAndDropImage
import androidx.compose.ui.desktop.linux.decodeFileChooserPath
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
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalPointerIconService
import androidx.compose.ui.platform.LocalTextInputContext
import androidx.compose.ui.platform.LocalTextToolbar
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
import androidx.compose.ui.scene.withFrameTransaction
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.TestDataMode
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import noria.CallbackInterceptor
import org.jetbrains.desktop.gtk.DragAndDropAction
import org.jetbrains.desktop.gtk.DragAndDropQueryData
import org.jetbrains.desktop.gtk.DragAndDropQueryResponse
import org.jetbrains.desktop.gtk.DragIconParams
import org.jetbrains.desktop.gtk.Event
import org.jetbrains.desktop.gtk.EventHandlerResult
import org.jetbrains.desktop.gtk.FileDialog
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

class GtkWindow private constructor(
    private val application: GtkApplication,
    internal val session: ApplicationSession,
    private val onCloseRequest: (WindowCloseRequestReason) -> Unit,
    private val gtkTextInputSessionOwner: GtkTextInputSessionOwner,
    override val nativeWindow: org.jetbrains.desktop.gtk.Window,
) : InteractiveMoveInitiator {
    override val id: LightweightWindowId = LightweightWindowId(nativeWindow.windowId)

    @Volatile
    private var isDisposed = false

    @Volatile
    internal var isMarkedForReuse = false

    private val nativeClosed = CompletableDeferred<Unit>()

    @Volatile
    internal var isFrameRequested = false

    private val fileDialogResponses =
        ConcurrentHashMap<RequestId, CancellableContinuation<List<Path>?>>()

    private var titleField by mutableStateOf("")
    private var overriddenSystemTheme by mutableStateOf<SystemTheme?>(null)

    // The registration init block lives at the BOTTOM of the class; see there.

    /**
     * Mark-in-place reuse (Noria's GTK model): builds a sibling window around the SAME native
     * window and the SAME id — the native id is monotonic and must never be re-fed into a
     * [WindowParams] (KDT errors on duplicate ids, and GTK only drops a closed id from its native
     * window map on a later `on_destroy`, so recreating a recycled id before that is a native
     * error), so the native window has to survive. The shared [gtkTextInputSessionOwner] targets
     * the surviving native window (not a window instance), keeping IME continuity across the swap.
     */
    internal fun reuse(
        session: ApplicationSession,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): GtkWindow {
        val newWindow = GtkWindow(
            application,
            session,
            onCloseRequest,
            gtkTextInputSessionOwner,
            nativeWindow,
        )

        // GTK's own seed list (differs from Wayland's). Noria seeds separate hasActiveAppearance
        // and hasKeyboardFocus fields here; this fork collapses both into a single isFocused
        // (see the field), so the one assignment stands in for both Noria fields.
        newWindow.size = size
        newWindow.contentSize = contentSize
        newWindow.isFocused = isFocused
        newWindow.placement = placement
        newWindow.customTitleBarInsets = customTitleBarInsets
        newWindow.decoration = decoration
        newWindow.density = density
        newWindow.screen = screen
        // GTK asks for the next frame via the frame-tick -> requestRedraw path, which only fires
        // while isFrameRequested is set. The surviving native window won't get a fresh
        // WindowConfigure to re-arm the flag, so arm the sibling's first frame here.
        newWindow.isFrameRequested = true

        // The surviving native window emits no WindowClosed for this dying instance, so the
        // WindowClosed/dispose drain of fileDialogResponses never runs for it. Cancel any in-flight
        // dialog continuations here or they would hang forever — their eventual FileChooserResponse
        // lands on the sibling's fresh, empty map (AIR-6085 WS3 task-9).
        fileDialogResponses.values.forEach { it.cancel() }
        fileDialogResponses.clear()

        // Unlike Noria, this fork's window owns its ComposeScene, so the old Kotlin side dies at
        // swap time (dispose() is a full no-op while marked; see there). Only the native window
        // lives on, inside the sibling that just registered itself in application.windows.
        isDisposed = true
        composeScene.close()
        architectureComponentsOwner.setLifecycleState(Lifecycle.State.DESTROYED)

        return newWindow
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
                return composeScene.withFrameTransaction {
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

    private val semanticsOwners = mutableStateSetOf<SemanticsOwner>()

    private val platformContext: PlatformContext = object : PlatformContext by PlatformContext.Empty(),
        PlatformContext.SemanticsOwnerListener {
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
        layoutDirection = layoutDirection,
        size = contentSizeInPx(),
        coroutineContext = session.coroutineScope.coroutineContext +
            KdtMainDispatcher.INSTANCE,
        platformContext = platformContext,
        dataSourceContext = session.dataSourceContext,
        invalidate = { isFrameRequested = true },
    )

    // KDT's file chooser is asynchronous: showOpenFileDialog/showSaveFileDialog only ISSUE a
    // request and the selection arrives later as an Event.FileChooserResponse dispatched on the
    // KDT event loop (see handleEvent). The dialog methods are therefore suspend: they post the
    // request and suspend until the response event resumes them, which makes them safe to call
    // from any thread — including the event-loop thread itself, which they never block.
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
    ): Path? = openFileDialog(
        mapCommonDialogParams(title, prompt, message, directoryPath),
        FileDialog.OpenDialogParams(
            selectDirectories = canChooseDirectories && !canChooseFiles,
            allowsMultipleSelections = false,
        ),
    )?.firstOrNull()

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
    ): List<Path> = openFileDialog(
        mapCommonDialogParams(title, prompt, message, directoryPath),
        FileDialog.OpenDialogParams(
            selectDirectories = canChooseDirectories && !canChooseFiles,
            allowsMultipleSelections = true,
        ),
    ) ?: emptyList()

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
    ): Path? = saveFileDialog(
        mapCommonDialogParams(title, prompt, message, directoryPath),
        FileDialog.SaveDialogParams(nameFieldStringValue = nameFieldStringValue),
    )?.firstOrNull()

    /**
     * Suspending core of the open-file dialogs. Issues the native request on the KDT event loop,
     * registers the continuation under its [RequestId] (see the Event.FileChooserResponse arm in
     * handleEvent, which URL-decodes and resumes), and suspends until the response arrives.
     *
     * Returns `null` only when the native side rejected the request (null [RequestId]); an empty
     * list means the user cancelled. Suspends without blocking, so it is safe to call from any
     * thread — including the event-loop thread itself.
     */
    internal suspend fun openFileDialog(
        commonParams: FileDialog.CommonDialogParams,
        openParams: FileDialog.OpenDialogParams,
    ): List<Path>? = suspendCancellableCoroutine { continuation ->
        application.onEventLoopAsync {
            // The caller may have been cancelled between suspending and this block running;
            // don't open a native chooser nobody is listening to.
            if (!continuation.isActive) return@onEventLoopAsync
            if (isDisposed) {
                continuation.resume(null)
                return@onEventLoopAsync
            }
            val requestId = nativeWindow.showOpenFileDialog(commonParams, openParams)
            if (requestId == null) {
                continuation.resume(null)
            } else {
                fileDialogResponses[requestId] = continuation
                continuation.invokeOnCancellation { fileDialogResponses.remove(requestId) }
            }
        }
    }

    /**
     * Suspending core of the save-file dialog. See [openFileDialog] for the request/response and
     * threading contract; the returned list holds at most one path.
     */
    internal suspend fun saveFileDialog(
        commonParams: FileDialog.CommonDialogParams,
        saveParams: FileDialog.SaveDialogParams,
    ): List<Path>? = suspendCancellableCoroutine { continuation ->
        application.onEventLoopAsync {
            // The caller may have been cancelled between suspending and this block running;
            // don't open a native chooser nobody is listening to.
            if (!continuation.isActive) return@onEventLoopAsync
            if (isDisposed) {
                continuation.resume(null)
                return@onEventLoopAsync
            }
            val requestId = nativeWindow.showSaveFileDialog(commonParams, saveParams)
            if (requestId == null) {
                continuation.resume(null)
            } else {
                fileDialogResponses[requestId] = continuation
                continuation.invokeOnCancellation { fileDialogResponses.remove(requestId) }
            }
        }
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
        // A window marked for reuse must stay fully live: still registered in application.windows
        // (reuseWindow() looks it up there, and structured quit must still reach it), scene still
        // open, native window untouched — the sibling adopts it. Its Kotlin side is torn down by
        // reuse() at swap time, or by disposeReusableNativeWindowResources() (unmark + dispose) if
        // never reclaimed. This is a full no-op while marked (not just the native-close skip Noria
        // can afford) because, unlike Noria, this fork's dispose also closes the ComposeScene —
        // skipping only the close would leave a throwing husk that reuseWindow would still find.
        if (isDisposed || isMarkedForReuse) return
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

    internal suspend fun awaitNativeClosed() {
        nativeClosed.await()
    }

    internal fun activate(token: String?) {
        onNativeWindowAsync { activate(token) }
    }

    override fun requestClose(reason: WindowCloseRequestReason) {
        if (!isDisposed) {
            // Runs on the main/event-loop thread by the @MainThread contract (see the Window
            // interface); withFrameTransaction joins the frame slice like the system-close path.
            composeScene.withFrameTransaction {
                onCloseRequest(reason)
            }
        }
    }

    internal fun requestCloseFromSystem() {
        composeScene.withFrameTransaction {
            onCloseRequest(WindowCloseRequestReason.UserRequest)
        }
    }

    internal fun currentTextInputSurroundingText(): TextInputSurroundingText? =
        gtkTextInputSessionOwner.currentTextInputSurroundingText

    internal fun queryDragAndDropTarget(query: DragAndDropQueryData): DragAndDropQueryResponse {
        return dragAndDropManager.onQuery(query)
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
                // Naked IME ingress joins the current frame slice.
                composeScene.withFrameTransaction {
                    gtkTextInputSessionOwner.handleTextInputEvent(
                        event.preeditStringData,
                        event.commitStringData,
                        event.deleteSurroundingTextData,
                    )
                }
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
                // KDT delivers URL-encoded file:// paths; decode them before resuming. An empty
                // list means the user cancelled — the open/save wrappers interpret that.
                fileDialogResponses.remove(event.requestId)
                    ?.resume(event.files.map(::decodeFileChooserPath))
                EventHandlerResult.Stop
            }

            is Event.DropPerformed -> {
                dragAndDropManager.onDrop(event)
                EventHandlerResult.Stop
            }

            is Event.DragAndDropLeave -> {
                dragAndDropManager.onLeave()
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
    override fun Content(onLayout: (WindowData) -> Unit) {
        // ComposeScene drives its own composition; nothing to host here.
        onLayout(WindowData(id, semanticsOwners))
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
            // The dispatch chain joins the current frame slice (as macOS does, and as this backend
            // already does for IME and DnD ingress), so DataSource reads in key handlers observe
            // the frame's pinned view under isolation.
            composeScene.withFrameTransaction {
                onPreviewKeyEvent(keyEvent) ||
                    composeScene.sendKeyEvent(keyEvent) ||
                    onKeyEvent(keyEvent)
            }
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
        ).encodeToPng()

        application.activeDragSource = GtkApplication.ActiveDragSource(
            windowId = id,
            itemsEntry = itemsEntry,
            supportedActions = supportedActions,
            onTransferCompleted = { action -> transferData.onTransferCompleted?.invoke(action) },
            dragIconPngBytes = dragImageBytes,
        )

        onNativeWindowAsync {
            // Native DnD takes over the pointer; GTK sends MouseExited when the grab starts and
            // the tracker clears the pressed buttons there (AIR-5571) — clearing here as well
            // broke interactive resize on KDE.
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
                LocalTextToolbar provides remember { DefaultTextToolbar() },
                LocalWindow provides this,
                LocalTextInputSessionOwner provides gtkTextInputSessionOwner,
                LocalPointerIconService provides pointerIconService,
                LocalInputModeManager provides inputModeManager,
            ) {
                contentState?.invoke(windowScope)
            }

            // Refresh hit testing under a stationary pointer after relayout. Queued via the scene's
            // (non-immediate) dispatcher so new SuspendingPointerInputFilters are already subscribed;
            // the tracker's generation check cancels stale refreshes as soon as a real event arrives.
            rememberCoroutineScope().launch {
                inputStateTracker
                    .prepareSyntheticPointerEventAfterRelayoutIfNecessary()
                    ?.let(inputStateTracker::sendSyntheticPointerEventAfterRelayoutIfCurrent)
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

    init {
        // Registered here, at the bottom of the class, so that inputStateTracker and the scene
        // are guaranteed to be initialized before any event dispatched by the application event
        // loop can reach handleEvent (see MacOsWindow's identical ordering).
        application.windows[id] = this
    }

    companion object {
        internal fun create(
            application: GtkApplication,
            session: ApplicationSession,
            onCloseRequest: (WindowCloseRequestReason) -> Unit,
        ): GtkWindow {
            // The native id is allocated monotonically and must NEVER be re-fed into a new
            // WindowParams — KDT's native side errors on duplicate ids, and GTK destroys windows
            // asynchronously (the native map entry is dropped only in a later on_destroy), so a
            // recycled id could collide with a still-live native entry. Reuse therefore keeps the
            // native window alive (GtkWindow.reuse) instead of ever recycling an id here.
            val nativeWindowId = application.allocateNativeWindowId()
            val nativeWindow = application.onEventLoopSync {
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
            // The IME session owner binds its callbacks to the surviving native window (not a
            // window instance) so it can be threaded through reuse() and keep IME state across the
            // swap. GTK text input is window-level, so it posts straight to this native window.
            val gtkTextInputSessionOwner = GtkTextInputSessionOwner(
                startInputMethod = { context ->
                    application.onEventLoopAsync { nativeWindow.textInputEnable(context) }
                },
                stopInputMethod = {
                    application.onEventLoopAsync { nativeWindow.textInputDisable() }
                },
                onDataChanged = { context ->
                    application.onEventLoopAsync { nativeWindow.textInputUpdate(context) }
                },
            )
            return GtkWindow(
                application,
                session,
                onCloseRequest,
                gtkTextInputSessionOwner,
                nativeWindow,
            )
        }

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

/**
 * Maps the fork's cross-platform (macOS-shaped) dialog parameters onto KDT's GTK
 * [FileDialog.CommonDialogParams].
 *
 * KDT's Linux/GTK dialog surface only exposes `modal`, `title`, `acceptLabel` and `currentFolder`,
 * so the macOS-only parameters — `message`, `canCreateDirectories`, the hidden-file/extension flags
 * and `resolvesAliases` — have no counterpart and are intentionally DROPPED. `message` is accepted
 * here only to keep the mapping signature aligned with the [Window] dialog methods. This is the GTK
 * twin of the Wayland `mapCommonDialogParams`; the logic is identical, only the package's
 * [FileDialog] type differs (path decoding is shared via [decodeFileChooserPath]).
 */
internal fun mapCommonDialogParams(
    title: String,
    prompt: String,
    message: String?,
    directoryPath: Path?,
): FileDialog.CommonDialogParams =
    FileDialog.CommonDialogParams(
        modal = true,
        title = title,
        acceptLabel = prompt.takeIf { it.isNotBlank() },
        currentFolder = directoryPath?.toString(),
    )

