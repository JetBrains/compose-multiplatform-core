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
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.enter
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.pumpScenelessDomainRotations
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.runtime.withTransaction
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.internal.getCurrentThreadId
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.trace
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns a [Recomposer] and frame clock shared by one or more scenes hosted by the same platform
 * container - the non-Android analog of Android's host-side recomposer/frame-clock machinery
 * (`AndroidComposeView` + the host recomposer + `Choreographer`).
 *
 * Two work queues mirror `AndroidUiDispatcher`'s two queues:
 * - [trampolineDispatcher] (Android's `toRunTrampolined`): coroutine dispatch, composition effects
 *   (`LaunchedEffect`, `rememberCoroutineScope` launches) and the recomposer's effect context;
 * - [frameDispatcher] (Android's `toRunOnFrame`), together with [frameClock]: `withFrameNanos`
 *   awaiters and recomposition (the recomposition loop runs on `frameDispatcher + frameClock`).
 *
 * Both are [FlushCoroutineDispatcher]s layered over the host's real dispatcher, so on a host with
 * a live native loop they drain automatically; [performFrame] and the scene phases also roll them
 * synchronously via [performTrampolineDispatch] / [performFrameDispatch].
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

    /**
     * The frame domains of the scenes this host drives. Their pins are swapped once per frame, at
     * the start of [performFrame] - the non-Android analog of the point in
     * `Choreographer.doFrame` where a new frame's state becomes visible. A scene registers on
     * construction and releases on close; an empty registry makes every frame-domain step below a
     * no-op, which is what keeps the frame-isolation-off path identical to stock.
     */
    private val frameDomains = mutableListOf<SnapshotHolder>()

    internal fun registerFrameDomain(holder: SnapshotHolder): AutoCloseable {
        frameDomains.add(holder)
        return AutoCloseable { frameDomains.remove(holder) }
    }

    /**
     * Runs a task dispatched on the **effect** queue ([trampolineDispatcher]) as one slice of the
     * frame cycle of every registered domain. Deliberately not applied to [frameDispatcher]:
     * wrapping recomposition tasks would merge the recomposer's own sequential child slices into
     * one and defer their per-slice delivery past the frame.
     *
     * Enter as well as transact, and in that order — the same pairing `withFrameTransaction` uses.
     * A transaction on its own is not a read scope: a source that binds its view in `makeCurrent`
     * (as RhizomeDB does) sees nothing from a bare transaction, so an effect that reads an entity
     * would fail even though it is nominally inside the frame cycle. These queues are not a rare
     * path — they carry every `LaunchedEffect` body and every `DisposableEffect`, which is where a
     * composition reconciles itself with the world outside it.
     *
     * Uses [SnapshotHolder.current] rather than `checkedCurrent`: a task can be dispatched before
     * the domain is activated (e.g. `RootNodeOwner`'s init synchronously launches a `snapshotFlow`
     * collector). In that pre-activation window `current` is null and the task runs bare, exactly
     * as on the frame-isolation-off path.
     */
    private fun runInFrameDomains(task: () -> Unit) = runInFrameDomains(0, task)

    private fun runInFrameDomains(index: Int, task: () -> Unit) {
        if (index == frameDomains.size) return task()
        val frame = frameDomains[index].current
        if (frame == null) {
            runInFrameDomains(index + 1, task)
        } else {
            frame.enter { frame.withTransaction { runInFrameDomains(index + 1, task) } }
        }
    }

    /**
     * Binds every registered domain's read view around [block], nesting one [enter] per domain.
     * Binds views only: no transaction is opened, so the recomposer keeps slicing its own pipeline
     * into sequential child slices that each publish before the next is taken (the same-frame
     * animation contract).
     */
    private fun enterFrameDomains(index: Int, block: () -> Unit) {
        if (index == frameDomains.size) return block()
        val unit = frameDomains[index].checkedCurrent
        if (unit == null) {
            enterFrameDomains(index + 1, block)
        } else {
            unit.enter { enterFrameDomains(index + 1, block) }
        }
    }

    /**
     * Trampoline queue (Android's `toRunTrampolined`):
     *   - Coroutine dispatch
     *   - Composition effects
     *   - Scheduled apply notifications
     * Rolled synchronously by [performTrampolineDispatch].
     */
    private val trampolineDispatcher =
        FlushCoroutineDispatcher(coroutineScope, ::runInFrameDomains)

    /**
     * Frame queue (Android's `toRunOnFrame`): `withFrameNanos` awaiters and recomposition tasks.
     * Rolled synchronously by [performFrameDispatch].
     */
    private val frameDispatcher = FlushCoroutineDispatcher(coroutineScope)

    /**
     * The clock that drives the recomposition loop.
     * Its `withFrameNanos` awaiters are resumed by [performFrame].
     */
    private val frameClock = BroadcastFrameClock(::onNewAwaiters)

    private val recomposer = Recomposer(coroutineContext + job + trampolineDispatcher)

    /**
     * Id of the host (compose) thread. Snapshot-observer callbacks run inline when on this thread,
     * otherwise they are posted to the shared [trampolineDispatcher].
     */
    private var composeThreadId: Long? by atomic(null)

    /**
     * Registers the [trampolineDispatcher] with the shared [GlobalSnapshotManager] so ambient
     * global writes schedule apply notifications onto the trampoline queue, where they are rolled
     * synchronously by [performTrampolineDispatch].
     */
    private val globalSnapshotRegistration = GlobalSnapshotManager.register(trampolineDispatcher)

    init {
        // The host must carry a (single-thread) continuation interceptor that work is dispatched
        // through. It need not be a CoroutineDispatcher directly - e.g. tests wrap it with an
        // ApplyingContinuationInterceptor that delegates to the test dispatcher.
        requireNotNull(coroutineContext[ContinuationInterceptor]) {
            "FrameRecomposer requires a ContinuationInterceptor in its coroutineContext"
        }
        @OptIn(InternalComposeApi::class)
        recomposer.setResilientModeEnabled(true)
        coroutineScope.launch(
            frameDispatcher + frameClock,
            start = CoroutineStart.UNDISPATCHED
        ) {
            recomposer.runRecomposeAndApplyChanges()
        }
        // Resilient mode captures a composition failure in errorState instead of tearing the
        // recomposer down; recover by reloading this host's compositions from their content
        // lambdas, so one bad frame doesn't leave a permanently dead window.
        coroutineScope.launch(frameDispatcher + frameClock) {
            @OptIn(ComposeToolingApi::class)
            recomposer.asRecomposerInfo().errorState.collect { error ->
                // The StateFlow replays its current value, so reacting to every emission would
                // fire a gratuitous full reload at construction (initial null) and a second,
                // state-destroying one after each error (the null written back by resetErrorState).
                if (error != null) {
                    // Not sure that it's correct, maybe we need to wait until the frame finishes
                    simulateHotReload()
                }
            }
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
        postponeFrameInvalidation {
            composeThreadId = getCurrentThreadId()

            // Inter-frame work - coroutine dispatch and composition effects - belongs to the
            // PREVIOUS frame and must run before the pin swap, on the pin it was scheduled under.
            performTrampolineDispatch()

            // Scene-less domains (e.g. an application-level composition) can only rotate through
            // the platform's async main-thread queue, which starves under sustained rendering.
            // Pump their due swaps here, on the ingress that survives saturation, so their pins
            // stop retaining superseded state records.
            pumpScenelessDomainRotations()

            // Pin swap. Publishes nothing itself: changes published externally since the previous
            // swap become visible to this frame, and each domain's pending delivery is dispatched
            // against that new view. Swap-first ordering lives in SnapshotHolder.rotate.
            frameDomains.fastForEach { it.rotate() }

            // Everything from here on reads the successor's view.
            enterFrameDomains(0) {
                frameDispatcher.flush()

                frameClock.sendFrame(frameTimeNanos)
            }
        }
        if (frameClock.hasAwaiters) {
            invalidate()
        }
    }

    /**
     * Simulates hot reload of the compositions of this host's [Recomposer], discarding their state.
     *
     * @see Recomposer.simulateHotReload
     */
    @OptIn(InternalComposeApi::class)
    internal fun simulateHotReload() = trace("FrameRecomposer:simulateHotReload") {
        recomposer.simulateHotReload()
    }

    /**
     * Returns whether the host still has recomposition or loop work to process.
     */
    fun hasPendingWork(): Boolean =
        recomposer.hasPendingWork ||
            trampolineDispatcher.hasImmediateTasks() ||
            frameDispatcher.hasImmediateTasks() ||
            frameClock.hasAwaiters

    /**
     * Cancels the host recomposer and releases host-owned resources.
     */
    override fun close() {
        frameDomains.clear()
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
     * [performTrampolineDispatch].
     */
    internal fun dispatch(block: () -> Unit) {
        trampolineDispatcher.dispatch(job, Runnable(block))
    }

    /**
     * Synchronously rolls the frame loop: drains the [frameDispatcher] queue (pending
     * `withFrameNanos` / recompose tasks) after first rolling the trampoline loop via
     * [performTrampolineDispatch].
     */
    internal fun performFrameDispatch(): Unit =
        trace("FrameRecomposer:performFrameDispatch") {
            composeThreadId = getCurrentThreadId()
            performTrampolineDispatch()
            frameDispatcher.flush()
        }

    /**
     * Synchronously rolls the trampoline loop: flushes pending snapshot apply notifications
     * implicitly by [GlobalSnapshotManager] or explicitly if there is no active registration.
     */
    internal fun performTrampolineDispatch(): Unit =
        trace("FrameRecomposer:performTrampolineDispatch") {
            if (globalSnapshotRegistration == null) {
                Snapshot.sendApplyNotifications()
            }
            trampolineDispatcher.flush()
        }
}
