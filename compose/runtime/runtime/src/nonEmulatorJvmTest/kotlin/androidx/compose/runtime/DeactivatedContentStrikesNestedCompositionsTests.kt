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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Removing a group that holds a composition context reports every composition created under it
 * as removed, and the recomposer skips a removed composition for the rest of the turn.
 * Deactivating content for reuse does the same, behind
 * [ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled].
 *
 * Deactivated content keeps its composition contexts, so they can be reused. But deactivation
 * forgets the remembered state and effects that own the compositions created from those
 * contexts, and those owners tear the compositions down or empty them. Until then a composition
 * must not compose against the state that deactivated its creator. A lazy list deactivates a row
 * that leaves its items, and an overlay anchored in that row is such a composition.
 *
 * The skip lasts for the rest of the turn only. The next frame clears it, because the skipped
 * compositions are still alive and a later change must reach them.
 */
class DeactivatedContentStrikesNestedCompositionsTests {
    @Test fun deactivatingContentInCompositionSkipsACompositionCreatedUnderIt() = checkDeactivatingContentInCompositionSkipsACompositionCreatedUnderIt()

    @Test
    fun deactivatingContentInCompositionSkipsACompositionCreatedUnderItWithTheLinkComposer() =
        withLinkComposer { checkDeactivatingContentInCompositionSkipsACompositionCreatedUnderIt() }

