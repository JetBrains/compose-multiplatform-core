/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.runtime

import androidx.collection.MutableObjectList
import androidx.collection.MutableScatterSet
import androidx.collection.ScatterSet
import androidx.collection.emptyObjectList
import androidx.collection.emptyScatterSet
import androidx.collection.mutableScatterMapOf
import androidx.collection.mutableScatterSetOf
import androidx.compose.runtime.collection.MultiValueMap
import androidx.compose.runtime.collection.fastForEach
import androidx.compose.runtime.collection.fastMap
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.runtime.collection.wrapIntoSet
import androidx.compose.runtime.external.kotlinx.collections.immutable.persistentSetOf
import androidx.compose.runtime.internal.AtomicReference
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.internal.SnapshotThreadLocal
import androidx.compose.runtime.snapshots.SnapshotDataSource
import androidx.compose.runtime.internal.logError
import androidx.compose.runtime.internal.logWarning
import androidx.compose.runtime.internal.trace
import androidx.compose.runtime.platform.SynchronizedObject
import androidx.compose.runtime.platform.makeSynchronizedObject
import androidx.compose.runtime.platform.synchronized
import androidx.compose.runtime.snapshots.MutableSnapshot
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.ReaderKind
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.StateObjectImpl
import androidx.compose.runtime.snapshots.TransparentObserverMutableSnapshot
import androidx.compose.runtime.snapshots.TransparentObserverSnapshot
import androidx.compose.runtime.snapshots.fastAll
import androidx.compose.runtime.snapshots.fastAny
import androidx.compose.runtime.snapshots.fastFilterIndexed
import androidx.compose.runtime.snapshots.fastForEach
import androidx.compose.runtime.snapshots.fastGroupBy
import androidx.compose.runtime.snapshots.fastMap
import androidx.compose.runtime.snapshots.fastMapNotNull
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe
import kotlin.collections.removeLast as removeLastKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.native.concurrent.ThreadLocal
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal const val recomposerKey = 1000

// TODO: Can we use rootKey for this since all compositions will have an eventual Recomposer parent?
private inline val RecomposerCompoundHashKey
    get() = CompositeKeyHashCode(recomposerKey)

/**
 * Runs [block] with a new, active [Recomposer] applying changes in the calling [CoroutineContext].
 * The [Recomposer] will be [closed][Recomposer.close] after [block] returns.
 * [withRunningRecomposer] will return once the [Recomposer] is [Recomposer.State.ShutDown] and all
 * child jobs launched by [block] have [joined][Job.join].
 */
public suspend fun <R> withRunningRecomposer(
    block: suspend CoroutineScope.(recomposer: Recomposer) -> R
): R = coroutineScope {
    val recomposer = Recomposer(coroutineContext)
    // Will be cancelled when recomposerJob cancels
    launch { recomposer.runRecomposeAndApplyChanges() }
    block(recomposer).also {
        recomposer.close()
        recomposer.join()
    }
}

/**
 * Read-only information about a [Recomposer]. Used when code should only monitor the activity of a
 * [Recomposer], and not attempt to alter its state or create new compositions from it.
 */
public interface RecomposerInfo {
    /** The current [State] of the [Recomposer]. See each [State] value for its meaning. */
    // TODO: Mirror the currentState/StateFlow API change here once we can safely add
    // default interface methods. https://youtrack.jetbrains.com/issue/KT-47000
    public val state: Flow<Recomposer.State>

    /**
     * `true` if the [Recomposer] has been assigned work to do and it is currently performing that
     * work or awaiting an opportunity to do so.
     */
    public val hasPendingWork: Boolean

    /**
     * The running count of the number of times the [Recomposer] awoke and applied changes to one or
     * more [Composer]s. This count is unaffected if the composer awakes and recomposed but
     * composition did not produce changes to apply.
     */
    public val changeCount: Long

    /**
     * Get flow of error states captured in composition. This flow is only available when recomposer
     * is in hot reload mode.
     *
     * @return a flow of error states captured during composition
     */
    @ComposeToolingApi
    public val errorState: StateFlow<RecomposerErrorInformation?>
        get() = DefaultErrorStateFlow

    /**
     * Register an observer to be notified when a composition is added to or removed from the given
     * [Recomposer]. When this method is called, the observer will be notified of all currently
     * registered compositions per the documentation in
     * [CompositionRegistrationObserver.onCompositionRegistered].
     */
    @ExperimentalComposeRuntimeApi
    public fun observe(observer: CompositionRegistrationObserver): CompositionObserverHandle? = null

    private companion object {
        @ComposeToolingApi
        private val DefaultErrorStateFlow: StateFlow<RecomposerErrorInformation?> =
            MutableStateFlow(null)
    }
}

/** Read only information about [Recomposer] error state. */
@ComposeToolingApi
public interface RecomposerErrorInformation {
    /** Exception which forced recomposition to halt. */
    public val cause: Throwable

    /**
     * Whether composition can recover from the error by itself. If the error is not recoverable,
     * recomposer will not react to invalidate calls until state is reloaded.
     */
    public val isRecoverable: Boolean
}

/**
 * Read only information about [Recomposer] error state. This is an internal API only kept for
 * backward compatibility.
 */
// TODO(b/469471141): Remove when Live Edit no longer depends on this API.
@InternalComposeApi
internal interface RecomposerErrorInfo {
    /** Exception which forced recomposition to halt. */
    val cause: Throwable

    /**
     * Whether composition can recover from the error by itself. If the error is not recoverable,
     * recomposer will not react to invalidate calls until state is reloaded.
     */
    val recoverable: Boolean
}

/**
 * The scheduler for performing recomposition and applying updates to one or more [Composition]s.
 */
// RedundantVisibilityModifier suppressed because metalava picks up internal function overrides
// if 'internal' is not explicitly specified - b/171342041
// NotCloseable suppressed because this is Kotlin-only common code; [Auto]Closeable not available.
@Suppress("RedundantVisibilityModifier", "NotCloseable")
@OptIn(InternalComposeApi::class)
public class Recomposer(effectCoroutineContext: CoroutineContext) : CompositionContext() {
    /**
     * This is a running count of the number of times the recomposer awoke and applied changes to
     * one or more composers. This count is unaffected if the composer awakes and recomposed but
     * composition did not produce changes to apply.
     */
    public var changeCount: Long = 0L
        private set

    private val broadcastFrameClock = BroadcastFrameClock { onNewFrameAwaiter() }
    private val nextFrameEndCallbackQueue = NextFrameEndCallbackQueue { onNewFrameAwaiter() }

    /** Valid operational states of a [Recomposer]. */
    public enum class State {
        /**
         * [cancel] was called on the [Recomposer] and all cleanup work has completed. The
         * [Recomposer] is no longer available for use.
         */
        ShutDown,

        /**
         * [cancel] was called on the [Recomposer] and it is no longer available for use. Cleanup
         * work has not yet been fully completed and composition effect coroutines may still be
         * running.
         */
        ShuttingDown,

        /**
         * The [Recomposer] is not tracking invalidations for known composers and it will not
         * recompose them in response to changes. Call [runRecomposeAndApplyChanges] to await and
         * perform work. This is the initial state of a newly constructed [Recomposer].
         */
        Inactive,

        /**
         * The [Recomposer] is [Inactive] but at least one effect associated with a managed
         * composition is awaiting a frame. This frame will not be produced until the [Recomposer]
         * is [running][runRecomposeAndApplyChanges].
         */
        InactivePendingWork,

        /**
         * The [Recomposer] is tracking composition and snapshot invalidations but there is
         * currently no work to do.
         */
        Idle,

        /**
         * The [Recomposer] has been notified of pending work it must perform and is either actively
         * performing it or awaiting the appropriate opportunity to perform it. This work may
         * include invalidated composers that must be recomposed, snapshot state changes that must
         * be presented to known composers to check for invalidated compositions, or coroutines
         * awaiting a frame using the Recomposer's [MonotonicFrameClock].
         */
        PendingWork,
    }

    private val stateLock = makeSynchronizedObject()

    // Begin properties guarded by stateLock
    private var runnerJob: Job? = null
    private var closeCause: Throwable? = null
    private val _knownCompositions = mutableListOf<ControlledComposition>()
    private var _knownCompositionsCache: List<ControlledComposition>? = null
    private var snapshotInvalidations = MutableScatterSet<Any>()
    private val compositionInvalidations = mutableVectorOf<ControlledComposition>()
    private val compositionsAwaitingApply = mutableListOf<ControlledComposition>()

    /**
     * The compositions wave 2 held back in the current frame that have not composed yet.
     *
     * Wave 2 fills this set. The measure pass that follows the frame reads it, and delivery
     * removes from it. The next frame's recompose clears it. Guarded by [stateLock].
     *
     * This assumes the host's pipeline: the measure pass that consumes the set runs after this
     * recomposer's recompose block and before its next one, on the thread that runs both. A
     * scene renders that way. A pipeline that measures before the frame's recompose, or on
     * another thread, is outside what this set orders. There the fallback re-arm is what still
     * delivers.
     */
    private val pendingParentDriven = mutableScatterSetOf<ControlledComposition>()

    /**
     * For each composition in [pendingParentDriven], the compositions that wait for it to
     * compose. Guarded by [stateLock].
     */
    private var waitersByEnclosing =
        mutableScatterMapOf<ControlledComposition, MutableList<ControlledComposition>>()

    /**
     * Counts processed frames, so [reArmDeferred] can test adjacency. It ticks once per frame at
     * the top of the frame body. Guarded by [stateLock].
     */
    private var currentDeferralFrame = 0L

    /**
     * How many held-back compositions a host composed without reporting them current, counted at
     * the start of the frame after. See [ParentDrivenHosting.reportCurrent]. Guarded by [stateLock].
     */
    internal var deferralProtocolViolations = 0
        private set

    /**
     * How many frames started while the record still held a composition that its own gate had
     * held back, with no host call since the record was published and its host still
     * measure-pending. See [countUnconsumed]. Guarded by [stateLock].
     */
    internal var deferralPipelineViolations = 0
        private set

    /**
     * The published compositions that their own gate held back, rather than an enclosing one.
     * Their host is measure-pending, so a measure pass must reach them. Guarded by [stateLock].
     */
    private val heldByOwnGate = mutableScatterSetOf<ControlledComposition>()

    /** Host calls into the record since it was last published. Guarded by [stateLock]. */
    private var hostCallsSincePublish = 0

    /** Reused by [collectUnconsumedLocked] and [countUnconsumed]. Only the frame loop uses it. */
    private val unconsumedCandidates = mutableListOf<ControlledComposition>()
    private val movableContentAwaitingInsert = mutableListOf<MovableContentStateReference>()
    private val movableContentRemoved =
        MultiValueMap<MovableContent<Any?>, MovableContentStateReference>()
    private val movableContentNestedStatesAvailable = NestedContentMap()
    private val movableContentStatesAvailable =
        mutableScatterMapOf<MovableContentStateReference, MovableContentState>()
    private val movableContentNestedExtractionsPending =
        MultiValueMap<MovableContentStateReference, MovableContentStateReference>()
    private var failedCompositions: MutableList<ControlledComposition>? = null
    private var compositionsRemoved: MutableScatterSet<ControlledComposition>? = null
    private var workContinuation: CancellableContinuation<Unit>? = null
    private var concurrentCompositionsOutstanding = 0
    private var isClosed: Boolean = false
    private var errorState = MutableStateFlow<RecomposerErrorState?>(null)
    private val resilientModeEnabled = AtomicReference(false)

    private var frameClockPaused: Boolean = false
    // End properties guarded by stateLock

    private val _state = MutableStateFlow(State.Inactive)
    private val pausedScopes = SnapshotThreadLocal<MutableScatterSet<RecomposeScopeImpl>?>()

