/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.desktop.headless

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeSchedulingDispatcher
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.ComposeUIDispatcherOverride
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.desktop.Application
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.Screen
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.activateApplication
import androidx.compose.ui.desktop.currentApplication
import androidx.compose.ui.desktop.deactivateApplication
import androidx.compose.ui.desktop.logging.logger
import androidx.compose.ui.desktop.removeApplication
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerIconService
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalPointerIconService
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.DpOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A windowless [Application] backend for tests: an in-process event-loop thread stands in for the
 * platform main thread ([ComposeUIDispatcher] resolves to it via [ComposeUIDispatcherOverride]),
 * the clipboard is in-memory, and [HeadlessUriHandler] records opened URLs into [FakeBrowser].
 *
 * Lifecycle protocol (ported from Noria's headless backend):
 * - [initialize] activates the application FIRST (so a rejected activation cannot clobber a live
 *   backend's dispatchers), then installs the event loop and dispatcher override; it may be called
 *   again after [resetForReuse] and then REUSES the same event loop.
 * - [resetForReuse] drains the event loop, uninstalls the dispatcher override and deactivates the
 *   application, but keeps the event loop alive for the next [initialize].
 * - [stopAndJoin] additionally closes the event loop and poisons the object permanently
 *   ([initialize] refuses to run after it).
 */
object HeadlessApplication : Application {
    private val lock = Any()
    private var shutdown = false
    private var initialized = false
    private var lastClosedEventLoopPendingTasksCount = 0

    private var devicePixelRatio: Double = 1.0
    private var uriHandler: UriHandler = HeadlessUriHandler()

    fun initialize(
        libraryFolderPath: String,
        devicePixelRatio: Double = 1.0,
        uriHandler: UriHandler = HeadlessUriHandler(),
    ): HeadlessApplication =
        synchronized(lock) {
            check(!shutdown) {
                "HeadlessApplication has already been shut down and cannot be reinitialized in the same process"
            }
            // Validate/activate BEFORE touching any dispatcher globals. activateApplication rejects
            // (throws IllegalStateException) when another backend already owns this JVM, and it has
            // no side effect that installEventLoop/configure depend on, so running it first means a
            // rejected activation leaves ComposeUIDispatcherOverride/ComposeSchedulingDispatcher (and
            // the live backend that owns them) untouched. Idempotent on reuse: activateApplication
            // accepts re-activating the same, already-active instance.
            activateApplication(this)
            if (!initialized) {
                installEventLoop(libraryFolderPath)
            }
            configure(devicePixelRatio, uriHandler)
            this
        }

    val current: HeadlessApplication
        get() {
            val application = runCatching { currentApplication() }.getOrNull()
            check(initialized && !shutdown && application === this) {
                "HeadlessApplication has not been initialized; last closed event loop had $lastClosedEventLoopPendingTasksCount pending tasks"
            }
            return this
        }

    private var eventLoopOrNull: HeadlessEventLoop? = null

    internal val eventLoop: HeadlessEventLoop
        get() = checkNotNull(eventLoopOrNull) { "HeadlessApplication has not been initialized" }

    private fun configure(
        devicePixelRatio: Double,
        uriHandler: UriHandler,
    ) {
        if (this.devicePixelRatio != devicePixelRatio) {
            check(windows.isEmpty()) {
                "HeadlessApplication devicePixelRatio cannot change while windows are still open"
            }
            this.devicePixelRatio = devicePixelRatio
            val defaultScreen = HeadlessScreen(devicePixelRatio = devicePixelRatio.toFloat())
            screens = mapOf(0 to defaultScreen)
        }
        this.uriHandler = uriHandler
    }

    override fun openUri(uri: String) {
        uriHandler.openUri(uri)
    }

    override var windows: Map<LightweightWindowId, HeadlessWindow> by mutableStateOf(emptyMap())
        internal set

    internal fun removeWindow(id: LightweightWindowId) {
        windows = windows - id
    }

    override var screens: Map<Int, Screen> by mutableStateOf(
        mapOf(0 to HeadlessScreen(devicePixelRatio = devicePixelRatio.toFloat())),
    )
        private set

    override val focusedWindow: Window?
        get() = windows.values.firstOrNull { it.isFocused }

    override var isActive: Boolean by mutableStateOf(true)
        private set

    override val systemTheme: SystemTheme = SystemTheme.Light

    private val quitHandlers = mutableMapOf<String, () -> Boolean>()

    val fontFamilyResolver: Lazy<FontFamily.Resolver> = lazy { createFontFamilyResolver() }

    // PointerIconService is an internal interface in this fork, so the property cannot be public;
    // desktopTest sees internals of desktopMain (friend compilation), which is all tests need.
    internal val pointerIconService: PointerIconService = HeadlessPointerIconService
    val inputModeManager: InputModeManager = InputModeManagerImpl(InputMode.Touch) {
        HeadlessPointerIconService.setHiddenUntilPointerMoves(it == InputMode.Keyboard)
        true
    }

    val hapticFeedback: HapticFeedback = NoopHapticFeedback()

    /**
     * Launches [stopAndJoin] on the UI dispatcher, i.e. the event-loop thread itself.
     *
     * Known degeneracy (documented, not fixed here — no behavior change): because shutdown then
     * runs ON the loop thread, the idle drain in [awaitEventLoopToBecomeIdle] counts the
     * still-running shutdown task in the event loop's pendingTasksCount, so the count never reaches
     * zero. The drain therefore always times out (~[HEADLESS_EVENT_LOOP_IDLE_TIMEOUT_MILLIS]ms) and
     * logs one spurious "dropping N queued tasks" warning before shutdown proceeds safely. The fix
     * would be an [HeadlessEventLoop.isCurrentThread] special-case that skips the barrier when
     * already on the loop thread, the way [awaitIdle] rejects that case outright.
     */
    @OptIn(DelicateCoroutinesApi::class)
    override fun quit() {
        GlobalScope.launch(ComposeUIDispatcher.immediate) {
            stopAndJoin()
        }
    }

    override fun close() {
        runBlocking { stopAndJoin() }
    }

    override fun putQuitHandler(id: String, quitHandler: () -> Boolean) {
        quitHandlers[id] = quitHandler
    }

    override fun removeQuitHandler(id: String) {
        quitHandlers.remove(id)
    }

    override suspend fun awaitWhenReady() {
        // TODO [pavel.sergeev] is the application immediately ready?
    }

    // Covariant return type so callers can reach HeadlessWindow-only members.
    override fun createWindow(
        session: ApplicationSession,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): HeadlessWindow = HeadlessWindow(
        application = this,
        devicePixelRatio = devicePixelRatio.toFloat(),
        session = session,
        onCloseRequest = onCloseRequest,
    )

    /** TestWindow entry point: wraps [coroutineScope] in a fresh [ApplicationSession]. */
    fun createWindow(coroutineScope: CoroutineScope): HeadlessWindow =
        createWindow(ApplicationSession(coroutineScope)) { }

    /**
     * Suspends until the event loop has no queued work left.
     *
     * This is what makes a step of the UI observable: the loop thread is the process's Compose UI
     * thread, so a frame's effect bodies, recomposer resumes and snapshot apply-notifications are
     * queued behind the render rather than finished with it. An assertion made before the queue
     * drains can see a half-applied frame.
     *
     * A single dispatch barrier is not enough — it clears one FIFO level, and work the frame queues
     * lands behind it — so this drains repeatedly until the queue is empty.
     *
     * Throws if the loop does not settle within [timeoutMillis], rather than returning quietly the
     * way shutdown does: a caller that is about to assert needs to know it is asserting against a
     * settled frame.
     *
     * [timeoutMillis] is a deadlock guard, not a performance assertion, so it is far longer than the
     * budget shutdown uses. It bounds *wall clock* while the frame's queued tail drains, and a slow
     * machine or a heavy tree makes that tail long — a tight budget here would surface as a
     * load-sensitive failure in every caller that steps frames, which is the opposite of the
     * determinism this exists to provide. Pass a shorter value only when a test is deliberately
     * asserting that the loop does *not* settle.
     */
    suspend fun awaitIdle(timeoutMillis: Long = HEADLESS_IDLE_WAIT_TIMEOUT_MILLIS) {
        // pendingTasksCount includes the task currently running, so from the loop thread itself the
        // count can never reach zero and this would only ever time out.
        check(eventLoopOrNull?.isCurrentThread() != true) {
            "awaitIdle() must not be called from the event-loop thread"
        }
        val pending = awaitEventLoopToBecomeIdle(timeoutMillis)
        check(pending == 0) {
            "Headless event loop did not become idle within ${timeoutMillis}ms; " +
                "$pending tasks still queued"
        }
    }

    override fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId) {
        // No-op for headless
    }

    override fun reuseWindow(
        id: LightweightWindowId,
        session: ApplicationSession,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): HeadlessWindow? = windows[id]?.apply {
        // The window's ComposeScene was built (in its constructor) from the original session's
        // coroutineContext and dataSourceContext, and both are fixed for the scene's lifetime.
        // Reassigning here does NOT retroactively rebind the scene's context: it only updates the
        // stored `session` field and reroutes future close requests through the new
        // `onCloseRequest` (see HeadlessWindow.requestClose).
        this.session = session
        this.onCloseRequest = onCloseRequest
    }

    override fun disposeReusableNativeWindowResources(id: LightweightWindowId) {
        // No-op for headless
    }

    override fun requestActivation() {
        isActive = true
    }

    override fun showEmojiAndSymbolsPopup() {
        // No-op for headless
    }

    override val nativeApplication: Any
        get() = Unit

    override fun invokeOnUiThread(block: () -> Unit) {
        eventLoop.dispatch { block() }
    }

    override fun isUiThread(): Boolean = eventLoop.isCurrentThread()

    @Composable
    override fun withCompositionLocal(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalUriHandler provides this@HeadlessApplication,
            LocalClipboard provides this@HeadlessApplication,
            LocalFontFamilyResolver provides fontFamilyResolver.value,
            LocalHapticFeedback provides hapticFeedback,
            LocalPointerIconService provides pointerIconService,
            LocalInputModeManager provides inputModeManager,
        ) {
            content()
        }
    }

    override suspend fun stopAndJoin() {
        try {
            resetState()
        } finally {
            warnIfEventLoopDidNotBecomeIdle(awaitEventLoopToBecomeIdle())
            recycleEventLoop()?.close(dropPendingTasks = true)
            removeApplication(this)
            synchronized(lock) {
                shutdown = true
            }
        }
    }

    override suspend fun resetForReuse() {
        try {
            resetState()
        } finally {
            warnIfEventLoopDidNotBecomeIdle(awaitEventLoopToBecomeIdle())
            deactivateEventLoop()
            deactivateApplication(this)
        }
    }

    private var clipboardContent: ClipEntry? = null

    override suspend fun getClipEntry(): ClipEntry? = clipboardContent

    override fun getClipEntrySync(): ClipEntry? = clipboardContent

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        clipEntry ?: return
        clipboardContent = clipEntry
    }

    override val nativeClipboard: Any = Unit

    private suspend fun resetState() {
        quitHandlers.clear()
        // Sessions are caller-owned (`runSession` structured concurrency) — no render-loop
        // registry to stop here; dispose whatever windows remain.
        windows.values.toList().forEach { it.dispose() }
        clipboardContent = null
        isActive = true
        // Font paragraph-cache reset: no compose-side hook yet; see spec §7 follow-up (a913dc9ea28ac).
    }

    private suspend fun awaitEventLoopToBecomeIdle(
        timeoutMillis: Long = HEADLESS_EVENT_LOOP_IDLE_TIMEOUT_MILLIS,
    ): Int {
        val currentEventLoop = synchronized(lock) { eventLoopOrNull } ?: return 0
        val becameIdle =
            withTimeoutOrNull(timeoutMillis.milliseconds) {
                while (currentEventLoop.pendingTasksCount > 0) {
                    val idleBarrier = CompletableDeferred<Unit>()
                    currentEventLoop.dispatch {
                        idleBarrier.complete(Unit)
                    }
                    idleBarrier.await()
                }
                true
            } ?: false

        return if (becameIdle) 0 else currentEventLoop.pendingTasksCount
    }

    private fun warnIfEventLoopDidNotBecomeIdle(pendingTasksAfterTimeout: Int) {
        if (pendingTasksAfterTimeout > 0) {
            logger.warn(
                "Timed out after ${HEADLESS_EVENT_LOOP_IDLE_TIMEOUT_MILLIS}ms waiting for the headless event loop to become idle; dropping $pendingTasksAfterTimeout queued tasks during shutdown",
            )
        }
    }

    private fun installEventLoop(libraryFolderPath: String) {
        val currentEventLoop = eventLoopOrNull
            ?: createHeadlessEventLoop(libraryFolderPath).also { eventLoopOrNull = it }
        ComposeUIDispatcherOverride = HeadlessMainDispatcher(currentEventLoop)
        ComposeSchedulingDispatcher = ComposeUIDispatcherOverride
        initialized = true
        lastClosedEventLoopPendingTasksCount = 0
    }

    private fun deactivateEventLoop() {
        synchronized(lock) {
            if (eventLoopOrNull == null) {
                return@synchronized
            }
            ComposeUIDispatcherOverride = null
            ComposeSchedulingDispatcher = null
            initialized = false
        }
    }

    private fun recycleEventLoop(): HeadlessEventLoop? =
        synchronized(lock) {
            val currentEventLoop = eventLoopOrNull ?: return@synchronized null
            lastClosedEventLoopPendingTasksCount = currentEventLoop.pendingTasksCount
            eventLoopOrNull = null
            ComposeUIDispatcherOverride = null
            ComposeSchedulingDispatcher = null
            initialized = false
            currentEventLoop
        }

    // ----- Event-injection API: tests drive windows without a real platform event source -----

    fun sendKeyDown(
        key: Key,
        windowId: LightweightWindowId,
        codePoint: Int?,
    ) {
        sendWindowEvent(Event.KeyDown(windowId, key, codePoint))
    }

    fun sendKeyUp(
        key: Key,
        windowId: LightweightWindowId,
        codePoint: Int?,
    ) {
        sendWindowEvent(Event.KeyUp(windowId, key, codePoint))
    }

    fun sendMouseDown(
        windowId: LightweightWindowId,
        button: PointerButton,
        location: DpOffset,
        timestamp: Double = nowEpochSeconds(),
    ) {
        sendWindowEvent(Event.MouseDown(windowId, button, location, timestamp))
    }

    fun sendMouseUp(
        windowId: LightweightWindowId,
        button: PointerButton,
        location: DpOffset,
        timestamp: Double = nowEpochSeconds(),
    ) {
        sendWindowEvent(Event.MouseUp(windowId, button, location, timestamp))
    }

    fun sendMouseMove(
        windowId: LightweightWindowId,
        location: DpOffset,
        timestamp: Double = nowEpochSeconds(),
    ) {
        sendWindowEvent(Event.MouseMoved(windowId, location, timestamp))
    }

    fun sendMouseEnter(
        windowId: LightweightWindowId,
        location: DpOffset,
        timestamp: Double = nowEpochSeconds(),
    ) {
        sendWindowEvent(Event.MouseEntered(windowId, location, timestamp))
    }

    fun sendMouseExit(
        windowId: LightweightWindowId,
        location: DpOffset,
        timestamp: Double = nowEpochSeconds(),
    ) {
        sendWindowEvent(Event.MouseExited(windowId, location, timestamp))
    }

    private fun sendWindowEvent(windowEvent: WindowEvent) {
        val window = windows[windowEvent.windowId] ?: return
        window.handleEvent(windowEvent)
    }
}

// Shutdown's budget: it is on the way out and warns rather than failing, so waiting long buys
// nothing.
private const val HEADLESS_EVENT_LOOP_IDLE_TIMEOUT_MILLIS = 1_000L

// [HeadlessApplication.awaitIdle]'s default: a deadlock guard for callers that are about to assert.
// It has to outlast the slowest legitimate frame tail on the slowest machine, because exceeding it
// fails the caller's test.
private const val HEADLESS_IDLE_WAIT_TIMEOUT_MILLIS = 30_000L
private val logger by lazy { logger<HeadlessApplication>() }
private fun nowEpochSeconds(): Double = System.currentTimeMillis() / 1000.0

internal object HeadlessPointerIconService : PointerIconService {
    override fun getIcon(): PointerIcon = PointerIcon.Default
    override fun setIcon(value: PointerIcon?) {}
    override fun getStylusHoverIcon(): PointerIcon? = null
    override fun setStylusHoverIcon(value: PointerIcon?) {}
    fun setHiddenUntilPointerMoves(hidden: Boolean) {}
    fun pushHide() {}
    fun popHide() {}
}

private class NoopHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}
