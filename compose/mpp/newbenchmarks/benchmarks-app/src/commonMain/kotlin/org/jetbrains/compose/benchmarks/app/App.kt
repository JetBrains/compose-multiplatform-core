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

import androidx.compose.runtime.Composable
import org.jetbrains.compose.benchmarks.MeasureApplicationRealFrames
import org.jetbrains.compose.benchmarks.report.BenchmarkReportType

@Composable
internal fun App(
    benchmarkCollectorUrl: String,
    testName: String,
    benchmarkType: BenchmarkReportType,
) {
    when (benchmarkType) {
        BenchmarkReportType.REAL_FRAMES -> MeasureApplicationRealFrames(
            id = testName,
            reportApiUrl = benchmarkCollectorUrl,
            warmupCount = 100,
            frameCount = 1000,
            content = benchmarkContent(testName),
        )
        BenchmarkReportType.STARTUP -> error("Startup measurement is not implemented in the app yet")
        else -> error("Unknown UI benchmark type: $benchmarkType")
    }
}
