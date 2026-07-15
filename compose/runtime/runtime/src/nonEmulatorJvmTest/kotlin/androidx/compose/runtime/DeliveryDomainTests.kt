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
import androidx.compose.runtime.mock.BufferedTestDataSource
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A source whose reads record ambiently and whose writes invalidate; values live. */
private class TokenSource : DataSource {
    fun invalidate(token: Any) = invalidateDependants(setOf(token))

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

private fun isolatingHolder(context: DataSourceContext = DataSourceContext()): SnapshotHolder =
    SnapshotHolder(context, isolating = true).also { it.activate() }

@OptIn(InternalComposeApi::class)
class DeliveryDomainTests {

    // Vector 1: per-domain timing - A's rotation delivers to A only; B delivers at B's.
    @Test
    fun eachDomainDeliversAtItsOwnRotation() {
        val state = mutableStateOf(0)
        val a = isolatingHolder()
        val b = isolatingHolder()
        val seenByA = mutableListOf<Set<Any>>()
        val seenByB = mutableListOf<Set<Any>>()
        val ha = a.registerApplyObserver { changed, _ -> seenByA.add(changed) }
        val hb = b.registerApplyObserver { changed, _ -> seenByB.add(changed) }
        try {
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
            assertTrue(seenByA.none { state in it })
            assertTrue(seenByB.none { state in it })
            a.rotate()
            assertEquals(1, seenByA.count { state in it }) // A delivered at A's rotation
            assertTrue(seenByB.none { state in it }) // B still pending
            assertTrue(b.hasPendingDelivery)
            b.rotate()
            assertEquals(1, seenByB.count { state in it })
        } finally {
            ha.dispose()
            hb.dispose()
            a.close()
            b.close()
        }
    }

    // Vector 2: own-cycle - a commit from A's slice is immediate for A and global,
    // pending for B (and there is NO echo back to A at any later rotation).
    @Test
    fun ownCycleCommitsAreImmediateForTheCommitterOnly() {
        val state = mutableStateOf(0)
        val a = isolatingHolder()
        val b = isolatingHolder()
        val seenByA = mutableListOf<Set<Any>>()
        val seenByB = mutableListOf<Set<Any>>()
        val seenGlobally = mutableListOf<Set<Any>>()
        val ha = a.registerApplyObserver { changed, _ -> seenByA.add(changed) }
        val hb = b.registerApplyObserver { changed, _ -> seenByB.add(changed) }
        val hg = Snapshot.registerApplyObserver { changed, _ -> seenGlobally.add(changed) }
        try {
            (a.current as DataSourceContext.Snapshot).withTransaction { state.value = 1 }
            assertEquals(1, seenByA.count { state in it }) // committer: immediate
            assertEquals(1, seenGlobally.count { state in it }) // global: immediate
            assertTrue(seenByB.none { state in it }) // sibling: pending
            b.rotate()
            assertEquals(1, seenByB.count { state in it })
            a.rotate() // must NOT echo the own commit back to A
            assertEquals(1, seenByA.count { state in it })
        } finally {
            ha.dispose()
            hb.dispose()
            hg.dispose()
            a.close()
            b.close()
        }
    }

    // Vector 3: global consumers are immediate even while domains are pinned.
    @Test
    fun globalObserversAreImmediateWhileDomainsArePinned() {
        val state = mutableStateOf(0)
        val a = isolatingHolder()
        val seenGlobally = mutableListOf<Set<Any>>()
        val hg = Snapshot.registerApplyObserver { changed, _ -> seenGlobally.add(changed) }
        try {
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
            assertEquals(1, seenGlobally.count { state in it }) // no parking for globals
        } finally {
            hg.dispose()
            a.close()
        }
    }

