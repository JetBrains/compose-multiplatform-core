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

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.jetbrains.compose.benchmarks.data.AverageFrameReport
import org.jetbrains.compose.benchmarks.data.BenchmarkReport
import org.jetbrains.compose.benchmarks.data.FpsReport
import org.jetbrains.compose.benchmarks.data.RealFramesReport
import org.jetbrains.compose.benchmarks.data.StartupReport
import org.jetbrains.compose.benchmarks.data.TargetEnvironment

internal interface Reporter {
    suspend fun sendEnvironment(report: TargetEnvironment)
    suspend fun sendReport(report: BenchmarkReport)

    companion object {
        fun create(reporterUrl: String?): Reporter =
            reporterUrl?.let { HttpReporter(it) } ?: StdoutReporter()
    }
}

private val reportJson = Json {
    explicitNulls = true
    encodeDefaults = true
}

private fun encodeReport(report: BenchmarkReport): String = when (report) {
    is StartupReport -> reportJson.encodeToString(report)
    is RealFramesReport -> reportJson.encodeToString(report)
    is AverageFrameReport -> reportJson.encodeToString(report)
    is FpsReport -> reportJson.encodeToString(report)
    else -> error("Unsupported benchmark report: ${report::class}")
}

private class HttpReporter(private val url: String) : Reporter {
    override suspend fun sendEnvironment(report: TargetEnvironment) {
        sendJson(reportJson.encodeToString(report), checkStatus = true)
    }

    override suspend fun sendReport(report: BenchmarkReport) {
        sendJson(encodeReport(report), checkStatus = false)
    }

    private fun createHttpClient(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(reportJson)
        }
    }

    private suspend fun sendJson(body: String, checkStatus: Boolean) {
        val client = createHttpClient()
        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (checkStatus) {
                check(response.status.isSuccess()) { "Environment collector returned ${response.status}" }
            }
        } finally {
            client.close()
        }
    }
}

private class StdoutReporter : Reporter {
    override suspend fun sendEnvironment(report: TargetEnvironment) {
        println("BENCHMARK_ENVIRONMENT:" + reportJson.encodeToString(report))
    }

    override suspend fun sendReport(report: BenchmarkReport) {
        println("BENCHMARK_REPORT:" + encodeReport(report))
    }
}
