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
 * The recomposer's side of layout-driven compositions: a composition that has a host, or an
 * enclosing composition with one, is handed to a host instead of being recomposed in the
 * recomposer's recompose block, and the host composes it later, in its layout pass.
 *
 * These tests pin the runtime's side only: which host the recomposer asks, in which order, what a
 * true or a false answer does, and what [ParentDrivenHosting.recomposeNow] and
 * [ParentDrivenHosting.recomposeLater] do when a host calls them. When a host composes what it
 * took, and in which order, belongs to the host and is tested with `SubcomposeLayout`. A
 * [RecordingHost] stands in for the host. A test stands in for a host's measure the way
 * `SubcomposeLayout` runs it: `setContent` on the composition. A test that delivers a waiter calls
 * `recomposeNow()` itself, as a host does.
 */
// A test stands in for the host, so it reaches the composition's side the way a host does.
@OptIn(InternalComposeApi::class)
private val Composition.hosting: ParentDrivenHosting
    get() = (this as CompositionServices).getCompositionService(ParentDrivenHostingKey)!!

/**
 * A host that answers as configured and records what the recomposer tells it.
 *
 * @param takes what [onInvalidated] answers, read at each call, as a host reads its node's state.
 * @param takesWaiters what [onWaiterInvalidated] answers.
 */
@OptIn(InternalComposeApi::class)
private class RecordingHost(
    val name: String = "host",
    var takes: () -> Boolean = { false },
    var takesWaiters: Boolean = true,
    private val log: MutableList<String>? = null,
) : ParentDrivenHost {
    var invalidatedCalls = 0
        private set

    /** Every waiter [onWaiterInvalidated] was called with, in call order. */
    val waiters = mutableListOf<ParentDrivenHosting>()

    /** The `enclosingRecomposed` of every [onInvalidated] call, in call order. */
    val invalidatedWith = mutableListOf<Boolean>()

    /** The `enclosingRecomposed` of every [onWaiterInvalidated] call, in call order. */
    val waiterInvalidatedWith = mutableListOf<Boolean>()

    var disposed = false
        private set

    /** Runs inside [onInvalidated], before it answers. */
    var whenInvalidated: () -> Unit = {}

    /** Runs inside [onDisposed], so a test can look at the recomposer at that moment. */
    var whenDisposed: () -> Unit = {}

    override fun onInvalidated(enclosingRecomposed: Boolean): Boolean {
        invalidatedCalls++
        invalidatedWith += enclosingRecomposed
        log?.add("$name.onInvalidated")
        whenInvalidated()
        return takes()
    }

    override fun onWaiterInvalidated(
        waiter: ParentDrivenHosting,
        enclosingRecomposed: Boolean,
    ): Boolean {
        waiters += waiter
        waiterInvalidatedWith += enclosingRecomposed
        log?.add("$name.onWaiterInvalidated")
        return takesWaiters
    }

    override fun onDisposed() {
        disposed = true
        whenDisposed()
    }
}

@OptIn(InternalComposeApi::class)
private fun Composition.installRecordingHost(
    name: String = "host",
    takes: () -> Boolean = { false },
    takesWaiters: Boolean = true,
    log: MutableList<String>? = null,
): RecordingHost =
    RecordingHost(name, takes, takesWaiters, log).also { hosting.installHost(it) }

/** A running recomposer, and the compositions a test creates on it, disposed in reverse. */
private class Env(val recomposer: Recomposer, private val frameClock: BroadcastFrameClock) {
    private val compositions = mutableListOf<Composition>()
    private var frameTime = 0L

    fun composition(parent: CompositionContext = recomposer): Composition =
        Composition(UnitApplier(), parent).also { compositions += it }

    /** Sends the pending snapshot changes, and runs one frame. */
    fun frame() {
        Snapshot.sendApplyNotifications()
        frameClock.sendFrame(++frameTime)
    }

