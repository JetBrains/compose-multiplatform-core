/*
 * Copyright 2025 The Android Open Source Project
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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class DataSourceTests {
    @Test
    fun aDataSourceCanBeImplemented() {
        NoopDataSource()
    }

    @Test
    fun observeCallsThePassedBlock() {
        var blockCallCount = 0
        DataSource.observe({ false }) { blockCallCount++ }
        assertEquals(1, blockCallCount)
    }

    @Test
    fun observeCallsStartAndEnd() {
        val testDataSource =
            object : DataSource<Unit, Unit> by NoopDataSource() {
                var startCallCount = 0
                var endCallCount = 0

                override fun startObservation(recordDependency: (Any) -> Boolean) {
                    startCallCount++
                }

                override fun endObservation(previousObservation: Unit) {
                    endCallCount++
                }
            }
        val dataSourceRegistration = DataSource.register(testDataSource)
        try {
            DataSource.observe({ false }) {
                assertEquals(1, testDataSource.startCallCount)
                assertEquals(0, testDataSource.endCallCount)
            }
            assertEquals(1, testDataSource.startCallCount)
            assertEquals(1, testDataSource.endCallCount)
        } finally {
            dataSourceRegistration.dispose()
        }
    }

    @Test
    fun observeCallsEndInReversedOrder() {
        val startCalls = mutableListOf<DataSource<*, *>>()
        val endCalls = mutableListOf<DataSource<*, *>>()

        class TestDataSource : DataSource<Unit, Unit> by NoopDataSource() {
            override fun startObservation(recordDependency: (Any) -> Boolean) {
                startCalls.add(this)
            }

            override fun endObservation(previousObservation: Unit) {
                endCalls.add(this)
            }
        }

        val testDataSource1 = TestDataSource()
        val testDataSource2 = TestDataSource()
        val dataSourceRegistration1 = DataSource.register(testDataSource1)
        val dataSourceRegistration2 = DataSource.register(testDataSource2)
        try {
            DataSource.observe({ false }) {
                assertEquals(listOf(testDataSource1, testDataSource2), startCalls.toList())
            }
            assertEquals(listOf(testDataSource2, testDataSource1), endCalls.toList())
        } finally {
            dataSourceRegistration1.dispose()
            dataSourceRegistration2.dispose()
        }
    }

    @Test
    fun nestedObserveCallsDoNotMergeDependencyRecorders() {
        val testKey = "testKey"
        var latestDependencyRecorder: ((Any) -> Boolean)? = null
        val testDataSource =
            object : DataSource<Unit, Unit> by NoopDataSource() {
                override fun startObservation(recordDependency: (Any) -> Boolean) {
                    latestDependencyRecorder = recordDependency
                }
            }
        val outerDependencies = mutableListOf<Any>()
        val innerDependencies = mutableListOf<Any>()
        val dataSourceRegistration = DataSource.register(testDataSource)
        try {
            DataSource.observe({
                outerDependencies.add(it)
                true
            }) {
                DataSource.observe({
                    innerDependencies.add(it)
                    true
                }) {
                    latestDependencyRecorder?.invoke(testKey)
                    assertTrue(outerDependencies.isEmpty())
                    assertEquals(listOf(testKey), innerDependencies.toList())
                }
            }
        } finally {
            dataSourceRegistration.dispose()
        }
    }

    @Test
    fun withoutReadObservationCallsThePassedBlock() {
        var blockCallCount = 0
        DataSource.withoutReadObservation { blockCallCount++ }
        assertEquals(1, blockCallCount)
    }

    @Test
    fun withoutReadObservationCallsPauseAndResume() {
        val testDataSource =
            object : DataSource<Unit, Unit> by NoopDataSource() {
                var pauseCallCount = 0
                var resumeCallCount = 0

                override fun pauseCurrentObservation() {
                    pauseCallCount++
                }

                override fun resumeCurrentObservation(pausedObservation: Unit) {
                    resumeCallCount++
                }
            }
        val dataSourceRegistration = DataSource.register(testDataSource)
        try {
            DataSource.withoutReadObservation {
                assertEquals(1, testDataSource.pauseCallCount)
                assertEquals(0, testDataSource.resumeCallCount)
            }
            assertEquals(1, testDataSource.pauseCallCount)
            assertEquals(1, testDataSource.resumeCallCount)
        } finally {
            dataSourceRegistration.dispose()
        }
    }

    @Test
    fun withoutReadObservationCallsResumeInReversedOrder() {
        val pauseCalls = mutableListOf<DataSource<*, *>>()
        val resumeCalls = mutableListOf<DataSource<*, *>>()

        class TestDataSource : DataSource<Unit, Unit> by NoopDataSource() {
            override fun pauseCurrentObservation() {
                pauseCalls.add(this)
            }

            override fun resumeCurrentObservation(pausedObservation: Unit) {
                resumeCalls.add(this)
            }
        }

        val testDataSource1 = TestDataSource()
        val testDataSource2 = TestDataSource()
        val dataSourceRegistration1 = DataSource.register(testDataSource1)
        val dataSourceRegistration2 = DataSource.register(testDataSource2)
        try {
            DataSource.withoutReadObservation {
                assertEquals(listOf(testDataSource1, testDataSource2), pauseCalls.toList())
            }
            assertEquals(listOf(testDataSource2, testDataSource1), resumeCalls.toList())
        } finally {
            dataSourceRegistration1.dispose()
            dataSourceRegistration2.dispose()
        }
    }

    @Test
    fun isolateCallsThePassedBlock() {
        var blockCallCount = 0
        DataSource.isolate(null) { blockCallCount++ }
        assertEquals(1, blockCallCount)
    }

    @Test
    fun isolateCallsStartAndEnd() {
        val testDataSource =
            object : DataSource<Unit, Unit> by NoopDataSource() {
                var startCallCount = 0
                var endCallCount = 0

                override fun startIsolation(recordChange: ((Any) -> Unit)?) {
                    startCallCount++
                }

                override fun endIsolation(throwable: Throwable?, previousIsolation: Unit) {
                    endCallCount++
                }
            }
        val dataSourceRegistration = DataSource.register(testDataSource)
        try {
            DataSource.isolate(null) {
                assertEquals(1, testDataSource.startCallCount)
                assertEquals(0, testDataSource.endCallCount)
            }
            assertEquals(1, testDataSource.startCallCount)
            assertEquals(1, testDataSource.endCallCount)
        } finally {
            dataSourceRegistration.dispose()
        }
    }

    @Test
    fun isolateCallsEndInReversedOrder() {
        val startCalls = mutableListOf<DataSource<*, *>>()
        val endCalls = mutableListOf<DataSource<*, *>>()

        class TestDataSource : DataSource<Unit, Unit> by NoopDataSource() {
            override fun startIsolation(recordChange: ((Any) -> Unit)?) {
                startCalls.add(this)
            }

            override fun endIsolation(throwable: Throwable?, previousIsolation: Unit) {
                endCalls.add(this)
            }
        }

        val testDataSource1 = TestDataSource()
        val testDataSource2 = TestDataSource()
        val dataSourceRegistration1 = DataSource.register(testDataSource1)
        val dataSourceRegistration2 = DataSource.register(testDataSource2)
        try {
            DataSource.isolate(null) {
                assertEquals(listOf(testDataSource1, testDataSource2), startCalls.toList())
            }
        } finally {
            dataSourceRegistration1.dispose()
            dataSourceRegistration2.dispose()
        }
        assertEquals(listOf(testDataSource2, testDataSource1), endCalls.toList())
    }

    @Test
    fun isolateRethrowsOnFailure() {
        val testThrowableMessage = "Test throwable"
        assertFailsWith<Throwable>(testThrowableMessage) {
            DataSource.isolate(null) { throw Throwable(testThrowableMessage) }
        }
    }

    @Test
    fun nestedIsolateCallsDoNotMergeChangeRecorders() {
        val testKey = "testKey"
        var latestChangeRecorder: ((Any) -> Unit)? = null
        val testDataSource =
            object : DataSource<Unit, Unit> by NoopDataSource() {
                override fun startIsolation(recordChange: ((Any) -> Unit)?) {
                    latestChangeRecorder = recordChange
                }
            }
        var outerChanges = mutableListOf<Any>()
        var innerChanges = mutableListOf<Any>()
        val dataSourceRegistration = DataSource.register(testDataSource)
        try {
            DataSource.isolate({ outerChanges.add(it) }) {
                DataSource.isolate({ innerChanges.add(it) }) {
                    latestChangeRecorder?.invoke(testKey)
                    assertEquals(listOf(testKey), innerChanges.toList())
                    assertTrue(outerChanges.isEmpty())
                }
            }
        } finally {
            dataSourceRegistration.dispose()
        }
    }

    @Test
    fun invalidationsCanBeMade() {
        val testIdentifier = "testIdentifier"
        var invalidations = mutableListOf<Any>()
        DataSource.registerInvalidator { invalidations.add(it) }
        DataSource.invalidateDependants(setOf(testIdentifier))
        assertEquals(listOf(setOf(testIdentifier)), invalidations.toList())
    }
}

private class NoopDataSource : DataSource<Unit, Unit> {
    override fun startObservation(recordDependency: (Any) -> Boolean) {}

    override fun endObservation(previousObservation: Unit) {}

    override fun pauseCurrentObservation() {}

    override fun resumeCurrentObservation(pausedObservation: Unit) {}

    override fun startIsolation(recordChange: ((Any) -> Unit)?) {}

    override fun endIsolation(throwable: Throwable?, previousIsolation: Unit) {}
}

/**
 * These are meta-tests which should ensure that all tests above which use the KeyValueDataSource
 * can rely on a correct DataSource implementation.
 *
 * They can also serve as a reference for any new DataSource implementation.
 */
