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

@file:OptIn(ExperimentalAtomicApi::class)

package androidx.compose.runtime

import androidx.compose.runtime.internal.ReadTrackingIndex
import androidx.compose.runtime.internal.SnapshotThreadLocal
import androidx.compose.runtime.platform.makeSynchronizedObject
import androidx.compose.runtime.platform.synchronized
import androidx.compose.runtime.snapshots.Snapshot as ComposeSnapshot
import androidx.compose.runtime.snapshots.dispatchOrParkApplyNotifications
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Reactively connects observable data sources and cacheable computations:
 *
 * Observable data sources should:
 *  - implement this interface and [register] themselves to be able to set up and
 *    tear down state around sections with cacheable computations
 *  - call [invalidateDependants] when observed values change
 *
 * Cacheable computations should:
 *  - wrap [observe] around sections where cacheable computations happen
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
     * this observation, except if observation has been paused via [withoutReadObservation].
     * Once a cacheable computation has a dependency on such an identifier, passing that
     * identifier to [invalidateDependants] will invalidate the compuation. Will return
     * `true` if a dependency has been recorded, `false` otherwise
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
    fun <T> isolate(block: () -> T): T


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
     * via [isolate], which opens a nested transaction for the block and publishes it (or
     * abandons it on failure) when the block ends. Transactions nest: an [isolate] called
     * while the calling thread is already inside one of this unit's transactions opens a
     * transaction nested in THAT one, whose publication folds into it silently - only
     * top-level transactions publish to the unit's surrounding and deliver invalidations.
     *
     * [beginIsolation] and [endIsolation] are the implementation surface [isolate] is
     * built on; call [isolate] instead of using them directly.
     */
    interface Snapshot {
        /**
         * SPI for [isolate]: opens a nested transaction of this unit's innermost
         * transaction active on the calling thread (of the unit itself when none) and
         * makes it current. Returns an opaque frame that must be passed to [endIsolation]
         * exactly once. Must not leave any thread state changed when it throws.
         */
        fun beginIsolation(): Any?

        /**
         * SPI for [isolate]: ends the transaction opened by the [beginIsolation] that
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
        fun endIsolation(frame: Any?, cause: Throwable?)

        /**
         * Ends the unit: abandons pending writes and releases the pin.
         *
         * Invalidations for changes published by others while a root unit is open are
         * PARKED by the runtime rather than dispatched (a pinned view would consume them
         * while still hiding the values). Disposing the unit rotates its pin and releases
         * every parked batch no other open unit's pin still predates, so dependents are
         * invalidated exactly when the values become visible to them.
         */
        fun dispose()
    }

    companion object {
        private val registeredDataSources = AtomicReference(emptyList<DataSource>())
        private val threadDependencyRecorder = SnapshotThreadLocal<((Any) -> Boolean)>()
        private val threadChangeRecorder = SnapshotThreadLocal<((Any) -> Unit)>()

        /**
         * Registers a [dataSource] so that it will be invoked when [observe] blocks
         * are entered.
         *
         * @return an [ObserverHandle] that can be used to unregister the observer
         *
         * @see [observe]
         * @see [DataSource.observe]
         */
        @JvmStatic
        fun register(dataSource: DataSource): ObserverHandle {
            registeredDataSources.getAndUpdate { it + dataSource }
            return ObserverHandle {
                registeredDataSources.getAndUpdate { it - dataSource }
            }
        }

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

        private var currentReadTrackingIndex: ReadTrackingIndex? = null

        /**
         * Invalidates all cacheable computations which depend on any of the given
         * [identifiers]. Dependencies are set up by calling the `recordDependency`
         * function passed into [DataSource.observe].
         *
         * While a frame-isolation unit is open, the invalidations are parked and released
         * at the next pin rotation, when the changed values become visible to observers.
         */
        @JvmStatic
        fun invalidateDependants(identifiers: Set<Any>) {
            dispatchOrParkApplyNotifications(identifiers, ComposeSnapshot.current)
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
         * Calls [block] with observation by all registered [DataSource]s.
         */
        @JvmStatic
        @JvmName("staticObserve")
        fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)? = null,
            block: () -> T
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
                return DataSourceObservationWrapper.run {
                    dataSources = registeredDataSources.load()
                    index = 0
                    this.recordDependency = recordDependency
                    this.recordChange = recordChange
                    this.block = block
                    @Suppress("UNCHECKED_CAST")
                    invoke() as T
                }
            } finally {
                threadDependencyRecorder.set(previousDependencyRecorder)
                threadChangeRecorder.set(previousChangeRecorder)
            }
        }

        /**
         * Passed [block] will be run with all the currently set read observers disabled.
         */
        @OptIn(ExperimentalContracts::class)
        @JvmStatic
        @JvmName("staticIsolate")
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

        /**
         * Isolates the values that are read from or written to any of the registered
         * [DataSource]s during the execution of the [block].
         *
         * Any value changes that happen concurrently to the [block] are not visible within it
         * and changes that the [block] makes also aren't visible outside of it until it has
         * returned.
         *
         * After the [block] has returned, any changes that happened during its execution are
         * committed and made visible to its surrounding. This may lead to conflicts, i.e. due
         * to concurrent threads making changes to the same values, and an exception may be
         * thrown.
         */
        fun <T> isolate(block: () -> T): T {
            return DataSourceIsolationWrapper.run {
                dataSources = registeredDataSources.load()
                index = 0
                this.block = block
                @Suppress("UNCHECKED_CAST")
                invoke() as T
            }
        }

        /**
         * Takes one isolation unit per registered [DataSource], composed as a single
         * [DataSource.Snapshot]. [isolate] fans out over the children; a failing child's
         * publication aborts the composite transaction and every remaining child discards
         * its writes (already-published children are not rolled back).
         */
        fun takeSnapshot(): Snapshot {
            // Ensure the snapshot runtime (and with it the built-in snapshot data
            // source, registered by its initializer) is loaded before consulting the
            // registry, so a unit taken before any other snapshot use isolates properly.
            ComposeSnapshot.current
            val children = registeredDataSources.load().map { it.takeSnapshot() }
            return children.singleOrNull() ?: CompositeDataSourceSnapshot(children)
        }

        /**
         * Advances all global and thread-specific state associated with all registered
         * [DataSource]s. This will also invalidate all cacheable computations that depend on values
         * which have changed in comparison to the previous state.
         * Depending on the place where these values were read, this might lead to invalidations of
         * the composition, layout, drawing, or any other cacheable computation.
         */
        fun advanceGlobalSnapshot() {
            val sources = registeredDataSources.load()
            if (sources.isNotEmpty()) {
                val unioned = LinkedHashSet<Any>()
                val invalidateAllKeys = LinkedHashSet<Any>()
                val patternHashesByKey = LinkedHashMap<Any, MutableList<Long>>()
                sources.forEach { source ->
                    val changed = source.advanceGlobalSnapshot()
                    unioned.addAll(changed)
                    changed.forEach { identifier ->
                        when (identifier) {
                            is Pair<*, *> -> {
                                val key = identifier.first ?: return@forEach
                                when (val payload = identifier.second) {
                                    null -> invalidateAllKeys.add(key)
                                    is Long -> patternHashesByKey.getOrPut(key) { mutableListOf() }.add(payload)
                                    else -> error("Unexpected data source identifier payload: $identifier")
                                }
                            }
                            else -> error("Unexpected data source identifier: $identifier")
                        }
                    }
                }
                invalidateAllKeys.forEach { key ->
                    snapshotIndicesFor(key)?.forEach { readTrackingIndex ->
                        readTrackingIndex.invalidateAll("DB")
                    }
                }
                patternHashesByKey.forEach { (key, patternHashes) ->
                    if (key !in invalidateAllKeys) {
                        snapshotIndicesFor(key)?.forEach { readTrackingIndex ->
                            readTrackingIndex.invalidate(patternHashes.toLongArray(), "DB")
                        }
                    }
                }
                if (unioned.isNotEmpty()) {
                    dispatchOrParkApplyNotifications(unioned, ComposeSnapshot.current)
                }
            }
            ComposeSnapshot.sendApplyNotifications()
        }

        private val readTrackingIndicesLock = makeSynchronizedObject()
        internal val keyToReadTrackingIndex = HashMap<Any, MutableSet<ReadTrackingIndex>>()

        internal fun registerReadTrackingIndex(key: Any, readTrackingIndex: ReadTrackingIndex) {
            synchronized(readTrackingIndicesLock) {
                keyToReadTrackingIndex.getOrPut(key) { mutableSetOf() }.add(readTrackingIndex)
            }
        }

        internal fun unregisterReadTrackingIndex(key: Any, readTrackingIndex: ReadTrackingIndex) {
            synchronized(readTrackingIndicesLock) {
                val indices = keyToReadTrackingIndex[key] ?: return
                indices.remove(readTrackingIndex)
                if (indices.isEmpty()) keyToReadTrackingIndex.remove(key)
            }
        }

        internal fun clearReadTrackingIndices(key: Any) {
            synchronized(readTrackingIndicesLock) {
                keyToReadTrackingIndex.remove(key)?.clear()
            }
        }

        private fun snapshotIndicesFor(key: Any): List<ReadTrackingIndex>? =
            synchronized(readTrackingIndicesLock) { keyToReadTrackingIndex[key]?.toList() }

        /**
         * Clears all recorded observations associated with [sourceKey].
         *
         * Integrations can use this when the lifetime of a concrete data source instance ends
         * before the observing computations have had a chance to tear themselves down normally.
         * This prevents the runtime from retaining invalidation callbacks for an already disposed
         * source.
         */
        @JvmStatic
        fun clearObservations(sourceKey: Any) {
            clearReadTrackingIndices(sourceKey)
        }

        internal inline fun withReadTrackingIndex(
            readTrackingIndex: ReadTrackingIndex,
            block: () -> Unit
        ) {
            val previousReadTrackingIndex = currentReadTrackingIndex
            currentReadTrackingIndex = readTrackingIndex
            block()
            currentReadTrackingIndex = previousReadTrackingIndex
        }
    }
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
 * [DataSource.Snapshot.endIsolation].
 */
