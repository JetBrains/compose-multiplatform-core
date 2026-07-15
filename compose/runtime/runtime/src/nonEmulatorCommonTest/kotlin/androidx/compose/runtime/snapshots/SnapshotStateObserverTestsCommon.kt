/*
 * Copyright 2021 The Android Open Source Project
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

import androidx.compose.runtime.DataSource
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.DerivedState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateObservers
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.mock.BufferedTestDataSource
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotStateObserverTestsCommon {

    @Test
    fun stateChangeTriggersCallback() {
        val data = ValueWrapper("Hello World")
        var changes = 0

        val state = mutableIntStateOf(0)
        val stateObserver = SnapshotStateObserver { it() }
        try {
            stateObserver.start()

            val onChangeListener: (ValueWrapper) -> Unit = { affected ->
                assertEquals(data, affected)
                assertEquals(0, changes)
                changes++
            }

            stateObserver.observeReads(data, onChangeListener) {
                // read the value
                state.intValue
            }

            Snapshot.notifyObjectsInitialized()
            state.intValue++
            Snapshot.sendApplyNotifications()

            assertEquals(1, changes)
        } finally {
            stateObserver.stop()
        }
    }

    @Test
    fun multipleStagesWorksTogether() {
        val strStage1 = ValueWrapper("Stage1")
        val strStage2 = ValueWrapper("Stage2")
        val strStage3 = ValueWrapper("Stage3")
        var stage1Changes = 0
        var stage2Changes = 0
        var stage3Changes = 0
        val stage1Model = mutableIntStateOf(0)
        val stage2Model = mutableIntStateOf(0)
        val stage3Model = mutableIntStateOf(0)

        val onChangeStage1: (ValueWrapper) -> Unit = { affectedData ->
            assertEquals(strStage1, affectedData)
            assertEquals(0, stage1Changes)
            stage1Changes++
        }
        val onChangeStage2: (ValueWrapper) -> Unit = { affectedData ->
            assertEquals(strStage2, affectedData)
            assertEquals(0, stage2Changes)
            stage2Changes++
        }
        val onChangeStage3: (ValueWrapper) -> Unit = { affectedData ->
            assertEquals(strStage3, affectedData)
            assertEquals(0, stage3Changes)
            stage3Changes++
        }
        val stateObserver = SnapshotStateObserver { it() }
        try {
            stateObserver.start()

            stateObserver.observeReads(strStage1, onChangeStage1) { stage1Model.intValue }

            stateObserver.observeReads(strStage2, onChangeStage2) { stage2Model.intValue }

            stateObserver.observeReads(strStage3, onChangeStage3) { stage3Model.intValue }

            Snapshot.notifyObjectsInitialized()

            stage1Model.intValue++
            stage2Model.intValue++
            stage3Model.intValue++

            Snapshot.sendApplyNotifications()

            assertEquals(1, stage1Changes)
            assertEquals(1, stage2Changes)
            assertEquals(1, stage3Changes)
        } finally {
            stateObserver.stop()
        }
    }

    @Test
    fun enclosedStagesCorrectlyObserveChanges() {
        val stage1Info = ValueWrapper("stage 1")
        val stage2Info1 = ValueWrapper("stage 1 - value 1")
        val stage2Info2 = ValueWrapper("stage 2 - value 2")
        var stage1Changes = 0
        var stage2Changes1 = 0
        var stage2Changes2 = 0
        val stage1Data = mutableIntStateOf(0)
        val stage2Data1 = mutableIntStateOf(0)
        val stage2Data2 = mutableIntStateOf(0)

        val onChangeStage1Listener: (ValueWrapper) -> Unit = { affected ->
            assertEquals(affected, stage1Info)
            assertEquals(stage1Changes, 0)
            stage1Changes++
        }
        val onChangeState2Listener: (ValueWrapper) -> Unit = { affected ->
            when (affected) {
                stage2Info1 -> {
                    assertEquals(0, stage2Changes1)
                    stage2Changes1++
                }
                stage2Info2 -> {
                    assertEquals(0, stage2Changes2)
                    stage2Changes2++
                }
                stage1Info -> {
                    error("stage 1 called in stage 2")
                }
            }
        }

        val stateObserver = SnapshotStateObserver { it() }
        try {
            stateObserver.start()

            stateObserver.observeReads(stage2Info1, onChangeState2Listener) {
                stage2Data1.intValue
                stateObserver.observeReads(stage2Info2, onChangeState2Listener) {
                    stage2Data2.intValue
                    stateObserver.observeReads(stage1Info, onChangeStage1Listener) {
                        stage1Data.intValue
                    }
                }
            }

            Snapshot.notifyObjectsInitialized()

            stage2Data1.intValue++
            stage2Data2.intValue++
            stage1Data.intValue++

            Snapshot.sendApplyNotifications()

            assertEquals(1, stage1Changes)
            assertEquals(1, stage2Changes1)
            assertEquals(1, stage2Changes2)
        } finally {
            stateObserver.stop()
        }
    }

    @Test
    fun stateReadTriggersCallbackAfterSwitchingAdvancingGlobalWithinObserveReads() {
        val info = ValueWrapper("Hello")
        var changes = 0

        val state = mutableIntStateOf(0)
        val onChangeListener: (ValueWrapper) -> Unit = { _ ->
            assertEquals(0, changes)
            changes++
        }

        val stateObserver = SnapshotStateObserver { it() }
        try {
            stateObserver.start()

            stateObserver.observeReads(info, onChangeListener) {
                // Create a sub-snapshot
                // this will be done by subcomposition, for example.
                val snapshot = Snapshot.takeMutableSnapshot()
                try {
                    // read the value
                    snapshot.enter { state.intValue }
                    snapshot.apply().check()
                } finally {
                    snapshot.dispose()
                }
            }

            state.intValue++

            Snapshot.sendApplyNotifications()

            assertEquals(1, changes)
        } finally {
            stateObserver.stop()
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun pauseStopsObserving() {
        val data = ValueWrapper("data")
        var changes = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(data, { changes++ }) {
                stateObserver.withNoObservations { state.value }
            }
        }

        assertEquals(0, changes)
    }

    @Test
    fun withoutReadObservationStopsObserving() {
        val data = ValueWrapper("data")
        var changes = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(data, { changes++ }) {
                Snapshot.withoutReadObservation { state.value }
            }
        }

        assertEquals(0, changes)
    }

    @Test
    fun changeAfterWithoutReadObservationIsObserving() {
        val data = ValueWrapper("data")
        var changes = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(data, { changes++ }) {
                Snapshot.withoutReadObservation { state.value }
                state.value
            }
        }

        assertEquals(1, changes)
    }

    @Suppress("DEPRECATION")
    @Test
    fun nestedPauseStopsObserving() {
        val data = ValueWrapper("data")
        var changes = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(data, { _ -> changes++ }) {
                stateObserver.withNoObservations {
                    stateObserver.withNoObservations { state.value }
                    state.value
                }
            }
        }

        assertEquals(0, changes)
    }

    @Test
    fun nestedWithoutReadObservation() {
        val data = ValueWrapper("data")
        var changes = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(data, { changes++ }) {
                Snapshot.withoutReadObservation {
                    Snapshot.withoutReadObservation { state.value }
                    state.value
                }
            }
        }

        assertEquals(0, changes)
    }

    @Test
    fun simpleObserving() {
        val data = ValueWrapper("data")
        var changes = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(data, { _ -> changes++ }) { state.value }
        }

        assertEquals(1, changes)
    }

    @Suppress("DEPRECATION")
    @Test
    fun observeWithinPause() {
        val data = ValueWrapper("data")
        var changes1 = 0
        var changes2 = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(data, { _ -> changes1++ }) {
                stateObserver.withNoObservations {
                    stateObserver.observeReads(data, { _ -> changes2++ }) { state.value }
                }
            }
        }
        assertEquals(0, changes1)
        assertEquals(1, changes2)
    }

    @Test
    fun observeWithinWithoutReadObservation() {
        val data = ValueWrapper("data")
        var changes1 = 0
        var changes2 = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(data, { changes1++ }) {
                Snapshot.withoutReadObservation {
                    stateObserver.observeReads(data, { changes2++ }) { state.value }
                }
            }
        }
        assertEquals(0, changes1)
        assertEquals(1, changes2)
    }

    @Test
    fun withoutReadsPausesNestedObservation() {
        var changes1 = 0
        var changes2 = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(ValueWrapper("scope1"), { changes1++ }) {
                stateObserver.observeReads(ValueWrapper("scope2"), { changes2++ }) {
                    Snapshot.withoutReadObservation { state.value }
                }
            }
        }
        assertEquals(0, changes1)
        assertEquals(0, changes2)
    }

    @Test
    fun withoutReadsPausesNestedObservationWhenNewMutableSnapshotIsEnteredWithin() {
        var changes1 = 0
        var changes2 = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(ValueWrapper("scope1"), { changes1++ }) {
                stateObserver.observeReads(ValueWrapper("scope2"), { changes2++ }) {
                    Snapshot.withoutReadObservation {
                        val newSnapshot = Snapshot.takeMutableSnapshot()
                        newSnapshot.enter { state.value }
                        newSnapshot.apply().check()
                        newSnapshot.dispose()
                    }
                }
            }
        }
        assertEquals(0, changes1)
        assertEquals(0, changes2)
    }

    @Test
    fun withoutReadsPausesNestedObservationWhenNewSnapshotIsEnteredWithin() {
        var changes1 = 0
        var changes2 = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(ValueWrapper("scope1"), { changes1++ }) {
                stateObserver.observeReads(ValueWrapper("scope2"), { changes2++ }) {
                    Snapshot.withoutReadObservation {
                        val newSnapshot = Snapshot.takeSnapshot()
                        newSnapshot.enter { state.value }
                        newSnapshot.dispose()
                    }
                }
            }
        }
        assertEquals(0, changes1)
        assertEquals(0, changes2)
    }

    @Test
    fun withoutReadsInReadOnlySnapshot() {
        var changes = 0

        runSimpleTest { stateObserver, state ->
            stateObserver.observeReads(ValueWrapper("scope"), { changes++ }) {
                val newSnapshot = Snapshot.takeSnapshot()
                newSnapshot.enter { Snapshot.withoutReadObservation { state.value } }
                newSnapshot.dispose()
            }
        }
        assertEquals(0, changes)
    }

    @Test
    fun derivedStateOfInvalidatesObserver() {
        var changes = 0

        runSimpleTest { stateObserver, state ->
            val derivedState = derivedStateOf { state.value }

            stateObserver.observeReads(ValueWrapper("scope"), { changes++ }) {
                // read
                derivedState.value
            }
        }
        assertEquals(1, changes)
    }

    // The derived-state hole, first-read path: a hook-based source's read during the FIRST
    // recalculation of a derivedStateOf was recorded by neither mechanism -- the derived state's
    // own recorder was never called, and the captured outer readObserver dropped it at the
    // deriveStateScopeCount guard. This is now fixed: the first recalculation runs inside
    // context.observe(), so the hook is installed and the dependency is captured correctly.
    //
    // A residual limitation remains on the RE-READ path (recordInvalidation -> rereadDerivedState),
    // which runs outside context.observe() and can silently drop the dependency again after an
    // equal recalculation -- see aHookBasedSourceDependencyIsLostAfterAnEqualRereadOutsideContext
    // below, which pins that as a known, not-yet-fixed limitation.
    @Test
    fun aHookBasedSourceReadInsideDerivedStateInvalidatesTheScope() {
        val source = BufferedTestDataSource()
        val holder = SnapshotHolder(DataSourceContext(source), isolating = false)
        val scope = ValueWrapper("scope")
        var changes = 0
        val stateObserver = SnapshotStateObserver(holder) { it() }
        try {
            stateObserver.start()
            val derived = derivedStateOf { source.read("a") }
            stateObserver.observeReads(scope, { changes++ }) { derived.value }
            source.write("a", 1)
            source.advanceAndInvalidate()
            assertEquals(1, changes)
        } finally {
            stateObserver.stop()
        }
    }

    // KNOWN LIMITATION (not fixed by task 4, needs a design decision about how the re-read path
    // reaches a hook-based source's observe() hook): SnapshotStateObserver.recordInvalidation
    // reads a recalculated derived value that turns out EQUAL to the previously recorded one, and
    // takes the rereadDerivedState() path instead of invalidating. That re-read runs OUTSIDE any
    // context.observe(), so the hook-based source's `hook` is null, the recalculation's
    // newDependencies comes back empty, and recordRead's dependencyToDerivedStates.removeScope
    // (value) clears the old dependency edges and re-adds nothing. The derived state's record is
    // left with alwaysInvalid = false and an empty dependency set, so `resultHash == readableHash`
    // holds forever afterwards: the record is permanently cached, and re-observing it from a NEW
    // scope does not recover it either -- every reader, old and new, keeps getting the stale value
    // this source produced at the last equal re-read, silently, for as long as the process runs.
    // This can render visibly wrong UI, not merely delay a refresh.
    //
    // Cheap mitigation: a source that calls DataSource.recordDependency unconditionally on every
    // read -- instead of only when it has a hook/witness installed -- has no gap at all here,
    // because the re-read path does install a snapshot read observer and thread-local recorder;
    // it just never invokes a per-source `observe()` hook. That is exactly why
    // stockWithoutReadObservationSuppressesAStaticRecorderSource (DataSourceContextTests.kt) keeps
    // working. Integrators can sidestep this entire class of bug by taking that shape instead of
    // this one.
    //
    // Reproduced with 1 -> 2 -> 0: 1 -> 2 is a same-truthiness recalculation (both > 0), which
    // walks the buggy re-read path and drops the dependency; 2 -> 0 is a genuine value change that
    // SHOULD invalidate the scope but no longer can.
    // Known limitation: rereadDerivedState() runs outside context.observe() and silently drops a
    // hook-based source's dependency after an equal recalculation. See task 4 report.
    @Ignore
    @Test
    fun aHookBasedSourceDependencyIsLostAfterAnEqualRereadOutsideContext() {
        val source = BufferedTestDataSource()
        val holder = SnapshotHolder(DataSourceContext(source), isolating = false)
        val scope = ValueWrapper("scope")
        var changes = 0
        val stateObserver = SnapshotStateObserver(holder) { it() }
        try {
            stateObserver.start()
            source.write("k", 1)
            source.advanceAndInvalidate()
            val derived = derivedStateOf { (source.read("k") ?: 0) > 0 }
            stateObserver.observeReads(scope, { changes++ }) { derived.value }

            // 1 -> 2: still > 0. The recalculated value is EQUAL to the recorded one, so this
            // takes the rereadDerivedState() path and drops the dependency -- it must not itself
            // invalidate the scope.
            source.write("k", 2)
            source.advanceAndInvalidate()
            assertEquals(0, changes, "an equal recalculation must not itself invalidate the scope")

            // 2 -> 0: > 0 flips to false, a genuine change that SHOULD invalidate the scope. It
            // does not, because the previous re-read already lost the "k" dependency.
            source.write("k", 0)
            source.advanceAndInvalidate()
            assertEquals(
                1,
                changes,
                "the derived state's value actually changed and must invalidate",
            )
        } finally {
            stateObserver.stop()
        }
    }

    @Suppress("MutableCollectionMutableState") // The point of this test
    @Test
    fun derivedStateOfReferentialChangeDoesNotInvalidateObserver() {
        var changes = 0

        runSimpleTest { stateObserver, _ ->
            val state = mutableStateOf(mutableListOf(42), referentialEqualityPolicy())
            val derivedState = derivedStateOf { state.value }

            stateObserver.observeReads(ValueWrapper("scope"), { changes++ }) {
                // read
                derivedState.value
            }

            state.value = mutableListOf(42)
        }
        assertEquals(0, changes)
    }

    @Test
    fun nestedDerivedStateOfInvalidatesObserver() {
        var changes = 0

        runSimpleTest { stateObserver, state ->
            val derivedState = derivedStateOf { state.value }
            val derivedState2 = derivedStateOf { derivedState.value }

            stateObserver.observeReads(ValueWrapper("scope"), { changes++ }) {
                // read
                derivedState2.value
            }
        }
        assertEquals(1, changes)
    }

    @Suppress("MutableCollectionMutableState") // The point of this test
    @Test
    fun derivedStateOfWithReferentialMutationPolicy() {
        var changes = 0

        runSimpleTest { stateObserver, _ ->
            val state = mutableStateOf(mutableListOf(1), referentialEqualityPolicy())
            val derivedState = derivedStateOf(referentialEqualityPolicy()) { state.value }

            stateObserver.observeReads(ValueWrapper("scope"), { changes++ }) {
                // read
                derivedState.value
            }

            state.value = mutableListOf(1)
        }
        assertEquals(1, changes)
    }

    @Suppress("MutableCollectionMutableState") // The point of this test
    @Test
    fun derivedStateOfWithStructuralMutationPolicy() {
        var changes = 0

        runSimpleTest { stateObserver, _ ->
            val state = mutableStateOf(mutableListOf(1), referentialEqualityPolicy())
            val derivedState = derivedStateOf(structuralEqualityPolicy()) { state.value }

            stateObserver.observeReads(ValueWrapper("scope"), { changes++ }) {
                // read
                derivedState.value
            }

            state.value = mutableListOf(1)
        }
        assertEquals(0, changes)
    }

    @Test
    fun readingDerivedStateAndDependencyInvalidates() {
        var changes = 0

        runSimpleTest { stateObserver, state ->
            val derivedState = derivedStateOf { state.value >= 0 }

            stateObserver.observeReads(ValueWrapper("scope"), { changes++ }) {
                // read derived state
                derivedState.value
                // read dependency
                state.value
            }
        }
        assertEquals(1, changes)
    }

    @Test
    fun readingDerivedStateWithDependencyChangeInvalidates() {
        var changes = 0

        runSimpleTest { stateObserver, state ->
            val state2 = mutableStateOf(false)
            val derivedState = derivedStateOf {
                if (state2.value) {
                    state.value
                } else {
                    null
                }
            }
            val onChange: (ValueWrapper) -> Unit = { changes++ }

            val scope = ValueWrapper("scope")
            stateObserver.observeReads(scope, onChange) {
                // read derived state
                derivedState.value
            }

            state2.value = true
            // advance snapshot
            Snapshot.sendApplyNotifications()
            Snapshot.notifyObjectsInitialized()

            stateObserver.observeReads(scope, onChange) {
                // read derived state
                derivedState.value
            }
        }
        assertEquals(2, changes)
    }

    @Test
    fun readingDerivedStateConditionallyInvalidatesBothScopes() {
        var changes = 0

        runSimpleTest { stateObserver, state ->
            val derivedState = derivedStateOf { state.value }

            val onChange: (ValueWrapper) -> Unit = { changes++ }
            stateObserver.observeReads(ValueWrapper("scope"), onChange) {
                // read derived state
                derivedState.value
            }

            val scope2 = ValueWrapper("other scope")
            // read the same state in other scope
            stateObserver.observeReads(scope2, onChange) { derivedState.value }

            // advance snapshot to invalidate reads
            Snapshot.notifyObjectsInitialized()

            // stop observing state in other scope
            stateObserver.observeReads(scope2, onChange) {
                /* no-op */
            }
        }
        assertEquals(1, changes)
    }

    @Test
    fun testRecursiveApplyChanges_SingleRecursive() {
        val stateObserver = SnapshotStateObserver { it() }
        val state1 = mutableIntStateOf(0)
        val state2 = mutableIntStateOf(0)
        try {
            stateObserver.start()
            Snapshot.notifyObjectsInitialized()

            val onChange: (ValueWrapper) -> Unit = { scope ->
                if (scope.s == "scope" && state1.intValue < 2) {
                    state1.intValue++
                    Snapshot.sendApplyNotifications()
                }
            }

            stateObserver.observeReads(ValueWrapper("scope"), onChange) {
                state1.intValue
                state2.intValue
            }

            repeat(10) {
                stateObserver.observeReads(ValueWrapper("scope $it"), onChange) {
                    state1.intValue
                    state2.intValue
                }
            }

            state1.intValue++
            state2.intValue++

            Snapshot.sendApplyNotifications()
        } finally {
            stateObserver.stop()
        }
    }

    @Test
    fun testRecursiveApplyChanges_MultiRecursive() {
        val stateObserver = SnapshotStateObserver { it() }
        val state1 = mutableIntStateOf(0)
        val state2 = mutableIntStateOf(0)
        val state3 = mutableIntStateOf(0)
        val state4 = mutableIntStateOf(0)
        try {
            stateObserver.start()
            Snapshot.notifyObjectsInitialized()

            val onChange: (ValueWrapper) -> Unit = { scope ->
                if (scope.s == "scope" && state1.intValue < 2) {
                    state1.intValue++
                    Snapshot.sendApplyNotifications()
                    state2.intValue++
                    Snapshot.sendApplyNotifications()
                    state3.intValue++
                    Snapshot.sendApplyNotifications()
                    state4.intValue++
                    Snapshot.sendApplyNotifications()
                }
            }

            stateObserver.observeReads(ValueWrapper("scope"), onChange) {
                state1.intValue
                state2.intValue
                state3.intValue
                state4.intValue
            }

            repeat(10) {
                stateObserver.observeReads(ValueWrapper("scope $it"), onChange) {
                    state1.intValue
                    state2.intValue
                    state3.intValue
                    state4.intValue
                }
            }

            state1.intValue++
            state2.intValue++
            state3.intValue++
            state4.intValue++

            Snapshot.sendApplyNotifications()
        } finally {
            stateObserver.stop()
        }
    }

    @Test
    fun readingValueAfterClearInvalidates() {
        var changes = 0

        runSimpleTest { stateObserver, state ->
            val changeBlock: (Any) -> Unit = { changes++ }
            // record observation
            val s = ValueWrapper("scope")
            stateObserver.observeReads(s, changeBlock) {
                // read state
                state.value
            }

            // clear scope
            stateObserver.clear(s)

            // record again
            stateObserver.observeReads(s, changeBlock) {
                // read state
                state.value
            }
        }
        assertEquals(1, changes)
    }

    @Test
    fun readingDerivedState_invalidatesWhenValueNotChanged() {
        var changes = 0
        val changeBlock: (Any) -> Unit = { changes++ }

        runSimpleTest { stateObserver, state ->
            var condition by mutableStateOf(false)
            val derivedState = derivedStateOf {
                // the same initial value for both branches
                if (condition) state.value else 0
            }

            // record observation
            stateObserver.observeReads("scope", changeBlock) {
                // read state
                derivedState.value
            }

            condition = true
            Snapshot.sendApplyNotifications()
        }
        assertEquals(1, changes)
    }

    @Test
    fun readingDerivedState_invalidatesIfReadBeforeSnapshotAdvance() {
        var changes = 0
        val changeBlock: (Any) -> Unit = {
            if (it == "draw_1") {
                changes++
            }
        }

        runSimpleTest { stateObserver, layoutState ->
            val derivedState = derivedStateOf { layoutState.value }

            // record observation for a draw scope
            stateObserver.observeReads("draw", changeBlock) { derivedState.value }

            // record observation for a different draw scope
            stateObserver.observeReads("draw_1", changeBlock) { derivedState.value }

            Snapshot.sendApplyNotifications()

            // record
            layoutState.value += 1

            // record observation for the first draw scope
            stateObserver.observeReads("draw", changeBlock) {
                // read state
                derivedState.value
            }

            // second block should be invalidated after we read the value
            assertEquals(1, changes)

            // record observation for the second draw scope
            stateObserver.observeReads("draw_1", changeBlock) {
                // read state
                derivedState.value
            }
        }
        assertEquals(2, changes)
    }

    // regression test for b/435655844
    @Test
    fun derivedStateReentrant() = runSimpleTest { observer, state ->
        val initialRead = mutableStateOf(true)
        // This cursed setup invalidates this derived state while it is inside apply observer
        // Initially this reads both `state` and `initialRead` states.
        //
        // During invalidation, we are incrementing the `state.value` while iterating over derived
        // states that have dependency on `initialRead`. `state.value` is incremented during that
        // iteration causing re-entrant apply observer with states that technically don't read
        // `initialRead` anymore. Note that it is technically possible to cause the same issue
        // with writing states from different threads as well.
        val derivedStates =
            Array(3) {
                derivedStateOf {
                    if (state.value >= 2) return@derivedStateOf
                    if (initialRead.value) return@derivedStateOf
                    if (state.value < 2) {
                        state.value++
                    }
                }
            }

        observer.observeReads(Unit, {}) { derivedStates.forEach { it.value } }

        initialRead.value = false
    }

    private fun runSimpleTest(
        block: (modelObserver: SnapshotStateObserver, data: MutableState<Int>) -> Unit
    ) {
        val stateObserver = SnapshotStateObserver { it() }
        val state = mutableIntStateOf(0)
        try {
            stateObserver.start()
            Snapshot.notifyObjectsInitialized()
            block(stateObserver, state)
            state.intValue++
            Snapshot.sendApplyNotifications()
        } finally {
            stateObserver.stop()
        }
    }

    // I2: a source whose reads are recorded by its observe() setup hook (not by the static
    // DataSource.recordDependency) must establish dependencies in the SnapshotStateObserver
    // path too - that path is what measure, layout, draw and semantics all run through.
    //
    // isolating = false deliberately: read tracking is a correctness property independent of
    // frame isolation, so it must work with the flag off. It also means the observer
    // registers a GLOBAL apply observer, so invalidateDependants reaches it immediately.
    @Test
    fun setupHookSourceReadsAreObservedThroughTheDeliveryDomainsContext() {
        val source = BufferedTestDataSource()
        val holder = SnapshotHolder(DataSourceContext(source), isolating = false)
        val scope = ValueWrapper("scope")
        var changes = 0
        val stateObserver = SnapshotStateObserver(holder) { it() }
        try {
            stateObserver.start()
            stateObserver.observeReads(scope, { changes++ }) { source.read("a") }
            source.write("a", 1)
            source.advanceAndInvalidate()
            assertEquals(1, changes)
        } finally {
            stateObserver.stop()
        }
    }

    // The wildcard path, through the same route.
    @Test
    fun theWildcardTokenInvalidatesAnObservedScopeThatReadTheSource() {
        val source = BufferedTestDataSource()
        val holder = SnapshotHolder(DataSourceContext(source), isolating = false)
        val scope = ValueWrapper("scope")
        var changes = 0
        val stateObserver = SnapshotStateObserver(holder) { it() }
        try {
            stateObserver.start()
            stateObserver.observeReads(scope, { changes++ }) { source.read("a") }
            source.write("a", 1)
            source.loseExactDelta()
            source.advanceAndInvalidate()
            assertEquals(1, changes)
        } finally {
            stateObserver.stop()
        }
    }

    // Parity guard: a substrate-only delivery domain must behave exactly as a null one.
    @Test
    fun aSubstrateOnlyDeliveryDomainObservesExactlyAsBefore() {
        val state = mutableIntStateOf(0)
        val holder = SnapshotHolder(DataSourceContext(), isolating = false)
        val scope = ValueWrapper("scope")
        var changes = 0
        val stateObserver = SnapshotStateObserver(holder) { it() }
        try {
            stateObserver.start()
            stateObserver.observeReads(scope, { changes++ }) { state.intValue }
            Snapshot.notifyObjectsInitialized()
            state.intValue++
            Snapshot.sendApplyNotifications()
            assertEquals(1, changes)
        } finally {
            stateObserver.stop()
        }
    }

    // Nested observeReads through a foreign context: the inner scope's reads must not be
    // attributed to the outer scope, and both must be invalidated by their own key.
    @Test
    fun nestedObserveReadsThroughAContextAttributeToTheirOwnScopes() {
        val source = BufferedTestDataSource()
        val holder = SnapshotHolder(DataSourceContext(source), isolating = false)
        val outer = ValueWrapper("outer")
        val inner = ValueWrapper("inner")
        var outerChanges = 0
        var innerChanges = 0
        val stateObserver = SnapshotStateObserver(holder) { it() }
        try {
            stateObserver.start()
            stateObserver.observeReads(outer, { outerChanges++ }) {
                source.read("o")
                stateObserver.observeReads(inner, { innerChanges++ }) { source.read("i") }
            }
            source.write("i", 1)
            source.advanceAndInvalidate()
            assertEquals(0, outerChanges, "the outer scope never read \"i\"")
            assertEquals(1, innerChanges)
        } finally {
            stateObserver.stop()
        }
    }

    // White-box test for the recordRead guard itself, bypassing DerivedState.kt entirely.
    // derivedStateObservers() (DerivedState.kt:387) is internal and reachable from this test
    // source set. Driving start()/done() manually reproduces exactly what a real derivedStateOf
    // recalculation would trigger on ObservedScopeMap.derivedStateObserver
    // (SnapshotStateObserver.kt:411-420) without going through an actual calculation, so there is
    // no local calculation observer merged above readObserver here - it is the top of the chain,
    // and DataSource.recordDependency reports exactly what the guard returns.
    @Test
    fun aReadDroppedByTheDerivedStateGuardReportsFalse() {
        val source = BufferedTestDataSource()
        val holder = SnapshotHolder(DataSourceContext(source), isolating = false)
        val scope = ValueWrapper("scope")
        val marker = derivedStateOf { 0 } as DerivedState<*>
        val stateObserver = SnapshotStateObserver(holder) { it() }
        try {
            stateObserver.start()
            stateObserver.observeReads(scope, {}) {
                val observers = derivedStateObservers()
                observers.forEach { it.start(marker) }
                try {
                    assertFalse(
                        DataSource.recordDependency("x"),
                        "a read made while a derived-state recalculation is in progress must be " +
                            "reported as dropped",
                    )
                } finally {
                    observers.forEach { it.done(marker) }
                }
            }
        } finally {
            stateObserver.stop()
        }
    }

    // ObservedScopeMap.recordRead used to silently drop a read at the deriveStateScopeCount guard
    // (a bare `return`, no signal) rather than reporting that it did so. It now returns Boolean,
    // and readObserver propagates it instead of hardcoding true (SnapshotStateObserver.kt:172,
    // :441, :457) - matching Composition.recordReadOf, which already returns false in the same
    // case (see aReadDroppedByTheDerivedStateGuardReportsFalse above for a direct test of the
    // guard).
    //
    // The fix is not observable as a black-box test through DataSource.recordDependency from
    // inside an actual derivedStateOf recalculation, though: every such recalculation wraps the
    // calculation in observeDataSourceReads with DerivedState.kt's own local observer (:226-239),
    // which unconditionally returns true and is OR-merged with whatever readObserver reports - the
    // derived state legitimately needs the read for its own dependency tracking regardless of what
    // SnapshotStateObserver decides, so the composite true a caller observes there is correct, not
    // a lie. A non-StateObject identifier such as a plain source key also sets alwaysInvalid = true
    // (DerivedState.kt:229-231), so the calculation reruns on every access (see
    // aDerivedStateReadingACustomSourceShouldCacheBetweenReads below) rather than once - every
    // recorded result here is therefore true, confirmed by stack traces rooted in
    // DerivedSnapshotState.currentRecord. This test pins that externally visible merge behavior so
    // a future change to it does not go unnoticed.
    @Test
    fun dataSourceRecordDependencyStaysTrueInsideADerivedStateRecalculation() {
        val source = BufferedTestDataSource()
        val holder = SnapshotHolder(DataSourceContext(source), isolating = false)
        val scope = ValueWrapper("scope")
        val results = mutableListOf<Boolean>()
        val stateObserver = SnapshotStateObserver(holder) { it() }
        try {
            stateObserver.start()
            val derived = derivedStateOf {
                results.add(DataSource.recordDependency("inner"))
                0
            }
            stateObserver.observeReads(scope, {}) { derived.value }
            assertTrue(
                results.isNotEmpty() && results.all { it },
                "every read made during a derived-state recalculation must be reported as " +
                    "recorded, since the derived state's own local observer always records it",
            )
        } finally {
            stateObserver.stop()
        }
    }

    // The deferred caching gap, pinned so it is not forgotten. A foreign identifier sets
    // alwaysInvalid = true (DerivedState.kt:228-231) and is skipped by readableHash (:143-146), so
    // a
    // derivedStateOf touching a custom source recalculates on EVERY read rather than every change.
    // Fixing it needs a per-source generation hook that readableHash can consult -- separate
    // design.
    @Ignore
    @Test
    fun aDerivedStateReadingACustomSourceShouldCacheBetweenReads() {
        val source = BufferedTestDataSource()
        val context = DataSourceContext(source)
        var recalculations = 0
        val derived = derivedStateOf {
            recalculations++
            source.read("a")
        }
        context.observe(recordDependency = { true }, recordChange = null) {
            derived.value
            derived.value
            derived.value
        }
        assertEquals(
            1,
            recalculations,
            "a derived state should cache across reads when nothing changed",
        )
    }
}

// In k/js string is a primitive type and it doesn't have identityHashCode
private class ValueWrapper(val s: String)
