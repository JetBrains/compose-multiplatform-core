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

package androidx.compose.runtime.snapshots

import androidx.compose.runtime.DataSource
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.enter
import androidx.compose.runtime.withTransaction
import androidx.compose.runtime.mock.BufferedTestDataSource
import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The read scope: binding a unit's view to the calling thread WITHOUT opening a transaction.
 *
 * This exists because a slice's publication dispatches its invalidations after the slice has
 * been restored off the thread, so those handlers run in the ENCLOSING view - which, on a
 * platform callback thread, is otherwise no view at all.
 */
@OptIn(InternalComposeApi::class)
class DataSourceEnterTests {
    /** A source that records the read-scope fan-out, and can fail its bind on demand. */
    private class RecordingSource(
        private val log: MutableList<String>,
        private val name: String,
        private val failOnMakeCurrent: Boolean = false,
    ) : DataSource {
        override fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)?,
            block: () -> T,
        ): T = block()

        override fun <T> withTransaction(block: () -> T): T = block()

        override fun advanceGlobalSnapshot(): Set<Any> = emptySet()

        override fun takeSnapshot(): DataSource.Snapshot =
            object : DataSource.Snapshot {
                override fun makeCurrent(): Any? {
                    log.add("bind:$name")
                    if (failOnMakeCurrent) error("boom")
                    return null
                }

                override fun restoreCurrent(previous: Any?) {
                    log.add("restore:$name")
                }

                override fun beginTransaction(): Any? = null

                override fun endTransaction(frame: Any?, cause: Throwable?) {}

                override fun dispose() {}
            }
    }

    @Test
    fun enteringAUnitMakesItsViewCurrentAndLeavingRestoresTheThread() {
        val unit = DataSourceContext().takeSnapshot()
        try {
            val outside = Snapshot.current
            unit.enter {
                assertNotSame(outside, Snapshot.current, "entering must bind the unit's own view")
            }
            assertSame(outside, Snapshot.current, "leaving must restore exactly what was displaced")
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun theEnteredViewRejectsDirectWrites() {
        val state = mutableStateOf(0)
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.enter {
                // The frame root is read-only: all mutation goes through a transaction, so an
                // unwrapped write is a bug and fails fast rather than reaching the world.
                assertFailsWith<IllegalStateException> { state.value = 1 }
            }
            assertEquals(0, state.value)
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun enteringTakesNoSnapshot() {
        val unit = DataSourceContext().takeSnapshot()
        try {
            val outside = Snapshot.openSnapshotCount()
            unit.enter {
                assertEquals(
                    outside,
                    Snapshot.openSnapshotCount(),
                    "enter binds an existing view; it must not take a snapshot",
                )
            }
            assertEquals(outside, Snapshot.openSnapshotCount())
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun makeCurrentFansOutForwardAndRestoresInReverse() {
        val log = mutableListOf<String>()
        val context = DataSourceContext(RecordingSource(log, "a"), RecordingSource(log, "b"))
        val unit = context.takeSnapshot()
        try {
            unit.enter {}
        } finally {
            unit.dispose()
        }
        // Same order as beginTransaction/endTransaction: substrate first on the way in (it does not
        // log), last-declared-first on the way out.
        assertEquals(listOf("bind:a", "bind:b", "restore:b", "restore:a"), log)
    }

    @Test
    fun aFailingMakeCurrentRestoresWhatWasAlreadyBound() {
        val log = mutableListOf<String>()
        val context =
            DataSourceContext(
                RecordingSource(log, "ok"),
                RecordingSource(log, "boom", failOnMakeCurrent = true),
            )
        val unit = context.takeSnapshot()
        try {
            assertFailsWith<IllegalStateException> { unit.enter { fail("the block must not run") } }
            // "ok" is unbound again; nothing is left half-entered on the thread.
            assertEquals(listOf("bind:ok", "bind:boom", "restore:ok"), log)
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun aCommittedWriteIsVisibleToALaterReadInTheSameEnteredScope() {
        val state = mutableStateOf(0)
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.enter {
                unit.withTransaction { state.value = 42 }
                assertEquals(
                    42,
                    state.value,
                    "the entered view adopts its own transaction's commit (stock fold adoption)",
                )
            }
        } finally {
            unit.dispose()
        }
    }

    /**
     * The Air crash, reduced. A publication dispatches its invalidations after the transaction has
     * been restored off the thread, and the composite ends foreign members BEFORE the substrate
     * publishes - so without a read scope the handler runs with no view from anyone.
     */
    @Test
    fun aHandlerFiredByAPublishCanReadAViewRequiringSourceInsideAReadScope() {
        val state = mutableStateOf(0)
        val source = BufferedTestDataSource(requiresBoundView = true)
        val context = DataSourceContext(source)
        val unit = context.takeSnapshot()
        var handled = 0
        val handle =
            Snapshot.registerApplyObserver { changed, _ ->
                if (state in changed) {
                    context.observe(recordDependency = { false }, recordChange = null) {
                        source.read("k")
                    }
                    handled++
                }
            }
        try {
            unit.enter { unit.withTransaction { state.value = 1 } }
            assertEquals(1, handled, "the publish dispatched, and the handler could read")
        } finally {
            handle.dispose()
            unit.dispose()
        }
    }

    /**
     * The negative control for the test above: without the read scope the same handler fails. This
     * is what pins the mechanism as load-bearing rather than incidental - delete `enter` from the
     * previous test and it becomes this one.
     */
    @Test
    fun withoutAReadScopeAHandlerFiredByAPublishHasNoView() {
        val state = mutableStateOf(0)
        val source = BufferedTestDataSource(requiresBoundView = true)
        val context = DataSourceContext(source)
        val unit = context.takeSnapshot()
        val handle =
            Snapshot.registerApplyObserver { changed, _ ->
                if (state in changed) {
                    context.observe(recordDependency = { false }, recordChange = null) {
                        source.read("k")
                    }
                }
            }
        try {
            val error = assertFailsWith<IllegalStateException> { unit.withTransaction { state.value = 1 } }
            assertTrue(
                error.message?.contains("No bound view") == true,
                "expected the source's own diagnostic, got: ${error.message}",
            )
        } finally {
            handle.dispose()
            unit.dispose()
        }
    }

    /**
     * THE CRUX. `transactionBase()` must still resolve the frame root as the base for a transaction
     * opened while that root is current, so the transaction is a depth-1 apply-through child that
     * delivers. If this fails, entering has changed delivery semantics and the design is wrong.
     */
    @Test
    fun aTransactionOpenedInsideAnEnteredScopeStillDeliversOnPublish() {
        val state = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.enter { unit.withTransaction { state.value = 1 } }
            assertEquals(
                1,
                notified.count { state in it },
                "entering must not suppress depth-1 delivery",
            )
        } finally {
            handle.dispose()
            unit.dispose()
        }
    }

    @Test
    fun aNestedTransactionInsideAnEnteredScopeStillFoldsSilently() {
        val outer = mutableStateOf(0)
        val inner = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.enter {
                unit.withTransaction {
                    outer.value = 1
                    unit.withTransaction { inner.value = 2 }
                    assertEquals(2, inner.value, "the fold is visible to the enclosing transaction")
                    assertTrue(
                        notified.none { inner in it },
                        "a nested transaction publishes nothing of its own",
                    )
                }
            }
            assertEquals(
                1,
                notified.count { outer in it && inner in it },
                "one publish at the outermost boundary, carrying both states",
            )
        } finally {
            handle.dispose()
            unit.dispose()
        }
    }
}
