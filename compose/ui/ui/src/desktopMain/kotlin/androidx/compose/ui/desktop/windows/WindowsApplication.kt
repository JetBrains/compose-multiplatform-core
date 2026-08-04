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

@file:OptIn(ExperimentalAtomicApi::class)

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.desktop.KdtMainDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeSchedulingDispatcher
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.desktop.Application
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.ParkedWindowResources
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.deactivateApplication
import androidx.compose.ui.desktop.logging.KLoggers
import androidx.compose.ui.desktop.logging.logger
import androidx.compose.ui.desktop.removeApplication
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.DefaultHapticFeedback
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.desktop.win32.Appearance
import org.jetbrains.desktop.win32.Event
import org.jetbrains.desktop.win32.EventHandlerResult
import org.jetbrains.desktop.win32.KotlinDesktopToolkit
import org.jetbrains.desktop.win32.LogLevel
import org.jetbrains.desktop.win32.Screen

object WindowsApplication : Application, Clipboard by WindowsClipboard() {
    internal val logger = logger<WindowsApplication>()
    private val lock = Any()
    private var configuredLibraryFolderPath: Path? = null
    private var shutdown = false
    private var initialized = false
    private var structuredQuitInProgress = false
    private var terminationInProgress = false

    // stopAndJoin() is reachable from several paths (structured quit, JVM shutdown hook); the native
    // event loop must be stopped exactly once (see stopAndJoin()).
    private val eventLoopStopped = AtomicBoolean(false)

    private var openUrls: (List<String>) -> Unit = {}
    private var uriHandler: UriHandler = WindowsUriHandler()
    private var customQuit: (() -> Boolean)? = null

    internal fun initialize(
        identifier: String,
        openUrls: (List<String>) -> Unit,
        libraryFolderPath: Path,
        logFolderPath: Path,
        uriHandler: UriHandler,
        customQuit: (() -> Boolean)?,
    ) {
        // Compose's internal scheduling must run on this backend's UI thread, the same one that mutates
        // LayoutNode state. ComposeUIDispatcher already resolves the correct per-platform KDT dispatcher.
        ComposeSchedulingDispatcher = ComposeUIDispatcher
        synchronized(lock) {
            check(!shutdown) {
                "WindowsApplication has already been shut down and cannot be reinitialized in the same process"
            }
            configuredLibraryFolderPath?.let { configuredLibraryFolderPath ->
                check(configuredLibraryFolderPath == libraryFolderPath) {
                    "WindowsApplication is already initialized for $configuredLibraryFolderPath and cannot be reinitialized for $libraryFolderPath"
                }
            } ?: run {
                startRuntime(libraryFolderPath, logFolderPath)
                configuredLibraryFolderPath = libraryFolderPath
                initialized = true
            }
            configure(openUrls, uriHandler, customQuit)
        }
    }

    internal fun current(): WindowsApplication {
        check(initialized && !shutdown) { "WindowsApplication has not been initialized" }
        return this
    }

    private fun configure(
        openUrls: (List<String>) -> Unit,
        uriHandler: UriHandler,
        customQuit: (() -> Boolean)?,
    ) {
        this.openUrls = openUrls
        this.uriHandler = uriHandler
        this.customQuit = customQuit
    }

