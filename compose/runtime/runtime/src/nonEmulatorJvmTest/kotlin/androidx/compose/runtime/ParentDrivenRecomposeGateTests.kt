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
 * The parent-driven recompose gate: a subcomposition whose host has a measure pass pending
 * skips its standalone recomposition (the pending measure re-runs its content with fresh
 * captures); with no measure pending it recomposes standalone as usual.
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
}