    fun disposeAll() {
        compositions.asReversed().forEach { if (!it.isDisposed) it.dispose() }
    }
}

@OptIn(InternalComposeApi::class)
private fun runWithRecomposer(resilient: Boolean = false, block: Env.() -> Unit): Unit =
    runBlocking {
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        if (resilient) recomposer.setResilientModeEnabled(true)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val env = Env(recomposer, frameClock)
        try {
            env.block()
        } finally {
            env.disposeAll()
            recomposer.cancel()
            runner.join()
        }
    }

@OptIn(InternalComposeApi::class)
class ParentDrivenRecomposeGateTests {
    @Test
    fun aTakenCompositionIsLeftToItsHostWhichReRunsIt() = runWithRecomposer {
        val state = mutableStateOf(0)
        var composed = 0
        var seen = -1
        val composition = composition()
        val host = composition.installRecordingHost(takes = { true })
        val content: @Composable () -> Unit = {
            composed++
            seen = state.value
        }
        composition.setContent(content)
        assertEquals(1, composed)

        state.value = 1
        frame()
        assertEquals(1, host.invalidatedCalls, "the host is asked instead of recomposing")
        assertEquals(1, composed, "a taken composition is not recomposed in the frame")
        assertFalse(recomposer.hasPendingWork, "and it asks for no further frame")
        assertTrue(composition.hasInvalidations, "its invalidation is left for the host")

        // The host's measure re-runs it, as SubcomposeLayout does.
        composition.setContent(content)
        assertEquals(2, composed)
        assertEquals(1, seen)
    }

    @Test
    fun aDeclinedCompositionRecomposesInTheSameFrame() = runWithRecomposer {
        val state = mutableStateOf(0)
        var seen = -1
        val composition = composition()
        val host = composition.installRecordingHost(takes = { false })
        composition.setContent { seen = state.value }

        state.value = 1
        frame()
        assertEquals(1, host.invalidatedCalls)
        assertEquals(1, seen, "a declined composition recomposes as any composition does")
        assertFalse(recomposer.hasPendingWork)
    }

    @Test
    fun theHostIsAskedAfterTheEnclosingCompositionRecomposedAndBeforeTheApply() =
        runWithRecomposer {
            // The enclosing composition recomposes first, in depth order, so the content it
            // supplies is recomposed by the time the host is asked. Its apply, which installs that
            // content with the host, runs after the whole recompose block, so the host is asked
            // before it: it must not depend on what the apply does.
            val state = mutableStateOf(0)
            val contextHolder = arrayOfNulls<CompositionContext>(1)
            var ancestorComposed = 0
            var ancestorApplied = 0
            var composedWhenAsked = -1
            var appliedWhenAsked = -1
            val ancestor = composition()
            ancestor.setContent {
                ancestorComposed++
                state.value
                contextHolder[0] = rememberCompositionContext()
                SideEffect { ancestorApplied++ }
            }
            val nested = composition(contextHolder[0]!!)
            val host = nested.installRecordingHost(takes = { true })
            host.whenInvalidated = {
                composedWhenAsked = ancestorComposed
                appliedWhenAsked = ancestorApplied
            }
            nested.setContent { state.value }

            state.value = 1
            frame()
            assertEquals(2, composedWhenAsked, "the enclosing composition recomposed first")
            assertEquals(1, appliedWhenAsked, "the host is asked before the enclosing apply")
            assertEquals(2, ancestorApplied, "sanity: the enclosing composition applied")
        }

