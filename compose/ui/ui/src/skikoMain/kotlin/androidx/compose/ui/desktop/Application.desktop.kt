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
import androidx.compose.runtime.DataSourceCompositionDomain
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.GlobalSnapshotManager
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.scene.ComposeSceneFeatureFlags
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.io.files.Path
import androidx.compose.runtime.Applier
import androidx.compose.runtime.ProvidedValue
import kotlinx.coroutines.withContext

suspend fun awaitApplication(
    identifier: String,
    openUrls: (List<String>) -> Unit,
    libraryFolder: Path,
    logFolder: Path,
    dataSourceContext: DataSourceContext = DataSourceContext(),
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
    application.runSession(
        dataSourceContext = dataSourceContext,
        awaitShutdown = { terminationSignal.join() },
        content = content,
    )
    application.stopAndJoin()
}

/**
 * The cooperative-yield application frame clock; default for [Application.runSession].
 */
@OptIn(kotlin.time.ExperimentalTime::class)
object YieldFrameClock : MonotonicFrameClock {
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
        session: ApplicationSession,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): Window

    fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId)

    fun reuseWindow(
        id: LightweightWindowId,
        session: ApplicationSession,
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

/**
 * Composes [content] as this application's root composition and SUSPENDS until
 * [awaitShutdown] returns, then tears the session down (the composition is disposed
 * BEFORE the recomposer is closed and joined: disposal cancels the content's effect
 * coroutines, without which the join would deadlock). The coroutine context is
 * inherited from the caller — elements installed around this call reach every session
 * coroutine, window scenes included. Overlay: ComposeUIDispatcher + [frameClock].
 *
 * [awaitShutdown]'s default returns immediately: the session closes right after the
 * first composition (windows created during composition keep themselves alive). Pass
 * a suspending body to keep the root composition reactive (windows added/removed by
 * state).
 */
suspend fun Application.runSession(
    dataSourceContext: DataSourceContext = DataSourceContext(),
    frameClock: MonotonicFrameClock = YieldFrameClock,
    onSessionReady: () -> Unit = {},
    awaitShutdown: suspend () -> Unit = {},
    vararg locals: ProvidedValue<*>,
    content: @Composable () -> Unit,
) {
    withContext(ComposeUIDispatcher + frameClock) {
        val session = ApplicationSession(
            coroutineScope = this,
            dataSourceContext = dataSourceContext,
        )
        GlobalSnapshotManager.ensureStarted()

        // The application composition's frame domain: read tracking over the launch-time
        // context, per-slice isolation (flag off) or a standing rotated cycle unit (flag on).
        val domain = DataSourceCompositionDomain(
            dataSourceContext = dataSourceContext,
            isolating = ComposeSceneFeatureFlags.isFrameIsolationEnabled,
            coroutineScope = this,
            dispatcher = ComposeUIDispatcher,
        )

        val recomposer = Recomposer(coroutineContext + domain.recomposerContext)

        launch {
            recomposer.runRecomposeAndApplyChanges()
        }

        val composition = Composition(ApplicationApplier(), recomposer)
        try {
            composition.setContent {
                withCompositionLocal {
                    CompositionLocalProvider(
                        ProvidableLocalApplicationSession provides session,
                        *locals
                    ) {
                        content()
                    }
                }
            }
            onSessionReady()
            awaitShutdown()
        } finally {
            // Dispose the composition BEFORE joining the recomposer. Disposal cancels the
            // content's still-running effect coroutines (LaunchedEffect, produceState, flow
            // collectors, awaitCancellation, ...), which are children of the recomposer's
            // effectJob. recomposer.close() only *completes* that job (no new children) — it does
            // not cancel the running ones — so join() would otherwise wait forever for effects
            // that never finish on their own, deadlocking shutdown (the scene coroutine never
            // completes, so stopAndJoin()/withScene hang).
            composition.dispose()
            domain.close()
            recomposer.close()
            recomposer.join()
        }
    }
}

internal val DefaultDragThreshold = 8.dp
internal val DefaultDoubleClickDistance = 5.dp
