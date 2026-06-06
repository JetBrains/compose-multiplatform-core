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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.internal.getCurrentThreadId
import androidx.compose.ui.util.trace
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns a [Recomposer] and frame clock shared by one or more scenes hosted by the same platform
 * container - the non-Android analog of Android's host-side recomposer/frame-clock machinery
 * (`AndroidComposeView` + the host recomposer + `Choreographer`).
 *
 * Two work queues mirror `AndroidUiDispatcher`'s two queues:
 * - [effectDispatcher] (Android's `toRunTrampolined`): coroutine dispatch, composition effects
 *   (`LaunchedEffect`, `rememberCoroutineScope` launches) and the recomposer's effect context;
 * - [recomposeDispatcher] (Android's `toRunOnFrame`), together with [frameClock]: `withFrameNanos`
 *   awaiters and recomposition (the recomposition loop runs on `recomposeDispatcher + frameClock`).
 *
 * Both are [FlushCoroutineDispatcher]s layered over the host's real dispatcher, so on a host with a
 * live native loop they drain automatically (like Android's `Handler.post`); [performFrame] and the
 * scene phases also drain them explicitly via [performScheduledEffects] /
 * [performScheduledRecomposerTasks].
 *
 * Android drives frames through `Choreographer.doFrame`; non-Android platforms have no such hook,
 * so the host calls [performFrame] explicitly before driving scene measure/layout and draw.
 *
 * The host dispatcher must be confined to a single thread, so [composeThreadId] is stable.
 * It is recorded whenever the recomposer runs on the host thread (via [performFrameDispatch]).
 */
@InternalComposeUiApi
class FrameRecomposer(
    coroutineContext: CoroutineContext,
    private val invalidate: () -> Unit = {},
) : AutoCloseable {
    private val job = Job()
    private val coroutineScope = CoroutineScope(coroutineContext + job)

    /** Trampoline queue: coroutine dispatch / composition effects / scheduled apply notifications. */
    private val effectDispatcher = FlushCoroutineDispatcher(coroutineScope)

    /** Frame queue: `withFrameNanos` awaiters and recomposition tasks. */
    private val recomposeDispatcher = FlushCoroutineDispatcher(coroutineScope)

    /**
     * The clock that drives the recomposition loop.
     * Its `withFrameNanos` awaiters are resumed by [performFrame].
     */
    private val frameClock = BroadcastFrameClock(::onNewAwaiters)

    private val recomposer = Recomposer(coroutineContext + job + effectDispatcher)

    /**
     * Id of the host (compose) thread. Snapshot-observer callbacks run inline when on this thread,
     * otherwise they are posted to the shared [effectDispatcher].
     */
    private var composeThreadId: Long? by atomic(null)

    /**
     * Registers `coroutineContext` with the shared [GlobalSnapshotManager] so ambient global writes
     * schedule apply notifications onto this host. Several [FrameRecomposer]s built on the same
     * host context share one observer and it's released only when the last of them is closed.
     */
    private val globalSnapshotRegistration = GlobalSnapshotManager.register(coroutineContext)

    init {
        // The host must carry a (single-thread) continuation interceptor that work is dispatched
        // through. It need not be a CoroutineDispatcher directly - e.g. tests wrap it with an
        // ApplyingContinuationInterceptor that delegates to the test dispatcher.
        requireNotNull(coroutineContext[ContinuationInterceptor]) {
            "FrameRecomposer requires a ContinuationInterceptor in its coroutineContext"
        }
        coroutineScope.launch(
            recomposeDispatcher + frameClock,
            start = CoroutineStart.UNDISPATCHED
        ) {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    /**
     * Returns the composition context backed by this host's recomposer.
     */
    val compositionContext: CompositionContext
        get() = recomposer

    private var isInFrame = false

    private fun onNewAwaiters() {
        if (isInFrame) return
        invalidate()
    }

    private inline fun <T> postponeFrameInvalidation(crossinline block: () -> T): T =
        trace("FrameRecomposer:performFrame") {
            check(!isInFrame)
            isInFrame = true
            try {
                block()
            } finally {
                isInFrame = false
            }
        }

    /**
     * Performs one host frame. Platforms call this once from their native frame callback before
     * running [androidx.compose.ui.scene.ComposeScene] measure/layout and draw phases.
     */
    fun performFrame(frameTimeNanos: Long) {
        // It's usually handled by [GlobalSnapshotManager], but currently there are a few places
        // that require synchronous execution, so this guard is for compatibility.
        Snapshot.sendApplyNotifications()

        recomposeFrame(frameTimeNanos)
    }

    /**
     * Advances only the host recomposer and frame clock by one frame at [frameTimeNanos].
     */
    private fun recomposeFrame(frameTimeNanos: Long) {
        postponeFrameInvalidation {
            // Flush composition effects (e.g. LaunchedEffect, coroutines launched in
            // rememberCoroutineScope()) queued by the previous turn must run before
            // recomposition tasks and frame-clock awaiters.
            performScheduledEffects()
            performScheduledRecomposerTasks()

            frameClock.sendFrame(frameTimeNanos)
        }
        if (frameClock.hasAwaiters) {
            invalidate()
        }
    }

    /**
     * Returns whether the host still has recomposition or loop work to process.
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
        globalSnapshotRegistration?.close()
        recomposer.cancel()
        job.cancel()
    }

    /**
     * Runs [block] with the [MonotonicFrameClock] owned by this host's recomposer.
     */
    suspend fun withMonotonicFrameClock(block: suspend () -> Unit) {
        val monotonicFrameClock = compositionContext.effectCoroutineContext[MonotonicFrameClock]
            ?: error("No MonotonicFrameClock found in FrameRecomposer.compositionContext")
        withContext(monotonicFrameClock) {
            block()
        }
    }

    /**
     * Runs [block] on the compose thread: inline when already on it, otherwise [dispatch]ed onto
     * the shared trampoline queue.
     */
    internal fun runOnComposeThread(block: () -> Unit) {
        if (composeThreadId == getCurrentThreadId()) block() else dispatch(block)
    }

    /**
     * Enqueues [block] onto the trampoline queue; it runs on the next loop turn or the next
     * [performScheduledEffects].
     */
    internal fun dispatch(block: () -> Unit) {
        effectDispatcher.dispatch(job, Runnable(block))
    }

    /**
     * Runs the frame queue (pending `withFrameNanos`/recompose tasks) and records the compose thread:
     * this is where the recomposer (`runRecomposeAndApplyChanges`) executes on the host thread, so it
     * is the single place [composeThreadId] is set. Driven by [performFrame] each frame, and by
     * `BaseComposeScene.setContent` so the compose thread is established before the first frame.
     */
    internal fun performScheduledRecomposerTasks(): Unit =
        trace("FrameRecomposer:performScheduledRecomposerTasks") {
            composeThreadId = getCurrentThreadId()
            recomposeDispatcher.flush()
        }

    /**
     * Runs the trampoline queue (coroutine dispatch / composition effects / scheduled apply
     * notifications).
     */
    internal fun performScheduledEffects(): Unit =
        trace("FrameRecomposer:performScheduledEffects") {
            effectDispatcher.flush()
        }
}
