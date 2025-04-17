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

package androidx.compose.runtime

import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
import androidx.collection.mutableObjectListOf
import androidx.collection.objectListOf
import androidx.compose.runtime.collection.removeLast
import androidx.compose.runtime.collection.toMutableObjectList
import androidx.compose.runtime.internal.AtomicReference
import androidx.compose.runtime.internal.MainThreadId
import androidx.compose.runtime.internal.currentThreadId
import androidx.compose.runtime.platform.makeSynchronizedObject
import androidx.compose.runtime.platform.synchronized
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.applyObservers
import androidx.compose.runtime.snapshots.sync
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmStatic

private val registeredDataSourcesReference =
    AtomicReference(objectListOf(Snapshot.dataSource))

@PublishedApi
internal val registeredDataSources: ObjectList<DataSource<*, *>>
    get() = registeredDataSourcesReference.get()

/**
 * DataSource allows Compose to observe when data with an observer API changes allowing Compose to
 * automatically respond and update. An example of such an API is [mutableStateOf] and the
 * corresponding [Snapshot] API which implements this interface allowing Compose to react
 * automatically to changes made to snapshot state. This interface allows integrating other data
 * sources similar to Snapshots and allows them to be used directly and natively in Compose without
 * needing any other integration.
 *
 * Observable data sources should:
 * - implement this interface and register it with [DataSource.register] to be able to set up and
 *   tear down observation, isolation, and invalidation state around sections with cacheable
 *   computations
 * - call [invalidateDependants] when observed values change
 *
 * Cacheable computations should:
 * - wrap [DataSource.observe] around sections where cacheable computations are made
 * - wrap [DataSource.withoutReadObservation] around sections that are already wrapped by [observe]
 *   but where you want to opt out of automatic dependency recording and updating
 * - wrap [DataSource.isolate] around sections where computations should not be affected by
 *   concurrent changes and/or where it should be possible to roll back any synchronous changes made
 *   by them in the case of a failure
 * - use [DataSource.registerInvalidator] to schedule work when recorded dependencies get
 *   invalidated
 *
 * @sample androidx.compose.runtime.samples.KeyValueDataSourceSample
 */
interface DataSource<T, U> {
    /**
     * Starts the observation of reads and writes. Observation will continue until [endObservation]
     * is called. [recordDependency] should be called by the data source implementation whenever an
     * observable value has been read in order to establish a dependency between the value and the
     * computations.
     *
     * @param recordDependency Records the given `identifier` as a dependency of this observation,
     *   except if observation has been paused via [withoutReadObservation]. Once a cacheable
     *   computation has a dependency on such an identifier, passing that identifier to
     *   [invalidateDependants] will invalidate the computation. Will return `true` if a dependency
     *   has been recorded, `false` otherwise
     */
    fun startObservation(recordDependency: (identifier: Any) -> Boolean): T

    /**
     * Ends the latest observation of reads and writes that was started with [startObservation].
     *
     * @param previousObservation The previously active observation that should be restored, if any.
     */
    fun endObservation(previousObservation: T)

    /**
     * Pauses all currently active read observations that were previously started with
     * [startObservation] and not yet ended with [endObservation]. Read observations must be resumed
     * with [resumeCurrentObservation] before [endObservation] is called.
     */
    fun pauseCurrentObservation(): T

    /**
     * Resumes all currently active read observations that were previously started with
     * [startObservation], not yet ended with [endObservation], and pausd with
     * [pauseCurrentObservation].
     */
    fun resumeCurrentObservation(pausedObservation: T)

    /**
     * Isolates the values that are read from or written to this data source during the subsequent
     * computation, until [endIsolation] is called.
     *
     * The implementation must ensure that any writes that happen outside of the
     * [startIsolation]/[endIsolation] pair have no effect on its inner behavior, and changes that
     * the delimited section produced also aren't visible until it has been exited.
     *
     * @param recordChange Records that a value with the given `identifier` has been changed during
     *   the computation that is wrapped by the [startIsolation]/[endIsolation] pair. If the data
     *   source supports *mutable* snapshot isolation, then it should call [recordChange] to add the
     *   given identifier to a set of changes that will only be made visible outside of the isolated
     *   computation by [endIsolation] (unless it is called with a non-null [Throwable]).
     */
    fun startIsolation(recordChange: ((identifier: Any) -> Unit)?): U

