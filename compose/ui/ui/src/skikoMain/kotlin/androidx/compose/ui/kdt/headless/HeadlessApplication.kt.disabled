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

@file:OptIn(ExperimentalTime::class)

package androidx.compose.ui.kdt.headless

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerIconService
import androidx.compose.ui.kdt.Application
import androidx.compose.ui.kdt.KdtMainDispatcherFactory
import androidx.compose.ui.kdt.LightweightWindowId
import androidx.compose.ui.kdt.Scene
import androidx.compose.ui.kdt.Screen
import androidx.compose.ui.kdt.Window
import androidx.compose.ui.kdt.activateApplication
import androidx.compose.ui.kdt.deactivateApplication
import androidx.compose.ui.kdt.logging.logger
import androidx.compose.ui.kdt.removeApplication
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.makeSynchronizedObject
import androidx.compose.ui.platform.synchronized
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.DpOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@ExperimentalTime
object HeadlessApplication : Application {
    private val lock = makeSynchronizedObject()
    private var shutdown = false
    private var initialized = false
    private var lastClosedEventLoopPendingTasksCount = 0

    private var devicePixelRatio: Double = 1.0
    private var uriHandler: UriHandler = HeadlessUriHandler()

    fun initialize(
        devicePixelRatio: Double = 1.0,
        uriHandler: UriHandler = HeadlessUriHandler(),
    ): HeadlessApplication =
        synchronized(lock) {
            check(!shutdown) {
                "HeadlessApplication has already been shut down and cannot be reinitialized in the same process"
            }
            if (!initialized) {
                installEventLoop()
            }
            configure(devicePixelRatio, uriHandler)
            activateApplication(this)
            this
        }

    val current: HeadlessApplication
        get() {
            check(initialized && !shutdown) {
                "HeadlessApplication has not been initialized; last closed event loop had $lastClosedEventLoopPendingTasksCount pending tasks"
            }
            return this
        }

    private var eventLoopOrNull: HeadlessEventLoop? = null

    private val eventLoop: HeadlessEventLoop
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

    override val windows = mutableStateMapOf<LightweightWindowId, HeadlessWindow>()

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

    val fontFamilyResolver: FontFamily.Resolver by lazy { createFontFamilyResolver() }

    private val pointerIconService: HeadlessPointerIconService = HeadlessPointerIconService
    internal val inputModeManager: InputModeManager = InputModeManagerImpl(InputMode.Touch) {
        pointerIconService.setHiddenUntilPointerMoves(it == InputMode.Keyboard)
        true
    }

    val hapticFeedback: HapticFeedback = NoopHapticFeedback()

    override fun quit() {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            stopAndJoin()
        }
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

    fun createWindow(coroutineScope: CoroutineScope): HeadlessWindow {
        return createHeadlessWindow(coroutineScope, null) {}
    }

    override fun createWindow(
        scene: Scene<*>,
        onCloseRequest: () -> Unit,
    ): Window {
        return createHeadlessWindow(scene.coroutineScope, scene, onCloseRequest)
    }

    private fun createHeadlessWindow(
        scope: CoroutineScope,
        scene: Scene<*>?,
        onCloseRequest: () -> Unit,
    ): HeadlessWindow {
        val window = HeadlessWindow(
            application = this,
            devicePixelRatio = devicePixelRatio.toFloat(),
            focusRestorationCoroutineScope = scope,
            scene = scene,
            onCloseRequest = onCloseRequest,
        )
        windows[window.id] = window
        return window
    }

    internal fun removeWindow(id: LightweightWindowId) {
        windows.remove(id)
    }

