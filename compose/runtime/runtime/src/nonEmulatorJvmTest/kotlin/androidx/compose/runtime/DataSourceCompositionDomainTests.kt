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

import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.snapshots.Snapshot
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * A minimal observable key-value source: reads record dependencies through the ambient
 * recorder chain, writes invalidate through the source-scoped parking machinery. Values are
 * read live (this source does not pin its own values; substrate pinning is what the domain
 * tests exercise).
 */
private class RecordingSource : DataSource {
    private val values = ConcurrentHashMap<String, Int>()

    fun read(key: String): Int {
        DataSource.recordDependency(key)
        return values[key] ?: 0
    }

    fun write(key: String, value: Int) {
        values[key] = value
        invalidateDependants(setOf(key))
    }

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

/** Queues dispatches so tests control exactly when domain slices and rotations run. */
private class ManualDispatcher : CoroutineDispatcher() {
    val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.add(block)
    }

    fun runNext() = queue.removeFirst().run()

    fun flush() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }
}

@OptIn(InternalComposeApi::class)
class DataSourceCompositionDomainTests {

    /** One harness per test: domain + recomposer + composition, torn down in order. */
    private fun runDomain(
        sources: List<DataSource> = emptyList(),
        isolating: Boolean,
        body: Harness.() -> Unit,
    ): Unit = runBlocking {
        val manual = ManualDispatcher()
        val domain =
            DataSourceCompositionDomain(
                dataSourceContext = DataSourceContext(*sources.toTypedArray()),
                isolating = isolating,
                coroutineScope = this,
                dispatcher = manual,
            )
        val frameClock = BroadcastFrameClock()
        val recomposer =
            Recomposer(
                coroutineContext + Dispatchers.Unconfined + frameClock + domain.recomposerContext
            )
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val composition = Composition(UnitApplier(), recomposer)
        try {
            Harness(this, domain, manual, frameClock, composition).body()
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
            domain.close()
        }
    }

    private class Harness(
        val scope: CoroutineScope,
        val domain: DataSourceCompositionDomain,
        val manual: ManualDispatcher,
        val frameClock: BroadcastFrameClock,
        val composition: Composition,
    ) {
        fun frame(n: Long) {
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(n)
        }
    }

    // Vector 1: read tracking - the domain composition recomposes on source invalidation.
    @Test
    fun domainCompositionRecomposesOnSourceInvalidation() {
        val source = RecordingSource()
        runDomain(listOf(source), isolating = false) {
            var composed = 0
            var seen = -1
            composition.setContent {
                composed++
                seen = source.read("k")
            }
            assertEquals(1, composed)
            assertEquals(0, seen)
            source.write("k", 42) // no pins open between passes: immediate dispatch
            frame(1L)
            assertEquals(2, composed)
            assertEquals(42, seen)
        }
    }

    // Vector 2: tracking precision - unread tokens do not recompose.
    @Test
    fun unreadTokensDoNotRecompose() {
        val source = RecordingSource()
        runDomain(listOf(source), isolating = false) {
            var composed = 0
            composition.setContent {
                composed++
                source.read("k")
            }
            source.write("other", 7)
            frame(1L)
            assertEquals(1, composed)
        }
    }