    private fun startRuntime(
        libraryFolderPath: Path,
        logFolderPath: Path,
    ) {
        // Do NOT force `skiko.library.path` to the KDT-extracted folder — skiko only ships its own
        // native dll inside its runtime jar on the classpath, not inside `kdt-extracted`.
        val logFilePath = logFolderPath.resolve("WindowsApplication").resolve("WindowsApplication.log")
        // Native logger init fails if the parent directory doesn't exist yet, so make sure it's there.
        Files.createDirectories(logFilePath.parent)
        KotlinDesktopToolkit.init(
            libraryFolderPath = libraryFolderPath,
            logFilePath = logFilePath,
            consoleLogLevel = LogLevel.Info,
            fileLogLevel = LogLevel.Debug,
            appenderInterface = KdtLoggerAppender(
                KLoggers.logger("androidx.compose.ui.desktop.windows.KdtLogger"),
            ),
        )
        val nativeApplication = org.jetbrains.desktop.win32.Application()
        nativeApplicationOrNull = nativeApplication
        didFinishLaunchingCompletableJob = Job()
        eventLoopThread = thread(start = true, name = "WindowsApplication Event Loop") {
            try {
                // The onStartup queue is win32's DidFinishLaunching substitute: it runs once on the
                // KDT/STA thread before the event loop, populating the reactive screens/theme state
                // and completing the launch job.
                nativeApplication.onStartup {
                    Thread.currentThread().name = "WindowsApplication Main Thread (KDT)"
                    screens = Screen.allScreens().mapIndexed { index, screen ->
                        index to WindowsScreen(screen)
                    }.toMap()
                    systemTheme = Appearance.getCurrent().toSystemTheme()
                    didFinishLaunchingCompletableJob.complete()
                }
                nativeApplication.runEventLoop { windowId, event ->
                    when (event) {
                        is Event.SystemAppearanceChange -> {
                            systemTheme = event.newAppearance.toSystemTheme()
                            EventHandlerResult.Stop
                        }
                        else -> {
                            // Window-addressed events; a window whose id no longer resolves (mid-
                            // creation, already disposed) lets the toolkit default handling run.
                            windowId.toLightweightWindowId()
                                ?.let { windows[it] }
                                ?.handleEvent(event)
                                ?: EventHandlerResult.Continue
                        }
                    }
                }
            } catch (throwable: Throwable) {
                didFinishLaunchingCompletableJob.completeExceptionally(throwable)
            }
        }
    }

    override fun openUri(uri: String) {
        uriHandler.openUri(uri)
    }

    private var nativeApplicationOrNull: org.jetbrains.desktop.win32.Application? = null
    override val nativeApplication: org.jetbrains.desktop.win32.Application
        get() = checkNotNull(nativeApplicationOrNull) { "WindowsApplication has not been initialized" }

    override fun invokeOnUiThread(block: () -> Unit) {
        nativeApplication.invokeOnDispatcher { block() }
    }

    override fun isUiThread(): Boolean = nativeApplication.isDispatcherThread()

    // Clipboard access (getClipEntry/setClipEntry/nativeClipboard/getClipEntrySync) is provided by
    // the delegated WindowsClipboard (OLE over Win32Clipboard/DataObject), mirroring how
    // MacOsApplication delegates to MacOsClipboard.

    private var didFinishLaunchingCompletableJob: CompletableJob = Job()

    override var screens: Map<Int, WindowsScreen> by mutableStateOf(emptyMap())
        private set

    override var windows: Map<LightweightWindowId, WindowsWindow> by mutableStateOf(emptyMap())
        internal set

    override val focusedWindow: Window? get() = windows.values.firstOrNull { it.isFocused }

    internal data class ReusableNativeWindowResources(
        val nativeWindow: org.jetbrains.desktop.win32.Window,
        val angleViewContext: AngleViewContext,
        val pointerIconService: WindowsPointerIconService,
        val inputMode: InputMode,
    )

    internal val reusableNativeWindowResources =
        ParkedWindowResources<ReusableNativeWindowResources>(warn = { logger.warn { it } })

    private fun hasEffectiveWindows(): Boolean =
        windows.isNotEmpty() || !reusableNativeWindowResources.isEmpty

    internal fun debugWindowStateSummary(): String =
        "windows=${windows.keys}, reusable=${reusableNativeWindowResources.keys}, " +
            "structuredQuitInProgress=$structuredQuitInProgress"

    private var eventLoopThread: Thread? = null

    private val fontFamilyResolver: FontFamily.Resolver by lazy { createFontFamilyResolver() }

    override val isActive: Boolean get() = true

