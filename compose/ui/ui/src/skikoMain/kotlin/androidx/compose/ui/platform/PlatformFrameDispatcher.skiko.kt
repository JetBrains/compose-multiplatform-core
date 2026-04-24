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

package androidx.compose.ui.platform

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.util.trace
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns a recomposer and frame clock shared by one or more scenes hosted by the same platform
 * container.
 *
 * Host-owned immediate dispatch loop for Compose work that should progress without ticking a frame.
 *
 * Android advances the equivalent work from the host dispatcher/recomposer loop rather than from a
 * scene object. Skiko still progresses it explicitly, so scenes depend on this narrower
 * host-dispatch contract instead of recomposer-specific scheduling details.
 */
@InternalComposeUiApi
class PlatformFrameDispatcher(
    coroutineContext: CoroutineContext,
    private val invalidate: () -> Unit = {},
) : AutoCloseable {
    private val job = Job()
    private val coroutineScope = CoroutineScope(coroutineContext + job)
    private val frameClock = BroadcastFrameClock(onNewAwaiters = invalidate)
    private val effectDispatcher = FlushCoroutineDispatcher(coroutineScope)
    private val recomposeDispatcher = FlushCoroutineDispatcher(coroutineScope)
    private val recomposer = Recomposer(coroutineContext + job + effectDispatcher)

    init {
        coroutineScope.launch(recomposeDispatcher + frameClock, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    /**
     * Returns the composition context backed by this host's recomposer.
     */
    val compositionContext: CompositionContext
        get() = recomposer

    /**
     * Advances the host by one frame at [frameTimeNanos].
     */
    fun recomposeFrame(frameTimeNanos: Long) {
        // Flush composition effects (e.g. LaunchedEffect, coroutines launched in
        // rememberCoroutineScope()) queued by the previous turn must run before
        // recomposition tasks and frame-clock awaiters.
        performScheduledEffects()
        performScheduledRecomposerTasks()

        frameClock.sendFrame(frameTimeNanos)
        if (hasPendingWork()) {
            invalidate()
        }
    }

    /**
     * Returns whether the host still has recomposition or frame-clock work to process.
     */
    fun hasPendingWork(): Boolean =
        recomposer.hasPendingWork ||
            effectDispatcher.hasImmediateTasks() ||
            recomposeDispatcher.hasImmediateTasks() ||
            frameClock.hasAwaiters

    /**
     * Cancels the host recomposer and releases host-owned resources.
     */
    override fun close() {
        recomposer.cancel()
        job.cancel()
    }

    /**
     * Runs [block] with the [MonotonicFrameClock] owned by this host's recomposer.
     */
    suspend fun withMonotonicFrameClock(block: suspend () -> Unit) {
        val monotonicFrameClock = compositionContext.effectCoroutineContext[MonotonicFrameClock]
            ?: error("No MonotonicFrameClock found in PlatformFrameDispatcher.compositionContext")
        withContext(monotonicFrameClock) {
            block()
        }
    }

    /**
     * Enqueues host-owned work to run later in the current turn, before the next frame.
     */
    internal fun dispatch(block: () -> Unit) {
        effectDispatcher.dispatch(job, Runnable(block))
    }

    internal fun performScheduledRecomposerTasks(): Unit =
        trace("PlatformFrameDispatcher:performScheduledRecomposerTasks") {
            recomposeDispatcher.flush()
        }

    internal fun performScheduledEffects(): Unit =
        trace("PlatformFrameDispatcher:performScheduledEffects") {
            effectDispatcher.flush()
        }
}

/**
 * Creates synthetic platform value storage backed by this host.
 */
@InternalComposeUiApi
fun PlatformFrameDispatcher.asPlatformValueStorage(): PlatformValueStorage =
    PlatformValueStorage.MapValueStorage().also {
        it.compositionContext = compositionContext
        it.platformFrameDispatcher = this
    }