    @Test
    fun aCompositionWithoutAHostGoesToTheNearestEnclosingHost() = runWithRecomposer {
        // hosted -> gateless -> leaf: the leaf has no host, and neither has the composition
        // between. Only the leaf changes.
        val state = mutableStateOf(0)
        var leafComposed = 0
        val outerContext = arrayOfNulls<CompositionContext>(1)
        val middleContext = arrayOfNulls<CompositionContext>(1)
        val hosted = composition()
        val host = hosted.installRecordingHost(takes = { true })
        hosted.setContent { outerContext[0] = rememberCompositionContext() }
        val middle = composition(outerContext[0]!!)
        middle.setContent { middleContext[0] = rememberCompositionContext() }
        val leaf = composition(middleContext[0]!!)
        leaf.setContent {
            leafComposed++
            state.value
        }

        state.value = 1
        frame()
        assertEquals(listOf(leaf.hosting), host.waiters, "the leaf waits for the hosted one")
        assertEquals(0, host.invalidatedCalls, "the hosted composition has nothing to compose")
        assertEquals(1, leafComposed, "a taken waiter is not recomposed in the frame")
        assertFalse(recomposer.hasPendingWork)

        assertTrue(leaf.hosting.recomposeNow())
        assertEquals(2, leafComposed, "the host delivers it with recomposeNow")
    }

    @Test
    fun aCompositionItsOwnHostDeclinesWaitsForAnEnclosingHost() = runWithRecomposer {
        // A slot whose own host will not measure it, nested in a slot whose host will. Composing
        // it now could compose it ahead of the enclosing slot, so it waits for that one.
        val state = mutableStateOf(0)
        var nestedComposed = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val log = mutableListOf<String>()
        val ancestor = composition()
        val ancestorHost = ancestor.installRecordingHost("ancestor", takes = { true }, log = log)
        ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
        val nested = composition(contextHolder[0]!!)
        val nestedHost = nested.installRecordingHost("nested", takes = { false }, log = log)
        nested.setContent {
            nestedComposed++
            state.value
        }

        state.value = 1
        frame()
        assertEquals(listOf("nested.onInvalidated", "ancestor.onWaiterInvalidated"), log)
        assertEquals(listOf(nested.hosting), ancestorHost.waiters)
        assertEquals(1, nestedHost.invalidatedCalls)
        assertEquals(1, nestedComposed, "it waits for the enclosing host")
    }

    @Test
    fun aWaiterEveryHostDeclinesRecomposesNow() = runWithRecomposer {
        // Each enclosing host is asked, nearest first, and one that declines passes the question
        // on. If none takes the waiter, it recomposes in the frame.
        val state = mutableStateOf(0)
        var leafSeen = -1
        val outerContext = arrayOfNulls<CompositionContext>(1)
        val innerContext = arrayOfNulls<CompositionContext>(1)
        val log = mutableListOf<String>()
        val outer = composition()
        outer.installRecordingHost("outer", takesWaiters = false, log = log)
        outer.setContent { outerContext[0] = rememberCompositionContext() }
        val inner = composition(outerContext[0]!!)
        inner.installRecordingHost("inner", takesWaiters = false, log = log)
        inner.setContent { innerContext[0] = rememberCompositionContext() }
        val leaf = composition(innerContext[0]!!)
        leaf.setContent { leafSeen = state.value }

        state.value = 1
        frame()
        assertEquals(listOf("inner.onWaiterInvalidated", "outer.onWaiterInvalidated"), log)
        assertEquals(1, leafSeen, "a waiter no host takes recomposes in the frame")
    }

    @Test
    fun waitersAreHandedOverInAscendingDepth() = runWithRecomposer {
        // The host releases its waiters in the order it got them, so a waiter nested in another
        // one must come after it.
        val state = mutableStateOf(0)
        val contexts = arrayOfNulls<CompositionContext>(2)
        val hosted = composition()
        val host = hosted.installRecordingHost(takes = { true })
        hosted.setContent { contexts[0] = rememberCompositionContext() }
        val middle = composition(contexts[0]!!)
        middle.setContent {
            state.value
            contexts[1] = rememberCompositionContext()
        }
        val leaf = composition(contexts[1]!!)
        leaf.setContent { state.value }

        state.value = 1
        frame()
        assertEquals(listOf(middle.hosting, leaf.hosting), host.waiters)
    }

