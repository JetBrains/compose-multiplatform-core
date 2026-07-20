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

package androidx.compose.ui.desktop

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.GlobalSnapshotManager
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.io.files.Path
import androidx.compose.runtime.Applier
import androidx.compose.runtime.ProvidedValue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

suspend fun awaitApplication(
    identifier: String,
    openUrls: (List<String>) -> Unit,
    libraryFolder: Path,
    logFolder: Path,
    content: @Composable () -> Unit,
) {
    val terminationSignal = Job()
    initializeApplication(
        identifier,
        openUrls,
        libraryFolder,
        logFolder,
    ) { terminationSignal.complete() }
    val application = Application.current
    application.awaitWhenReady()
    coroutineScope {
        runComposeScene(
            context = coroutineContext,
            frameClock = YieldFrameClock,
            prepareMainThread = { },
            restoreMainThread = { },
            content = content,
        )
        terminationSignal.join()
        // Cancel the composition/recomposition coroutines so this function returns.
        this.coroutineContext.cancel()
    }
    application.stopAndJoin()
}

/**
 * Sets up and runs a Compose "application scene": creates the [Scene], installs the snapshot
 * callback interceptor, starts a [Recomposer] and root [Composition], composes [content], then
 * keeps the scene alive until [awaitShutdown] returns before tearing everything down.
 *
 * This is the single shared scene-launching core reused by both [launchScene] (and therefore
 * [awaitApplication]) and the desktop `noria.ui.loop.withScene` render loop. Everything that differs
 * between those callers is injected:
 *  - [frameClock]: the [MonotonicFrameClock] driving recomposition (e.g. [YieldFrameClock] for the
 *    application entry points, or a frame-emitting clock for the render loop).
 *  - [locals]: extra composition locals wrapped around [content] (e.g. the effect coroutine
 *    context for the render loop).
 *  - [onSceneReady]: invoked once, right after the initial composition has been applied.
 *  - [awaitShutdown]: suspends for as long as the scene should stay alive. The default returns
 *    immediately, closing the recomposer right after the first composition (windows created during
 *    composition keep themselves alive); pass a suspending body to keep the root composition
 *    reactive (needed when windows are added/removed based on state).
 */
internal suspend fun <T> runComposeScene(
    context: CoroutineContext,
    frameClock: MonotonicFrameClock,
    prepareMainThread: () -> T,
    restoreMainThread: (T) -> Unit,
    onSceneReady: () -> Unit = {},
    awaitShutdown: suspend () -> Unit = {},
    vararg locals: ProvidedValue<*>,
    content: @Composable () -> Unit,
) {
    withContext(context + ComposeUIDispatcher + frameClock) {
        val scene = Scene(
            coroutineScope = this,
            prepareMainThread = prepareMainThread,
            restoreMainThread = restoreMainThread,
        )
        GlobalSnapshotManager.setCallbackInterceptor(scene::withPreparedMainThread)
        GlobalSnapshotManager.ensureStarted()

        val recomposer = Recomposer(coroutineContext)

        launch {
            recomposer.runRecomposeAndApplyChanges()
        }

        val application = Application.current
        val composition = Composition(ApplicationApplier(), recomposer)
        try {
            composition.setContent {
                application.withCompositionLocal {
                    CompositionLocalProvider(
                        ProvidableLocalScene provides scene,
                        *locals
                    ) {
                        content()
                    }
                }
            }
            onSceneReady()
            awaitShutdown()
            recomposer.close()
            recomposer.join()
        } finally {
            composition.dispose()
        }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal object YieldFrameClock : MonotonicFrameClock {
    private val origin = kotlin.time.TimeSource.Monotonic.markNow()

    override suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R
    ): R {
        // We call `yield` to avoid blocking UI thread. If we don't call this then application
        // can be frozen for the user in some cases as it will not receive any input events.
        yield()
        return onFrame(origin.elapsedNow().inWholeNanoseconds)
    }
}

internal class ApplicationApplier : Applier<Any> {
    override val current: Any = Unit
    override fun down(node: Any) = Unit
    override fun up() = Unit
    override fun insertTopDown(index: Int, instance: Any) {
        check(instance is Unit) { "Composable content may not be added directly into the Application scope" }
    }
    override fun insertBottomUp(index: Int, instance: Any) {
        check(instance is Unit) { "Composable content may not be added directly into the Application scope" }
    }
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun clear() = Unit
    override fun onEndChanges() = Unit
}

expect fun initializeApplication(
    identifier: String,
    openUrls: (List<String>) -> Unit,
    libraryFolder: Path,
    logFolder: Path,
    uriHandler: UriHandler = defaultUriHandler(),
    customQuit: (() -> Boolean)? = null,
)

internal expect fun currentApplication(): Application

internal expect fun defaultUriHandler(): UriHandler

internal expect fun activateApplication(application: Application)

internal expect fun deactivateApplication(application: Application)

internal expect fun removeApplication(application: Application)

interface Application : Clipboard, UriHandler, AutoCloseable {
    companion object {
        val current: Application
            get() = currentApplication()
    }

    /**
     * [AutoCloseable] hook; synchronously drains the application main loop via [stopAndJoin].
     * This lets callers write `initApplication().use { application -> runApplication(application) { … } }`.
     */
    override fun close()

    val systemTheme: SystemTheme
    val dragThreshold: Dp
        get() = DefaultDragThreshold
    val doubleClickDistance: Dp
        get() = DefaultDoubleClickDistance

    fun createWindow(
        scene: Scene<*>,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): Window

    fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId)

    fun reuseWindow(
        id: LightweightWindowId,
        scene: Scene<*>,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): Window?

    fun disposeReusableNativeWindowResources(id: LightweightWindowId)

    //    val mainWindow: KdtWindow?
    val windows: Map<LightweightWindowId, Window>
    val focusedWindow: Window?

    val isActive: Boolean

    @MainThread
    fun requestActivation()
//    suspend fun yieldActivationTo(other: KdtApplication): Boolean

    val screens: Map<out Any, Screen>

    @MainThread
    fun showEmojiAndSymbolsPopup()

    fun quit()
    fun putQuitHandler(id: String, quitHandler: () -> Boolean)
    fun removeQuitHandler(id: String)
    suspend fun awaitWhenReady()

    val nativeApplication: Any

    // these locals are the same as compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/CompositionLocals.kt,
    // but we want to provide outside a scene too
    @Composable
    fun withCompositionLocal(content: @Composable () -> Unit)

    suspend fun resetForReuse() {
        stopAndJoin()
    }

    suspend fun stopAndJoin()
}

interface IconDecoratedApplication : Application {
    @MainThread
    fun setIcon(icon: ByteArray)
}

internal val DefaultDragThreshold = 8.dp
internal val DefaultDoubleClickDistance = 5.dp