    // Vector 8: the boundary - a recomposer with NO domain does not track source reads.
    @Test
    fun withoutADomainSourceReadsAreNotTracked(): Unit = runBlocking {
        val source = RecordingSource()
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        var composed = 0
        val composition = Composition(UnitApplier(), recomposer)
        try {
            composition.setContent {
                composed++
                source.read("k")
            }
            source.write("k", 42)
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(1, composed) // intentionally frozen: the stock branch stays stock
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    // Vector 3: derived state over a source recomputes on invalidation. If this exposes a
    // derived-cache staleness gap (the source's published store is not snapshot-backed),
    // do NOT paper over it - record the actual behavior and flag it in the task report.
    @Test
    fun derivedStateOverASourceRecomputes() {
        val source = RecordingSource()
        runDomain(listOf(source), isolating = false) {
            var seen = -1
            val derived = derivedStateOf { source.read("k") + 100 }
            composition.setContent { seen = derived.value }
            assertEquals(100, seen)
            source.write("k", 1)
            frame(1L)
            assertEquals(101, seen)
        }
    }

    // Vector 4 (flag off): a recompose pass is internally atomic against external commits.
    @Test
    fun aPassIsAtomicAgainstMidPassExternalCommits() {
        runDomain(isolating = false) {
            val observed = mutableStateOf(0)
            var midPass = -1
            var endOfPass = -1
            composition.setContent {
                midPass = observed.value
                // An external commit lands mid-pass...
                thread { Snapshot.withMutableSnapshot { observed.value = 1 } }.join()
                // ...and must stay invisible to the rest of this pass.
                endOfPass = observed.value
            }
            assertEquals(0, midPass)
            assertEquals(0, endOfPass)
            assertEquals(1, observed.value) // visible after the pass unit disposed
        }
    }

    // Vector 4b (flag off): an effect task is one atomic slice.
    @Test
    fun anEffectTaskIsOneAtomicSlice() {
        runDomain(isolating = false) {
            val observed = mutableStateOf(0)
            var first = -1
            var second = -1
            composition.setContent {
                LaunchedEffect(Unit) {
                    first = observed.value
                    thread { Snapshot.withMutableSnapshot { observed.value = 1 } }.join()
                    second = observed.value
                }
            }
            manual.flush() // the queued effect task runs as one context.isolate slice
            assertEquals(0, first)
            assertEquals(0, second) // pinned for the whole resumption
            assertEquals(1, observed.value)
        }
    }

    // Vector 5 (flag on): slices share one generation until rotation.
    @Test
    fun flagOnSlicesShareOneGenerationUntilRotation() {
        // Created BEFORE the domain: the standing unit's pinned view must include the state
        // object itself (in real usage states are created inside the domain's slices).
        val observed = mutableStateOf(0)
        runDomain(isolating = true) {
            composition.setContent { observed.value }
            var taskSaw = -1
            // Queue a task slice FIRST, then commit externally, then run only the task:
            scope.launch(domain.recomposerContext) { taskSaw = observed.value }
            thread { Snapshot.withMutableSnapshot { observed.value = 1 } }.join()
            assertEquals(1, observed.value) // world-visible...
            manual.runNext() // ...but the task slice joins the standing generation
            assertEquals(0, taskSaw)
            manual.flush() // the rotation the parked commit scheduled
        }
    }

    // Vector 6 (flag on): parking + wake-up rotation + release drive recomposition.
    @Test
    fun aPendingInvalidationIsDeliveredByTheScheduledRotation() {
        // See flagOnSlicesShareOneGenerationUntilRotation for why the state predates the domain.
        val observed = mutableStateOf(0)
        runDomain(isolating = true) {
            var composed = 0
            var seen = -1
            composition.setContent {
                composed++
                seen = observed.value
            }
            assertEquals(1, composed)
            thread { Snapshot.withMutableSnapshot { observed.value = 1 } }.join()
            frame(1L)
            assertEquals(1, composed) // pending for this domain: no recompose yet
            assertTrue(manual.queue.isNotEmpty()) // onPendingDelivery woke a rotation task
            manual.flush() // rotation releases the batch
            frame(2L)
            assertEquals(2, composed)
            assertEquals(1, seen)
        }
    }

    // Vector 7: teardown with a pending union outstanding loses nothing and does not
    // throw; global observers already saw the commit (they are always immediate now), and
    // work queued after close falls back to the stock path.
    @Test
    fun closeDropsThePendingUnionAndLateWorkFallsBackToStock(): Unit = runBlocking {
        val manual = ManualDispatcher()
        val domain =
            DataSourceCompositionDomain(
                dataSourceContext = DataSourceContext(),
                isolating = true,
                coroutineScope = this,
                dispatcher = manual,
            )
        val observed = mutableStateOf(0)
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        try {
            thread { Snapshot.withMutableSnapshot { observed.value = 1 } }.join()
            // Global: immediate, even though this same commit is now sitting in the
            // domain's own pending union (which nothing here is registered to observe).
            assertEquals(1, notified.count { observed in it })
            domain.close() // drops the pending union; does not throw, delivers nothing more
            assertEquals(1, notified.count { observed in it }) // unchanged: dropped, not delivered
            var ran = false
            launch(domain.recomposerContext) { ran = true }
            // Flushes the stale rotation task the earlier commit's onPendingDelivery wake
            // had queued (now a no-op: the domain is closed) and the late task above.
            manual.flush() // late task runs on the stock fallback, no crash
            assertTrue(ran)
        } finally {
            handle.dispose()
        }
    }

    // Vector 9: two domains sharing the SAME underlying context keep per-domain own-cycle
    // attribution - each domain's own commit is immediate for it, pending for the other,
    // and never echoed back to the committer at its own later rotation.
    @Test
    fun domainsOfASharedContextKeepPerDomainOwnCycleAttribution() {
        val source = RecordingSource()
        val shared = DataSourceContext(source)
        val a = SnapshotHolder(shared, isolating = true).also { it.activate() }
        val b = SnapshotHolder(shared, isolating = true).also { it.activate() }
        val seenByA = mutableListOf<Set<Any>>()
        val seenByB = mutableListOf<Set<Any>>()
        val ha = a.registerApplyObserver { changed, _ -> seenByA.add(changed) }
        val hb = b.registerApplyObserver { changed, _ -> seenByB.add(changed) }
        try {
            (a.current as DataSourceContext.Snapshot).withTransaction {
                source.invalidateDependants(setOf("t"))
            }
            assertEquals(1, seenByA.count { "t" in it }) // A's own cycle: immediate
            assertTrue(seenByB.none { "t" in it }) // B: pending, not lost
            b.rotate()
            assertEquals(1, seenByB.count { "t" in it }) // released at B's own rotation
            a.rotate() // must NOT echo A's own commit back to A
            assertEquals(1, seenByA.count { "t" in it })
        } finally {
            ha.dispose()
            hb.dispose()
            a.close()
            b.close()
        }
    }

    // theRenderPathPumpRotatesWhenTheAsyncChannelIsStarved: removed - the render-path pump
    // it exercised was deleted with the per-consumer delivery rework (rotation is now
    // wake-driven via SnapshotHolder.onPendingDelivery; see
    // DeliveryDomainTests.theWakeCallbackFiresOnPush and
    // DataSourceCompositionDomainTests.aPendingInvalidationIsDeliveredByTheScheduledRotation).
}