    @Test
    fun anEnclosingCompositionQueuedWithNothingToRecomposeIsNotHandedOver() = runWithRecomposer {
        // Invalidating a nested composition's scope queues every composition enclosing it too.
        // One of those with no invalidation of its own must not cost its host a measure.
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val scopeHolder = arrayOfNulls<RecomposeScope>(1)
        var nestedComposed = 0
        val hosted = composition()
        val host = hosted.installRecordingHost(takes = { true })
        hosted.setContent { contextHolder[0] = rememberCompositionContext() }
        val nested = composition(contextHolder[0]!!)
        nested.setContent {
            nestedComposed++
            scopeHolder[0] = currentRecomposeScope
        }

        scopeHolder[0]!!.invalidate()
        frame()
        assertEquals(0, host.invalidatedCalls, "the enclosing composition had nothing to do")
        assertEquals(listOf(nested.hosting), host.waiters)
        assertEquals(1, nestedComposed)
    }

    @Test
    fun aCompositionItsEnclosingCompositionRemovesIsNotHandedOver() = runWithRecomposer {
        // An overlay that closes with the entity it shows, reduced to the runtime: the enclosing
        // composition's recompose drops the group that holds the nested composition's context, in
        // the same frame in which the nested composition would read the state that removed it. It is skipped for the rest of
        // the frame, and its host is not asked either.
        val entity = mutableStateOf<String?>("present")
        var staleReads = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestor = composition()
        ancestor.setContent {
            if (entity.value != null) contextHolder[0] = rememberCompositionContext()
        }
        val nested = composition(contextHolder[0]!!)
        val host = nested.installRecordingHost(takes = { false })
        nested.setContent { if (entity.value == null) staleReads++ }

        Snapshot.withMutableSnapshot { entity.value = null }
        frame()
        assertEquals(0, host.invalidatedCalls, "a removed composition's host is not asked")
        assertEquals(0, staleReads, "and it is not recomposed against the removal")
    }

    @Test
    fun aCompositionAloneInvalidatedIsOfferedWithNothingEnclosingRecomposed() =
        runWithRecomposer {
            // Only the nested composition reads the state, so nothing above it can give its host
            // new content: its host is told so, and a host that declines leaves it to recompose
            // in the frame, as stock Compose does.
            val state = mutableStateOf(0)
            var seen = -1
            val contextHolder = arrayOfNulls<CompositionContext>(1)
            val ancestor = composition()
            ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
            val nested = composition(contextHolder[0]!!)
            val host = nested.installRecordingHost(takes = { false })
            nested.setContent { seen = state.value }

            state.value = 1
            frame()
            assertEquals(listOf(false), host.invalidatedWith)
            assertEquals(1, seen, "a declined composition recomposes in the frame")
        }

    @Test
    fun anEnclosingCompositionThatRecomposedWithChangesIsReportedToTheHost() =
        runWithRecomposer {
            // Two levels up: the change of any composition enclosing it counts, because its apply
            // can install new content with any host between the two.
            val state = mutableStateOf(0)
            val contexts = arrayOfNulls<CompositionContext>(2)
            val outer = composition()
            outer.setContent {
                // A SideEffect gives every recompose a change to apply.
                val value = state.value
                SideEffect { value }
                contexts[0] = rememberCompositionContext()
            }
            val middle = composition(contexts[0]!!)
            middle.setContent { contexts[1] = rememberCompositionContext() }
            val nested = composition(contexts[1]!!)
            val host = nested.installRecordingHost(takes = { true })
            nested.setContent { state.value }

            state.value = 1
            frame()
            assertEquals(listOf(true), host.invalidatedWith)
        }

