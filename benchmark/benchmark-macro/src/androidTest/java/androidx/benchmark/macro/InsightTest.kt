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

    // TODO (b/377581661) use deeplink from metric
    private val deepLink =
        TraceDeepLink(
            outputRelativePath = "/fake/output/relative/path.perfetto-trace",
            selectionParams =
                TraceDeepLink.SelectionParams(
                    pid = 27246,
                    tid = 27246,
                    ts = 351868459760497,
                    dur = 24573541,
                    query = "SELECT 🐲\nFROM 🐉\nWHERE \ud83d\udc09.NAME = 'ハク'"
                )
        )

    private val canonicalTraceInsights =
        listOf(
            Insight(
                observedLabel = "123305107ns",
                deepLink = deepLink,
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
                deepLink = deepLink,
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
                deepLink = deepLink,
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
                    "seen in iterations: [6](uri:///fake/output/relative/path.perfetto-trace?selectionParams=eNoryEyxNTI3MjFTK0Gwim2NTQ0tzCxMTC3NzQxMLM3VUkqLbI1MTM2NTU0M1QpLU4sqbYNdfVydQ7RV3QxULd1ULQ1UnYxUDRzdgvx9kcQsLIFi4R6uQa4ognp-jr5AEWMXbVUjc1VXY1ULIHIDM4xUHd2AggBTViPI)(123305107ns)"
            ),
            InsightSummary(
                category =
                    "[JIT Activity](https://d.android.com/test#JIT_ACTIVITY) (expected: < 100000000ns)",
                observedV2 =
                    "seen in iterations: [6](file:///fake/output/relative/path.perfetto-trace)(328462261ns)",
                observedV3 =
                    "seen in iterations: [6](uri:///fake/output/relative/path.perfetto-trace?selectionParams=eNoryEyxNTI3MjFTK0Gwim2NTQ0tzCxMTC3NzQxMLM3VUkqLbI1MTM2NTU0M1QpLU4sqbYNdfVydQ7RV3QxULd1ULQ1UnYxUDRzdgvx9kcQsLIFi4R6uQa4ognp-jr5AEWMXbVUjc1VXY1ULIHIDM4xUHd2AggBTViPI)(328462261ns)"
            ),
            InsightSummary(
                category =
                    "[JIT compiled methods](https://d.android.com/test#JIT_COMPILED_METHODS) (expected: < 65 count)",
                observedV2 =
                    "seen in iterations: [6](file:///fake/output/relative/path.perfetto-trace)(150 count)",
                observedV3 =
                    "seen in iterations: [6](uri:///fake/output/relative/path.perfetto-trace?selectionParams=eNoryEyxNTI3MjFTK0Gwim2NTQ0tzCxMTC3NzQxMLM3VUkqLbI1MTM2NTU0M1QpLU4sqbYNdfVydQ7RV3QxULd1ULQ1UnYxUDRzdgvx9kcQsLIFi4R6uQa4ognp-jr5AEWMXbVUjc1VXY1ULIHIDM4xUHd2AggBTViPI)(150 count)"
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
                        packageName = "androidx.compose.integration.hero.macrobenchmark.target"
                    )
                )
                .isEqualTo(canonicalTraceInsights)
        }
    }

    @MediumTest
    @Test
    fun createInsightSummaries() {
        assertThat(canonicalTraceInsights.createInsightSummaries())
            .isEqualTo(canonicalTraceInsightSummary)
    }
}
