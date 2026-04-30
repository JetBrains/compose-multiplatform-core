@file:OptIn(ExperimentalComposeUiApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.gtk

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.desktop.Application
import androidx.compose.ui.desktop.DefaultDoubleClickDistance
import androidx.compose.ui.desktop.DefaultDragThreshold
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.ProvidableLocalScene
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.desktop.SceneHandle
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.deactivateApplication
import androidx.compose.ui.desktop.noWindowAnimationCoroutineContext
import androidx.compose.ui.desktop.removeApplication
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.DefaultHapticFeedback
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fleet.reporting.shared.runtime.currentSpan
import fleet.reporting.shared.tracing.span
import fleet.reporting.shared.tracing.spannedScope
import fleet.reporting.shared.tracing.withCurrentSpan
import fleet.util.async.Resource
import fleet.util.async.resource
import fleet.util.async.withSupervisor
import fleet.util.logging.logger
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import noria.DrainableUpdateQueue
import noria.impl.EffectCoroutineContextCompositionLocal
import noria.noria
import noria.ui.core.RenderPerfMetrics
import noria.ui.loop.FrameCompletionCallbacks
import noria.ui.loop.FrameCompletionCallbacksCompositionLocal
import noria.ui.loop.FrameInvalidationCallbacks
import noria.ui.loop.FrameInvalidationCallbacksCompositionLocal
import noria.ui.loop.RenderLoop
import noria.ui.loop.internal.LocalRenderPerfMetrics
import noria.ui.platform.DrainableCoroutineDispatcher
import org.jetbrains.desktop.gtk.ApplicationConfig
import org.jetbrains.desktop.gtk.ColorSchemeValue
import org.jetbrains.desktop.gtk.DataSource
import org.jetbrains.desktop.gtk.DesktopSetting
import org.jetbrains.desktop.gtk.DragAndDropAction
import org.jetbrains.desktop.gtk.DragAndDropQueryResponse
import org.jetbrains.desktop.gtk.Event
import org.jetbrains.desktop.gtk.EventHandlerResult
import org.jetbrains.desktop.gtk.KotlinDesktopToolkit
import org.jetbrains.desktop.gtk.LogLevel

object GtkApplication : Application {
    internal val logger = logger<GtkApplication>()

    private val lock = Any()
    private val nextWindowId = AtomicLong(1L)

    private var configuredIdentifier: String? = null
    private var configuredLibraryFolderPath: Path? = null
    private var nativeStateDeferred: CompletableDeferred<Unit>? = null
    private var runtimeThread: Thread? = null
    private var shutdown = false
    private var initialized = false
    private var structuredQuitInProgress = false
    private var terminationInProgress = false

    private lateinit var nativeApplicationImpl: org.jetbrains.desktop.gtk.Application
    private lateinit var clipboardImpl: GtkClipboard
    private lateinit var notificationCenterImpl: GtkNotificationCenter
    private lateinit var glProcFuncImpl: org.jetbrains.desktop.gtk.Application.GlProcFunc

    private var uriHandler: UriHandler = GtkUriHandler()
    private var customQuit: (() -> Boolean)? = null

    internal fun initialize(
        identifier: String,
        openUrls: (List<String>) -> Unit,
        libraryFolderPath: Path,
        logFolderPath: Path,
        uriHandler: UriHandler,
        customQuit: (() -> Boolean)?,
    ) {
        synchronized(lock) {
            check(!shutdown) {
                "GtkApplication has already been shut down and cannot be reinitialized in the same process"
            }
            configuredIdentifier?.let { configuredIdentifier ->
                check(configuredIdentifier == identifier) {
                    "GtkApplication is already initialized for '$configuredIdentifier' and cannot be reinitialized for '$identifier'"
                }
            }
            configuredLibraryFolderPath?.let { configuredLibraryFolderPath ->
                check(configuredLibraryFolderPath == libraryFolderPath) {
                    "GtkApplication is already initialized for $configuredLibraryFolderPath and cannot be reinitialized for $libraryFolderPath"
                }
            } ?: run {
                System.setProperty("skiko.library.path", libraryFolderPath.toString())
                val logFilePath = logFolderPath.resolve("GtkApplication").resolve("GtkApplication.log")
                didFinishLaunchingCompletableJob = Job()
                initializeToolkitOnce(libraryFolderPath, logFilePath)
                nativeStateDeferred = CompletableDeferred()
                startRuntime(identifier)
                configuredIdentifier = identifier
                configuredLibraryFolderPath = libraryFolderPath
                initialized = true
            }
            configure(uriHandler, customQuit)
        }
    }

    internal fun current(): GtkApplication {
        check(initialized && !shutdown) { "GtkApplication has not been initialized" }
        return this
    }

    private fun configure(
        uriHandler: UriHandler,
        customQuit: (() -> Boolean)?,
    ) {
        this.uriHandler = uriHandler
        this.customQuit = customQuit
    }

    override fun openUri(uri: String) {
        uriHandler.openUri(uri)
    }

    override val nativeApplication: org.jetbrains.desktop.gtk.Application
        get() {
            awaitNativeState()
            return nativeApplicationImpl
        }

    internal data class ActiveDragSource(
        val windowId: LightweightWindowId,
        val mimeData: Map<String, ByteArray>,
        val supportedActions: Set<DragAndDropTransferAction>,
        val onTransferCompleted: (DragAndDropTransferAction?) -> Unit,
        val dragIconPngBytes: ByteArray?,
    )

    internal var activeDragSource: ActiveDragSource? = null

    internal val clipboard: GtkClipboard
        get() {
            awaitNativeState()
            return clipboardImpl
        }
    val notificationCenter: GtkNotificationCenter
        get() {
            awaitNativeState()
            return notificationCenterImpl
        }
    internal val glProcFunc: org.jetbrains.desktop.gtk.Application.GlProcFunc
        get() {
            awaitNativeState()
            return glProcFuncImpl
        }

    private var didFinishLaunchingCompletableJob: CompletableJob = Job()
    private val quitHandlers = ConcurrentHashMap<String, () -> Boolean>()
    private var keyboardFocusWindowId: LightweightWindowId? = null

    override var screens: Map<Int, GtkScreen> by mutableStateOf(emptyMap())
        private set

    override var windows: Map<LightweightWindowId, GtkWindow> by mutableStateOf(emptyMap())
        internal set

    override val focusedWindow: Window?
        get() = windows.values.firstOrNull { it.isFocused }

    override var systemTheme: SystemTheme by mutableStateOf(SystemTheme.Unknown)
        private set
    override var dragThreshold: Dp by mutableStateOf(DefaultDragThreshold)
        private set
    override var doubleClickDistance: Dp by mutableStateOf(DefaultDoubleClickDistance)
        private set
    internal var doubleClickIntervalMillis: Long by mutableStateOf(500L)
        private set
    private var middleClickPasteEnabled by mutableStateOf(true)

    override fun getClipEntrySync(): ClipEntry = clipboard.getClipEntrySync()

    override suspend fun getClipEntry(): ClipEntry = clipboard.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry) {
        clipboard.setClipEntry(clipEntry)
    }

    override suspend fun systemSelection(): String? =
        if (middleClickPasteEnabled) {
            clipboard.systemSelection()
        } else {
            null
        }

    override suspend fun setSystemSelection(text: String?) {
        if (middleClickPasteEnabled) {
            clipboard.setSystemSelection(text)
        }
    }

    override val nativeClipboard: Any
        get() = clipboard.nativeClipboard

    internal fun handleEvent(event: Event): EventHandlerResult {
        return try {
            when (event) {
                is Event.ApplicationStarted -> {
                    screens = nativeApplication.allScreens().screens.mapIndexed { index, screen ->
                        index to GtkScreen(screen)
                    }.toMap()
                    didFinishLaunchingCompletableJob.complete()
                    EventHandlerResult.Stop
                }

                is Event.DesktopSettingChange -> {
                    val setting = event.setting
                    when (setting) {
                        is DesktopSetting.ColorScheme -> {
                            systemTheme = when (setting.value) {
                                ColorSchemeValue.PreferDark -> SystemTheme.Dark
                                ColorSchemeValue.PreferLight -> SystemTheme.Light
                                else -> SystemTheme.Unknown
                            }
                            nativeApplication.setPreferDarkTheme(systemTheme == SystemTheme.Dark)
                        }

                        is DesktopSetting.DoubleClickInterval -> {
                            doubleClickIntervalMillis = setting.value.inWholeMilliseconds
                        }

                        is DesktopSetting.DoubleClickDistancePixels -> {
                            doubleClickDistance = setting.value.dp
                        }

                        is DesktopSetting.DragAndDropDragThresholdPixels -> {
                            dragThreshold = setting.value.dp
                        }

                        is DesktopSetting.MiddleClickPaste -> {
                            middleClickPasteEnabled = setting.value
                        }

                        else -> {}
                    }
                    EventHandlerResult.Stop
                }

                is Event.DisplayConfigurationChange -> {
                    screens = event.screens.screens.mapIndexed { index, screen ->
                        index to GtkScreen(screen)
                    }.toMap()
                    EventHandlerResult.Stop
                }

                is Event.DataTransferAvailable -> {
                    val handledByClipboard = clipboard.onDataTransferAvailable(event)
                    val handledByWindow = windows.values.any { it.onDataTransferAvailable(event) }
                    if (handledByClipboard || handledByWindow) {
                        EventHandlerResult.Stop
                    } else {
                        EventHandlerResult.Continue
                    }
                }

                is Event.DataTransfer -> {
                    val handledByClipboard = clipboard.onDataReceived(event)
                    val handledByWindow = windows.values.any { it.onDataTransfer(event) }
                    if (handledByClipboard || handledByWindow) {
                        EventHandlerResult.Stop
                    } else {
                        EventHandlerResult.Continue
                    }
                }

                is Event.NotificationShown -> {
                    notificationCenter.onNotificationShown(event)
                    EventHandlerResult.Stop
                }

                is Event.NotificationClosed -> {
                    notificationCenter.onNotificationClosed(event) { windowId, activationToken ->
                        windows[windowId]?.activate(activationToken)
                    }
                    EventHandlerResult.Stop
                }

                is Event.KeyDown,
                is Event.KeyUp,
                is Event.ModifiersChanged,
                is Event.TextInput,
                    -> {
                    keyboardFocusWindowId?.let { windowId ->
                        windows[windowId]?.handleEvent(event)
                    } ?: EventHandlerResult.Continue
                }

                is Event.WindowKeyboardEnter -> {
                    keyboardFocusWindowId = event.windowId.toLightweightWindowId()
                    windows[keyboardFocusWindowId]?.handleEvent(event) ?: EventHandlerResult.Continue
                }

                is Event.WindowKeyboardLeave -> {
                    val windowId = event.windowId.toLightweightWindowId()
                    if (keyboardFocusWindowId == windowId) {
                        keyboardFocusWindowId = null
                    }
                    windows[windowId]?.handleEvent(event) ?: EventHandlerResult.Continue
                }

                is Event.DragIconDraw -> {
                    activeDragSource?.let { source ->
                        GtkWindow.drawDragIcon(event, source.dragIconPngBytes, glProcFunc)
                        EventHandlerResult.Stop
                    } ?: EventHandlerResult.Continue
                }

                is Event.DragAndDropFinished -> {
                    val windowId = event.windowId.toLightweightWindowId()
                    activeDragSource
                        ?.takeIf { it.windowId == windowId }
                        ?.let { source ->
                            source.onTransferCompleted(event.action?.toComposeActionOrNull())
                            activeDragSource = null
                            EventHandlerResult.Stop
                        } ?: EventHandlerResult.Continue
                }

                is Event.FileChooserResponse -> {
                    if (windows.values.any { it.handleEvent(event) == EventHandlerResult.Stop }) {
                        EventHandlerResult.Stop
                    } else {
                        EventHandlerResult.Continue
                    }
                }

                is Event.WindowConfigure,
                is Event.WindowDraw,
                is Event.WindowScaleChanged,
                is Event.WindowScreenChange,
                is Event.WindowFrameTick,
                is Event.WindowClosed,
                is Event.MouseDown,
                is Event.MouseUp,
                is Event.MouseMoved,
                is Event.MouseEntered,
                is Event.MouseExited,
                is Event.ScrollWheel,
                is Event.DropPerformed,
                is Event.DragAndDropLeave,
                is Event.DragAndDropFeedbackFinished,
                    -> {
                    event.windowIdOrNull()
                        ?.let { windowId -> windows[windowId]?.handleEvent(event) }
                        ?: EventHandlerResult.Continue
                }

                is Event.DragIconFrameTick -> {
                    if (activeDragSource != null) {
                        nativeApplication.requestRedrawDragIcon()
                        EventHandlerResult.Stop
                    } else {
                        EventHandlerResult.Continue
                    }
                }

                else -> EventHandlerResult.Continue
            }
        } catch (throwable: Throwable) {
            logger.error(throwable) { "Failed to handle GTK KDT event $event" }
            EventHandlerResult.Continue
        }
    }

    internal fun onRuntimeFailure(throwable: Throwable) {
        didFinishLaunchingCompletableJob.completeExceptionally(throwable)
    }

    internal fun queryDragAndDropTarget(query: org.jetbrains.desktop.gtk.DragAndDropQueryData):
        DragAndDropQueryResponse =
        windows[query.windowId.toLightweightWindowId()]
            ?.queryDragAndDropTarget(query)
            ?: DragAndDropQueryResponse(emptyList())

    internal fun getDataTransferData(
        dataSource: DataSource,
        mimeType: String,
    ): ByteArray? =
        when (dataSource) {
            DataSource.Clipboard -> clipboard.getMimeData(mimeType)
            DataSource.DragAndDrop -> activeDragSource?.mimeData?.get(mimeType)
            DataSource.PrimarySelection ->
                if (middleClickPasteEnabled) {
                    clipboard.getPrimarySelectionMimeData(mimeType)
                } else {
                    null
                }
        }

    internal fun requestCloseFromSystem(windowId: Long) {
        windows[windowId.toLightweightWindowId()]?.requestCloseFromSystem()
    }

    internal fun handleApplicationWantsToTerminate(): Boolean {
        if (!quitHandlers.values.fold(true) { accumulator, quitHandler -> quitHandler() and accumulator }) return false
        requestStructuredQuit()
        return false
    }

    internal fun getSurroundingText(windowId: Long) =
        windows[windowId.toLightweightWindowId()]?.currentTextInputSurroundingText() ?:
            org.jetbrains.desktop.gtk.TextInputSurroundingText(
                surroundingText = "",
                cursorCodepointOffset = 0U,
                selectionStartCodepointOffset = 0U,
            )

    internal fun allocateNativeWindowId(): Long = nextWindowId.getAndIncrement()

    override val isActive: Boolean
        get() = true

    override fun requestActivation() {
        (focusedWindow as? GtkWindow)?.requestFocusAndBringToFront()
    }

    override fun putQuitHandler(id: String, quitHandler: () -> Boolean) {
        quitHandlers[id] = quitHandler
    }

    override fun removeQuitHandler(id: String) {
        quitHandlers.remove(id)
    }

    override suspend fun awaitWhenReady() {
        didFinishLaunchingCompletableJob.join()
    }

    override fun quit() {
        if (quitHandlers.values.fold(true) { accumulator, quitHandler -> quitHandler() and accumulator }) {
            requestStructuredQuit()
        }
    }

    override fun showEmojiAndSymbolsPopup(): Unit = Unit

    private val fontFamilyResolver: FontFamily.Resolver by lazy { createFontFamilyResolver() }
    private val renderLoops = mutableListOf<RenderLoop>()

    override suspend fun stopAndJoin() {
        structuredQuitInProgress = false
        terminationInProgress = true
        try {
            resetState()
        } finally {
            nativeApplication.stopEventLoop()
            runtimeThread?.join()
            removeApplication(this)
            shutdown = true
        }
    }

    override suspend fun resetForReuse() {
        try {
            resetState()
        } finally {
            deactivateApplication(this)
        }
    }

    override fun createWindow(
        scene: Scene<*>,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): Window = GtkWindow(this, scene, onCloseRequest)

    override fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId) {}

    override fun reuseWindow(
        id: LightweightWindowId,
        scene: Scene<*>,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): Window? = null

    override fun disposeReusableNativeWindowResources(id: LightweightWindowId) {}

    private fun requestStructuredQuit() {
        if (terminationInProgress || structuredQuitInProgress) return

        val windowsToClose = windows.values.toList()
        if (windowsToClose.isNotEmpty()) {
            structuredQuitInProgress = true
            windowsToClose.forEach { it.requestClose(WindowCloseRequestReason.ApplicationQuit) }
            return
        }

        continueTerminationAsync()
    }

    internal fun finishStructuredQuitIfNeeded() {
        if (!structuredQuitInProgress || windows.isNotEmpty()) return
        continueTerminationAsync()
    }

    private fun continueTerminationAsync() {
        if (terminationInProgress) return
        structuredQuitInProgress = false
        terminationInProgress = true
        val customQuit = customQuit
        thread(start = true, isDaemon = true, name = "GtkApplication Structured Quit") {
            customQuit?.invoke() ?: runBlocking {
                stopAndJoin()
            }
        }
    }

    private suspend fun resetState() {
        val windowsToClose = windows.values.toList()
        windowsToClose.forEach { it.dispose() }
        windowsToClose.forEach { it.awaitNativeClosed() }
        while (renderLoops.isNotEmpty()) {
            renderLoops.first().stopAndJoin()
        }
        keyboardFocusWindowId = null
        activeDragSource = null
        quitHandlers.clear()
        // The native GTK runtime persists across resetForReuse() and destroys windows asynchronously.
        // Reusing Kotlin-side window IDs before native WindowClosed arrives can collide with still-live
        // native entries in KDT's window_id_to_window map, so GTK window IDs must stay monotonic.
    }

    override fun <T> CoroutineScope.launchScene(
        applyCoroutineContext: CoroutineContext,
        prepareMainThread: () -> T,
        restoreMainThread: (T) -> Unit,
        content: @Composable () -> Unit,
    ): SceneHandle {
        val drainableDispatcher = DrainableCoroutineDispatcher(Dispatchers.Main)
        lateinit var reconcile: () -> Unit
        lateinit var scene: Scene<T>
        val renderPerfMetrics = RenderPerfMetrics()
        val terminationSignal = Job()
        var frameRequested = false
        var noWindowsFrameRequested = false
        var nextNoWindowsFrameDeadlineNs = 0L

        fun requestFrame() {
            val windowsInScene = windows.values.filter { it.scene == scene }
            if (windowsInScene.isNotEmpty()) {
                if (!frameRequested) {
                    frameRequested = true
                    windowsInScene.forEach {
                        it.isFrameRequested = true
                        it.requestFrame()
                    }
                }
            } else if (!noWindowsFrameRequested) {
                noWindowsFrameRequested = true
                val delayMs = ((nextNoWindowsFrameDeadlineNs - System.nanoTime()).coerceAtLeast(0L) + 999_999L) / 1_000_000L
                launch {
                    if (delayMs > 0L) {
                        delay(delayMs)
                    }
                    if (terminationSignal.isCompleted) {
                        noWindowsFrameRequested = false
                        return@launch
                    }
                    nativeApplication.runOnEventLoopAsync {
                        noWindowsFrameRequested = false
                        nextNoWindowsFrameDeadlineNs = System.nanoTime() + noWindowsMinFrameIntervalNs
                        scene.withPreparedMainThread {
                            withoutReentrancy {
                                scene.reconcile()
                            }
                        }
                    }
                }
            }
        }

        val drainableUpdateQueue = DrainableUpdateQueue(::requestFrame)
        val broadcastFrameClock = BroadcastFrameClock(::requestFrame)
        val framesFlow = MutableSharedFlow<RenderLoop.FrameInfo>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val sceneJob = launch {
            try {
                withSupervisor(
                    applyCoroutineContext +
                        broadcastFrameClock +
                        drainableDispatcher +
                        noWindowAnimationCoroutineContext { windows.values.any { it.scene == scene } } +
                        CoroutineName("SceneCoroutine"),
                ) { sceneCoroutineScope ->
                    scene = Scene(
                        sceneCoroutineScope,
                        prepareMainThread,
                        restoreMainThread,
                        reconcile = { reconcile() },
                    )
                    val frameInvalidationCallbacks: FrameInvalidationCallbacks = AtomicReference(null)
                    val frameCompletionCallbacks: FrameCompletionCallbacks = AtomicReference(null)

                    fun noriaResource(content: @Composable () -> Unit): Resource<Unit> {
                        return resource { consumer ->
                            val noria = withContext(Dispatchers.Main.immediate) {
                                scene.withPreparedMainThread {
                                    val noria = noria(drainableUpdateQueue) {
                                        content()
                                    }
                                    val renderLoopSpan = currentSpan
                                    reconcile = {
                                        renderPerfMetrics.startReconcile()
                                        drainableDispatcher.drain()
                                        val reconcileTime = measureTime {
                                            broadcastFrameClock.sendFrame(
                                                initialTimestamp.elapsedNow().inWholeNanoseconds,
                                            )
                                            frameInvalidationCallbacks.exchange(null)?.forEach { it() }
                                            withCurrentSpan(renderLoopSpan) {
                                                span("frame") {
                                                    noria.reconcile()
                                                    frameInvalidationCallbacks.exchange(null)?.let { callbacks ->
                                                        var shouldReconcileAgain = false
                                                        callbacks.forEach { callback ->
                                                            shouldReconcileAgain = callback() || shouldReconcileAgain
                                                        }
                                                        if (shouldReconcileAgain) {
                                                            noria.reconcile()
                                                        }
                                                    }
                                                    frameInvalidationCallbacks.exchange(null)?.let { callbacks ->
                                                        callbacks.forEach { callback ->
                                                            if (callback()) {
                                                                requestFrame()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        val frameInfo = RenderLoop.FrameInfo(reconcileTime.inWholeNanoseconds)
                                        frameCompletionCallbacks.exchange(null)?.forEach { it(frameInfo) }
                                        framesFlow.tryEmit(frameInfo)
                                        renderPerfMetrics.endReconcile()
                                        frameRequested = false
                                    }
                                    withoutReentrancy { reconcile() }
                                    noria
                                }
                            }
                            try {
                                consumer(Unit)
                            } finally {
                                withContext(NonCancellable) {
                                    spannedScope("destroy noria") {
                                        val destroyCompletion = Job()
                                        nativeApplication.runOnEventLoopAsync {
                                            scene.withPreparedMainThread {
                                                noria.destroy()
                                            }
                                            destroyCompletion.complete()
                                        }
                                        destroyCompletion.join()
                                    }
                                }
                            }
                        }
                    }

                    noriaResource {
                        CompositionLocalProvider(
                            ProvidableLocalScene provides scene,
                            LocalRenderPerfMetrics provides renderPerfMetrics,
                            EffectCoroutineContextCompositionLocal provides sceneCoroutineScope.coroutineContext,
                            FrameInvalidationCallbacksCompositionLocal provides frameInvalidationCallbacks,
                            FrameCompletionCallbacksCompositionLocal provides frameCompletionCallbacks,
                            LocalUriHandler provides this@GtkApplication,
                            LocalClipboard provides this@GtkApplication,
                            LocalFontFamilyResolver provides fontFamilyResolver,
                            LocalHapticFeedback provides remember { DefaultHapticFeedback() },
                        ) {
                            content()
                        }
                    }.use {
                        terminationSignal.join()
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    span("complete and join drainable dispatcher") {
                        drainableDispatcher.completeAndJoin()
                    }
                }
            }
        }

        val renderLoop = object : RenderLoop {
            override suspend fun stopAndJoin() {
                terminationSignal.complete()
                sceneJob.join()
                renderLoops.remove(this)
            }

            override val framesFlow: Flow<RenderLoop.FrameInfo>
                get() = framesFlow
        }
        return SceneHandle(renderLoop, broadcastFrameClock).also {
            renderLoops.add(it.renderLoop)
        }
    }

    private var reconcileInProgress = false

    internal fun withoutReentrancy(block: () -> Unit) {
        if (!reconcileInProgress) {
            reconcileInProgress = true
            try {
                block()
            } finally {
                reconcileInProgress = false
            }
        }
    }

    internal fun isEventLoopThread(): Boolean = nativeApplication.isEventLoopThread()

    internal fun onEventLoopAsync(block: org.jetbrains.desktop.gtk.Application.() -> Unit) {
        val nativeApplication = nativeApplication
        if (nativeApplication.isEventLoopThread()) {
            nativeApplication.block()
        } else {
            nativeApplication.runOnEventLoopAsync {
                nativeApplication.block()
            }
        }
    }

    internal fun <T> onEventLoopSync(block: org.jetbrains.desktop.gtk.Application.() -> T): T {
        val nativeApplication = nativeApplication
        if (nativeApplication.isEventLoopThread()) {
            return nativeApplication.block()
        }
        val result = CompletableDeferred<T>()
        nativeApplication.runOnEventLoopAsync {
            try {
                result.complete(nativeApplication.block())
            } catch (throwable: Throwable) {
                result.completeExceptionally(throwable)
            }
        }
        return runBlocking {
            result.await()
        }
    }

    private fun nativeStateDeferred(): CompletableDeferred<Unit> =
        requireNotNull(nativeStateDeferred) {
            "GTK native runtime has not been started"
        }

    private fun awaitNativeState() {
        runBlocking {
            nativeStateDeferred().await()
        }
    }

    private fun initializeToolkitOnce(libraryFolderPath: Path, logFilePath: Path) {
        configuredLibraryFolderPath?.let { configuredLibraryFolderPath ->
            check(configuredLibraryFolderPath == libraryFolderPath) {
                "GTK KDT is already initialized for $configuredLibraryFolderPath and cannot be reinitialized for $libraryFolderPath in the same process"
            }
            return
        }
        KotlinDesktopToolkit.init(
            libraryFolderPath = libraryFolderPath,
            logFilePath = logFilePath,
            consoleLogLevel = LogLevel.Info,
            fileLogLevel = LogLevel.Debug,
        )
    }

    private fun startRuntime(identifier: String) {
        val nativeStateDeferred = nativeStateDeferred()
        runtimeThread = thread(start = true, isDaemon = true, name = "GtkApplication Event Loop") {
            try {
                Thread.currentThread().name = "GtkApplication Main Thread (KDT)"
                val nativeApplication = org.jetbrains.desktop.gtk.Application(identifier)
                val clipboard = GtkClipboard(nativeApplication)
                val notificationCenter = GtkNotificationCenter(nativeApplication)
                val glProcFunc = nativeApplication.getEglProcFunc()
                    ?: nativeApplication.initializeGl("libGL.so")
                    ?: error("GTK KDT failed to initialize OpenGL")
                nativeApplicationImpl = nativeApplication
                clipboardImpl = clipboard
                notificationCenterImpl = notificationCenter
                glProcFuncImpl = glProcFunc
                nativeStateDeferred.complete(Unit)
                nativeApplication.runEventLoop(
                    ApplicationConfig(
                        eventHandler = ::handleEvent,
                        queryDragAndDropTarget = ::queryDragAndDropTarget,
                        getDataTransferData = ::getDataTransferData,
                        windowCloseRequest = { windowId ->
                            requestCloseFromSystem(windowId)
                            false
                        },
                        applicationWantsToTerminate = ::handleApplicationWantsToTerminate,
                        getSurroundingText = ::getSurroundingText,
                    ),
                )
            } catch (throwable: Throwable) {
                nativeStateDeferred.completeExceptionally(throwable)
                onRuntimeFailure(throwable)
            }
        }
    }

}

internal fun Long.toLightweightWindowId(): LightweightWindowId = LightweightWindowId(this)

internal fun DragAndDropAction.toComposeActionOrNull():
    DragAndDropTransferAction? =
    when (this) {
        DragAndDropAction.Copy -> DragAndDropTransferAction.Copy
        DragAndDropAction.Move -> DragAndDropTransferAction.Move
    }

private fun Event.windowIdOrNull(): LightweightWindowId? =
    when (this) {
        is Event.WindowConfigure -> windowId.toLightweightWindowId()
        is Event.WindowDraw -> windowId.toLightweightWindowId()
        is Event.WindowScaleChanged -> windowId.toLightweightWindowId()
        is Event.WindowScreenChange -> windowId.toLightweightWindowId()
        is Event.WindowFrameTick -> windowId.toLightweightWindowId()
        is Event.WindowClosed -> windowId.toLightweightWindowId()
        is Event.MouseDown -> windowId.toLightweightWindowId()
        is Event.MouseUp -> windowId.toLightweightWindowId()
        is Event.MouseMoved -> windowId.toLightweightWindowId()
        is Event.MouseEntered -> windowId.toLightweightWindowId()
        is Event.MouseExited -> windowId.toLightweightWindowId()
        is Event.ScrollWheel -> windowId.toLightweightWindowId()
        is Event.DropPerformed -> windowId.toLightweightWindowId()
        is Event.DragAndDropLeave -> windowId.toLightweightWindowId()
        is Event.DragAndDropFeedbackFinished -> windowId.toLightweightWindowId()
        else -> null
    }

private val initialTimestamp = TimeSource.Monotonic.markNow()
private const val noWindowsMinFrameIntervalNs = 1_000_000_000L / 60