inline fun <T> DataSource.Snapshot.isolate(block: () -> T): T {
    val frame = beginIsolation()
    var cause: Throwable? = null
    try {
        return block()
    } catch (e: Throwable) {
        cause = e
        throw e
    } finally {
        endIsolation(frame, cause)
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
private object DataSourceObservationWrapper : Function0<Any?> {
    lateinit var dataSources: List<DataSource>
    var index = 0
    lateinit var recordDependency: (Any) -> Boolean
    var recordChange: ((Any) -> Unit)? = null
    lateinit var block: () -> Any?

    override fun invoke(): Any? {
        return if (index < dataSources.size) {
            dataSources[index++].observe(recordDependency, recordChange, this)
        } else {
            block()
        }
    }
}

private class CompositeDataSourceSnapshot(
    private val children: List<DataSource.Snapshot>
) : DataSource.Snapshot {
    override fun beginIsolation(): Any? {
        val frames = ArrayList<Any?>(children.size)
        try {
            children.forEach { frames.add(it.beginIsolation()) }
        } catch (e: Throwable) {
            // A child failed to open: unwind the already-opened ones (in reverse, with the
            // failure as the cause so nothing publishes) and rethrow.
            for (index in frames.indices.reversed()) {
                try {
                    children[index].endIsolation(frames[index], e)
                } catch (secondary: Throwable) {
                    e.addSuppressed(secondary)
                }
            }
            throw e
        }
        return frames
    }

    override fun endIsolation(frame: Any?, cause: Throwable?) {
        @Suppress("UNCHECKED_CAST")
        val frames = frame as List<Any?>
        // Children began first-to-last; end last-to-first. The first failure (e.g. a
        // publish conflict) becomes the cause for every remaining child, so their writes
        // are discarded rather than left pending, and is rethrown once all have ended.
        var failure: Throwable? = cause
        for (index in children.indices.reversed()) {
            try {
                children[index].endIsolation(frames[index], failure)
            } catch (e: Throwable) {
                if (failure == null) failure = e else failure.addSuppressed(e)
            }
        }
        if (cause == null && failure != null) throw failure
    }

    override fun dispose() = children.forEach { it.dispose() }
}

@Suppress("IMPLEMENTING_FUNCTION_INTERFACE")
private object DataSourceIsolationWrapper : Function0<Any?> {
    lateinit var dataSources: List<DataSource>
    var index = 0
    lateinit var block: () -> Any?

    override fun invoke(): Any? {
        return if (index < dataSources.size) {
            dataSources[index++].isolate(this)
        } else {
            block()
        }
    }
}

private inline fun <T> AtomicReference<T>.getAndUpdate(
    updater: (T) -> T
): T {
    var previousValue: T
    var successfullySet: Boolean
    do {
        previousValue = load()
        val nextValue = updater(previousValue)
        successfullySet = compareAndSet(previousValue, nextValue)
    } while (!successfullySet)
    return previousValue
}
