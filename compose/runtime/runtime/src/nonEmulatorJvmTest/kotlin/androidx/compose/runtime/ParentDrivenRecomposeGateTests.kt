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
import kotlin.test.assertFailsWith
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
 * A test stands in for a host's measure the way SubcomposeLayout runs it: `setContent` on the
 * composition, then `reportCurrent()`. The report, not the compose, is what releases the
 * compositions that wait for it.
 *
 * This file uses three related terms. `parent` and `child` name two sibling root compositions
 * in one test. `ancestor` and `nested` name a real composition-context chain. `subcomposition`
 * names a composition a real host creates, as `SubcomposeLayout` does.
 */
// A test stands in for the host, so it reaches the protocol the way a host does.
@OptIn(InternalComposeApi::class)
private val Composition.hosting: ParentDrivenHosting
    get() = (this as CompositionServices).getCompositionService(ParentDrivenHostingKey)!!

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
        composition.hosting.setRecomposeGate { measurePending }
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

            // The pending measure's re-subcompose (setContent and reportCurrent, as
            // SubcomposeLayout does) recovers with fresh values - the skipped invalidation is
            // consumed by design.
            measurePending = false
            composition.setContent {
                composed++
                seen = state.value
            }
            composition.hosting.reportCurrent()
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
        child.hosting.setRecomposeGate { measurePending }
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
            ancestor.hosting.setRecomposeGate { ancestorMeasurePending }
            ancestor.setContent {
                ancestorComposed++
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.hosting.setRecomposeGate { false } // its own host has no measure pending
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
            ancestor.hosting.setRecomposeGate { ancestorMeasurePending }
            val ancestorContent: @Composable () -> Unit = {
                state.value
                ancestorContextHolder[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)

            val mid =
                Composition(UnitApplier(), ancestorContextHolder[0]!!).also { midHolder[0] = it }
            mid.hosting.setRecomposeGate { false } // its own host has no measure pending
            mid.setContent {
                midComposed++
                state.value
                midContextHolder[0] = rememberCompositionContext()
            }

            val leaf = Composition(UnitApplier(), midContextHolder[0]!!).also { leafHolder[0] = it }
            leaf.hosting.setRecomposeGate { false }
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
            ancestor.hosting.reportCurrent()
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
            ancestor.hosting.setRecomposeGate { ancestorMeasurePending }
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
            nested.hosting.setRecomposeGate { false }
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
            ancestor.hosting.reportCurrent()
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
        //
        // No host ever re-runs the ancestor here: its gate simply opens, and nothing calls
        // setContent on it again. deliverDeferral therefore never runs for the ancestor, and
        // never releases the nested composition either. Only the re-arm's fallback
        // invalidation of the nested composition can deliver it on the next frame.
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
        var nestedSaw = -1
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.hosting.setRecomposeGate { ancestorMeasurePending }
            val ancestorContent: @Composable () -> Unit = {
                ancestorComposed++
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.hosting.setRecomposeGate { false } // its own host has no measure pending
            nested.setContent {
                nestedComposed++
                nestedSaw = state.value
            }
            assertEquals(1, ancestorComposed)
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

            // The ancestor's gate simply opens. Nothing calls setContent on it again, so
            // deliverDeferral never runs for it. Only the re-arm can still deliver the nested
            // composition.
            ancestorMeasurePending = false
            frameClock.sendFrame(2L)
            assertEquals(1, ancestorComposed, "the ancestor itself never composes again")
            assertEquals(2, nestedComposed, "the re-arm alone delivers the nested composition")
            assertEquals(1, nestedSaw, "and it sees the new value")
        } finally {
            nestedHolder[0]?.let { if (!it.isDisposed) it.dispose() }
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aWaiterIsRefreshedOnlyAfterItsEnclosingCompositionComposes(): Unit = runBlocking {
        // Wave 2 holds the nested composition back behind its gated ancestor. Refreshing it in
        // wave 2 would let its host run before the ancestor's host. So the refresh must wait for
        // the ancestor to compose, and then happen once.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var refreshRequests = 0
        var nestedComposed = 0
        var nestedSaw = -1
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.hosting.setRecomposeGate { true } // its host's measure is always pending
            val ancestorContent: @Composable () -> Unit = {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.hosting.setRecomposeGate { false } // its own host has no measure pending
            nested.hosting.setRefreshRequest { refreshRequests++ }
            val nestedContent: @Composable () -> Unit = {
                nestedComposed++
                nestedSaw = state.value
            }
            nested.setContent(nestedContent)
            assertEquals(1, nestedComposed)

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(1, nestedComposed, "wave 2 must hold the nested composition back")
            assertEquals(0, refreshRequests, "wave 2 must not refresh it before its ancestor")

            // The ancestor's host measure re-runs the ancestor and reports it current, as
            // SubcomposeLayout does.
            ancestor.setContent(ancestorContent)
            ancestor.hosting.reportCurrent()
            assertEquals(1, refreshRequests, "the ancestor's compose must release it once")

            // The nested host's measure then re-runs the nested composition and reports it.
            nested.setContent(nestedContent)
            nested.hosting.reportCurrent()
            assertEquals(2, nestedComposed)
            assertEquals(1, nestedSaw)
            assertFalse(
                recomposer.hasPendingWork,
                "a delivered composition must leave no fallback re-arm behind",
            )
        } finally {
            nestedHolder[0]?.let { if (!it.isDisposed) it.dispose() }
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aWaiterWithoutARefreshRequestRecomposesRightAfterItsEnclosingComposition(): Unit =
        runBlocking {
            // A gateless composition has no host that could refresh it. The runtime recomposes it
            // directly once the ancestor has composed and applied. So it sees what the ancestor
            // produced in that compose, and it applies, which runs its side effects.
            val frameClock = BroadcastFrameClock()
            val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
            val runner =
                launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                    recomposer.runRecomposeAndApplyChanges()
                }
            val state = mutableStateOf(0)
            val produced = intArrayOf(-1)
            var nestedComposed = 0
            var nestedSawProduced = -1
            var nestedSawFresh = -1
            var nestedSideEffects = 0
            val contextHolder = arrayOfNulls<CompositionContext>(1)
            val ancestorHolder = arrayOfNulls<Composition>(1)
            val nestedHolder = arrayOfNulls<Composition>(1)

            try {
                val ancestor =
                    Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
                ancestor.hosting.setRecomposeGate { true }
                val ancestorContent: @Composable () -> Unit = {
                    produced[0] = state.value
                    contextHolder[0] = rememberCompositionContext()
                }
                ancestor.setContent(ancestorContent)
                val nested =
                    Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
                nested.setContent {
                    nestedComposed++
                    nestedSawProduced = produced[0]
                    nestedSawFresh = state.value
                    SideEffect { nestedSideEffects++ }
                }
                assertEquals(1, nestedComposed)
                assertEquals(1, nestedSideEffects)

                state.value = 1
                Snapshot.sendApplyNotifications()
                frameClock.sendFrame(1L)
                assertEquals(1, nestedComposed, "wave 2 must hold the gateless composition back")

                ancestor.setContent(ancestorContent)
                ancestor.hosting.reportCurrent()
                assertEquals(2, nestedComposed, "it must recompose right after its ancestor")
                assertEquals(1, nestedSawProduced, "and see what the ancestor just produced")
                assertEquals(1, nestedSawFresh)
                assertEquals(2, nestedSideEffects, "and its changes must really be applied")
                assertFalse(recomposer.hasPendingWork, "and leave no fallback re-arm behind")
            } finally {
                nestedHolder[0]?.let { if (!it.isDisposed) it.dispose() }
                ancestorHolder[0]?.dispose()
                recomposer.cancel()
                runner.join()
            }
        }

    @Test
    fun aThreeLevelChainReleasesInCompositionOrder(): Unit = runBlocking {
        // Each waiter is recorded against its nearest held-back ancestor. One compose of the top
        // ancestor must then release the middle one, and the middle one's compose the leaf.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val order = StringBuilder()
        val ancestorContext = arrayOfNulls<CompositionContext>(1)
        val midContext = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(3)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            ancestor.hosting.setRecomposeGate { true }
            val ancestorContent: @Composable () -> Unit = {
                order.append("A${state.value} ")
                ancestorContext[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)
            val mid = Composition(UnitApplier(), ancestorContext[0]!!).also { holders[1] = it }
            mid.setContent {
                order.append("M${state.value} ")
                midContext[0] = rememberCompositionContext()
            }
            val leaf = Composition(UnitApplier(), midContext[0]!!).also { holders[2] = it }
            leaf.setContent { order.append("L${state.value} ") }
            order.clear()

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals("", order.toString(), "wave 2 must hold the whole chain back")

            ancestor.setContent(ancestorContent)
            ancestor.hosting.reportCurrent()
            assertEquals("A1 M1 L1 ", order.toString(), "one compose must release the chain in order")
            assertFalse(recomposer.hasPendingWork)
        } finally {
            holders[2]?.let { if (!it.isDisposed) it.dispose() }
            holders[1]?.let { if (!it.isDisposed) it.dispose() }
            holders[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aWaiterDisposedBeforeItsEnclosingCompositionIsSkipped(): Unit = runBlocking {
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var refreshRequests = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.hosting.setRecomposeGate { true }
            val ancestorContent: @Composable () -> Unit = {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)
            val nested = Composition(UnitApplier(), contextHolder[0]!!)
            nested.hosting.setRecomposeGate { false }
            nested.hosting.setRefreshRequest { refreshRequests++ }
            nested.setContent { state.value }

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            nested.dispose()

            ancestor.setContent(ancestorContent)
            assertEquals(0, refreshRequests, "a disposed waiter must not be refreshed")
        } finally {
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aWaiterInvalidatedDuringItsOwnDeliveryStillRecomposesNext(): Unit = runBlocking {
        // The deferral's re-arm and a real invalidation of the same waiter share one entry in
        // compositionInvalidations, because Recomposer.invalidate de-duplicates. The waiter's own
        // delivery recompose here schedules a further invalidation of itself, once, through a
        // SideEffect. Delivery must not drop that real invalidation just because the re-arm flag
        // was set: the waiter must still recompose on the next frame.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var ancestorMeasurePending = true
        var nestedComposed = 0
        // SideEffect runs after every apply, including the very first one. The guard below
        // counts applies instead of using a one-shot flag, so the self-invalidation happens on
        // the delivery recompose (the second apply), not on the initial one (the first).
        var sideEffectRuns = 0
        val scopeHolder = arrayOfNulls<RecomposeScope>(1)
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.hosting.setRecomposeGate { ancestorMeasurePending }
            val ancestorContent: @Composable () -> Unit = {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)
            // No gate and no refresh request, so delivery recomposes it directly.
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.setContent {
                nestedComposed++
                state.value
                scopeHolder[0] = currentRecomposeScope
                SideEffect {
                    sideEffectRuns++
                    if (sideEffectRuns == 2) {
                        scopeHolder[0]!!.invalidate()
                    }
                }
            }
            assertEquals(1, nestedComposed)

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(1, nestedComposed, "wave 2 must hold the nested composition back")

            // The ancestor's compose delivers nested through a direct recompose. That recompose's
            // own SideEffect invalidates it again, once, after it has already been marked
            // delivered. The gate opens first: invalidating the nested scope also invalidates the
            // ancestor (CompositionContextImpl.invalidate always does), and a gate still stuck at
            // true would propagation-defer the nested composition behind the ancestor forever.
            ancestorMeasurePending = false
            ancestor.setContent(ancestorContent)
            ancestor.hosting.reportCurrent()
            assertEquals(2, nestedComposed, "delivery must recompose the waiter once")
            assertTrue(
                recomposer.hasPendingWork,
                "the invalidation scheduled during delivery must survive it",
            )

            frameClock.sendFrame(2L)
            assertEquals(
                3,
                nestedComposed,
                "the invalidation scheduled during delivery must not be dropped",
            )
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
            composition.hosting.setRecomposeGate { true }
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
            ancestor.hosting.setRecomposeGate { true } // never settles
            ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.hosting.setRecomposeGate { false }
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
    fun aSecondDeferralInTheSameFrameDoesNotRestartTheBound(): Unit = runBlocking {
        // Wave 2 defers the nested composition, and then its host holds it in the same frame.
        // Both re-arm it. The second one must not restart the count, or the bound never trips.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)
        var frame = 0L

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.hosting.setRecomposeGate { true } // never settles
            ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.hosting.setRecomposeGate { false }
            nested.hosting.setRefreshRequest {}
            nested.setContent { state.value }

            state.value = 1
            Snapshot.sendApplyNotifications()
            repeat(62) {
                recomposer.invalidate(ancestor as ControlledComposition)
                frameClock.sendFrame(++frame)
                // Its host's measure holds it again in the same frame.
                assertTrue(nested.hosting.deferToEnclosingComposition())
            }
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
            ancestor.hosting.setRecomposeGate { true } // never settles
            ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.hosting.setRecomposeGate { nestedGateOpen }
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
            ancestor.hosting.setRecomposeGate { false }
            ancestor.setContent {
                order += "ancestor"
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.hosting.setRecomposeGate { false }
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

    @Test
    fun waveTwoVisitsAnAncestorBeforeItsDescendant(): Unit = runBlocking {
        // Wave 2 must visit an enclosing composition before one nested inside it, because the
        // enclosing one is what removes the nested one. This pins that invariant across the change
        // from registration order to depth order.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val order = mutableListOf<String>()
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val midHolder = arrayOfNulls<Composition>(1)
        val leafHolder = arrayOfNulls<Composition>(1)
        val ancestorContext = arrayOfNulls<CompositionContext>(1)
        val midContext = arrayOfNulls<CompositionContext>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.hosting.setRecomposeGate { false }
            ancestor.setContent {
                order += "ancestor"
                state.value
                ancestorContext[0] = rememberCompositionContext()
            }
            val mid = Composition(UnitApplier(), ancestorContext[0]!!).also { midHolder[0] = it }
            mid.hosting.setRecomposeGate { false }
            mid.setContent {
                order += "mid"
                state.value
                midContext[0] = rememberCompositionContext()
            }
            val leaf = Composition(UnitApplier(), midContext[0]!!).also { leafHolder[0] = it }
            leaf.hosting.setRecomposeGate { false }
            leaf.setContent {
                order += "leaf"
                state.value
            }

            order.clear()
            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)

            assertEquals(
                listOf("ancestor", "mid", "leaf"),
                order,
                "wave 2 must visit each composition before the ones nested inside it",
            )
        } finally {
            leafHolder[0]?.dispose()
            midHolder[0]?.dispose()
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aGatelessDescendantWaitsForItsGatedAncestor(): Unit = runBlocking {
        // A gated composition recomposes in wave 2. A gate-less one recomposes in wave 1, which runs
        // first. So a gate-less composition nested inside a gated one recomposes before it. The
        // nested composition then runs a content lambda that its ancestor has not refreshed.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val order = mutableListOf<String>()
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)
        val ancestorContext = arrayOfNulls<CompositionContext>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.hosting.setRecomposeGate { false } // gated, but the gate never closes
            ancestor.setContent {
                order += "ancestor"
                state.value
                ancestorContext[0] = rememberCompositionContext()
            }
            // No gate here, so this one recomposes in wave 1.
            val nested =
                Composition(UnitApplier(), ancestorContext[0]!!).also { nestedHolder[0] = it }
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
                "a gate-less composition must not recompose before the composition that encloses it",
            )
        } finally {
            nestedHolder[0]?.dispose()
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aGatelessDescendantRecomposesInTheSameFrameAsItsGatedAncestor(): Unit = runBlocking {
        // Wave 1 sends a gate-less composition to wave 2 when its ancestor is gated. The
        // ancestor's gate returns false here, so the ancestor recomposes in the same pass.
        // The nested composition must recompose in the same frame, not one frame later.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var ancestorComposed = 0
        var nestedComposed = 0
        val ancestorContext = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.hosting.setRecomposeGate { false } // gated, but the gate never closes
            ancestor.setContent {
                ancestorComposed++
                state.value
                ancestorContext[0] = rememberCompositionContext()
            }
            // No gate here, so this one enters wave 1 unless its ancestor sends it to wave 2.
            val nested =
                Composition(UnitApplier(), ancestorContext[0]!!).also { nestedHolder[0] = it }
            nested.setContent {
                nestedComposed++
                state.value
            }
            assertEquals(1, ancestorComposed)
            assertEquals(1, nestedComposed)

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)

            assertEquals(2, ancestorComposed, "the settled ancestor recomposes in this frame")
            assertEquals(
                2,
                nestedComposed,
                "the gate-less nested composition must recompose in the same frame, not the " +
                    "next one",
            )
        } finally {
            nestedHolder[0]?.dispose()
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aGatelessDescendantDeferredByItsAncestorArrivesWhenTheAncestorRecomposes(): Unit =
        runBlocking {
            // Wave 1 sends this gate-less composition to wave 2 because its ancestor is gated.
            // The ancestor defers this frame, so the nested composition must defer with it.
            // Calling setContent on the ancestor directly, simulating its host's measure pass,
            // delivers the nested composition immediately through deliverDeferral. No further
            // frame is needed for that; sendFrame(2L) below only proves the recomposer then
            // settles with nothing left to do.
            val frameClock = BroadcastFrameClock()
            val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
            val runner =
                launch(
                    Dispatchers.Unconfined + frameClock,
                    start = CoroutineStart.UNDISPATCHED,
                ) {
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
                val ancestor =
                    Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
                ancestor.hosting.setRecomposeGate { ancestorMeasurePending }
                val ancestorContent: @Composable () -> Unit = {
                    state.value
                    contextHolder[0] = rememberCompositionContext()
                }
                ancestor.setContent(ancestorContent)
                // No gate here, so this one enters wave 1 unless its ancestor sends it to wave
                // 2.
                val nested =
                    Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
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
                    "the gate-less nested composition must defer with its gated ancestor",
                )
                assertTrue(recomposer.hasPendingWork, "the deferred invalidation must survive")

                // The ancestor's pending measure re-runs its content. That is what the gate
                // promised. This delivers the nested composition through deliverDeferral, in
                // the same call.
                ancestorMeasurePending = false
                ancestor.setContent(ancestorContent)
                ancestor.hosting.reportCurrent()
                assertEquals(
                    2,
                    nestedComposed,
                    "delivery happens as soon as the ancestor composes",
                )
                frameClock.sendFrame(2L)
                assertEquals(2, nestedComposed, "no further frame is needed")
                assertEquals(1, nestedSaw, "and it sees the new value")
            } finally {
                nestedHolder[0]?.let { if (!it.isDisposed) it.dispose() }
                ancestorHolder[0]?.dispose()
                recomposer.cancel()
                runner.join()
            }
        }

    @Test
    fun aGatelessDescendantRecomposesAfterItsGatelessAncestorInWaveOne(): Unit = runBlocking {
        // This is D1 from the design. Both compositions here have no gate. Both run in wave 1.
        // Wave 1 sorts by depth. The ancestor must run before the composition nested inside it.
        //
        // `RecomposeScope.invalidate()` reaches the recomposer directly. It never touches the
        // snapshot system. This test calls it on the nested composition's own scope first.
        // It then writes a state read only by the ancestor. The intent is to make the nested
        // composition's invalidation arrive at the recomposer before the ancestor's.
        //
        // The attempt does not build a red-green test. `CompositionContextImpl.invalidate`
        // invalidates the enclosing composition before the nested one. Both composer
        // implementations, `GapComposer.kt` and `LinkComposer.kt`, share this behavior. A
        // direct call on the nested composition's own scope still enqueues the ancestor first.
        // The nested composition still arrives second. The ancestor recomposes first here, with
        // or without the depth sort in `Recomposer.kt`.
        //
        // The test still pins the invariant. A future change must not break it silently. The
        // sibling test `aGatelessDescendantWaitsForItsGatedAncestor` covers the deterministic
        // shape, D2. There a gate-less descendant sits under a gated ancestor. That shape is a
        // real red-green test.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val ancestorState = mutableStateOf(0)
        val order = mutableListOf<String>()
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)
        val ancestorContext = arrayOfNulls<CompositionContext>(1)
        val nestedScopeHolder = arrayOfNulls<RecomposeScope>(1)

        try {
            // Neither composition has a gate. Both recompose in wave 1.
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.setContent {
                order += "ancestor"
                ancestorState.value
                ancestorContext[0] = rememberCompositionContext()
            }
            val nested =
                Composition(UnitApplier(), ancestorContext[0]!!).also { nestedHolder[0] = it }
            nested.setContent {
                order += "nested"
                nestedScopeHolder[0] = currentRecomposeScope
            }

            order.clear()
            // A direct call on the nested composition's own scope, made before any state
            // write, tests whether call order alone can invert arrival at the recomposer.
            nestedScopeHolder[0]!!.invalidate()
            // Only the ancestor reads this state.
            ancestorState.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)

            assertEquals(
                listOf("ancestor", "nested"),
                order,
                "the ancestor must recompose before the composition nested inside it",
            )
        } finally {
            nestedHolder[0]?.dispose()
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aHostHoldsASlotWhileAnEnclosingCompositionIsPending(): Unit = runBlocking {
        // A host that is about to compose a slot at measure time asks first. While an enclosing
        // composition has not composed in this frame, the answer is yes. The runtime records the
        // slot once, even if the host asks twice, and refreshes it once after the enclosing one.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var refreshRequests = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestorHolder = arrayOfNulls<Composition>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { ancestorHolder[0] = it }
            ancestor.hosting.setRecomposeGate { true }
            val ancestorContent: @Composable () -> Unit = {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.hosting.setRecomposeGate { true } // its own host is pending as well
            nested.hosting.setRefreshRequest { refreshRequests++ }
            nested.setContent {} // reads nothing, so wave 2 never sees it

            assertFalse(
                nested.hosting.deferToEnclosingComposition(),
                "with nothing held back in this frame, nothing holds",
            )

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L) // wave 2 holds the ancestor back

            assertTrue(nested.hosting.deferToEnclosingComposition(), "the pending ancestor holds it")
            assertTrue(nested.hosting.deferToEnclosingComposition(), "asking twice still holds")
            assertTrue(recomposer.hasPendingWork, "the hold re-arms it as the fallback")

            ancestor.setContent(ancestorContent)
            ancestor.hosting.reportCurrent()
            assertEquals(1, refreshRequests, "the ancestor's compose must refresh it exactly once")
            assertFalse(
                nested.hosting.deferToEnclosingComposition(),
                "a delivered ancestor no longer holds",
            )
        } finally {
            nestedHolder[0]?.let { if (!it.isDisposed) it.dispose() }
            ancestorHolder[0]?.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aCompositionReportedCurrentWithoutComposingReleasesItsWaiters(): Unit = runBlocking {
        // Only the leaf reads the state. Its invalidation climbs the chain, so wave 2 holds the
        // middle composition back as well, and the leaf waits for it. The middle host then finds
        // nothing to compose. Its report must release the leaf, but only after the ancestor.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var middleRefreshes = 0
        var leafRefreshes = 0
        var leafSaw = -1
        val ancestorContext = arrayOfNulls<CompositionContext>(1)
        val middleContext = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(3)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            ancestor.hosting.setRecomposeGate { true }
            val ancestorContent: @Composable () -> Unit = {
                ancestorContext[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)
            val middle = Composition(UnitApplier(), ancestorContext[0]!!).also { holders[1] = it }
            middle.hosting.setRecomposeGate { false }
            middle.hosting.setRefreshRequest { middleRefreshes++ }
            middle.setContent { middleContext[0] = rememberCompositionContext() }
            val leaf = Composition(UnitApplier(), middleContext[0]!!).also { holders[2] = it }
            leaf.hosting.setRecomposeGate { false }
            leaf.hosting.setRefreshRequest { leafRefreshes++ }
            val leafContent: @Composable () -> Unit = { leafSaw = state.value }
            leaf.setContent(leafContent)

            middle.hosting.reportCurrent() // nothing held back, so nothing happens
            assertEquals(0, middleRefreshes + leafRefreshes)

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(0, leafRefreshes, "wave 2 must hold the leaf back")
            assertFalse(middle.hasInvalidations, "sanity: the middle one has nothing to compose")

            middle.hosting.reportCurrent()
            assertEquals(0, leafRefreshes, "the middle one must wait while the ancestor is pending")

            ancestor.setContent(ancestorContent)
            ancestor.hosting.reportCurrent()
            assertEquals(1, middleRefreshes, "the ancestor's compose must release the middle one")
            assertEquals(0, leafRefreshes, "the middle one has not been re-run yet")

            middle.hosting.reportCurrent()
            assertEquals(1, leafRefreshes, "the report must release the leaf")

            leaf.setContent(leafContent)
            leaf.hosting.reportCurrent()
            assertEquals(1, leafSaw)
            assertFalse(
                recomposer.hasPendingWork,
                "delivery must leave no fallback re-arm behind",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aRecoveredCompositionErrorClearsTheRecord(): Unit = runBlocking {
        // In recovery mode a compose that throws at measure time drops every pending
        // invalidation, and recovery recomposes everything. The record must not survive that
        // and hold a slot behind an ancestor that nothing is going to deliver.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        recomposer.setResilientModeEnabled(true)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(3)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            ancestor.hosting.setRecomposeGate { true }
            ancestor.setContent {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            val nested = Composition(UnitApplier(), contextHolder[0]!!).also { holders[1] = it }
            nested.hosting.setRecomposeGate { true }
            nested.hosting.setRefreshRequest {}
            nested.setContent {}

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertTrue(nested.hosting.deferToEnclosingComposition(), "sanity: the ancestor is pending")

            // Another host's measure-time compose fails, and recovery mode captures the error.
            val failing = Composition(UnitApplier(), recomposer).also { holders[2] = it }
            failing.setContent { throw IllegalStateException("a failing measure-time compose") }

            assertFalse(
                nested.hosting.deferToEnclosingComposition(),
                "a recovered composition error must clear the record",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aRecoveredErrorInOneReleasedWaiterStopsTheRelease(): Unit = runBlocking {
        // A released waiter that throws puts the recomposer into its error state in recovery
        // mode. Recovery recomposes everything later, so the release must not go on recomposing
        // the other waiters against the failed state.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        recomposer.setResilientModeEnabled(true)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var attempts = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(3)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            ancestor.hosting.setRecomposeGate { true }
            val ancestorContent: @Composable () -> Unit = {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            ancestor.setContent(ancestorContent)
            repeat(2) { index ->
                val waiter =
                    Composition(UnitApplier(), contextHolder[0]!!).also { holders[index + 1] = it }
                waiter.setContent {
                    if (state.value == 1) {
                        attempts++
                        throw IllegalStateException("a failing released waiter")
                    }
                }
            }

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(0, attempts, "sanity: wave 2 holds both waiters back")

            ancestor.setContent(ancestorContent)
            ancestor.hosting.reportCurrent()
            assertEquals(1, attempts, "the release must stop after the first waiter fails")
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aHostThatComposesAHeldCompositionWithoutReportingIsDetected(): Unit = runBlocking {
        // A host that re-runs a held-back composition must report it current. One that does not
        // leaves the compositions nested in it waiting for their fallback. The recomposer counts
        // such a composition when the next frame starts.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val other = mutableStateOf(0)
        var measurePending = false
        val holders = arrayOfNulls<Composition>(2)

        try {
            val slot = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            slot.hosting.setRecomposeGate { measurePending }
            val slotContent: @Composable () -> Unit = { state.value }
            slot.setContent(slotContent)
            val unrelated = Composition(UnitApplier(), recomposer).also { holders[1] = it }
            unrelated.setContent { other.value }

            // A host that reports: no violation.
            measurePending = true
            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            measurePending = false
            slot.setContent(slotContent)
            slot.hosting.reportCurrent()
            other.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(2L)
            assertEquals(0, recomposer.deferralProtocolViolations, "a reporting host is fine")

            // A host that does not report.
            measurePending = true
            state.value = 2
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(3L)
            measurePending = false
            slot.setContent(slotContent)
            other.value = 2
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(4L)
            assertEquals(
                1,
                recomposer.deferralProtocolViolations,
                "a host that composes a held composition without reporting must be detected",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aHostThatAppliesAPausedCompositionWithoutReportingIsDetected(): Unit = runBlocking {
        // The paused path is a host re-run too: its apply brings the composition up to date, so
        // a host that applies a held composition this way must also report it current.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val other = mutableStateOf(0)
        var measurePending = false
        val holders = arrayOfNulls<Composition>(2)

        try {
            val slot = PausableComposition(UnitApplier(), recomposer).also { holders[0] = it }
            slot.hosting.setRecomposeGate { measurePending }
            val slotContent: @Composable () -> Unit = { state.value }
            slot.setContent(slotContent)
            val unrelated = Composition(UnitApplier(), recomposer).also { holders[1] = it }
            unrelated.setContent { other.value }

            measurePending = true
            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            measurePending = false
            val paused = slot.setPausableContent(slotContent)
            while (!paused.isComplete) paused.resume { false }
            paused.apply()
            other.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(2L)
            assertEquals(
                1,
                recomposer.deferralProtocolViolations,
                "a paused apply of a held composition without a report must be detected",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun aFrameWhoseMeasurePassNeverRanIsDetected(): Unit = runBlocking {
        // The record assumes that a measure pass consumes it before the next frame. A pipeline
        // that runs two frames with no measure between them clears it unconsumed, and only the
        // fallback delivers. The recomposer counts that at the next frame start.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val other = mutableStateOf(0)
        var measurePending = false
        val holders = arrayOfNulls<Composition>(2)

        try {
            val slot = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            slot.hosting.setRecomposeGate { measurePending }
            val slotContent: @Composable () -> Unit = { state.value }
            slot.setContent(slotContent)
            val unrelated = Composition(UnitApplier(), recomposer).also { holders[1] = it }
            unrelated.setContent { other.value }

            // A frame followed by its measure pass: no violation.
            measurePending = true
            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            measurePending = false
            slot.setContent(slotContent)
            slot.hosting.reportCurrent()
            other.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(2L)
            assertEquals(0, recomposer.deferralPipelineViolations, "a consumed record is fine")

            // A frame whose measure pass never runs before the next frame.
            measurePending = true
            state.value = 2
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(3L)
            other.value = 2
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(4L)
            assertEquals(
                1,
                recomposer.deferralPipelineViolations,
                "a record no measure pass consumed must be detected",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun theCapTripLogsOneLineWithoutAStackTrace(): Unit = runBlocking {
        // The trip is a diagnostic, not a crash. On desktop it prints its message once, without
        // the stack of the frame that happened to trip it.
        val captured = java.io.ByteArrayOutputStream()
        val previousErr = System.err
        System.setErr(java.io.PrintStream(captured, true))
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(2)
        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            ancestor.hosting.setRecomposeGate { true } // never settles
            ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
            val nested = Composition(UnitApplier(), contextHolder[0]!!).also { holders[1] = it }
            nested.hosting.setRecomposeGate { false }
            nested.setContent { state.value }

            state.value = 1
            Snapshot.sendApplyNotifications()
            var frame = 0L
            repeat(62) {
                recomposer.invalidate(ancestor as ControlledComposition)
                frameClock.sendFrame(++frame)
            }
        } finally {
            System.setErr(previousErr)
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
        val output = captured.toString()
        assertEquals(
            1,
            Regex("held back behind an enclosing composition").findAll(output).count(),
            "the cap trip must print its message once; got: $output",
        )
        assertFalse(output.contains("\tat "), "the cap trip must not print a stack trace")
    }

    @Test
    fun anUnrecoveredCompositionErrorClearsTheRecord(): Unit = runBlocking {
        // Outside recovery mode a compose that throws at measure time leaves the recomposer in
        // its error state, which runs no more frames. So no frame start clears the record. It
        // must be cleared with the error, or every slot under a stale entry stays held.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(3)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            ancestor.hosting.setRecomposeGate { true }
            ancestor.setContent {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            val nested = Composition(UnitApplier(), contextHolder[0]!!).also { holders[1] = it }
            nested.hosting.setRecomposeGate { true }
            nested.hosting.setRefreshRequest {}
            nested.setContent {}

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertTrue(nested.hosting.deferToEnclosingComposition(), "sanity: the ancestor is pending")

            // Another host's measure-time compose fails, and the error is not recovered.
            val failing = Composition(UnitApplier(), recomposer).also { holders[2] = it }
            assertFailsWith<IllegalStateException> {
                failing.setContent { throw IllegalStateException("a failing measure-time compose") }
            }

            assertFalse(
                nested.hosting.deferToEnclosingComposition(),
                "an unrecovered composition error must clear the record",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun theNextFrameClearsTheRecord(): Unit = runBlocking {
        // An ancestor that did not compose in its frame must not hold a slot in a later frame, in
        // which wave 2 did not hold it back again.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        val other = mutableStateOf(0)
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(3)

        try {
            val ancestor = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            ancestor.hosting.setRecomposeGate { true }
            ancestor.setContent {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            val nested = Composition(UnitApplier(), contextHolder[0]!!).also { holders[1] = it }
            nested.hosting.setRecomposeGate { true }
            nested.hosting.setRefreshRequest {}
            nested.setContent {}
            val unrelated = Composition(UnitApplier(), recomposer).also { holders[2] = it }
            unrelated.setContent { other.value }

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertTrue(nested.hosting.deferToEnclosingComposition())

            other.value = 1 // a frame for an unrelated reason
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(2L)
            assertFalse(
                nested.hosting.deferToEnclosingComposition(),
                "the record from frame 1 must not hold in frame 2",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun disposingAPendingEnclosingCompositionReleasesItsWaiters(): Unit = runBlocking {
        // An overlay's anchor can go away while the overlay's slot waits for it. The slot must
        // not stay held until the fallback's next frame.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val state = mutableStateOf(0)
        var refreshRequests = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val nestedHolder = arrayOfNulls<Composition>(1)

        try {
            val ancestor = Composition(UnitApplier(), recomposer)
            ancestor.hosting.setRecomposeGate { true }
            ancestor.setContent {
                state.value
                contextHolder[0] = rememberCompositionContext()
            }
            val nested =
                Composition(UnitApplier(), contextHolder[0]!!).also { nestedHolder[0] = it }
            nested.hosting.setRecomposeGate { false }
            nested.hosting.setRefreshRequest { refreshRequests++ }
            nested.setContent { state.value }

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(0, refreshRequests)

            ancestor.dispose()
            assertEquals(1, refreshRequests, "disposing the ancestor must release its waiter")
        } finally {
            nestedHolder[0]?.let { if (!it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }
}
