/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.compose.runtime.internal.SnapshotThreadLocal
import androidx.compose.runtime.internal.currentThreadId
import androidx.compose.runtime.internal.currentThreadName
import androidx.compose.runtime.platform.makeSynchronizedObject
import androidx.compose.runtime.platform.synchronized
import androidx.compose.runtime.snapshots.Snapshot as ComposeSnapshot
import androidx.compose.runtime.snapshots.SnapshotDataSource
import androidx.compose.runtime.snapshots.deliverInvalidations
import androidx.compose.runtime.snapshots.substrateRootOf
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmStatic

/**
 * Reactively connects observable data sources and cacheable computations:
 *
 * Observable data sources should:
 *  - implement this interface and be handed to the scene(s) that consume them via a
 *    [DataSourceContext], which sets up and tears down state around sections with
 *    cacheable computations
 *  - call [invalidateDependants] when observed values change
 *
 * Cacheable computations should:
 *  - wrap [DataSourceContext.observe] around sections where cacheable computations happen
 *  - use [registerInvalidator] to schedule work when recorded dependencies get
 *    invalidated
 */
interface DataSource {
    /**
     * Observes reads and writes that are made during the execution of the [block].
     * [recordDependency] and [recordChange] are supposed to be called by the
     * data source implementation whenever an observable value has been read or
     * changed, respectively, in order to establish reactive links between them and
     * the computations.
     *
     * @param recordDependency Records the given `identifier` as a dependency of
     * this observation. Once a cacheable computation has a dependency on such an identifier,
     * passing that identifier to [invalidateDependants] will invalidate the compuation. Will
     * return `true` if a dependency has been recorded, `false` otherwise.
     *
     * Note: what this parameter actually is depends on which member receives it. For the
     * substrate member (`members[0]`, always the internal [SnapshotDataSource] - no external
     * implementer of this interface can ever be that member), it is the caller's own recorder,
     * captured directly when [DataSourceContext] calls this member's [observe]. For any foreign
     * member - the only kind an external implementer of this interface can be - it is instead a
     * small forwarding function to the static [DataSource.recordDependency], which re-resolves the
     * ambient recorder from a thread-local on every call rather than capturing one fixed function
     * at [observe] time; this is what lets a read made inside, say, a `derivedStateOf`
     * recalculation still reach the recorder that is ambient at read time.
     *
     * The two honour [ComposeSnapshot.withoutReadObservation] for different reasons, neither of
     * which is guidance an external implementer needs to act on. The substrate's recorder is
     * installed directly as the snapshot's own `readObserver`; [ComposeSnapshot.withoutReadObservation]
     * works by nulling [ComposeSnapshot.current]'s `readObserver`, so that recorder is simply never
     * invoked during the paused block - it does not consult anything to know it is paused. A
     * foreign member's forwarding function, by contrast, resolves to
     * [DataSource.recordDependency], which explicitly checks that same `readObserver` and returns
     * `false` while it is `null`. Both end up suppressing the read while paused, by unrelated
     * means.
     * @param recordChange Records that a value with the given `identifier` has been
     * changed during the evaluation of the [block]. This allows for prioritized
     * scheduling of cacheable computations got invalidated by the synchronous change
     * in order to ensure consistency as far as possible.
     */
    fun <T> observe(
        recordDependency: (identifier: Any) -> Boolean,
        recordChange: ((identifier: Any) -> Unit)?,
        block: () -> T
    ): T

    /*
     * ## Sources that need thread-bound state to read
     *
     * A source whose [observe] needs something bound to the calling thread - a database context, a
     * transaction handle - gets it from [Snapshot.makeCurrent], which the frame cycle calls at every
     * scene ingress and around every rotation's invalidation dispatch. Two limits apply, and both
     * produce failures in awkward places if ignored:
     *
     * 1. **Frame isolation must be enabled.** With the flag off there is no frame unit, nothing is
     *    ever bound, and such a source cannot read at all. A source that needs no bound state (one
     *    reading a plain in-memory map, say) is unaffected and works in both modes.
     *
     * 2. **Scene CONSTRUCTION is not covered, even with the flag on.** Attaching the root layout node
     *    drives semantics computation, which observes reads - and that happens before the frame domain
     *    is activated, which by invariant cannot be moved earlier. So there is a window with no unit
     *    in existence and therefore no view. A source must tolerate that: treat an absent view as
     *    "read the latest" rather than failing. This is a known wart with no good answer yet; see
     *    `docs/superpowers/specs/2026-07-28-read-scope-entry-design.md`.
     */

