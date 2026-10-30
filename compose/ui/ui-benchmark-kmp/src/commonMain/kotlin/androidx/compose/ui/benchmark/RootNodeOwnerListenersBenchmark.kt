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

import androidx.compose.ui.node.RootNodeOwner
import androidx.compose.ui.util.fastForEach
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)

@State(Scope.Benchmark)
open class RootNodeOwnerListenersBenchmark {
    @Param("16", "64", "256") var size = 0

    private lateinit var root: RootNodeOwner
    private lateinit var listeners: List<() -> Unit>
    private lateinit var cascadingListeners: List<() -> Unit>
    private var checksum = 0

    @Setup
    fun setup() {
        root = createBenchmarkOwner()
        listeners = List(size) { index -> { checksum += index + 1 } }
        cascadingListeners =
            List(size) { index ->
                { root.owner.registerOnEndApplyChangesListener(listeners[index]) }
            }
    }

    @Benchmark
    open fun registerAndDispatch(): Int {
        checksum = 0
        listeners.fastForEach { listener ->
            root.owner.registerOnEndApplyChangesListener(listener)
        }
        root.owner.onEndApplyChanges()
        return checksum
    }

    @Benchmark
    open fun registerDuplicatesAndDispatch(): Int {
        checksum = 0
        repeat(4) {
            listeners.fastForEach { listener ->

                root.owner.registerOnEndApplyChangesListener(listener)
            }
        }
        root.owner.onEndApplyChanges()
        return checksum
    }

    @Benchmark
    open fun registerDuringDispatch(): Int {
        checksum = 0
        cascadingListeners.fastForEach { listener ->
            root.owner.registerOnEndApplyChangesListener(listener)
        }
        root.owner.onEndApplyChanges()
        return checksum
    }

    @TearDown
    fun tearDown() {
        root.dispose()
    }
}