    override fun requestActivation() {
        // Windows doesn't have a direct equivalent of activateIgnoringOtherApps.
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
        // currently, we must evaluate ALL handlers because SafeQuitInterceptor may quit too early otherwise
        if (quitHandlers.values.fold(true) { accumulator, shouldTerminate -> shouldTerminate() and accumulator }) {
            requestStructuredQuit()
        }
    }

    override fun showEmojiAndSymbolsPopup() {
        // TODO: Windows emoji picker (Win + .)
    }

    override var systemTheme: SystemTheme by mutableStateOf(SystemTheme.Unknown)
        private set

    @Composable
    override fun withCompositionLocal(content: @Composable (() -> Unit)) {
        // The pointer-icon service and input-mode manager are per-window on win32 (each HWND owns its
        // cursor via Window.setCursor), so — unlike macOS's app-global services — they are provided by
        // the window scene, not here. Only the genuinely application-global locals are provided here.
        CompositionLocalProvider(
            LocalUriHandler provides this@WindowsApplication,
            LocalClipboard provides this@WindowsApplication,
            LocalFontFamilyResolver provides fontFamilyResolver,
            LocalHapticFeedback provides remember { DefaultHapticFeedback() },
        ) {
            content()
        }
    }

    override suspend fun stopAndJoin() {
        structuredQuitInProgress = false
        terminationInProgress = true
        try {
            resetState()
        } finally {
            // stopAndJoin() is reachable from several paths (structured quit, JVM shutdown hook); make
            // sure the native event loop is stopped exactly once.
            if (eventLoopStopped.compareAndSet(expectedValue = false, newValue = true)) {
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
        session: ApplicationSession,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): Window {
        return WindowsWindow(
            this,
            session,
            onCloseRequest = onCloseRequest,
        )
    }

    override fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId) {
        val window = windows[id]
        if (window == null) {
            logger.warn {
                "prepareNativeWindowResourcesForReuse: no active window found for id=$id; ${debugWindowStateSummary()}"
            }
            return
        }
        // Park the native resources that must outlive the outgoing wrapper so the next window at
        // this id can adopt them: the surviving HWND, its ANGLE/OpenGL context, and — crucially —
        // the SAME pointer-icon service instance (it owns the Cursor.hide()/show() balance; a fresh
        // instance could never issue the show() undoing an outstanding hide()) plus the current
        // input mode. WindowsWindow.dispose() then sees this parked entry via peekContains and skips
        // destroying the HWND/context.
        reusableNativeWindowResources.park(
            id,
            ReusableNativeWindowResources(
                nativeWindow = window.nativeWindow,
                angleViewContext = window.angleViewContext,
                pointerIconService = window.pointerIconService,
                inputMode = window.inputModeManager.inputMode,
            ),
        )
    }

    override fun reuseWindow(
        id: LightweightWindowId,
        session: ApplicationSession,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): Window? = reusableNativeWindowResources.reuseParkedResources(
        id,
        onMissing = {
            logger.warn {
                "reuseWindow: no reusable native resources found for id=$id session=${session.hashCode()}; " +
                    debugWindowStateSummary()
            }
        },
        disposeOutgoing = {
            logger.debug { "Reusing window $id" }
            windows[id]?.dispose()
        },
        build = { resources ->
            WindowsWindow(
                this,
                session,
                onCloseRequest,
                reusedNativeWindow = resources.nativeWindow,
                reusedAngleViewContext = resources.angleViewContext,
                reusedPointerIconService = resources.pointerIconService,
                reusedInputMode = resources.inputMode,
            )
        },
    )

    override fun disposeReusableNativeWindowResources(id: LightweightWindowId) {
        reusableNativeWindowResources.disposeWith(id) { resources ->
            resources.angleViewContext.close()
            resources.nativeWindow.destroy()
            resources.nativeWindow.destroyLightweightWindowId()
        }
        if (structuredQuitInProgress && !hasEffectiveWindows()) {
            finishStructuredQuitIfNeeded()
        }
    }

    private fun requestStructuredQuit() {
        if (terminationInProgress || structuredQuitInProgress) return

        val windowsToClose = windows.values.toList()
        if (windowsToClose.isNotEmpty()) {
            structuredQuitInProgress = true
            logger.info { "Quit approved; requesting structured close for ${windowsToClose.size} window(s)" }
            windowsToClose.forEach { it.requestClose(WindowCloseRequestReason.ApplicationQuit) }
            return
        }

        if (!reusableNativeWindowResources.isEmpty) {
            structuredQuitInProgress = true
            logger.info {
                "Quit approved; waiting for ${reusableNativeWindowResources.keys.size} reusable window resource(s) to drain"
            }
            return
        }

        continueTerminationAsync()
    }

    internal fun finishStructuredQuitIfNeeded() {
        if (!structuredQuitInProgress || hasEffectiveWindows()) return
        logger.info { "Structured quit finished closing windows; continuing application shutdown" }
        continueTerminationAsync()
    }

    private fun continueTerminationAsync() {
        if (terminationInProgress) return
        structuredQuitInProgress = false
        terminationInProgress = true
        val customQuit = customQuit
        // A separate thread, unlike macOS's in-place runBlocking: this is reached ON the win32
        // dispatcher thread (the last WindowClosed arrives there), where stopAndJoin()'s
        // eventLoopThread.join() would self-deadlock.
        thread(start = true, isDaemon = true, name = "WindowsApplication Structured Quit") {
            customQuit?.invoke() ?: runBlocking {
                stopAndJoin()
            }
        }
    }

    private suspend fun resetState() {
        // Window disposal reaches OLE calls (e.g. DragDropManager.revokeDropTarget, which calls
        // RevokeDragDrop) that require the same STA thread that registered them, i.e. the application
        // dispatcher thread. Without this hop, resetState() (called from resetForReuse()/stopAndJoin()
        // on whatever thread the caller's coroutine is on) marshals OLE calls onto the wrong thread and
        // fails with RPC_E_WRONG_THREAD (0x8001010E).
        withContext(KdtMainDispatcher.INSTANCE.immediate) {
            windows.values.toList().forEach { it.dispose() }
            reusableNativeWindowResources.drainWith { resources ->
                resources.angleViewContext.close()
                resources.nativeWindow.destroy()
                resources.nativeWindow.destroyLightweightWindowId()
            }
            quitHandlers.clear()
        }
    }

    override fun close() {
        runBlocking { stopAndJoin() }
    }
}

internal fun Appearance.toSystemTheme(): SystemTheme = when (this) {
    Appearance.Dark -> SystemTheme.Dark
    Appearance.Light -> SystemTheme.Light
}

/**
 * The load-bearing lightweight-window reuse ordering, factored out of the native-touching
 * [WindowsApplication.reuseWindow] so it is unit-testable without a real HWND (the WindowsWindow ctor
 * needs a native window). Mirrors macOS's `reuseWindow` shape:
 *
 * 1. presence-check via [ParkedWindowResources.peekContains]; if nothing is parked for [id], run
 *    [onMissing] (which warns) and return null;
 * 2. [disposeOutgoing] the old wrapper **while its entry is still parked** — this ordering is what
 *    lets `WindowsWindow.dispose()`'s peekContains gate skip destroying the HWND/ANGLE context that
 *    is about to be reused (and lets the new wrapper re-register the OLE drop target on the surviving
 *    HWND). Taking the entry first would make dispose() tear the native window down;
 * 3. only then [ParkedWindowResources.take] the parked entry and [build] the new wrapper over it.
 */
internal fun <R : Any, W : Any> ParkedWindowResources<R>.reuseParkedResources(
    id: LightweightWindowId,
    onMissing: () -> Unit,
    disposeOutgoing: () -> Unit,
    build: (R) -> W,
): W? {
    if (!peekContains(id)) {
        onMissing()
        return null
    }
    disposeOutgoing()
    val resources = take(id) ?: return null
    return build(resources)
}

internal fun currentWindowsNativeApplication(): org.jetbrains.desktop.win32.Application =
    WindowsApplication.current().nativeApplication
