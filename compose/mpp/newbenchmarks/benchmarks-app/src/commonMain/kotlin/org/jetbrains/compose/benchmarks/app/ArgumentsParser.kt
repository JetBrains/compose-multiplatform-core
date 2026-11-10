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
import org.jetbrains.compose.benchmarks.app.benchmarks.animation.AnimatedVisibility
import org.jetbrains.compose.benchmarks.app.benchmarks.canvasdrawing.CanvasDrawing
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.components.MainUiNoImageUseModel
import org.jetbrains.compose.benchmarks.app.benchmarks.heavyshader.HeavyShader
import org.jetbrains.compose.benchmarks.app.benchmarks.lazygrid.LazyGrid
import org.jetbrains.compose.benchmarks.app.benchmarks.multipleComponents.MultipleComponentsExample
import org.jetbrains.compose.benchmarks.app.benchmarks.textlayout.TextLayout
import org.jetbrains.compose.benchmarks.app.benchmarks.visualeffects.NYContent
import org.jetbrains.compose.benchmarks.report.BenchmarkReportType


internal fun parseBenchmarkType(value: String): BenchmarkReportType = when (value) {
    "startup" -> BenchmarkReportType.STARTUP
    "realFrames" -> BenchmarkReportType.REAL_FRAMES
    "averageFrame" -> BenchmarkReportType.AVERAGE_FRAME
    "fps" -> BenchmarkReportType.FPS
    else -> error("Unknown benchmark type: $value")
}

internal fun parseArguments(args: Array<String>): Triple<String, String, BenchmarkReportType> {
    require(args.size == 3) {
        "Usage: benchmarks-app <benchmarkCollectorUrl> <test-name> <benchmark-type>"
    }
    return Triple(args[0], args[1], parseBenchmarkType(args[2]))
}

internal fun benchmarkContent(testName: String): @Composable () -> Unit = when (testName) {
    "AnimatedVisibility" -> { { AnimatedVisibility() } }
    "LazyGrid" -> { { LazyGrid() } }
    "LazyGrid-ItemLaunchedEffect" -> { { LazyGrid(withLaunchedEffectInItem = true) } }
    "LazyGrid-SmoothScroll" -> { { LazyGrid(smoothScroll = true) } }
    "LazyGrid-SmoothScroll-ItemLaunchedEffect" -> {
        { LazyGrid(smoothScroll = true, withLaunchedEffectInItem = true) }
    }
    "VisualEffects" -> { { NYContent(1920, 1080) } }
    "LazyList" -> { { MainUiNoImageUseModel() } }
    "MultipleComponents" -> { { MultipleComponentsExample(isVectorGraphicsSupported = true) } }
    "MultipleComponents-NoVectorGraphics" -> {
        { MultipleComponentsExample(isVectorGraphicsSupported = false) }
    }
    "TextLayout" -> { { TextLayout() } }
    "CanvasDrawing" -> { { CanvasDrawing() } }
    "HeavyShader" -> { { HeavyShader() } }
    else -> error("Unknown benchmark case: $testName")
}