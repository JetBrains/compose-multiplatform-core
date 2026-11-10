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

package org.jetbrains.compose.benchmarks.app

import org.jetbrains.compose.benchmarks.MeasureRenderAverageFrame
import org.jetbrains.compose.benchmarks.MeasureRenderFps
import org.jetbrains.compose.benchmarks.report.BenchmarkReportType

internal fun BenchmarkReportType.isRenderBenchmark(): Boolean =
    this == BenchmarkReportType.AVERAGE_FRAME || this == BenchmarkReportType.FPS

internal suspend fun runRenderBenchmark(
    benchmarkCollectorUrl: String,
    testName: String,
    benchmarkType: BenchmarkReportType,
) {
    val content = benchmarkContent(testName)
    when (benchmarkType) {
        BenchmarkReportType.AVERAGE_FRAME -> MeasureRenderAverageFrame(
            id = testName,
            reportApiUrl = benchmarkCollectorUrl,
            width = 1920,
            height = 1080,
            targetFps = 120,
            warmupCount = 100,
            frameCount = 1000,
            content = content,
        )
        BenchmarkReportType.FPS -> MeasureRenderFps(
            id = testName,
            reportApiUrl = benchmarkCollectorUrl,
            width = 1920,
            height = 1080,
            targetFps = 120,
            warmupCount = 100,
            frameCount = 1000,
            content = content,
        )
        else -> error("Unknown render benchmark type: $benchmarkType")
    }
}
