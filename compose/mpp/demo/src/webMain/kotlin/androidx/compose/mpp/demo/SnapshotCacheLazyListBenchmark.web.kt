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

package androidx.compose.mpp.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import kotlin.math.abs
import kotlin.math.round
import kotlinx.browser.document

private const val BenchmarkItemCount = 100_000
private const val InitialItemIndex = 1_000
private val BenchmarkItemHeight = 96.dp
private val BenchmarkColors =
    listOf(
        Color(0xFF2255AA),
        Color(0xFF36639A),
        Color(0xFF4A718A),
        Color(0xFF5E7F7A),
        Color(0xFF728D6A),
    )

/**
 * A deterministic browser benchmark for comparing RenderNode snapshot caching while a LazyList
 * scrolls. Open with:
 *
 * `?demo=snapshotCacheBenchmark&snapshotCache=true&scenario=move&layerDepth=24&autoRun=true`
 *
 * Automation can add `waitForProfiler=true`, wait for `data-benchmark-state=measurement-ready`,
 * sample cumulative browser CPU metrics, set `data-benchmark-command=start`, and wait for
 * `data-benchmark-state=measurement-complete`. After taking the ending CPU sample, it must set
 * `data-benchmark-command=finish`. The final JSON is written to `data-benchmark-result`.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun snapshotCacheLazyListBenchmark(
    queryParams: Map<String, String>,
    snapshotCache: Boolean,
) {
    val configuration = BenchmarkConfiguration.from(queryParams)
    setBenchmarkAttribute("data-benchmark-state", "initializing")
    setBenchmarkAttribute("data-snapshot-cache", snapshotCache.toString())
    ComposeViewport {
        MaterialTheme { SnapshotCacheLazyListBenchmark(configuration, snapshotCache) }
    }
}

@Composable
private fun SnapshotCacheLazyListBenchmark(
    initialConfiguration: BenchmarkConfiguration,
    snapshotCache: Boolean,
) {
    val listState = rememberLazyListState()
    val drawCounter = remember { DrawCounter() }
    val density = LocalDensity.current
    var configuration by remember { mutableStateOf(initialConfiguration) }
    var runId by remember { mutableIntStateOf(if (initialConfiguration.autoRun) 1 else 0) }
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BenchmarkResult?>(null) }
    var profilerPhase by remember { mutableStateOf(ProfilerPhase.Idle) }

    LaunchedEffect(Unit) { setBenchmarkAttribute("data-benchmark-state", "ready") }

    LaunchedEffect(runId) {
        if (runId == 0) return@LaunchedEffect
        isRunning = true
        result = null
        profilerPhase = ProfilerPhase.WarmingUp
        setBenchmarkAttribute("data-benchmark-state", "warming-up")
        setBenchmarkAttribute("data-benchmark-command", "")
        setBenchmarkAttribute("data-benchmark-result", "")
        try {
            val measured =
                runBenchmark(
                    state = listState,
                    configuration = configuration,
                    itemHeightPx = with(density) { BenchmarkItemHeight.roundToPx() },
                    drawCounter = drawCounter,
                    onProfilerPhaseChange = { profilerPhase = it },
                )
            result = measured
            setBenchmarkAttribute("data-benchmark-result", measured.toJson(snapshotCache))
            setBenchmarkAttribute("data-benchmark-state", "complete")
        } catch (throwable: Throwable) {
            setBenchmarkAttribute("data-benchmark-state", "error")
            setBenchmarkAttribute("data-benchmark-result", throwable.message.orEmpty())
            throw throwable
        } finally {
            isRunning = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("LazyList snapshot-cache benchmark", style = MaterialTheme.typography.h6)
        Text(
            "useSnapshotCache=$snapshotCache; ${configuration.frames} measured frames; " +
                "${configuration.warmupFrames} warm-up frames; " +
                "${configuration.layerDepth} nested layers per item"
        )
        if (configuration.waitForProfiler) {
            ProfilerControls(profilerPhase)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BenchmarkScenario.entries.forEach { scenario ->
                Button(
                    enabled = !isRunning,
                    onClick = {
                        configuration = configuration.copy(scenario = scenario, autoRun = false)
                        runId++
                    },
                ) {
                    Text(scenario.displayName)
                }
            }
        }
        Text(
            if (isRunning) {
                "Running ${configuration.scenario.displayName}…"
            } else {
                result?.summary(snapshotCache)
                    ?: "Select a scenario, or add &autoRun=true to the URL."
            }
        )
        Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colors.onSurface)) {
            BenchmarkList(listState, drawCounter, configuration.layerDepth)
        }
    }
}

@Composable
private fun ProfilerControls(phase: ProfilerPhase) {
    val instructions =
        when (phase) {
            ProfilerPhase.Idle,
            ProfilerPhase.WarmingUp -> "Warming up before the measured run…"
            ProfilerPhase.MeasurementReady ->
                "Start recording in Chrome Performance, then click Start measured run."
            ProfilerPhase.Running -> "Measured run in progress…"
            ProfilerPhase.MeasurementComplete ->
                "Measured run complete. Stop Chrome recording, then click Finish benchmark."
            ProfilerPhase.Finished -> "Profiler measurement finished."
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(instructions)
        when (phase) {
            ProfilerPhase.MeasurementReady ->
                Button(onClick = { setBenchmarkAttribute("data-benchmark-command", "start") }) {
                    Text("Start measured run")
                }
            ProfilerPhase.MeasurementComplete ->
                Button(onClick = { setBenchmarkAttribute("data-benchmark-command", "finish") }) {
                    Text("Finish benchmark")
                }
            else -> Unit
        }
    }
}

@Composable
private fun BenchmarkList(state: LazyListState, drawCounter: DrawCounter, layerDepth: Int) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(count = BenchmarkItemCount, key = { it }, contentType = { "benchmark-row" }) { index
            ->
            BenchmarkRow(index, drawCounter, layerDepth)
        }
    }
}

@Composable
private fun BenchmarkRow(index: Int, drawCounter: DrawCounter, layerDepth: Int) {
    Row(
        Modifier.fillMaxWidth()
            .height(BenchmarkItemHeight)
            .background(if (index % 2 == 0) Color(0xFFF5F5F5) else Color.White)
            .drawWithContent {
                drawCounter.count++
                drawContent()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(72.dp)) { NestedGraphicsLayers(layerDepth) { BenchmarkDrawing(index) } }
        Column {
            Text("Item $index", style = MaterialTheme.typography.subtitle1)
            Spacer(Modifier.height(4.dp))
            Text(
                "Stable text and layered vector-like drawing",
                style = MaterialTheme.typography.body2,
            )
        }
    }
}

@Composable
private fun NestedGraphicsLayers(depth: Int, content: @Composable () -> Unit) {
    if (depth == 0) {
        content()
    } else {
        Box(Modifier.fillMaxSize().graphicsLayer()) { NestedGraphicsLayers(depth - 1, content) }
    }
}

@Composable
private fun BenchmarkDrawing(index: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val color = BenchmarkColors[index % BenchmarkColors.size]
        drawRect(color.copy(alpha = 0.15f), size = size)
        repeat(8) { shapeIndex ->
            val inset = shapeIndex * size.minDimension / 20f
            drawRect(
                color = color.copy(alpha = 0.25f + shapeIndex * 0.08f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = 1f + shapeIndex / 2f),
            )
        }
        drawCircle(color, radius = size.minDimension / 7f)
    }
}

private suspend fun runBenchmark(
    state: LazyListState,
    configuration: BenchmarkConfiguration,
    itemHeightPx: Int,
    drawCounter: DrawCounter,
    onProfilerPhaseChange: (ProfilerPhase) -> Unit,
): BenchmarkResult {
    val initialOffset =
        if (configuration.scenario == BenchmarkScenario.MoveOnly) itemHeightPx / 2 else 0
    state.scrollToItem(InitialItemIndex, initialOffset)
    repeat(3) { withFrameNanos {} }

    repeat(configuration.warmupFrames) { frame ->
        state.dispatchRawDelta(configuration.scenario.delta(frame, itemHeightPx))
        withFrameNanos {}
    }

    drawCounter.count = 0
    val firstItemBefore = state.firstVisibleItemIndex
    if (configuration.waitForProfiler) {
        setBenchmarkAttribute("data-benchmark-state", "measurement-ready")
        onProfilerPhaseChange(ProfilerPhase.MeasurementReady)
        while (benchmarkAttribute("data-benchmark-command") != "start") {
            withFrameNanos {}
        }
        setBenchmarkAttribute("data-benchmark-command", "")
    }
    setBenchmarkAttribute("data-benchmark-state", "running")
    onProfilerPhaseChange(ProfilerPhase.Running)
    var previousFrameNanos = withFrameNanos { it }
    val frameDurationsMillis = DoubleArray(configuration.frames)
    repeat(configuration.frames) { frame ->
        state.dispatchRawDelta(configuration.scenario.delta(frame, itemHeightPx))
        val frameNanos = withFrameNanos { it }
        frameDurationsMillis[frame] = (frameNanos - previousFrameNanos) / 1_000_000.0
        previousFrameNanos = frameNanos
    }
    repeat(2) { withFrameNanos {} }
    if (configuration.waitForProfiler) {
        setBenchmarkAttribute("data-benchmark-state", "measurement-complete")
        onProfilerPhaseChange(ProfilerPhase.MeasurementComplete)
        while (benchmarkAttribute("data-benchmark-command") != "finish") {
            withFrameNanos {}
        }
        setBenchmarkAttribute("data-benchmark-command", "")
    }
    onProfilerPhaseChange(ProfilerPhase.Finished)

    val sortedDurations = frameDurationsMillis.sortedArray()
    return BenchmarkResult(
        scenario = configuration.scenario,
        frames = configuration.frames,
        elapsedMillis = frameDurationsMillis.sum(),
        medianFrameMillis = percentile(sortedDurations, 0.50),
        p95FrameMillis = percentile(sortedDurations, 0.95),
        p99FrameMillis = percentile(sortedDurations, 0.99),
        maxFrameMillis = sortedDurations.last(),
        itemsTraversed = abs(state.firstVisibleItemIndex - firstItemBefore),
        itemDisplayListRecordings = drawCounter.count,
        layerDepth = configuration.layerDepth,
    )
}

private fun percentile(sortedValues: DoubleArray, percentile: Double): Double {
    val index = ((sortedValues.size - 1) * percentile).toInt()
    return sortedValues[index]
}

private enum class BenchmarkScenario(val queryName: String, val displayName: String) {
    MoveOnly("move", "Move only"),
    Normal("normal", "Normal"),
    Fast("fast", "Fast"),
    Extreme("extreme", "Extreme");

    fun delta(frame: Int, itemHeightPx: Int): Float =
        when (this) {
            MoveOnly -> if (frame % 2 == 0) 5f else -5f
            Normal -> itemHeightPx / 4f
            Fast -> itemHeightPx.toFloat()
            Extreme -> itemHeightPx * 4f
        }

    companion object {
        fun fromQuery(value: String?): BenchmarkScenario =
            entries.firstOrNull { it.queryName == value } ?: Normal
    }
}

private enum class ProfilerPhase {
    Idle,
    WarmingUp,
    MeasurementReady,
    Running,
    MeasurementComplete,
    Finished,
}

private data class BenchmarkConfiguration(
    val scenario: BenchmarkScenario,
    val frames: Int,
    val warmupFrames: Int,
    val autoRun: Boolean,
    val waitForProfiler: Boolean,
    val layerDepth: Int,
) {
    companion object {
        fun from(queryParams: Map<String, String>) =
            BenchmarkConfiguration(
                scenario = BenchmarkScenario.fromQuery(queryParams["scenario"]),
                frames = queryParams["frames"]?.toIntOrNull()?.coerceIn(30, 5_000) ?: 600,
                warmupFrames =
                    queryParams["warmupFrames"]?.toIntOrNull()?.coerceIn(0, 1_000) ?: 180,
                autoRun = queryParams["autoRun"] == "true",
                waitForProfiler = queryParams["waitForProfiler"] == "true",
                layerDepth = queryParams["layerDepth"]?.toIntOrNull()?.coerceIn(0, 64) ?: 24,
            )
    }
}

private data class BenchmarkResult(
    val scenario: BenchmarkScenario,
    val frames: Int,
    val elapsedMillis: Double,
    val medianFrameMillis: Double,
    val p95FrameMillis: Double,
    val p99FrameMillis: Double,
    val maxFrameMillis: Double,
    val itemsTraversed: Int,
    val itemDisplayListRecordings: Int,
    val layerDepth: Int,
) {
    fun summary(snapshotCache: Boolean): String =
        "${scenario.displayName}, cache=$snapshotCache: median=${medianFrameMillis.oneDecimal()} ms, " +
            "p95=${p95FrameMillis.oneDecimal()} ms, p99=${p99FrameMillis.oneDecimal()} ms, " +
            "max=${maxFrameMillis.oneDecimal()} ms; $itemsTraversed items traversed; " +
            "$itemDisplayListRecordings item display-list recordings; layerDepth=$layerDepth"

    fun toJson(snapshotCache: Boolean): String =
        """
        {"snapshotCache":$snapshotCache,"scenario":"${scenario.queryName}","frames":$frames,"elapsedMillis":${elapsedMillis.oneDecimal()},"medianFrameMillis":${medianFrameMillis.oneDecimal()},"p95FrameMillis":${p95FrameMillis.oneDecimal()},"p99FrameMillis":${p99FrameMillis.oneDecimal()},"maxFrameMillis":${maxFrameMillis.oneDecimal()},"itemsTraversed":$itemsTraversed,"itemDisplayListRecordings":$itemDisplayListRecordings,"layerDepth":$layerDepth}
    """
            .trimIndent()
}

private class DrawCounter(var count: Int = 0)

private fun Double.oneDecimal(): Double = round(this * 10.0) / 10.0

private fun setBenchmarkAttribute(name: String, value: String) {
    document.documentElement?.setAttribute(name, value)
}

private fun benchmarkAttribute(name: String): String? = document.documentElement?.getAttribute(name)
