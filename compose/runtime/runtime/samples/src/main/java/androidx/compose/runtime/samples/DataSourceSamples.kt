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

package androidx.compose.runtime.samples

import androidx.annotation.Sampled
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DataSource
import kotlin.collections.set
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty

@Sampled
@Composable
fun KeyValueDataSourceSample() {
    val keyValueDataSource = KeyValueDataSource()
    val keyValueDataSourceRegistration = DataSource.register(keyValueDataSource)
    keyValueDataSource["hostname"] = "localhost"
    var hostname: String by keyValueDataSource

    Column {
        Row {
            TextField(
                value = hostname,
                onValueChange = { hostname = it },
            )
        }
    }
}

/**
 * This is a correct and thread-safe implementation of a key-value data source. It should serve as a
 * reference for other [DataSource] implementations. Pay special attention to the following details:
 * - Nested [DataSources.observe] and [DataSources.isolate] blocks call through to their parent
 *   `recordDependency` and `recordChange` functions, respectively (unless
 *   [DataSources.withoutReadObservation] breaks that chain).
 * - Every thread has its own call stack and thus its own currently applicable observations and
 *   isolations. Make sure you don't mix them up.
 * - Special attention must be paid when merging changes from different threads into a common
 *   singleton.
 */
private class KeyValueDataSource() : DataSource<((Any) -> Boolean)?, KeyValueDataSource.Snapshot?> {
    operator fun get(key: Any): Any? {
        threadDependencyRecorder.get()?.invoke(key)
        return threadSnapshot.get().let {
            if (it != null) {
                it[key]
            } else {
                synchronized(globalSnapshot) { globalSnapshot[key] }
            }
        }
    }

    operator fun set(key: Any, value: Any?) {
        threadSnapshot.get().let {
            if (it != null) {
                it[key] = value
            } else {
                synchronized(globalSnapshot) { globalSnapshot[key] = value }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(@Suppress("unused") thisObj: Any?, property: KProperty<*>): T =
        this[property.name] as T

    operator fun <T> setValue(@Suppress("unused") thisObj: Any?, property: KProperty<*>, value: T) {
        this[property.name] = value
    }

    override fun startObservation(recordDependency: (Any) -> Boolean): ((Any) -> Boolean)? {
        val previousDependencyRecorder = threadDependencyRecorder.get()
        threadDependencyRecorder.set {
            recordDependency(it) or (previousDependencyRecorder?.invoke(it) == true)
        }
        return previousDependencyRecorder
    }

    override fun endObservation(previousObservation: ((Any) -> Boolean)?) {
        threadDependencyRecorder.set(previousObservation)
    }

    override fun pauseCurrentObservation(): ((Any) -> Boolean)? {
        val previousDependencyRecorder = threadDependencyRecorder.get()
        threadDependencyRecorder.set(null)
        return previousDependencyRecorder
    }

    override fun resumeCurrentObservation(pausedObservation: ((Any) -> Boolean)?) {
        checkNotNull(threadDependencyRecorder.get()) {
            "Cannot resume observation while another observation is active"
        }
        threadDependencyRecorder.set(pausedObservation)
    }

    override fun startIsolation(recordChange: ((Any) -> Unit)?): Snapshot? {
        val previousSnapshot = threadSnapshot.get()
        val newSnapshot =
            previousSnapshot?.takeNestedMutableSnapshot(recordChange)
                ?: synchronized(globalSnapshot) {
                    globalSnapshot.takeNestedMutableSnapshot(recordChange)
                }
        newSnapshot.makeCurrent()
        return previousSnapshot
    }

    override fun endIsolation(throwable: Throwable?, previousIsolation: Snapshot?) {
        val snapshot = threadSnapshot.get()!!
        try {
            if (throwable == null) {
                snapshot.apply()
            }
        } finally {
            snapshot.restorePrevious(previousIsolation)
        }
    }

    fun takeMutableSnapshot(recordChange: ((Any) -> Unit)? = null): Snapshot {
        return threadSnapshot.get()?.takeNestedMutableSnapshot(recordChange)
            ?: synchronized(globalSnapshot) {
                globalSnapshot.takeNestedMutableSnapshot(recordChange)
            }
    }

    inline fun global(block: () -> Unit) {
        globalSnapshot.enter(block)
    }

    fun sendPendingInvalidations() {
        globalSnapshot.flushPendingInvalidations()
    }

    private val globalSnapshot = Snapshot(parent = null) {}
    private val threadSnapshot = ThreadLocal<Snapshot>()
    val currentSnapshot: Snapshot
        get() = threadSnapshot.get() ?: globalSnapshot

    private val threadDependencyRecorder = ThreadLocal<((Any) -> Boolean)?>()

    inner class Snapshot(
        private val parent: Snapshot?,
        private val changeRecorder: ((Any) -> Unit)?,
    ) {
        private val initialContent: Map<Any, Any?> =
            parent?.let { HashMap(it.content) } ?: emptyMap()
        private val content = HashMap<Any, Any?>(initialContent)
        private val changes: MutableSet<Any> = mutableSetOf<Any>()
        private var applied = false

        operator fun get(key: Any): Any? {
            return content[key]
        }

        operator fun set(key: Any, value: Any?) {
            if (!content.containsKey(key) || content[key] != value) {
                content[key] = value
                changes.add(key)
                changeRecorder?.invoke(key)
                parent?.changeRecorder?.invoke(key)
            }
        }

        fun takeNestedMutableSnapshot(recordChange: ((Any) -> Unit)? = null): Snapshot {
            return Snapshot(this, recordChange)
        }

        @OptIn(ExperimentalContracts::class)
        inline fun <T> enter(block: () -> T): T {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

            val previousSnapshot = makeCurrent()
            try {
                return block()
            } finally {
                restorePrevious(previousSnapshot)
            }
        }

        fun makeCurrent(): Snapshot? {
            val previousSnapshot = threadSnapshot.get()
            threadSnapshot.set(this)
            return previousSnapshot
        }

        fun restorePrevious(previousSnapshot: Snapshot?) {
            check(threadSnapshot.get() === this) {
                "Cannot restore snapshot; $this is not the current snapshot"
            }
            threadSnapshot.set(previousSnapshot)
        }

        fun apply() {
            check(!applied) { "A snapshot may not be applied multiple times" }
            if (parent === globalSnapshot) {
                synchronized(globalSnapshot) { mergeIntoParent() }
            } else {
                mergeIntoParent()
            }
            applied = true
        }

        private fun mergeIntoParent() {
            checkNotNull(parent) { "Only nested snapshots can be merged into their parent" }
            check(changes.none { initialContent[it] != parent[it] && content[it] != parent[it] }) {
                "Write conflict: The values for the following keys have changed concurrently in " +
                    "source and destination: ${
                    changes.filter { initialContent[it] != parent[it] && content[it] != parent[it] }
                        .map { "$it (was ${initialContent[it]}, now ${this[it]} VS ${parent[it]})" }
                }"
            }
            for (key in changes) {
                // We don't go through the setter on parent here because we want to avoid recording
                // changes again. Those have already been propagated up and recorded on the original
                // write.
                parent.content[key] = content[key]
            }
        }

        fun flushPendingInvalidations() {
            check(this === globalSnapshot) {
                "Only the global snapshot may flush its changes as invalidations"
            }
            synchronized(globalSnapshot) {
                DataSource.invalidateDependants(changes)
                changes.clear()
            }
        }
    }
}

// TODO(manuel.unterhofer@jetbrains.com) Add example that hooks Room into Compose
