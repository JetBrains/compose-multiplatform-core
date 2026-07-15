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
    fun externalNotificationsAndOwnCommitsBothDispatchImmediately() {
        // A bare ApplyThroughSnapshot has no SnapshotHolder/domain registered around it, so
        // per-consumer delivery degenerates to the stock global dispatch for everything it
        // sees: its own commits AND any external commit, both immediately, and dispose()
        // releases nothing further because nothing was ever pending.
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
            assertEquals(1, external.value) // the external commit applied normally...
            assertEquals(1, notified.count { external in it }) // ...and dispatched immediately
            ctx.dispose()
            assertEquals(1, notified.count { external in it }) // nothing further at dispose
            assertEquals(1, notified.count { own in it }) // and no redelivery of the own commit
        } finally {
            ctx.dispose()
            handle.dispose()
        }
    }

    // aParkedBatchWaitsForEveryPinThatPredatesTheCommit: removed - the global "wait for
    // every open pin" watermark it exercised no longer exists under the per-consumer
    // delivery rework; each domain now delivers independently at its own rotation (see
    // DeliveryDomainTests.eachDomainDeliversAtItsOwnRotation), and a bare, non-domain
    // ApplyThroughSnapshot like the one this test used is never gated at all (see
    // externalNotificationsAndOwnCommitsBothDispatchImmediately above).

    // anOwnCommitIsRedeliveredWhenAPredatingSiblingPinRotates: removed - sibling-echo
    // redelivery is exactly what the per-consumer delivery rework forbids: a committing
    // domain's own commit must never be redelivered to it later, and a bare, non-domain
    // ApplyThroughSnapshot has no rotation to redeliver at in the first place. The inverted
    // invariant (no echo back to the committer) is pinned by
    // DeliveryDomainTests.ownCycleCommitsAreImmediateForTheCommitterOnly.

    // parkingNudgesRegisteredParkedApplyNotifiers: removed - the global parked-apply-
    // notifier registry it exercised was deleted with the per-consumer delivery rework;
    // the wake-up is now SnapshotHolder.onPendingDelivery, per domain (see
    // DeliveryDomainTests.theWakeCallbackFiresOnPush).

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
