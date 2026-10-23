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

package androidx.compose.ui.benchmark

import androidx.compose.ui.platform.FlushCoroutineDispatcher
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@State(Scope.Benchmark)
open class FlushCoroutineDispatcherBenchmark {

    @Param("16", "64", "256")
    var size: Int = 0

    private lateinit var dispatcher: FlushCoroutineDispatcher
    private lateinit var scope: CoroutineScope
    private var checksum = 0

    @Setup
    fun setup() {
        scope = CoroutineScope(Dispatchers.Default + Job())
        dispatcher = FlushCoroutineDispatcher(scope)
    }

    @TearDown
    fun tearDown() {
        scope.cancel()
    }

    @Benchmark
    open fun dispatchAndFlush(): Int {
        checksum = 0
        for (i in 0 until size) {
            val index = i
            val r = Runnable{
                checksum += index + 1
            }
            dispatcher.dispatch(kotlin.coroutines.EmptyCoroutineContext,r)
        }
        dispatcher.flush()
        return checksum
    }

    @Benchmark
    open fun dispatchCascadingAndFlush(): Int {
        checksum = 0
        // Dispatch tasks that spawn more tasks during flush
        for (i in 0 until size) {
            val index = i
            val r = Runnable {
                checksum += index + 1
                // Add another task during flush
                val r2 = Runnable {
                    checksum += index + 1
                }
                dispatcher.dispatch(kotlin.coroutines.EmptyCoroutineContext, r2)
            }
            dispatcher.dispatch(kotlin.coroutines.EmptyCoroutineContext, r)
        }
        dispatcher.flush()
        return checksum
    }

    @Benchmark
    open fun dispatchViaLaunchAndFlush(): Int {
        checksum = 0
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        for (i in 0 until size) {
            val index = i
            jobs.add(scope.launch(dispatcher) {
                checksum += index + 1
            })
        }
        dispatcher.flush()
        return checksum
    }
}
