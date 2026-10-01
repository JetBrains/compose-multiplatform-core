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

package androidx.compose.ui.tooling.recomposition

import androidx.compose.runtime.Applier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.observe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalComposeRuntimeApi::class, ExperimentalCoroutinesApi::class)
class RecompositionRecorderTest {

    private class Harness(scope: TestScope) {
        val settings = InspectorSettings().apply {
            publishIntervalNanos = 0
            loopFrameThreshold = 3
        }
        var lastSnapshot: InspectorSnapshot = InspectorSnapshot.Empty
        var now = 0L
        val recorder = RecompositionRecorder(settings, clock = { now }) { lastSnapshot = it }
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(scope.coroutineContext + clock)
        val registration = recomposer.observe(recorder)
        var frameTime = 0L

        init {
            scope.launch(clock, start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
        }

        fun compose(content: @Composable () -> Unit): Composition =
            Composition(NoopApplier(), recomposer).also {
                it.setContent { RecompositionInspectable(content) }
            }

        /** Runs one host frame the way FrameRecomposer.performFrame would. */
        fun frame(scope: TestScope) {
            frameTime += 16_000_000
            now = frameTime
            recorder.onFrameStart()
            Snapshot.sendApplyNotifications()
            scope.advanceUntilIdle()
            clock.sendFrame(frameTime)
            scope.advanceUntilIdle()
            recorder.onFrameEnd()
        }

        fun close() {
            registration.dispose()
            recomposer.cancel()
        }
    }

    private class NoopApplier : Applier<Unit> {
        override val current: Unit = Unit
        override fun down(node: Unit) {}
        override fun up() {}
        override fun insertTopDown(index: Int, instance: Unit) {}
        override fun insertBottomUp(index: Int, instance: Unit) {}
        override fun remove(index: Int, count: Int) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun clear() {}
    }

    private fun InspectorSnapshot.scopeThatRanMost(): ScopeSnapshot =
        scopes.maxByOrNull { it.runCount } ?: fail("no scopes recorded")

    @Test
    fun stateWriteIsAttributedToTheScopeThatReadsIt() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(this)
        val counter = mutableStateOf(0)
        var reads = 0
        h.frame(this)
        h.compose {
            Leaf(counter) { reads++ }
        }
        h.frame(this)
        val initialRuns = h.lastSnapshot.scopeThatRanMost().runCount

        counter.value = 1
        h.frame(this)

        val snapshot = h.lastSnapshot
        val leaf = snapshot.scopes.single { it.invalidators.isNotEmpty() }
        assertEquals(initialRuns + 1, leaf.runCount)
        assertEquals(RunCause.OwnState, leaf.lastRunCause)
        val invalidator = leaf.invalidators.single()
        assertEquals(1, invalidator.count)
        assertTrue(invalidator.description.contains("MutableState") || invalidator.description.contains("1"), invalidator.description)
        assertEquals(snapshot.frameCount, leaf.lastRunFrame)
        assertTrue(snapshot.findings.none { it.kind == FindingKind.RecomposeLoop })
        h.close()
    }