    /**
     * A [Job] used as a parent of any effects created by this [Recomposer]'s compositions. Its
     * cleanup is used to advance to [State.ShuttingDown] or [State.ShutDown].
     *
     * Initialized after other state above, since it is possible for [Job.invokeOnCompletion] to run
     * synchronously during construction if the [Recomposer] is constructed with a completed or
     * cancelled [Job].
     */
    private val effectJob =
        Job(effectCoroutineContext[Job]).apply {
            invokeOnCompletion { throwable ->
                // Since the running recompose job is operating in a disjoint job if present,
                // kick it out and make sure no new ones start if we have one.
                val cancellation =
                    CancellationException("Recomposer effect job completed", throwable)

                var continuationToResume: CancellableContinuation<Unit>? = null
                synchronized(stateLock) {
                    val runnerJob = runnerJob
                    if (runnerJob != null) {
                        _state.value = State.ShuttingDown
                        // If the recomposer is closed we will let the runnerJob return from
                        // runRecomposeAndApplyChanges normally and consider ourselves shut down
                        // immediately.
                        if (!isClosed) {
                            // This is the job hosting frameContinuation; no need to resume it
                            // otherwise
                            runnerJob.cancel(cancellation)
                        } else if (workContinuation != null) {
                            continuationToResume = workContinuation
                        }
                        workContinuation = null
                        runnerJob.invokeOnCompletion { runnerJobCause ->
                            synchronized(stateLock) {
                                closeCause =
                                    throwable?.apply {
                                        runnerJobCause
                                            ?.takeIf { it !is CancellationException }
                                            ?.let { addSuppressed(it) }
                                    }
                                _state.value = State.ShutDown
                            }
                        }
                    } else {
                        closeCause = cancellation
                        _state.value = State.ShutDown
                    }
                }
                continuationToResume?.resume(Unit)
            }
        }

    /** The [effectCoroutineContext] is derived from the parameter of the same name. */
    override val effectCoroutineContext: CoroutineContext =
        effectCoroutineContext + broadcastFrameClock + effectJob

    /**
     * The scene-owned cell carrying the current frame-cycle [DataSource.Snapshot], when frame
     * isolation is enabled. `null` on hosts that don't isolate frames.
     */
    /**
     * The domain declared in this recomposer's OWN coroutine context, inherited by every root
     * composition whose parent is this recomposer. That is the scene-less
     * [DataSourceCompositionDomain] shape: one recomposer, one domain, every composition in it.
     *
     * A host recomposer that drives scenes declares none here - it drives several domains at once
     * (a window's scene plus one per popup/layer) and a composition's domain is resolved from the
     * composition instead, via [domainOf].
     */
    internal override val frameSnapshotHolder: SnapshotHolder? =
        this.effectCoroutineContext[SnapshotHolder]

    /**
     * Every domain this recomposer drives, for the work spanning all of its compositions at once:
     * the pass-level slices below, and apply-observer routing. Seeded with the context-declared
     * domain above; hosts add and remove scenes' domains through [registerFrameDomain].
     *
     * Its own lock, not `stateLock`: this is taken on the paths that register snapshot observers,
     * and `stateLock` is taken by the apply observer they register, so sharing one lock would
     * couple those two orders.
     */
    private val frameDomainLock = makeSynchronizedObject()
    private val frameDomains: MutableList<SnapshotHolder> =
        mutableListOf<SnapshotHolder>().also { domains ->
            frameSnapshotHolder?.let { domains.add(it) }
        }

    /**
     * Registers a host-driven scene domain. A host recomposer exists before any scene does, and
     * gains and loses domains as scenes and their popups come and go, so apply routing is
     * re-derived on every change rather than decided once at startup.
     */
    @InternalComposeApi
    fun registerFrameDomain(holder: SnapshotHolder): ObserverHandle {
        synchronized(frameDomainLock) { frameDomains.add(holder) }
        syncFrameDomainApplyObservers()
        return ObserverHandle {
            synchronized(frameDomainLock) { frameDomains.remove(holder) }
            syncFrameDomainApplyObservers()
        }
    }

    /**
     * The domain [composition] belongs to. Resolved from the composition - which inherits it from
     * its parent chain - rather than from this recomposer, because one host recomposer drives
     * several. Falls back to the context-declared domain for a [ControlledComposition] that is not
     * a [CompositionImpl], i.e. a test double.
     */
    private fun domainOf(composition: ControlledComposition): SnapshotHolder? =
        if (composition is CompositionImpl) composition.frameSnapshotHolder
        else frameSnapshotHolder

    private val hasBroadcastFrameClockAwaitersLocked: Boolean
        get() = !frameClockPaused && broadcastFrameClock.hasAwaiters

    private val hasNextFrameEndAwaitersLocked: Boolean
        get() = !frameClockPaused && nextFrameEndCallbackQueue.hasAwaiters

    private val hasBroadcastFrameClockAwaiters: Boolean
        get() = synchronized(stateLock) { hasBroadcastFrameClockAwaitersLocked }

    @OptIn(ExperimentalComposeRuntimeApi::class)
    private var registrationObservers: MutableObjectList<CompositionRegistrationObserver>? = null

    /**
     * Determine the new value of [_state]. Call only while locked on [stateLock]. If it returns a
     * continuation, that continuation should be resumed after releasing the lock.
     */
    private fun deriveStateLocked(): CancellableContinuation<Unit>? {
        if (_state.value <= State.ShuttingDown) {
            clearKnownCompositionsLocked()
            snapshotInvalidations = MutableScatterSet()
            compositionInvalidations.clear()
            compositionsAwaitingApply.clear()
            movableContentAwaitingInsert.clear()
            failedCompositions = null
            workContinuation?.cancel()
            workContinuation = null
            errorState.value = null
            return null
        }

        val newState =
            when {
                errorState.value != null -> {
                    State.Inactive
                }
                runnerJob == null -> {
                    snapshotInvalidations = MutableScatterSet()
                    compositionInvalidations.clear()
                    if (hasBroadcastFrameClockAwaitersLocked || hasNextFrameEndAwaitersLocked)
                        State.InactivePendingWork
                    else State.Inactive
                }
                compositionInvalidations.isNotEmpty() ||
                    snapshotInvalidations.isNotEmpty() ||
                    compositionsAwaitingApply.isNotEmpty() ||
                    movableContentAwaitingInsert.isNotEmpty() ||
                    concurrentCompositionsOutstanding > 0 ||
                    hasBroadcastFrameClockAwaitersLocked ||
                    hasNextFrameEndAwaitersLocked ||
                    movableContentRemoved.isNotEmpty() -> State.PendingWork
                else -> State.Idle
            }

        _state.value = newState
        return if (newState == State.PendingWork) {
            workContinuation.also { workContinuation = null }
        } else null
    }

    private fun onNewFrameAwaiter() {
        synchronized(stateLock) {
                deriveStateLocked().also {
                    if (_state.value <= State.ShuttingDown)
                        throw CancellationException(
                            "Recomposer shutdown; frame clock awaiter will never resume",
                            closeCause,
                        )
                }
            }
            ?.resume(Unit)
    }

    /** `true` if there is still work to do for an active caller of [runRecomposeAndApplyChanges] */
    private val shouldKeepRecomposing: Boolean
        get() = synchronized(stateLock) { !isClosed } || effectJob.children.any { it.isActive }

    /** The current [State] of this [Recomposer]. See each [State] value for its meaning. */
    @Deprecated("Replaced by currentState as a StateFlow", ReplaceWith("currentState"))
    public val state: Flow<State>
        get() = currentState

    /** The current [State] of this [Recomposer], available synchronously. */
    public val currentState: StateFlow<State>
        get() = _state

    // A separate private object to avoid the temptation of casting a RecomposerInfo
    // to a Recomposer if Recomposer itself were to implement RecomposerInfo.
    private inner class RecomposerInfoImpl : RecomposerInfo {
        override val state: Flow<State>
            get() = this@Recomposer.currentState

        override val hasPendingWork: Boolean
            get() = this@Recomposer.hasPendingWork

        override val changeCount: Long
            get() = this@Recomposer.changeCount

        @ComposeToolingApi
        override val errorState: StateFlow<RecomposerErrorInformation?>
            get() = this@Recomposer.errorState

        @ComposeToolingApi
        val currentError: RecomposerErrorInformation?
            get() = synchronized(stateLock) { this@Recomposer.errorState.value }

        @OptIn(ExperimentalComposeRuntimeApi::class)
        override fun observe(observer: CompositionRegistrationObserver): CompositionObserverHandle =
            this@Recomposer.observe(observer)

        fun invalidateGroupsWithKey(key: Int) {
            val compositions: List<ControlledComposition> = knownCompositions()
            compositions
                .fastMapNotNull { it as? CompositionImpl }
                .fastForEach { it.invalidateGroupsWithKey(key) }
        }

        fun saveStateAndDisposeForHotReload(): List<HotReloadable> {
            val compositions: List<ControlledComposition> = knownCompositions()
            return compositions
                .fastMapNotNull { it as? CompositionImpl }
                .fastMap { HotReloadable(it).apply { clearContent() } }
        }

        fun resetErrorState(): RecomposerErrorState? = this@Recomposer.resetErrorState()

        fun retryFailedCompositions() = this@Recomposer.retryFailedCompositions()
    }

    private class HotReloadable(private val composition: CompositionImpl) {
        private var composable: @Composable () -> Unit = composition.composable

        fun clearContent() {
            if (composition.isRoot) {
                composition.setContent {}
            }
        }

        fun resetContent() {
            composition.composable = composable
        }

        fun recompose() {
            if (composition.isRoot) {
                composition.setContent(composable)
            }
        }
    }

    @OptIn(ComposeToolingApi::class)
    private class RecomposerErrorState(
        override val cause: Throwable,
        override val isRecoverable: Boolean,
    ) : RecomposerErrorInfo, RecomposerErrorInformation {
        override val recoverable: Boolean
            get() = isRecoverable
    }

    private val recomposerInfo = RecomposerInfoImpl()

    /** Obtain a read-only [RecomposerInfo] for this [Recomposer]. */
    public fun asRecomposerInfo(): RecomposerInfo = recomposerInfo

    /**
     * Simulates hot reload of the compositions known to this [Recomposer] only: their content is
     * disposed and composed again from the same content lambdas, so all remembered state is
     * discarded and all effects are re-launched.
     *
     * Unlike the global [androidx.compose.runtime.simulateHotReload], other recomposers of the
     * process are left untouched, and hot reload mode is only enabled for the duration of the
     * reload, so there is no need to call [disableHotReloadMode] afterwards.
     *
     * Must be called on the thread that drives the compositions of this [Recomposer].
     */
    @InternalComposeApi
    public fun simulateHotReload() {
        val wasHotReloadEnabled = _hotReloadEnabled.get()
        // Enabled for the duration of the reload so that a failure of the reload composition is
        // captured in `errorState` instead of being thrown, as it is during a real hot reload.
        _hotReloadEnabled.set(true)
        try {
            val holders = recomposerInfo.saveStateAndDisposeForHotReload()
            recomposerInfo.resetErrorState()
            holders.fastForEach { it.resetContent() }
            holders.fastForEach { it.recompose() }
            recomposerInfo.retryFailedCompositions()
        } finally {
            _hotReloadEnabled.set(wasHotReloadEnabled)
        }
    }

    /**
     * Propagate all invalidations from `snapshotInvalidations` to all the known compositions.
     *
     * @return `true` if the frame has work to do (e.g. [hasFrameWorkLocked])
     */
    private fun recordComposerModifications(): Boolean {
        var compositions: List<ControlledComposition> = emptyList()
        val changes =
            synchronized(stateLock) {
                if (snapshotInvalidations.isEmpty()) return hasFrameWorkLocked
                compositions = knownCompositionsLocked()
                snapshotInvalidations.wrapIntoSet().also {
                    snapshotInvalidations = MutableScatterSet()
                }
            }
        var complete = false
        try {
            run {
                compositions.fastForEach { composition ->
                    composition.recordModificationsOf(changes)

                    // Stop dispatching if the recomposer if we detect the recomposer
                    // is shutdown.
                    if (_state.value <= State.ShuttingDown) return@run
                }
            }
            complete = true
        } finally {
            if (!complete) {
                // If the previous loop was not complete, we have not sent all of theses
                // changes to all the composers so try again after the exception that caused
                // the early exit is handled and we can then retry sending the changes.
                synchronized(stateLock) { snapshotInvalidations.addAll(changes) }
            }
        }
        return synchronized(stateLock) {
            if (deriveStateLocked() != null) {
                error("called outside of runRecomposeAndApplyChanges")
            }
            hasFrameWorkLocked
        }
    }

    private fun registerRunnerJob(callingJob: Job) {
        synchronized(stateLock) {
            closeCause?.let { throw it }
            if (_state.value <= State.ShuttingDown) error("Recomposer shut down")
            if (runnerJob != null) error("Recomposer already running")
            runnerJob = callingJob
            if (deriveStateLocked() != null) {
                composeImmediateRuntimeError("called outside of runRecomposeAndApplyChanges")
            }
        }
    }

