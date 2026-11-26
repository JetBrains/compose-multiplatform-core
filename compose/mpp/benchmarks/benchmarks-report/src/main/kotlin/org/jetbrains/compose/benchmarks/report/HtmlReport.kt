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

package org.jetbrains.compose.benchmarks.report

import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumMap
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private val reportJson = Json { encodeDefaults = true }
private const val realFramesBudgetHz = 60
private const val realFramesBudgetMs = 1000.0 / realFramesBudgetHz
private const val realFramesBudgetTolerancePercent = 5
private const val realFramesBudgetTolerance = realFramesBudgetTolerancePercent / 100.0
private const val realFramesOverBudgetMs = realFramesBudgetMs * (1 + realFramesBudgetTolerance)

/** Display-paced per-frame metrics use linear distribution scales. */
@Serializable
private enum class Pacing {
    /** Time between frames: 1000 / refresh rate, in milliseconds. */
    @SerialName("interval")
    INTERVAL,

    /** Frames per second: the refresh rate itself. */
    @SerialName("rate")
    RATE,
}

/**
 * Every metric the report can show, in display order. The page has no per-metric logic: it reads
 * titles, units, descriptions, and the comparison direction from the catalog built from these.
 */
private enum class Metric(
    val id: String,
    val title: String,
    val unit: String,
    val description: String,
    val higherIsBetter: Boolean = false,
    val pacing: Pacing? = null,
) {
    PROCESS_TO_MAIN(
        "processToMain", "Process to main", "ms",
        "Elapsed time from process launch to main entry, when available.",
    ),
    MAIN_TO_FIRST_FRAME(
        "mainToFirstFrame", "Main to first frame", "ms",
        "Elapsed time from main entry to the first observed frame callback.",
    ),
    MAIN_TO_NTH_FRAME(
        "mainToNthFrame", "Main to Nth frame", "ms",
        "Elapsed time from main entry to the configured Nth frame callback after the first frame.",
    ),
    FRAME_TIME(
        "frameTime", "Frame time", "ms",
        "Callback intervals: elapsed monotonic time between consecutive withFrameNanos callbacks, sampled " +
            "inside each callback; not CPU render time or presentation timestamps. Historical reports may " +
            "instead contain frame-clock timestamp differences or wall-clock waits for callbacks; " +
            "regenerating HTML does not change collected measurements.",
        pacing = Pacing.INTERVAL,
    ),
    FRAME_RATE(
        "frameRate", "Frame rate", "FPS",
        "1000 / callback interval in milliseconds, using only positive, finite intervals; not measured " +
            "display FPS. Historical reports use the interval measurement method of the runtime that " +
            "collected them.",
        higherIsBetter = true,
        pacing = Pacing.RATE,
    ),
    OVER_BUDGET_INTERVALS(
        "overBudgetIntervals", "Over-budget intervals", "%",
        "Percentage of positive, finite callback intervals longer than " +
            "${realFramesOverBudgetMs.toDescriptionText()} ms: a fixed $realFramesBudgetHz Hz budget " +
            "(1000/$realFramesBudgetHz ms) plus $realFramesBudgetTolerancePercent% tolerance. Callback delays " +
            "do not prove missed presentation deadlines.",
    ),
    ESTIMATED_MISSED_VSYNC(
        "estimatedMissedVsync", "Estimated missed VSync", "%",
        "Estimated missed slots / total slots × 100, using positive, finite callback intervals. " +
            "Slots = max(1, ceil(interval / (1000/$realFramesBudgetHz ms) − " +
            "${realFramesBudgetTolerance.toDescriptionText()})). Assumes a fixed $realFramesBudgetHz Hz budget " +
            "with $realFramesBudgetTolerancePercent% tolerance, not measured VSync or presentation events; " +
            "callback scheduling jitter can inflate this estimate.",
    ),
    TOTAL_DURATION(
        "totalDuration", "Total duration", "ms",
        "Wall-clock time for the measured frame-loop intervals. Compare runs with the same frame count.",
    ),
    THROUGHPUT(
        "throughput", "Throughput", "FPS",
        "Measured frame count divided by total wall-clock duration, in FPS.",
        higherIsBetter = true,
    ),
    AVERAGE_CPU_TIME(
        "averageCpuTime", "Average CPU time", "ms",
        "Total offscreen measurement time minus GPU-completion waits, divided by frame count.",
    ),
    AVERAGE_GPU_TIME(
        "averageGpuTime", "Average GPU time", "ms",
        "Total offscreen GPU-completion wait time divided by frame count; not GPU timestamp timing.",
    ),
    AVERAGE_FRAME_FPS(
        "averageFrameFps", "Average frame FPS", "FPS",
        "Offscreen frame count divided by total measurement time, including GPU-completion waits; " +
            "not display FPS.",
        higherIsBetter = true,
    ),
    CPU_TIME(
        "cpuTime", "CPU time", "ms",
        "Per-frame offscreen elapsed time minus the GPU-completion wait. Includes CPU work and other " +
            "elapsed overhead, not CPU utilization.",
    ),
    GPU_TIME(
        "gpuTime", "GPU time", "ms",
        "Per-frame wall-clock time waiting for GPU completion during offscreen rendering; not GPU " +
            "timestamp timing.",
    ),
    FRAME_FPS(
        "frameFps", "Frame FPS", "FPS",
        "Reciprocal of per-frame offscreen elapsed time, including the GPU-completion wait; not display FPS.",
        higherIsBetter = true,
    ),
}

