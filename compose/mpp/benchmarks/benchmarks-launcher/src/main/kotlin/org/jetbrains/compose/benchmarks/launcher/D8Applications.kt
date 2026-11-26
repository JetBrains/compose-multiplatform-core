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

package org.jetbrains.compose.benchmarks.launcher

import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.io.path.exists
import kotlin.io.path.readText

// The task publishes report.json atomically. The started marker excludes Gradle configuration and
// compilation from the measurement timeout. Keep watching until Gradle exits to detect task failures.
internal fun waitForD8Report(
    output: Path, name: String, timeout: Double, process: Process, onReport: (Path) -> Unit,
) {
    var started: Long? = null
    var collected = false
    fun collect() {
        val report = output.resolve("report.json")
        if (!collected && report.exists()) {
            val document = Json.parseToJsonElement(report.readText()).jsonObject
            check(document.text("id").isNotBlank() && document.text("name") == name &&
                document.text("type") == "averageFrame") { "unexpected D8 report contents: $report" }
            onReport(report)
            collected = true
        }
    }
    while (true) {
        collect()
        if (!process.isAlive) {
            collect()
            check(process.exitValue() == 0) { "D8 Gradle task failed (exit ${process.exitValue()}); see run log" }
            check(collected) { "D8 Gradle task exited without a report" }
            return
        }
        if (started == null && output.resolve("started").exists()) started = System.nanoTime()
        started?.let {
            check((System.nanoTime() - it) / 1e9 < timeout) { "no D8 completion before test timeout" }
        }
        Thread.sleep(100)
    }
}
