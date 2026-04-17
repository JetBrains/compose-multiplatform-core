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

@file:OptIn(ExperimentalComposeUiApi::class)

package androidx.compose.ui.kdt.macos

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.kdt.Application
import androidx.compose.ui.kdt.IconDecoratedApplication
import androidx.compose.ui.kdt.LightweightWindowId
import androidx.compose.ui.kdt.ProvidableLocalScene
import androidx.compose.ui.kdt.Scene
import androidx.compose.ui.kdt.SceneHandle
import androidx.compose.ui.kdt.Window
import androidx.compose.ui.kdt.deactivateApplication
import androidx.compose.ui.kdt.removeApplication
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.DefaultHapticFeedback
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalPointerIconService
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import fleet.reporting.shared.runtime.currentSpan
import fleet.reporting.shared.tracing.span
import fleet.reporting.shared.tracing.spannedScope
import fleet.reporting.shared.tracing.withCurrentSpan
import fleet.util.async.Resource
import fleet.util.async.resource
import fleet.util.async.withSupervisor
import androidx.compose.ui.kdt.logging.logger
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
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
import org.jetbrains.desktop.macos.AppMenuManager
import org.jetbrains.desktop.macos.AppMenuStructure
import org.jetbrains.desktop.macos.Appearance
import org.jetbrains.desktop.macos.DragAndDropHandler
import org.jetbrains.desktop.macos.DragInfo
import org.jetbrains.desktop.macos.DragOperation
import org.jetbrains.desktop.macos.DragOperationsSet
import org.jetbrains.desktop.macos.DragSourceCallbacks
import org.jetbrains.desktop.macos.DragTargetCallbacks
import org.jetbrains.desktop.macos.DraggingContext
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.EventHandlerResult
import org.jetbrains.desktop.macos.GrandCentralDispatch
import org.jetbrains.desktop.macos.KotlinDesktopToolkit
import org.jetbrains.desktop.macos.LogicalPoint
import org.jetbrains.desktop.macos.Sound
import org.jetbrains.desktop.macos.WindowEvent
import org.jetbrains.desktop.macos.WindowId