    /**
     * Isolates the values that are read from or written to this data source during the execution
     * of the [block].
     *
     * The implementation must ensure that any writes that happen outside of the [block] have
     * no effect on it and changes that the [block] produced also aren't visible until it has
     * returned.
     *
     * After the [block] has returned, any changes that happened during its execution must
     * be committed and made visible to its surrounding. If this can lead to conflicts, i.e.
     * due to concurrent threads making changes to the same values, then an exception may
     * be thrown.
     */
    fun <T> withTransaction(block: () -> T): T


    fun advanceGlobalSnapshot(): Set<Any>

    /**
     * Takes a root isolation unit pinned to this source's current state.
     *
     * Until [Snapshot.dispose], reads within the unit's isolation observe the pinned state
     * plus everything published through the unit itself; changes published by others stay
     * invisible.
     */
    fun takeSnapshot(): Snapshot

    /**
     * An explicit unit of isolation over one or more data sources. All work runs inside it
     * via [withTransaction], which opens a nested transaction for the block and publishes it (or
     * abandons it on failure) when the block ends. Transactions nest: an [withTransaction] called
     * while the calling thread is already inside one of this unit's transactions opens a
     * transaction nested in THAT one, whose publication folds into it silently - only
     * top-level transactions publish to the unit's surrounding and deliver invalidations.
     *
     * [beginTransaction] and [endTransaction] are the implementation surface [withTransaction] is
     * built on; call [withTransaction] instead of using them directly.
     */
    interface Snapshot {
        /**
         * SPI for [enter]: binds this unit's read view to the calling thread WITHOUT
         * opening a transaction - reads see the unit's pinned view, nothing publishes, and
         * no snapshot is taken. Returns an opaque token that must be passed to
         * [restoreCurrent] exactly once.
         *
         * This is what gives frame-end work a defined view to read through. A transaction's
         * publication dispatches its invalidations AFTER the transaction has been restored
         * off the thread (see [endTransaction]'s canonical order), so those handlers run in
         * the ENCLOSING view - which, on a platform callback thread that entered no scope,
         * is no view at all. A source whose [DataSource.observe] needs thread-bound state
         * would then have none.
         *
         * Implementations must be re-entrant - save and restore, never set and clear - and
         * must not publish. Must not leave any thread state changed when it throws.
         */
        fun makeCurrent(): Any?

        /**
         * SPI for [enter]: unbinds, restoring exactly what the [makeCurrent] that returned
         * [previous] displaced.
         *
         * Runs in a `finally`: it MUST NOT throw.
         */
        fun restoreCurrent(previous: Any?)

        /**
         * SPI for [withTransaction]: opens a nested transaction of this unit's innermost
         * transaction active on the calling thread (of the unit itself when none) and
         * makes it current. Returns an opaque frame that must be passed to [endTransaction]
         * exactly once. Must not leave any thread state changed when it throws.
         */
        fun beginTransaction(): Any?

        /**
         * SPI for [withTransaction]: ends the transaction opened by the [beginTransaction] that
         * returned [frame], in this canonical order:
         * 1. ALWAYS restore the calling thread's previous state first.
         * 2. If [cause] is `null`, publish the transaction's writes to its surrounding
         *    (resolve-or-fail: a conflicting concurrent publication discards the writes
         *    and throws, after step 3).
         * 3. ALWAYS release the transaction, abandoning unpublished writes.
         *
         * Must not throw when [cause] is non-null (the block already failed with it;
         * secondary failures must not mask it).
         */
        fun endTransaction(frame: Any?, cause: Throwable?)

