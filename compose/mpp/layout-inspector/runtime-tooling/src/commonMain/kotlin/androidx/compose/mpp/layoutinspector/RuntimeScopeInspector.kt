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

package androidx.compose.mpp.layoutinspector

import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Collects restart-scope metrics for a composition and its subcompositions. Runtime supports a
 * single observer per composition. Close the inspector when collection is no longer needed.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
class RuntimeScopeInspector(composition: Composition) {
    private val observer = ScopeMetricsObserver()
    private val handle = checkNotNull(composition.setObserver(observer)) {
        "The composition does not support runtime observation"
    }

    fun snapshot(): List<RuntimeScopeMetrics> = observer.snapshot()

    fun close() {
        if (observer.close()) handle.dispose()
    }
}

/**
 * Counts for a live restart scope since attachment. [id] is local to one inspector.
 * [observedStates] is unknown until the inspector sees a visit that reports the scope's reads.
 */
data class RuntimeScopeMetrics(
    val id: Long,
    val observedStates: Int?,
    val invalidations: Long,
    val visits: Long,
    val skips: Long,
)

@OptIn(ExperimentalComposeRuntimeApi::class)
private class ScopeMetricsObserver : CompositionObserver {
    private val lock = SynchronizedObject()
    private val scopes = mutableMapOf<RecomposeScope, ScopeRecord>()
    private var nextId = 1L
    private var closed = false

    override fun onBeginComposition(composition: ObservableComposition) {}

    override fun onScopeEnter(scope: RecomposeScope) {
        synchronized(lock) {
            if (!closed) {
                scopes.getOrPut(scope) { ScopeRecord(nextId++) }.visit =
                    ScopeVisit(scope.isMarkedSkipped())
            }
        }
    }

    override fun onReadInScope(scope: RecomposeScope, value: Any) {
        synchronized(lock) {
            val reads = scopes[scope]?.visit?.reads ?: return@synchronized
            if (reads.none { it === value }) reads.add(value)
        }
    }

    override fun onScopeExit(scope: RecomposeScope) {
        synchronized(lock) {
            val record = scopes[scope] ?: return@synchronized
            val visit = record.visit ?: return@synchronized
            record.visit = null
            record.visits++
            if (scope.isMarkedSkipped()) {
                // A read replay can enter with the flag still set from the previous visit.
                if (!visit.skippedOnEnter) record.skips++
            } else {
                record.observedStates = visit.reads.size
            }
        }
    }

    override fun onEndComposition(composition: ObservableComposition) {}

    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) {
        synchronized(lock) {
            if (!closed) scopes.getOrPut(scope) { ScopeRecord(nextId++) }.invalidations++
        }
    }

    override fun onScopeDisposed(scope: RecomposeScope) {
        synchronized(lock) { scopes.remove(scope) }
    }

    fun snapshot(): List<RuntimeScopeMetrics> =
        synchronized(lock) {
            scopes.values.map { record ->
                RuntimeScopeMetrics(
                    id = record.id,
                    observedStates = record.observedStates,
                    invalidations = record.invalidations,
                    visits = record.visits,
                    skips = record.skips,
                )
            }
        }

    fun close(): Boolean =
        synchronized(lock) {
            if (closed) return@synchronized false
            closed = true
            scopes.clear()
            true
        }

    private class ScopeRecord(val id: Long) {
        var observedStates: Int? = null
        var invalidations = 0L
        var visits = 0L
        var skips = 0L
        var visit: ScopeVisit? = null
    }

    private class ScopeVisit(val skippedOnEnter: Boolean) {
        val reads = mutableListOf<Any>()
    }
}

// This adapter uses the skip flag available in the Compose Runtime used by this fork.
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private fun RecomposeScope.isMarkedSkipped(): Boolean =
    (this as? androidx.compose.runtime.RecomposeScopeImpl)?.skipped == true