class KeyValueDataSourceTests {
    @Test
    fun aKeyValueDataSourceCanBeCreated() {
        KeyValueDataSource()
    }

    @Test
    fun aValueCanBeWrittenAndRead() {
        val testKey = "testKey"
        val testValue = 1
        val dataSource = KeyValueDataSource()
        dataSource[testKey] = testValue
        assertEquals(testValue, dataSource[testKey])
    }

    @Test
    fun endIsolationMergesChangesIntoTheGlobalContent() {
        val testKey = "testKey"
        val initialValue = 1
        val dataSource = KeyValueDataSource()
        dataSource[testKey] = initialValue
        dataSource.startIsolation(null)
        val updatedValue = 2
        try {
            dataSource[testKey] = updatedValue
            assertEquals(updatedValue, dataSource[testKey])
        } finally {
            dataSource.endIsolation(null, null)
        }
        assertEquals(updatedValue, dataSource[testKey])
    }

    @Test
    fun endIsolationDoesNotMergeChangesIntoTheGlobalContentOnFailure() {
        val testKey = "testKey"
        val initialValue = 1
        val dataSource = KeyValueDataSource()
        dataSource[testKey] = initialValue
        dataSource.startIsolation(null)
        val updatedValue = 2
        try {
            dataSource[testKey] = updatedValue
            assertEquals(updatedValue, dataSource[testKey])
        } finally {
            dataSource.endIsolation(Throwable("Test throwable"), null)
        }
        assertEquals(initialValue, dataSource[testKey])
    }

