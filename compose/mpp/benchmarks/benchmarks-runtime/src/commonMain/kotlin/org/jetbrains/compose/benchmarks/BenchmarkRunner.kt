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

package org.jetbrains.compose.benchmarks

import androidx.compose.runtime.Composable
import kotlin.time.Duration
import kotlin.time.TimeSource.Monotonic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.benchmarks.data.AverageFrameReport
import org.jetbrains.compose.benchmarks.data.FpsReport
import org.jetbrains.compose.benchmarks.data.RealFramesReport
import org.jetbrains.compose.benchmarks.data.StartupReport

private val ReportingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

@Composable
fun MeasureApplicationStartup(
    id: String,
    name: String,
    mainMark: Monotonic.ValueTimeMark,
    timeToMain: Duration?,
    collectorUrl: String?,
    frameCount: Int,
    content: @Composable () -> Unit,
) {
    val onComplete: (StartupResult) -> Unit = { result ->
        ReportingScope.launch {
            val reporter = Reporter.create(collectorUrl)
            val report = StartupReport(
                id = id,
                name = name,
                frameCount = frameCount,
                timeToMain = timeToMain,
                timeToFirstFrame = result.timeOfFirstFrame - mainMark,
                timeToNthFrame = result.timeOfNthFrame - mainMark,
            )
            reporter.sendReport(report)
        }
    }

    MeasureStartup(frameCount, onComplete, content)
}

@Composable
fun MeasureApplicationRealFrames(
    id: String,
    name: String,
    collectorUrl: String?,
    warmupCount: Int,
    frameCount: Int,
    content: @Composable () -> Unit,
) {
    val onComplete: (RealFramesResult) -> Unit = { result ->
        ReportingScope.launch {
            val reporter = Reporter.create(collectorUrl)
            val report = RealFramesReport(
                id = id,
                name = name,
                warmupCount = warmupCount,
                frameCount = frameCount,
                frameTimes = result.frameTimes,
                totalDuration = result.totalDuration,
            )
            reporter.sendReport(report)
        }
    }

    MeasureRealFrames(
        warmupCount = warmupCount,
        frameCount = frameCount,
        onComplete = onComplete,
        content = content,
    )
}

suspend fun MeasureRenderAverageFrame(
    id: String,
    name: String,
    collectorUrl: String?,
    width: Int,
    height: Int,
    targetFps: Int,
    warmupCount: Int,
    frameCount: Int,
    content: @Composable () -> Unit
) {
    val reporter = Reporter.create(collectorUrl)
    val measurement = ContentMeasurement(width, height, targetFps, warmupCount, content)
    val result = measurement.measureAverageFrame(frameCount)
    val report = AverageFrameReport(
        id = id,
        name = name,
        width = width,
        height = height,
        targetFps = targetFps,
        warmupCount = warmupCount,
        frameCount = frameCount,
        averageFrameInfo = result,
    )
    reporter.sendReport(report)
}

suspend fun MeasureRenderFps(
    id: String,
    name: String,
    collectorUrl: String?,
    width: Int,
    height: Int,
    targetFps: Int,
    warmupCount: Int,
    frameCount: Int,
    content: @Composable () -> Unit
) {
    val reporter = Reporter.create(collectorUrl)
    val measurement = ContentMeasurement(width, height, targetFps, warmupCount, content)
    val result = measurement.measureRenderFps(frameCount)
    val report = FpsReport(
        id = id,
        name = name,
        width = width,
        height = height,
        targetFps = targetFps,
        warmupCount = warmupCount,
        frameCount = frameCount,
        frames = result,
    )
    reporter.sendReport(report)
}
