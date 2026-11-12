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

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private val reportJson = Json { encodeDefaults = true }

@Serializable
internal data class ChartMeasurement(val title: String, val unit: String, val value: Double)

@Serializable
internal data class ChartDistribution(val title: String, val unit: String, val values: List<Double>)

@Serializable
internal data class ChartRun(
    val target: String,
    val kotlin: String,
    val compose: String,
    val name: String,
    val type: String,
    val iteration: Int,
    val status: String,
    val error: String?,
    val measurements: List<ChartMeasurement>,
    val distributions: List<ChartDistribution>,
    val browser: String? = null,
    val browserName: String? = null,
    val parameters: JsonObject = JsonObject(emptyMap()),
)

@Serializable
private data class ChartConfiguration(
    val target: String,
    val kotlin: String,
    val compose: String,
    val browser: String?,
    val browserName: String?,
    val metadata: JsonObject,
)

@Serializable
private data class ChartReport(
    val composeVersions: List<String>,
    val runs: List<ChartRun>,
    val warnings: List<String>,
    val configurations: List<ChartConfiguration>,
)

/** Reads only this session. No server is needed to open the resulting file. */
internal fun generateHtmlReport(session: Path, composeVersions: List<String>): Path {
    val warnings = mutableListOf<String>()
    val sessionInfo = try {
        val file = session.resolve("session.json")
        if (Files.exists(file)) reportJson.parseToJsonElement(file.readText()).jsonObject else null
    } catch (error: Exception) {
        warnings.add("Cannot read session metadata: "+error.message)
        null
    }
    val launcherCommand = sessionInfo?.get("launcherCommand")?.jsonArray
        ?.joinToString(" ") { shellArgument(it.jsonPrimitive.content) }
    val totalTime = sessionInfo?.get("totalTimeSeconds")?.jsonPrimitive?.longOrNull?.let { seconds ->
        String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
    val runs = mutableListOf<ChartRun>()
    val configurations = mutableListOf<ChartConfiguration>()
    Files.walk(session, 4).use { paths ->
        paths.filter { it.fileName.toString() == "summary.json" && Files.isRegularFile(it) }
            .sorted().forEach { summaryPath ->
                try {
                    val summary = reportJson.parseToJsonElement(summaryPath.readText()).jsonObject
                    val browser = summary["browser"]?.takeUnless { it == JsonNull }
                        ?.jsonObject?.requiredText("executable")
                    val browserName = browser?.let { executable ->
                        val path = Path.of(executable)
                        path.firstOrNull { it.toString().endsWith(".app") }?.toString()?.removeSuffix(".app")
                            ?: path.fileName.toString()
                    }
                    val target = summary.requiredText("target")
                    val configuration = target + (browserName?.let { " / $it" } ?: "")
                    val kotlin = summary.requiredText("kotlinVersion")
                    val compose = if (summary["localCompose"]?.jsonPrimitive?.booleanOrNull == true) {
                        "local"
                    } else summary.requiredText("composeVersion")
                    summary["error"]?.jsonPrimitive?.contentOrNull?.let {
                        warnings.add("$configuration / Kotlin $kotlin / Compose $compose: $it")
                    }
                    val collection = summary["environment"] as? JsonObject
                    val environment = try {
                        collection?.get("report")?.jsonPrimitive?.contentOrNull?.let { relative ->
                            val file = summaryPath.parent.resolve(relative).normalize()
                            require(file.startsWith(summaryPath.parent)) { "environment path outside configuration" }
                            val document = reportJson.parseToJsonElement(file.readText()).jsonObject
                            require(document["id"] == collection["id"] && "runtime" in document && "type" !in document) {
                                "environment does not match collection"
                            }
                            document
                        }
                    } catch (error: Exception) {
                        warnings.add("$configuration environment: ${error.message}")
                        null
                    }
                    collection?.get("error")?.jsonPrimitive?.contentOrNull?.let {
                        warnings.add("$configuration environment: $it")
                    }
                    val metadata = buildJsonObject {
                        put("configuration", JsonObject(summary.filterKeys {
                            it in listOf("target", "kotlinVersion", "composeVersion", "localCompose", "composeCorePath", "browser")
                        }))
                        (summary["launcherEnvironment"] as? JsonObject)?.let {
                            put("launcher", JsonObject(it.filterKeys { key -> key != "launchCommand" }))
                        }
                        put("runtime", environment ?: JsonPrimitive("Not collected"))
                    }
                    configurations.add(ChartConfiguration(target, kotlin, compose, browser, browserName, metadata))
                    for (element in summary.getValue("runs").jsonArray) {
                        val run = element.jsonObject
                        var parameters = buildJsonObject {
                            put("type", run.getValue("type"))
                            for (key in listOf("repeat", "timeoutSeconds")) summary[key]?.let { put(key, it) }
                        }
                        val (measurements, distributions) = try {
                            run["report"]?.jsonPrimitive?.contentOrNull?.let { relative ->
                                val file = summaryPath.parent.resolve(relative).normalize()
                                require(file.startsWith(summaryPath.parent)) { "report path outside configuration" }
                                val report = reportJson.parseToJsonElement(file.readText()).jsonObject
                                require(listOf("id", "name", "type").all { report[it] == run[it] }) {
                                    "report does not match run"
                                }
                                // Keep input parameters, not ids, measurements, or per-frame samples.
                                parameters = JsonObject(parameters + report.filterKeys {
                                    it !in setOf(
                                        "id", "name", "timeToMain", "timeToFirstFrame", "timeToNthFrame",
                                        "frameTimes", "totalDuration", "averageFrameInfo", "frames",
                                    )
                                })
                                reportMeasurements(report) to reportDistributions(report)
                            } ?: (emptyList<ChartMeasurement>() to emptyList<ChartDistribution>())
                        } catch (error: Exception) {
                            warnings.add("${run.requiredText("name")} / $configuration / Kotlin $kotlin / Compose $compose: ${error.message}")
                            emptyList<ChartMeasurement>() to emptyList<ChartDistribution>()
                        }
                        runs.add(ChartRun(
                            target, kotlin, compose, run.requiredText("name"), run.requiredText("type"),
                            run.getValue("iteration").jsonPrimitive.int, run.requiredText("status"),
                            run["error"]?.jsonPrimitive?.contentOrNull, measurements, distributions,
                            browser, browserName, parameters,
                        ))
                    }
                } catch (error: Exception) {
                    warnings.add("Cannot read ${session.relativize(summaryPath)}: ${error.message}")
                }
            }
    }
    val payload = reportJson.encodeToString(ChartReport(composeVersions, runs, warnings, configurations))
        // JSON lives in a script raw-text element, not in an HTML text node.
        .replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
    val html = createHTML().html {
        lang = "en"
        head {
            meta { charset = "utf-8" }
            meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
            title("Compose benchmark reports")
            style { unsafe { +reportResource("report.css") } }
            script {
                unsafe {
                    +reportResource("chart.umd.min.js")
                }
            }
        }
        body {
            header {
                h1 { +"Compose benchmark reports" }
                p { +"Session: ${session.fileName}" }
                totalTime?.let { p { +"Total time: $it" } }
                launcherCommand?.let { command ->
                    p(classes = "launcher-command-label") { +"Launcher command" }
                    pre(classes = "launcher-command") { code { +command } }
                }
                div(classes = "control-row") {
                    span { id = "target-label"; +"Target" }
                    div(classes = "segmented-control") {
                        id = "target"
                        attributes["role"] = "radiogroup"
                        attributes["aria-labelledby"] = "target-label"
                    }
                }
                div(classes = "control-row") {
                    id = "browser-control"
                    attributes["hidden"] = ""
                    span { id = "browser-label"; +"Browser" }
                    div(classes = "segmented-control") {
                        id = "browser"
                        attributes["role"] = "radiogroup"
                        attributes["aria-labelledby"] = "browser-label"
                    }
                }
                section {
                    id = "environment"
                    attributes["aria-label"] = "Build and target environment"
                }
            }
            main {
                div(classes = "control-row") {
                    span { id = "mode-label"; +"Measurement mode" }
                    div(classes = "segmented-control") {
                        id = "mode"
                        attributes["role"] = "radiogroup"
                        attributes["aria-labelledby"] = "mode-label"
                    }
                }
                div(classes = "control-row") {
                    span { id = "test-label"; +"Test" }
                    div(classes = "segmented-control") {
                        id = "test"
                        attributes["role"] = "radiogroup"
                        attributes["aria-labelledby"] = "test-label"
                    }
                }
                fieldSet(classes = "kotlin-filter") {
                    legend { +"Kotlin versions" }
                    div { id = "kotlin-versions" }
                }
                p {
                    id = "message"
                    attributes["role"] = "status"
                    +"Loading charts. JavaScript must be enabled to view this report."
                }
                div { id = "warnings" }
                div { id = "charts" }
            }
            script(type = "application/json") { id = "report-data"; unsafe { +payload } }
            script { unsafe { +reportResource("report.js") } }
        }
    }
    return session.resolve("index.html").also { it.writeText("<!DOCTYPE html>\n$html\n") }
}

private fun shellArgument(value: String): String =
    if (value.matches(Regex("[A-Za-z0-9_./:=,@%+-]+"))) value
    else "'" + value.replace("'", "'\"'\"'") + "'"

private fun reportResource(name: String): String =
    requireNotNull(ChartRun::class.java.getResource("/org/jetbrains/compose/benchmarks/launcher/$name")) {
        "missing report resource: $name"
    }.readText()

private fun JsonObject.requiredText(key: String): String = getValue(key).jsonPrimitive.content

/** Converts the collector's JSON representation without changing the shared report models. */
internal fun reportMeasurements(report: JsonObject): List<ChartMeasurement> = buildList {
    fun addValue(title: String, unit: String, value: Double) {
        if (value.isFinite()) add(ChartMeasurement(title, unit, value))
    }
    fun duration(title: String, key: String) {
        report[key]?.takeUnless { it == JsonNull }?.let {
            addValue(title, "ms", durationMillis(it))
        }
    }
    fun samples(title: String, unit: String, values: List<Double>) {
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return
        addValue("$title — mean", unit, sorted.sumOf { it / sorted.size })
        for (percentile in listOf(50, 95, 99)) {
            val index = (sorted.size - 1) * percentile / 100.0
            val low = sorted[floor(index).toInt()]
            val high = sorted[ceil(index).toInt()]
            addValue("$title — p$percentile", unit, low + (high - low) * (index - floor(index)))
        }
    }
    when (report.requiredText("type")) {
        "startup" -> {
            duration("Process to main", "timeToMain")
            duration("Main to first frame", "timeToFirstFrame")
            duration("Main to Nth frame (${report.requiredText("frameCount")} after first)", "timeToNthFrame")
        }
        "realFrames" -> {
            samples("Frame time", "ms", report.getValue("frameTimes").jsonArray.map(::durationMillis))
            duration("Total duration", "totalDuration")
            val seconds = durationMillis(report.getValue("totalDuration")) / 1000
            if (seconds > 0) addValue("Throughput", "FPS", report.getValue("frameCount").jsonPrimitive.int / seconds)
        }
        "averageFrame" -> {
            val frame = report.getValue("averageFrameInfo").jsonObject
            addValue("Average CPU time", "ms", durationMillis(frame.getValue("cpu")))
            addValue("Average GPU time", "ms", durationMillis(frame.getValue("gpu")))
            addValue("Average frame FPS", "FPS", frame.getValue("fps").jsonPrimitive.double)
        }
        "fps" -> {
            val frames = report.getValue("frames").jsonArray.map { it.jsonObject }
            samples("CPU time", "ms", frames.map { durationMillis(it.getValue("cpu")) })
            samples("GPU time", "ms", frames.map { durationMillis(it.getValue("gpu")) })
            samples("Frame FPS", "FPS", frames.map { it.getValue("fps").jsonPrimitive.double })
        }
        else -> error("unknown report type: ${report.requiredText("type")}")
    }
}

internal fun reportDistributions(report: JsonObject): List<ChartDistribution> {
    if (report.requiredText("type") != "fps") return emptyList()
    val frames = report.getValue("frames").jsonArray.map { it.jsonObject }
    return listOf(
        ChartDistribution("CPU time", "ms", frames.map { durationMillis(it.getValue("cpu")) }.filter { it.isFinite() }),
        ChartDistribution("GPU time", "ms", frames.map { durationMillis(it.getValue("gpu")) }.filter { it.isFinite() }),
        ChartDistribution("Frame FPS", "FPS", frames.map { it.getValue("fps").jsonPrimitive.double }.filter { it.isFinite() }),
    )
}

private fun durationMillis(value: JsonElement): Double =
    Duration.parse(value.jsonPrimitive.content).toDouble(DurationUnit.MILLISECONDS)
