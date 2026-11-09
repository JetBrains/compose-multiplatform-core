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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.time.Duration
import kotlin.time.TimeSource.Monotonic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.benchmarks.report.RealFramesReport
import org.jetbrains.compose.benchmarks.report.StartupReport

private val ReportingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

@Composable
fun MeasureApplicationStartup(
    id: String,
    mainMark: Monotonic.ValueTimeMark,
    timeToMain: Duration,
    reportApiUrl: String,
    frameCount: Int,
    content: @Composable () -> Unit,
) {
    val onComplete: (StartupResult) -> Unit = { result ->
        ReportingScope.launch {
            val report = StartupReport(
                id = id,
                frameCount = frameCount,
                timeToMain = timeToMain,
                timeToFirstFrame = result.timeOfFirstFrame - mainMark,
                timeToNthFrame = result.timeOfNthFrame - mainMark,
            )
            val client = createHttpClient()
            try {
                client.post(reportApiUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(report)
                }
            } finally {
                client.close()
            }
        }
    }

    MeasureStartup(frameCount, onComplete, content)
}

@Composable
fun MeasureApplicationRealFrames(
    id: String,
    reportApiUrl: String,
    frameCount: Int,
    content: @Composable () -> Unit,
) {
    val onComplete: (RealFramesResult) -> Unit = { result ->
        ReportingScope.launch {
            val report = RealFramesReport(
                id = id,
                frameCount = frameCount,
                frameTimes = result.frameTimes,
                totalDuration = result.totalDuration,
            )
            val client = createHttpClient()
            try {
                client.post(reportApiUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(report)
                }
            } finally {
                client.close()
            }
        }
    }

    MeasureRealFrames(frameCount, onComplete, content)
}