    // Vector 4: delivered ⊆ visible - a token pushed AFTER the union swap is delivered at
    // the NEXT rotation, when the value is visible; the scope is never permanently stale.
    // (Deterministic form: push between two rotations and assert it lands in the second.)
    @Test
    fun tokensRacingARotationDeliverAtTheNextOne() {
        val state = mutableStateOf(0)
        val a = isolatingHolder()
        val seenByA = mutableListOf<Set<Any>>()
        val ha = a.registerApplyObserver { changed, _ -> seenByA.add(changed) }
        try {
            a.rotate() // empty union: delivers nothing
            assertTrue(seenByA.isEmpty())
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
            a.rotate() // pushed before this swap -> delivered here, value visible here
            assertEquals(1, seenByA.count { state in it })
            (a.current as DataSourceContext.Snapshot).withTransaction {
                assertEquals(1, state.value) // delivered ⊆ visible
            }
        } finally {
            ha.dispose()
            a.close()
        }
    }

    // Vector 5: frozen-domain boundedness - a domain that never rotates does not block
    // anyone and its union is bounded by DISTINCT tokens under sustained commits.
    @Test
    fun aFrozenDomainBlocksNobodyAndStaysBounded() {
        val state = mutableStateOf(0)
        val frozen = isolatingHolder()
        val live = isolatingHolder()
        val seenByLive = mutableListOf<Set<Any>>()
        val hl = live.registerApplyObserver { changed, _ -> seenByLive.add(changed) }
        try {
            repeat(1000) { n -> thread { Snapshot.withMutableSnapshot { state.value = n } }.join() }
            live.rotate()
            assertEquals(1, seenByLive.count { state in it }) // live domain delivered freely
            assertTrue(frozen.hasPendingDelivery) // frozen still pending...
            // ...but bounded: 1000 commits to ONE object = one distinct token.
            assertEquals(1, frozen.pendingDeliveryCountForTest())
        } finally {
            hl.dispose()
            frozen.close()
            live.close()
        }
    }

    // Vector 6: wake fires per domain, on push.
    @Test
    fun theWakeCallbackFiresOnPush() {
        val state = mutableStateOf(0)
        val a = isolatingHolder()
        var woken = 0
        a.onPendingDelivery = { woken++ }
        try {
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
            assertTrue(woken >= 1)
            val wokenBeforeRotate = woken
            a.rotate()
            assertEquals(wokenBeforeRotate, woken) // delivery itself does not re-wake
        } finally {
            a.close()
        }
    }

    // Vector 7: close drops the union without delivering and without global effects.
    @Test
    fun closeDropsThePendingUnion() {
        val state = mutableStateOf(0)
        val a = isolatingHolder()
        val seenByA = mutableListOf<Set<Any>>()
        val ha = a.registerApplyObserver { changed, _ -> seenByA.add(changed) }
        try {
            thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
            assertTrue(a.hasPendingDelivery)
            a.close()
            assertTrue(seenByA.isEmpty()) // dropped, not delivered
            assertFalse(a.hasPendingDelivery)
        } finally {
            ha.dispose()
        }
    }

    // Vector 9: the render-path pump rotates a scene-less domain whose async channel is
    // starved, collapsing its pin's record retention and delivering its pending union.
    @Test
    fun theRenderPathPumpRotatesAStarvedDomain(): Unit =
        kotlinx.coroutines.runBlocking {
            val state = mutableStateOf(0)
            // A domain whose rotation task can never run: dispatched onto a queue nobody drains.
            val starved =
                object : kotlinx.coroutines.CoroutineDispatcher() {
                    val queue = ArrayDeque<Runnable>()

                    override fun dispatch(
                        context: kotlin.coroutines.CoroutineContext,
                        block: Runnable,
                    ) {
                        queue.add(block)
                    }
                }
            val domain =
                DataSourceCompositionDomain(
                    dataSourceContext = DataSourceContext(),
                    isolating = true,
                    coroutineScope = this,
                    dispatcher = starved,
                )
            val seen = mutableListOf<Set<Any>>()
            // Reach the domain's observers through its recomposer context element.
            val holder =
                domain.recomposerContext[androidx.compose.runtime.internal.SnapshotHolder]!!
            val handle = holder.registerApplyObserver { changed, _ -> seen.add(changed) }
            try {
                pumpScenelessDomainRotations() // nothing due: a no-op
                thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
                assertTrue(seen.none { state in it }) // pending; the async task cannot run
                pumpScenelessDomainRotations() // the render path performs the due swap
                assertEquals(1, seen.count { state in it }) // delivered by the pumped rotation
            } finally {
                handle.dispose()
                domain.close()
                // Drain the starved queue so runBlocking's children complete (the queued
                // rotation task runs as a no-op: the pump already consumed the due flag).
                while (starved.queue.isNotEmpty()) starved.queue.removeFirst().run()
            }
        }

