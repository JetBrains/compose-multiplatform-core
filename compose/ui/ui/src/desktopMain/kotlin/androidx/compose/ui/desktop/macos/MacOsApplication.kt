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

package androidx.compose.ui.desktop.macos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.desktop.Application
import androidx.compose.ui.desktop.IconDecoratedApplication
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.deactivateApplication
import androidx.compose.ui.desktop.logging.logger
import androidx.compose.ui.desktop.removeApplication
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    private var structuredQuitInProgress = false
    internal var terminationInProgress = false
        private set

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
        // Do NOT force `skiko.library.path` to the KDT-extracted folder — skiko only ships its own
        // native dylib inside its runtime jar on the classpath, not inside `kdt-extracted`.
        val logFilePath = logFolderPath.resolve("MacOsApplication").resolve("MacOsApplication.log")
        // Native logger init fails if the parent directory doesn't exist yet, so make sure it's there.
        java.nio.file.Files.createDirectories(logFilePath.parent)
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

    private fun hasEffectiveWindows(): Boolean =
        windows.isNotEmpty() || reusableNativeWindowResources.isNotEmpty()


    // todo[unterhofer] Back with the native keyWindow and the corresponding event
    override val focusedWindow: Window? get() = windows.values.firstOrNull { it.isFocused }

    private fun startEventLoopThread(): Thread = thread(start = true, name = "EventLoopWatcher") {
        try {
            GrandCentralDispatch.startOnMainThread {
                if (!isNativeApplicationInitialized) {
                    nativeApplication.init(org.jetbrains.desktop.macos.Application.ApplicationConfig())
                    isNativeApplicationInitialized = true
                }
                Thread.currentThread().name = "MacOsApplication Main Thread (KDT)"
                systemTheme = nativeApplication.appearance.toSystemTheme()
                screens = org.jetbrains.desktop.macos.Screen.allScreens().screens.associate {
                    it.screenId to MacOsScreen(it)
                }
                nativeApplication.setQuitHandler {
                    // currently, we must evaluate ALL handlers because SafeQuitInterceptor may quit too early otherwise
                    val shouldTerminate = quitHandlers.values.fold(true) { accumulator, quitHandler ->
                        quitHandler() and accumulator
                    }
                    if (!shouldTerminate) return@setQuitHandler false

                    if (structuredQuitInProgress) return@setQuitHandler false

                    val windowsToClose = windows.values.toList()
                    if (windowsToClose.isNotEmpty()) {
                        structuredQuitInProgress = true
                        logger.info { "Quit approved; requesting structured close for ${windowsToClose.size} window(s)" }
                        windowsToClose.forEach { it.requestClose(WindowCloseRequestReason.ApplicationQuit) }
                        return@setQuitHandler false
                    }

                    if (reusableNativeWindowResources.isNotEmpty()) {
                        structuredQuitInProgress = true
                        logger.info {
                            "Quit approved; waiting for ${reusableNativeWindowResources.size} reusable window resource(s) to drain"
                        }
                        return@setQuitHandler false
                    }

                    terminationInProgress = true
                    this@MacOsApplication.customQuit?.invoke() ?: run {
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
                                notificationCenter = MacOsNotificationCenter.init(this@MacOsApplication)
                                didFinishLaunchingCompletableJob.complete()
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

    var notificationCenter: MacOsNotificationCenter? = null

//    val sound: Sound = Sound

    private val fontFamilyResolver: FontFamily.Resolver by lazy { createFontFamilyResolver() }

    private val pointerIconService: MacOsPointerIconService = MacOsPointerIconService
    internal val inputModeManager: InputModeManager = InputModeManagerImpl(InputMode.Touch) {
        pointerIconService.setHiddenUntilPointerMoves(it == InputMode.Keyboard)
        true
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

    override suspend fun stopAndJoin() {
        structuredQuitInProgress = false
        terminationInProgress = true
        try {
            resetState()
        } finally {
            withContext(MacOsKdtMainDispatcher.INSTANCE.immediate) {
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
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
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
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
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
            nativeWindow.windowId().destroyLightweightWindowId()
            desktopGpuContext.destroyMetalViewContext(viewContext)
        }
        if (structuredQuitInProgress && !hasEffectiveWindows()) {
            GrandCentralDispatch.dispatchOnMain(highPriority = false) {
                finishStructuredQuitIfNeeded()
            }
        }
    }

    internal fun finishStructuredQuitIfNeeded() {
        if (!structuredQuitInProgress || hasEffectiveWindows()) return

        logger.info { "Structured quit finished closing windows; continuing application shutdown" }
        terminationInProgress = true
        this.customQuit?.invoke() ?: runBlocking {
            stopAndJoin()
        }
    }

    private suspend fun resetState() {
        withContext(MacOsKdtMainDispatcher.INSTANCE.immediate) {
            windows.values.toList().forEach { it.dispose() }
            reusableNativeWindowResources.values.forEach { (nativeWindow, viewContext) ->
                nativeWindow.close()
                nativeWindow.windowId().destroyLightweightWindowId()
                desktopGpuContext.destroyMetalViewContext(viewContext)
            }
            reusableNativeWindowResources.clear()
            quitHandlers.clear()
        }
    }

    override var systemTheme: SystemTheme by mutableStateOf(SystemTheme.Unknown)
        private set

    override fun close() {
        runBlocking { stopAndJoin() }
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