    private fun checkDeactivatingContentInCompositionSkipsACompositionCreatedUnderIt(): Unit =
        runBlocking {
        // ReusableContentHost deactivates its content while the composition recomposes. The
        // nested composition is invalidated in the same transaction and comes later in the pass.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val active = mutableStateOf(true)
        val state = mutableStateOf(0)
        var nestedComposed = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(2)

        try {
            val parent = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            parent.setContent {
                ReusableContentHost(active.value) {
                    contextHolder[0] = rememberCompositionContext()
                }
            }
            val nested = Composition(UnitApplier(), contextHolder[0]!!).also { holders[1] = it }
            nested.setContent {
                nestedComposed++
                state.value
            }
            assertEquals(1, nestedComposed)

            Snapshot.withMutableSnapshot {
                active.value = false
                state.value = 1
            }
            frameClock.sendFrame(1L)
            assertEquals(
                1,
                nestedComposed,
                "a composition created under deactivated content must not recompose in that turn",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test fun aSkipFromOutsideTheFrameDoesNotLeakIntoTheNextFrame() = checkASkipFromOutsideTheFrameDoesNotLeakIntoTheNextFrame()

    @Test
    fun aSkipFromOutsideTheFrameDoesNotLeakIntoTheNextFrameWithTheLinkComposer() =
        withLinkComposer { checkASkipFromOutsideTheFrameDoesNotLeakIntoTheNextFrame() }

    private fun checkASkipFromOutsideTheFrameDoesNotLeakIntoTheNextFrame(): Unit =
        runBlocking {
        // A host re-runs the parent outside the frame, as SubcomposeLayout does from its measure,
        // and the re-run deactivates content. The composition created under it is still alive.
        // A change that reaches it on the next frame must recompose it.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val active = mutableStateOf(true)
        val state = mutableStateOf(0)
        var nestedComposed = 0
        val contextHolder = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(2)

        try {
            val parent = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            val parentContent: @Composable () -> Unit = {
                ReusableContentHost(active.value) { contextHolder[0] = rememberCompositionContext() }
            }
            parent.setContent(parentContent)
            val nested = Composition(UnitApplier(), contextHolder[0]!!).also { holders[1] = it }
            nested.setContent {
                nestedComposed++
                state.value
            }
            assertEquals(1, nestedComposed)

            // Outside any frame: the host's re-run deactivates the content.
            active.value = false
            parent.setContent(parentContent)

            state.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)
            assertEquals(
                2,
                nestedComposed,
                "a skip recorded outside the frame must not consume the next frame's change",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun withTheSkipDisabledDeactivatingContentDoesNotSkipACompositionCreatedUnderIt() =
        withNestedCompositionSkipOnDeactivation(enabled = false) {
            runBlocking {
                val frameClock = BroadcastFrameClock()
                val recomposer =
                    Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
                val runner =
                    launch(
                        Dispatchers.Unconfined + frameClock,
                        start = CoroutineStart.UNDISPATCHED,
                    ) {
                        recomposer.runRecomposeAndApplyChanges()
                    }
                val active = mutableStateOf(true)
                val state = mutableStateOf(0)
                var nestedComposed = 0
                val contextHolder = arrayOfNulls<CompositionContext>(1)
                val holders = arrayOfNulls<Composition>(2)
                try {
                    val parent = Composition(UnitApplier(), recomposer).also { holders[0] = it }
                    parent.setContent {
                        ReusableContentHost(active.value) {
                            contextHolder[0] = rememberCompositionContext()
                        }
                    }
                    val nested =
                        Composition(UnitApplier(), contextHolder[0]!!).also { holders[1] = it }
                    nested.setContent {
                        nestedComposed++
                        state.value
                    }

                    Snapshot.withMutableSnapshot {
                        active.value = false
                        state.value = 1
                    }
                    frameClock.sendFrame(1L)
                    assertEquals(
                        2,
                        nestedComposed,
                        "with the flag off, deactivation must leave the composition to recompose",
                    )
                } finally {
                    holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
                    recomposer.cancel()
                    runner.join()
                }
            }
        }

    @Test fun theSkipReachesACompositionNestedTwoLevelsBelowDeactivatedContent() = checkTheSkipReachesACompositionNestedTwoLevelsBelowDeactivatedContent()

    @Test
    fun theSkipReachesACompositionNestedTwoLevelsBelowDeactivatedContentWithTheLinkComposer() =
        withLinkComposer { checkTheSkipReachesACompositionNestedTwoLevelsBelowDeactivatedContent() }

    private fun checkTheSkipReachesACompositionNestedTwoLevelsBelowDeactivatedContent(): Unit =
        runBlocking {
        // Removal strikes transitively, through every composition context in the struck
        // compositions. Deactivation must too.
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + Dispatchers.Unconfined + frameClock)
        val runner =
            launch(Dispatchers.Unconfined + frameClock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val active = mutableStateOf(true)
        val state = mutableStateOf(0)
        var leafComposed = 0
        val outerContext = arrayOfNulls<CompositionContext>(1)
        val midContext = arrayOfNulls<CompositionContext>(1)
        val holders = arrayOfNulls<Composition>(3)

        try {
            val parent = Composition(UnitApplier(), recomposer).also { holders[0] = it }
            parent.setContent {
                ReusableContentHost(active.value) { outerContext[0] = rememberCompositionContext() }
            }
            val mid = Composition(UnitApplier(), outerContext[0]!!).also { holders[1] = it }
            mid.setContent { midContext[0] = rememberCompositionContext() }
            val leaf = Composition(UnitApplier(), midContext[0]!!).also { holders[2] = it }
            leaf.setContent {
                leafComposed++
                state.value
            }
            assertEquals(1, leafComposed)

            Snapshot.withMutableSnapshot {
                active.value = false
                state.value = 1
            }
            frameClock.sendFrame(1L)
            assertEquals(
                1,
                leafComposed,
                "a composition two levels below deactivated content must not recompose in that turn",
            )
        } finally {
            holders.reversed().forEach { if (it != null && !it.isDisposed) it.dispose() }
            recomposer.cancel()
            runner.join()
        }
    }

    // Compositions pick their composer when they are created, so the flag must be on while the
    // test creates them.
    @OptIn(ExperimentalComposeApi::class)
    private fun withLinkComposer(block: () -> Unit) {
        val previous = ComposeRuntimeFlags.isLinkBufferComposerEnabled
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
        try {
            block()
        } finally {
            ComposeRuntimeFlags.isLinkBufferComposerEnabled = previous
        }
    }

    @OptIn(ExperimentalComposeApi::class, InternalComposeApi::class)
    private fun withNestedCompositionSkipOnDeactivation(enabled: Boolean, block: () -> Unit) {
        val previous = ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled
        ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled = enabled
        try {
            block()
        } finally {
            ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled = previous
        }
    }
}
