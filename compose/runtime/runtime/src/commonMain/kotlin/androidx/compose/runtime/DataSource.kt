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
import androidx.compose.runtime.snapshots.Snapshot
import fleet.multiplatform.shims.MultiplatformConcurrentHashMap
import fleet.multiplatform.shims.MultiplatformConcurrentHashSet
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

    companion object {
        private val registeredDataSource = AtomicReference<DataSource?>(null)

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
            check(registeredDataSource.compareAndSet(null, dataSource)) {
                "Multiple data sources aren't currently supported"
            }
            return ObserverHandle { registeredDataSource.compareAndSet(dataSource, null) }
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
            return false
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
        }

        private var currentReadTrackingIndex: ReadTrackingIndex? = null

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
            return ObserverHandle { }
        }

        /**
         * Calls [block] with observation by all registered [DataSource]s.
         */
        @OptIn(ExperimentalContracts::class)
        @JvmStatic
        @JvmName("staticObserve")
        fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)? = null,
            block: () -> T
        ): T {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            val registeredDataSource = registeredDataSource.load()
            return if (registeredDataSource != null) {
                registeredDataSource.observe(recordDependency, recordChange, block)
            } else {
                block()
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
            return Snapshot.withoutReadObservation(block)
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
            return block()
        }

        /**
         * Advances all global and thread-specific state associated with all registered
         * [DataSource]s. This will also invalidate all cacheable computations that depend on values
         * which have changed in comparison to the previous state.
         * Depending on the place where these values were read, this might lead to invalidations of
         * the composition, layout, drawing, or any other cacheable computation.
         */
        fun advanceGlobalSnapshot() {
            registeredDataSource.load()?.advanceGlobalSnapshot()?.let { identifiers ->
                val invalidateAllKeys = LinkedHashSet<Any>()
                val patternHashesByKey = LinkedHashMap<Any, MutableList<Long>>()
                identifiers.forEach { identifier ->
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
                invalidateAllKeys.forEach { key ->
                    keyToReadTrackingIndex[key]
                        ?.toList()
                        ?.forEach { readTrackingIndex ->
                            readTrackingIndex.invalidateAll("DB")
                        }
                }
                patternHashesByKey.forEach { (key, patternHashes) ->
                    if (key !in invalidateAllKeys) {
                        keyToReadTrackingIndex[key]
                            ?.toList()
                            ?.forEach { readTrackingIndex ->
                                readTrackingIndex.invalidate(patternHashes.toLongArray(), "DB")
                            }
                    }
                }
            }
            Snapshot.sendApplyNotifications()
        }

        internal val keyToReadTrackingIndex = MultiplatformConcurrentHashMap<Any, MultiplatformConcurrentHashSet<ReadTrackingIndex>>()

        internal fun registerReadTrackingIndex(key: Any, readTrackingIndex: ReadTrackingIndex) {
            keyToReadTrackingIndex.computeIfAbsent(key) { MultiplatformConcurrentHashSet() }.add(readTrackingIndex)
        }

        internal fun unregisterReadTrackingIndex(key: Any, readTrackingIndex: ReadTrackingIndex) {
            keyToReadTrackingIndex.compute(key) { _, indices ->
                if (indices == null) return@compute null
                indices.remove(readTrackingIndex)
                if (indices.isEmpty()) null else indices
            }
        }

        internal fun clearReadTrackingIndices(key: Any) {
            keyToReadTrackingIndex.remove(key)?.clear()
        }

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
 * The type returned by observer registration methods that unregisters the observer when it is
 * disposed.
 */
@Suppress("CallbackName")
fun interface ObserverHandle {
    /** Dispose the observer causing it to be unregistered from the snapshot system. */
    fun dispose()
}

private fun Set<Pair<Any, Long>>.dropKeys(): LongArray {
    return LongArray(size).apply {
        this@dropKeys.forEachIndexed { index, (_, patternHash) -> this[index] = patternHash }
    }
}
