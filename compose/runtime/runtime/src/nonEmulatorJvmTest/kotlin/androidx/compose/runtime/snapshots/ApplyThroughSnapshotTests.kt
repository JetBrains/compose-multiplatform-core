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

import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for the read-pinned, apply-through snapshot kind: reads inside the context (and its
 * children) see the state at creation plus everything already applied through it; a child
 * apply commits through to the global immediately, and the read-only context itself never
 * has anything pending at dispose.
 */
@OptIn(InternalComposeApi::class)
class ApplyThroughSnapshotTests {
    @Test
    fun readsArePinnedAcrossExternalCommits() {
        val external = mutableStateOf(0)
        val ctx = takeApplyThroughSnapshot()
        try {
            thread { Snapshot.withMutableSnapshot { external.value = 1 } }.join()
            ctx.enter { assertEquals(0, external.value) } // context view pinned
            val child = ctx.takeNestedMutableSnapshot()
            child.enter { assertEquals(0, external.value) } // child view pinned too
            child.dispose()
        } finally {
            ctx.dispose()
        }
        assertEquals(1, external.value)
    }

    @Test
    fun aChildApplyIsImmediatelyVisibleToTheWorld() {
        val state = mutableStateOf(0)
        val external = mutableStateOf(0)
        val ctx = takeApplyThroughSnapshot()
        try {
            thread { Snapshot.withMutableSnapshot { external.value = 1 } }.join()
            val child = ctx.takeNestedMutableSnapshot()
            child.enter { state.value = 42 }
            child.apply().check()
            child.dispose()
            // World-visible immediately: raw global read AND a fresh stock snapshot.
            assertEquals(42, state.value)
            val fresh = Snapshot.takeSnapshot()
            try {
                fresh.enter { assertEquals(42, state.value) }
            } finally {
                fresh.dispose()
            }
            // The context still does not see the external commit.
            ctx.enter {
                assertEquals(42, state.value) // own commit visible through the context
                assertEquals(0, external.value) // external still pinned out
            }
        } finally {
            ctx.dispose()
        }
    }

    @Test
    fun aConflictingChildFailsAloneAndTheContextStaysUsable() {
        val state = mutableStateOf(0)
        val other = mutableStateOf(0)
        val ctx = takeApplyThroughSnapshot()
        try {
            val child = ctx.takeNestedMutableSnapshot()
            child.enter { state.value = 1 }
            thread { Snapshot.withMutableSnapshot { state.value = 2 } }.join()
            assertTrue(child.apply() is SnapshotApplyResult.Failure)
            child.dispose() // abandons the conflicted writes, stock
            assertEquals(2, state.value) // the external write won
            ctx.enter { assertEquals(0, state.value) } // the pin never saw either write
            val next = ctx.takeNestedMutableSnapshot() // the context stays usable
            next.enter { other.value = 5 }
            next.apply().check()
            next.dispose()
            assertEquals(5, other.value)
        } finally {
            ctx.dispose()
        }
    }

    @Test
    fun anEqualValueRaceResolvesViaTheMergePolicy() {
        // mergeRecords resolves a concurrent commit that is equivalent to the child's own
        // write (the stock policies return `current` from their equivalence branch), so a
        // benign double-write race publishes instead of failing.
        val state = mutableStateOf(0)
        val ctx = takeApplyThroughSnapshot()
        try {
            val child = ctx.takeNestedMutableSnapshot()
            child.enter { state.value = 2 }
            thread { Snapshot.withMutableSnapshot { state.value = 2 } }.join()
            child.apply().check() // equivalent values: resolved, not conflicting
            child.dispose()
            assertEquals(2, state.value)
        } finally {
            ctx.dispose()
        }
    }