/** A catalog entry: one [Metric], or one of its variants, as the page shows it. */
@Serializable
private data class ChartMetric(
    val title: String,
    val unit: String,
    val higherIsBetter: Boolean,
    val description: List<String>,
    val pacing: Pacing?,
)

/** Collects the metrics that reports actually contain; runs refer to them by id. */
private class MetricCatalog {
    // Keeps metrics in declaration order, whichever report mentions them first.
    private val variants = EnumMap<Metric, MutableMap<String, ChartMetric>>(Metric::class.java)

    /**
     * Returns the id of [metric], or of its [variant]: a per-frame statistic or a parameterized form
     * that gets its own [title] and an extra description paragraph, [note].
     */
    fun id(metric: Metric, variant: String? = null, title: String = metric.title, note: String? = null): String {
        val id = listOfNotNull(metric.id, variant).joinToString(".")
        variants.getOrPut(metric) { linkedMapOf() }.getOrPut(id) {
            ChartMetric(title, metric.unit, metric.higherIsBetter, listOfNotNull(metric.description, note), metric.pacing)
        }
        return id
    }

    fun toMap(): Map<String, ChartMetric> = variants.values.flatMap { it.entries }.associate { it.toPair() }
}

@Serializable
private data class ChartRun(
    val target: String,
    val kotlin: String,
    val compose: String,
    val name: String,
    val type: String,
    val iteration: Int,
    val status: String,
    val error: String?,
    /** Values by [MetricCatalog] id. */
    val measurements: Map<String, Double>,
    /** Per-frame samples by [MetricCatalog] id. */
    val distributions: Map<String, List<Double>>,
    val browser: String? = null,
    val browserName: String? = null,
    val parameters: JsonObject = JsonObject(emptyMap()),
    val reportPath: String? = null,
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
    val kotlinVersions: List<String>,
    val metrics: Map<String, ChartMetric>,
    val runs: List<ChartRun>,
    val warnings: List<String>,
    val configurations: List<ChartConfiguration>,
)

