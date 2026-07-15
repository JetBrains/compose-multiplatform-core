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
import androidx.compose.runtime.withTransaction
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot.Companion.openSnapshotCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(InternalComposeApi::class)
class DataSourceSnapshotTests {
    @Test
    fun anIsolateBlockPublishesItsWritesAndTheUnitStaysUsable() {
        val state = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.withTransaction { state.value = 1 }
            assertEquals(1, state.value) // published to the world at the block's end
            assertEquals(1, notified.count { state in it }) // observers saw it once
            unit.withTransaction {
                assertEquals(1, state.value) // the own publication stays visible inside
                state.value = 2 // a rewrite in a later block is delivered again
            }
            assertEquals(2, state.value)
            assertEquals(2, notified.count { state in it })
        } finally {
            unit.dispose()
            handle.dispose()
        }
    }

    @Test
    fun anIsolateBlockWithoutWritesPublishesNothing() {
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.withTransaction { /* read-only slice */ }
            assertTrue(notified.isEmpty())
        } finally {
            unit.dispose()
            handle.dispose()
        }
    }

    @Test
    fun writesStayInvisibleToTheWorldUntilTheBlockEnds() {
        val state = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.withTransaction {
                state.value = 1
                Snapshot.global { assertEquals(0, state.value) } // the world is still blind
                assertTrue(notified.none { state in it }) // and nothing was delivered yet
            }
            assertEquals(1, state.value) // atomically published at the boundary
            assertEquals(1, notified.count { state in it })
        } finally {
            unit.dispose()
            handle.dispose()
        }
    }

    @Test
    fun aNestedIsolateFoldsIntoTheEnclosingBlockAndPublishesNothingItself() {
        // withTransaction() nests by thread: called inside one of this unit's transactions, the
        // new transaction nests in it - its writes fold into the enclosing transaction
        // silently, and only the outermost boundary publishes and delivers.
        val outerState = mutableStateOf(0)
        val innerState = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.withTransaction {
                outerState.value = 1
                unit.withTransaction { innerState.value = 2 } // nested: folds, no own publication
                assertEquals(2, innerState.value) // visible to the rest of the block
                Snapshot.global { assertEquals(0, innerState.value) } // but not to the world
                assertTrue(notified.none { innerState in it })
            }
            assertEquals(
                1,
                notified.count { outerState in it && innerState in it },
            ) // the outermost boundary published once, carrying both writes
        } finally {
            unit.dispose()
            handle.dispose()
        }
    }

    @Test
    fun aNestedIsolateThatThrowsDiscardsOnlyItsOwnWrites() {
        // Real nested transactionality: the failing nested block abandons its transaction;
        // the enclosing transaction's writes are untouched and publish normally.
        val outerState = mutableStateOf(0)
        val innerState = mutableStateOf(0)
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.withTransaction {
                outerState.value = 1
                assertFailsWith<IllegalStateException> {
                    unit.withTransaction {
                        innerState.value = 2
                        error("nested failure")
                    }
                }
                assertEquals(0, innerState.value) // the nested writes were discarded
                assertEquals(1, outerState.value) // the enclosing writes are intact
            }
            assertEquals(1, outerState.value) // and published
            assertEquals(0, innerState.value)
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun explicitIsolationFramesFoldSilentlyAndTheOwningFrameDeliversOnce() {
        // The begin/end SPI is also the explicit choreography surface: an inner frame's
        // end folds into the outer transaction silently (stock nested apply), and the
        // fold's writes are delivered - once - when the outer frame publishes.
        val inNested = mutableStateOf(0)
        val alsoInOuter = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSourceContext().takeSnapshot()
        try {
            val outerFrame = unit.beginTransaction()
            val innerFrame = unit.beginTransaction() // nests inside the outer transaction
            inNested.value = 1
            alsoInOuter.value = 1
            unit.endTransaction(innerFrame, null) // silent fold into the outer transaction
            assertEquals(1, inNested.value) // folded writes visible to the outer frame
            assertTrue(notified.none { inNested in it }) // the fold itself was silent
            alsoInOuter.value = 2 // rewritten in the outer transaction after the fold
            unit.endTransaction(outerFrame, null) // publish-through + delivery
            assertEquals(1, notified.count { inNested in it }) // one delivery, at the publish
            assertEquals(1, notified.count { alsoInOuter in it }) // one batch, one delivery
            assertEquals(1, inNested.value)
            assertEquals(2, alsoInOuter.value)
        } finally {
            unit.dispose()
            handle.dispose()
        }
    }

    @Test
    fun aFailedTopLevelBlockDiscardsItsWrites() {
        val state = mutableStateOf(0)
        val unit = DataSourceContext().takeSnapshot()
        try {
            assertFailsWith<IllegalStateException> {
                unit.withTransaction {
                    state.value = 1
                    error("slice failure")
                }
            }
            assertEquals(0, state.value) // abandoned with the transaction
            unit.withTransaction {
                assertEquals(0, state.value)
                state.value = 2 // the unit stays usable
            }
            assertEquals(2, state.value)
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun disposeDoesNotRedeliverTheUnitsOwnPublishedChanges() {
        // The unit's own publications dispatch immediately, and with no sibling context
        // pinned before them nothing is enqueued for redelivery - so the dispose-time
        // release must add nothing, or every writing frame would end in one spurious
        // re-invalidation wave.
        val state = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSourceContext().takeSnapshot()
        try {
            unit.withTransaction { state.value = 1 }
            val delivered = notified.count { state in it }
            unit.dispose() // must NOT redeliver the unit's own publication
            assertEquals(delivered, notified.count { state in it })
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun disposeReleasesAllSnapshots() {
        val openBefore = openSnapshotCount()
        val state = mutableStateOf(0)
        val unit = DataSourceContext().takeSnapshot()
        unit.withTransaction { state.value = 1 }
        assertFailsWith<IllegalStateException> {
            unit.withTransaction {
                state.value = 2 // abandoned with the failing transaction
                error("abandoned")
            }
        }
        unit.dispose()
        assertEquals(1, state.value)
        assertEquals(openBefore, openSnapshotCount())
    }

    @Test
    fun disposeIsIdempotent() {
        val unit = DataSourceContext().takeSnapshot()
        unit.withTransaction { /* no-op */ }
        unit.dispose()
        unit.dispose() // a second dispose call must stay legal
    }
}
