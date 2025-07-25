/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.wear.compose.material3.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.wear.compose.material3.macrobenchmark.common.BlankScreenBenchmark
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class BlankScreenBenchmarkTest :
    BenchmarkTestBase(
        compilationMode = CompilationMode.DEFAULT,
        macrobenchmarkScreen = BlankScreenBenchmark,
        actionSuffix = "BLANK_SCREEN_ACTIVITY",
        metrics = listOf(MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
    )
