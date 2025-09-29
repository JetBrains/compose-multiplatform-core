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

import androidx.compose.runtime.DataSource.Companion.invalidateDependants
import androidx.compose.runtime.internal.AtomicReference
import androidx.compose.runtime.internal.SnapshotThreadLocal
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.applyObservers
import androidx.compose.runtime.snapshots.sync
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

private val registeredDataSources = AtomicReference(emptyList<DataSource>())
private val threadDependencyRecorder = SnapshotThreadLocal<((Any) -> Boolean)>()
private val threadChangeRecorder = SnapshotThreadLocal<((Any) -> Unit)>()

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

    companion object {
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

        /**
         * Invalidates all cacheable computations which depend on any of the given
         * [identifiers]. Dependencies are set up by calling the `recordDependency`
         * function passed into [DataSource.observe].
         */
        @JvmStatic
        fun invalidateDependants(identifiers: Set<Any>) {
            // TODO[unterhofer] Flip this so that observers are managed here
            sync { applyObservers }.forEach { it(identifiers, Snapshot.current) }
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
            // TODO[unterhofer] Flip this so that observers are managed here
            val snapshotApplyObserver = Snapshot.registerApplyObserver { identifiers, _ ->
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
                { identifier: Any -> recordDependency(identifier) || it(identifier) }
            } ?: recordDependency
            threadDependencyRecorder.set(mergedDependencyRecorder)
            val previousChangeRecorder = threadChangeRecorder.get()
            val mergedChangeRecorder = when {
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
                    dataSources = registeredDataSources.get()
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
        @JvmStatic
        @JvmName("staticIsolate")
        fun <T> withoutReadObservation(block: @DisallowComposableCalls () -> T): T {
            val previousDependencyRecorder = threadDependencyRecorder.get()
            threadDependencyRecorder.set { false }
            try {
                return Snapshot.withoutReadObservation(block)
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
                dataSources = registeredDataSources.get()
                index = 0
                this.block = block
                @Suppress("UNCHECKED_CAST")
                invoke() as T
            }
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

private fun <T> AtomicReference<T>.getAndUpdate(
    updater: (T) -> T
): T {
    var previousValue: T
    var successfullySet: Boolean
    do {
        previousValue = get()
        val nextValue = updater(previousValue)
        successfullySet = compareAndSet(previousValue, nextValue)
    } while (!successfullySet)
    return previousValue
}