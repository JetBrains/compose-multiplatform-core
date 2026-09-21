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

package androidx.compose.runtime

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The parent-driven recompose gate: a subcomposition whose host has a measure pass pending
 * skips its standalone recomposition (the pending measure re-runs its content with fresh
 * captures); with no measure pending it recomposes standalone as usual.
 *
 * This file uses three related terms. `parent` and `child` name two sibling root compositions
 * in one test. `ancestor` and `nested` name a real composition-context chain. `subcomposition`
 * names a composition a real host creates, as `SubcomposeLayout` does.
 */
@OptIn(InternalComposeApi::class)
class ParentDrivenRecomposeGateTests {
    @Test
    fun gateSkipsStandaloneRecomposeAndTheMeasureRefreshRecovers(): Unit = runBlocking {
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var composed = 0
        var seen = -1
        var measurePending = false // what the ui layer's gate reads from its LayoutNode
        val composition = Composition(UnitApplier(), recomposer)
        composition.setParentDrivenRecomposeGate { measurePending }
        try {
            composition.setContent {
                composed++
                seen = state.value
            }
            assertEquals(1, composed)

            // No measure pending: the standalone recomposition runs as usual.
            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(2, composed)
            assertEquals(1, seen)

            // Measure pending: the standalone pass is skipped - it would pair the previous
            // measure's stale captures with fresh reads.
            measurePending = true
            state.value = 2
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(2L)
            assertEquals(2, composed) // skipped
            assertEquals(1, seen)

            // The pending measure's re-subcompose (setContent, as SubcomposeLayout does)
            // recovers with fresh values - the skipped invalidation is consumed by design.
            measurePending = false
            composition.setContent {
                composed++
                seen = state.value
            }
            assertEquals(3, composed)
            assertEquals(2, seen)

            // Gate closed again: the next change recomposes standalone.
            state.value = 3
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(3L)
            assertEquals(4, composed)
            assertEquals(3, seen)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun theGateIsEvaluatedAfterTheApplyStage(): Unit = runBlocking {
        // A parent's applyChanges is what installs refreshed child content (e.g. a
        // SubcomposeLayout measure policy capturing new values) and marks the host's
        // measure pending. The gate decision must therefore come AFTER the apply stage of
        // the same pass: a child invalidated alongside its parent must not recompose
        // standalone against captures the parent is just about to refresh.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var measurePending = false
        var parentComposed = 0
        var childComposed = 0
        val parent = Composition(UnitApplier(), recomposer)
        val child = Composition(UnitApplier(), recomposer)
        child.setParentDrivenRecomposeGate { measurePending }
        try {
            parent.setContent {
                parentComposed++
                state.value // the parent depends on the same state as the child
                // What installing a refreshed measure policy does to the host node,
                // reduced to its timing essence: it happens during applyChanges.
                SideEffect { measurePending = true }
            }
            child.setContent {
                childComposed++
                state.value
            }
            assertEquals(1, parentComposed)
            assertEquals(1, childComposed)

            measurePending = false // not pending when the frame begins
            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(2, parentComposed) // the parent recomposed and applied first...
            assertEquals(1, childComposed) // ...so the gate was already true for the child
        } finally {
            child.dispose()
            parent.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aNestedCompositionWaitsWhileItsAncestorIsDeferred(): Unit = runBlocking {
        // An ancestor's recompose and apply step removes a nested composition. A nested
        // composition must wait until that removal runs. The gate works per host. So the
        // ancestor can defer while its nested composition still runs.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var ancestorMeasurePending = false
        var ancestorComposed = 0
        var nestedComposed = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.setParentDrivenRecomposeGate { ancestorMeasurePending }
            ancestor.setContent {
                ancestorComposed++
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.setParentDrivenRecomposeGate { false } // its own host has no measure pending
            nested.setContent {
                nestedComposed++
                state.value
            }
            assertEquals(1, ancestorComposed)
            assertEquals(1, nestedComposed)

            ancestorMeasurePending = true
            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)

            assertEquals(1, ancestorComposed, "the gate defers the ancestor")
            assertEquals(
                1,
                nestedComposed,
                "the nested composition must not recompose ahead of its remover",
            )
        } finally {
            nestedHolder[0]?.dispose()
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun theDeferralPropagatesThroughAThreeLevelChain(): Unit = runBlocking {
        // Section 6.1 of the design says one forward pass is enough. That claim needs the
        // deferral to be transitive. A composition that the propagation deferred must defer its
        // own descendant. `leaf` is the transitive step here. `mid` must defer it, and not
        // `ancestor`.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var ancestorMeasurePending = true
        var midComposed = 0
        var leafComposed = 0
        val ancestorContextHolder = arrayOfNulls<CompositionContext>(1)
        val midContextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val midHolder = arrayOfNulls<Composition>(1)
        val leafHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.setParentDrivenRecomposeGate { ancestorMeasurePending }
            val ancestorContent: @Composable () -> Unit = {
                state.value
                ancestorContextHolder[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)

            val mid =
                Composition(UnitApplier(), ancestorContextHolder[0]!!).also { midHolder[0] = it }
            mid.setParentDrivenRecomposeGate { false } // its own host has no measure pending
            mid.setContent {
                midComposed++
                state.value
                midContextHolder[0] = rememberCompositionContext()
            }

            val leaf = Composition(UnitApplier(), midContextHolder[0]!!).also { leafHolder[0] = it }
            leaf.setParentDrivenRecomposeGate { false }
            leaf.setContent {
                leafComposed++
                state.value
            }
            assertEquals(1, midComposed, "the initial composition of mid must run once")
            assertEquals(1, leafComposed, "the initial composition of leaf must run once")

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(1, midComposed, "the ancestor's gate must defer mid")
            assertEquals(
                1,
                leafComposed,
                "the propagation deferred mid, so it must defer leaf as well",
            )

            // The ancestor's pending measure re-runs its content. That is what the gate promised.
            ancestorMeasurePending = false
            ancestor.setContent(ancestorContent)
            frameClock.sendFrame(2L)
            assertEquals(2, midComposed, "mid must deliver after its ancestor")
            assertEquals(2, leafComposed, "leaf must deliver after mid")
        } finally {
            leafHolder[0]?.dispose()
            midHolder[0]?.dispose()
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aNestedCompositionNeverReadsStateItsAncestorRemoves(): Unit = runBlocking {
        // This is the AIR-6691 shape, reduced to the runtime.
        // Here, `entity` stands for the RhizomeDB dialog entity. Null stands for its deletion.
        // The ancestor holds the gate that drops the nested composition.
        // The nested composition re-reads the entity. That read throws an exception in Air.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val entity = mutableStateOf<String?>("present")
        var ancestorMeasurePending = false
        var staleReads = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.setParentDrivenRecomposeGate { ancestorMeasurePending }
            val ancestorContent: @Composable () -> Unit = {
                contextHolder[0] = rememberCompositionContext()
                if (entity.value == null) {
                    // This stands for the applier releasing the nested host. `SideEffect` runs
                    // after apply, not during it.
                    SideEffect { nestedHolder[0]?.dispose() }
                }
            }
            ancestor.setContent(ancestorContent)
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.setParentDrivenRecomposeGate { false }
            nested.setContent { if (entity.value == null) staleReads++ }
            assertEquals(0, staleReads)

            ancestorMeasurePending = true
            Snapshot.withMutableSnapshot { entity.value = null }
            frameClock.sendFrame(1L)
            assertEquals(0, staleReads, "the nested composition read the deleted entity")

            // The ancestor's pending measure re-runs its content. That run removes the nested
            // composition.
            ancestorMeasurePending = false
            ancestor.setContent(ancestorContent)
            frameClock.sendFrame(2L)
            assertEquals(0, staleReads, "the nested composition must never see the deletion")
            assertTrue(
                nestedHolder[0]!!.isDisposed,
                "the ancestor's refresh must have removed the nested composition, so the " +
                    "re-arm cannot resurrect it",
            )
        } finally {
            nestedHolder[0]?.let { if (!it.isDisposed) it.dispose() }
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aDeferredNestedCompositionStillDelivers(): Unit = runBlocking {
        // The composition that the propagation deferred has no refresh of its own scheduled.
        // Its own gate said it was ready. So the pass must re-arm its invalidation. If it does
        // not, the content stays stale until an unrelated change arrives.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var ancestorMeasurePending = false
        var nestedComposed = 0
        var nestedSaw = -1
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.setParentDrivenRecomposeGate { ancestorMeasurePending }
            val ancestorContent: @Composable () -> Unit = {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.setParentDrivenRecomposeGate { false } // its own host has no measure pending
            nested.setContent {
                nestedComposed++
                nestedSaw = state.value
            }
            assertEquals(1, nestedComposed)
            assertEquals(0, nestedSaw)

            ancestorMeasurePending = true
            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(
                1,
                nestedComposed,
                "the propagation must defer the nested composition this frame",
            )
            assertTrue(recomposer.hasPendingWork, "the deferred invalidation must survive")

            // The ancestor's pending measure re-runs its content. That is what the gate promised.
            ancestorMeasurePending = false
            ancestor.setContent(ancestorContent)
            frameClock.sendFrame(2L)
            assertEquals(2, nestedComposed, "the nested composition delivers after its ancestor")
            assertEquals(1, nestedSaw, "and it sees the new value")
        } finally {
            nestedHolder[0]?.let { if (!it.isDisposed) it.dispose() }
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aGateDeferredCompositionDoesNotAskForAnotherFrame(): Unit = runBlocking {
        // The gate contract consumes a skipped invalidation on purpose. The host's pending
        // measure re-runs the content. So the pass must not re-arm this composition. If it
        // does, it asks for a new frame every frame.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var composed = 0
        val compositionHolder = arrayOfNulls<Composition>(1)

        try {
            val composition =
                Composition(UnitApplier(), recomposer).also { compositionHolder[0] = it }
            composition.setParentDrivenRecomposeGate { true }
            composition.setContent {
                composed++
                state.value
            }
            assertEquals(1, composed)

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(1, composed, "the gate defers it")

            frameClock.sendFrame(2L)
            assertEquals(1, composed, "and nothing re-armed it")
            assertFalse(recomposer.hasPendingWork, "a gate-deferred composition is not re-armed")
        } finally {
            compositionHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun theDeferralReArmStopsAfterTheBound(): Unit = runBlocking {
        // An ancestor that stays gated for many consecutive frames points at a layout fault.
        // Re-arming it at frame rate would keep an idle scene rendering forever.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var nestedComposed = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)
        var frame = 0L

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.setParentDrivenRecomposeGate { true } // never settles
            ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.setParentDrivenRecomposeGate { false }
            nested.setContent {
                nestedComposed++
                state.value
            }
            assertEquals(1, nestedComposed, "the initial composition must run once")

            state.value = 1
            Snapshot.sendApplyNotifications()
            // Frames 1 through 60: the counter climbs to exactly the cap. The re-arm still
            // runs on frame 60, so work stays pending going into frame 61.
            repeat(60) {
                // This stands in for the host's measure. It repeatedly finds the ancestor
                // dirty and never lets it settle.
                recomposer.invalidate(ancestor as ControlledComposition)
                frameClock.sendFrame(++frame)
            }
            assertTrue(recomposer.hasPendingWork, "the bound must not trip before the cap")

            // Frame 61: the counter passes the cap, so the re-arm stops. Frame 62 only drains
            // the ancestor. `nested` has no pending invalidation, so `reArmDeferred` never runs.
            repeat(2) {
                recomposer.invalidate(ancestor as ControlledComposition)
                frameClock.sendFrame(++frame)
            }
            assertEquals(1, nestedComposed, "the ancestor never settles, so it never delivers")
            assertFalse(recomposer.hasPendingWork, "the re-arm must stop at the bound")
        } finally {
            nestedHolder[0]?.dispose()
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun theDeferralReArmSurvivesAGateBranchGap(): Unit = runBlocking {
        // A composition's own host briefly finds it pending on some frames. On those frames the
        // composition takes the gate branch instead of the propagation-deferred branch. This is
        // the normal end of one run, not a sign of a stuck composition, and it must not carry a
        // count across into an unrelated later run. See CompositionImpl.consecutiveDeferralReArms.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var nestedComposed = 0
        var nestedGateOpen = false
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)
        var frame = 0L

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.setParentDrivenRecomposeGate { true } // never settles
            ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.setParentDrivenRecomposeGate { nestedGateOpen }
            nested.setContent {
                nestedComposed++
                state.value
            }
            assertEquals(1, nestedComposed, "the initial composition must run once")

            state.value = 1
            Snapshot.sendApplyNotifications()
            // 62 repeats of one propagation-deferred frame followed by one gate-branch frame.
            // Each gate-branch frame ends the run, so it never carries past the pair. A bound
            // that only resets on an actual recompose still counts the propagation-deferred
            // frame in each repeat. 62 of them pass the cap.
            repeat(62) {
                recomposer.invalidate(ancestor as ControlledComposition)
                recomposer.invalidate(nested as ControlledComposition)
                frameClock.sendFrame(++frame)

                nestedGateOpen = true
                recomposer.invalidate(nested as ControlledComposition)
                frameClock.sendFrame(++frame)
                nestedGateOpen = false
            }
            // One more propagation-deferred frame. Under frame adjacency the gate branch above
            // reset the run every time, so the count here is 1 and the re-arm still runs.
            recomposer.invalidate(ancestor as ControlledComposition)
            recomposer.invalidate(nested as ControlledComposition)
            frameClock.sendFrame(++frame)

            assertEquals(1, nestedComposed, "the ancestor never settles, so it never delivers")
            assertTrue(
                recomposer.hasPendingWork,
                "a gate-branch gap must restart the run, not extend it past the bound",
            )
        } finally {
            nestedHolder[0]?.dispose()
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun nothingIsDeferredWhenNoAncestorIsGated(): Unit = runBlocking {
        // This is the common path. Both hosts are settled, so both recompose in the same
        // pass. The ancestor goes first. Wave 2 visits compositions in the order in which
        // each initial composition finished. A nested composition is always created after
        // its parent.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val order = mutableListOf<String>()
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.setParentDrivenRecomposeGate { false }
            ancestor.setContent {
                order += "ancestor"
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.setParentDrivenRecomposeGate { false }
            nested.setContent {
                order += "nested"
                state.value
            }

            order.clear()
            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)

            assertEquals(
                listOf("ancestor", "nested"),
                order,
                "with no ancestor gated, nothing may be deferred, and the ancestor must recompose " +
                    "before the nested composition",
            )
        } finally {
            nestedHolder[0]?.dispose()
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }
}