        /**
         * Ends the unit: abandons pending writes and releases the pin.
         *
         * While a root unit is open, invalidations of foreign commits are delivered per
         * frame DOMAIN rather than per unit: each domain's observers hear about them at
         * that domain's own next pin rotation, when its view starts including the
         * published values; globally registered observers hear immediately, regardless
         * of any open unit.
         */
        fun dispose()
    }

    companion object {
        /**
         * Records the given identifier as a dependency of any ongoing cacheable
         * computation.
         *
         * This function is a convenience API intended to be called instead of the
         * function passed into [DataSource.observe] by data sources that do not
         * implement [DataSource].
         *
         * @return `true` if a dependency has been recorded, `false` otherwise.
         *
         * @see [DataSource.observe]
         */
        @JvmStatic
        fun recordDependency(identifier: Any): Boolean {
            // Stock Snapshot.withoutReadObservation suppresses observation by nulling the current
            // snapshot's read observer. Honour that here, so a custom source is opted out by the same
            // call the rest of Compose already uses -- 53 call sites, ~20 on measure/layout/draw.
            // This guard is deliberately broader than just withoutReadObservation: it covers ANY
            // snapshot whose readObserver is null, which also includes Snapshot.global { } (the
            // global snapshot hardcodes a null readObserver). That matches stock behaviour, where a
            // mutableStateOf read inside Snapshot.global is unobserved too.
            if (ComposeSnapshot.current.readObserver == null) return false
            return threadDependencyRecorder.get()?.invoke(identifier) ?: false
        }

        /**
         * Records a change of the value with the given identifier.
         *
         * This function is a convenience API intended to be called instead of the
         * function passed into [DataSource.observe] by data sources that do not
         * implement [DataSource].
         *
         * @see [DataSource.observe]
         */
        @JvmStatic
        fun recordChange(identifier: Any) {
            threadChangeRecorder.get()?.invoke(identifier)
        }

        /**
         * Registers an invalidator that will be called when invalidations
         * happen, e.g. via [invalidateDependants].
         * The observer will be called with the identifiers of all dependencies that
         * are invalidated.
         *
         * @return an [ObserverHandle] that can be used to unregister the observer
         */
        @JvmStatic
        fun registerInvalidator(invalidator: (identifiers: Set<Any>) -> Unit): ObserverHandle {
            val snapshotApplyObserver = ComposeSnapshot.registerApplyObserver { identifiers, _ ->
                invalidator(identifiers)
            }
            return ObserverHandle {
                snapshotApplyObserver.dispose()
            }
        }

        /**
         * Passed [block] will be run with all the currently set read observers disabled.
         */
        @OptIn(ExperimentalContracts::class)
        @JvmStatic
        fun <T> withoutReadObservation(block: @DisallowComposableCalls () -> T): T {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            val previousDependencyRecorder = threadDependencyRecorder.get()
            threadDependencyRecorder.set { false }
            try {
                return ComposeSnapshot.withoutReadObservation(block)
            } finally {
                threadDependencyRecorder.set(previousDependencyRecorder)
            }
        }
    }
}

/** Thread-local observation recorders shared by the companion hooks and context fan-outs. */
private val threadDependencyRecorder = SnapshotThreadLocal<((Any) -> Boolean)>()
private val threadChangeRecorder = SnapshotThreadLocal<((Any) -> Unit)>()

/**
 * What foreign [DataSource] members receive as their `recordDependency`, instead of the caller's raw
 * lambda. Resolving the recorder at READ time rather than at `observe()` time is what makes
 * `withoutReadObservation` able to suppress a source that captured it, and what lets a read inside a
 * `derivedStateOf` recalculation reach that derived state's own recorder. A single process-wide
 * instance: no per-call allocation.
 */
private val foreignRecordDependency: (Any) -> Boolean = { DataSource.recordDependency(it) }