    @Test
    fun applyingASnapshotThatCollidesWithAGlobalChangeWillFail() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource

        val snapshot = dataSource.snapshot { state = 1 }
        state = 2
        assertFailsWith<IllegalStateException> { snapshot.apply() }
        assertEquals(2, state)
    }

    @Test
    fun applyingCollidingSnapshotsWillFail() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val snapshot1 = dataSource.snapshot { state = 1 }
        val snapshot2 = dataSource.snapshot { state = 2 }
        assertEquals(0, state)
        snapshot1.apply()
        assertEquals(1, state)
        assertFailsWith<IllegalStateException> { snapshot2.apply() }
        assertEquals(1, state)
    }

    @Test
    fun stateReadsCanBeObserved() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0

        val readStates = mutableListOf<Any>()
        dataSource.startObservation { readStates.add(it) }

        val result = state

        dataSource.endObservation(null)

        assertEquals(0, result)
        assertEquals(1, readStates.size)
        assertEquals("state", readStates[0])
    }

    @Test
    fun stateWritesCanBeObserved() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val writtenStates = mutableListOf<Any>()
        val snapshot = dataSource.takeMutableSnapshot { write -> writtenStates.add(write) }
        snapshot.enter {
            assertEquals(0, writtenStates.size)
            state = 2
            assertEquals(1, writtenStates.size)
        }
        assertEquals(1, writtenStates.size)
        assertEquals("state", writtenStates[0])
    }

    @Test
    fun invalidationsCanBeObserved() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val invalidator =
            DataSource.registerInvalidator { changes -> assertTrue("state" in changes) }
        val snapshot = dataSource.takeMutableSnapshot()
        try {
            snapshot.enter { state = 2 }
            snapshot.apply()
        } finally {
            invalidator.dispose()
        }
    }

    @Test
    fun globalChangesCanBeObserved() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0

        var applyObserved = false
        val invalidator =
            DataSource.registerInvalidator { changes ->
                assertTrue("state" in changes)
                applyObserved = true
            }
        try {
            state = 2

            dataSource.sendPendingInvalidations()

            assertTrue(applyObserved)
        } finally {
            invalidator.dispose()
        }
    }

    @Test
    fun aNestedMutableSnapshotCanBeTaken() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val snapshot = dataSource.takeMutableSnapshot()
        snapshot.enter { state = 1 }
        val nested = snapshot.takeNestedMutableSnapshot()
        nested.enter { state = 2 }

        assertEquals(0, state)
        snapshot.enter { assertEquals(1, state) }
        nested.enter { assertEquals(2, state) }
    }

    @Test
    fun aNestedMutableSnapshotCanBeAppliedToItsParent() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val snapshot = dataSource.takeMutableSnapshot()
        snapshot.enter { state = 1 }
        val nested = snapshot.takeNestedMutableSnapshot()

        nested.enter { state = 2 }
        assertEquals(0, state)
        snapshot.enter { assertEquals(1, state) }
        nested.enter { assertEquals(2, state) }

        nested.apply()
        assertEquals(0, state)
        snapshot.enter { assertEquals(2, state) }

        snapshot.apply()
        assertEquals(2, state)
    }

    @Test
    fun atomicChangesNest() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        dataSource.atomic {
            state = 1
            dataSource.atomic {
                state = 2

                dataSource.global { assertEquals(0, state) }
            }
            assertEquals(2, state)
            dataSource.global { assertEquals(0, state) }
        }
        assertEquals(2, state)
    }

    @Test
    fun siblingNestedMutableSnapshotsAreIsolatedFromEachOther() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val snapshot = dataSource.takeMutableSnapshot()
        snapshot.enter { state = 10 }

        val nested1 = snapshot.takeNestedMutableSnapshot()
        nested1.enter { state = 1 }
        val nested2 = snapshot.takeNestedMutableSnapshot()
        nested2.enter { state = 2 }

        assertEquals(0, state)
        snapshot.enter { assertEquals(10, state) }
        nested1.enter { assertEquals(1, state) }
        nested2.enter { assertEquals(2, state) }
    }

    @Test
    fun readingInANestedObservationNotifiesTheParent() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val read = HashSet<Any>()
        val dataSourceRegistration = DataSource.register(dataSource)
        try {
            DataSource.observe({ it: Any ->
                read.add(it)
                true
            }) {
                DataSource.observe({ false }) { state }
            }
        } finally {
            dataSourceRegistration.dispose()
        }
        assertContains(read, "state")
    }

    @Test
    fun readingInANestedObservationNotifiesNestedAndItsParent() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val parentRead = HashSet<Any>()
        val nestedRead = HashSet<Any>()
        val dataSourceRegistration = DataSource.register(dataSource)
        try {
            DataSource.observe({ it: Any ->
                parentRead.add(it)
                true
            }) {
                DataSource.observe({ it: Any ->
                    nestedRead.add(it)
                    true
                }) {
                    state
                }
            }
        } finally {
            dataSourceRegistration.dispose()
        }
        assertContains(nestedRead, "state")
        assertContains(parentRead, "state")
    }

    @Test
    fun withoutReadObservationDisablesAllCurrentDependencyRecorders() {
        val testDataSource = KeyValueDataSource()
        val testKey = "testKey"
        testDataSource[testKey] = 1
        val dataSourceRegistration = DataSource.register(testDataSource)
        try {
            DataSource.observe({ fail("recordDependency was called unexpectedly") }) {
                DataSource.withoutReadObservation { testDataSource[testKey] }
            }
        } finally {
            dataSourceRegistration.dispose()
        }
    }

    @Test
    fun writingToANestedSnapshotNotifiesTheParent() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val written = HashSet<Any>()
        val snapshot = dataSource.takeMutableSnapshot { written.add(it) }
        val nested = snapshot.takeNestedMutableSnapshot()
        nested.enter { state = 2 }
        assertContains(written, "state")
    }

    @Test
    fun writingToANestedSnapshotNotifiesNestedAndItsParent() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val parentWritten = HashSet<Any>()
        val nestedWritten = HashSet<Any>()
        val dataSourceRegistration = DataSource.register(dataSource)
        try {
            DataSource.isolate({ parentWritten.add(it) }) {
                DataSource.isolate({ nestedWritten.add(it) }) { state = 2 }
            }
        } finally {
            dataSourceRegistration.dispose()
        }
        assertContains(nestedWritten, "state")
        assertContains(parentWritten, "state")
    }

    @Test
    fun applyingANestedSnapshotDoesNotNotifyTheParent() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val written = HashSet<Any>()
        val snapshot = dataSource.takeMutableSnapshot { written.add(it) }
        val nested = snapshot.takeNestedMutableSnapshot()
        nested.enter { state = 2 }
        assertContains(written, "state")
        written.clear()
        nested.apply()
        assertTrue(written.isEmpty())
    }

    @Test
    fun snapshotsChangesCanMerge() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val snapshot1 = dataSource.takeMutableSnapshot()
        val snapshot2 = dataSource.takeMutableSnapshot()
        // Change the state to the same value in both snapshots
        snapshot1.enter { state = 1 }
        snapshot2.enter { state = 1 }

        // Still 0 until one of the snapshots is applied
        assertEquals(0, state)

        // Apply snapshot 1 should change the value to 1
        snapshot1.apply()
        assertEquals(1, state)

        // Applying snapshot 2 should succeed because it changed the value to the same value.
        snapshot2.apply()
        assertEquals(1, state)
    }

    @Test
    fun appliedSnapshotsDoNotRepeatChangeNotifications() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0
        val snapshot1 = dataSource.takeMutableSnapshot()
        val snapshot2 = dataSource.takeMutableSnapshot()
        val changeCount =
            dataSource.changeCountOf("state") {
                snapshot1.enter { state = 1 }
                snapshot2.enter { state = 1 }
                snapshot1.apply()
                snapshot2.apply()
            }
        assertEquals(1, changeCount)
    }

    @Test // Regression test for b/181162478
    fun nestedSnapshotsAreIsolated() {
        val dataSource = KeyValueDataSource()
        var state1: Int? by dataSource
        state1 = 0
        var state2: Int? by dataSource
        state2 = 0
        val parent = dataSource.takeMutableSnapshot()
        parent.enter { state1 = 1 }
        dataSource.atomic { state2 = 2 }
        val snapshot = parent.takeNestedMutableSnapshot()
        parent.apply()
        snapshot.enter {
            // Should see the change of state1
            assertEquals(1, state1)

            // But not the state change of state2
            assertEquals(0, state2)
        }
        snapshot.apply()
    }

    @Test // Regression test for b/200575924
    // Test copied from b/200575924 bu chrnie@foxmail.com
    fun nestedMutableSnapshotCanNotSeeOtherSnapshotChange() {
        val dataSource = KeyValueDataSource()
        var state: Int? by dataSource
        state = 0

        val snapshot1 = dataSource.takeMutableSnapshot()
        val snapshot2 = dataSource.takeMutableSnapshot()

        snapshot2.enter { state = 1 }

        snapshot1.enter { dataSource.atomic { assertEquals(0, state) } }
    }

    @Test
    fun canTakeNestedMutableSnapshotsFromInvalidator() {
        val dataSource = KeyValueDataSource()
        var takenSnapshot: KeyValueDataSource.Snapshot? = null
        val invalidator =
            DataSource.registerInvalidator { _ ->
                if (takenSnapshot != null) error("already took a nested snapshot")
                takenSnapshot = dataSource.takeMutableSnapshot()
            }

        try {
            var state: Int? by dataSource
            state = 0

            dataSource.sendPendingInvalidations()

            assertNotNull(takenSnapshot) { "snapshot was not taken by observer" }
        } finally {
            invalidator.dispose()
        }
    }

    @Test
    fun makeCurrentAndRestorePreviousWork() {
        val dataSource = KeyValueDataSource()
        val snapshot = dataSource.takeMutableSnapshot()
        val oldSnapshot = snapshot.makeCurrent()
        try {
            assertSame(
                snapshot,
                dataSource.currentSnapshot,
                "expected taken snapshot to be current",
            )
        } finally {
            snapshot.restorePrevious(oldSnapshot)
        }
        assertNotSame(
            snapshot,
            dataSource.currentSnapshot,
            "expected taken snapshot not to be current",
        )
    }

    @Test
    fun restorePreviousThrowsIfNotCurrent() {
        val dataSource = KeyValueDataSource()
        val snapshot = dataSource.takeMutableSnapshot()
        assertFailsWith<IllegalStateException> { snapshot.restorePrevious(null) }
    }

    @Test
    fun throwInMutableSnapshot() {
        assertFailsWith<IllegalStateException> {
            KeyValueDataSource().atomic { error("Test error") }
        }
    }

    @Test
    fun throwInApplyWithMutableSnapshot() {
        assertFailsWith<IllegalStateException> {
            val dataSource = KeyValueDataSource()
            var state: Int? by dataSource
            dataSource.atomic {
                dataSource.global { state = 1 }
                state = 2
            }
        }
    }
}

