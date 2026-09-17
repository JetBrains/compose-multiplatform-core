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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.BottomAppBar
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.sqrt
import kotlinx.browser.document

private const val PagerBenchmarkPageCount = 100
private const val PagerBenchmarkInitialPage = 50

/**
 * Models full-screen, stable, complex pages whose placement changes during a pager scroll. Open
 * with:
 *
 * `?demo=snapshotCachePagerBenchmark&snapshotCache=true&complexity=400&autoRun=true`
 *
 * `complexity` is the number of flat Canvas draw groups per page. `retainedPages` controls
 * `beyondViewportPageCount`, and `shadows` adds elevation to the app bars and cards. With
 * `waitForProfiler=true`, start the measured run and finish it using the buttons shown on the page.
 * Automation can use the same `data-benchmark-*` DOM attributes as the LazyList benchmark.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun snapshotCachePagerBenchmark(queryParams: Map<String, String>, snapshotCache: Boolean) {
    val configuration = PagerBenchmarkConfiguration.from(queryParams)
    setPagerBenchmarkAttribute("data-benchmark-state", "initializing")
    setPagerBenchmarkAttribute("data-snapshot-cache", snapshotCache.toString())
    ComposeViewport { MaterialTheme { SnapshotCachePagerBenchmark(configuration, snapshotCache) } }
}

@Composable
private fun SnapshotCachePagerBenchmark(
    configuration: PagerBenchmarkConfiguration,
    snapshotCache: Boolean,
) {
    val pagerState =
        rememberPagerState(initialPage = PagerBenchmarkInitialPage) { PagerBenchmarkPageCount }
    val drawCounter = remember { PagerDrawCounter() }
    var runId by remember { mutableIntStateOf(if (configuration.autoRun) 1 else 0) }
    var isRunning by remember { mutableStateOf(false) }
    var profilerPhase by remember { mutableStateOf(PagerProfilerPhase.Idle) }
    var result by remember { mutableStateOf<PagerBenchmarkResult?>(null) }

    LaunchedEffect(Unit) { setPagerBenchmarkAttribute("data-benchmark-state", "ready") }
    LaunchedEffect(runId) {
        if (runId == 0) return@LaunchedEffect
        isRunning = true
        result = null
        profilerPhase = PagerProfilerPhase.WarmingUp
        setPagerBenchmarkAttribute("data-benchmark-state", "warming-up")
        setPagerBenchmarkAttribute("data-benchmark-command", "")
        setPagerBenchmarkAttribute("data-benchmark-result", "")
        try {
            val measured =
                runPagerBenchmark(
                    state = pagerState,
                    configuration = configuration,
                    drawCounter = drawCounter,
                    onProfilerPhaseChange = { profilerPhase = it },
                )
            result = measured
            setPagerBenchmarkAttribute("data-benchmark-result", measured.toJson(snapshotCache))
            setPagerBenchmarkAttribute("data-benchmark-state", "complete")
        } catch (throwable: Throwable) {
            setPagerBenchmarkAttribute("data-benchmark-state", "error")
            setPagerBenchmarkAttribute("data-benchmark-result", throwable.message.orEmpty())
            throw throwable
        } finally {
            isRunning = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Pager snapshot-cache benchmark", style = MaterialTheme.typography.h6)
        Text(
            "useSnapshotCache=$snapshotCache; complexity=${configuration.complexity}; " +
                "retainedPages=${configuration.retainedPages}; shadows=${configuration.shadows}; " +
                "${configuration.frames} measured frames"
        )
        if (configuration.waitForProfiler) {
            PagerProfilerControls(profilerPhase)
        }
        Button(enabled = !isRunning, onClick = { runId++ }) { Text("Run pager benchmark") }
        Text(
            if (isRunning) {
                "Running deterministic pager scroll…"
            } else {
                result?.summary(snapshotCache) ?: "Click Run, or add &autoRun=true to the URL."
            }
        )
        Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colors.onSurface)) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = configuration.retainedPages,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                PagerBenchmarkPage(page, configuration, drawCounter)
            }
        }
    }
}

@Composable
private fun PagerProfilerControls(phase: PagerProfilerPhase) {
    val instructions =
        when (phase) {
            PagerProfilerPhase.Idle,
            PagerProfilerPhase.WarmingUp -> "Warming up before the measured run…"
            PagerProfilerPhase.MeasurementReady ->
                "Start recording in Chrome Performance, then click Start measured run."
            PagerProfilerPhase.Running -> "Measured run in progress…"
            PagerProfilerPhase.MeasurementComplete ->
                "Measured run complete. Stop Chrome recording, then click Finish benchmark."
            PagerProfilerPhase.Finished -> "Profiler measurement finished."
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(instructions)
        when (phase) {
            PagerProfilerPhase.MeasurementReady ->
                Button(
                    onClick = { setPagerBenchmarkAttribute("data-benchmark-command", "start") }
                ) {
                    Text("Start measured run")
                }
            PagerProfilerPhase.MeasurementComplete ->
                Button(
                    onClick = { setPagerBenchmarkAttribute("data-benchmark-command", "finish") }
                ) {
                    Text("Finish benchmark")
                }
            else -> Unit
        }
    }
}

@Composable
private fun PagerBenchmarkPage(
    page: Int,
    configuration: PagerBenchmarkConfiguration,
    drawCounter: PagerDrawCounter,
) {
    val elevation = if (configuration.shadows) 4.dp else 0.dp
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account ${page + 1}") },
                elevation = elevation,
                backgroundColor = Color(0xFF24486B),
            )
        },
        bottomBar = {
            BottomAppBar(elevation = elevation, backgroundColor = Color(0xFF24486B)) {
                Text(
                    "Overview    Activity    Settings",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        },
    ) { contentPadding ->
        Box(
            Modifier.fillMaxSize()
                .padding(contentPadding)
                .background(if (page % 2 == 0) Color(0xFFF3F6F9) else Color(0xFFF7F4F0))
                .drawWithContent {
                    drawCounter.count++
                    drawContent()
                }
        ) {
            FlatPagerDrawing(page, configuration.complexity)
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(6) { section ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        elevation = elevation,
                        color = Color.White.copy(alpha = 0.92f),
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(34.dp)
                                    .background(
                                        if (section % 2 == 0) Color(0xFF3D7AA8)
                                        else Color(0xFF6A8F5B)
                                    )
                            )
                            Column {
                                Text(
                                    "Section ${section + 1}",
                                    style = MaterialTheme.typography.body1,
                                )
                                Text(
                                    "Stable content on page ${page + 1}",
                                    style = MaterialTheme.typography.caption,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlatPagerDrawing(page: Int, complexity: Int) {
    Canvas(Modifier.fillMaxSize()) {
        if (complexity == 0) return@Canvas
        val columns = ceil(sqrt(complexity.toDouble())).toInt().coerceAtLeast(1)
        val rows = ((complexity + columns - 1) / columns).coerceAtLeast(1)
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        repeat(complexity) { index ->
            val column = index % columns
            val row = index / columns
            val inset = 1f + index % 3
            val color = if ((index + page) % 2 == 0) Color(0xFF2F6E9E) else Color(0xFF789C63)
            val topLeft = Offset(column * cellWidth + inset, row * cellHeight + inset)
            val shapeSize =
                Size(
                    (cellWidth - inset * 2).coerceAtLeast(1f),
                    (cellHeight - inset * 2).coerceAtLeast(1f),
                )
            drawRect(color.copy(alpha = 0.08f), topLeft = topLeft, size = shapeSize)
            drawRect(
                color.copy(alpha = 0.18f),
                topLeft = topLeft,
                size = shapeSize,
                style = Stroke(width = 1f),
            )
        }
    }
}

private suspend fun runPagerBenchmark(
    state: PagerState,
    configuration: PagerBenchmarkConfiguration,
    drawCounter: PagerDrawCounter,
    onProfilerPhaseChange: (PagerProfilerPhase) -> Unit,
): PagerBenchmarkResult {
    state.scrollToPage(PagerBenchmarkInitialPage)
    while (state.layoutInfo.pageSize == 0) {
        withFrameNanos {}
    }
    repeat(3) { withFrameNanos {} }

    repeat(configuration.warmupFrames) { frame ->
        state.dispatchRawDelta(pagerDelta(frame, state.layoutInfo.pageSize, configuration))
        withFrameNanos {}
    }

    drawCounter.count = 0
    if (configuration.waitForProfiler) {
        setPagerBenchmarkAttribute("data-benchmark-state", "measurement-ready")
        onProfilerPhaseChange(PagerProfilerPhase.MeasurementReady)
        while (pagerBenchmarkAttribute("data-benchmark-command") != "start") {
            withFrameNanos {}
        }
        setPagerBenchmarkAttribute("data-benchmark-command", "")
    }
    setPagerBenchmarkAttribute("data-benchmark-state", "running")
    onProfilerPhaseChange(PagerProfilerPhase.Running)
    var previousFrameNanos = withFrameNanos { it }
    val frameDurationsMillis = DoubleArray(configuration.frames)
    var absoluteScrollDistance = 0f
    repeat(configuration.frames) { frame ->
        absoluteScrollDistance +=
            abs(state.dispatchRawDelta(pagerDelta(frame, state.layoutInfo.pageSize, configuration)))
        val frameNanos = withFrameNanos { it }
        frameDurationsMillis[frame] = (frameNanos - previousFrameNanos) / 1_000_000.0
        previousFrameNanos = frameNanos
    }
    repeat(2) { withFrameNanos {} }
    if (configuration.waitForProfiler) {
        setPagerBenchmarkAttribute("data-benchmark-state", "measurement-complete")
        onProfilerPhaseChange(PagerProfilerPhase.MeasurementComplete)
        while (pagerBenchmarkAttribute("data-benchmark-command") != "finish") {
            withFrameNanos {}
        }
        setPagerBenchmarkAttribute("data-benchmark-command", "")
    }
    onProfilerPhaseChange(PagerProfilerPhase.Finished)

    val sortedDurations = frameDurationsMillis.sortedArray()
    return PagerBenchmarkResult(
        frames = configuration.frames,
        elapsedMillis = frameDurationsMillis.sum(),
        medianFrameMillis = pagerPercentile(sortedDurations, 0.50),
        p95FrameMillis = pagerPercentile(sortedDurations, 0.95),
        p99FrameMillis = pagerPercentile(sortedDurations, 0.99),
        maxFrameMillis = sortedDurations.last(),
        pageWidthsScrolled = absoluteScrollDistance / state.layoutInfo.pageSize,
        composeDisplayListRecordings = drawCounter.count,
        complexity = configuration.complexity,
        retainedPages = configuration.retainedPages,
        shadows = configuration.shadows,
    )
}

private fun pagerDelta(
    frame: Int,
    pageSize: Int,
    configuration: PagerBenchmarkConfiguration,
): Float {
    val direction = if ((frame / configuration.framesPerPage) % 2 == 0) -1f else 1f
    return direction * pageSize / configuration.framesPerPage
}

private fun pagerPercentile(sortedValues: DoubleArray, percentile: Double): Double {
    val index = ((sortedValues.size - 1) * percentile).toInt()
    return sortedValues[index]
}

private enum class PagerProfilerPhase {
    Idle,
    WarmingUp,
    MeasurementReady,
    Running,
    MeasurementComplete,
    Finished,
}

private data class PagerBenchmarkConfiguration(
    val frames: Int,
    val warmupFrames: Int,
    val framesPerPage: Int,
    val complexity: Int,
    val retainedPages: Int,
    val shadows: Boolean,
    val autoRun: Boolean,
    val waitForProfiler: Boolean,
) {
    companion object {
        fun from(queryParams: Map<String, String>) =
            PagerBenchmarkConfiguration(
                frames = queryParams["frames"]?.toIntOrNull()?.coerceIn(30, 5_000) ?: 1_200,
                warmupFrames =
                    queryParams["warmupFrames"]?.toIntOrNull()?.coerceIn(0, 1_000) ?: 180,
                framesPerPage =
                    queryParams["framesPerPage"]?.toIntOrNull()?.coerceIn(10, 600) ?: 120,
                complexity = queryParams["complexity"]?.toIntOrNull()?.coerceIn(0, 2_000) ?: 400,
                retainedPages = queryParams["retainedPages"]?.toIntOrNull()?.coerceIn(0, 5) ?: 1,
                shadows = queryParams["shadows"] == "true",
                autoRun = queryParams["autoRun"] == "true",
                waitForProfiler = queryParams["waitForProfiler"] == "true",
            )
    }
}

private data class PagerBenchmarkResult(
    val frames: Int,
    val elapsedMillis: Double,
    val medianFrameMillis: Double,
    val p95FrameMillis: Double,
    val p99FrameMillis: Double,
    val maxFrameMillis: Double,
    val pageWidthsScrolled: Float,
    val composeDisplayListRecordings: Int,
    val complexity: Int,
    val retainedPages: Int,
    val shadows: Boolean,
) {
    fun summary(snapshotCache: Boolean): String =
        "cache=$snapshotCache: median=${medianFrameMillis.pagerOneDecimal()} ms, " +
            "p95=${p95FrameMillis.pagerOneDecimal()} ms, " +
            "p99=${p99FrameMillis.pagerOneDecimal()} ms, " +
            "max=${maxFrameMillis.pagerOneDecimal()} ms; " +
            "pageWidthsScrolled=${pageWidthsScrolled.toDouble().pagerOneDecimal()}; " +
            "Compose display-list recordings=$composeDisplayListRecordings"

    fun toJson(snapshotCache: Boolean): String =
        """
        {"snapshotCache":$snapshotCache,"frames":$frames,"elapsedMillis":${elapsedMillis.pagerOneDecimal()},"medianFrameMillis":${medianFrameMillis.pagerOneDecimal()},"p95FrameMillis":${p95FrameMillis.pagerOneDecimal()},"p99FrameMillis":${p99FrameMillis.pagerOneDecimal()},"maxFrameMillis":${maxFrameMillis.pagerOneDecimal()},"pageWidthsScrolled":${pageWidthsScrolled.toDouble().pagerOneDecimal()},"composeDisplayListRecordings":$composeDisplayListRecordings,"complexity":$complexity,"retainedPages":$retainedPages,"shadows":$shadows}
    """
            .trimIndent()
}

private class PagerDrawCounter(var count: Int = 0)

private fun Double.pagerOneDecimal(): Double = round(this * 10.0) / 10.0

private fun setPagerBenchmarkAttribute(name: String, value: String) {
    document.documentElement?.setAttribute(name, value)
}

private fun pagerBenchmarkAttribute(name: String): String? =
    document.documentElement?.getAttribute(name)
