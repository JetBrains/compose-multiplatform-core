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
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.UnitApplier
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.invalidateDependants
import androidx.compose.runtime.withTransaction
import androidx.compose.runtime.mock.BufferedTestDataSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** A minimal foreign source for parking-scope tests. */
private fun plainSource(): DataSource =
    object : DataSource {
        override fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)?,
            block: () -> T,
        ): T = block()

        override fun <T> withTransaction(block: () -> T): T = block()

        override fun advanceGlobalSnapshot(): Set<Any> = emptySet()

        override fun takeSnapshot(): DataSource.Snapshot =
            object : DataSource.Snapshot {
                override fun makeCurrent(): Any? = null

                override fun restoreCurrent(previous: Any?) {}

                override fun beginTransaction(): Any? = null

                override fun endTransaction(frame: Any?, cause: Throwable?) {}

                override fun dispose() {}
            }
    }

@OptIn(InternalComposeApi::class)
class DataSourceSnapshotTestsJvm {
    @Test
    fun thePinnedViewExcludesExternalPublicationsAcrossPublishes() {
        val pinned = mutableStateOf(0)
        val own = mutableStateOf(0)
        val unit = DataSourceContext().takeSnapshot()
        try {
            thread { Snapshot.withMutableSnapshot { pinned.value = 1 } }.join()
            unit.withTransaction {
                assertEquals(0, pinned.value) // external write invisible
                own.value = 10
            }
            unit.withTransaction {
                assertEquals(0, pinned.value) // STILL invisible after our own publish
                assertEquals(10, own.value) // own chain visible
            }
        } finally {
            unit.dispose()
        }
        assertEquals(1, pinned.value)
        val fresh = DataSourceContext().takeSnapshot()
        try {
            fresh.withTransaction { assertEquals(1, pinned.value) } // new pin sees it
        } finally {
            fresh.dispose()
        }
    }

    @Test
    fun aConflictingTransactionFailsAloneAndAFreshOneThenSucceeds() {
        val state = mutableStateOf(0)
        val other = mutableStateOf(0)
        val unit = DataSourceContext().takeSnapshot()
        try {
            val frame = unit.beginTransaction()
            state.value = 1
            thread { Snapshot.withMutableSnapshot { state.value = 2 } }.join()
            assertFailsWith<SnapshotApplyConflictException> {
                unit.endTransaction(frame, null) // the publish conflicts; writes discarded
            }
            assertEquals(2, state.value) // the external write won
            unit.withTransaction { other.value = 5 } // a fresh transaction succeeds; unit unharmed
            assertEquals(5, other.value)
        } finally {
            unit.dispose()
        }
    }