    // Vector 8: source tokens reach domains whose context does NOT contain the source.
    @Test
    fun sourceTokensReachNonMemberDomains() {
        val source = TokenSource()
        val nonMember = isolatingHolder(DataSourceContext()) // source NOT in this context
        val seen = mutableListOf<Set<Any>>()
        val h = nonMember.registerApplyObserver { changed, _ -> seen.add(changed) }
        try {
            source.invalidate("t")
            assertTrue(seen.none { "t" in it }) // pending, not lost
            nonMember.rotate()
            assertEquals(1, seen.count { "t" in it }) // delivery is universal
        } finally {
            h.dispose()
            nonMember.close()
        }
    }

    // Vector 10: the drain must run BEFORE the pin rotation, so a token delivered by that
    // rotation is already visible in the successor's view (`delivered ⊆ visible`). Reversing
    // the two would leave the reader permanently stale.
    @Test
    fun drainingBeforeRotationKeepsDeliveredTokensVisible() {
        val source = BufferedTestDataSource()
        val context = DataSourceContext(source)
        val holder = isolatingHolder(context)
        val seen = mutableListOf<Set<Any>>()
        val h = holder.registerApplyObserver { changed, _ -> seen.add(changed) }
        try {
            source.write("k", 7)
            // The span-boundary drain: publishes the new version and delivers its identifiers.
            context.advanceGlobalSnapshot()
            assertTrue(seen.none { "k" in it }, "pending for this domain, not yet delivered")
            holder.rotate()
            assertEquals(1, seen.count { "k" in it }, "delivered at this domain's rotation")
            (holder.current as DataSourceContext.Snapshot).withTransaction {
                assertEquals(7, source.read("k"), "and already visible: delivered subset visible")
            }
        } finally {
            h.dispose()
            holder.close()
        }
    }

    // The scene-less domain must drain its context as part of its own rotation, and its
    // rotation must be reachable from the context's wake.
    @Test
    fun aScenelessDomainDrainsItsContextWhenItRotates(): Unit =
        kotlinx.coroutines.runBlocking {
            val source = BufferedTestDataSource()
            val context = DataSourceContext(source)
            val starved =
                object : kotlinx.coroutines.CoroutineDispatcher() {
                    val queue = ArrayDeque<Runnable>()

                    override fun dispatch(
                        context: kotlin.coroutines.CoroutineContext,
                        block: Runnable,
                    ) {
                        queue.add(block)
                    }
                }
            val domain =
                DataSourceCompositionDomain(
                    dataSourceContext = context,
                    isolating = true,
                    coroutineScope = this,
                    dispatcher = starved,
                )
            try {
                source.write("k", 3)
                // The context's wake reaches the domain, which marks a rotation due.
                context.scheduleAdvance()
                assertTrue(context.hasPendingAdvance)
                // The render-path pump performs the due rotation, which drains first.
                pumpScenelessDomainRotations()
                assertFalse(context.hasPendingAdvance, "the rotation must have drained the context")
                assertEquals(3, source.publishedValue("k"))
            } finally {
                domain.close()
                while (starved.queue.isNotEmpty()) starved.queue.removeFirst().run()
            }
        }
}
