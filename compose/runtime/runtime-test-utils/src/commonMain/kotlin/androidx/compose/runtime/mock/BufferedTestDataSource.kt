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

package androidx.compose.runtime.mock

import androidx.compose.runtime.DataSource
import androidx.compose.runtime.invalidateDependants
import kotlin.concurrent.Volatile

/**
 * A test [DataSource] shaped like a real database integration, which the older test sources in this
 * repository are not. Four traits matter, and each one makes a different class of bug expressible:
 * 1. Reads are recorded ONLY through the [observe] setup hook - never through the static
 *    [DataSource.recordDependency]. A consumer that fails to install the hook therefore records no
 *    dependency at all. (Sources that use the static recorder are tracked everywhere already, which
 *    is why they cannot express that bug.)
 * 2. Writes are BUFFERED. Only [advanceGlobalSnapshot] publishes them, and it returns the
 *    identifiers that changed since the previous advance. A source like this is invisible to the
 *    rest of the system until something drains it.
 * 3. [loseExactDelta] makes the next advance report [wildcard] instead of individual keys,
 *    modelling "exact delta unavailable". [wildcard] is recorded alongside every key, so naming it
 *    invalidates every reader of this source.
 * 4. [takeSnapshot] pins the published version, so isolation and pin rotation are exercised.
 * 5. [requiresBoundView] models a source that cannot read at all without a view bound to the thread -
 *    a database context, say. Such a source throws when [observe] is reached outside both a
 *    transaction and a read scope, which is exactly how Fleet's RhizomeDB source behaves and exactly
 *    the crash that motivated `DataSource.Snapshot.makeCurrent`.
 *
 * NOT thread-safe: drive it from a single thread. [published] is `@Volatile` only so that a value
 * published on one thread is visible to a reader on another; the buffer, the change set, the pin and
 * the bound view are all single-thread state.
 *
 * @param requiresBoundView when true, [observe] fails unless a transaction or a read scope has bound
 *   a view on the calling thread.
 */
class BufferedTestDataSource(private val requiresBoundView: Boolean = false) : DataSource {

    /**
     * Recorded as a dependency alongside every key that is read, so that naming it in an advance
     * invalidates every computation that read anything from this source.
     */
    val wildcard: Any = Any()

    @Volatile private var published: Map<String, Int> = emptyMap()

    private var buffered = mutableMapOf<String, Int>()
    private var changedKeys = mutableSetOf<Any>()
    private var invalidateAllOnNextAdvance = false

    /** The recorder installed by [observe], or null when no hook is installed. */
    private var hook: ((Any) -> Boolean)? = null

    /** The view pinned by an isolating unit taken from this source, or null when unpinned. */
    private var pinnedView: Map<String, Int>? = null

    /**
     * The view bound by a read scope ([DataSource.Snapshot.makeCurrent]), or null when the thread is
     * outside one. Distinct from [pinnedView]: a read scope grants visibility WITHOUT opening a
     * transaction, and it outlives the transactions taken inside it - which is what gives a
     * publication's invalidation handlers something to read through.
     */
    private var boundView: Map<String, Int>? = null

    /** Whether anything has granted this thread a view to read through. */
    private val hasView: Boolean
        get() = pinnedView != null || boundView != null

    /**
     * A tracked read. Records `key` and [wildcard] through the installed hook - and records NOTHING
     * when no hook is installed, which is exactly the failure this source exists to expose.
     *
     * A transaction's pin wins over an enclosing read scope, which in turn wins over the published
     * version.
     */
    fun read(key: String): Int? {
        hook?.let { record ->
            record(key)
            record(wildcard)
        }
        return (pinnedView ?: boundView ?: published)[key]
    }

    /** Buffers a write. Invisible to readers until [advanceGlobalSnapshot]. */
    fun write(key: String, value: Int) {
        buffered[key] = value
        changedKeys.add(key)
    }

    /** Makes the next advance report [wildcard] instead of the individual changed keys. */
    fun loseExactDelta() {
        invalidateAllOnNextAdvance = true
    }

    /** The value currently published to readers, ignoring any buffered write. */
    fun publishedValue(key: String): Int? = published[key]

    /** Publishes buffered writes AND delivers their identifiers, in one call. */
    fun advanceAndInvalidate() {
        invalidateDependants(advanceGlobalSnapshot())
    }

    override fun <T> observe(
        recordDependency: (Any) -> Boolean,
        recordChange: ((Any) -> Unit)?,
        block: () -> T,
    ): T {
        check(!requiresBoundView || hasView) {
            "No bound view: observe() was reached outside both a transaction and a read scope"
        }
        val previous = hook
        hook = recordDependency
        try {
            return block()
        } finally {
            hook = previous
        }
    }

    override fun <T> withTransaction(block: () -> T): T {
        val previous = pinnedView
        pinnedView = published
        try {
            return block()
        } finally {
            pinnedView = previous
        }
    }

    override fun advanceGlobalSnapshot(): Set<Any> {
        if (buffered.isEmpty() && !invalidateAllOnNextAdvance) return emptySet()
        published = published + buffered
        buffered = mutableMapOf()
        val changed: Set<Any> = if (invalidateAllOnNextAdvance) setOf(wildcard) else changedKeys
        changedKeys = mutableSetOf()
        invalidateAllOnNextAdvance = false
        return changed
    }

    override fun takeSnapshot(): DataSource.Snapshot = Pin(published)

    /**
     * A root pin over the version published when it was taken. Nesting is counted so that a nested
     * [beginTransaction] does not clobber the outer pin's restore value.
     */
    private inner class Pin(private val base: Map<String, Int>) : DataSource.Snapshot {
        private var depth = 0
        private var outerView: Map<String, Int>? = null

        // The read scope: grant visibility of this pin's version without opening a transaction.
        // Save/restore rather than set/clear, so entering nests.
        override fun makeCurrent(): Any? {
            val previous = boundView
            boundView = base
            return previous
        }

        @Suppress("UNCHECKED_CAST")
        override fun restoreCurrent(previous: Any?) {
            boundView = previous as Map<String, Int>?
        }

        override fun beginTransaction(): Any? {
            if (depth++ == 0) {
                outerView = pinnedView
                pinnedView = base
            }
            return null
        }

        override fun endTransaction(frame: Any?, cause: Throwable?) {
            if (--depth == 0) {
                pinnedView = outerView
                outerView = null
            }
        }

        override fun dispose() {}
    }
}
