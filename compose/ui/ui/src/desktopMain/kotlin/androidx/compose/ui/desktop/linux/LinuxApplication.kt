@file:OptIn(ExperimentalComposeUiApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.linux

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
import androidx.compose.ui.desktop.Application
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.DefaultDoubleClickDistance
import androidx.compose.ui.desktop.DefaultDragThreshold
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.deactivateApplication
import androidx.compose.ui.desktop.getDataForLinuxMimeType
import androidx.compose.ui.desktop.logging.logger
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
import androidx.compose.ui.window.WindowDecoration
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.jetbrains.desktop.linux.ApplicationConfig
import org.jetbrains.desktop.linux.ColorSchemeValue
import org.jetbrains.desktop.linux.DataSource
import org.jetbrains.desktop.linux.DesktopSetting
import org.jetbrains.desktop.linux.DesktopTitlebarAction
import org.jetbrains.desktop.linux.DragAndDropQueryResponse
import org.jetbrains.desktop.linux.Event
import org.jetbrains.desktop.linux.EventHandlerResult
import org.jetbrains.desktop.linux.KotlinDesktopToolkit
import org.jetbrains.desktop.linux.LogLevel
import org.jetbrains.desktop.linux.RequestId

object LinuxApplication : Application {
    internal val logger = logger<LinuxApplication>()
    private val lock = Any()
    private var configuredIdentifier: String? = null
    private var configuredLibraryFolderPath: Path? = null
    private var shutdown = false
    private var initialized = false
    private var structuredQuitInProgress = false
    private var terminationInProgress = false

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
                "LinuxApplication has already been shut down and cannot be reinitialized in the same process"
            }
            configuredIdentifier?.let { configuredIdentifier ->
                check(configuredIdentifier == identifier) {
                    "LinuxApplication is already initialized for '$configuredIdentifier' and cannot be reinitialized for '$identifier'"
                }
            } ?: run {
                this.identifier = identifier
            }
            configuredLibraryFolderPath?.let { configuredLibraryFolderPath ->
                check(configuredLibraryFolderPath == libraryFolderPath) {
                    "LinuxApplication is already initialized for $configuredLibraryFolderPath and cannot be reinitialized for $libraryFolderPath"
                }
            } ?: run {
                startRuntime(libraryFolderPath, logFolderPath)
                configuredLibraryFolderPath = libraryFolderPath
                configuredIdentifier = identifier
                initialized = true
            }
            configure(uriHandler, customQuit)
        }
    }

    internal fun current(): LinuxApplication {
        check(initialized && !shutdown) { "LinuxApplication has not been initialized" }
        return this
    }

    internal lateinit var identifier: String

    private var uriHandler: UriHandler = LinuxUriHandler()
    private var customQuit: (() -> Boolean)? = null

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

    private fun startRuntime(
        libraryFolderPath: Path,
        logFolderPath: Path,
    ) {
        // Do NOT force `skiko.library.path` to the KDT-extracted folder — skiko only ships its own
        // native dylib inside its runtime jar on the classpath, not inside `kdt-extracted`.
        val logFilePath = logFolderPath.resolve("LinuxApplication").resolve("LinuxApplication.log")
        // Native logger init fails if the parent directory doesn't exist yet, so make sure it's there.
        Files.createDirectories(logFilePath.parent)
        KotlinDesktopToolkit.init(
            libraryFolderPath = libraryFolderPath,
            logFilePath = logFilePath,
            consoleLogLevel = LogLevel.Info,
            fileLogLevel = LogLevel.Debug,
        )
        val nativeApplication = org.jetbrains.desktop.linux.Application()
        nativeApplicationOrNull = nativeApplication
        clipboardOrNull = LinuxClipboard(nativeApplication)
        notificationCenterOrNull = LinuxNotificationCenter(nativeApplication)
        didFinishLaunchingCompletableJob = Job()
        eventLoopThread = thread(start = true, name = "LinuxApplication Event Loop") {
            try {
                nativeApplication.runEventLoop(
                    ApplicationConfig(
                        eventHandler = ::handleEvent,
                        queryDragAndDropTarget = { query ->
                            windows[query.windowId.toLightweightWindowId()]
                                ?.queryDragAndDropTarget(query)
                                ?: DragAndDropQueryResponse(emptyList())
                        },
                        getDataTransferData = { dataSource, mimeType ->
                            when (dataSource) {
                                DataSource.Clipboard -> clipboard.getMimeData(mimeType)
                                DataSource.DragAndDrop -> activeDragSource?.itemsEntry?.getDataForLinuxMimeType(mimeType)
                                DataSource.PrimarySelection ->
                                    if (middleClickPasteEnabled) {
                                        clipboard.getPrimarySelectionData(mimeType)
                                    } else {
                                        null
                                    }
                            }
                        },
                    ),
                )
            } catch (throwable: Throwable) {
                didFinishLaunchingCompletableJob.completeExceptionally(throwable)
            }
        }
    }

    private var nativeApplicationOrNull: org.jetbrains.desktop.linux.Application? = null
    override val nativeApplication: org.jetbrains.desktop.linux.Application
        get() = checkNotNull(nativeApplicationOrNull) { "LinuxApplication has not been initialized" }

    @Composable
    override fun withCompositionLocal(content: @Composable (() -> Unit)) {
        CompositionLocalProvider(
            LocalUriHandler provides this@LinuxApplication,
            LocalClipboard provides this@LinuxApplication,
            LocalFontFamilyResolver provides fontFamilyResolver,
            LocalHapticFeedback provides remember { DefaultHapticFeedback() },
        ) {
            content()
        }
    }

    internal data class ActiveDragSource(
        val windowId: LightweightWindowId,
        val itemsEntry: ClipboardItemsEntry,
        val supportedActions: Set<DragAndDropTransferAction>,
        val onTransferCompleted: (DragAndDropTransferAction?) -> Unit,
        val dragIconPngBytes: ByteArray?,
    )

    internal var activeDragSource: ActiveDragSource? = null

    private var clipboardOrNull: LinuxClipboard? = null
    internal val clipboard: LinuxClipboard
        get() = checkNotNull(clipboardOrNull) { "LinuxApplication has not been initialized" }
    private var notificationCenterOrNull: LinuxNotificationCenter? = null
    val notificationCenter: LinuxNotificationCenter
        get() = checkNotNull(notificationCenterOrNull) { "LinuxApplication has not been initialized" }

    private val fontFamilyResolver: FontFamily.Resolver by lazy { createFontFamilyResolver() }

    private var didFinishLaunchingCompletableJob: CompletableJob = Job()
    private val quitHandlers = ConcurrentHashMap<String, () -> Boolean>()
    private val pendingActivationRequests = ConcurrentHashMap<RequestId, LightweightWindowId>()
    private val nextWindowId = AtomicLong(1L)
    private var keyboardFocusWindowId: LightweightWindowId? = null

    override var screens: Map<Int, LinuxScreen> by mutableStateOf(emptyMap())
        private set

    override var windows: SnapshotStateMap<LightweightWindowId, LinuxWindow> = mutableStateMapOf()
        internal set

    override val focusedWindow: Window?
        get() = windows.values.firstOrNull { it.isFocused }

    override var systemTheme: SystemTheme by mutableStateOf(SystemTheme.Unknown)
        private set

    @ExperimentalComposeUiApi
    internal var customTitleBarLayout:
        Pair<List<WindowDecoration.TitleBarElement>, List<WindowDecoration.TitleBarElement>>? by mutableStateOf(
        null,
    )
        private set
    internal var titleBarDoubleClickAction: DesktopTitlebarAction by mutableStateOf(
        DesktopTitlebarAction.ToggleMaximize,
    )
        private set
    internal var titleBarMiddleClickAction: DesktopTitlebarAction by mutableStateOf(
        DesktopTitlebarAction.None,
    )
        private set
    internal var titleBarRightClickAction: DesktopTitlebarAction by mutableStateOf(
        DesktopTitlebarAction.Menu,
    )
        private set
    override var dragThreshold: Dp by mutableStateOf(DefaultDragThreshold)
        private set
    override var doubleClickDistance: Dp by mutableStateOf(DefaultDoubleClickDistance)
        private set
    internal var doubleClickIntervalMillis: Long by mutableStateOf(500L)
        private set
    private var middleClickPasteEnabled by mutableStateOf(true)
    private var cursorTheme: String? = null
    private var cursorSize: UInt? = null

    override fun getClipEntrySync(): ClipEntry = clipboard.getClipEntrySync()

    override suspend fun getClipEntry(): ClipEntry = clipboard.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
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

    private var eventLoopThread: Thread? = null

    private fun handleEvent(event: Event): EventHandlerResult {
        return try {
            when (event) {
                Event.ApplicationStarted -> {
                    screens = nativeApplication.allScreens().screens.mapIndexed { index, screen ->
                        index to LinuxScreen(screen)
                    }.toMap()
                    didFinishLaunchingCompletableJob.complete()
                    EventHandlerResult.Stop
                }

                Event.ApplicationWillTerminate -> EventHandlerResult.Continue

                Event.ApplicationWantsToTerminate -> {
                    if (quitHandlers.values.fold(true) { accumulator, quitHandler -> quitHandler() and accumulator }) {
                        requestStructuredQuit()
                    }
                    EventHandlerResult.Continue
                }

                is Event.DisplayConfigurationChange -> {
                    screens = event.screens.screens.mapIndexed { index, screen ->
                        index to LinuxScreen(screen)
                    }.toMap()
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
                        }

                        is DesktopSetting.DoubleClickInterval -> {
                            doubleClickIntervalMillis = setting.value.inWholeMilliseconds
                        }

                        is DesktopSetting.MiddleClickPaste -> {
                            middleClickPasteEnabled = setting.value
                        }

                        is DesktopSetting.TitlebarLayout -> {
                            customTitleBarLayout = setting.value.toCustomTitleBarLayout()
                        }

                        is DesktopSetting.ActionDoubleClickTitlebar -> {
                            titleBarDoubleClickAction = setting.value
                        }

                        is DesktopSetting.ActionMiddleClickTitlebar -> {
                            titleBarMiddleClickAction = setting.value
                        }

                        is DesktopSetting.ActionRightClickTitlebar -> {
                            titleBarRightClickAction = setting.value
                        }

                        is DesktopSetting.CursorTheme -> {
                            cursorTheme = setting.value
                            applyCursorThemeIfReady()
                        }

                        is DesktopSetting.CursorSize -> {
                            cursorSize = setting.value
                            applyCursorThemeIfReady()
                        }

                        else -> {}
                    }
                    EventHandlerResult.Stop
                }

                is Event.DataTransferCancelled -> {
                    when (event.dataSource) {
                        DataSource.Clipboard -> clipboard.clearClipboardData()
                        DataSource.PrimarySelection -> clipboard.clearPrimarySelectionData()
                        DataSource.DragAndDrop -> {
                            activeDragSource?.onTransferCompleted(null)
                            activeDragSource = null
                        }
                    }
                    EventHandlerResult.Stop
                }

                is Event.DataTransferAvailable -> {
                    clipboard.onDataTransferAvailable(event)
                    EventHandlerResult.Stop
                }

                is Event.DataTransfer -> {
                    if (clipboard.onDataReceived(event)) {
                        EventHandlerResult.Stop
                    } else {
                        EventHandlerResult.Continue
                    }
                }
                is Event.ActivationTokenResponse -> {
                    pendingActivationRequests.remove(event.requestId)?.let { windowId ->
                        windows[windowId]?.activate(event.token)
                    }
                    EventHandlerResult.Stop
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
                    windows[keyboardFocusWindowId]?.handleEvent(event)
                        ?: EventHandlerResult.Continue
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
                        LinuxWindow.drawDragIcon(event, source.dragIconPngBytes)
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

                is Event.WindowClosed -> {
                    val windowId = event.windowId.toLightweightWindowId()
                    windows[windowId]?.let { window ->
                        windows.remove(windowId)
                        window.onClosed()
                    }
                    EventHandlerResult.Stop
                }

                is Event.WindowCloseRequest,
                is Event.WindowConfigure,
                is Event.WindowDraw,
                is Event.WindowScaleChanged,
                is Event.WindowScreenChange,
                is Event.TextInputAvailability,
                is Event.MouseDown,
                is Event.MouseUp,
                is Event.MouseMoved,
                is Event.MouseEntered,
                is Event.MouseExited,
                is Event.ScrollWheel,
                is Event.DropPerformed,
                is Event.DragAndDropLeave,
                    -> {
                    event.windowIdOrNull()
                        ?.let { windowId -> windows[windowId]?.handleEvent(event) }
                        ?: EventHandlerResult.Continue
                }

                is Event.FileChooserResponse -> {
                    if (windows.values.any { it.handleEvent(event) == EventHandlerResult.Stop }) {
                        EventHandlerResult.Stop
                    } else {
                        EventHandlerResult.Continue
                    }
                }

                else -> EventHandlerResult.Continue
            }
        } catch (throwable: Throwable) {
            logger.error(throwable) { "Failed to handle Linux KDT event $event" }
            EventHandlerResult.Continue
        }
    }

    private fun applyCursorThemeIfReady() {
        val theme = cursorTheme ?: return
        val size = cursorSize ?: return
        nativeApplication.setCursorTheme(theme, size)
    }

    internal fun allocateNativeWindowId(): Long = nextWindowId.getAndIncrement()

    internal fun requestWindowActivation(windowId: LightweightWindowId) {
        val nativeWindow = windows[windowId]?.nativeWindow ?: return
        onEventLoopAsync {
            nativeWindow.requestInternalActivationToken()?.also { requestId ->
                pendingActivationRequests[requestId] = windowId
            }
        }
    }

    override val isActive: Boolean
        get() = true

    override fun requestActivation() {
        (focusedWindow as? LinuxWindow)?.requestFocusAndBringToFront()
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

    override fun showEmojiAndSymbolsPopup() {}

    override fun close() {
        runBlocking { stopAndJoin() }
    }

    override suspend fun stopAndJoin() {
        structuredQuitInProgress = false
        terminationInProgress = true
        try {
            resetState()
        } finally {
            nativeApplication.stopEventLoop()
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
        val window = LinuxWindow(this, scene, onCloseRequest)
        windows[window.id] = window
        return window
    }

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
        thread(start = true, isDaemon = true, name = "LinuxApplication Structured Quit") {
            customQuit?.invoke() ?: runBlocking {
                stopAndJoin()
            }
        }
    }

    private suspend fun resetState() {
        windows.values.toList().forEach { it.dispose() }
        activeDragSource = null
        keyboardFocusWindowId = null
        pendingActivationRequests.clear()
        quitHandlers.clear()
        // The native Wayland runtime persists across resetForReuse() and window removal is processed
        // by later event-loop iterations after close has been requested. Reusing Kotlin-side window IDs
        // before native state has drained can collide with still-live native entries, so Linux window
        // IDs must stay monotonic for the lifetime of the process.
    }

    internal fun onEventLoopAsync(block: org.jetbrains.desktop.linux.Application.() -> Unit) {
        val nativeApplication = nativeApplication
        if (nativeApplication.isEventLoopThread()) {
            nativeApplication.block()
        } else {
            nativeApplication.runOnEventLoopAsync {
                nativeApplication.block()
            }
        }
    }
}

