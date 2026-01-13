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

package androidx.compose.runtime.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.runtime.SnapshotFlowManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.filters.LargeTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * These benchmarks measure the amount of time needed to make multiple calls to
 * [Snapshot.sendApplyNotifications] from one thread while [snapshotFlow]s on a different thread
 * emit values. The aim of these benchmarks is to test the impact of lock contention between the
 * apply observers associated with a [snapshotFlow] and the logic in a [snapshotFlow] that handles
 * emitting values.
 *
 * These benchmarks were copied from [SnapshotFlowBenchmark] and adapted to be multithreaded.
 */
@LargeTest
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SnapshotFlowLockContentionBenchmark(
    private val snapshotFlowManagerKind: SnapshotFlowManagerSharing,
    private val n: Int,
) {
    enum class SnapshotFlowManagerSharing {
        /**
         * Make each [snapshotFlow] in the benchmark be backed by a distinct [SnapshotFlowManager].
         */
        NONE,

        /** Make all [snapshotFlow]s in the benchmark share the same [SnapshotFlowManager]. */
        MAXIMAL,
    }

    @get:Rule val benchmarkRule = BenchmarkRule()

    /**
     * A test in which there are [n] [snapshotFlow]s that each watch one of [n] distinct state
     * objects.
     */
    @Test
    fun eachSnapshotFlowWatchesOneStateObject() {
        val stateObjects = List(n) { mutableStateOf(false) }
        val jobs = Array<Job?>(n) { null }

        val manager =
            if (snapshotFlowManagerKind == SnapshotFlowManagerSharing.MAXIMAL) SnapshotFlowManager()
            else null
        var latch = CountDownLatch(n)

        for (i in 0 until n) {
            val flow =
                snapshotFlowFactory(manager) { stateObjects[i].value }.onEach { latch.countDown() }
            jobs[i] = flow.launchIn(CoroutineScope(Dispatchers.Main))
        }

        latch.await()

        benchmarkRule.measureRepeated {
            latch = CountDownLatch(n)
            (0 until n).forEach { i ->
                runWithMeasurementDisabled { stateObjects[i].value = !stateObjects[i].value }
                Snapshot.sendApplyNotifications()
            }
            latch.await()
        }

        manager?.dispose()
        jobs.forEach { it!!.cancel() }
    }

    /**
     * A test with [n] [snapshotFlow]s and [n] distinct state objects, in which each [snapshotFlow]
     * watches 10 state objects, and each state object is watched by 10 [snapshotFlow]s.
     */
    @Test
    fun eachSnapshotFlowWatchesTenStateObjects() {
        var generation = AtomicInteger(0)
        val stateObjects = List(n) { mutableIntStateOf(0) }
        val jobs = Array<Job?>(n) { null }

        val manager =
            if (snapshotFlowManagerKind == SnapshotFlowManagerSharing.MAXIMAL) SnapshotFlowManager()
            else null
        var latch = CountDownLatch(n)

        for (i in 0 until n) {
            val flow =
                snapshotFlowFactory(manager) {
                        listOf(
                            stateObjects[i].value,
                            stateObjects[(i + 1) % n].value,
                            stateObjects[(i + 2) % n].value,
                            stateObjects[(i + 3) % n].value,
                            stateObjects[(i + 4) % n].value,
                            stateObjects[(i + 5) % n].value,
                            stateObjects[(i + 6) % n].value,
                            stateObjects[(i + 7) % n].value,
                            stateObjects[(i + 8) % n].value,
                            stateObjects[(i + 9) % n].value,
                        )
                    }
                    .onEach { l ->
                        if (l.all { it == generation.get() }) {
                            latch.countDown()
                        }
                    }
            jobs[i] = flow.launchIn(CoroutineScope(Dispatchers.Main))
        }

        latch.await()

        benchmarkRule.measureRepeated {
            generation.addAndGet(1)
            latch = CountDownLatch(n)
            (0 until n).forEach { i ->
                runWithMeasurementDisabled { stateObjects[i].value = generation.get() }
                Snapshot.sendApplyNotifications()
            }
            latch.await()
        }

        manager?.dispose()
        jobs.forEach { it!!.cancel() }
    }

    companion object {
        // Like `snapshotFlow`, but with a nullable `manager` parameter.
        fun <T> snapshotFlowFactory(manager: SnapshotFlowManager?, block: () -> T): Flow<T> {
            return if (manager == null) {
                snapshotFlow(block)
            } else {
                snapshotFlow(manager, block)
            }
        }

        @Parameterized.Parameters(name = "snapshotFlowManagerSharing={0}, n={1}")
        @JvmStatic
        fun parameters() =
            listOf<Array<Any?>>(
                arrayOf(SnapshotFlowManagerSharing.NONE, 10),
                arrayOf(SnapshotFlowManagerSharing.MAXIMAL, 10),
                arrayOf(SnapshotFlowManagerSharing.NONE, 100),
                arrayOf(SnapshotFlowManagerSharing.MAXIMAL, 100),
            )
    }
}