    /**
     * Await the invalidation of any associated [Composer]s, recompose them, and apply their changes
     * to their associated [Composition]s if recomposition is successful.
     *
     * While [runRecomposeAndApplyChanges] is running, [awaitIdle] will suspend until there are no
     * more invalid composers awaiting recomposition.
     *
     * This method will not return unless the [Recomposer] is [close]d and all effects in managed
     * compositions complete. Unhandled failure exceptions from child coroutines will be thrown by
     * this method.
     */
    public suspend fun runRecomposeAndApplyChanges(): Unit =
        recompositionRunner { parentFrameClock ->
            val toRecompose = mutableListOf<ControlledComposition>()
            // Reused each frame: the depth-ordered copies wave 1 and wave 2 iterate.
            val waveOneOrder = mutableListOf<ControlledComposition>()
            val waveTwoOrder = mutableListOf<ControlledComposition>()
            val toInsert = mutableListOf<MovableContentStateReference>()
            val toApply = mutableListOf<ControlledComposition>()
            val toLateApply = mutableScatterSetOf<ControlledComposition>()
            val toComplete = mutableScatterSetOf<ControlledComposition>()
            val modifiedValues = MutableScatterSet<Any>()
            val modifiedValuesSet = modifiedValues.wrapIntoSet()
            val alreadyComposed = mutableScatterSetOf<ControlledComposition>()
            val skippedParentDriven = mutableScatterSetOf<ControlledComposition>()
            // Wave 2 defers every composition in this set. A composition's own gate causes
            // the deferral, or the propagation from an enclosing composition causes it.
            // nearestDeferredAncestor below reads this set to find the nearest held-back
            // ancestor of a propagation-deferred composition, so it can be recorded as that
            // ancestor's waiter. The end of wave 2 publishes this set into pendingParentDriven.
            val deferredParentDriven = mutableScatterSetOf<ControlledComposition>()
            // The propagation defers every composition in this list, and wave 2 re-arms these
            // as the fallback. A gate schedules its own refresh, so a gate-deferred composition
            // needs no re-arm. A host's measure-time hold re-arms its slot on its own.
            val toReArmAfterDeferral = mutableListOf<ControlledComposition>()
            // The compositions wave 2 held back by their own gate. Published into heldByOwnGate.
            val heldByOwnGateThisFrame = mutableScatterSetOf<ControlledComposition>()
            // Wave 2 records here which held-back composition each propagation-deferred one waits
            // for. The end of wave 2 publishes it by trading places with waitersByEnclosing.
            var deferralWaiters =
                mutableScatterMapOf<ControlledComposition, MutableList<ControlledComposition>>()

            fun enqueueForRecompose(composition: ControlledComposition) {
                if (composition !in toRecompose && composition !in skippedParentDriven) {
                    toRecompose += composition
                }
            }

            // Parent-driven compositions (those with a recompose gate installed) are handled
            // in two phases. The recompose pass DEFERS all of them: a standalone
            // recomposition would pair the captures baked into their content lambda by the
            // parent with fresh direct reads, and whether the parent is about to refresh
            // those captures is not knowable yet. applyChanges installs the refreshed
            // content, for example a new SubcomposeLayout measure policy that captures new
            // values, and then marks the host's measure pending. AFTER the apply stage
            // the gate is exact: gated compositions are left to the pending measure pass
            // (which recomposes them with fresh captures), and the rest recompose in a
            // second wave. The second wave runs in the same frame, with captures that are
            // provably current. A changed value would have already made the parent's body
            // or measure dirty.
            // A composition also waits when an enclosing one is deferred, even if its own
            // gate lets it through. It waits for the nearest deferred enclosing composition.
            // That one releases it once its own host has re-run it in this frame's measure
            // pass. A re-arm is the fallback for a frame in which that does not happen. Wave 2
            // receives every composition with a gate installed, and every composition wave 1
            // sends there because an enclosing composition already waits.
            fun isParentDrivenComposition(composition: ControlledComposition): Boolean =
                (composition as? CompositionImpl)?.parentDrivenRecomposeGate != null

            fun shouldSkipParentDrivenComposition(
                composition: ControlledComposition
            ): Boolean =
                (composition as? CompositionImpl)?.parentDrivenRecomposeGate?.invoke() == true

            // An enclosing composition re-executes and supplies fresh values to a nested
            // composition. A nested composition must not run first with values captured in
            // an earlier pass. Wave 1 sorts toRecompose by depth before it calls this
            // function. Wave 2 sorts skippedParentDriven by depth before it calls this
            // function. A parent has a lower depth than the sub-composition it hosts.
            // Ascending depth order classifies every ancestor before this function runs on
            // it. There is no exception to this order.
            // Tests whether any composition enclosing [composition] is in [set]. The walk climbs
            // parentComposition, so a root ends it.
            fun hasAncestorIn(
                composition: ControlledComposition,
                set: ScatterSet<ControlledComposition>,
            ): Boolean {
                var enclosing = (composition as? CompositionImpl)?.parentComposition
                while (enclosing != null) {
                    if (enclosing in set) return true
                    enclosing = enclosing.parentComposition
                }
                return false
            }

            // The nearest composition enclosing [composition] that wave 2 already held back, or
            // null. Wave 2 visits in ascending depth, so every ancestor is classified first.
            fun nearestDeferredAncestor(
                composition: ControlledComposition
            ): ControlledComposition? {
                var enclosing = (composition as? CompositionImpl)?.parentComposition
                while (enclosing != null) {
                    if (enclosing in deferredParentDriven) return enclosing
                    enclosing = enclosing.parentComposition
                }
                return null
            }

            fun clearRecompositionState() {
                synchronized(stateLock) {
                    toRecompose.clear()
                    toInsert.clear()
                    waveOneOrder.clear()
                    waveTwoOrder.clear()

                    toApply.fastForEach {
                        it.abandonChanges()
                        recordFailedCompositionLocked(it)
                    }
                    toApply.clear()

                    toLateApply.forEach {
                        it.abandonChanges()
                        recordFailedCompositionLocked(it)
                    }
                    toLateApply.clear()

                    toComplete.forEach { it.changesApplied() }
                    toComplete.clear()

                    modifiedValues.clear()
                    skippedParentDriven.clear()
                    deferredParentDriven.clear()
                    toReArmAfterDeferral.clear()
                    deferralWaiters.clear()
                    heldByOwnGateThisFrame.clear()
                    clearDeferralRecordLocked()

                    alreadyComposed.forEach {
                        it.abandonChanges()
                        recordFailedCompositionLocked(it)
                    }
                    alreadyComposed.clear()
                }
            }

            fun fillToInsert() {
                toInsert.clear()
                synchronized(stateLock) {
                    movableContentAwaitingInsert.fastForEach { toInsert += it }
                    movableContentAwaitingInsert.clear()
                }
            }

            while (shouldKeepRecomposing) {
                awaitWorkAvailable()

                // Don't await a new frame if we don't have frame-scoped work
                if (!recordComposerModifications()) continue

                // Align work with the next frame to coalesce changes.
                // Note: it is possible to resume from the above with no recompositions pending,
                // instead someone might be awaiting our frame clock dispatch below.
                // We use the cached frame clock from above not just so that we don't locate it
                // each time, but because we've installed the broadcastFrameClock as the scope
                // clock above for user code to locate.
                parentFrameClock.withFrameNanos { frameTime ->
                    val deferralFrameOrdinal =
                        synchronized(stateLock) {
                            // Before the protocol check, which clears the marks this one
                            // reads as proof that a host ran.
                            collectUnconsumedLocked()
                            countUnreportedComposesLocked()
                            clearDeferralRecordLocked()
                            // A skip recorded after the last recompose block, in a measure pass
                            // or outside any frame, belongs to that turn. Its compositions can
                            // still be alive, so it must not swallow this frame's changes.
                            compositionsRemoved = null
                            ++currentDeferralFrame
                        }
                    countUnconsumed()
                    // Dispatch MonotonicFrameClock frames first; this may produce new
                    // composer invalidations that we must handle during the same frame.
                    if (hasBroadcastFrameClockAwaiters) {
                        trace("Recomposer:animation") {
                            withTransactionOrApplyNotifications {
                                // Propagate the frame time to anyone who is awaiting from the
                                // recomposer clock.
                                broadcastFrameClock.sendFrame(frameTime)
                            }
                        }
                    }

                    trace("Recomposer:recompose") {
                        skippedParentDriven.clear()

                        // Drain any composer invalidations from snapshot changes and record
                        // composers to work on
                        recordComposerModifications()
                        synchronized(stateLock) {
                            compositionInvalidations.forEach { composition ->
                                enqueueForRecompose(composition)
                                (composition as? CompositionImpl)?.deferralReArmPending = false
                            }
                            compositionInvalidations.clear()
                        }

                        // Perform recomposition for any invalidated composers
                        modifiedValues.clear()
                        alreadyComposed.clear()
                        withIsolationOrNotifyObjectsInitialized {while (toRecompose.isNotEmpty() || toInsert.isNotEmpty()) {
                            try {
                                // Visit an enclosing composition before one nested inside it.
                                // Wave 2 visits compositions in the same order. Wave 1 also
                                // skips a composition when an enclosing one is already skipped.
                                // Without the skip, the nested composition would recompose with
                                // a lambda the enclosing composition has not refreshed yet.
                                // Ascending depth classifies every ancestor first, so the skip
                                // sees an enclosing composition before the ones nested in it.
                                waveOneOrder.clear()
                                waveOneOrder.addAll(toRecompose)
                                if (waveOneOrder.size > 1) {
                                    waveOneOrder.sortBy {
                                        (it as? CompositionImpl)?.compositionDepth ?: 0
                                    }
                                }
                                waveOneOrder.fastForEach { composition ->
                                    if (
                                        isParentDrivenComposition(composition) ||
                                            (skippedParentDriven.isNotEmpty() &&
                                                hasAncestorIn(composition, skippedParentDriven))
                                    ) {
                                        skippedParentDriven.add(composition)
                                    } else {
                                        performRecompose(composition, modifiedValues)?.let {
                                            toApply += it
                                        }
                                        alreadyComposed.add(composition)
                                    }
                                }
                            } catch (e: Throwable) {
                                processCompositionError(e, recoverable = true)
                                clearRecompositionState()
                                return@withFrameNanos
                            } finally {
                                toRecompose.clear()
                                waveOneOrder.clear()
                            }

                                // Find any trailing recompositions that need to be composed because
                                // of a value change by a composition. This can happen, for example, if
                                // a CompositionLocal changes in a parent and was read in a child
                                // composition that was otherwise valid.
                                if (
                                    modifiedValues.isNotEmpty() || compositionInvalidations.isNotEmpty()
                                ) {
                                    synchronized(stateLock) {
                                        knownCompositionsLocked().fastForEach { value ->
                                            if (
                                                value !in alreadyComposed &&
                                                value !in skippedParentDriven &&
                                                value.observesAnyOf(modifiedValuesSet)
                                            ) {
                                                enqueueForRecompose(value)
                                            }
                                        }

                                        // Composable lambda is a special kind of value that is not
                                        // observed
                                        // by the snapshot system, but invalidates composition scope
                                        // directly instead.
                                        compositionInvalidations.removeIf { value ->
                                            if (
                                                value !in alreadyComposed &&
                                                value !in skippedParentDriven &&
                                                value !in toRecompose
                                            ) {
                                                enqueueForRecompose(value)
                                                (value as? CompositionImpl)?.deferralReArmPending =
                                                    false
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    }
                                }

                                if (toRecompose.isEmpty()) {
                                    try {
                                        fillToInsert()
                                        while (toInsert.isNotEmpty()) {
                                            toLateApply += performInsertValues(
                                                toInsert,
                                                modifiedValues
                                            )
                                            fillToInsert()
                                        }
                                    } catch (e: Throwable) {
                                        processCompositionError(e, recoverable = true)
                                        clearRecompositionState()
                                        return@withFrameNanos
                                    }
                                }
                            }

                            // This is an optimization to avoid reallocating TransparentSnapshot for
                            // each observeChanges within `apply`. Many modifiers use observation in
                            // `onAttach` and other lifecycle methods, and allocations can be mitigated
                            // by updating read observer in the snapshot allocated here.
                            withTransparentSnapshot {
                                if (toApply.isNotEmpty()) {
                                    changeCount++

                                    // Perform apply changes
                                    try {
                                        // We could do toComplete += toApply but doing it like below
                                        // avoids unnecessary allocations since toApply is a mutable
                                        // list
                                        // toComplete += toApply
                                        toApply.fastForEach { composition ->
                                            toComplete.add(composition)
                                        }
                                        toApply.fastForEach { composition ->
                                            composition.applyChanges()
                                        }
                                    } catch (e: Throwable) {
                                        processCompositionError(e)
                                        clearRecompositionState()
                                        return@withFrameNanos
                                    } finally {
                                        toApply.clear()
                                    }
                                }

                                if (toLateApply.isNotEmpty()) {
                                    try {
                                        toComplete += toLateApply
                                        toLateApply.forEach { composition ->
                                            composition.applyLateChanges()
                                        }
                                    } catch (e: Throwable) {
                                        processCompositionError(e)
                                        clearRecompositionState()
                                        return@withFrameNanos
                                    } finally {
                                        toLateApply.clear()
                                    }
                                }

                                // The second wave is for the deferred parent-driven
                                // compositions. The apply stage above installed any refreshed
                                // parent content. It also marked the affected hosts' measure
                                // pending. The gate is now exact. See the note at its
                                // declaration. A composition the gate still defers is left to
                                // the pending measure pass. A composition that an enclosing
                                // deferred composition holds back waits for it to compose, and
                                // is re-armed as the fallback. The rest recompose and apply now,
                                // in the same frame.
                                if (skippedParentDriven.isNotEmpty()) {
                                    // Visit an enclosing composition before one nested inside it.
                                    // An enclosing composition's recompose and apply supplies
                                    // fresh values to the nested one. The nested composition
                                    // must not run first with stale values. Depth gives that
                                    // order directly. Registration order only approximated it.
                                    // Two error paths could still invert registration order. The
                                    // isDisposed guard below then skips any nested composition an
                                    // earlier apply already removed.
                                    waveTwoOrder.clear()
                                    skippedParentDriven.forEach { waveTwoOrder += it }
                                    if (waveTwoOrder.size > 1) {
                                        waveTwoOrder.sortBy {
                                            (it as? CompositionImpl)?.compositionDepth ?: 0
                                        }
                                    }
                                    deferredParentDriven.clear()
                                    toReArmAfterDeferral.clear()
                                    waveTwoOrder.fastForEach { composition ->
                                        if (composition.isDisposed) return@fastForEach
                                        if (shouldSkipParentDrivenComposition(composition)) {
                                            // The host's pending measure re-runs the content, so
                                            // this invalidation is consumed by design.
                                            deferredParentDriven.add(composition)
                                            heldByOwnGateThisFrame.add(composition)
                                            return@fastForEach
                                        }
                                        val enclosing =
                                            if (deferredParentDriven.isNotEmpty()) {
                                                nearestDeferredAncestor(composition)
                                            } else null
                                        if (enclosing != null) {
                                            // The enclosing composition releases this one once
                                            // its host has re-run it in this frame's measure
                                            // pass. The re-arm is the fallback for a frame in
                                            // which that does not happen.
                                            deferredParentDriven.add(composition)
                                            deferralWaiters
                                                .getOrPut(enclosing) { mutableListOf() }
                                                .add(composition)
                                            toReArmAfterDeferral += composition
                                            return@fastForEach
                                        }
                                        val needsApply =
                                            try {
                                                performRecompose(composition, modifiedValues)
                                            } catch (e: Throwable) {
                                                processCompositionError(e, recoverable = true)
                                                clearRecompositionState()
                                                return@withFrameNanos
                                            }
                                        if (needsApply != null) {
                                            toComplete.add(needsApply)
                                            try {
                                                needsApply.applyChanges()
                                                needsApply.applyLateChanges()
                                            } catch (e: Throwable) {
                                                processCompositionError(e)
                                                clearRecompositionState()
                                                return@withFrameNanos
                                            }
                                        }
                                        alreadyComposed.add(composition)
                                    }

                                    waveTwoOrder.clear()

                                    // Re-arm each composition the propagation deferred above. The
                                    // re-arm is the fallback for a frame in which its enclosing
                                    // composition does not compose and release it.
                                    toReArmAfterDeferral.fastForEach { composition ->
                                        if (!composition.isDisposed) {
                                            reArmDeferred(composition, deferralFrameOrdinal)
                                        }
                                    }

                                    // Publish what wave 2 held back, for the measure pass that
                                    // follows this frame.
                                    synchronized(stateLock) {
                                        deferredParentDriven.forEach {
                                            pendingParentDriven += it
                                            (it as? CompositionImpl)?.composedSinceReport = false
                                        }
                                        heldByOwnGate += heldByOwnGateThisFrame
                                        hostCallsSincePublish = 0
                                        // The frame start emptied waitersByEnclosing, and no host
                                        // records a waiter before this, so the maps trade places
                                        // instead of copying. The copy stays as the fallback.
                                        if (waitersByEnclosing.isEmpty()) {
                                            val published = deferralWaiters
                                            deferralWaiters = waitersByEnclosing
                                            waitersByEnclosing = published
                                        } else {
                                            deferralWaiters.forEach { enclosing, waiters ->
                                                waitersByEnclosing
                                                    .getOrPut(enclosing) { mutableListOf() }
                                                    .addAll(waiters)
                                            }
                                            deferralWaiters.clear()
                                        }
                                    }
                                }

                                if (toComplete.isNotEmpty()) {
                                    try {
                                        toComplete.forEach { composition ->
                                            composition.changesApplied()
                                        }
                                    } catch (e: Throwable) {
                                        processCompositionError(e)
                                        clearRecompositionState()
                                        return@withFrameNanos
                                    } finally {
                                        toComplete.clear()
                                    }
                                }
                            }

                            synchronized(stateLock) {
                                runtimeCheck(deriveStateLocked() == null) {
                                    "unexpected to get continuation here"
                                }
                            }
                        }
                        alreadyComposed.clear()
                        skippedParentDriven.clear()
                        deferredParentDriven.clear()
                        toReArmAfterDeferral.clear()
                        deferralWaiters.clear()
                        heldByOwnGateThisFrame.clear()
                        modifiedValues.clear()
                        compositionsRemoved = null
                    }
                }

                discardUnusedMovableContentState()
                nextFrameEndCallbackQueue.markFrameComplete()
            }
        }

    private fun processCompositionError(
        e: Throwable,
        failedInitialComposition: ControlledComposition? = null,
        recoverable: Boolean = false,
    ) {
        if ((_hotReloadEnabled.get() || resilientModeEnabled.get()) && e !is ComposeRuntimeError) {
            synchronized(stateLock) {
                logError("Error was captured in composition while error recovery was enabled.", e)

                compositionsAwaitingApply.clear()
                compositionInvalidations.clear()
                snapshotInvalidations = MutableScatterSet()

                movableContentAwaitingInsert.clear()
                movableContentRemoved.clear()
                movableContentStatesAvailable.clear()

                // Recovery recomposes everything, and every pending invalidation is dropped above,
                // so nothing may stay held behind a composition this frame recorded.
                clearDeferralRecordLocked()

                errorState.value = RecomposerErrorState(isRecoverable = recoverable, cause = e)

                if (failedInitialComposition != null) {
                    recordFailedCompositionLocked(failedInitialComposition)
                }

                if (deriveStateLocked() != null) {
                    composeImmediateRuntimeError(
                        "expected to go to inactive state due to composition error"
                    )
                }
            }
        } else {
            // withFrameNanos uses `runCatching` to ensure that crashes are not propagated to
            // AndroidUiDispatcher. This means that errors that happen during recomposition might
            // be delayed by a frame and swallowed if composed into inconsistent state caused by
            // the error.
            // Common case is subcomposition: if measure occurs after recomposition has thrown,
            // composeInitial will throw because of corrupted composition while original exception
            // won't be recorded.
            synchronized(stateLock) {
                logError("Error was captured in composition.", e)
                // The error state stops all further frames, so no frame start would clear the
                // record, and every slot under a stale entry would stay held.
                clearDeferralRecordLocked()
                val errorState = errorState.value
                if (errorState == null) {
                    // Record exception if current error state is empty.
                    this.errorState.value = RecomposerErrorState(isRecoverable = false, cause = e)
                } else {
                    // Re-throw original cause if we recorded it previously.
                    throw errorState.cause
                }
            }

            throw e
        }
    }

    private inline fun withTransparentSnapshot(block: () -> Unit) {
        val currentSnapshot = Snapshot.current

        val snapshot =
            if (currentSnapshot is MutableSnapshot) {
                TransparentObserverMutableSnapshot(
                    currentSnapshot,
                    null,
                    null,
                    mergeParentObservers = true,
                    ownsParentSnapshot = false,
                )
            } else {
                TransparentObserverSnapshot(
                    currentSnapshot,
                    null,
                    mergeParentObservers = true,
                    ownsParentSnapshot = false,
                )
            }
        try {
            snapshot.enter(block)
        } finally {
            snapshot.dispose()
        }
    }

    /**
     * Returns a cached copy of the list of known compositions that can be iterated safely without
     * holding the `stateLock`.
     */
    private fun knownCompositions(): List<ControlledComposition> {
        return synchronized(stateLock) { knownCompositionsLocked() }
    }

    private fun knownCompositionsLocked(): List<ControlledComposition> {
        val cache = _knownCompositionsCache
        if (cache != null) return cache

        val compositions = _knownCompositions
        val newCache = if (compositions.isEmpty()) emptyList() else ArrayList(compositions)
        _knownCompositionsCache = newCache
        return newCache
    }

    @OptIn(ExperimentalComposeRuntimeApi::class)
    private fun clearKnownCompositionsLocked() {
        knownCompositionsLocked().fastForEach { composition ->
            unregisterCompositionLocked(composition)
        }
        _knownCompositions.clear()
        _knownCompositionsCache = emptyList()
    }

    private fun removeKnownCompositionLocked(composition: ControlledComposition) {
        if (_knownCompositions.remove(composition)) {
            _knownCompositionsCache = null
            unregisterCompositionLocked(composition)
        }
    }

    private fun addKnownCompositionLocked(composition: ControlledComposition) {
        _knownCompositions += composition
        _knownCompositionsCache = null
    }

    @OptIn(ExperimentalComposeRuntimeApi::class)
    private fun registerCompositionLocked(composition: ControlledComposition) {
        registrationObservers?.forEach {
            if (composition is ObservableComposition) {
                it.onCompositionRegistered(composition)
            }
        }
    }

    @OptIn(ExperimentalComposeRuntimeApi::class)
    private fun unregisterCompositionLocked(composition: ControlledComposition) {
        registrationObservers?.forEach {
            if (composition is ObservableComposition) {
                it.onCompositionUnregistered(composition)
            }
        }
    }

    @OptIn(ExperimentalComposeRuntimeApi::class)
    internal fun addCompositionRegistrationObserver(
        observer: CompositionRegistrationObserver
    ): CompositionObserverHandle {
        synchronized(stateLock) {
            val observers =
                registrationObservers
                    ?: MutableObjectList<CompositionRegistrationObserver>().also {
                        registrationObservers = it
                    }

            observers += observer
            _knownCompositions.fastForEach { composition ->
                if (composition is ObservableComposition) {
                    observer.onCompositionRegistered(composition)
                }
            }
        }

        return object : CompositionObserverHandle {
            override fun dispose() {
                synchronized(stateLock) { registrationObservers?.remove(observer) }
            }
        }
    }

    private fun resetErrorState(): RecomposerErrorState? {
        var error: RecomposerErrorState? = null
        synchronized(stateLock) {
                error = errorState.value
                if (error != null) {
                    errorState.value = null
                    deriveStateLocked()
                } else {
                    null
                }
            }
            ?.resume(Unit)
        return error
    }

    private fun retryFailedCompositions() {
        val compositionsToRetry =
            synchronized(stateLock) { failedCompositions.also { failedCompositions = null } }
                ?: return
        try {
            while (compositionsToRetry.isNotEmpty()) {
                val composition = compositionsToRetry.removeLastKt()
                if (composition !is CompositionImpl) continue

                composition.invalidateAll()
                composition.setContent(composition.composable)

                if (errorState.value != null) break
            }
        } finally {
            if (compositionsToRetry.isNotEmpty()) {
                // If we did not complete the last list then add the remaining compositions back
                // into the failedCompositions list
                synchronized(stateLock) {
                    compositionsToRetry.fastForEach { recordFailedCompositionLocked(it) }
                }
            }
        }
    }

    private fun recordFailedCompositionLocked(composition: ControlledComposition) {
        val failedCompositions =
            failedCompositions
                ?: mutableListOf<ControlledComposition>().also { failedCompositions = it }

        if (composition !in failedCompositions) {
            failedCompositions += composition
        }
        removeKnownCompositionLocked(composition)
    }

    private val hasSchedulingWork: Boolean
        get() =
            synchronized(stateLock) {
                snapshotInvalidations.isNotEmpty() ||
                    compositionInvalidations.isNotEmpty() ||
                    hasBroadcastFrameClockAwaitersLocked ||
                    hasNextFrameEndAwaitersLocked
            }

    private suspend fun awaitWorkAvailable() {
        if (!hasSchedulingWork) {
            // NOTE: Do not remove the `<Unit>` from the next line even if the IDE reports it as
            // redundant. Removing this causes reports it cannot infer the type. (KT-79553)
            @Suppress("RemoveExplicitTypeArguments") // See note above
            suspendCancellableCoroutine<Unit> { co ->
                synchronized(stateLock) {
                        if (hasSchedulingWork) {
                            co
                        } else {
                            workContinuation = co
                            null
                        }
                    }
                    ?.resume(Unit)
            }
        }
    }

    @OptIn(ExperimentalComposeApi::class)
    private suspend fun recompositionRunner(
        block: suspend CoroutineScope.(parentFrameClock: MonotonicFrameClock) -> Unit
    ) {
        val parentFrameClock = coroutineContext.monotonicFrameClock
        withContext(broadcastFrameClock) {
            // Enforce mutual exclusion of callers; register self as current runner
            val callingJob = coroutineContext.job
            registerRunnerJob(callingJob)

            // Observe data source invalidations and propagate them to known composers only from
            // this caller's dispatcher, never working with the same composer in parallel.
            // unregisterApplyObserver is called as part of the big finally below
            val unregisterApplyObserver =
                registerFrameDomainApplyObserver { changed, _ ->
                    synchronized(stateLock) {
                            if (_state.value >= State.Idle) {
                                val snapshotInvalidations = snapshotInvalidations
                                changed.fastForEach {
                                    if (
                                        it is StateObjectImpl &&
                                            !it.isReadIn(ReaderKind.Composition)
                                    ) {
                                        // continue if we know that state is never read in
                                        // composition
                                        return@fastForEach
                                    }
                                    snapshotInvalidations.add(it)
                                }
                                deriveStateLocked()
                            } else null
                        }
                        ?.resume(Unit)
                }

            addRunning(recomposerInfo)

            try {
                // Invalidate all registered composers when we start since we weren't observing
                // snapshot changes on their behalf. Assume anything could have changed.
                knownCompositions().fastForEach { it.invalidateAll() }

                coroutineScope { block(parentFrameClock) }
            } finally {
                unregisterApplyObserver.dispose()
                synchronized(stateLock) {
                    if (runnerJob === callingJob) {
                        runnerJob = null
                    }
                    if (deriveStateLocked() != null) {
                        composeImmediateRuntimeError(
                            "called outside of runRecomposeAndApplyChanges"
                        )
                    }
                }
                removeRunning(recomposerInfo)
            }
        }
    }

    /**
     * Permanently shut down this [Recomposer] for future use. [currentState] will immediately
     * reflect [State.ShuttingDown] (or a lower state) before this call returns. All ongoing
     * recompositions will stop, new composer invalidations with this [Recomposer] at the root will
     * no longer occur, and any [LaunchedEffect]s currently running in compositions managed by this
     * [Recomposer] will be cancelled. Any [rememberCoroutineScope] scopes from compositions managed
     * by this [Recomposer] will also be cancelled. See [join] to await the completion of all of
     * these outstanding tasks.
     */
    public fun cancel() {
        // Move to State.ShuttingDown immediately rather than waiting for effectJob to join
        // if we're cancelling to shut down the Recomposer. This permits other client code
        // to use `state.first { it < State.Idle }` or similar to reliably and immediately detect
        // that the recomposer can no longer be used.
        // It looks like a CAS loop would be more appropriate here, but other occurrences
        // of taking stateLock assume that the state cannot change without holding it.
        synchronized(stateLock) {
            if (_state.value >= State.Idle) {
                _state.value = State.ShuttingDown
            }
        }
        effectJob.cancel()
    }

    /**
     * Close this [Recomposer]. Once all effects launched by managed compositions complete, any
     * active call to [runRecomposeAndApplyChanges] will return normally and this [Recomposer] will
     * be [State.ShutDown]. See [join] to await the completion of all of these outstanding tasks.
     */
    public fun close() {
        if (effectJob.complete()) {
            synchronized(stateLock) { isClosed = true }
        }
    }

    /** Await the completion of a [cancel] operation. */
    public suspend fun join() {
        currentState.first { it == State.ShutDown }
    }

    /**
     * Schedules an [action] to be invoked when the recomposer finishes the next composition of a
     * frame (including the completion of subcompositions). If a frame is currently in-progress,
     * [action] will be invoked when the current frame fully finishes composing. If a frame isn't
     * currently in-progress, a new frame will be scheduled (if one hasn't been already) and
     * [action] will execute at the completion of the next frame's composition. If a new frame is
     * scheduled and there is no other work to execute, [action] will still execute.
     *
     * [action] will always execute on the applier thread.
     *
     * @return A [CancellationHandle] that can be used to unregister the [action]. The returned
     *   handle is thread-safe and may be cancelled from any thread. Cancelling the handle only
     *   removes the callback from the queue. If [action] is currently executing, it will not be
     *   cancelled by this handle.
     */
    public override fun scheduleFrameEndCallback(action: () -> Unit): CancellationHandle {
        return nextFrameEndCallbackQueue.scheduleFrameEndCallback(action)
    }

    internal override fun composeInitial(
        composition: ControlledComposition,
        content: @Composable () -> Unit,
    ) {
        val composerWasComposing = composition.isComposing

        val newComposition =
            synchronized(stateLock) {
                if (_state.value > State.ShuttingDown) {
                    val new = composition !in knownCompositionsLocked()
                    if (new) {
                        registerCompositionLocked(composition)
                    }
                    new
                } else {
                    true
                }
            }

        try {
            composing(composition, null) { composition.composeContent(content) }
        } catch (e: Throwable) {
            if (newComposition) {
                synchronized(stateLock) { unregisterCompositionLocked(composition) }
            }

            processCompositionError(e, composition, recoverable = true)
            return
        }

        synchronized(stateLock) {
            if (_state.value > State.ShuttingDown) {
                if (composition !in knownCompositionsLocked()) {
                    addKnownCompositionLocked(composition)
                }
            } else {
                unregisterCompositionLocked(composition)
            }
        }

        // TODO(b/143755743)
        if (!composerWasComposing && domainOf(composition) == null) {
            Snapshot.notifyObjectsInitialized()
        }

        try {
            performInitialMovableContentInserts(composition)
        } catch (e: Throwable) {
            processCompositionError(e, composition, recoverable = true)
            return
        }

        try {
            composition.applyChanges()
            composition.applyLateChanges()
        } catch (e: Throwable) {
            processCompositionError(e)
            return
        }

        if (!composerWasComposing && domainOf(composition) == null) {
            // Ensure that any state objects created during applyChanges are seen as changed
            // if modified after this call.
            Snapshot.notifyObjectsInitialized()
        }
    }

    internal override fun composeInitialPaused(
        composition: ControlledComposition,
        shouldPause: ShouldPauseCallback,
        content: @Composable () -> Unit,
    ): ScatterSet<RecomposeScopeImpl> {
        return try {
            composition.pausable(shouldPause) {
                composeInitial(composition, content)
                pausedScopes.get() ?: emptyScatterSet()
            }
        } finally {
            pausedScopes.set(null)
        }
    }

    internal override fun recomposePaused(
        composition: ControlledComposition,
        shouldPause: ShouldPauseCallback,
        invalidScopes: ScatterSet<RecomposeScopeImpl>,
    ): ScatterSet<RecomposeScopeImpl> {
        return try {
            recordComposerModifications()
            composition.recordModificationsOf(invalidScopes.wrapIntoSet())
            composition.pausable(shouldPause) {
                val needsApply = performRecompose(composition, null)
                if (needsApply != null) {
                    performInitialMovableContentInserts(composition)
                    needsApply.applyChanges()
                    needsApply.applyLateChanges()
                }
                pausedScopes.get() ?: emptyScatterSet()
            }
        } finally {
            pausedScopes.set(null)
        }
    }

    override fun reportPausedScope(scope: RecomposeScopeImpl) {
        val scopes =
            pausedScopes.get()
                ?: run {
                    val newScopes = mutableScatterSetOf<RecomposeScopeImpl>()
                    pausedScopes.set(newScopes)
                    newScopes
                }
        scopes.add(scope)
    }

    private fun performInitialMovableContentInserts(composition: ControlledComposition) {
        synchronized(stateLock) {
            if (!movableContentAwaitingInsert.fastAny { it.composition == composition }) return
        }
        val toInsert = mutableListOf<MovableContentStateReference>()
        fun fillToInsert() {
            toInsert.clear()
            synchronized(stateLock) {
                val iterator = movableContentAwaitingInsert.iterator()
                while (iterator.hasNext()) {
                    val value = iterator.next()
                    if (value.composition == composition) {
                        toInsert.add(value)
                        iterator.remove()
                    }
                }
            }
        }
        fillToInsert()
        while (toInsert.isNotEmpty()) {
            performInsertValues(toInsert, null)
            fillToInsert()
        }
    }

    private fun performRecompose(
        composition: ControlledComposition,
        modifiedValues: MutableScatterSet<Any>?,
    ): ControlledComposition? {
        if (
            composition.isComposing ||
                composition.isDisposed ||
                compositionsRemoved?.contains(composition) == true
        )
            return null

        return if (
            composing(composition, modifiedValues) {
                if (modifiedValues?.isNotEmpty() == true) {
                    // Record write performed by a previous composition as if they happened during
                    // composition.
                    composition.prepareCompose {
                        modifiedValues.forEach { composition.recordWriteOf(it) }
                    }
                }
                composition.recompose()
            }
        )
            composition
        else null
    }

    /**
     * Counts the held-back compositions a host composed without reporting them current, before
     * the record is cleared. Such a host breaks the protocol of [ParentDrivenHosting.reportCurrent]: the
     * compositions nested in it then wait for their fallback, or starve under a busy host. The
     * first one is logged. Must hold [stateLock].
     */
    private fun countUnreportedComposesLocked() {
        pendingParentDriven.forEach { composition ->
            val impl = composition as? CompositionImpl ?: return@forEach
            if (!impl.composedSinceReport || impl.isDisposed) return@forEach
            impl.composedSinceReport = false
            if (deferralProtocolViolations++ == 0) {
                logWarning(
                    "A host composed a composition the recomposer held back, without reporting " +
                        "it current. The compositions that wait for it fall back to a later frame."
                )
            }
        }
    }

    /**
     * Collects the candidates for a record that no measure pass consumed. The record assumes the
     * host's pipeline: the measure pass that follows this recomposer's recompose block reads it
     * before the next block. A composition that its own gate held back has a measure-pending
     * host, so that measure pass services the host. A candidate is such a composition, still
     * held, in a frame in which no host called in at all. [countUnconsumed] then asks each
     * candidate's gate. Must hold [stateLock].
     */
    private fun collectUnconsumedLocked() {
        unconsumedCandidates.clear()
        if (hostCallsSincePublish != 0) return
        // A host that composed a held composition without reporting it did run a measure pass.
        // countUnreportedComposesLocked counts that host instead.
        if (pendingParentDriven.any { (it as? CompositionImpl)?.composedSinceReport == true }) {
            return
        }
        heldByOwnGate.forEach {
            if (it in pendingParentDriven && !it.isDisposed) unconsumedCandidates += it
        }
    }

    /**
     * Counts a record that no measure pass consumed, and logs the first one. A candidate whose
     * gate is still true has a host that is still measure-pending, so no measure pass serviced
     * it, and only the fallback re-arm delivers. A candidate whose gate is false was measured,
     * and its host simply no longer uses it, for example a removed lazy item. The gates are host
     * code, so they run outside [stateLock].
     */
    private fun countUnconsumed() {
        if (unconsumedCandidates.isEmpty()) return
        val unconsumed =
            unconsumedCandidates.fastAny {
                (it as? CompositionImpl)?.parentDrivenRecomposeGate?.invoke() == true
            }
        unconsumedCandidates.clear()
        if (!unconsumed) return
        val first = synchronized(stateLock) { deferralPipelineViolations++ == 0 }
        if (first) {
            logWarning(
                "A frame started before the measure pass that should follow the previous frame's " +
                    "recompose. Compositions held back for that measure pass fall back to a " +
                    "later frame."
            )
        }
    }

    /** Empties the deferral record. Must hold [stateLock]. */
    private fun clearDeferralRecordLocked() {
        pendingParentDriven.clear()
        waitersByEnclosing.clear()
        heldByOwnGate.clear()
    }

    /**
     * Re-arms the invalidation of a composition that the deferral propagation deferred.
     *
     * Such a composition has no refresh of its own scheduled. Its own gate said it was ready. So
     * the pass must re-arm it. If it does not, the content stays stale until an unrelated change
     * arrives.
     *
     * The re-arm is a fallback. An enclosing composition that composes in the same frame releases
     * this composition through [deliverDeferral], and delivery removes the re-arm again. The
     * re-arm delivers only on a frame in which this composition is not delivered, for example
     * because its enclosing composition does not compose. The re-arm's standalone recompose
     * replays the content this composition already has. So for a slot that its host held only
     * because the host supplied new content, the new content lands when the host measures again,
     * not through the re-arm.
     *
     * Never call this for a composition that its own gate deferred. Its host's pending measure
     * re-runs the content. Re-arming it would ask for a new frame every frame.
     *
     * [CompositionImpl.consecutiveDeferralReArms] bounds the re-arm. It counts consecutive frames
     * in which this composition was re-armed and not delivered. A second re-arm in the same frame
     * does not change the count. Composing by either path resets the count to zero:
     * [CompositionImpl.composeContent] and [CompositionImpl.recompose] do that reset. So the cap
     * trips after 60 consecutive frames in which this composition was never delivered. That
     * happens, for example, when its enclosing composition never composes, when its refresh
     * request is swallowed because its host is mid-measure, or when its host belongs to another
     * owner whose measure pass does not follow the enclosing composition's.
     *
     * Past the cap this stops re-arming. A later change delivers only when a subscribed scope
     * reports it and the propagation does not defer this composition again in that frame.
     * Otherwise the content stays stale until the host's next measure refreshes it.
     *
     * The trip logs once, on the frame the run first passes the cap. A waiter that is never
     * delivered is a fault, and without a signal it shows only as stale content. The one logger
     * in `commonMain` is `logError`, which takes a [Throwable], so the log carries an
     * [IllegalStateException] that holds the same message.
     *
     * The ordinal counts processed frames, not wall-clock time. So an idle period does not break
     * an unbroken run. Only a processed frame that does not defer this composition breaks it.
     */
    private fun reArmDeferred(composition: ControlledComposition, frameOrdinal: Long) {
        val impl = composition as? CompositionImpl ?: return
        val firstReArmThisFrame = impl.lastDeferralFrame != frameOrdinal
        // Count only an unbroken run of frames. A frame in which the propagation did not defer
        // this composition ends the run. Noria tests the same adjacency against its epoch.
        impl.consecutiveDeferralReArms =
            when (impl.lastDeferralFrame) {
                // Wave 2 and the host's hold can both re-arm it in one frame.
                frameOrdinal -> impl.consecutiveDeferralReArms
                frameOrdinal - 1 -> impl.consecutiveDeferralReArms + 1
                else -> 1
            }
        impl.lastDeferralFrame = frameOrdinal
        if (impl.consecutiveDeferralReArms > MAX_CONSECUTIVE_DEFERRAL_RE_ARMS) {
            if (
                firstReArmThisFrame &&
                    impl.consecutiveDeferralReArms == MAX_CONSECUTIVE_DEFERRAL_RE_ARMS + 1
            ) {
                val message =
                    "A composition was held back behind an enclosing composition for " +
                        "$MAX_CONSECUTIVE_DEFERRAL_RE_ARMS consecutive frames without being " +
                        "delivered. Its re-arm stops, and its pending change waits for a later " +
                        "change or its host's next measure."
                logWarning(message)
            }
            return
        }
        impl.deferralReArmPending = true
        invalidate(composition)
    }

    /**
     * Marks [composition] as brought up to date by its host, or gone, for the deferral record, and
     * releases the compositions that wait for it.
     *
     * A released waiter with a refresh request asks its host to re-run it. The measure pass that is
     * running then re-runs it after [composition], at any node depth. A released waiter without a
     * request is recomposed directly, but only when [releaseWithoutRequest] is true. A dispose
     * passes false, because it runs inside another composition's apply. Such a waiter keeps its
     * fallback re-arm instead.
     *
     * Delivery removes the re-arm's entry from [compositionInvalidations], so a delivered
     * composition leaves no frame request behind. The entry can also carry a real invalidation,
     * because [invalidate] de-duplicates, for example from a `SideEffect` that invalidates its own
     * scope during the compose that just delivered it, or from another thread. So once the entry
     * is removed, a composition that still has invalidations is queued again. The check comes
     * after the removal, so an invalidation that arrives in between is never lost.
     * [ControlledComposition.hasInvalidations] takes the composition's own lock, and no code path
     * here may hold both locks at once, so the check runs after [stateLock] is released. The flag
     * is read without the lock first, as it is written, so the common case takes no extra lock.
     */
    private fun deliverDeferral(
        composition: ControlledComposition,
        releaseWithoutRequest: Boolean,
    ) {
        val impl = composition as? CompositionImpl
        val reArmPending = impl?.deferralReArmPending == true
        var removedReArm = false
        val released =
            synchronized(stateLock) {
                if (reArmPending && impl?.deferralReArmPending == true) {
                    impl.deferralReArmPending = false
                    compositionInvalidations -= composition
                    removedReArm = true
                }
                takeWaitersLocked(composition)
            }
        if (removedReArm && composition.hasInvalidations) invalidate(composition)
        if (released != null) releaseWaiters(released, releaseWithoutRequest)
    }

    /**
     * Takes [composition] out of the deferral record and returns the compositions that waited for
     * it, or null. Must hold [stateLock].
     */
    private fun takeWaitersLocked(
        composition: ControlledComposition
    ): MutableList<ControlledComposition>? {
        pendingParentDriven.remove(composition)
        return waitersByEnclosing.remove(composition)
    }

    /**
     * Releases [waiters], as [deliverDeferral] describes. Runs outside [stateLock], because it calls
     * into hosts and can recompose.
     */
    private fun releaseWaiters(
        waiters: List<ControlledComposition>,
        releaseWithoutRequest: Boolean,
    ) {
        for (index in waiters.indices) {
            // A waiter that failed to recompose in recovery mode leaves the recomposer in its
            // error state, and recovery recomposes everything. So the release stops there. The
            // check also covers a failure deeper in a chain, released from inside this one.
            if (errorState.value != null) return
            val waiter = waiters[index]
            if (waiter.isDisposed) continue
            val refresh = (waiter as? CompositionImpl)?.parentDrivenRefreshRequest
            when {
                refresh != null -> refresh()
                releaseWithoutRequest -> recomposeReleasedWaiter(waiter)
            }
        }
    }

    /**
     * Recomposes a released waiter that no host re-runs, then delivers it.
     *
     * It runs inside the report that the enclosing composition's host makes with
     * [ParentDrivenHosting.reportCurrent], so inside the measure pass. An error here is reported the same
     * way as one in [composeInitial]: in recovery mode it is recorded and does not escape;
     * otherwise it is rethrown, out of the host's measure. A waiter released after the error falls
     * back to its own re-arm. A waiter that is composing right now keeps its fallback re-arm.
     */
    private fun recomposeReleasedWaiter(composition: ControlledComposition) {
        if (composition.isDisposed || composition.isComposing) return
        val needsApply =
            try {
                performRecompose(composition, null)
            } catch (e: Throwable) {
                processCompositionError(e, composition, recoverable = true)
                return
            }
        if (needsApply != null) {
            try {
                performInitialMovableContentInserts(composition)
                needsApply.applyChanges()
                needsApply.applyLateChanges()
                needsApply.changesApplied()
            } catch (e: Throwable) {
                processCompositionError(e)
                return
            }
            if (domainOf(composition) == null) {
                // Ensure that any state objects created during applyChanges are seen as changed
                // if modified after this call.
                Snapshot.notifyObjectsInitialized()
            }
        }
        deliverDeferral(composition, releaseWithoutRequest = true)
    }

    @OptIn(ExperimentalComposeApi::class)
    private fun performInsertValues(
        references: List<MovableContentStateReference>,
        modifiedValues: MutableScatterSet<Any>?,
    ): List<ControlledComposition> {
        val tasks = references.fastGroupBy { it.composition }
        for ((composition, refs) in tasks) {
            runtimeCheck(!composition.isComposing)
            composing(composition, modifiedValues) {
                // Map insert movable content to movable content states that have been released
                // during `performRecompose`.
                val pairs =
                    synchronized(stateLock) {
                        refs
                            .fastMap { reference ->
                                reference to
                                    movableContentRemoved.removeLast(reference.content).also {
                                        if (it != null) {
                                            movableContentNestedStatesAvailable.usedContainer(it)
                                        }
                                    }
                            }
                            .let { pairs ->
                                // Check for any nested states
                                if (
                                    pairs.fastAny {
                                        it.second == null &&
                                            it.first.content in movableContentNestedStatesAvailable
                                    }
                                ) {
                                    // We have at least one nested state we could use, if a state
                                    // is available for the container then schedule the state to be
                                    // removed from the container when it is released.
                                    pairs.fastMap { pair ->
                                        if (pair.second == null) {
                                            val nestedContentReference =
                                                movableContentNestedStatesAvailable.removeLast(
                                                    pair.first.content
                                                )
                                            if (nestedContentReference == null) return@fastMap pair
                                            val content = nestedContentReference.content
                                            val container = nestedContentReference.container
                                            movableContentNestedExtractionsPending.add(
                                                container,
                                                content,
                                            )
                                            pair.first to content
                                        } else pair
                                    }
                                } else pairs
                            }
                    }

                // Avoid mixing creating new content with moving content as the moved content
                // may release content when it is moved as it is recomposed when move.
                val toInsert =
                    if (
                        pairs.fastAll { it.second == null } || pairs.fastAll { it.second != null }
                    ) {
                        pairs
                    } else {
                        // Return the content not moving to the awaiting list. These will come back
                        // here in the next iteration of the caller's loop and either have content
                        // to move or by still needing to create the content.
                        val toReturn =
                            pairs.fastMapNotNull { item ->
                                if (item.second == null) item.first else null
                            }
                        synchronized(stateLock) { movableContentAwaitingInsert += toReturn }

                        // Only insert the moving content this time
                        pairs.fastFilterIndexed { _, item -> item.second != null }
                    }

                // toInsert is guaranteed to be not empty as,
                // 1) refs is guaranteed to be not empty as a condition of groupBy
                // 2) pairs is guaranteed to be not empty as it is a map of refs
                // 3) toInsert is guaranteed to not be empty because the toReturn and toInsert
                //    lists have at least one item by the condition of the guard in the if
                //    expression. If one would be empty the condition is true and the filter is not
                //    performed. As both have at least one item toInsert has at least one item. If
                //    the filter is not performed the list is pairs which has at least one item.
                composition.insertMovableContent(toInsert)
            }
        }
        return tasks.keys.toList()
    }

    private fun discardUnusedMovableContentState() {
        val unusedValues =
            synchronized(stateLock) {
                if (movableContentRemoved.isNotEmpty()) {
                    val references = movableContentRemoved.values()
                    movableContentRemoved.clear()
                    movableContentNestedStatesAvailable.clear()
                    movableContentNestedExtractionsPending.clear()
                    val unusedValues =
                        references.fastMap { it to movableContentStatesAvailable[it] }
                    movableContentStatesAvailable.clear()
                    unusedValues
                } else emptyObjectList()
            }
        unusedValues.forEach { (reference, state) ->
            if (state != null) {
                reference.composition.disposeUnusedMovableContent(state)
            }
        }
    }

    private fun readObserverOf(composition: ControlledComposition): (Any) -> Boolean {
        return { value -> composition.recordReadOf(value) }
    }

    /**
     * Domain routing: with an isolating frame domain, invalidations are delivered at the domain's
     * own pin rotations (per-consumer delivery); otherwise stock global timing.
     *
     * [frameDomainApplyObserver] is held while the recomposition runner is live, and the handles
     * are re-derived by [syncFrameDomainApplyObservers] whenever [frameDomains] changes: a host
     * recomposer starts with no domains at all and gains one per scene afterwards, so deciding
     * this once at startup would leave every scene on the global path.
     */
    private var frameDomainApplyObserver: ((Set<Any>, Snapshot) -> Unit)? = null
    private val frameDomainApplyHandles = mutableListOf<Pair<SnapshotHolder, ObserverHandle>>()
    private var globalApplyHandle: ObserverHandle? = null

    private fun registerFrameDomainApplyObserver(
        observer: (Set<Any>, Snapshot) -> Unit
    ): ObserverHandle {
        synchronized(frameDomainLock) { frameDomainApplyObserver = observer }
        syncFrameDomainApplyObservers()
        return ObserverHandle {
            synchronized(frameDomainLock) { frameDomainApplyObserver = null }
            syncFrameDomainApplyObservers()
        }
    }

    private fun syncFrameDomainApplyObservers() {
        synchronized(frameDomainLock) {
            val observer = frameDomainApplyObserver
            if (observer == null) {
                frameDomainApplyHandles.fastForEach { it.second.dispose() }
                frameDomainApplyHandles.clear()
                globalApplyHandle?.dispose()
                globalApplyHandle = null
                return
            }
            val isolating = frameDomains.filter { it.isolating }
            frameDomainApplyHandles.fastForEach { entry ->
                if (!isolating.fastAny { it === entry.first }) entry.second.dispose()
            }
            frameDomainApplyHandles.retainAll { entry ->
                isolating.fastAny { it === entry.first }
            }
            isolating.fastForEach { holder ->
                if (!frameDomainApplyHandles.fastAny { it.first === holder }) {
                    frameDomainApplyHandles.add(holder to holder.registerApplyObserver(observer))
                }
            }
            // The global observer covers whatever is NOT routed through an isolating domain. With
            // at least one isolating domain it would hand that domain's changes over ahead of its
            // pin rotation, which is the early delivery per-consumer routing exists to prevent.
            if (isolating.isEmpty()) {
                if (globalApplyHandle == null) {
                    globalApplyHandle = Snapshot.registerApplyObserver(observer)
                }
            } else {
                globalApplyHandle?.dispose()
                globalApplyHandle = null
            }
        }
    }

    private fun writeObserverOf(
        composition: ControlledComposition,
        modifiedValues: MutableScatterSet<Any>?,
    ): (Any) -> Unit {
        return { value ->
            composition.recordWriteOf(value)
            modifiedValues?.add(value)
        }
    }

    private inline fun <T> composing(
        composition: ControlledComposition,
        modifiedValues: MutableScatterSet<Any>?,
        noinline block: () -> T,
    ): T {
        val holder = domainOf(composition)
        val frameSnapshot = holder?.checkedCurrent
        return when {
            // Frame isolation on: compose in a nested transaction of the cycle unit,
            // observing through the scene's context.
            frameSnapshot != null ->
                // Enter as well as transact. A scene's render path has already entered the unit by
                // the time it flushes the recompose dispatcher, but that dispatcher also runs tasks
                // from its own coroutine scope, outside any frame - and composing without a bound
                // read view is not merely slower, it is blind to every source that binds one.
                frameSnapshot.enter {
                    frameSnapshot.withTransaction {
                        holder.context.observe(
                            recordDependency = readObserverOf(composition),
                            recordChange = writeObserverOf(composition, modifiedValues),
                            block = block,
                        )
                    }
                }
            // Frame isolation off but a scene context exists: the per-pass pinning path,
            // fanning out over the scene's sources.
            holder != null ->
                holder.context.withTransaction {
                    holder.context.observe(
                        recordDependency = readObserverOf(composition),
                        recordChange = writeObserverOf(composition, modifiedValues),
                        block = block,
                    )
                }
            // No scene context: upstream-stock substrate-only behavior.
            else ->
                SnapshotDataSource.withTransaction {
                    SnapshotDataSource.observe(
                        recordDependency = readObserverOf(composition),
                        recordChange = writeObserverOf(composition, modifiedValues),
                        block = block,
                    )
                }
        }
    }

    /**
     * The frame-cycle unit to run this recomposer's pass-level work in: the single domain's unit
     * when it drives one (the overwhelmingly common case - one window, one scene), a composite
     * over all of them when it drives several, and `null` when none of them currently has one.
     *
     * A composite rather than nesting `enter`/`withTransaction` per domain, because the callers
     * are `inline` and their blocks return non-locally out of the enclosing function: one inline
     * enter+transaction preserves that, a recursive helper cannot.
     *
     * `checkedCurrent` is null once a holder is closed, so work still queued when a scene closed
     * runs on the stock path instead of failing.
     */
    private fun currentFrameUnit(): DataSource.Snapshot? {
        val domains =
            synchronized(frameDomainLock) {
                if (frameDomains.isEmpty()) return null
                frameDomains.toList()
            }
        var first: DataSource.Snapshot? = null
        var rest: MutableList<DataSource.Snapshot>? = null
        for (i in domains.indices) {
            val unit = domains[i].checkedCurrent ?: continue
            val previous = first
            if (previous == null) {
                first = unit
            } else {
                val list = rest ?: mutableListOf(previous).also { rest = it }
                list.add(unit)
            }
        }
        val all = rest
        return if (all != null) CompositeFrameUnit(all) else first
    }

    private inline fun withTransactionOrApplyNotifications(block: () -> Unit) {
        val unit = currentFrameUnit()
        if (unit != null) {
            // See composing(): the read view has to be bound, not just a transaction opened.
            unit.enter { unit.withTransaction(block) }
        } else {
            block()
            // Ensure any global changes are observed
            Snapshot.sendApplyNotifications()
        }
    }

    private inline fun withIsolationOrNotifyObjectsInitialized(block: () -> Unit) {
        val unit = currentFrameUnit()
        if (unit != null) {
            // See composing(): the read view has to be bound, not just a transaction opened.
            unit.enter { unit.withTransaction(block) }
        } else {
            block()
            // Ensure any state objects that were written during apply changes, e.g.
            // nodes with state-backed properties, get sent apply notifications to
            // invalidate anything observing the nodes. Call this method instead of
            // sendApplyNotifications to ensure that objects that were _created_ in this
            // snapshot are also considered changed after this point.
            Snapshot.notifyObjectsInitialized()
        }
    }

    /**
     * `true` if this [Recomposer] has any pending work scheduled, regardless of whether or not it
     * is currently [running][runRecomposeAndApplyChanges].
     */
    public val hasPendingWork: Boolean
        get() =
            synchronized(stateLock) {
                snapshotInvalidations.isNotEmpty() ||
                    compositionInvalidations.isNotEmpty() ||
                    concurrentCompositionsOutstanding > 0 ||
                    compositionsAwaitingApply.isNotEmpty() ||
                    hasBroadcastFrameClockAwaitersLocked ||
                    hasNextFrameEndAwaitersLocked ||
                    movableContentRemoved.isNotEmpty()
            }

    /**
     * Enables resilient mode for this [Recomposer]: an error thrown during composition is captured
     * in [RecomposerInfo.errorState] instead of being rethrown out of
     * [runRecomposeAndApplyChanges], letting the caller recover (for example via
     * [simulateHotReload]) instead of crashing. [ComposeRuntimeError]s are still rethrown, as they
     * indicate that internal runtime invariants were violated.
     */
    @InternalComposeApi
    public fun setResilientModeEnabled(value: Boolean) {
        resilientModeEnabled.set(value)
    }

    private val hasFrameWorkLocked: Boolean
        get() =
            compositionInvalidations.isNotEmpty() ||
                hasBroadcastFrameClockAwaitersLocked ||
                hasNextFrameEndAwaitersLocked ||
                movableContentRemoved.isNotEmpty()

    /**
     * Suspends until the currently pending recomposition frame is complete. Any recomposition for
     * this recomposer triggered by actions before this call begins will be complete and applied (if
     * recomposition was successful) when this call returns.
     *
     * If [runRecomposeAndApplyChanges] is not currently running the [Recomposer] is considered idle
     * and this method will not suspend.
     */
    public suspend fun awaitIdle() {
        currentState.takeWhile { it > State.Idle }.collect()
    }

    /**
     * Pause broadcasting the frame clock while recomposing. This effectively pauses animations, or
     * any other use of the [withFrameNanos], while the frame clock is paused.
     *
     * [pauseCompositionFrameClock] should be called when the recomposer is not being displayed for
     * some reason such as not being the current activity in Android, for example.
     *
     * Calls to [pauseCompositionFrameClock] are thread-safe and idempotent (calling it when the
     * frame clock is already paused is a no-op).
     */
    public fun pauseCompositionFrameClock() {
        synchronized(stateLock) { frameClockPaused = true }
    }

    /**
     * Resume broadcasting the frame clock after is has been paused. Pending calls to
     * [withFrameNanos] will start receiving frame clock broadcasts at the beginning of the frame
     * and a frame will be requested if there are pending calls to [withFrameNanos] if a frame has
     * not already been scheduled.
     *
     * Calls to [resumeCompositionFrameClock] are thread-safe and idempotent (calling it when the
     * frame clock is running is a no-op).
     */
    public fun resumeCompositionFrameClock() {
        synchronized(stateLock) {
                if (frameClockPaused) {
                    frameClockPaused = false
                    deriveStateLocked()
                } else null
            }
            ?.resume(Unit)
    }

    // Recomposer always starts with a constant compound hash
    internal override val compositeKeyHashCode: CompositeKeyHashCode
        get() = RecomposerCompoundHashKey

    internal override val collectingCallByInformation: Boolean
        get() = _hotReloadEnabled.get()

    // Collecting parameter happens at the level of a composer; starts as false
    internal override val collectingParameterInformation: Boolean
        get() = false

    internal override val collectingSourceInformation: Boolean
        get() = composeStackTraceMode == ComposeStackTraceMode.SourceInformation

    internal override val stackTraceEnabled: Boolean
        get() = composeStackTraceMode != ComposeStackTraceMode.None

    internal override fun recordInspectionTable(table: MutableSet<CompositionData>) {
        // TODO: The root recomposer might be a better place to set up inspection
        // than the current configuration with an CompositionLocal
    }

    internal override fun registerComposition(composition: ControlledComposition) {
        // Do nothing.
    }

    internal override fun unregisterComposition(composition: ControlledComposition) {
        val released =
            synchronized(stateLock) {
                removeKnownCompositionLocked(composition)
                compositionInvalidations -= composition
                compositionsAwaitingApply -= composition
                (composition as? CompositionImpl)?.deferralReArmPending = false
                takeWaitersLocked(composition)
            }
        // A dispose runs inside another composition's apply. So only waiters with a refresh
        // request are released here. The others keep their fallback re-arm.
        if (released != null) releaseWaiters(released, releaseWithoutRequest = false)
    }

    internal override fun deferToEnclosing(composition: ControlledComposition): Boolean {
        val frameOrdinal =
            synchronized(stateLock) {
                hostCallsSincePublish++
                if (pendingParentDriven.isEmpty()) return false
                if (!waitForPendingEnclosingLocked(composition)) return false
                currentDeferralFrame
            }
        // The fallback, for a frame in which the enclosing composition does not compose.
        reArmDeferred(composition, frameOrdinal)
        return true
    }

    /**
     * A host brought [composition] up to date at measure time. See [ParentDrivenHosting.reportCurrent].
     * If wave 2 held it back, it is now delivered, unless a composition enclosing it is still
     * pending. Then it waits for that one instead. It needs no fallback re-arm for that wait: the
     * host just ran it, and its own waiters keep their re-arms.
     */
    internal override fun reportCurrent(composition: ControlledComposition) {
        (composition as? CompositionImpl)?.composedSinceReport = false
        synchronized(stateLock) {
            hostCallsSincePublish++
            if (composition !in pendingParentDriven) return
            if (waitForPendingEnclosingLocked(composition)) return
        }
        deliverDeferral(composition, releaseWithoutRequest = true)
    }

    /**
     * Records [composition] as a waiter of the nearest composition enclosing it that is in
     * [pendingParentDriven], and returns whether there is one. Must hold [stateLock].
     */
    private fun waitForPendingEnclosingLocked(composition: ControlledComposition): Boolean {
        var enclosing = (composition as? CompositionImpl)?.parentComposition
        while (enclosing != null && enclosing !in pendingParentDriven) {
            enclosing = enclosing.parentComposition
        }
        if (enclosing == null) return false
        val waiters = waitersByEnclosing.getOrPut(enclosing) { mutableListOf() }
        if (composition !in waiters) waiters += composition
        return true
    }

    internal override fun invalidate(composition: ControlledComposition) {
        synchronized(stateLock) {
                if (composition !in compositionInvalidations) {
                    compositionInvalidations += composition
                    deriveStateLocked()
                } else null
            }
            ?.resume(Unit)
    }

    internal override fun invalidateScope(scope: RecomposeScopeImpl) {
        synchronized(stateLock) {
                snapshotInvalidations.add(scope)
                deriveStateLocked()
            }
            ?.resume(Unit)
    }

    internal override fun insertMovableContent(reference: MovableContentStateReference) {
        synchronized(stateLock) {
                movableContentAwaitingInsert += reference
                deriveStateLocked()
            }
            ?.resume(Unit)
    }

    internal override fun deletedMovableContent(reference: MovableContentStateReference) {
        synchronized(stateLock) {
                movableContentRemoved.add(reference.content, reference)
                if (reference.nestedReferences != null) {
                    val container = reference
                    fun recordNestedStatesOf(reference: MovableContentStateReference) {
                        reference.nestedReferences?.fastForEach { nestedReference ->
                            movableContentNestedStatesAvailable.add(
                                nestedReference.content,
                                NestedMovableContent(nestedReference, container),
                            )
                            recordNestedStatesOf(nestedReference)
                        }
                    }
                    recordNestedStatesOf(reference)
                }
                deriveStateLocked()
            }
            ?.resume(Unit)
    }

    internal override fun movableContentStateReleased(
        reference: MovableContentStateReference,
        data: MovableContentState,
        applier: Applier<*>,
    ) {
        synchronized(stateLock) {
            movableContentStatesAvailable[reference] = data
            val extractions = movableContentNestedExtractionsPending[reference]
            if (extractions.isNotEmpty()) {
                val states = data.slotStorage.extractNestedStates(applier, extractions)
                states.forEach { reference, state ->
                    movableContentStatesAvailable[reference] = state
                }
            }
        }
    }

    internal override fun reportRemovedComposition(composition: ControlledComposition) {
        synchronized(stateLock) {
            val compositionsRemoved =
                compositionsRemoved
                    ?: mutableScatterSetOf<ControlledComposition>().also {
                        compositionsRemoved = it
                    }
            compositionsRemoved.add(composition)
        }
    }

    override fun movableContentStateResolve(
        reference: MovableContentStateReference
    ): MovableContentState? =
        synchronized(stateLock) { movableContentStatesAvailable.remove(reference) }

    override val composition: Composition?
        get() = null

    /**
     * hack: the companion object is thread local in Kotlin/Native to avoid freezing
     * [_runningRecomposers] with the current memory model. As a side effect, recomposers are now
     * forced to be single threaded in Kotlin/Native targets.
     *
     * This annotation WILL BE REMOVED with the new memory model of Kotlin/Native.
     */
    @ThreadLocal
    public companion object {

        private val _runningRecomposers = MutableStateFlow(persistentSetOf<RecomposerInfoImpl>())

        private val _hotReloadEnabled = AtomicReference(false)

        /**
         * An observable [Set] of [RecomposerInfo]s for currently
         * [running][runRecomposeAndApplyChanges] [Recomposer]s. Emitted sets are immutable.
         */
        public val runningRecomposers: StateFlow<Set<RecomposerInfo>>
            get() = _runningRecomposers

        internal fun setHotReloadEnabled(value: Boolean) {
            _hotReloadEnabled.set(value)
        }

        private fun addRunning(info: RecomposerInfoImpl) {
            while (true) {
                val old = _runningRecomposers.value
                val new = old.add(info)
                if (old === new || _runningRecomposers.compareAndSet(old, new)) break
            }
        }

        private fun removeRunning(info: RecomposerInfoImpl) {
            while (true) {
                val old = _runningRecomposers.value
                val new = old.remove(info)
                if (old === new || _runningRecomposers.compareAndSet(old, new)) break
            }
        }

        internal fun saveStateAndDisposeForHotReload(): Any {
            // NOTE: when we move composition/recomposition onto multiple threads, we will want
            // to ensure that we pause recompositions before this call.
            _hotReloadEnabled.set(true)
            return _runningRecomposers.value.flatMap { it.saveStateAndDisposeForHotReload() }
        }

        internal fun loadStateAndComposeForHotReload(token: Any) {
            // NOTE: when we move composition/recomposition onto multiple threads, we will want
            // to ensure that we pause recompositions before this call.
            _hotReloadEnabled.set(true)

            _runningRecomposers.value.forEach { it.resetErrorState() }

            @Suppress("UNCHECKED_CAST") val holders = token as List<HotReloadable>
            holders.fastForEach { it.resetContent() }
            holders.fastForEach { it.recompose() }

            _runningRecomposers.value.forEach { it.retryFailedCompositions() }
        }

        @OptIn(ComposeToolingApi::class)
        internal fun invalidateGroupsWithKey(key: Int) {
            _hotReloadEnabled.set(true)
            _runningRecomposers.value.forEach {
                if (it.currentError?.isRecoverable == false) {
                    return@forEach
                }

                it.resetErrorState()

                it.invalidateGroupsWithKey(key)

                it.retryFailedCompositions()
            }
        }

        /** This is an internal API only kept for backward compatibility. */
        @OptIn(ComposeToolingApi::class)
        internal fun getCurrentErrors(): List<RecomposerErrorInfo> =
            _runningRecomposers.value.mapNotNull { it.currentError as? RecomposerErrorInfo }

        @OptIn(ComposeToolingApi::class)
        internal fun getRecomposerErrors(): List<RecomposerErrorInformation> =
            _runningRecomposers.value.mapNotNull { it.currentError }

        internal fun clearErrors() {
            _runningRecomposers.value.mapNotNull { it.resetErrorState() }
        }
    }
}

/** Sentinel used by [ProduceFrameSignal] */
private val ProduceAnotherFrame = Any()
private val FramePending = Any()

/**
 * The deferral propagation can re-arm a composition in this many consecutive frames. Past that
 * count the re-arm stops, which ends the frame requests of a waiter that is never delivered.
 *
 * The cap counts consecutive frames in which the waiter was not delivered at all. A frame in
 * which the enclosing composition composes normally delivers the waiter and resets the count, so
 * a busy enclosing host, one that is measure-pending on nearly every frame, does not reach the
 * cap on that account. The cap trips when the waiter is never delivered: for example its
 * enclosing composition never composes, its refresh request is swallowed because its host is
 * mid-measure, or its host belongs to another owner whose measure does not follow the enclosing
 * one. The invalidation is then lost. See [Recomposer.reArmDeferred]. Noria uses the same count
 * for the same class of loop. Its behavior past the cap differs, because it keeps delivering and
 * suppresses only the frame request.
 */
private const val MAX_CONSECUTIVE_DEFERRAL_RE_ARMS: Int = 60

/**
 * Multiple producer, single consumer conflated signal that tells concurrent composition when it
 * should try to produce another frame. This class is intended to be used along with a lock shared
 * between producers and consumer.
 */
private class ProduceFrameSignal {
    private var pendingFrameContinuation: Any? = null

    /**
     * Suspend until a frame is requested. After this method returns the signal is in a
     * [FramePending] state which must be acknowledged by a call to [takeFrameRequestLocked] once
     * all data that will be used to produce the frame has been claimed.
     */
    suspend fun awaitFrameRequest(lock: SynchronizedObject) {
        synchronized(lock) {
            if (pendingFrameContinuation === ProduceAnotherFrame) {
                pendingFrameContinuation = FramePending
                return
            }
        }
        suspendCancellableCoroutine<Unit> { co ->
            synchronized(lock) {
                    if (pendingFrameContinuation === ProduceAnotherFrame) {
                        pendingFrameContinuation = FramePending
                        co
                    } else {
                        pendingFrameContinuation = co
                        null
                    }
                }
                ?.resume(Unit)
        }
    }

    /**
     * Signal from the frame request consumer that the frame is beginning with data that was
     * available up until this point. (Synchronizing access to that data is up to the caller.)
     */
    fun takeFrameRequestLocked() {
        checkPrecondition(pendingFrameContinuation === FramePending) { "frame not pending" }
        pendingFrameContinuation = null
    }

    fun requestFrameLocked(): Continuation<Unit>? =
        when (val co = pendingFrameContinuation) {
            is Continuation<*> -> {
                pendingFrameContinuation = FramePending
                @Suppress("UNCHECKED_CAST")
                co as Continuation<Unit>
            }
            ProduceAnotherFrame,
            FramePending -> null
            null -> {
                pendingFrameContinuation = ProduceAnotherFrame
                null
            }
            else -> error("invalid pendingFrameContinuation $co")
        }
}

@OptIn(InternalComposeApi::class)
private class NestedContentMap {
    private val contentMap = MultiValueMap<MovableContent<Any?>, NestedMovableContent>()
    private val containerMap = MultiValueMap<MovableContentStateReference, MovableContent<Any?>>()

    fun add(content: MovableContent<Any?>, nestedContent: NestedMovableContent) {
        contentMap.add(content, nestedContent)
        containerMap.add(nestedContent.container, content)
    }

    fun clear() {
        contentMap.clear()
        containerMap.clear()
    }

    fun removeLast(key: MovableContent<Any?>) =
        contentMap.removeLast(key).also { if (contentMap.isEmpty()) containerMap.clear() }

    operator fun contains(key: MovableContent<Any?>) = key in contentMap

    fun usedContainer(reference: MovableContentStateReference) {
        containerMap.forEachValue(reference) { value ->
            contentMap.removeValueIf(value) { it.container == reference }
        }
    }
}

@InternalComposeApi
private class NestedMovableContent(
    val content: MovableContentStateReference,
    val container: MovableContentStateReference,
)

/**
 * One frame-cycle unit standing for several: binds and transacts every unit in [units], so a host
 * recomposer driving more than one scene can slice its pass-level work against all of their
 * domains through a single [DataSource.Snapshot].
 *
 * Domains are entered in order and left in reverse, matching the nesting this replaces. A failure
 * part-way through binding unwinds what it already bound: a half-bound thread would otherwise keep
 * a superseded view current for the rest of the frame.
 */
private class CompositeFrameUnit(private val units: List<DataSource.Snapshot>) :
    DataSource.Snapshot {
    override fun makeCurrent(): Any? {
        val previous = arrayOfNulls<Any?>(units.size)
        var bound = 0
        try {
            while (bound < units.size) {
                previous[bound] = units[bound].makeCurrent()
                bound++
            }
        } catch (e: Throwable) {
            for (i in bound - 1 downTo 0) units[i].restoreCurrent(previous[i])
            throw e
        }
        return previous
    }

    override fun restoreCurrent(previous: Any?) {
        @Suppress("UNCHECKED_CAST") val saved = previous as Array<Any?>
        for (i in units.indices.reversed()) units[i].restoreCurrent(saved[i])
    }

    override fun beginTransaction(): Any? {
        val frames = arrayOfNulls<Any?>(units.size)
        var opened = 0
        try {
            while (opened < units.size) {
                frames[opened] = units[opened].beginTransaction()
                opened++
            }
        } catch (e: Throwable) {
            for (i in opened - 1 downTo 0) units[i].endTransaction(frames[i], e)
            throw e
        }
        return frames
    }

    override fun endTransaction(frame: Any?, cause: Throwable?) {
        @Suppress("UNCHECKED_CAST") val frames = frame as Array<Any?>
        for (i in units.indices.reversed()) units[i].endTransaction(frames[i], cause)
    }

    /** The units belong to their holders, which rotate and dispose them. */
    override fun dispose() = Unit
}
