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

import androidx.benchmark.Insight
import androidx.benchmark.InsightSummary
import androidx.benchmark.TraceDeepLink
import androidx.benchmark.createInsightSummaries
import androidx.benchmark.macro.perfetto.queryStartupInsights
import androidx.benchmark.perfetto.PerfettoTraceProcessor
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InsightTest {
    private val api35ColdStart =
        createTempFileFromAsset(prefix = "api35_startup_cold_classinit", suffix = ".perfetto-trace")
            .absolutePath

    private val targetPackage = "androidx.compose.integration.hero.macrobenchmark.target"

    // TODO (b/377581661) take time ranges from SlowStartReason
    private fun startupDeepLinkOf(reasonId: String, tid: Int) =
        TraceDeepLink(
            outputRelativePath = "/fake/output/relative/path.perfetto-trace",
            selectionParams =
                TraceDeepLink.StartupSelectionParams(
                    packageName = targetPackage,
                    tid = tid,
                    selectionStart = null,
                    selectionEnd = null,
                    reasonId = reasonId,
                )
        )

    private val canonicalTraceInsights =
        listOf(
            Insight(
                observedLabel = "123305107ns",
                deepLink =
                    startupDeepLinkOf("POTENTIAL_CPU_CONTENTION_WITH_ANOTHER_PROCESS", tid = 27246),
                iterationIndex = 6,
                category =
                    Insight.Category(
                        titleUrl =
                            "https://d.android.com/test#POTENTIAL_CPU_CONTENTION_WITH_ANOTHER_PROCESS",
                        title = "Potential CPU contention with another process",
                        postTitleLabel = " (expected: < 100000000ns)"
                    )
            ),
            Insight(
                observedLabel = "328462261ns",
                deepLink = startupDeepLinkOf("JIT_ACTIVITY", tid = 27251),
                iterationIndex = 6,
                category =
                    Insight.Category(
                        titleUrl = "https://d.android.com/test#JIT_ACTIVITY",
                        title = "JIT Activity",
                        postTitleLabel = " (expected: < 100000000ns)"
                    )
            ),
            Insight(
                observedLabel = "150 count",
                deepLink = startupDeepLinkOf("JIT_COMPILED_METHODS", tid = 27251),
                iterationIndex = 6,
                category =
                    Insight.Category(
                        titleUrl = "https://d.android.com/test#JIT_COMPILED_METHODS",
                        title = "JIT compiled methods",
                        postTitleLabel = " (expected: < 65 count)"
                    )
            )
        )

    private val canonicalTraceInsightSummary =
        listOf(
            InsightSummary(
                category =
                    "[Potential CPU contention with another process](https://d.android.com/test#POTENTIAL_CPU_CONTENTION_WITH_ANOTHER_PROCESS) (expected: < 100000000ns)",
                observedV2 =
                    "seen in iterations: [6](file:///fake/output/relative/path.perfetto-trace)(123305107ns)",
                observedV3 =
                    "seen in iterations: [6](uri:///fake/output/relative/path.perfetto-trace?enablePlugins=android_startup&selectionParams=eNodx7EKwyAQANC_yegQSjtlCCIkUFQSS0e56mEk6IWLQz6_pW97B4QdEmooOECNTDleIlA56ESRa8PE0DJVsSGTKBCYPljDVoB30YATtq7lOPSP_nbvGOGk6n-3xint5vHppX15afS_Rvv37CY_auMmtXi7GKnW9Qv6Wi72)(123305107ns)"
            ),
            InsightSummary(
                category =
                    "[JIT Activity](https://d.android.com/test#JIT_ACTIVITY) (expected: < 100000000ns)",
                observedV2 =
                    "seen in iterations: [6](file:///fake/output/relative/path.perfetto-trace)(328462261ns)",
                observedV3 =
                    "seen in iterations: [6](uri:///fake/output/relative/path.perfetto-trace?enablePlugins=android_startup&selectionParams=eNoNx7EKhDAMBuC3cQwoyE0OclMdbioHTvJfG2qRJhIz-Pjnt30n0oHCHzSeINm05puStlMvpirOxeBVhXY2pYZk-mNJe4Md5LDC3nnN0_Aaxr4zxqWyPV9C3OZ3DN8Q1z-O5yTF)(328462261ns)"
            ),
            InsightSummary(
                category =
                    "[JIT compiled methods](https://d.android.com/test#JIT_COMPILED_METHODS) (expected: < 65 count)",
                observedV2 =
                    "seen in iterations: [6](file:///fake/output/relative/path.perfetto-trace)(150 count)",
                observedV3 =
                    "seen in iterations: [6](uri:///fake/output/relative/path.perfetto-trace?enablePlugins=android_startup&selectionParams=eNoNx8EKgkAQBuC38TiQEJ08pZCRGeR9-dsd1kV2RsY59Pj13b4dcUPmJyp3kGRa0pei1l0PpiLO2eBFhVY2pYpo-mGJa4Vt5LDM3nhJXXtpz6fGGIdK-P8-LuE6T6_xMfRhGpbb3L9_v1onGA==)(150 count)"
            ),
        )

    @MediumTest
    @Test
    fun queryStartupInsights() {
        PerfettoTraceProcessor.runSingleSessionServer(api35ColdStart) {
            assertThat(
                    queryStartupInsights(
                        helpUrlBase = "https://d.android.com/test#",
                        traceOutputRelativePath = "/fake/output/relative/path.perfetto-trace",
                        iteration = 6,
                        packageName = Packages.MISSING
                    )
                )
                .isEmpty()

            assertThat(
                    queryStartupInsights(
                        helpUrlBase = "https://d.android.com/test#",
                        traceOutputRelativePath = "/fake/output/relative/path.perfetto-trace",
                        iteration = 6,
                        packageName = targetPackage
                    )
                )
                .isEqualTo(canonicalTraceInsights)
        }
    }

    @MediumTest
    @Test
    fun createInsightSummaries() {
        assertThat(canonicalTraceInsights.createInsightSummaries().map { it.observedV3 })
            .isEqualTo(canonicalTraceInsightSummary.map { it.observedV3 })
    }
}
