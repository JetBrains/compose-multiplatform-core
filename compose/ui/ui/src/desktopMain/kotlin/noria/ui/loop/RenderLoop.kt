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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.desktop.Application
import androidx.compose.ui.desktop.runSession
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import noria.CallbackInterceptorCompositionLocal
import noria.impl.EffectCoroutineContextCompositionLocal

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
    tryToInvalidateCurrentFrame(currentComposer.currentCompositionLocalMap, f)
}

@OptIn(InternalComposeApi::class)
fun tryToInvalidateCurrentFrame(compositionLocalMap: CompositionLocalMap, f: () -> Boolean) {
    val interceptor = compositionLocalMap[CallbackInterceptorCompositionLocal]
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

/**
 * Runs [content] as a Compose scene driven by this render loop, then runs [block] with the
 * render loop installed in its coroutine context.
 *
 * Callers install context elements they want reachable from scene coroutines (dispatch
 * affinity, DB handles, ...) with `withContext(...)` around the call; those elements propagate
 * into every scene coroutine via structured concurrency. The [EffectCoroutineContextCompositionLocal]
 * provided here carries the same elements plus a scene-session [SupervisorJob]: coroutines
 * launched into it are cancelled when this [withScene] call ends (after the scene has shut
 * down), one failing effect does not cancel its siblings, and cancelling [withScene] itself
 * cancels them all. NOT the raw scope job on purpose — a plain child of [supervisorScope]
 * would make the scope WAIT for long-lived effects on normal completion and hang shutdown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> withScene(
    content: @Composable () -> Unit,
    dataSourceContext: DataSourceContext = DataSourceContext(),
    block: suspend CoroutineScope.() -> T,
): T {
    val sceneFramesFlow = MutableSharedFlow<RenderLoop.FrameInfo>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    return supervisorScope {
        val shutdownSignal = Job()
        val recomposerInitialization = Job()
        val applicationFrameClock = RenderLoopFrameClock(shutdownSignal, sceneFramesFlow)
        // Ties Noria-launched effect lifetimes to the session: explicitly cancelled in
        // the finally below, after the session itself has shut down.
        val effectJob = SupervisorJob(coroutineContext[Job])
        val effectCoroutineContext =
            coroutineContext.minusKey(Job) + effectJob + ComposeUIDispatcher + applicationFrameClock

        val sessionJob = launch {
            Application.current.runSession(
                frameClock = applicationFrameClock,
                dataSourceContext = dataSourceContext,
                locals = arrayOf(EffectCoroutineContextCompositionLocal provides effectCoroutineContext),
                onSessionReady = {
                    // Guarantee at least one frame so a late `framesFlow.first()` never hangs on an
                    // otherwise-idle session.
                    sceneFramesFlow.tryEmit(RenderLoop.FrameInfo(System.nanoTime()))
                    recomposerInitialization.complete()
                },
                awaitShutdown = { shutdownSignal.join() },
                content = content,
            )
        }
        // If the session fails before it becomes ready, surface the failure instead of hanging on join.
        sessionJob.invokeOnCompletion { cause ->
            if (cause != null) recomposerInitialization.completeExceptionally(cause)
        }

        val renderLoop = object : RenderLoop {
            override val key: CoroutineContext.Key<*> = RenderLoop

            override val framesFlow: Flow<RenderLoop.FrameInfo>
                get() = sceneFramesFlow

            override suspend fun stopAndJoin() {
                shutdownSignal.complete()
                sessionJob.join()
            }
        }

        try {
            recomposerInitialization.join()
            withContext(renderLoop + applicationFrameClock, block)
        } finally {
            shutdownSignal.complete()
            sessionJob.join()
            // The session is down; end the session-scoped effects. Without this cancel the
            // supervisorScope would wait forever for any still-running effect coroutine.
            effectJob.cancel()
        }
    }
}

/**
 * A [MonotonicFrameClock] that cooperatively yields to the UI thread
 * and additionally emits a [RenderLoop.FrameInfo] to [framesFlow] on every frame, giving the
 * Compose backend a best-effort [RenderLoop.framesFlow].
 */
private class RenderLoopFrameClock(
    private val shutdownSignal: Job,
    private val framesFlow: MutableSharedFlow<RenderLoop.FrameInfo>,
) : MonotonicFrameClock {

    override suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R
    ): R {
        if (shutdownSignal.isCompleted) {
            throw CancellationException("Render loop shutting down")
        }

        // We call `yield` to avoid blocking the UI thread. If we don't call this then application
        // can be frozen for the user in some cases as it will not receive any input events.
        yield()

        if (shutdownSignal.isCompleted) {
            throw CancellationException("Render loop shutting down")
        }

        val frameTimeNanos = System.nanoTime()
        val result = onFrame(frameTimeNanos)
        framesFlow.tryEmit(RenderLoop.FrameInfo(frameTimeNanos))
        return result
    }
}
