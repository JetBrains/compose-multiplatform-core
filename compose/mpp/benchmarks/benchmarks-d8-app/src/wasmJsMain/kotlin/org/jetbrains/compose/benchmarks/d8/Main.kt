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

@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class)

package org.jetbrains.compose.benchmarks.d8

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.promise
import kotlinx.serialization.json.Json
import org.jetbrains.compose.benchmarks.ContentMeasurement
import org.jetbrains.compose.benchmarks.app.benchmarkContent
import org.jetbrains.compose.benchmarks.data.AverageFrameReport
import org.jetbrains.compose.benchmarks.data.BenchmarkCase
import org.jetbrains.compose.benchmarks.skiko.configureBenchmarkSkiko
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko

private const val Width = 1920
private const val Height = 1080
private const val TargetFps = 120
private const val WarmupCount = 100
private const val FrameCount = 1000

private val reportJson = Json { encodeDefaults = true }
private val benchmarkScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

// Importing the module must not start workloads: the host invokes an entry point explicitly.
fun main() = Unit

@JsExport
fun launch(benchmarkName: String): Promise<JsAny?> = benchmarkScope.promise {
    val report = measureCase(benchmarkName, warmupCount = WarmupCount, frameCount = FrameCount)
    println("BENCHMARK_REPORT:${reportJson.encodeToString(report)}")
    1.toJsNumber()
}

@JsExport
fun customLaunch(benchmarkName: String, frameCount: Int): Promise<JsAny?> = benchmarkScope.promise {
    require(frameCount > 0) { "frameCount must be positive" }
    measureCase(benchmarkName, warmupCount = 0, frameCount = frameCount)
    1.toJsNumber()
}

private suspend fun measureCase(
    benchmarkName: String,
    warmupCount: Int,
    frameCount: Int,
): AverageFrameReport {
    val case = requireNotNull(BenchmarkCase.entries.firstOrNull {
        it.id.equals(benchmarkName, ignoreCase = true)
    }) { "Unknown benchmark case: $benchmarkName" }
    require(case != BenchmarkCase.MultipleComponents) {
        "${case.id}: XML vector resources are not supported in D8"
    }

    configureBenchmarkSkiko()
    suspendAwaitSkiko()
    val measurement = ContentMeasurement(
        width = Width,
        height = Height,
        targetFps = TargetFps,
        warmupCount = warmupCount,
        content = benchmarkContent(case.id),
    )
    return AverageFrameReport(
        // Uuid.random() requires Web Crypto, which D8 does not provide.
        id = Uuid.fromLongs(Random.nextLong(), Random.nextLong()).toString(),
        name = case.id,
        width = Width,
        height = Height,
        targetFps = TargetFps,
        warmupCount = warmupCount,
        frameCount = frameCount,
        averageFrameInfo = measurement.measureAverageFrame(frameCount),
    )
}

@Suppress("SuspendCoroutineLacksCancellationGuarantees")
@OptIn(InternalSkikoApi::class)
private suspend fun suspendAwaitSkiko(): Unit = suspendCoroutine { continuation ->
    awaitSkiko.then(
        onFulfilled = { continuation.resume(Unit); null },
        onRejected = { continuation.resumeWithException(it.asJsException()); null },
    )
}