/** Installs merged observation recorders around [block] (see [DataSource.recordDependency]). */
private fun <T> withObservationRecorders(
    recordDependency: (Any) -> Boolean,
    recordChange: ((Any) -> Unit)?,
    block: () -> T,
): T {
    val previousDependencyRecorder = threadDependencyRecorder.get()
    val mergedDependencyRecorder = previousDependencyRecorder?.let {
        { identifier: Any ->
            val recorded = recordDependency(identifier)
            val previouslyRecorded = it(identifier)
            recorded || previouslyRecorded
        }
    } ?: recordDependency
    threadDependencyRecorder.set(mergedDependencyRecorder)
    val previousChangeRecorder = threadChangeRecorder.get()
    val mergedChangeRecorder: ((Any) -> Unit)? = when {
        recordChange == null -> previousChangeRecorder
        previousChangeRecorder == null -> recordChange
        else -> { identifier: Any ->
            recordChange(identifier)
            previousChangeRecorder(identifier)
        }
    }
    threadChangeRecorder.set(mergedChangeRecorder)
    try {
        return block()
    } finally {
        threadDependencyRecorder.set(previousDependencyRecorder)
        threadChangeRecorder.set(previousChangeRecorder)
    }
}

/**
 * Registry-free ambient observation for calculations without context reachability
 * (derived-state calculations, snapshot-state observers): merges [recordDependency] into
 * the thread-local recorder chain - foreign sources' reads still record through
 * [DataSource.recordDependency] - and observes substrate reads directly. Foreign sources'
 * [DataSource.observe] setup hooks are NOT invoked here; those wrap context-reachable
 * sections only.
 */
internal fun <T> observeDataSourceReads(
    recordDependency: (Any) -> Boolean,
    recordChange: ((Any) -> Unit)? = null,
    block: () -> T,
): T =
    withObservationRecorders(recordDependency, recordChange) {
        ComposeSnapshot.observe(recordDependency, recordChange, block)
    }

/**
 * Context-aware ambient observation: in addition to merging [recordDependency] into the
 * thread-local recorder chain, this installs every member's own [DataSource.observe] setup
 * hook around [block]. Sources whose reads are recorded by that hook - rather than by the
 * static [DataSource.recordDependency] - therefore establish dependencies in EVERY observed
 * phase (measure, layout, draw, semantics), not only in composition.
 *
 * With a null [context] this is the registry-free path and is semantically equivalent to the
 * overload above. With a substrate-only context it is also semantically equivalent, because
 * [DataSourceContext.observe] short-circuits to that same overload - the equivalence is
 * semantic only, though: that path adds a null check on [context], a `members.size` check, and
 * one more non-inlined stack frame.
 *
 * Attribution granularity follows the substrate exactly, including its limits: a read made
 * while drawing a node that has no graphics layer of its own is attributed to the nearest
 * ancestor layer's scope rather than to that node, because only the layer-backed draw path
 * observes reads. Invalidation is therefore coarser than per-node in that case, but never
 * missed - identical to how a `mutableStateOf` read in the same position behaves.
 *
 * Documented limitation: a hook-based source's FIRST read made INSIDE a `derivedStateOf`
 * recalculation now establishes a dependency correctly, in both composition and this observer.
 * Composition's own read-recording function and this observer's scope-map read-recording function
 * both gate on a derived-state-recalculation guard of the same shape - the former driven by
 * subcomposition too, the latter by this observer's own derived-state-recalculation callback - and
 * the source's captured recorder now resolves through that guard on either path - so composition
 * is affected identically to this observer, not unaffected. This never applied to static-recorder
 * sources (those relying on [DataSource.recordDependency] alone): those go through the
 * thread-local chain regardless of where the read happens, so they always worked, on first read
 * and re-read alike - it is exactly backwards to say the hole applied to them too.
 *
 * What remains open is internal RE-READ: an equal-valued derived state recalculation is routed,
 * during invalidation processing, through a re-read path that runs outside any `context.observe`
 * call. A hook-based source's read there still establishes no dependency, so once such a
 * `derivedStateOf` recalculates to an equal value, its dependency set is silently emptied and its
 * cached result record becomes permanently valid: every later change to the source is missed, by
 * every reader, including one newly composed in a brand-new scope that has never read this
 * derived state before. Re-observing it does NOT recover correctness - the stale value keeps
 * being served indefinitely, which can render visibly wrong UI, not merely delay a refresh. Pinned
 * by the `@Ignore`d test `aHookBasedSourceDependencyIsLostAfterAnEqualRereadOutsideContext`
 * (SnapshotStateObserverTestsCommon.kt), disabled because the gap is not fixed yet; fixing it
 * needs a per-source generation hook that the re-read path can consult, separate work.
 *
 * Cheap mitigation available today: a source that calls [DataSource.recordDependency]
 * unconditionally on every read - rather than only when it has its own hook/witness installed -
 * has no gap here at all, because the re-read path does install a snapshot read observer and
 * thread-local recorder; it just never invokes a per-source [observe] setup hook. Integrators can
 * sidestep this entire class of bug by taking that shape instead of a hook-based one.
 *
 * [context] deliberately has NO default value: a default would make calls that pass only
 * `recordDependency` and `block` ambiguous between the two overloads.
 */