    @Test
    fun anEnclosingCompositionQueuedWithNothingToRecomposeIsNotReportedAsRecomposed() =
        runWithRecomposer {
            // Invalidating the nested composition's scope queues the enclosing one too. It
            // recomposes nothing, so it cannot give the host new content.
            val contextHolder = arrayOfNulls<CompositionContext>(1)
            val scopeHolder = arrayOfNulls<RecomposeScope>(1)
            val ancestor = composition()
            ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
            val nested = composition(contextHolder[0]!!)
            val host = nested.installRecordingHost(takes = { true })
            nested.setContent { scopeHolder[0] = currentRecomposeScope }

            scopeHolder[0]!!.invalidate()
            frame()
            assertEquals(listOf(false), host.invalidatedWith)
        }

    @Test
    fun anEnclosingCompositionTakenByItsHostIsReportedAsRecomposed() = runWithRecomposer {
        // A composition handed to the layout pass composes after this block and applies there,
        // so the ones nested in it must wait for it as for one that recomposed.
        val state = mutableStateOf(0)
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestor = composition()
        val ancestorHost = ancestor.installRecordingHost("ancestor", takes = { true })
        ancestor.setContent {
            state.value
            contextHolder[0] = rememberCompositionContext()
        }
        val nested = composition(contextHolder[0]!!)
        val nestedHost = nested.installRecordingHost("nested", takes = { true })
        nested.setContent { state.value }

        state.value = 1
        frame()
        assertEquals(listOf(false), ancestorHost.invalidatedWith)
        assertEquals(listOf(true), nestedHost.invalidatedWith)
    }

    @Test
    fun aCompositionReachedOnlyByAWriteDuringAnEnclosingComposeIsOfferedWithTheWriteRecorded() =
        runWithRecomposer {
            // The enclosing composition writes, while it composes, a state the nested one reads,
            // as `rememberUpdatedState` does. That write queues the nested composition after the
            // enclosing one without recording anything for it; it must still be offered, and a
            // host that takes it must find the write recorded, so its re-run delivers it.
            val state = mutableStateOf(0)
            val updated = mutableStateOf(0)
            val contextHolder = arrayOfNulls<CompositionContext>(1)
            var seen = -1
            var nestedComposed = 0
            val outer = composition()
            outer.setContent {
                val value = state.value
                updated.value = value
                SideEffect { value }
                contextHolder[0] = rememberCompositionContext()
            }
            val nested = composition(contextHolder[0]!!)
            val host = nested.installRecordingHost(takes = { true })
            var recordedWhenOffered = false
            host.whenInvalidated = { recordedWhenOffered = nested.hasInvalidations }
            val content: @Composable () -> Unit = {
                nestedComposed++
                seen = updated.value
            }
            nested.setContent(content)

            state.value = 1
            frame()
            assertEquals(listOf(true), host.invalidatedWith, "it is offered, after the outer one")
            assertTrue(recordedWhenOffered, "with the write recorded as its invalidation")
            assertEquals(1, nestedComposed, "a taken composition is not recomposed in the frame")
            assertFalse(recomposer.hasPendingWork, "and it asks for no further frame")

            nested.setContent(content)
            assertEquals(1, seen, "the host's re-run delivers the write")
        }

    @Test
    fun aQueuedCompositionWithNothingRecordedThatReadsAWriteEarlierInThePassIsOffered() =
        runWithRecomposer {
            // An invalidation of a composition nested deeper queues the middle one with nothing
            // recorded, in the same pass as the outer one. The outer one then writes, while it
            // composes, a state the middle one reads, before the pass reaches the middle one.
            val state = mutableStateOf(0)
            val updated = mutableStateOf(0)
            val contexts = arrayOfNulls<CompositionContext>(2)
            val deepScope = arrayOfNulls<RecomposeScope>(1)
            var middleComposed = 0
            val outer = composition()
            outer.setContent {
                val value = state.value
                updated.value = value
                SideEffect { value }
                contexts[0] = rememberCompositionContext()
            }
            val middle = composition(contexts[0]!!)
            val host = middle.installRecordingHost(takes = { true }, takesWaiters = false)
            middle.setContent {
                middleComposed++
                updated.value
                contexts[1] = rememberCompositionContext()
            }
            val deep = composition(contexts[1]!!)
            deep.setContent { deepScope[0] = currentRecomposeScope }

            state.value = 1
            deepScope[0]!!.invalidate()
            frame()
            assertEquals(listOf(true), host.invalidatedWith, "it is offered, after the outer one")
            assertEquals(1, middleComposed, "a taken composition is not recomposed in the frame")
            assertTrue(middle.hasInvalidations, "the write is left for its host")
            assertFalse(recomposer.hasPendingWork, "and it asks for no further frame")
        }