object MacOsApplication : Application,
    IconDecoratedApplication,
    Clipboard by MacOsClipboard {
    internal val logger = logger<MacOsApplication>()
    var isNativeApplicationInitialized: Boolean = false
    private val lock = Any()
    private var configuredLibraryFolderPath: Path? = null
    private var shutdown = false
    private var initialized = false

    private var openUrls: (List<String>) -> Unit = {}
    private var uriHandler: UriHandler = MacOsUriHandler()
    private var customQuit: (() -> Boolean)? = null

    private fun configure(
        openUrls: (List<String>) -> Unit,
        uriHandler: UriHandler,
        customQuit: (() -> Boolean)?,
    ) {
        this.openUrls = openUrls
        this.uriHandler = uriHandler
        this.customQuit = customQuit
    }

    override fun openUri(uri: String) {
        uriHandler.openUri(uri)
    }

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
                "MacOsApplication has already been shut down and cannot be reinitialized in the same process"
            }
            configuredLibraryFolderPath?.let { configuredLibraryFolderPath ->
                check(configuredLibraryFolderPath == libraryFolderPath) {
                    "MacOsApplication is already initialized for $configuredLibraryFolderPath and cannot be reinitialized for $libraryFolderPath"
                }
            } ?: run {
                startRuntime(libraryFolderPath, logFolderPath)
                configuredLibraryFolderPath = libraryFolderPath
                initialized = true
            }
            configure(openUrls, uriHandler, customQuit)
        }
    }

    internal fun current(): MacOsApplication {
        check(initialized && !shutdown) { "MacOsApplication has not been initialized" }
        return this
    }

    private fun startRuntime(
        libraryFolderPath: Path,
        logFolderPath: Path,
    ) {
        // todo[unterhofer] Remove this as soon as we don't rely on this property anywhere anymore
        System.setProperty("skiko.library.path", libraryFolderPath.toString())
        val logFilePath = logFolderPath.resolve("MacOsApplication").resolve("MacOsApplication.log")
        KotlinDesktopToolkit.init(libraryFolderPath, logFilePath)
        didFinishLaunchingCompletableJob = Job()
        eventLoopThread = startEventLoopThread()
    }

    private var didFinishLaunchingCompletableJob: CompletableJob = Job()
    val didFinishLaunching: Job
        get() = didFinishLaunchingCompletableJob

    override var screens: Map<Int, MacOsScreen> by mutableStateOf(emptyMap())
        private set

    override var windows: SnapshotStateMap<LightweightWindowId, MacOsWindow> = mutableStateMapOf()
        internal set

    internal val reusableNativeWindowResources =
        mutableMapOf<LightweightWindowId, Pair<org.jetbrains.desktop.macos.Window, MetalViewContext>>()


    // todo[unterhofer] Back with the native keyWindow and the corresponding event
    override val focusedWindow: Window? get() = windows.values.firstOrNull { it.isFocused }

    private fun startEventLoopThread(): Thread = thread(start = true, name = "EventLoopWatcher") {
        try {
            GrandCentralDispatch.startOnMainThread {
                if (!isNativeApplicationInitialized) {
                    nativeApplication.init(org.jetbrains.desktop.macos.Application.ApplicationConfig())
                    isNativeApplicationInitialized = true
                } else {
                    // We get this event only when the application is initialized
                    // at the same time we can't initialize the native application twice for the same process
                    GrandCentralDispatch.startOnMainThread {
                        didFinishLaunchingCompletableJob.complete()
                    }
                }
                Thread.currentThread().name = "MacOsApplication Main Thread (KDT)"
                systemTheme = nativeApplication.appearance.toSystemTheme()
                screens = org.jetbrains.desktop.macos.Screen.allScreens().screens.associate {
                    it.screenId to MacOsScreen(it)
                }
                nativeApplication.setQuitHandler {
                    // currently, we must evaluate ALL handlers because SafeQuitInterceptor may quit too early otherwise
                    quitHandlers.values.fold(true) { accumulator, shouldTerminate -> shouldTerminate() and accumulator } &&
                        this@MacOsApplication.customQuit?.invoke()
                        ?: run {
                            runBlocking {
                                stopAndJoin()
                            }
                        true
                    }
                }

                DragAndDropHandler.init(
                    object : DragTargetCallbacks {
                        override fun onDragEntered(info: DragInfo): DragOperation {
                            val windowId =
                                info.lightweightDestinationWindowId() ?: return DragOperation.NONE
                            return windows[windowId]
                                ?.macOsDragAndDropManager
                                ?.onDragEntered(info) ?: DragOperation.NONE
                        }

                        override fun onDragExited(info: DragInfo?) {
                            val windowId = info?.lightweightDestinationWindowId() ?: return
                            windows[windowId]
                                ?.macOsDragAndDropManager
                                ?.onDragExited()
                        }

                        override fun onDragPerformed(info: DragInfo): Boolean {
                            val windowId = info.lightweightDestinationWindowId() ?: return false
                            return windows[windowId]
                                ?.macOsDragAndDropManager
                                ?.onDragPerformed(info) ?: false
                        }

                        override fun onDragUpdated(info: DragInfo): DragOperation {
                            val windowId =
                                info.lightweightDestinationWindowId() ?: return DragOperation.NONE
                            return windows[windowId]
                                ?.macOsDragAndDropManager
                                ?.onDragUpdated(info) ?: DragOperation.NONE
                        }
                    },
                    object : DragSourceCallbacks {
                        override fun onDragSourceOperationMask(
                            sourceWindowId: WindowId,
                            sequenceNumber: Long,
                            context: DraggingContext,
                        ): DragOperationsSet {
                            val windowId = sourceWindowId.toLightweightWindowId()
                            val data = windows[windowId]?.activeDragAndDropTransferData
                                ?: return DragOperationsSet.NONE
                            return data.supportedActions
                                .map { action -> action.toDragOperation() }
                                .map { dragOperation -> DragOperationsSet.of(dragOperation) }
                                .fold(DragOperationsSet.NONE) { acc, set -> acc + set }
                        }

                        override fun onDragSourceSessionEndedAt(
                            sourceWindowId: WindowId,
                            sequenceNumber: Long,
                            locationOnScreen: LogicalPoint,
                            dragOperation: DragOperation,
                        ) {
                            val window = windows[sourceWindowId.toLightweightWindowId()]
                            window?.activeDragAndDropTransferData?.onTransferCompleted?.invoke(
                                dragOperation.toDragAndDropTransferAction(),
                            )
                            window?.activeDragAndDropTransferData = null
                        }
                    },
                )

                nativeApplication.runEventLoop { event ->
                    try {
                        when (event) {
                            is WindowEvent -> {
                                if (
                                    event is Event.MouseMoved ||
                                    event is Event.MouseDragged ||
                                    event is Event.MouseEntered ||
                                    event is Event.MouseExited
                                ) {
                                    pointerIconService.setHiddenUntilPointerMoves(false)
                                }
                                val window = windows[event.lightweightWindowId()]
                                window?.handleEvent(event)
                                    ?: EventHandlerResult.Continue
                            }
                            is Event.ApplicationOpenUrls -> {
                                this@MacOsApplication.openUrls(event.urls)
                                EventHandlerResult.Stop
                            }
                            is Event.ApplicationDidFinishLaunching -> {
                                didFinishLaunchingCompletableJob.complete()
                                notificationCenter.init()
                                EventHandlerResult.Stop
                            }
                            is Event.ApplicationAppearanceChange -> {
                                systemTheme = event.newAppearance.toSystemTheme()
                                EventHandlerResult.Stop
                            }
                            is Event.DisplayConfigurationChange -> {
                                // todo[unterhofer] Make the instances stable once screens are reactive
                                //  and not mere facades
                                screens =
                                    org.jetbrains.desktop.macos.Screen.allScreens().screens.associate {
                                        it.screenId to MacOsScreen(it)
                                    }
                                EventHandlerResult.Stop
                            }
                            else -> EventHandlerResult.Continue
                        }
                    } catch (throwable: Throwable) {
                        logger.error(throwable) {
                            "Failed to handle event $event; will let it propagate"
                        }
                        EventHandlerResult.Continue
                    }
                }
            }
        } catch (throwable: Throwable) {
            didFinishLaunchingCompletableJob.completeExceptionally(throwable)
        }
    }

    private var eventLoopThread: Thread? = null
    internal val desktopGpuContext by lazy { DesktopGpuContext() }

    fun setMainMenu(menu: AppMenuStructure?) {
        if (menu == null) {
            AppMenuManager.setMainMenuToNone()
        } else {
            AppMenuManager.setMainMenu(menu)
        }
    }

    override fun showEmojiAndSymbolsPopup() {
        nativeApplication.orderFrontCharactersPalette()
    }

    override fun setIcon(icon: ByteArray) {
        nativeApplication.setDockIcon(icon)
    }

    val notificationCenter: MacOsNotificationCenter = MacOsNotificationCenter(this)

    val sound: Sound = Sound

    private val fontFamilyResolver: FontFamily.Resolver by lazy { createFontFamilyResolver() }

    private val pointerIconService: PointerIconService = MacOsPointerIconService
    internal val inputModeManager: InputModeManager = InputModeManagerImpl(InputMode.Touch) {
        pointerIconService.setHiddenUntilPointerMoves(it == InputMode.Keyboard)
    }

    // todo[unterhofer] Make this reactive
    override val isActive: Boolean get() = nativeApplication.isActive

    override fun requestActivation() {
        nativeApplication.activateIgnoringOtherApps()
    }

    private val quitHandlers = ConcurrentHashMap<String, () -> Boolean>()
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
        nativeApplication.requestTermination()
    }

    override val nativeApplication: org.jetbrains.desktop.macos.Application
        get() = org.jetbrains.desktop.macos.Application

    private val renderLoops = mutableListOf<RenderLoop>()
    override suspend fun stopAndJoin() {
        try {
            resetState()
        } finally {
            withContext(Dispatchers.Main.immediate) {
                nativeApplication.stopEventLoop()
            }
            eventLoopThread?.join()
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
        onCloseRequest: () -> Unit,
    ): Window {
        return MacOsWindow(
            this,
            scene,
            onCloseRequest = onCloseRequest,
        )
    }

    override fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId) {
        windows[id]?.let { window ->
            reusableNativeWindowResources[id] = window.nativeWindow to window.viewContext
        }
    }

    override fun reuseWindow(
        id: LightweightWindowId,
        scene: Scene<*>,
        onCloseRequest: () -> Unit,
    ): Window? {
        return reusableNativeWindowResources[id]?.let { (nativeWindow, viewContext) ->
            logger.debug { "Reusing window $id" }
            windows[id]?.dispose()
            reusableNativeWindowResources.remove(id)
            MacOsWindow(this, scene, nativeWindow, viewContext, onCloseRequest)
        }
    }

    override fun disposeReusableNativeWindowResources(id: LightweightWindowId) {
        reusableNativeWindowResources.remove(id)?.let { (nativeWindow, viewContext) ->
            nativeWindow.close()
            desktopGpuContext.destroyMetalViewContext(viewContext)
        }
    }

    private suspend fun resetState() {
        withContext(Dispatchers.Main.immediate) {
            windows.values.toList().forEach { it.dispose() }
            reusableNativeWindowResources.values.forEach { (nativeWindow, viewContext) ->
                nativeWindow.close()
                desktopGpuContext.destroyMetalViewContext(viewContext)
            }
            reusableNativeWindowResources.clear()
            while (renderLoops.isNotEmpty()) {
                renderLoops.first().stopAndJoin()
            }
            quitHandlers.clear()
        }
    }

    override var systemTheme: SystemTheme by mutableStateOf(SystemTheme.Unknown)
        private set

    @OptIn(ExperimentalComposeUiApi::class)
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
        var frameRequested = false
        fun requestFrame() {
            val windowsInScene = windows.values.filter { it.scene == scene }
            if (windowsInScene.isNotEmpty()) {
                if (!frameRequested) {
                    frameRequested = true
                    windowsInScene.forEach { it.isFrameRequested = true }
                }
            } else {
                // Reset frameRequested: it may have been set to true for a window
                // that has since been disposed (e.g. reuseWindow before DisplayLink fired).
                frameRequested = false
                GrandCentralDispatch.dispatchOnMain(highPriority = false) {
                    scene.withPreparedMainThread {
                        withoutReentrancy {
                            scene.reconcile()
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
        val terminationSignal = Job()
        val sceneJob = launch {
            try {
                withSupervisor(
                    applyCoroutineContext +
                        broadcastFrameClock +
                        drainableDispatcher +
                        CoroutineName("SceneCoroutine"),
                ) { sceneCoroutineScope ->
                    scene = Scene(
                        sceneCoroutineScope, prepareMainThread, restoreMainThread,
                        reconcile = {
                            reconcile() // lateinit
                        },
                    )
                    logger.debug { "Scene ${scene.hashCode()} created" }
                    val frameInvalidationCallbacks: FrameInvalidationCallbacks =
                        AtomicReference(null)
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
                                            broadcastFrameClock.sendFrame(initialTimestamp.elapsedNow().inWholeNanoseconds)

                                            frameInvalidationCallbacks.exchange(null)
                                                ?.forEach { it() }
                                            withCurrentSpan(renderLoopSpan) {
                                                span("frame") {
                                                    noria.reconcile()
                                                    frameInvalidationCallbacks.exchange(null)
                                                        ?.let { invalidationCallbacks ->
                                                            var shouldReconcileAgain = false
                                                            invalidationCallbacks.forEach { callback ->
                                                                val requestedReconcile = callback()
                                                                shouldReconcileAgain =
                                                                    shouldReconcileAgain || requestedReconcile
                                                            }
                                                            if (shouldReconcileAgain) {
                                                                noria.reconcile()
                                                            }
                                                        }
                                                    frameInvalidationCallbacks.exchange(null)
                                                        ?.let { invalidationCallbacks ->
                                                            invalidationCallbacks.forEach { callback ->
                                                                val requestedReconcile = callback()
                                                                if (requestedReconcile) {
                                                                    requestFrame()
                                                                }
                                                            }
                                                        }
                                                }
                                            }
                                        }
                                        val frameInfo =
                                            RenderLoop.FrameInfo(reconcileTime.inWholeNanoseconds)
                                        frameCompletionCallbacks.exchange(null)
                                            ?.let { completionCallbacks ->
                                                for (completionCallback in completionCallbacks) {
                                                    completionCallback(frameInfo)
                                                }
                                            }
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
                                        GrandCentralDispatch.dispatchOnMain {
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
                            LocalUriHandler provides this@MacOsApplication,
                            LocalClipboard provides this@MacOsApplication,
                            LocalFontFamilyResolver provides fontFamilyResolver,
                            LocalHapticFeedback provides remember { DefaultHapticFeedback() },
                            LocalPointerIconService provides pointerIconService,
                            LocalInputModeManager provides inputModeManager,
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
}

internal fun Appearance.toSystemTheme(): SystemTheme = when (this) {
    Appearance.Dark -> SystemTheme.Dark
    Appearance.Light -> SystemTheme.Light
}

internal fun DragAndDropTransferAction.toDragOperation(): DragOperation = when (this) {
    DragAndDropTransferAction.Copy -> DragOperation.COPY
    DragAndDropTransferAction.Link -> DragOperation.LINK
    DragAndDropTransferAction.Move -> DragOperation.MOVE
    else -> DragOperation.NONE
}

internal fun DragOperation.toDragAndDropTransferAction(): DragAndDropTransferAction? = when (this) {
    DragOperation.COPY -> DragAndDropTransferAction.Copy
    DragOperation.LINK -> DragAndDropTransferAction.Link
    DragOperation.MOVE -> DragAndDropTransferAction.Move
    else -> null
}

private val initialTimestamp = TimeSource.Monotonic.markNow()