internal fun <T> observeDataSourceReads(
    context: DataSourceContext?,
    recordDependency: (Any) -> Boolean,
    recordChange: ((Any) -> Unit)? = null,
    block: () -> T,
): T =
    if (context != null) context.observe(recordDependency, recordChange, block)
    else observeDataSourceReads(recordDependency, recordChange, block)

/**
 * An immutable set of [DataSource]s isolated and observed together — one per scene (or,
 * later, per group of related scenes). The built-in snapshot substrate is always the first
 * member. The context is the only fan-out point: there is no global source registry.
 */
class DataSourceContext(vararg sources: DataSource) {
    internal val members: List<DataSource> =
        listOf<DataSource>(SnapshotDataSource) +
            sources.filter { it !== SnapshotDataSource }.distinct()

    companion object {
        /**
         * A shared substrate-only context for scenes constructed without explicit sources.
         * Safe to share between unrelated scenes: with no foreign members, units taken from
         * it register no source pins, so their cycles cannot couple through it.
         */
        val Empty: DataSourceContext = DataSourceContext()
    }

    private val observationWrapper = ObservationWrapper()
    private val isolationWrapper = IsolationWrapper()

    // The thread currently descending through this context's fan-out (observe's or isolate's),
    // or -1L when none is. Guards the shared-context hazard: a context with foreign members must
    // be used from a single thread at a time - e.g. ApplicationSession shares one context across
    // every window scene and the application composition domain. Sequential use from different
    // threads stays legal; only an OVERLAPPING entry from a second thread is rejected. Mirrors
    // SnapshotStateObserver's `currentMapThreadId` check in `observeReads`.
    // Shared by [observe] and [withTransaction]; must guard only their fan-out branches, never the
    // members.size == 1 fast path.
    private var fanOutThreadId = -1L

    private inline fun <T> guardingFanOut(block: () -> T): T {
        val oldThreadId = fanOutThreadId
        val threadId = currentThreadId()
        if (oldThreadId != -1L) {
            requirePrecondition(oldThreadId == threadId) {
                "Detected multithreaded access to a shared DataSourceContext: " +
                    "previousThreadId=$oldThreadId, " +
                    "currentThread={id=$threadId, name=${currentThreadName()}}. " +
                    "A DataSourceContext with foreign members must be used from a single " +
                    "thread at a time (sequential use from different threads is fine)."
            }
        }
        fanOutThreadId = threadId
        try {
            return block()
        } finally {
            fanOutThreadId = oldThreadId
        }
    }

    // --- the pending-advance signal ---

    private val advanceLock = makeSynchronizedObject()
    private var pendingAdvance = false
    private var wakes = emptyList<() -> Unit>()

    /**
     * Registers a content-free wake for this context, fired by [scheduleAdvance]. The domains
     * that drive this context register here - a scene registers its render request, a
     * scene-less domain its rotation task - so that a member's change with no accompanying
     * snapshot write still results in a pass that drains the context.
     *
     * This is `@InternalComposeApi` rather than `internal` because the scene layer that
     * consumes it lives in another module; the precedent is [pumpScenelessDomainRotations].
     *
     * @return a handle that unregisters the wake when disposed.
     */
    @InternalComposeApi
    fun registerWake(wake: () -> Unit): ObserverHandle {
        val replay =
            synchronized(advanceLock) {
                wakes = wakes + wake
                pendingAdvance
            }
        // A signal raised before this wake existed would otherwise never reach it: only a drain
        // clears the flag, and a domain constructed after the signal has no other way to learn
        // of it. Fired outside the lock, for the same reason scheduleAdvance does.
        if (replay) wake()
        return ObserverHandle { synchronized(advanceLock) { wakes = wakes - wake } }
    }

