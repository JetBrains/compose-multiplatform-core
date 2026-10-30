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
import kotlinx.benchmark.Warmup
import kotlin.random.Random

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@State(Scope.Benchmark)
open class SortedSetBenchmark {

    @Param("16", "64", "256")
    var size: Int = 0

    private lateinit var source: IntArray
    private lateinit var shuffledSource: IntArray
    private lateinit var filledSet: androidx.compose.ui.node.SortedSet<Int>

    private val comparator = Comparator<Int> { a, b -> a.compareTo(b) }

    @Setup
    fun setup() {
        source = IntArray(size) { it }
        shuffledSource = source.copyOf().apply { shuffle(Random(42)) }

        filledSet = androidx.compose.ui.node.SortedSet(comparator)
        for (i in 0 until size) {
            filledSet.add(source[i])
        }
    }

    @Benchmark
    open fun addAll(): Int {
        val s = androidx.compose.ui.node.SortedSet<Int>(comparator)
        var added = 0
        for (i in 0 until size) {
            if (s.add(source[i])) added++
        }
        return added
    }

    @Benchmark
    open fun removeShuffled(): Int {
        val s = androidx.compose.ui.node.SortedSet<Int>(comparator)
        for (i in 0 until size) s.add(source[i])
        var removed = 0
        for (i in 0 until size) {
            if (s.remove(shuffledSource[i])) removed++
        }
        return removed
    }

    @Benchmark
    open fun popAll(): Int {
        val s = androidx.compose.ui.node.SortedSet<Int>(comparator)
        for (i in 0 until size) s.add(source[i])
        var sum = 0
        while (!s.isEmpty()) {
            sum += s.first()
            s.remove(s.first())
        }
        return sum
    }

    @Benchmark
    open fun containsAll(): Int {
        var found = 0
        for (i in 0 until size) {
            if (filledSet.contains(source[i])) found++
        }
        return found
    }
}