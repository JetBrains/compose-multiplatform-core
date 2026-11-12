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
import org.jetbrains.compose.benchmarks.MeasureApplicationStartup
import org.jetbrains.compose.benchmarks.data.BenchmarkReportType

@Composable
internal fun App(
    benchmarkCollectorUrl: String,
    testName: String,
    benchmarkType: BenchmarkReportType,
    startupTiming: StartupTiming,
    id: String,
) {
    when (benchmarkType) {
        BenchmarkReportType.REAL_FRAMES -> MeasureApplicationRealFrames(
            id = id,
            name = testName,
            reportApiUrl = benchmarkCollectorUrl,
            warmupCount = 100,
            frameCount = 1000,
            content = benchmarkContent(testName),
        )
        BenchmarkReportType.STARTUP -> MeasureApplicationStartup(
            id = id,
            name = testName,
            mainMark = startupTiming.mainMark,
            timeToMain = startupTiming.timeToMain,
            reportApiUrl = benchmarkCollectorUrl,
            frameCount = 30,
            content = benchmarkContent(testName),
        )
        else -> error("Unknown UI benchmark type: $benchmarkType")
    }
}