internal fun Long.toLightweightWindowId(): LightweightWindowId = LightweightWindowId(this)

internal fun org.jetbrains.desktop.linux.DragAndDropAction.toComposeActionOrNull():
    DragAndDropTransferAction? =
    when (this) {
        org.jetbrains.desktop.linux.DragAndDropAction.Copy -> DragAndDropTransferAction.Copy
        org.jetbrains.desktop.linux.DragAndDropAction.Move -> DragAndDropTransferAction.Move
    }

private fun Event.windowIdOrNull(): LightweightWindowId? =
    when (this) {
        is Event.WindowCloseRequest -> windowId.toLightweightWindowId()
        is Event.WindowConfigure -> windowId.toLightweightWindowId()
        is Event.WindowDraw -> windowId.toLightweightWindowId()
        is Event.WindowScaleChanged -> windowId.toLightweightWindowId()
        is Event.WindowScreenChange -> windowId.toLightweightWindowId()
        is Event.TextInputAvailability -> windowId.toLightweightWindowId()
        is Event.MouseDown -> windowId.toLightweightWindowId()
        is Event.MouseUp -> windowId.toLightweightWindowId()
        is Event.MouseMoved -> windowId.toLightweightWindowId()
        is Event.MouseEntered -> windowId.toLightweightWindowId()
        is Event.MouseExited -> windowId.toLightweightWindowId()
        is Event.ScrollWheel -> windowId.toLightweightWindowId()
        is Event.DropPerformed -> windowId.toLightweightWindowId()
        is Event.DragAndDropLeave -> windowId.toLightweightWindowId()
        else -> null
    }

@OptIn(ExperimentalComposeUiApi::class)
private fun String.toCustomTitleBarLayout():
    Pair<List<WindowDecoration.TitleBarElement>, List<WindowDecoration.TitleBarElement>> =
    split(':', limit = 2)
        .let { parts -> listOf(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }) }
        .map { part ->
            part.split(',')
                .filter(String::isNotEmpty)
                .map(::toTitleBarElement)
        }
        .let { it[0] to it[1] }

@OptIn(ExperimentalComposeUiApi::class)
private fun toTitleBarElement(name: String): WindowDecoration.TitleBarElement =
    when (name) {
        "appmenu" -> WindowDecoration.TitleBarElement.AppMenu
        "icon" -> WindowDecoration.TitleBarElement.Icon
        "spacer" -> WindowDecoration.TitleBarElement.Spacer
        "minimize" -> WindowDecoration.TitleBarElement.MinimizeButton
        "maximize" -> WindowDecoration.TitleBarElement.MaximizeButton
        "close" -> WindowDecoration.TitleBarElement.CloseButton
        else -> WindowDecoration.TitleBarElement.Spacer
    }