    @Test
    fun consecutiveChildrenSeeEachOtherAndNeverConflict() {
        val state = mutableStateOf(0)
        val ctx = takeApplyThroughSnapshot()
        try {
            repeat(5) { i ->
                val child = ctx.takeNestedMutableSnapshot()
                child.enter {
                    assertEquals(i, state.value) // earlier commits visible via the context
                    state.value = i + 1
                }
                assertTrue(child.apply() == SnapshotApplyResult.Success)
                child.dispose()
            }
            assertEquals(5, state.value)
        } finally {
            ctx.dispose()
        }
    }

    @Test
    fun siblingChildrenConflictOnOverlappingWrites() {
        // A sibling committed after this child was taken is a genuine race on the same
        // object: resolve-or-fail, exactly like an external commit.
        val state = mutableStateOf(0)
        val ctx = takeApplyThroughSnapshot()
        try {
            val loser = ctx.takeNestedMutableSnapshot()
            val winner = ctx.takeNestedMutableSnapshot()
            winner.enter { state.value = 1 }
            winner.apply().check()
            winner.dispose()
            loser.enter { state.value = 2 }
            assertTrue(loser.apply() is SnapshotApplyResult.Failure)
            loser.dispose()
            assertEquals(1, state.value) // the first commit stands
        } finally {
            ctx.dispose()
        }
    }

    @Test
    fun directWritesToTheContextAreRejected() {
        val state = mutableStateOf(0)
        val ctx = takeApplyThroughSnapshot()
        try {
            val error =
                assertFailsWith<IllegalStateException> { ctx.enter { state.value = 1 } }
            assertEquals("Cannot modify a state object in a read-only snapshot", error.message)
            assertEquals(0, state.value)
        } finally {
            ctx.dispose()
        }
    }

    @Test
    fun aChildApplyDeliversItsWholeBatchExactlyOnce() {
        val early = mutableStateOf(0)
        val late = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val ctx = takeApplyThroughSnapshot()
        try {
            val child = ctx.takeNestedMutableSnapshot()
            child.enter {
                early.value = 1
                // A mid-life id advance (what the Recomposer's notifyObjectsInitialized
                // does): records now span several ids, but the batch is still ONE batch.
                Snapshot.notifyObjectsInitialized()
                late.value = 1
            }
            assertTrue(notified.none { early in it }) // nothing before the apply
            child.apply().check()
            child.dispose()
            assertEquals(1, notified.count { early in it }) // delivered exactly once
            assertEquals(1, notified.count { late in it })
            val again = ctx.takeNestedMutableSnapshot()
            again.enter { early.value = 2 } // a rewrite in a later child
            again.apply().check()
            again.dispose()
            assertEquals(2, notified.count { early in it }) // is delivered again
        } finally {
            ctx.dispose()
            handle.dispose()
        }
    }

    @Test
    fun externalNotificationsAreParkedUntilDisposeAndOwnCommitsDispatchImmediately() {
        val external = mutableStateOf(0)
        val own = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val ctx = takeApplyThroughSnapshot()
        try {
            val child = ctx.takeNestedMutableSnapshot()
            child.enter { own.value = 1 }
            child.apply().check()
            child.dispose()
            assertEquals(1, notified.count { own in it }) // own commit delivered right away
            thread { Snapshot.withMutableSnapshot { external.value = 1 } }.join()
            assertEquals(1, external.value) // the external VALUE committed normally...
            assertEquals(0, notified.count { external in it }) // ...its notification parked
            ctx.dispose()
            assertEquals(1, notified.count { external in it }) // released at the rotation
            assertEquals(1, notified.count { own in it }) // the own commit NOT redelivered
        } finally {
            ctx.dispose()
            handle.dispose()
        }
    }

    @Test
    fun aParkedBatchWaitsForEveryPinThatPredatesTheCommit() {
        // The watermark rule: a parked batch releases only once no open pin predating the
        // commit remains - releasing earlier would hand the notification to a receiver
        // whose pin still hides the values, re-creating the consumed-invalidation bug.
        val state = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val first = takeApplyThroughSnapshot()
        val second = takeApplyThroughSnapshot()
        try {
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
            assertTrue(notified.none { state in it }) // parked: two pins predate it
            first.dispose()
            assertTrue(notified.none { state in it }) // still parked: the second pin remains
            second.dispose()
            assertEquals(1, notified.count { state in it }) // released once, at the watermark
        } finally {
            first.dispose()
            second.dispose()
            handle.dispose()
        }
    }

