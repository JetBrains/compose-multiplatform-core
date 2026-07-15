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

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DataSource
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.UnitApplier
import androidx.compose.runtime.isolate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(InternalComposeApi::class)
class DataSourceSnapshotTestsJvm {
    @Test
    fun thePinnedViewExcludesExternalPublicationsAcrossPublishes() {
        val pinned = mutableStateOf(0)
        val own = mutableStateOf(0)
        val unit = DataSource.takeSnapshot()
        try {
            thread { Snapshot.withMutableSnapshot { pinned.value = 1 } }.join()
            unit.isolate {
                assertEquals(0, pinned.value) // external write invisible
                own.value = 10
            }
            unit.isolate {
                assertEquals(0, pinned.value) // STILL invisible after our own publish
                assertEquals(10, own.value) // own chain visible
            }
        } finally {
            unit.dispose()
        }
        assertEquals(1, pinned.value)
        val fresh = DataSource.takeSnapshot()
        try {
            fresh.isolate { assertEquals(1, pinned.value) } // new pin sees it
        } finally {
            fresh.dispose()
        }
    }

    @Test
    fun aConflictingTransactionFailsAloneAndAFreshOneThenSucceeds() {
        val state = mutableStateOf(0)
        val other = mutableStateOf(0)
        val unit = DataSource.takeSnapshot()
        try {
            val frame = unit.beginIsolation()
            state.value = 1
            thread { Snapshot.withMutableSnapshot { state.value = 2 } }.join()
            assertFailsWith<SnapshotApplyConflictException> {
                unit.endIsolation(frame, null) // the publish conflicts; writes discarded
            }
            assertEquals(2, state.value) // the external write won
            unit.isolate { other.value = 5 } // a fresh transaction succeeds; unit unharmed
            assertEquals(5, other.value)
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun isolateCleansUpEvenWhenItsPublishConflicts() {
        val openBefore = Snapshot.openSnapshotCount()
        val state = mutableStateOf(0)
        val unit = DataSource.takeSnapshot()
        try {
            assertFailsWith<SnapshotApplyConflictException> {
                unit.isolate {
                    state.value = 1
                    // A concurrent external commit to the same object makes the publish
                    // at the end of this block conflict.
                    thread { Snapshot.withMutableSnapshot { state.value = 2 } }.join()
                }
            }
            assertEquals(2, state.value) // the external commit won; ours was discarded
        } finally {
            unit.dispose()
        }
        // The failed publish must not leak the transaction or anything it pinned.
        assertEquals(openBefore, Snapshot.openSnapshotCount())
    }

    @Test
    fun anExternalPublicationIsParkedWhileAUnitIsOpenAndReleasedAtItsDispose() {
        val state = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val unit = DataSource.takeSnapshot()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        try {
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
            assertEquals(1, state.value) // the VALUE is committed and world-visible...
            assertTrue(notified.none { state in it }) // ...but the notification is parked
            assertTrue(Snapshot.hasParkedApplyNotifications())
            unit.dispose() // the pin rotation releases it onto now-fresh views
            assertEquals(1, notified.count { state in it }) // delivered exactly once
            assertFalse(Snapshot.hasParkedApplyNotifications())
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun ownChainPublishesNeverConflictWithEachOther() {
        val state = mutableStateOf(0)
        val unit = DataSource.takeSnapshot()
        try {
            repeat(5) { i ->
                unit.isolate {
                    assertEquals(i, state.value) // earlier publications visible to later blocks
                    state.value = i + 1
                }
            }
            assertEquals(5, state.value)
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun createdObjectsCommitThroughAndSurviveTheUnitsDispose() {
        // Objects CREATED in a block write in place and never enter `modified`; their
        // records are committed by id retirement, not by batch membership. And because
        // the context closes like an applied snapshot, disposing the unit must not abandon
        // the records the world already sees.
        lateinit var created: MutableState<Int>
        val unit = DataSource.takeSnapshot()
        unit.isolate {
            created = mutableStateOf(0)
            created.value = 42
        }
        assertEquals(42, created.value) // world-visible right after the block
        unit.dispose()
        assertEquals(42, created.value) // and still after the unit is gone
    }

    // The following tests replicate the scene frame loop: a holder-carried cycle unit
    // wraps the recomposer's frame dispatch the way BaseComposeScene does when frame
    // isolation is enabled, with a pin swap at frame start.

    @Test
    fun externalPublicationConvergesAtThePinSwapWithoutAStaleRecompose(): Unit = runBlocking {
        val holder = SnapshotHolder()
        val frameClock = BroadcastFrameClock()
        val recomposer =
            Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock + holder)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var compositions = 0
        var composedValue = -1
        val composition = Composition(UnitApplier(), recomposer)
        var unit = DataSource.takeSnapshot().also { holder.current = it }
        try {
            unit.isolate {
                composition.setContent { compositions++; composedValue = state.value }
            }
            assertEquals(1, compositions)

            // Between frames: an external thread publishes a change.
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()

            // Frame: pin swap FIRST (publishes nothing, releases the parked invalidation),
            // then the frame is dispatched WITHOUT an enclosing slice, like the scene does:
            // the Recomposer slices its own pipeline into sequential children.
            holder.current = null
            unit.dispose()
            unit = DataSource.takeSnapshot().also { holder.current = it }
            frameClock.sendFrame(1L)

            assertEquals(2, compositions) // exactly one recompose - no stale extra frame
            assertEquals(1, composedValue) // and it saw the fresh value
        } finally {
            holder.current = null
            unit.dispose()
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun frameClockWritesRecomposeInTheSameFrame(): Unit = runBlocking {
        val holder = SnapshotHolder()
        val frameClock = BroadcastFrameClock()
        val recomposer =
            Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock + holder)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val tick = mutableStateOf(0L)
        val composedTicks = mutableListOf<Long>()
        val composition = Composition(UnitApplier(), recomposer)
        val unit = DataSource.takeSnapshot().also { holder.current = it }
        try {
            unit.isolate {
                composition.setContent {
                    composedTicks.add(tick.value)
                    LaunchedEffect(Unit) {
                        while (true) {
                            withFrameNanos { tick.value = it }
                        }
                    }
                }
            }
            // Frames are dispatched WITHOUT an enclosing slice, like the scene does: the
            // animation pump publishes its slice - delivering the tick's invalidation -
            // before the recompose pass is taken, which is what makes the tick compose in
            // the SAME frame.
            frameClock.sendFrame(1L)
            assertTrue(1L in composedTicks, "same-frame tick, saw: $composedTicks")
            frameClock.sendFrame(2L)
            assertTrue(2L in composedTicks, "same-frame tick, saw: $composedTicks")
        } finally {
            holder.current = null
            unit.dispose()
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun anEffectTaskFlushedInsideASliceFoldsIntoTheSlicesPublish() {
        // Reproduces the effect-dispatcher wrapper in ComposeSceneRecomposer: an effect
        // task flushed while an input slice is current runs through isolate(), which nests
        // its transaction inside the slice - the task's writes are visible to the rest of
        // the slice, fold silently, and the slice boundary owns the one publish. Keep
        // runEffectTask below in sync with that wrapper.
        val holder = SnapshotHolder()
        val sliceState = mutableStateOf(0)
        val effectState = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSource.takeSnapshot().also { holder.current = it }

        fun runEffectTask(task: () -> Unit) {
            holder.current?.isolate(task) ?: task()
        }

        try {
            // An input slice: writes, and flushes an effect mid-slice.
            unit.isolate {
                sliceState.value = 1
                runEffectTask { effectState.value = 2 } // flushed while the slice is current
                assertEquals(2, effectState.value) // the effect write is visible to the slice
                assertTrue(notified.none { effectState in it }) // but nothing is published yet
                assertTrue(notified.none { sliceState in it })
            }
            assertEquals(1, sliceState.value)
            assertEquals(2, effectState.value)
            assertEquals(
                1,
                notified.count { sliceState in it && effectState in it },
            ) // one atomic publish carried both the slice's and the effect's writes
        } finally {
            holder.current = null
            unit.dispose()
            handle.dispose()
        }
    }

    @Test
    fun aStandaloneEffectTaskPublishesItsOwnSlice() {
        // The other branch of the same wrapper: an effect task flushed with no slice
        // current gets its own top-level transaction and publishes immediately on return.
        val holder = SnapshotHolder()
        val effectState = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSource.takeSnapshot().also { holder.current = it }

        fun runEffectTask(task: () -> Unit) {
            holder.current?.isolate(task) ?: task()
        }

        try {
            runEffectTask { effectState.value = 2 }
            assertEquals(2, effectState.value) // published on return
            assertEquals(1, notified.count { effectState in it })
        } finally {
            holder.current = null
            unit.dispose()
            handle.dispose()
        }
    }

    @Test
    fun aNestedSliceDefersItsPublishToTheOutermostBoundary() {
        // Reproduces BaseComposeScene slice nesting (e.g. calculateContentSize invoked from
        // within an input slice): the nested isolate() folds into the enclosing slice, so
        // the outermost boundary owns the one atomic publish.
        val outerState = mutableStateOf(0)
        val innerState = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSource.takeSnapshot()

        fun <T> runSlice(block: () -> T): T = unit.isolate(block)

        try {
            runSlice {
                outerState.value = 1
                runSlice { innerState.value = 2 } // nested slice inside the current one
                assertEquals(2, innerState.value) // the inner write is visible to the slice
                assertTrue(notified.none { innerState in it }) // inner exit published nothing
            }
            assertEquals(1, outerState.value)
            assertEquals(2, innerState.value)
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
    fun writesFromANestedTransactionAreVisibleToALaterOneInsideTheSameSlice() {
        // The KDT first-frame topology: the initial composition (nested transaction 1)
        // creates and writes state; a subcomposition's composeInitial (nested transaction
        // 2) reads it - all inside one slice of the same root unit. Objects created in
        // transaction 1 write in place (empty `modified`), so this pins that the stock
        // empty-fold still adopts their ids.
        val unit = DataSource.takeSnapshot()
        try {
            lateinit var state: MutableState<Int>
            val slice = unit.beginIsolation()
            val first = unit.beginIsolation() // nests inside the slice
            state = mutableStateOf(0)
            state.value = 42
            unit.endIsolation(first, null)
            assertEquals(42, state.value) // direct read in the slice
            val second = unit.beginIsolation() // a second nested transaction
            assertEquals(42, state.value)
            unit.endIsolation(second, null)
            unit.endIsolation(slice, null)
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun nestedWritesAroundNotifyObjectsInitializedStayVisibleToLaterTransactions() {
        // Same topology, plus the id-advance the Recomposer performs mid-composition:
        // notifyObjectsInitialized() advances the composing snapshot's id, so the nested
        // transaction publishes records under several ids (snapshotId + previousIds). All
        // of them must be adopted and stay visible to later nested transactions.
        val unit = DataSource.takeSnapshot()
        try {
            lateinit var before: MutableState<Int>
            lateinit var after: MutableState<Int>
            val slice = unit.beginIsolation()
            val composing = unit.beginIsolation()
            before = mutableStateOf(0)
            before.value = 1
            Snapshot.notifyObjectsInitialized()
            after = mutableStateOf(0)
            after.value = 2
            unit.endIsolation(composing, null)
            assertEquals(1, before.value)
            assertEquals(2, after.value)
            val reader = unit.beginIsolation()
            assertEquals(1, before.value)
            assertEquals(2, after.value)
            unit.endIsolation(reader, null)
            unit.endIsolation(slice, null)
        } finally {
            unit.dispose()
        }
    }
}
