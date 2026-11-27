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

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Reads the live console log without treating a partially written line as a complete report. */
internal fun waitForStdoutReport(
    log: Path,
    reports: Path,
    id: String,
    started: Long,
    timeout: Double,
    applicationIsAlive: () -> Boolean,
    name: String? = null,
    mode: String? = null,
): Path = Files.newInputStream(log).use { input ->
    val prefix = if (mode == null) "BENCHMARK_ENVIRONMENT:" else "BENCHMARK_REPORT:"
    val pending = ByteArrayOutputStream()
    val buffer = ByteArray(8192)

    fun find(): Path? {
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return null
            for (index in 0 until count) {
                val byte = buffer[index]
                if (byte != '\n'.code.toByte()) {
                    pending.write(byte.toInt())
                    continue
                }
                val line = pending.toString(Charsets.UTF_8).trimEnd('\r')
                pending.reset()
                if (!line.startsWith(prefix)) continue
                val body = line.removePrefix(prefix)
                val document = Json.parseToJsonElement(body).jsonObject
                // Ignore records from another run, rather than assigning them the current run ID.
                if (document.text("id") != id) continue
                if (mode == null) {
                    check("runtime" in document && "type" !in document) {
                        "unexpected environment document in $log"
                    }
                } else {
                    check(document.text("name") == name && document.text("type") == mode) {
                        "unexpected report contents in $log"
                    }
                }
                val report = reports.resolve(id + "-" + (mode ?: "environment") + "-stdout.json")
                Files.writeString(report, body)
                return report
            }
        }
    }

    var report = find()
    while (report == null) {
        check((System.nanoTime() - started) / 1e9 < timeout) { "no stdout report before timeout; see $log" }
        if (!applicationIsAlive()) {
            return@use find() ?: error("application exited without a stdout report; see $log")
        }
        Thread.sleep(100)
        report = find()
    }
    report
}