    /**
     * Signals that a member holds data it has not published yet: marks an advance due and
     * wakes every registered domain exactly once. Repeated calls before the next
     * [advanceGlobalSnapshot] coalesce into that single wake.
     *
     * May be called from whichever thread produced the data - the same contract
     * [androidx.compose.runtime.internal.SnapshotHolder.onPendingDelivery] already carries.
     * The wake itself must therefore be cheap and idempotent.
     */
    fun scheduleAdvance() {
        val toWake =
            synchronized(advanceLock) {
                if (pendingAdvance) return
                pendingAdvance = true
                wakes
            }
        toWake.forEach { it() }
    }

    /**
     * Whether a [scheduleAdvance] is still waiting to be drained. Reading it does not consume
     * it; only [advanceGlobalSnapshot] clears it. Scenes fold this into their
     * "do I need to render?" decision.
     */
    val hasPendingAdvance: Boolean
        get() = synchronized(advanceLock) { pendingAdvance }

    /**
     * Takes one isolation unit per member, composed as a single [Snapshot]. A failing
     * member's publication aborts the composite transaction and every remaining member
     * discards its writes (already-published members are not rolled back).
     */
    fun takeSnapshot(): Snapshot = Snapshot(members.map { it.takeSnapshot() })

    /**
     * Calls [block] with observation by all members of this context.
     *
     * A substrate-only context short-circuits to [observeDataSourceReads]: with
     * `members == [SnapshotDataSource]` the fan-out below reduces to exactly that, so
     * skipping it is behaviour-preserving. It matters for two reasons. It keeps the
     * no-foreign-sources path - the overwhelmingly common one, and the only shape upstream
     * ever sees - free of the wrapper indirection entirely. And it keeps the shared
     * [observationWrapper] reachable only from contexts that have foreign members, which are
     * created one per scene; [Empty], the one context that IS shared between scenes, is
     * substrate-only and therefore never touches it.
     *
     * A context with foreign members must be used from a single thread (the wrapper's
     * descent state is not concurrent-safe). Nesting on that thread is supported. An
     * overlapping entry from a different thread throws (see [guardingFanOut]).
     */
    fun <T> observe(
        recordDependency: (Any) -> Boolean,
        recordChange: ((Any) -> Unit)? = null,
        block: () -> T
    ): T {
        if (members.size == 1) {
            return observeDataSourceReads(recordDependency, recordChange, block)
        }
        return guardingFanOut {
            withObservationRecorders(recordDependency, recordChange) {
                observationWrapper.run {
                    dataSources = members
                    index = 0
                    this.recordDependency = recordDependency
                    this.recordChange = recordChange
                    this.block = block
                    @Suppress("UNCHECKED_CAST")
                    invoke() as T
                }
            }
        }
    }

    /**
     * Isolates the values that are read from or written to any member of this context
     * during the execution of the [block] (the per-pass pinning path used when frame
     * isolation is off).
     *
     * Substrate-only contexts short-circuit for the same reasons as [observe].
     */
    fun <T> withTransaction(block: () -> T): T {
        if (members.size == 1) {
            return SnapshotDataSource.withTransaction(block)
        }
        return guardingFanOut {
            isolationWrapper.run {
                dataSources = members
                index = 0
                this.block = block
                @Suppress("UNCHECKED_CAST")
                invoke() as T
            }
        }
    }

