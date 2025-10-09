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

package androidx.tracing.driver.wire

import androidx.tracing.driver.ProcessTrack
import androidx.tracing.driver.TraceContext
import kotlin.coroutines.CoroutineContext
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import kotlinx.coroutines.Dispatchers
import okio.blackholeSink
import okio.buffer

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = BenchmarkTimeUnit.SECONDS)
@State(Scope.Benchmark)
open class TracingDriverJvmBenchmark {
    private val disabledTraceContext =
        buildTraceContext(sink = buildInMemorySink(), isEnabled = false)
    private val disabledProcessTrack: ProcessTrack =
        disabledTraceContext.getOrCreateProcessTrack(id = 10, name = "Benchmark")
    private val enabledTraceContext =
        buildTraceContext(sink = buildInMemorySink(), isEnabled = true)
    private val enabledProcessTrack =
        enabledTraceContext.getOrCreateProcessTrack(id = 10, name = "Benchmark")

    @Benchmark
    open fun reference() {
        // Intentionally empty.
    }

    @Benchmark
    open fun traceSectionDisabled() {
        disabledProcessTrack.trace("benchmark") {
            // Do nothing
        }
    }

    @Benchmark
    open fun traceSectionEnabled() {
        enabledProcessTrack.trace("benchmark") {
            // Do nothing
        }
    }

    private fun buildTraceContext(
        sink: TraceSink,
        @Suppress("SameParameterValue") isEnabled: Boolean,
    ): TraceContext {
        return TraceContext(sink = sink, isEnabled = isEnabled)
    }

    fun buildInMemorySink(coroutineContext: CoroutineContext = Dispatchers.IO): TraceSink {
        return TraceSink(
            sequenceId = 1,
            bufferedSink = blackholeSink().buffer(),
            coroutineContext = coroutineContext,
        )
    }
}