    /**
     * Ends the latest isolation that was started with [startIsolation]. All synchronous changes
     * that were recorded since the isolation was entered may now be made visible towards the
     * outside.
     *
     * The implementation must tear down the innermost layer of isolation and decide if it should
     * apply and publish any recorded changes towards the outside. If [throwable] is non-null, that
     * means that the given `Throwable` was caught during the computation. In that case, it is
     * recommended to *not* make the changes visible. Instead, they should be discarded and the
     * surrounding application state should be reverted or put into a failure mode if recovery is
     * impossible.
     *
     * @param throwable The throwable that was caught during the computation, if any. If it is
     *   non-null, no changes from the isolation should be made visible outside of it.
     * @param previousIsolation The previously active isolation that should be restored, if any.
     */
    fun endIsolation(throwable: Throwable?, previousIsolation: U)

    companion object {

        /**
         * Registers the [dataSource] so that it will be notified when [observe] or [isolate] blocks
         * are evaluated. This will affect all compositions and all stages of the UI pipeline,
         * including but not limited to composition, layout, and drawing.
         *
         * @sample androidx.compose.runtime.samples.KeyValueDataSourceSample
         * @see [observe]
         * @see [withoutReadObservation]
         * @see [isolate]
         */
        fun <T, U> register(dataSource: DataSource<T, U>): ObserverHandle {
            requirePrecondition(dataSource !in registeredDataSources) {
                "DataSource is already registered: $dataSource"
            }
            var addingSucceeded = false
            while (!addingSucceeded) {
                val currentDataSources = registeredDataSources
                addingSucceeded =
                    registeredDataSourcesReference.compareAndSet(
                        currentDataSources,
                        currentDataSources.toMutableObjectList().apply { add(dataSource) },
                    )
            }
            return ObserverHandle {
                var removingSucceeded = false
                while (!removingSucceeded) {
                    val currentDataSources = registeredDataSources
                    removingSucceeded =
                        registeredDataSourcesReference.compareAndSet(
                            currentDataSources,
                            currentDataSources.toMutableObjectList().apply { remove(dataSource) },
                        )
                }
            }
        }

        /** Calls [block] with observation by all registered [DataSource]s. */
        @Suppress("BanInlineOptIn")
        @OptIn(ExperimentalContracts::class)
        inline fun <T> observe(noinline recordDependency: (Any) -> Boolean, block: () -> T): T {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            val dataSources = registeredDataSources

            val previousObservations =
                MutableTokenListPool.acquire().apply { ensureCapacity(dataSources.size) }
            dataSources.forEach { dataSource ->
                previousObservations.add(dataSource.startObservation(recordDependency))
            }
            try {
                return block()
            } finally {
                previousObservations.forEachReversedIndexed { index, previousObservation ->
                    @Suppress("UNCHECKED_CAST")
                    val dataSource = (dataSources[index] as DataSource<Any?, *>)
                    dataSource.endObservation(previousObservation)
                }
                MutableTokenListPool.release(previousObservations)
            }
        }

        /** Passed [block] will be run with all the currently set read observers disabled. */
        @Suppress("BanInlineOptIn")
        @OptIn(ExperimentalContracts::class)
        inline fun <T> withoutReadObservation(block: @DisallowComposableCalls () -> T): T {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            val dataSources = registeredDataSources

            val previousObservations =
                MutableTokenListPool.acquire().apply { ensureCapacity(dataSources.size) }
            dataSources.forEach { dataSource ->
                previousObservations.add(dataSource.pauseCurrentObservation())
            }
            try {
                return block()
            } finally {
                previousObservations.forEachReversedIndexed { index, previousObservation ->
                    @Suppress("UNCHECKED_CAST")
                    val dataSource = (dataSources[index] as DataSource<Any?, *>)
                    dataSource.resumeCurrentObservation(previousObservation)
                }
                MutableTokenListPool.release(previousObservations)
            }
        }

        /**
         * Isolates the values that are read from or written to any of the registered [DataSource]s
         * during the execution of the [block].
         *
         * Any value changes that happen concurrently to the [block] are not visible within it and
         * changes that the [block] makes also aren't visible outside of it until it has returned.
         *
         * After the [block] has returned, any changes that happened during its execution are
         * committed and made visible to its surrounding. This may lead to conflicts, i.e. due to
         * concurrent threads making changes to the same values, and an exception may be thrown.
         */
        @Suppress("BanInlineOptIn")
        @OptIn(ExperimentalContracts::class)
        inline fun <T> isolate(noinline recordChange: ((Any) -> Unit)? = null, block: () -> T): T {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            val dataSources = registeredDataSources

            val previousIsolations: MutableObjectList<Any?> =
                MutableTokenListPool.acquire().apply { ensureCapacity(dataSources.size) }
            dataSources.forEach { dataSource ->
                previousIsolations.add(dataSource.startIsolation(recordChange))
            }
            try {
                val result = block()
                previousIsolations.forEachReversedIndexed { index, previousIsolation ->
                    @Suppress("UNCHECKED_CAST")
                    val dataSource = dataSources[index] as DataSource<*, Any?>
                    dataSource.endIsolation(null, previousIsolation)
                }
                MutableTokenListPool.release(previousIsolations)
                return result
            } catch (throwable: Throwable) {
                previousIsolations.forEachReversedIndexed { index, previousIsolation ->
                    @Suppress("UNCHECKED_CAST")
                    val dataSource = dataSources[index] as DataSource<*, Any?>
                    dataSource.endIsolation(throwable, previousIsolation)
                }
                MutableTokenListPool.release(previousIsolations)
                throw throwable
            }
        }

        /**
         * Invalidates all cacheable computations which depend on any of the given [identifiers].
         * Dependencies are set up by calling the `recordDependency` function passed into [observe].
         */
        @JvmStatic
        fun invalidateDependants(identifiers: Set<Any>) {
            // TODO(manuel.unterhofer@jetbrains.com) Flip this so that observers are managed here
            sync { applyObservers }.forEach { it(identifiers, Snapshot.current) }
        }

        /**
         * Registers an invalidator that will be called when invalidations happen, e.g. via
         * [invalidateDependants]. The observer will be called with the identifiers of all
         * dependencies that are invalidated.
         *
         * @return an [ObserverHandle] that can be used to unregister the observer
         */
        @JvmStatic
        fun registerInvalidator(invalidator: (identifiers: Set<Any>) -> Unit): ObserverHandle {
            // TODO(manuel.unterhofer@jetbrains.com) Flip this so that observers are managed here
            val snapshotApplyObserver =
                Snapshot.registerApplyObserver { identifiers, _ -> invalidator(identifiers) }
            return ObserverHandle { snapshotApplyObserver.dispose() }
        }
    }
}

