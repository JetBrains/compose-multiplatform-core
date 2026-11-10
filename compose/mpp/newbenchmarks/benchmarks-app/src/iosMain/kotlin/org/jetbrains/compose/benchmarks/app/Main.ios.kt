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

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

fun MainViewController(
    benchmarkCollectorUrl: String,
    testName: String,
    benchmarkType: String,
): UIViewController {
    val type = parseBenchmarkType(benchmarkType)
    require(!type.isRenderBenchmark()) { "Render benchmarks must be launched without UI" }
    return ComposeUIViewController {
        App(benchmarkCollectorUrl, testName, type)
    }
}

fun isRenderBenchmark(benchmarkType: String): Boolean =
    parseBenchmarkType(benchmarkType).isRenderBenchmark()

fun runRenderBenchmark(
    benchmarkCollectorUrl: String,
    testName: String,
    benchmarkType: String,
) {
    val type = parseBenchmarkType(benchmarkType)
    require(type.isRenderBenchmark()) { "Expected averageFrame or fps" }
    MainScope().launch {
        runRenderBenchmark(benchmarkCollectorUrl, testName, type)
    }
}