fun generateHtmlReport(session: Path): Path {
    val warnings = mutableListOf<String>()
    val sessionInfo = try {
        val file = session.resolve("session.json")
        if (Files.exists(file)) reportJson.parseToJsonElement(file.readText()).jsonObject else null
    } catch (error: Exception) {
        warnings.add("Cannot read session metadata: "+error.message)
        null
    }
    val launcherArguments = sessionInfo?.get("launcherCommand")?.jsonArray
        ?.map { it.jsonPrimitive.content }.orEmpty()
    val launcherCommand = launcherArguments.takeIf { it.isNotEmpty() }
        ?.joinToString(" ") { shellArgument(it) }
    val totalTime = sessionInfo?.get("totalTimeSeconds")?.jsonPrimitive?.longOrNull?.let { seconds ->
        String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
    val runs = mutableListOf<ChartRun>()
    val configurations = mutableListOf<ChartConfiguration>()
    val metrics = MetricCatalog()
    // Keep requested endpoints even when a configuration has no summary or measurements.
    val composeVersions = requestedVersions(launcherArguments, "--compose-version")
    val kotlinVersions = requestedVersions(launcherArguments, "--kotlin-version")
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
                    composeVersions.add(compose)
                    kotlinVersions.add(kotlin)
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
                            it in listOf("target", "kotlinVersion", "composeVersion", "localCompose", "browser")
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
                            if (run.requiredText("type") == "realFrames") {
                                put("frameBudgetHz", realFramesBudgetHz)
                                put("frameBudgetMs", realFramesBudgetMs)
                                put("frameBudgetTolerancePercent", realFramesBudgetTolerancePercent)
                            }
                            for (key in listOf("repeat", "timeoutSeconds")) summary[key]?.let { put(key, it) }
                        }
                        var reportPath: String? = null
                        val (measurements, distributions) = try {
                            run["report"]?.jsonPrimitive?.contentOrNull?.let { relative ->
                                val file = summaryPath.parent.resolve(relative).normalize()
                                require(file.startsWith(summaryPath.parent)) { "report path outside configuration" }
                                val report = reportJson.parseToJsonElement(file.readText()).jsonObject
                                require(listOf("id", "name", "type").all { report[it] == run[it] }) {
                                    "report does not match run"
                                }
                                reportPath = session.relativize(file).toString()
                                // Keep input parameters, not ids, measurements, or per-frame samples.
                                parameters = JsonObject(parameters + report.filterKeys {
                                    it !in setOf(
                                        "id", "name", "timeToMain", "timeToFirstFrame", "timeToNthFrame",
                                        "frameTimes", "totalDuration", "averageFrameInfo", "frames",
                                    )
                                })
                                reportMeasurements(report, metrics) to reportDistributions(report, metrics)
                            } ?: (emptyMap<String, Double>() to emptyMap<String, List<Double>>())
                        } catch (error: Exception) {
                            warnings.add("${run.requiredText("name")} / $configuration / Kotlin $kotlin / Compose $compose: ${error.message}")
                            emptyMap<String, Double>() to emptyMap<String, List<Double>>()
                        }
                        runs.add(ChartRun(
                            target, kotlin, compose, run.requiredText("name"), run.requiredText("type"),
                            run.getValue("iteration").jsonPrimitive.int, run.requiredText("status"),
                            run["error"]?.jsonPrimitive?.contentOrNull, measurements, distributions,
                            browser, browserName, parameters, reportPath,
                        ))
                    }
                } catch (error: Exception) {
                    warnings.add("Cannot read ${session.relativize(summaryPath)}: ${error.message}")
                }
            }
    }
    val kotlinOrder = kotlinVersions.withIndex().associate { it.value to it.index }
    val composeOrder = composeVersions.withIndex().associate { it.value to it.index }
    runs.sortWith(compareBy({ kotlinOrder.getValue(it.kotlin) }, { composeOrder.getValue(it.compose) }))
    configurations.sortWith(compareBy({ kotlinOrder.getValue(it.kotlin) }, { composeOrder.getValue(it.compose) }))
    val payload = reportJson.encodeToString(ChartReport(
        composeVersions.toList(), kotlinVersions.toList(), metrics.toMap(), runs, warnings, configurations,
    ))
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
                    div(classes = "environment-heading") {
                        h2 { +"Build and target environment" }
                        button(classes = "test-info-button") {
                            id = "environment-info-button"
                            attributes["type"] = "button"
                            attributes["aria-label"] = "Build and target environment information"
                            attributes["aria-expanded"] = "false"
                            attributes["aria-controls"] = "environment-info"
                            +"info"
                        }
                    }
                    div {
                        id = "environment-info"
                        attributes["hidden"] = ""
                    }
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
            dialog(classes = "raw-dialog") {
                id = "dialog"
                attributes["aria-labelledby"] = "dialog-title"
                div(classes = "raw-dialog-heading") {
                    h2 { id = "dialog-title" }
                    button(classes = "chart-raw-button") {
                        id = "dialog-close"
                        attributes["type"] = "button"
                        +"Close"
                    }
                }
                div { id = "dialog-content" }
            }
            div(classes = "value-tooltip") {
                id = "value-tooltip"
                attributes["role"] = "tooltip"
                attributes["hidden"] = ""
            }
            script(type = "application/json") { id = "report-data"; unsafe { +payload } }
            script { unsafe { +reportResource("report.js") } }
        }
    }
    return session.resolve("index.html").also { it.writeText("<!DOCTYPE html>\n$html\n") }
}