    @Test
    fun aWaiterIsOfferedWithWhetherAnEnclosingCompositionRecomposed() = runWithRecomposer {
        // The flag is computed for the waiter's own chain, so an enclosing host that is asked
        // for it hears the same answer as its own host would.
        val state = mutableStateOf(0)
        val other = mutableStateOf(0)
        val contexts = arrayOfNulls<CompositionContext>(2)
        val outer = composition()
        outer.setContent {
            val value = other.value
            SideEffect { value }
            contexts[0] = rememberCompositionContext()
        }
        val hosted = composition(contexts[0]!!)
        val host = hosted.installRecordingHost(takes = { false }, takesWaiters = false)
        hosted.setContent { contexts[1] = rememberCompositionContext() }
        val leaf = composition(contexts[1]!!)
        leaf.setContent { state.value }

        state.value = 1
        frame()
        assertEquals(listOf(false), host.waiterInvalidatedWith, "only the leaf changed")

        Snapshot.withMutableSnapshot {
            state.value = 2
            other.value = 1
        }
        frame()
        assertEquals(
            listOf(false, true),
            host.waiterInvalidatedWith,
            "the outermost composition changed with the leaf",
        )
    }

    @Test
    fun anAncestorRecomposesBeforeItsDescendant() = runWithRecomposer {
        // Compositions that no host takes recompose in ascending depth, whatever order they were
        // registered in, so a nested one never runs ahead of the one that supplies its content.
        val state = mutableStateOf(0)
        val log = mutableListOf<String>()
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestor = composition()
        ancestor.setContent {
            if (state.value > 0) log += "ancestor"
            contextHolder[0] = rememberCompositionContext()
        }
        val nested = composition(contextHolder[0]!!)
        nested.setContent { if (state.value > 0) log += "nested" }

        state.value = 1
        frame()
        assertEquals(listOf("ancestor", "nested"), log)
    }

    @Test
    fun recomposeNowRecomposesAndAppliesInPlace() = runWithRecomposer {
        // A waiter without a host has nothing that could re-run it. Its host releases it with
        // recomposeNow once the composition it waits for is current. So it sees what that
        // composition produced, and it applies, which runs its side effects.
        val state = mutableStateOf(0)
        val produced = intArrayOf(-1)
        var nestedComposed = 0
        var nestedSawProduced = -1
        var nestedSideEffects = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestor = composition()
        val host = ancestor.installRecordingHost(takes = { true })
        val ancestorContent: @Composable () -> Unit = {
            produced[0] = state.value
            contextHolder[0] = rememberCompositionContext()
        }
        ancestor.setContent(ancestorContent)
        val nested = composition(contextHolder[0]!!)
        nested.setContent {
            nestedComposed++
            nestedSawProduced = produced[0]
            state.value
            SideEffect { nestedSideEffects++ }
        }

        state.value = 1
        frame()
        assertEquals(1, nestedComposed, "sanity: the waiter was taken")
        assertEquals(listOf(nested.hosting), host.waiters)

        ancestor.setContent(ancestorContent)
        assertTrue(nested.hosting.recomposeNow(), "no error, so the release goes on")
        assertEquals(2, nestedComposed, "it recomposes in the call")
        assertEquals(1, nestedSawProduced, "and sees what the enclosing composition produced")
        assertEquals(2, nestedSideEffects, "and its changes are really applied")
        assertFalse(recomposer.hasPendingWork, "and it leaves no frame request behind")
    }

