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

package noria.ui.loop

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.configureSwingGlobalsForCompose
import androidx.compose.ui.platform.GlobalSnapshotManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.GlobalDensity
import androidx.compose.ui.window.GlobalLayoutDirection
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import noria.CallbackInterceptorCompositionLocal
import noria.impl.EffectCoroutineContextCompositionLocal
import org.jetbrains.skiko.MainUIDispatcher

interface RenderLoop : CoroutineContext.Element {
    companion object : CoroutineContext.Key<RenderLoop> {
//    val logger = logger<RenderLoop>()
    }

    @JvmInline
    value class FrameInfo(val elapsedTimeNanos: Long)

    override val key: CoroutineContext.Key<*> get() = RenderLoop

    suspend fun stopAndJoin()

    val framesFlow: Flow<FrameInfo>
}

@Composable
fun tryToInvalidateCurrentFrame(f: () -> Boolean = { true }) {
    tryToInvalidateCurrentFrame(currentComposer, f)
}

@OptIn(InternalComposeApi::class)
fun tryToInvalidateCurrentFrame(composer: Composer, f: () -> Boolean) {
//    val interceptor = composer.currentCompositionLocalMap[CallbackInterceptorCompositionLocal]
//    composer.recordSideEffect {
//        interceptor.execute(f)
//    }
}

@OptIn(InternalComposeApi::class)
@Composable
fun onFrameCompletion(block: (RenderLoop.FrameInfo) -> Unit) {
    val interceptor = CallbackInterceptorCompositionLocal.current
    currentComposer.recordSideEffect {
        interceptor.execute {
            block(
                RenderLoop.FrameInfo(
                    0L
                )
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> withRenderLoopAndFrameClock(
    content: @Composable ApplicationScope.() -> Unit,
    applyCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T {
    // todo Emit frames
    val framesFlow = MutableSharedFlow<RenderLoop.FrameInfo>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    if (System.getProperty("compose.application.configure.swing.globals") == "true") {
        configureSwingGlobalsForCompose()
    }

    return supervisorScope {
        val shutdownSignal = Job()
        var recomposerJob: Job? = null
        try {
            val applicationFrameClock = CooperativeYieldFrameClock(shutdownSignal)

            val recomposerInitialization = Job()
            val recomposerCleanup = Job()

            recomposerJob = launch(MainUIDispatcher + applicationFrameClock) {
                var composition: Composition? = null
                var recomposer: Recomposer? = null

                try {
                    GlobalSnapshotManager.ensureStarted()

                    recomposer = Recomposer(coroutineContext)

                    var isOpen by mutableStateOf(true)
                    val applicationScope = object : ApplicationScope {
                        override fun exitApplication() {
                            isOpen = false
                            shutdownSignal.complete()
                        }
                    }

                    launch { recomposer.runRecomposeAndApplyChanges() }

                    val applier = ApplicationApplier()
                    composition = Composition(applier, recomposer)

                    composition.setContent {
                        if (isOpen) {
                            CompositionLocalProvider(
                                LocalDensity provides GlobalDensity,
                                LocalLayoutDirection provides GlobalLayoutDirection,
                                EffectCoroutineContextCompositionLocal provides applyCoroutineContext + MainUIDispatcher + applicationFrameClock
                            ) {
                                applicationScope.content()
                            }
                        }
                    }

                    recomposerInitialization.complete()

                    shutdownSignal.join()
                } catch (throwable: Throwable) {
                    shutdownSignal.complete()
                    if (!recomposerInitialization.completeExceptionally(throwable)) {
                        throw throwable
                    }
                } finally {
                    try {
                        composition?.dispose()
                        recomposer?.close() // Terminates runRecomposeAndApplyChanges() gracefully
                        recomposer?.join()
                    } catch (e: Exception) {
                        println("Warning: Exception during render loop cleanup: ${e.message}")
                    } finally {
                        recomposerCleanup.complete()
                    }
                }
            }

            recomposerInitialization.join()

            val renderLoop = object : RenderLoop {
                override val key: CoroutineContext.Key<*> = RenderLoop

                override val framesFlow: Flow<RenderLoop.FrameInfo>
                    get() = framesFlow

                override suspend fun stopAndJoin() {
                    shutdownSignal.complete()
                    recomposerCleanup.join()
                }
            }

            withContext(renderLoop + applicationFrameClock, block)
        } finally {
            shutdownSignal.complete()
            recomposerJob?.join()
        }
    }
}

private class CooperativeYieldFrameClock(
    private val shutdownSignal: Job
) : MonotonicFrameClock {

    override suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R
    ): R {
        if (shutdownSignal.isCompleted) {
            throw CancellationException("Render loop shutting down")
        }

        // We call `yield` to avoid blocking the UI thread. If we don't call this then application
        // can be frozen for the user in some cases as it will not receive any input events.
        //
        // Swing dispatcher will process all pending events and resume after `yield`.
        yield()

        if (shutdownSignal.isCompleted) {
            throw CancellationException("Render loop shutting down")
        }

        return onFrame(System.nanoTime())
    }
}

private class ApplicationApplier : Applier<Any> {
    override val current: Any = Unit
    override fun down(node: Any) = Unit
    override fun up() = Unit
    override fun insertTopDown(index: Int, instance: Any) {
        if (instance !is Unit) {
            throw IllegalStateException(
                "Composable content may not be added directly into " +
                    ApplicationScope::class.simpleName
            )
        }
    }

    override fun insertBottomUp(index: Int, instance: Any) {
        if (instance !is Unit) {
            throw IllegalStateException(
                "Composable content may not be added directly into " +
                    ApplicationScope::class.simpleName
            )
        }
    }

    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun clear() = Unit
    override fun onEndChanges() = Unit
}