/**
 * The type returned by observer registration methods that unregisters the observer when it is
 * disposed.
 */
fun interface ObserverHandle {
    /** Dispose the observer causing it to be unregistered from the snapshot system. */
    fun dispose()
}

/**
 * Resource pool for avoiding allocations as far as possible while iterating through [DataSource]s.
 * Specifically, we need [MutableObjectList]s to hold the tokens they return from
 * [DataSource.startObservation] and [DataSource.startIsolation] to later pass them back into
 * [DataSource.endObservation] and [DataSource.endIsolation], respectively.
 */
@PublishedApi
internal object MutableTokenListPool {
    private val mainThreadArrayPool = mutableObjectListOf<MutableObjectList<Any?>>()
    private val nonMainThreadLock = makeSynchronizedObject()
    private val nonMainThreadArrayPool = mutableObjectListOf<MutableObjectList<Any?>>()

    fun acquire(): MutableObjectList<Any?> {
        return if (currentThreadId() == MainThreadId) {
            if (mainThreadArrayPool.isNotEmpty()) {
                mainThreadArrayPool.removeLast()
            } else {
                mutableObjectListOf<Any?>()
            }
        } else {
            synchronized(nonMainThreadLock) {
                if (nonMainThreadArrayPool.isNotEmpty()) {
                    nonMainThreadArrayPool.removeLast()
                } else {
                    mutableObjectListOf<Any?>()
                }
            }
        }
    }

    fun release(tokenArray: MutableObjectList<Any?>) {
        tokenArray.clear()
        if (currentThreadId() == MainThreadId) {
            mainThreadArrayPool.add(tokenArray)
        } else {
            synchronized(nonMainThreadLock) { nonMainThreadArrayPool.add(tokenArray) }
        }
    }
}