private class KeyValueDataSource() : DataSource<((Any) -> Boolean)?, KeyValueDataSource.Snapshot?> {
    operator fun get(key: Any): Any? {
        threadDependencyRecorder.get()?.invoke(key)
        return threadSnapshot.get().let {
            if (it != null) {
                it[key]
            } else {
                synchronized(globalSnapshot) { globalSnapshot[key] }
            }
        }
    }

    operator fun set(key: Any, value: Any?) {
        threadSnapshot.get().let {
            if (it != null) {
                it[key] = value
            } else {
                synchronized(globalSnapshot) { globalSnapshot[key] = value }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(@Suppress("unused") thisObj: Any?, property: KProperty<*>): T =
        this[property.name] as T

    operator fun <T> setValue(@Suppress("unused") thisObj: Any?, property: KProperty<*>, value: T) {
        this[property.name] = value
    }

    override fun startObservation(recordDependency: (Any) -> Boolean): ((Any) -> Boolean)? {
        val previousDependencyRecorder = threadDependencyRecorder.get()
        threadDependencyRecorder.set {
            recordDependency(it) or (previousDependencyRecorder?.invoke(it) == true)
        }
        return previousDependencyRecorder
    }

    override fun endObservation(previousObservation: ((Any) -> Boolean)?) {
        threadDependencyRecorder.set(previousObservation)
    }

    override fun pauseCurrentObservation(): ((Any) -> Boolean)? {
        val previousDependencyRecorder = threadDependencyRecorder.get()
        threadDependencyRecorder.set(null)
        return previousDependencyRecorder
    }

    override fun resumeCurrentObservation(pausedObservation: ((Any) -> Boolean)?) {
        checkPrecondition(threadDependencyRecorder.get() == null) {
            "Cannot resume observation while another observation is active"
        }
        threadDependencyRecorder.set(pausedObservation)
    }

    override fun startIsolation(recordChange: ((Any) -> Unit)?): Snapshot? {
        val previousSnapshot = threadSnapshot.get()
        val newSnapshot =
            previousSnapshot?.takeNestedMutableSnapshot(recordChange)
                ?: synchronized(globalSnapshot) {
                    globalSnapshot.takeNestedMutableSnapshot(recordChange)
                }
        newSnapshot.makeCurrent()
        return previousSnapshot
    }

    override fun endIsolation(throwable: Throwable?, previousIsolation: Snapshot?) {
        val snapshot = threadSnapshot.get()!!
        try {
            if (throwable == null) {
                snapshot.apply()
            }
        } finally {
            snapshot.restorePrevious(previousIsolation)
        }
    }

    fun takeMutableSnapshot(recordChange: ((Any) -> Unit)? = null): Snapshot {
        return threadSnapshot.get()?.takeNestedMutableSnapshot(recordChange)
            ?: synchronized(globalSnapshot) {
                globalSnapshot.takeNestedMutableSnapshot(recordChange)
            }
    }

    inline fun global(block: () -> Unit) {
        globalSnapshot.enter(block)
    }

    fun sendPendingInvalidations() {
        globalSnapshot.flushPendingInvalidations()
    }

    private val globalSnapshot = Snapshot(parent = null) {}
    private val threadSnapshot = ThreadLocal<Snapshot>()
    val currentSnapshot: Snapshot
        get() = threadSnapshot.get() ?: globalSnapshot

    private val threadDependencyRecorder = ThreadLocal<((Any) -> Boolean)?>()

    inner class Snapshot(
        private val parent: Snapshot?,
        private val changeRecorder: ((Any) -> Unit)?,
    ) {
        private val initialContent: Map<Any, Any?> =
            parent?.let { HashMap(it.content) } ?: emptyMap()
        private val content = HashMap<Any, Any?>(initialContent)
        private val changes: MutableSet<Any> = mutableSetOf<Any>()
        private var applied = false

        operator fun get(key: Any): Any? {
            return content[key]
        }

        operator fun set(key: Any, value: Any?) {
            if (!content.containsKey(key) || content[key] != value) {
                content[key] = value
                changes.add(key)
                changeRecorder?.invoke(key)
                parent?.changeRecorder?.invoke(key)
            }
        }

        fun takeNestedMutableSnapshot(recordChange: ((Any) -> Unit)? = null): Snapshot {
            return Snapshot(this, recordChange)
        }

        @OptIn(ExperimentalContracts::class)
        inline fun <T> enter(block: () -> T): T {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

            val previousSnapshot = makeCurrent()
            try {
                return block()
            } finally {
                restorePrevious(previousSnapshot)
            }
        }

        fun makeCurrent(): Snapshot? {
            val previousSnapshot = threadSnapshot.get()
            threadSnapshot.set(this)
            return previousSnapshot
        }

        fun restorePrevious(previousSnapshot: Snapshot?) {
            checkPrecondition(threadSnapshot.get() === this) {
                "Cannot restore snapshot; $this is not the current snapshot"
            }
            threadSnapshot.set(previousSnapshot)
        }

        fun apply() {
            checkPrecondition(!applied) { "A snapshot may not be applied multiple times" }
            if (parent === globalSnapshot) {
                synchronized(globalSnapshot) { mergeIntoParent() }
            } else {
                mergeIntoParent()
            }
            applied = true
        }

        private fun mergeIntoParent() {
            checkPrecondition(parent != null) {
                "Only nested snapshots can be merged into their parent"
            }
            checkPrecondition(
                changes.none { initialContent[it] != parent[it] && content[it] != parent[it] }
            ) {
                "Write conflict: The values for the following keys have changed concurrently in source and destination: ${
                    changes.filter { initialContent[it] != parent[it] && content[it] != parent[it] }
                        .map { "$it (was ${initialContent[it]}, now ${this[it]} VS ${parent[it]})" }
                }"
            }
            for (key in changes) {
                // We don't go through the setter on parent here because we want to avoid recording
                // changes again. Those have already been propagated up and recorded on the original
                // write.
                parent.content[key] = content[key]
            }
        }

        fun flushPendingInvalidations() {
            checkPrecondition(this === globalSnapshot) {
                "Only the global snapshot may flush its changes as invalidations"
            }
            synchronized(globalSnapshot) {
                DataSource.invalidateDependants(changes)
                changes.clear()
            }
        }
    }
}

private inline fun KeyValueDataSource.changeCountOf(key: Any, block: () -> Unit): Int {
    var changeCount = 0
    val invalidator =
        DataSource.registerInvalidator { changes -> if (changes.contains(key)) changeCount++ }
    try {
        block()
    } finally {
        sendPendingInvalidations()
        invalidator.dispose()
    }
    return changeCount
}

private inline fun <T> KeyValueDataSource.atomic(block: () -> T): T {
    val snapshot = takeMutableSnapshot()
    var hasFailed = false
    return try {
        snapshot.enter(block)
    } catch (throwable: Throwable) {
        hasFailed = true
        throw throwable
    } finally {
        if (!hasFailed) {
            snapshot.apply()
        }
    }
}

private inline fun KeyValueDataSource.snapshot(block: () -> Unit): KeyValueDataSource.Snapshot {
    val snapshot = takeMutableSnapshot()
    snapshot.enter(block)
    return snapshot
}
