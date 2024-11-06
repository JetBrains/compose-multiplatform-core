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

package androidx.benchmark.macro

import androidx.benchmark.DeviceInfo
import androidx.benchmark.json.BenchmarkData.TestResult.SingleMetricResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 31)
@RunWith(AndroidJUnit4::class)
class RuntimeImageTest {
    private val className = RuntimeImageTest::class.java.name

    private fun captureRecyclerViewListStartupMetrics(
        testName: String,
    ): Map<String, SingleMetricResult> =
        macrobenchmarkWithStartupMode(
                uniqueName = "${className}_$testName",
                className = className,
                testName = testName,
                packageName = Packages.TARGET,
                metrics = listOf(ArtMetric()),
                compilationMode = CompilationMode.None(),
                iterations = 3,
                experimentalConfig = null,
                startupMode = StartupMode.COLD,
                setupBlock = {},
                measureBlock = {
                    startActivityAndWait {
                        it.setPackage(packageName)
                        it.action =
                            "androidx.benchmark.integration.macrobenchmark.target.RECYCLER_VIEW"
                        it.putExtra("ITEM_COUNT", 5)
                    }
                }
            )
            .metrics

    @LargeTest
    @Test
    fun classInitCount() {
        assumeTrue("Test requires runtime image support", DeviceInfo.supportsRuntimeImages)
        assumeTrue("Test requires class init tracing", DeviceInfo.supportsClassInitTracing)

        val testName = RuntimeImageTest::classInitCount.name
        val results = captureRecyclerViewListStartupMetrics(testName)

        val classInitCount = results["artClassInitCount"]!!.runs

        // observed >700 in practice, lower threshold used to be resilient
        assertTrue(
            classInitCount.all { it > 500 },
            "too few class inits seen, observed: $classInitCount"
        )
    }
}
