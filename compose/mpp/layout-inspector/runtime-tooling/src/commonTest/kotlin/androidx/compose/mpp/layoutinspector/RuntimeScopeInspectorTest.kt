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

package androidx.compose.mpp.layoutinspector

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.structuralEqualityPolicy
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeScopeInspectorTest {
    @Test
    fun tracksDistinctReadsAndDropsDisposedScopes() = withInspector { composition, inspector ->
        val visible = mutableStateOf(true)
        val useSecond = mutableStateOf(true)
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        composition.setContent {
            if (visible.value) ReadStates(useSecond, first, second) {}
        }

        val initial = inspector.snapshot().single { it.observedStates == 3 }
        assertEquals(1L, initial.visits)
        assertEquals(0L, initial.invalidations)

        useSecond.value = false
        composition.recomposeAfter(useSecond)

        val updated = inspector.snapshot().single { it.id == initial.id }
        assertEquals(2, updated.observedStates)
        assertEquals(2L, updated.visits)
        assertEquals(1L, updated.invalidations)
        assertEquals(0L, updated.skips)

        visible.value = false
        composition.recomposeAfter(visible)

        assertNull(inspector.snapshot().find { it.id == initial.id })
        inspector.close()
        composition.setContent { ReadStates(useSecond, first, second) {} }
        assertTrue(inspector.snapshot().isEmpty())
    }

    @Test
    fun countsSkipAndReadReplayAsVisitsButOnlyCountsOneSkip() =
        withInspector { composition, inspector ->
            val parentState = mutableStateOf(0)
            val otherParentState = mutableStateOf(1)
            val source = mutableStateOf(0)
            val derivedState = derivedStateOf(structuralEqualityPolicy()) { source.value % 2 }
            var executions = 0
            val onRead: (Int) -> Unit = { executions++ }
            composition.setContent {
                val value = parentState.value + otherParentState.value
                SideEffect { check(value >= 0) }
                ReadState(derivedState, onRead)
            }

            val initial = inspector.snapshot().single { it.observedStates == 1 }
            assertEquals(1, executions)

            parentState.value++
            composition.recomposeAfter(parentState)

            val skipped = inspector.snapshot().single { it.id == initial.id }
            assertEquals(2L, skipped.visits)
            assertEquals(1L, skipped.skips)
            assertEquals(1, skipped.observedStates)
            assertEquals(1, executions)

            source.value += 2
            composition.recomposeAfter(source)

            val replayed = inspector.snapshot().single { it.id == initial.id }
            assertEquals(3L, replayed.visits)
            assertEquals(1L, replayed.skips)
            assertEquals(1L, replayed.invalidations)
            assertEquals(1, replayed.observedStates)
            assertEquals(1, executions)
        }

    private fun withInspector(block: (ControlledComposition, RuntimeScopeInspector) -> Unit) {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = ControlledComposition(EmptyApplier(), recomposer)
        try {
            val inspector = RuntimeScopeInspector(composition)
            try {
                block(composition, inspector)
            } finally {
                inspector.close()
            }
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

    private fun ControlledComposition.recomposeAfter(value: Any) {
        recordModificationsOf(setOf(value))
        val snapshot = Snapshot.takeMutableSnapshot(::recordReadOf, ::recordWriteOf)
        try {
            val hasChanges = snapshot.enter { recompose() }
            snapshot.apply().check()
            if (hasChanges) applyChanges()
        } finally {
            snapshot.dispose()
        }
    }
}

@Composable
private fun ReadStates(
    useSecond: State<Boolean>,
    first: State<Int>,
    second: State<Int>,
    onRead: (Int) -> Unit,
) {
    val value = first.value + first.value + if (useSecond.value) second.value else 0
    SideEffect { onRead(value) }
}

@Composable
private fun ReadState(state: State<Int>, onRead: (Int) -> Unit) {
    val value = state.value
    SideEffect { onRead(value) }
}

private class EmptyApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) {}
    override fun insertBottomUp(index: Int, instance: Unit) {}
    override fun remove(index: Int, count: Int) {}
    override fun move(from: Int, to: Int, count: Int) {}
    override fun onClear() {}
}