    override fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId) {
        // No-op for headless
    }

    override fun reuseWindow(
        id: LightweightWindowId,
        scene: Scene<*>,
        onCloseRequest: () -> Unit,
    ): Window? {
        return windows[id]?.apply {
            this.scene = scene
            this.onCloseRequest = onCloseRequest
        }
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
            deactivateApplication(this)
        }
    }

    private var clipboardContent: ClipEntry? = null

    override suspend fun getClipEntry(): ClipEntry? = clipboardContent

    override fun getClipEntrySync(): ClipEntry? = clipboardContent

    override suspend fun setClipEntry(clipEntry: ClipEntry) {
        clipboardContent = clipEntry
    }

    override val nativeClipboard: Any = Unit

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

    private suspend fun resetState() {
        quitHandlers.clear()
        windows.values.toList().forEach { it.dispose() }
//        while (renderLoops.isNotEmpty()) {
//            renderLoops.first().stopAndJoin()
//        }
        clipboardContent = null
        isActive = true
        reconcileInProgress = false
    }

    private suspend fun awaitEventLoopToBecomeIdle(): Int {
        val currentEventLoop = synchronized(lock) { eventLoopOrNull } ?: return 0
        val becameIdle =
            withTimeoutOrNull(HEADLESS_EVENT_LOOP_IDLE_TIMEOUT_MILLIS.milliseconds) {
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
                "Timed out after ${HEADLESS_EVENT_LOOP_IDLE_TIMEOUT_MILLIS}ms waiting for the headless event loop to become idle; dropping $pendingTasksAfterTimeout queued tasks during shutdown"
            )
        }
    }

    private fun installEventLoop() {
        val currentEventLoop = createHeadlessEventLoop()
        eventLoopOrNull = currentEventLoop
        KdtMainDispatcherFactory.overridingMainDispatcher = HeadlessMainDispatcher(currentEventLoop)
        initialized = true
        lastClosedEventLoopPendingTasksCount = 0
    }

    private fun recycleEventLoop(): HeadlessEventLoop? =
        synchronized(lock) {
            val currentEventLoop = eventLoopOrNull ?: return@synchronized null
            lastClosedEventLoopPendingTasksCount = currentEventLoop.pendingTasksCount
            eventLoopOrNull = null
            KdtMainDispatcherFactory.overridingMainDispatcher = null
            initialized = false
            currentEventLoop
        }

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
        timestamp: Double = Clock.System.now().toEpochMilliseconds() / 1000.0,
    ) {
        sendWindowEvent(Event.MouseDown(windowId, button, location, timestamp))
    }

    fun sendMouseUp(
        windowId: LightweightWindowId,
        button: PointerButton,
        location: DpOffset,
        timestamp: Double = Clock.System.now().toEpochMilliseconds() / 1000.0,
    ) {
        sendWindowEvent(Event.MouseUp(windowId, button, location, timestamp))
    }

    fun sendMouseMove(
        windowId: LightweightWindowId,
        location: DpOffset,
        timestamp: Double = Clock.System.now().toEpochMilliseconds() / 1000.0,
    ) {
        sendWindowEvent(Event.MouseMoved(windowId, location, timestamp))
    }

    fun sendMouseEnter(
        windowId: LightweightWindowId,
        location: DpOffset,
        timestamp: Double = Clock.System.now().toEpochMilliseconds() / 1000.0,
    ) {
        sendWindowEvent(Event.MouseEntered(windowId, location, timestamp))
    }

    fun sendMouseExit(
        windowId: LightweightWindowId,
        location: DpOffset,
        timestamp: Double = Clock.System.now().toEpochMilliseconds() / 1000.0,
    ) {
        sendWindowEvent(Event.MouseExited(windowId, location, timestamp))
    }

    private fun sendWindowEvent(
        windowEvent: WindowEvent
    ) {
        val window = windows[windowEvent.windowId] ?: return
        window.handleEvent(windowEvent)
    }
}

private val initialTimestamp = TimeSource.Monotonic.markNow()
private const val HEADLESS_EVENT_LOOP_IDLE_TIMEOUT_MILLIS = 1_000L
private val logger = logger<HeadlessApplication>()

private object HeadlessPointerIconService : PointerIconService {
    override fun getIcon(): PointerIcon = PointerIcon.Default
    override fun setIcon(value: PointerIcon?) {}
    override fun getStylusHoverIcon(): PointerIcon? = null
    override fun setStylusHoverIcon(value: PointerIcon?) {}
    fun setHiddenUntilPointerMoves(hidden: Boolean) {}
    fun pushHide() {}
    fun popHide() {}
}

object FakeBrowser {
    var lastOpenedUrl: String? = null
}

private class NoopHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}