    /**
     * Advances all global and thread-specific state associated with this context's
     * members. This will also invalidate all cacheable computations that depend on values
     * which have changed in comparison to the previous state.
     * Depending on the place where these values were read, this might lead to invalidations
     * of the composition, layout, drawing, or any other cacheable computation.
     *
     * Identifiers are OPAQUE: whatever a member returns from
     * [DataSource.advanceGlobalSnapshot] is delivered verbatim to the consumers that
     * recorded it as a dependency. The runtime never inspects an identifier's shape.
     *
     * The substrate flush at the end is unconditional, while the members' own advance above is
     * not (a member may have nothing pending). A substrate-only drain can therefore perform
     * two global advances - one from [DataSource.advanceGlobalSnapshot], one from the trailing
     * [ComposeSnapshot.sendApplyNotifications] - where the pre-context code performed one.
     */
    fun advanceGlobalSnapshot() {
        synchronized(advanceLock) { pendingAdvance = false }
        val unioned = LinkedHashSet<Any>()
        members.forEach { unioned.addAll(it.advanceGlobalSnapshot()) }
        if (unioned.isNotEmpty()) {
            deliverInvalidations(unioned, ComposeSnapshot.current)
        }
        // A member's own advance may itself write snapshot state (e.g. resetting a buffer
        // held in a mutableStateOf), so flush the substrate once more afterwards.
        ComposeSnapshot.sendApplyNotifications()
    }

    /** The composite frame-cycle unit over this context's members. */
    class Snapshot internal constructor(
        private val children: List<DataSource.Snapshot>,
    ) : DataSource.Snapshot {
        /** Identifies the frame cycle this unit belongs to, for own-cycle attribution. */
        internal val substrateRoot: ComposeSnapshot? = substrateRootOf(children.first())

        override fun makeCurrent(): Any? {
            val children = children
            // Substrate-only is the overwhelmingly common shape and this runs at every scene
            // ingress, so it must not allocate: hand the single token straight back.
            if (children.size == 1) return children[0].makeCurrent()
            val tokens = arrayOfNulls<Any?>(children.size)
            var bound = 0
            try {
                while (bound < children.size) {
                    tokens[bound] = children[bound].makeCurrent()
                    bound++
                }
            } catch (e: Throwable) {
                // A child failed to bind: unbind the already-bound ones (in reverse) so the
                // thread is left exactly as it was, and rethrow.
                for (index in bound - 1 downTo 0) {
                    try {
                        children[index].restoreCurrent(tokens[index])
                    } catch (secondary: Throwable) {
                        e.addSuppressed(secondary)
                    }
                }
                throw e
            }
            return tokens
        }

        override fun restoreCurrent(previous: Any?) {
            val children = children
            if (children.size == 1) return children[0].restoreCurrent(previous)
            @Suppress("UNCHECKED_CAST")
            val tokens = previous as Array<Any?>
            // Bound first-to-last; unbind last-to-first, mirroring endTransaction.
            for (index in children.indices.reversed()) {
                children[index].restoreCurrent(tokens[index])
            }
        }

        override fun beginTransaction(): Any? {
            val frames = ArrayList<Any?>(children.size)
            try {
                children.forEach { frames.add(it.beginTransaction()) }
            } catch (e: Throwable) {
                // A child failed to open: unwind the already-opened ones (in reverse, with
                // the failure as the cause so nothing publishes) and rethrow.
                for (index in frames.indices.reversed()) {
                    try {
                        children[index].endTransaction(frames[index], e)
                    } catch (secondary: Throwable) {
                        e.addSuppressed(secondary)
                    }
                }
                throw e
            }
            return frames
        }

        override fun endTransaction(frame: Any?, cause: Throwable?) {
            @Suppress("UNCHECKED_CAST")
            val frames = frame as List<Any?>
            // Children began first-to-last; end last-to-first. The first failure (e.g. a
            // publish conflict) becomes the cause for every remaining child, so their
            // writes are discarded rather than left pending, and is rethrown once all
            // have ended.
            var failure: Throwable? = cause
            for (index in children.indices.reversed()) {
                try {
                    children[index].endTransaction(frames[index], failure)
                } catch (e: Throwable) {
                    if (failure == null) failure = e else failure.addSuppressed(e)
                }
            }
            if (cause == null && failure != null) throw failure
        }

        override fun dispose() {
            children.forEach { it.dispose() }
        }
    }
}