    @Test
    fun isolateCleansUpEvenWhenItsPublishConflicts() {
        val openBefore = Snapshot.openSnapshotCount()
        val state = mutableStateOf(0)
        val unit = DataSourceContext().takeSnapshot()
        try {
            assertFailsWith<SnapshotApplyConflictException> {
                unit.withTransaction {
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
    fun anExternalPublicationIsPendingForADomainUntilItsRotation() {
        // Delivery is now per-domain, at the domain's own SnapshotHolder.rotate() - not at
        // a bare unit's dispose(), and not gated globally: global observers stay immediate
        // even while a domain sits on a pending publication.
        val external = mutableStateOf(0)
        val notifiedGlobally = mutableListOf<Set<Any>>()
        val notifiedByDomain = mutableListOf<Set<Any>>()
        val globalHandle =
            Snapshot.registerApplyObserver { changed, _ -> notifiedGlobally.add(changed) }
        val holder = SnapshotHolder(DataSourceContext(), isolating = true).also { it.activate() }
        val domainHandle =
            holder.registerApplyObserver { changed, _ -> notifiedByDomain.add(changed) }
        try {
            thread { Snapshot.withMutableSnapshot { external.value = 1 } }.join()
            assertEquals(1, notifiedGlobally.count { external in it }) // global: immediate
            assertTrue(notifiedByDomain.none { external in it }) // domain: pending...
            assertTrue(holder.hasPendingDelivery)
            holder.rotate()
            assertEquals(1, notifiedByDomain.count { external in it }) // ...released at rotation
        } finally {
            domainHandle.dispose()
            globalHandle.dispose()
            holder.close()
        }
    }

    @Test
    fun aClosedHolderYieldsNullInsteadOfFailing() {
        val holder = SnapshotHolder(DataSourceContext(), isolating = true)
        // Open but empty is a lifecycle bug and fails fast.
        assertFailsWith<IllegalStateException> { holder.checkedCurrent }
        holder.activate()
        assertEquals(holder.current, holder.checkedCurrent)
        holder.close()
        // After close, straggler frame work falls back to the stock, un-isolated path.
        assertEquals(null, holder.checkedCurrent)
    }

    @Test
    fun invalidateDependantsDispatchesImmediatelyFromInsideASliceAndIsPendingForTheDomainOutside() {
        // A source publishing at a slice boundary dispatches while the slice child is
        // still current: that is the cycle's OWN commit and must deliver immediately to
        // both the domain and globals (same-frame contract). The same call outside any
        // slice is not attributed to this domain's current unit, so it is pending for this
        // domain until its own rotation - though it is still immediate for globals.
        val source = plainSource()
        val token = Any()
        val notifiedGlobally = mutableListOf<Set<Any>>()
        val notifiedByDomain = mutableListOf<Set<Any>>()
        val globalHandle =
            Snapshot.registerApplyObserver { changed, _ -> notifiedGlobally.add(changed) }
        val holder =
            SnapshotHolder(DataSourceContext(source), isolating = true).also { it.activate() }
        val domainHandle =
            holder.registerApplyObserver { changed, _ -> notifiedByDomain.add(changed) }
        try {
            holder.current!!.withTransaction {
                source.invalidateDependants(setOf(token))
                assertEquals(1, notifiedByDomain.count { token in it }) // delivered immediately
            }
            source.invalidateDependants(setOf(token)) // outside any slice: not this cycle's own
            assertEquals(2, notifiedGlobally.count { token in it }) // global: both immediate
            assertEquals(1, notifiedByDomain.count { token in it }) // domain: the second pending...
            assertTrue(holder.hasPendingDelivery)
            holder.rotate()
            assertEquals(2, notifiedByDomain.count { token in it }) // ...released at the rotation
        } finally {
            domainHandle.dispose()
            globalHandle.dispose()
            holder.close()
        }
    }

    // aSourceScopedInvalidationReleasesAtItsOwnContextsRotationOnly: removed - the global
    // parked-notification assertion it exercised was deleted with the per-consumer
    // delivery rework; the decoupling property itself is now covered by DeliveryDomainTests
    // (per-domain pending unions, not global source-keyed parking).

    // substrateInvalidationsStillWaitForEveryPin: removed - the global "every context's pin
    // gates the batch" watermark it exercised no longer exists under the per-consumer
    // delivery rework; each domain now delivers independently at its own rotation (see
    // DeliveryDomainTests.eachDomainDeliversAtItsOwnRotation), and a raw (non-domain) unit
    // like the ones this test used is never gated at all.

    // anOwnCycleSourceInvalidationDispatchesImmediatelyAndRedeliversToSiblingPins: removed -
    // sibling-echo redelivery is exactly what the per-consumer delivery rework forbids: a
    // committing domain's own commit must never be redelivered to it later, and a raw
    // (non-domain) unit has no rotation to redeliver at in the first place. The inverted
    // invariant (no echo back to the committer) is pinned by
    // DeliveryDomainTests.ownCycleCommitsAreImmediateForTheCommitterOnly.

    @Test
    fun aSourceWithNoOpenPinDispatchesImmediately() {
        val source = plainSource()
        val token = Any()
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        try {
            source.invalidateDependants(setOf(token)) // nobody pinned: nothing to protect
            assertEquals(1, notified.count { token in it })
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun ownChainPublishesNeverConflictWithEachOther() {
        val state = mutableStateOf(0)
        val unit = DataSourceContext().takeSnapshot()
        try {
            repeat(5) { i ->
                unit.withTransaction {
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
        val unit = DataSourceContext().takeSnapshot()
        unit.withTransaction {
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
        // The state must exist BEFORE the holder's own cycle unit is taken (activate()
        // takes it), or the unit's pin would predate - and hide - the state's creation.
        val state = mutableStateOf(0)
        // The holder must be a REGISTERED domain (activate(), not a manually-assigned
        // current): the Recomposer's own recompose-trigger observer is domain-scoped, and
        // domain-scoped observers only ever fire for registered domains.
        val holder = SnapshotHolder(DataSourceContext(), isolating = true).also { it.activate() }
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock + holder)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        var compositions = 0
        var composedValue = -1
        val composition = Composition(UnitApplier(), recomposer)
        try {
            holder.current!!.withTransaction {
                composition.setContent {
                    compositions++
                    composedValue = state.value
                }
            }
            assertEquals(1, compositions)

            // Between frames: an external thread publishes a change.
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()

            // Frame: the pin swap FIRST (publishes nothing, releases the pending
            // invalidation to this domain) in the scene's order - rotate() takes the
            // successor BEFORE disposing the predecessor, so `delivered subset visible`
            // holds - then the frame is dispatched WITHOUT an enclosing slice, like the
            // scene does: the Recomposer slices its own pipeline into sequential children.
            holder.rotate()
            frameClock.sendFrame(1L)

            assertEquals(2, compositions) // exactly one recompose - no stale extra frame
            assertEquals(1, composedValue) // and it saw the fresh value
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
            holder.close()
        }
    }

    @Test
    fun aHookBasedSourceReadInsideDerivedStateInvalidatesTheComposition(): Unit = runBlocking {
        // Composition-path analog of SnapshotStateObserverTestsCommon's
        // aHookBasedSourceReadInsideDerivedStateInvalidatesTheScope. Composition.recordReadOf
        // gates a derived-state recalculation's reads on its OWN guard (!areChildrenComposing),
        // a mechanism distinct from SnapshotStateObserver's deriveStateScopeCount guard -
        // subcomposition raises the same composition-side guard too - so it needs its own
        // coverage rather than relying on the observer-path test alone.
        // The derived state (itself a StateObject) must exist BEFORE the holder's own cycle
        // unit is taken (activate() takes it), or the unit's pin would predate - and hide -
        // its creation, exactly like the mutableStateOf(0) in the sibling tests above.
        val source = BufferedTestDataSource()
        val derived = derivedStateOf { source.read("a") }
        val holder =
            SnapshotHolder(DataSourceContext(source), isolating = true).also { it.activate() }
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock + holder)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        var compositions = 0
        var composedValue: Int? = null
        val composition = Composition(UnitApplier(), recomposer)
        try {
            holder.current!!.withTransaction {
                composition.setContent {
                    compositions++
                    composedValue = derived.value
                }
            }
            assertEquals(1, compositions)
            assertEquals(null, composedValue)

            source.write("a", 1)
            source.advanceAndInvalidate()

            // Same pin-swap-then-frame order as
            // externalPublicationConvergesAtThePinSwapWithoutAStaleRecompose.
            holder.rotate()
            frameClock.sendFrame(1L)

            assertEquals(2, compositions) // the composition observed a dependency on "a"...
            assertEquals(1, composedValue) // ...and recomposed to the fresh value
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
            holder.close()
        }
    }

    @Test
    fun frameClockWritesRecomposeInTheSameFrame(): Unit = runBlocking {
        // The state must exist BEFORE the holder's own cycle unit is taken (activate()
        // takes it), or the unit's pin would predate - and hide - the state's creation.
        val tick = mutableStateOf(0L)
        // See externalPublicationConvergesAtThePinSwapWithoutAStaleRecompose for why the
        // holder is activate()'d rather than manually assigned.
        val holder = SnapshotHolder(DataSourceContext(), isolating = true).also { it.activate() }
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock + holder)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val composedTicks = mutableListOf<Long>()
        val composition = Composition(UnitApplier(), recomposer)
        try {
            holder.current!!.withTransaction {
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
            composition.dispose()
            recomposer.cancel()
            runner.join()
            holder.close()
        }
    }

    @Test
    fun anEffectTaskFlushedInsideASliceFoldsIntoTheSlicesPublish() {
        // Reproduces the effect-dispatcher wrapper in ComposeSceneRecomposer: an effect
        // task flushed while an input slice is current runs through withTransaction(), which nests
        // its transaction inside the slice - the task's writes are visible to the rest of
        // the slice, fold silently, and the slice boundary owns the one publish. Keep
        // runEffectTask below in sync with that wrapper.
        // Both states must exist BEFORE the holder's own cycle unit is taken (activate()
        // takes it), or the unit's pin would predate - and hide - their creation.
        val sliceState = mutableStateOf(0)
        val effectState = mutableStateOf(0)
        val holder = SnapshotHolder(DataSourceContext(), isolating = true).also { it.activate() }
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = holder.current!!

        fun runEffectTask(task: () -> Unit) {
            holder.current?.withTransaction(task) ?: task()
        }

        try {
            // An input slice: writes, and flushes an effect mid-slice.
            unit.withTransaction {
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
            holder.close()
            handle.dispose()
        }
    }

    @Test
    fun aStandaloneEffectTaskPublishesItsOwnSlice() {
        // The other branch of the same wrapper: an effect task flushed with no slice
        // current gets its own top-level transaction and publishes immediately on return.
        // The state must exist BEFORE the holder's own cycle unit is taken (activate()
        // takes it), or the unit's pin would predate - and hide - the state's creation.
        val effectState = mutableStateOf(0)
        val holder = SnapshotHolder(DataSourceContext(), isolating = true).also { it.activate() }
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }

        fun runEffectTask(task: () -> Unit) {
            holder.current?.withTransaction(task) ?: task()
        }

        try {
            runEffectTask { effectState.value = 2 }
            assertEquals(2, effectState.value) // published on return
            assertEquals(1, notified.count { effectState in it })
        } finally {
            holder.close()
            handle.dispose()
        }
    }

    @Test
    fun aNestedSliceDefersItsPublishToTheOutermostBoundary() {
        // Reproduces BaseComposeScene slice nesting (e.g. calculateContentSize invoked from
        // within an input slice): the nested withTransaction() folds into the enclosing slice, so
        // the outermost boundary owns the one atomic publish.
        val outerState = mutableStateOf(0)
        val innerState = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        val unit = DataSourceContext().takeSnapshot()

        fun <T> runSlice(block: () -> T): T = unit.withTransaction(block)

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
        val unit = DataSourceContext().takeSnapshot()
        try {
            lateinit var state: MutableState<Int>
            val slice = unit.beginTransaction()
            val first = unit.beginTransaction() // nests inside the slice
            state = mutableStateOf(0)
            state.value = 42
            unit.endTransaction(first, null)
            assertEquals(42, state.value) // direct read in the slice
            val second = unit.beginTransaction() // a second nested transaction
            assertEquals(42, state.value)
            unit.endTransaction(second, null)
            unit.endTransaction(slice, null)
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
        val unit = DataSourceContext().takeSnapshot()
        try {
            lateinit var before: MutableState<Int>
            lateinit var after: MutableState<Int>
            val slice = unit.beginTransaction()
            val composing = unit.beginTransaction()
            before = mutableStateOf(0)
            before.value = 1
            Snapshot.notifyObjectsInitialized()
            after = mutableStateOf(0)
            after.value = 2
            unit.endTransaction(composing, null)
            assertEquals(1, before.value)
            assertEquals(2, after.value)
            val reader = unit.beginTransaction()
            assertEquals(1, before.value)
            assertEquals(2, after.value)
            unit.endTransaction(reader, null)
            unit.endTransaction(slice, null)
        } finally {
            unit.dispose()
        }
    }
}