    @Test
    fun childWithoutOwnInvalidationIsReportedAsParentDriven() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(this)
        val parentState = mutableStateOf(0)
        h.frame(this)
        h.compose {
            val v = parentState.value
            // A new lambda every time keeps the child from skipping.
            UnskippableChild(v) { v + 1 }
        }
        h.frame(this)
        repeat(3) {
            parentState.value++
            h.frame(this)
        }
        val snapshot = h.lastSnapshot
        val child = snapshot.scopes.firstOrNull { it.parentDrivenCount >= 3 }
        assertNotNull(child, "expected a parent-driven scope, got: " +
            snapshot.scopes.joinToString { "${it.name}: runs=${it.runCount} parentDriven=${it.parentDrivenCount} cause=${it.lastRunCause}" })
        assertEquals(RunCause.ParentDriven, child.lastRunCause)
        h.close()
    }

    @Test
    fun compositionWritingWhatItReadsIsReportedAsLoop() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(this)
        val state = mutableStateOf(0)
        var runs = 0
        h.frame(this)
        h.compose {
            // Reads and then writes the same state during composition: a backwards write.
            // (Skipped on the first run: a write during the initial composition is discarded.)
            val current = state.value
            if (runs++ > 0) state.value = current + 1
        }
        h.frame(this)
        state.value = 1
        repeat(h.settings.loopFrameThreshold + 2) { h.frame(this) }

        val snapshot = h.lastSnapshot
        val looping = snapshot.findings.filter { it.kind == FindingKind.RecomposeLoop }
        assertTrue(looping.isNotEmpty(), "expected a RecomposeLoop finding, got: " +
            snapshot.findings.joinToString { it.kind.name })
        val scope = snapshot.scopesById.getValue(looping.first().scopeId)
        assertTrue(scope.consecutiveFrames >= h.settings.loopFrameThreshold, "consecutive=${scope.consecutiveFrames}")
        assertTrue(scope.invalidatedDuringCompositionCount > 0)
        assertTrue(scope.invalidators.isNotEmpty())
        h.close()
    }

    @Test
    fun effectFeedingBackIntoItsScopeIsReportedAsEveryFrame() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(this)
        val state = mutableStateOf(0)
        h.frame(this)
        h.compose {
            val current = state.value
            SideEffect { state.value = current + 1 }
        }
        repeat(h.settings.loopFrameThreshold + 2) { h.frame(this) }

        val snapshot = h.lastSnapshot
        val finding = snapshot.findings.firstOrNull { it.kind == FindingKind.RecomposesEveryFrame }
        assertNotNull(finding, "expected RecomposesEveryFrame, got: " + snapshot.findings.joinToString { it.kind.name })
        val scope = snapshot.scopesById.getValue(finding.scopeId)
        assertEquals(0, scope.invalidatedDuringCompositionCount)
        assertTrue(scope.invalidators.isNotEmpty())
        h.close()
    }

    @Test
    fun explicitInvalidateIsRecordedWithoutAnInvalidator() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(this)
        var scopeHandle: androidx.compose.runtime.RecomposeScope? = null
        h.frame(this)
        h.compose {
            scopeHandle = currentRecomposeScope
        }
        h.frame(this)
        scopeHandle!!.invalidate()
        h.frame(this)
        val scope = h.lastSnapshot.scopes.single { it.explicitInvalidateCount > 0 }
        assertEquals(RunCause.ExplicitInvalidate, scope.lastRunCause)
        assertTrue(scope.invalidators.isEmpty())
        h.close()
    }

    @Test
    fun excludedScopeAndItsChildrenAreNotRecorded() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(this)
        val appState = mutableStateOf(0)
        val panelState = mutableStateOf(0)
        h.frame(this)
        h.compose {
            Leaf(appState) {}
            val root = currentRecomposeScope
            h.recorder.excludeScope(root)
            PanelChild(panelState)
        }
        h.frame(this)
        repeat(3) {
            panelState.value++
            h.frame(this)
        }
        appState.value = 1
        h.frame(this)
        val snapshot = h.lastSnapshot
        assertTrue(snapshot.scopes.none { it.invalidators.any { inv -> inv.description.contains("value=3") } },
            "panel child leaked into the snapshot: " + snapshot.scopes.joinToString { "${it.name}:${it.runCount}" })
        val leaf = snapshot.scopes.single { it.invalidators.isNotEmpty() }
        assertEquals(1, leaf.invalidators.single().count)
        h.close()
    }
}

@Composable
private fun <T> Leaf(state: MutableState<T>, onRead: (T) -> Unit) {
    onRead(state.value)
}

@Composable
private fun UnskippableChild(value: Int, compute: () -> Int) {
    var local by androidx.compose.runtime.remember { mutableStateOf(0) }
    local = compute() + value
}

@Composable
private fun PanelChild(state: MutableState<Int>) {
    val v = state.value
    Leaf(state) {}
    UnskippableChild(v) { v }
}
