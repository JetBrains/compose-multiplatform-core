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

package org.jetbrains.compose.benchmarks.collector

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.benchmarks.data.AverageFrameReport
import org.jetbrains.compose.benchmarks.data.BenchmarkReport
import org.jetbrains.compose.benchmarks.data.BenchmarkReportType
import org.jetbrains.compose.benchmarks.data.FpsReport
import org.jetbrains.compose.benchmarks.data.RealFramesReport
import org.jetbrains.compose.benchmarks.data.StartupReport
import org.jetbrains.compose.benchmarks.data.TargetEnvironment

fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: benchmarks-collector <port> <output-directory>" }
    val port = requireNotNull(args[0].toIntOrNull()?.takeIf { it in 1..65535 }) {
        "Port must be an integer between 1 and 65535"
    }
    val outputDirectory = Path.of(args[1]).toAbsolutePath()
    Files.createDirectories(outputDirectory)

    embeddedServer(CIO, host = "127.0.0.1", port = port) {
        collectReports(outputDirectory)
    }.start(wait = true)
}

internal fun Application.collectReports(outputDirectory: Path) {
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
    }
    routing {
        post("/environment") {
            val body = call.receiveText()
            val environment = try {
                Json.decodeFromString<TargetEnvironment>(body)
            } catch (_: IllegalArgumentException) {
                call.respondText("Invalid target environment", status = HttpStatusCode.BadRequest)
                return@post
            }
            if (!environment.id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                call.respondText("Invalid environment id", status = HttpStatusCode.BadRequest)
                return@post
            }
            val fileName = withContext(Dispatchers.IO) {
                saveDocument(outputDirectory, environment.id, "environment", body)
            }
            println("Target environment saved: ${outputDirectory.resolve(fileName)}")
            call.respondText(fileName, status = HttpStatusCode.Created)
        }
        post("/reports") {
            val body = call.receiveText()
            val report = try {
                Json.decodeFromString(BenchmarkReportSerializer, body)
            } catch (_: IllegalArgumentException) {
                call.respondText("Invalid benchmark report", status = HttpStatusCode.BadRequest)
                return@post
            }

            if (!report.id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                call.respondText("Invalid report id", status = HttpStatusCode.BadRequest)
                return@post
            }

            val fileName = withContext(Dispatchers.IO) {
                val fileName = saveReport(outputDirectory, report.id, report.type, body)
                printReport(report, outputDirectory.resolve(fileName))
                fileName
            }
            call.respondText(fileName, status = HttpStatusCode.Created)
        }
    }
}

private fun printReport(report: BenchmarkReport, path: Path) {
    println(buildString {
        appendLine("${report.name} (${report.type}), id: ${report.id}")
        when (report) {
            is StartupReport -> {
                appendLine("Frames after first: ${report.frameCount}")
                appendLine("Process to main: ${report.timeToMain ?: "unavailable"}")
                appendLine("Main to first frame: ${report.timeToFirstFrame}")
                appendLine("Main to Nth frame: ${report.timeToNthFrame}")
            }
            is RealFramesReport -> {
                appendLine("Frames: ${report.frameCount}, warmup: ${report.warmupCount}")
                appendLine("Total duration: ${report.totalDuration}")
                if (report.frameCount > 0 && report.totalDuration > Duration.ZERO) {
                    appendLine("Average frame time: ${report.totalDuration / report.frameCount}")
                    appendLine("FPS: ${report.frameCount / report.totalDuration.toDouble(DurationUnit.SECONDS)}")
                }
            }
            is AverageFrameReport -> {
                appendLine("Size: ${report.width}x${report.height}, target FPS: ${report.targetFps}")
                appendLine("Frames: ${report.frameCount}, warmup: ${report.warmupCount}")
                appendLine("Average CPU time: ${report.averageFrameInfo.cpu}")
                appendLine("Average GPU time: ${report.averageFrameInfo.gpu}")
                appendLine("FPS: ${report.averageFrameInfo.fps}")
            }
            is FpsReport -> {
                appendLine("Size: ${report.width}x${report.height}, target FPS: ${report.targetFps}")
                appendLine("Frames: ${report.frameCount}, warmup: ${report.warmupCount}")
                val frames = report.frames
                if (frames.isNotEmpty()) {
                    val cpu = frames.fold(Duration.ZERO) { total, frame -> total + frame.cpu }
                    val gpu = frames.fold(Duration.ZERO) { total, frame -> total + frame.gpu }
                    appendLine("Average CPU time: ${cpu / frames.size}")
                    appendLine("Average GPU time: ${gpu / frames.size}")
                    appendLine("Per-frame render FPS range: ${frames.minOf { it.fps }}..${frames.maxOf { it.fps }}")
                }
            }
        }
        appendLine("Saved to: $path")
    })
}

private object BenchmarkReportSerializer :
    JsonContentPolymorphicSerializer<BenchmarkReport>(BenchmarkReport::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<BenchmarkReport> {
        val typeElement = (element as? JsonObject)?.get("type")
            ?: throw SerializationException("Missing benchmark report type")
        val type = Json.decodeFromJsonElement(BenchmarkReportType.serializer(), typeElement)
        return when (type) {
            BenchmarkReportType.STARTUP -> StartupReport.serializer()
            BenchmarkReportType.REAL_FRAMES -> RealFramesReport.serializer()
            BenchmarkReportType.AVERAGE_FRAME -> AverageFrameReport.serializer()
            BenchmarkReportType.FPS -> FpsReport.serializer()
        }
    }
}

private fun saveReport(outputDirectory: Path, id: String, type: BenchmarkReportType, body: String): String {
    val typeName = Json.encodeToJsonElement(
        BenchmarkReportType.serializer(),
        type
    ).jsonPrimitive.content
    return saveDocument(outputDirectory, id, typeName, body)
}

@Synchronized
private fun saveDocument(outputDirectory: Path, id: String, kind: String, body: String): String {
    var fileName: String
    do {
        // GitHub Actions artifacts reject colons in file names.
        val timestamp = Instant.now().toString().replace(':', '-')
        fileName = "$id-$kind-$timestamp.json"
    } while (Files.exists(outputDirectory.resolve(fileName)))

    val temporaryFile = Files.createTempFile(outputDirectory, ".$kind-", ".tmp")
    try {
        Files.writeString(temporaryFile, body)
        Files.move(temporaryFile, outputDirectory.resolve(fileName), ATOMIC_MOVE)
        return fileName
    } finally {
        Files.deleteIfExists(temporaryFile)
    }
}