/**
 * Invalidates all cacheable computations which depend on any of the given [identifiers]
 * of THIS source. Dependencies are set up by calling the `recordDependency` function
 * passed into [DataSource.observe].
 *
 * Delivery: global observers and the committing cycle's own domain hear immediately;
 * every other open frame domain hears at its own next pin rotation, when its view starts
 * including the published values. Domains receive tokens regardless of context
 * membership - membership scopes what a frame view PINS, never who gets invalidated.
 */
fun DataSource.invalidateDependants(identifiers: Set<Any>) {
    if (identifiers.isEmpty()) return
    deliverInvalidations(identifiers, ComposeSnapshot.current)
}

/**
 * Runs [block] in a nested transaction of this unit and publishes it (or abandons it when
 * [block] throws) on return.
 *
 * Transactions nest by thread: called while the calling thread is already inside one of
 * this unit's transactions, the new transaction nests in that one - its writes are visible
 * to the rest of the enclosing transaction after the block, its publication folds silently
 * into it (the enclosing boundary owns the delivery), and a failure discards only the
 * nested writes. Called outside, the transaction is top-level: its publication commits to
 * the unit's surrounding and delivers the invalidations.
 *
 * This facade owns the begin/end pairing (guaranteed by inlining even across non-local
 * returns from [block]); implementations own the lifecycle order inside
 * [DataSource.Snapshot.endTransaction].
 */
inline fun <T> DataSource.Snapshot.withTransaction(block: () -> T): T {
    val frame = beginTransaction()
    var cause: Throwable? = null
    try {
        return block()
    } catch (e: Throwable) {
        cause = e
        throw e
    } finally {
        endTransaction(frame, cause)
    }
}

/**
 * Runs [block] with this unit's read view bound to the calling thread. Reads see the unit's
 * pinned view; nothing is published, no transaction is opened and no snapshot is taken.
 *
 * Writes are rejected: the frame view is read-only, so all mutation must go through
 * [withTransaction], which is where a publication conflict has somewhere to surface. An unwrapped
 * write therefore fails fast instead of silently reaching the world unconflicted.
 *
 * Nests freely - the underlying bind is a save/restore pair, so an [enter] inside an [enter]
 * (or inside an [withTransaction]) composes without special handling.
 *
 * This facade owns the bind/unbind pairing, guaranteed by inlining even across non-local
 * returns from [block].
 */
inline fun <T> DataSource.Snapshot.enter(block: () -> T): T {
    val previous = makeCurrent()
    try {
        return block()
    } finally {
        restoreCurrent(previous)
    }
}

/**
 * The type returned by observer registration methods that unregisters the observer when it is
 * disposed.
 */
@Suppress("CallbackName")
fun interface ObserverHandle {
    /** Dispose the observer causing it to be unregistered from the snapshot system. */
    fun dispose()
}

@Suppress("IMPLEMENTING_FUNCTION_INTERFACE")
private class ObservationWrapper : Function0<Any?> {
    lateinit var dataSources: List<DataSource>
    var index = 0
    lateinit var recordDependency: (Any) -> Boolean
    var recordChange: ((Any) -> Unit)? = null
    lateinit var block: () -> Any?

    override fun invoke(): Any? {
        val i = index
        return if (i < dataSources.size) {
            index = i + 1
            // The substrate keeps the caller's recorder: it installs it as the snapshot's read
            // observer, so every mutableStateOf read in the process goes through it and must not pay
            // for an extra hop. Only foreign members need -- and pay for -- the indirection.
            val recorder = if (i == 0) recordDependency else foreignRecordDependency
            dataSources[i].observe(recorder, recordChange, this)
        } else {
            block()
        }
    }
}

@Suppress("IMPLEMENTING_FUNCTION_INTERFACE")
private class IsolationWrapper : Function0<Any?> {
    lateinit var dataSources: List<DataSource>
    var index = 0
    lateinit var block: () -> Any?

    override fun invoke(): Any? {
        return if (index < dataSources.size) {
            dataSources[index++].withTransaction(this)
        } else {
            block()
        }
    }
}
