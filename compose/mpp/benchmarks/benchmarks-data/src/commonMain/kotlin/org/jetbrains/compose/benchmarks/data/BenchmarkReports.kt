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

package org.jetbrains.compose.benchmarks.data

import kotlin.time.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BenchmarkReportType {
    @SerialName("startup")
    STARTUP,

    @SerialName("realFrames")
    REAL_FRAMES,

    @SerialName("averageFrame")
    AVERAGE_FRAME,

    @SerialName("fps")
    FPS,
}

interface BenchmarkReport {
    val id: String
    val name: String
    val type: BenchmarkReportType
}

@Serializable
data class StartupReport(
    override val id: String,
    override val name: String,
    override val type: BenchmarkReportType = BenchmarkReportType.STARTUP,
    val frameCount: Int,
    val timeToMain: Duration? = null,
    val timeToFirstFrame: Duration,
    val timeToNthFrame: Duration,
) : BenchmarkReport

@Serializable
data class RealFramesReport(
    override val id: String,
    override val name: String,
    override val type: BenchmarkReportType = BenchmarkReportType.REAL_FRAMES,
    val warmupCount: Int,
    val frameCount: Int,
    val frameTimes: List<Duration>,
    val totalDuration: Duration,
) : BenchmarkReport

@Serializable
data class FrameInfo(
    val cpu: Duration,
    val gpu: Duration,
    val fps: Double,
)

@Serializable
data class AverageFrameReport(
    override val id: String,
    override val name: String,
    override val type: BenchmarkReportType = BenchmarkReportType.AVERAGE_FRAME,
    val width: Int,
    val height: Int,
    val targetFps: Int,
    val warmupCount: Int,
    val frameCount: Int,
    val averageFrameInfo: FrameInfo,
) : BenchmarkReport

@Serializable
data class FpsReport(
    override val id: String,
    override val name: String,
    override val type: BenchmarkReportType = BenchmarkReportType.FPS,
    val width: Int,
    val height: Int,
    val targetFps: Int,
    val warmupCount: Int,
    val frameCount: Int,
    val frames: List<FrameInfo>,
) : BenchmarkReport