    @Test
    fun recomposeNowWithNothingToRecomposeComposesNothing() = runWithRecomposer {
        var composed = 0
        val composition = composition()
        composition.setContent { composed++ }
        assertTrue(composition.hosting.recomposeNow())
        assertEquals(1, composed)
        assertFalse(recomposer.hasPendingWork)
    }

    @Test
    fun recomposeNowWithdrawsAQueuedRecomposeLater() = runWithRecomposer {
        // A host can queue a composition for the next frame and then deliver it in this one
        // after all. The delivery must withdraw the queued entry, so the recomposer goes idle.
        val state = mutableStateOf(0)
        var composed = 0
        var seen = -1
        val composition = composition()
        composition.setContent {
            composed++
            seen = state.value
        }

        state.value = 1
        Snapshot.sendApplyNotifications()
        composition.hosting.recomposeLater()
        assertTrue(recomposer.hasPendingWork, "sanity: the composition is queued")

        assertTrue(composition.hosting.recomposeNow())
        assertEquals(2, composed, "recomposeNow delivers the change")
        assertEquals(1, seen)
        assertFalse(recomposer.hasPendingWork, "and withdraws the queued entry")
    }

    @Test
    fun recomposeNowSkipsADisposedComposition() = runWithRecomposer {
        val state = mutableStateOf(0)
        var nestedComposed = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestor = composition()
        ancestor.installRecordingHost(takes = { true })
        ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
        val nested = composition(contextHolder[0]!!)
        nested.setContent {
            nestedComposed++
            state.value
        }

        state.value = 1
        frame()
        nested.dispose()

        assertTrue(nested.hosting.recomposeNow(), "a skipped waiter does not stop a release")
        assertEquals(1, nestedComposed, "a disposed waiter must not be recomposed")
    }

    @Test
    fun recomposeNowReturnsFalseAfterARecoveredError() = runWithRecomposer(resilient = true) {
        // A released waiter that throws puts the recomposer into its error state in recovery
        // mode. Recovery recomposes everything later, so a host releasing several waiters must
        // learn to stop, and a later release must not compose against the failed state.
        val state = mutableStateOf(0)
        var attempts = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestor = composition()
        val host = ancestor.installRecordingHost(takes = { true })
        ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
        repeat(2) {
            composition(contextHolder[0]!!).setContent {
                if (state.value == 1) {
                    attempts++
                    throw IllegalStateException("a failing released waiter")
                }
            }
        }

        state.value = 1
        frame()
        assertEquals(0, attempts, "sanity: both waiters are taken")
        assertEquals(2, host.waiters.size)

        // The host's release, as SubcomposeLayout runs it: stop on false.
        var released = 0
        for (waiter in host.waiters) {
            released++
            if (!waiter.recomposeNow()) break
        }
        assertEquals(1, released, "the first failure must stop the release")
        assertEquals(1, attempts)
        assertFalse(host.waiters[1].recomposeNow(), "in the error state recomposeNow is false")
        assertEquals(1, attempts, "and composes nothing")
    }

