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

package androidx.compose.desktop.examples.frameconsistency

import androidx.compose.runtime.DataSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal key/value [DataSource]: the reference implementation of the three-method
 * [DataSource.Snapshot] contract for an external source.
 *
 * - Per-key dependency tokens are recorded on [read]/[entries]; [update] commits
 *   atomically and invalidates exactly the affected tokens.
 * - [takeSnapshot] returns a unit pinned to the source's state at the take; transactions
 *   ([DataSource.Snapshot.beginIsolation]) stack per thread, nested ones fold silently
 *   into their parent, and only a top-level publication commits (per-key
 *   last-writer-wins onto the latest published state) and invalidates.
 * - There is NO external-change bookkeeping: commits made by others while a unit is open
 *   are handled entirely by the runtime's parked-invalidation back-pressure - their
 *   invalidations are parked and released at the pin rotation. A source implementation
 *   only pins its own reads and publishes its own commits.
 */
internal class MapDataSource<K : Any, V> : DataSource {
    /** The committed map, replaced wholesale per commit; volatile for un-pinned readers. */
    @Volatile
    private var published: Map<K, V> = emptyMap()

    private val lock = Any()

    /** The transaction whose view is current on this thread (innermost wins). */
    private val currentTransaction = ThreadLocal<Transaction<K, V>?>()

    /** The view captured by the legacy [isolate] (the flag-off per-pass read pinning). */
    private val legacyIsolation = ThreadLocal<Map<K, V>?>()

    /**
     * A stable dependency identifier per key, kept after removal so a computation that
     * read an absent key is still invalidated when the key is (re)added.
     */
    private val keyTokens = ConcurrentHashMap<K, Any>()

    /** A single identifier for the map's structure (which keys exist). */
    private val keysToken = Any()

    private fun tokenFor(key: K): Any = keyTokens.getOrPut(key) { Any() }

    private fun view(): Map<K, V> =
        currentTransaction.get()?.view() ?: legacyIsolation.get() ?: published

    /** A read of a single [key]: records a dependency and returns the current view's value. */
    fun read(key: K): V? {
        DataSource.recordDependency(tokenFor(key))
        return view()[key]
    }

    /** A read of the whole map: depends on the key structure and on every present value. */
    fun entries(): Map<K, V> {
        DataSource.recordDependency(keysToken)
        val view = view()
        for (key in view.keys) DataSource.recordDependency(tokenFor(key))
        return view
    }

    /**
     * Atomically writes all [entries]: one commit, one invalidation batch. Inside a
     * transaction the writes stay pending until the transaction's boundary publishes them;
     * outside, they commit globally - and while a frame cycle is open the runtime parks
     * the invalidations and releases them at the pin rotation, exactly when the values
     * become visible to the pinned readers.
     */
    fun update(vararg entries: Pair<K, V>) {
        val transaction = currentTransaction.get()
        val tokens = LinkedHashSet<Any>()
        if (transaction != null) {
            entries.forEach { (key, value) ->
                if (key !in transaction.view()) tokens.add(keysToken)
                tokens.add(tokenFor(key))
                transaction.write(key, value)
            }
            transaction.tokens.addAll(tokens)
            tokens.forEach { DataSource.recordChange(it) }
        } else {
            synchronized(lock) {
                entries.forEach { (key, _) ->
                    if (key !in published) tokens.add(keysToken)
                    tokens.add(tokenFor(key))
                }
                published = published + entries
            }
            tokens.forEach { DataSource.recordChange(it) }
            DataSource.invalidateDependants(tokens)
        }
    }

    override fun <T> observe(
        recordDependency: (identifier: Any) -> Boolean,
        recordChange: ((identifier: Any) -> Unit)?,
        block: () -> T,
    ): T = block()

    override fun <T> isolate(block: () -> T): T {
        val alreadyIsolated = legacyIsolation.get() != null
        if (!alreadyIsolated) legacyIsolation.set(view())
        try {
            return block()
        } finally {
            if (!alreadyIsolated) legacyIsolation.set(null)
        }
    }

    override fun advanceGlobalSnapshot(): Set<Any> = emptySet()

    override fun takeSnapshot(): DataSource.Snapshot = MapSnapshot()

    private class Transaction<K : Any, V>(
        val parent: Transaction<K, V>?,
        private val base: Map<K, V>,
    ) {
        val writes = LinkedHashMap<K, V>()
        val tokens = LinkedHashSet<Any>()
        private var cachedView: Map<K, V>? = null

        fun view(): Map<K, V> {
            if (writes.isEmpty()) return base
            return cachedView ?: (base + writes).also { cachedView = it }
        }

        fun write(key: K, value: V) {
            writes[key] = value
            cachedView = null
        }
    }

    private inner class MapSnapshot : DataSource.Snapshot {
        /**
         * The pinned base: the source's state when this unit was taken, advanced only by
         * the unit's own top-level publications - never by others' commits.
         */
        private var base: Map<K, V> = published
        private var disposed = false

        override fun beginIsolation(): Any? {
            check(!disposed) { "Cannot isolate in a disposed snapshot" }
            val parent = currentTransaction.get()
            val transaction = Transaction<K, V>(parent, parent?.view() ?: base)
            currentTransaction.set(transaction)
            return transaction
        }

        override fun endIsolation(frame: Any?, cause: Throwable?) {
            @Suppress("UNCHECKED_CAST")
            val transaction = frame as Transaction<K, V>
            // The canonical order: ALWAYS restore the thread first; publish unless the
            // block failed (this source resolves same-key races last-writer-wins instead
            // of failing); abandoning pending writes is just dropping the transaction.
            currentTransaction.set(transaction.parent)
            if (cause != null || transaction.writes.isEmpty()) return
            val parent = transaction.parent
            if (parent != null) {
                // Nested: fold silently into the enclosing transaction. The top-level
                // boundary owns the commit and the invalidations.
                transaction.writes.forEach { (key, value) -> parent.write(key, value) }
                parent.tokens.addAll(transaction.tokens)
            } else {
                // Top-level: commit the delta onto the LATEST published state (keys
                // committed by others stay intact), advance the pin by our own writes
                // only, and invalidate. The runtime dispatches this immediately - it is
                // the cycle's own commit - and itself redelivers to any sibling cycle
                // still pinned before it.
                synchronized(lock) { published = published + transaction.writes }
                base = transaction.view()
                DataSource.invalidateDependants(transaction.tokens)
            }
        }

        override fun dispose() {
            // Nothing to re-arm here: invalidations for commits made by others while this
            // unit was open were parked by the runtime and are released at this rotation.
            disposed = true
        }
    }
}