    @Test
    fun anOwnCommitIsRedeliveredWhenAPredatingSiblingPinRotates() {
        // A commit through context A is dispatched immediately (A's same-frame machinery
        // depends on it), but a sibling context pinned before it consumed that dispatch
        // against a view that hides the values; the enqueued redelivery is released at the
        // sibling's rotation - one benign echo to the committer included.
        val state = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val committer = takeApplyThroughSnapshot()
        val sibling = takeApplyThroughSnapshot()
        try {
            val child = committer.takeNestedMutableSnapshot()
            child.enter { state.value = 1 }
            child.apply().check()
            child.dispose()
            assertEquals(1, notified.count { state in it }) // immediate own dispatch
            sibling.dispose()
            assertEquals(2, notified.count { state in it }) // redelivered for the sibling
            committer.dispose()
            assertEquals(2, notified.count { state in it }) // nothing left to release
        } finally {
            committer.dispose()
            sibling.dispose()
            handle.dispose()
        }
    }

    @Test
    fun parkingNudgesRegisteredParkedApplyNotifiers() {
        // Parking replaces the dispatch that would otherwise wake render scheduling; the
        // content-free notifier is what keeps an idle scene rendering (and thus rotating
        // the pin that releases the parked work).
        val state = mutableStateOf(0)
        var nudges = 0
        val notifierHandle = Snapshot.registerParkedApplyNotifier { nudges++ }
        val ctx = takeApplyThroughSnapshot()
        try {
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
            assertTrue(nudges > 0)
        } finally {
            ctx.dispose()
            notifierHandle.dispose()
        }
    }

    @Test
    fun createdObjectsSurviveTheContextDispose() {
        // Records committed through the context live at ids the context's previousIds also
        // reference; the applied-style close must not abandon them (the stock unapplied
        // dispose would destroy values the world already sees).
        lateinit var created: MutableState<Int>
        val ctx = takeApplyThroughSnapshot()
        val child = ctx.takeNestedMutableSnapshot()
        child.enter {
            created = mutableStateOf(0)
            created.value = 42
        }
        child.apply().check()
        child.dispose()
        assertEquals(42, created.value)
        ctx.dispose()
        assertEquals(42, created.value)
    }

    @Test
    fun disposeWithAnOpenChildFailsFast() {
        val ctx = takeApplyThroughSnapshot()
        val child = ctx.takeNestedMutableSnapshot()
        try {
            val error = assertFailsWith<IllegalStateException> { ctx.dispose() }
            assertEquals("Cannot dispose while a child snapshot is open", error.message)
        } finally {
            child.dispose()
            ctx.dispose()
        }
    }

    @Test
    fun nothingAccumulatesAcrossChildrenAndDispose() {
        val state = mutableStateOf(0)
        val before = Snapshot.openSnapshotCount()
        val ctx = takeApplyThroughSnapshot()
        var midLife = -1
        repeat(3) { i ->
            val child = ctx.takeNestedMutableSnapshot()
            child.enter { state.value = i + 1 }
            child.apply().check()
            child.dispose()
            // Mid-life stability: commits must not accumulate open ids across cycles.
            if (midLife == -1) midLife = Snapshot.openSnapshotCount()
            assertEquals(midLife, Snapshot.openSnapshotCount())
        }
        // Writeless children must not accumulate either.
        repeat(3) {
            val child = ctx.takeNestedMutableSnapshot()
            child.enter { /* read-only */ }
            child.apply().check()
            child.dispose()
            assertEquals(midLife, Snapshot.openSnapshotCount())
        }
        ctx.dispose()
        assertEquals(before, Snapshot.openSnapshotCount()) // no leaked ids/children/pins
        assertEquals(3, state.value) // and nothing was left pending
    }
}