private fun requestedVersions(arguments: List<String>, option: String): MutableSet<String> {
    // Like the launcher's vararg option, the last occurrence wins.
    val index = arguments.indexOfLast { it == option || it.startsWith("$option=") }
    if (index < 0) return linkedSetOf()
    val values = buildList {
        arguments[index].takeIf { it.startsWith("$option=") }?.let { add(it.substringAfter('=')) }
        addAll(arguments.drop(index + 1).takeWhile { !it.startsWith("-") })
    }
    return values.flatMap { it.split(",") }.toCollection(linkedSetOf())
}

private fun shellArgument(value: String): String =
    if (value.matches(Regex("[A-Za-z0-9_./:=,@%+-]+"))) value
    else "'" + value.replace("'", "'\"'\"'") + "'"

private fun reportResource(name: String): String =
    requireNotNull(ChartRun::class.java.getResource("/org/jetbrains/compose/benchmarks/report/$name")) {
        "missing report resource: $name"
    }.readText()

private fun JsonObject.requiredText(key: String): String = getValue(key).jsonPrimitive.content

/** Formats a constant for a metric description: at most two decimals, no trailing zeros. */
private fun Double.toDescriptionText(): String =
    toBigDecimal().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun reportMeasurements(report: JsonObject, catalog: MetricCatalog): Map<String, Double> = buildMap {
    fun addValue(
        metric: Metric, value: Double,
        variant: String? = null, title: String = metric.title, note: String? = null,
    ) {
        if (value.isFinite()) put(catalog.id(metric, variant, title, note), value)
    }
    fun duration(metric: Metric, key: String, variant: String? = null, title: String = metric.title) {
        report[key]?.takeUnless { it == JsonNull }?.let {
            addValue(metric, durationMillis(it), variant, title)
        }
    }
    fun samples(metric: Metric, values: List<Double>) {
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return
        addValue(
            metric, sorted.sumOf { it / sorted.size }, "mean", "${metric.title} — mean",
            "Uses the arithmetic mean of finite frame samples in each run.",
        )
        for (percentile in listOf(50, 95, 99)) {
            val index = (sorted.size - 1) * percentile / 100.0
            val low = sorted[floor(index).toInt()]
            val high = sorted[ceil(index).toInt()]
            addValue(
                metric, low + (high - low) * (index - floor(index)), "p$percentile", "${metric.title} — p$percentile",
                "Uses P$percentile of finite frame samples in each run, with linear interpolation between sorted values.",
            )
        }
    }
    when (report.requiredText("type")) {
        "startup" -> {
            val frameCount = report.requiredText("frameCount")
            duration(Metric.PROCESS_TO_MAIN, "timeToMain")
            duration(Metric.MAIN_TO_FIRST_FRAME, "timeToFirstFrame")
            // Runs with different frame counts measure different things and get separate charts.
            duration(
                Metric.MAIN_TO_NTH_FRAME, "timeToNthFrame", frameCount,
                "${Metric.MAIN_TO_NTH_FRAME.title} ($frameCount after first)",
            )
        }
        "realFrames" -> {
            val frameTimes = report.getValue("frameTimes").jsonArray.map(::durationMillis)
            samples(Metric.FRAME_TIME, frameTimes)
            val intervals = frameTimes.filter { it.isFinite() && it > 0 }
            if (intervals.isNotEmpty()) {
                addValue(
                    Metric.OVER_BUDGET_INTERVALS,
                    100.0 * intervals.count { it > realFramesOverBudgetMs } / intervals.size,
                )
                // Infer refresh slots from frame-loop intervals, not actual presentation timestamps.
                val slots = intervals.map {
                    ceil(it / realFramesBudgetMs - realFramesBudgetTolerance).coerceAtLeast(1.0)
                }
                addValue(Metric.ESTIMATED_MISSED_VSYNC, 100.0 * slots.sumOf { it - 1 } / slots.sum())
            }
            duration(Metric.TOTAL_DURATION, "totalDuration")
            val seconds = durationMillis(report.getValue("totalDuration")) / 1000
            if (seconds > 0) addValue(Metric.THROUGHPUT, report.getValue("frameCount").jsonPrimitive.int / seconds)
        }
        "averageFrame" -> {
            val frame = report.getValue("averageFrameInfo").jsonObject
            addValue(Metric.AVERAGE_CPU_TIME, durationMillis(frame.getValue("cpu")))
            addValue(Metric.AVERAGE_GPU_TIME, durationMillis(frame.getValue("gpu")))
            addValue(Metric.AVERAGE_FRAME_FPS, frame.getValue("fps").jsonPrimitive.double)
        }
        "fps" -> {
            val frames = report.getValue("frames").jsonArray.map { it.jsonObject }
            samples(Metric.CPU_TIME, frames.map { durationMillis(it.getValue("cpu")) })
            samples(Metric.GPU_TIME, frames.map { durationMillis(it.getValue("gpu")) })
            samples(Metric.FRAME_FPS, frames.map { it.getValue("fps").jsonPrimitive.double })
        }
        else -> error("unknown report type: ${report.requiredText("type")}")
    }
}

private fun reportDistributions(report: JsonObject, catalog: MetricCatalog): Map<String, List<Double>> = buildMap {
    fun addSamples(metric: Metric, values: List<Double>) {
        put(catalog.id(metric), values.filter { it.isFinite() })
    }
    when (report.requiredText("type")) {
        "realFrames" -> {
            val frameTimes = report.getValue("frameTimes").jsonArray.map(::durationMillis).filter { it.isFinite() }
            addSamples(Metric.FRAME_TIME, frameTimes)
            addSamples(Metric.FRAME_RATE, frameTimes.filter { it > 0 }.map { 1000.0 / it })
        }
        "fps" -> {
            val frames = report.getValue("frames").jsonArray.map { it.jsonObject }
            addSamples(Metric.CPU_TIME, frames.map { durationMillis(it.getValue("cpu")) })
            addSamples(Metric.GPU_TIME, frames.map { durationMillis(it.getValue("gpu")) })
            addSamples(Metric.FRAME_FPS, frames.map { it.getValue("fps").jsonPrimitive.double })
        }
    }
}

private fun durationMillis(value: JsonElement): Double =
    Duration.parse(value.jsonPrimitive.content).toDouble(DurationUnit.MILLISECONDS)
