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

import androidx.compose.runtime.mock.BufferedTestDataSource
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalComposeApi::class)
class DataSourceContextTests {
    /** A recording source for fan-out order/robustness assertions. */
    private class RecordingSource(val log: MutableList<String>, val name: String) : DataSource {
        override fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)?,
            block: () -> T,
        ): T {
            log.add("observe:$name")
            return block()
        }

        override fun <T> withTransaction(block: () -> T): T {
            log.add("transaction:$name")
            return block()
        }

        override fun advanceGlobalSnapshot(): Set<Any> {
            log.add("advance:$name")
            return emptySet()
        }

        override fun takeSnapshot(): DataSource.Snapshot =
            object : DataSource.Snapshot {
                override fun makeCurrent(): Any? = null

                override fun restoreCurrent(previous: Any?) {}

                override fun beginTransaction(): Any? {
                    log.add("begin:$name")
                    return null
                }

                override fun endTransaction(frame: Any?, cause: Throwable?) {
                    log.add("end:$name:${cause != null}")
                }

                override fun dispose() {
                    log.add("dispose:$name")
                }
            }
    }

    /** A source that hands out opaque identifiers from its advance. */
    private class AdvancingSource(private val identifiers: Set<Any>) : DataSource {
        override fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)?,
            block: () -> T,
        ): T = block()

        override fun <T> withTransaction(block: () -> T): T = block()

        override fun advanceGlobalSnapshot(): Set<Any> = identifiers

        override fun takeSnapshot(): DataSource.Snapshot =
            object : DataSource.Snapshot {
                override fun makeCurrent(): Any? = null

                override fun restoreCurrent(previous: Any?) {}

                override fun beginTransaction(): Any? = null

                override fun endTransaction(frame: Any?, cause: Throwable?) {}

                override fun dispose() {}
            }
    }

    /** Signals a fresh advance from inside its own advance, like a source that keeps producing. */
    private class ReSignallingSource : DataSource {
        var context: DataSourceContext? = null

        override fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)?,
            block: () -> T,
        ): T = block()

        override fun <T> withTransaction(block: () -> T): T = block()

        override fun advanceGlobalSnapshot(): Set<Any> {
            context?.scheduleAdvance()
            return emptySet()
        }

        override fun takeSnapshot(): DataSource.Snapshot =
            object : DataSource.Snapshot {
                override fun makeCurrent(): Any? = null

                override fun restoreCurrent(previous: Any?) {}

                override fun beginTransaction(): Any? = null

                override fun endTransaction(frame: Any?, cause: Throwable?) {}

                override fun dispose() {}
            }
    }

    @Test
    fun takeSnapshotReturnsTheContextsCompositeType() {
        val context = DataSourceContext()
        val unit = context.takeSnapshot()
        assertIs<DataSourceContext.Snapshot>(unit)
        unit.dispose()
    }

    @Test
    fun membersAreSubstrateFirstAndDeduped() {
        val log = mutableListOf<String>()
        val source = RecordingSource(log, "a")
        val context = DataSourceContext(source, source)
        assertEquals(2, context.members.size) // substrate + a (deduped)
        val unit = context.takeSnapshot()
        // The foreign member participates in the isolate lifecycle exactly once.
        unit.withTransaction {}
        assertEquals(
            listOf("begin:a", "end:a:false"),
            log.filter { it.startsWith("begin") || it.startsWith("end") },
        )
        unit.dispose()
    }

    @Test
    fun isolateEndsChildrenInReverseWithTheFailureAsCause() {
        val log = mutableListOf<String>()
        val a = RecordingSource(log, "a")
        val b = RecordingSource(log, "b")
        val context = DataSourceContext(a, b)
        val unit = context.takeSnapshot()
        runCatching { unit.withTransaction { error("boom") } }
        // b (last member) ends before a, both with the failure as cause.
        assertEquals(listOf("end:b:true", "end:a:true"), log.filter { it.startsWith("end:") })
        unit.dispose()
    }

    @Test
    fun observeAndIsolateFanOutOverMembersInOrder() {
        val log = mutableListOf<String>()
        val a = RecordingSource(log, "a")
        val b = RecordingSource(log, "b")
        val context = DataSourceContext(a, b)
        context.observe(recordDependency = { false }, recordChange = null) {}
        context.withTransaction {}
        assertEquals(listOf("observe:a", "observe:b"), log.filter { it.startsWith("observe") })
        assertEquals(listOf("transaction:a", "transaction:b"), log.filter { it.startsWith("transaction") })
    }

    @Test
    fun contextIsolateStillIsolatesSnapshotState() {
        val state = mutableStateOf(0)
        val context = DataSourceContext()
        context.withTransaction {
            state.value = 1
            // The substrate isolate is Snapshot.withMutableSnapshot: writes stay invisible
            // to the world until the block ends.
            Snapshot.global { assertEquals(0, state.value) }
        }
        assertEquals(1, state.value)
    }

    @Test
    fun receiverInvalidateDependantsReachesApplyObservers() {
        val token = Any()
        val source = RecordingSource(mutableListOf(), "src")
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        try {
            source.invalidateDependants(setOf(token))
            assertEquals(1, notified.count { token in it })
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun advanceDeliversArbitraryIdentifiersVerbatim() {
        val token = Any()
        val context = DataSourceContext(AdvancingSource(setOf(token, 42L, "text")))
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        try {
            context.advanceGlobalSnapshot()
            assertEquals(
                1,
                notified.count { token in it && 42L in it && "text" in it },
                "identifiers must reach apply observers unchanged, whatever their shape",
            )
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun advanceUnionsIdentifiersFromEveryMember() {
        val context = DataSourceContext(AdvancingSource(setOf("a")), AdvancingSource(setOf("b")))
        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        try {
            context.advanceGlobalSnapshot()
            assertEquals(1, notified.count { "a" in it && "b" in it })
        } finally {
            handle.dispose()
        }
    }

    // Parity guard: must pass before and after this task.
    @Test
    fun advanceOnASubstrateOnlyContextFlushesPendingGlobalWritesAndNothingElse() {
        val state = mutableStateOf(0)
        val context = DataSourceContext()
        val notified = mutableListOf<Set<Any>>()
        // Registering first also advances the global snapshot, so the observer cannot see
        // changes that predate this call.
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        try {
            state.value = 1 // a raw global write, not yet notified
            context.advanceGlobalSnapshot()
            assertEquals(1, notified.count { state in it })
            notified.clear()
            context.advanceGlobalSnapshot() // nothing pending now
            assertTrue(notified.isEmpty(), "an empty advance must deliver nothing")
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun theWildcardTokenInvalidatesEveryReaderOfTheSource() {
        val source = BufferedTestDataSource()
        val context = DataSourceContext(source)
        val recorded = mutableListOf<Any>()
        context.observe(
            recordDependency = {
                recorded.add(it)
                true
            },
            recordChange = null,
        ) {
            source.read("a")
        }
        assertTrue("a" in recorded, "the setup hook must record the key that was read")
        assertTrue(source.wildcard in recorded, "the setup hook must also record the wildcard")

        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified.add(changed) }
        try {
            source.write("a", 1)
            source.loseExactDelta()
            context.advanceGlobalSnapshot()
            assertEquals(
                1,
                notified.count { source.wildcard in it },
                "losing the exact delta must deliver the wildcard, which every reader depends on",
            )
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun bufferedWritesAreInvisibleUntilTheAdvancePublishesThem() {
        val source = BufferedTestDataSource()
        source.write("a", 1)
        assertEquals(null, source.publishedValue("a"), "a buffered write must not be visible")
        source.advanceGlobalSnapshot()
        assertEquals(1, source.publishedValue("a"))
    }

    // Behavioural proof that the substrate-only branch is equivalent to the fan-out branch:
    // substrate reads are still observed AND the ambient recorder chain is still installed.
    @Test
    fun aSubstrateOnlyContextObservesSubstrateReadsAndInstallsTheAmbientRecorder() {
        val state = mutableStateOf(0)
        val context = DataSourceContext()
        assertEquals(1, context.members.size, "precondition: substrate-only")
        val reads = mutableListOf<Any>()
        var ambientRecorded: Boolean? = null
        context.observe(
            recordDependency = {
                reads.add(it)
                true
            },
            recordChange = null,
        ) {
            state.value // a substrate read
            ambientRecorded = DataSource.recordDependency("foreign-token")
        }
        assertTrue(state in reads, "substrate reads must still be observed")
        assertTrue("foreign-token" in reads, "the ambient recorder chain must still be installed")
        assertEquals(true, ambientRecorded, "recordDependency must report that it recorded")
    }

    @Test
    fun aSubstrateOnlyContextStillIsolatesSnapshotState() {
        val state = mutableStateOf(0)
        val context = DataSourceContext()
        context.withTransaction {
            state.value = 1
            Snapshot.global { assertEquals(0, state.value) }
        }
        assertEquals(1, state.value)
    }

    // Nesting must fan out correctly BOTH times: the outer descent must not be corrupted by
    // the inner one resetting the shared wrapper's fields.
    @Test
    fun nestedObserveOnAMultiMemberContextFansOutOverEveryMemberBothTimes() {
        val log = mutableListOf<String>()
        val a = RecordingSource(log, "a")
        val b = RecordingSource(log, "b")
        val context = DataSourceContext(a, b)
        var innerRan = false
        var outerResumed = false
        context.observe(recordDependency = { false }, recordChange = null) {
            context.observe(recordDependency = { false }, recordChange = null) { innerRan = true }
            outerResumed = true
        }
        assertTrue(innerRan, "the nested observation must run")
        assertTrue(outerResumed, "the enclosing observation must resume after the nested one")
        assertEquals(
            listOf("observe:a", "observe:b", "observe:a", "observe:b"),
            log.filter { it.startsWith("observe") },
            "each descent must visit every foreign member exactly once",
        )
    }

    @Test
    fun nestedIsolateOnAMultiMemberContextFansOutOverEveryMemberBothTimes() {
        val log = mutableListOf<String>()
        val a = RecordingSource(log, "a")
        val b = RecordingSource(log, "b")
        val context = DataSourceContext(a, b)
        context.withTransaction { context.withTransaction {} }
        assertEquals(
            listOf("transaction:a", "transaction:b", "transaction:a", "transaction:b"),
            log.filter { it.startsWith("transaction") },
        )
    }

    @Test
    fun scheduleAdvanceWakesRegisteredDomainsAndIsClearedByTheDrain() {
        val source = BufferedTestDataSource()
        val context = DataSourceContext(source)
        var wakes = 0
        val handle = context.registerWake { wakes++ }
        try {
            assertFalse(context.hasPendingAdvance, "nothing signalled yet")
            source.write("a", 1)
            context.scheduleAdvance()
            assertEquals(1, wakes, "the signal must wake every registered domain")
            assertTrue(context.hasPendingAdvance)
            context.advanceGlobalSnapshot()
            assertFalse(context.hasPendingAdvance, "the drain must clear the signal")
            assertEquals(1, source.publishedValue("a"), "the drain must publish buffered writes")
        } finally {
            handle.dispose()
        }
    }

    // A domain constructed AFTER the signal must still learn of it: only a drain clears
    // `pendingAdvance`, so a wake registered while it is still set must replay it immediately.
    @Test
    fun registerWakeReplaysAnAlreadyPendingSignal() {
        val context = DataSourceContext(BufferedTestDataSource())
        context.scheduleAdvance()
        var wakes = 0
        val handle = context.registerWake { wakes++ }
        try {
            assertEquals(1, wakes, "a pending signal must be replayed to a newly registered wake")
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun repeatedScheduleAdvanceCallsCoalesceIntoOneWake() {
        val context = DataSourceContext(BufferedTestDataSource())
        var wakes = 0
        val handle = context.registerWake { wakes++ }
        try {
            repeat(5) { context.scheduleAdvance() }
            assertEquals(1, wakes, "repeated signals before a drain must coalesce")
            context.advanceGlobalSnapshot()
            context.scheduleAdvance()
            assertEquals(2, wakes, "a fresh signal after the drain must wake again")
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aDisposedWakeIsNoLongerCalled() {
        val context = DataSourceContext(BufferedTestDataSource())
        var wakes = 0
        val handle = context.registerWake { wakes++ }
        handle.dispose()
        context.scheduleAdvance()
        assertEquals(0, wakes)
    }

    // A signalled-but-undrained context must report pending so a scene can decide to render.
    @Test
    fun hasPendingAdvanceSurvivesUntilAnActualDrain() {
        val context = DataSourceContext(BufferedTestDataSource())
        context.scheduleAdvance()
        assertTrue(context.hasPendingAdvance)
        assertTrue(context.hasPendingAdvance, "reading it must not consume it")
        context.advanceGlobalSnapshot()
        assertFalse(context.hasPendingAdvance)
    }

    // The clear-FIRST ordering. A signal raised while the drain is running must survive it: the
    // member has fresh data that this pass did not publish. Were the flag cleared LAST, this
    // signal would be swallowed and that data would never be drained.
    @Test
    fun aSignalRaisedDuringTheDrainSurvivesIt() {
        val source = ReSignallingSource()
        val context = DataSourceContext(source)
        source.context = context
        context.advanceGlobalSnapshot()
        assertTrue(
            context.hasPendingAdvance,
            "a scheduleAdvance() made during the drain must leave the flag set",
        )
    }

    /** Records through the STATIC recorder, like MapDataSource — the other source shape. */
    private class StaticRecorderSource : DataSource {
        fun read(key: String) {
            DataSource.recordDependency(key)
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

    @Test
    fun stockWithoutReadObservationSuppressesAHookBasedSource() {
        val source = BufferedTestDataSource()
        val context = DataSourceContext(source)
        val recorded = mutableListOf<Any>()
        context.observe(
            recordDependency = {
                recorded.add(it)
                true
            },
            recordChange = null,
        ) {
            source.read("tracked")
            Snapshot.withoutReadObservation { source.read("untracked") }
        }
        assertTrue("tracked" in recorded, "a normal read must still be recorded")
        assertFalse(
            "untracked" in recorded,
            "withoutReadObservation must suppress a source that captured its recorder",
        )
    }

    @Test
    fun stockWithoutReadObservationSuppressesAStaticRecorderSource() {
        val source = StaticRecorderSource()
        val context = DataSourceContext(source)
        val recorded = mutableListOf<Any>()
        context.observe(
            recordDependency = {
                recorded.add(it)
                true
            },
            recordChange = null,
        ) {
            source.read("tracked")
            Snapshot.withoutReadObservation { source.read("untracked") }
        }
        assertTrue("tracked" in recorded)
        assertFalse("untracked" in recorded)
    }

    @Test
    fun dataSourceWithoutReadObservationSuppressesAHookBasedSource() {
        val source = BufferedTestDataSource()
        val context = DataSourceContext(source)
        val recorded = mutableListOf<Any>()
        context.observe(
            recordDependency = {
                recorded.add(it)
                true
            },
            recordChange = null,
        ) {
            source.read("tracked")
            DataSource.withoutReadObservation { source.read("untracked") }
        }
        // Positive control: without this, the test would also pass if
        // DataSource.withoutReadObservation
        // suppressed ALL recording rather than just the paused block's.
        assertTrue("tracked" in recorded, "a read outside the paused block must still be recorded")
        assertFalse("untracked" in recorded)
    }

    /**
     * `{ false }` is not a floor: a nested `observe` inside a paused block re-enables recording,
     * because [ComposeSnapshot.observe] installs its own non-null read observer -- the same way
     * stock deliberately resets `isPaused` in `observeReads` -- and [DataSource.recordDependency]'s
     * merge in `withObservationRecorders` folds the nested recorder in ahead of the paused `{ false
     * }` chain link left behind by [DataSource.withoutReadObservation]. This guards that the paused
     * link stays in the chain doing nothing (so the nested reads reach only the nested recorder,
     * not any recorder from further out) rather than being skipped by accident.
     */
    @Test
    fun aNestedObserveInsideAPausedBlockReEnablesRecording() {
        val context = DataSourceContext()
        val outerRecorded = mutableListOf<Any>()
        val innerRecorded = mutableListOf<Any>()
        context.observe(
            recordDependency = {
                outerRecorded.add(it)
                true
            },
            recordChange = null,
        ) {
            DataSource.withoutReadObservation {
                DataSource.recordDependency("suppressed") // paused: reaches neither recorder
                context.observe(
                    recordDependency = {
                        innerRecorded.add(it)
                        true
                    },
                    recordChange = null,
                ) {
                    DataSource.recordDependency("nested")
                }
            }
        }
        assertFalse("suppressed" in outerRecorded)
        assertFalse("suppressed" in innerRecorded)
        assertTrue(
            "nested" in innerRecorded,
            "a nested observe must re-enable recording despite the enclosing pause",
        )
        assertFalse(
            "nested" in outerRecorded,
            "the paused outer scope must not also receive the nested observe's reads -- that is " +
                "what threadDependencyRecorder.set { false } in withoutReadObservation guards",
        )
    }
}