    @Test
    fun aWaiterInvalidatedDuringItsOwnDeliveryStillRecomposesNext() = runWithRecomposer {
        // A queued recomposeLater and a real invalidation of the same waiter share one entry in
        // the recomposer's queue. The waiter's delivery here schedules a further invalidation of
        // itself, once, through a SideEffect. Withdrawing the queued entry must not drop that
        // real invalidation: the waiter must still recompose on the next frame.
        val state = mutableStateOf(0)
        var nestedComposed = 0
        var sideEffectRuns = 0
        var takesWaiters = true
        val scopeHolder = arrayOfNulls<RecomposeScope>(1)
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestor = composition()
        val host = ancestor.installRecordingHost()
        ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
        val nested = composition(contextHolder[0]!!)
        nested.setContent {
            nestedComposed++
            state.value
            scopeHolder[0] = currentRecomposeScope
            SideEffect {
                sideEffectRuns++
                // The second apply is the delivery's.
                if (sideEffectRuns == 2) scopeHolder[0]!!.invalidate()
            }
        }

        state.value = 1
        frame()
        assertEquals(1, nestedComposed, "sanity: the waiter was taken")
        nested.hosting.recomposeLater()

        // The host takes no more waiters, so the next frame recomposes what is queued.
        takesWaiters = false
        host.takesWaiters = takesWaiters
        assertTrue(nested.hosting.recomposeNow())
        assertEquals(2, nestedComposed, "delivery must recompose the waiter once")
        assertTrue(recomposer.hasPendingWork, "the invalidation scheduled during delivery survives")

        frame()
        assertEquals(3, nestedComposed, "and the next frame recomposes it")
    }

    @Test
    fun recomposeLaterHandsTheCompositionOverAgainOnTheNextFrame() = runWithRecomposer {
        // The delivery of a waiter where the host must not compose, such as a dispose: the next
        // frame asks the hosts again, and recomposes it if none takes it.
        val state = mutableStateOf(0)
        var seen = -1
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val ancestor = composition()
        val host = ancestor.installRecordingHost()
        ancestor.setContent { contextHolder[0] = rememberCompositionContext() }
        val nested = composition(contextHolder[0]!!)
        nested.setContent { seen = state.value }

        state.value = 1
        frame()
        assertEquals(1, host.waiters.size, "sanity: the waiter was taken")
        assertEquals(0, seen)

        host.takesWaiters = false
        nested.hosting.recomposeLater()
        frame()
        assertEquals(2, host.waiters.size, "the host is asked again")
        assertEquals(1, seen, "and a waiter it declines recomposes")
        assertFalse(recomposer.hasPendingWork)
    }

    @Test
    fun onDisposedIsCalledOnceBeforeTheCompositionIsUnregistered() = runWithRecomposer {
        // The host hands back the compositions that wait for a disposed one from onDisposed. That
        // must happen while the disposed composition is still registered with the recomposer,
        // and only once, however often dispose is called.
        val composition = composition()
        val host = composition.installRecordingHost()
        var calls = 0
        var queuedWhenDisposed = false
        host.whenDisposed = {
            calls++
            queuedWhenDisposed = recomposer.hasPendingWork
        }
        composition.setContent {}
        // A queued entry, which the unregistration removes.
        composition.hosting.recomposeLater()
        assertTrue(recomposer.hasPendingWork, "sanity: the composition is queued")

        composition.dispose()
        assertTrue(host.disposed)
        assertTrue(queuedWhenDisposed, "onDisposed must run before the unregistration")
        assertFalse(recomposer.hasPendingWork, "sanity: the unregistration dequeued it")

        composition.dispose()
        assertEquals(1, calls, "a second dispose must not call onDisposed again")
    }

    @Test
    fun installingASecondHostThrows() = runWithRecomposer {
        val composition = composition()
        val first = composition.installRecordingHost()
        assertFailsWith<IllegalStateException> { composition.installRecordingHost() }
        assertTrue(composition.hosting.host === first, "the first host stays installed")
    }

    @Test
    fun aThrowingHostIsHandledLikeAFailingRecompose() = runWithRecomposer(resilient = true) {
        // A host must not throw, but the recomposer treats one that does like a failing
        // recompose: in recovery mode the error is recorded, and the recomposer keeps running
        // instead of losing its coroutine.
        val state = mutableStateOf(0)
        var hostFails = false
        val composition = composition()
        composition.installRecordingHost(
            takes = { if (hostFails) throw IllegalStateException("a failing host") else false }
        )
        composition.setContent { state.value }

        hostFails = true
        state.value = 1
        frame()
        assertFalse(composition.hosting.recomposeNow(), "the error is recorded")
    }
}
