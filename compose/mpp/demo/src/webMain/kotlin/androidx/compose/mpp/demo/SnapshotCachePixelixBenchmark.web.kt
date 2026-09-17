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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.round
import kotlinx.browser.document

private const val PixelixPageCount = 3
private const val PixelixInitialPage = 1
private const val PixelixMediaPageCount = 3
private val PixelixTimelineNames = listOf("Home", "Local", "Global")
private val PixelixColors =
    listOf(
        Color(0xFF477998),
        Color(0xFF6A8E5E),
        Color(0xFF9A6A78),
        Color(0xFF826D9B),
        Color(0xFF9A7548),
    )

/**
 * Models the wide render tree in Pixelix's home screen: three retained timeline pages, staggered
 * lazy grids, clipped post and media nodes, nested media pagers, translucent overlays, and an
 * optional video-like draw invalidation on every retained timeline. Open with:
 *
 * `?demo=snapshotCachePixelixBenchmark&snapshotCache=true&autoRun=true`
 *
 * `dynamicMedia=true` animates one visible media item per timeline. `clips=false` removes the
 * numerous rounded clips, while `nestedPager=false` replaces each post's media pager with one media
 * node. With `waitForProfiler=true`, use the on-page buttons to delimit the measured run.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun snapshotCachePixelixBenchmark(
    queryParams: Map<String, String>,
    snapshotCache: Boolean,
) {
    val configuration = PixelixBenchmarkConfiguration.from(queryParams)
    setPixelixBenchmarkAttribute("data-benchmark-state", "initializing")
    setPixelixBenchmarkAttribute("data-snapshot-cache", snapshotCache.toString())
    ComposeViewport { MaterialTheme { PixelixBenchmark(configuration, snapshotCache) } }
}

@Composable
private fun PixelixBenchmark(configuration: PixelixBenchmarkConfiguration, snapshotCache: Boolean) {
    val pagerState = rememberPagerState(initialPage = PixelixInitialPage) { PixelixPageCount }
    val drawCounter = remember { PixelixDrawCounter() }
    val animationTick = remember { mutableIntStateOf(0) }
    var runId by remember { mutableIntStateOf(if (configuration.autoRun) 1 else 0) }
    var isRunning by remember { mutableStateOf(false) }
    var profilerPhase by remember { mutableStateOf(PixelixProfilerPhase.Idle) }
    var result by remember { mutableStateOf<PixelixBenchmarkResult?>(null) }

    LaunchedEffect(Unit) { setPixelixBenchmarkAttribute("data-benchmark-state", "ready") }
    LaunchedEffect(runId) {
        if (runId == 0) return@LaunchedEffect
        isRunning = true
        result = null
        profilerPhase = PixelixProfilerPhase.WarmingUp
        setPixelixBenchmarkAttribute("data-benchmark-state", "warming-up")
        setPixelixBenchmarkAttribute("data-benchmark-command", "")
        setPixelixBenchmarkAttribute("data-benchmark-result", "")
        try {
            val measured =
                runPixelixBenchmark(
                    state = pagerState,
                    configuration = configuration,
                    drawCounter = drawCounter,
                    onFrame = { animationTick.intValue++ },
                    onProfilerPhaseChange = { profilerPhase = it },
                )
            result = measured
            setPixelixBenchmarkAttribute("data-benchmark-result", measured.toJson(snapshotCache))
            setPixelixBenchmarkAttribute("data-benchmark-state", "complete")
        } catch (throwable: Throwable) {
            setPixelixBenchmarkAttribute("data-benchmark-state", "error")
            setPixelixBenchmarkAttribute("data-benchmark-result", throwable.message.orEmpty())
            throw throwable
        } finally {
            isRunning = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Pixelix-like snapshot-cache benchmark", style = MaterialTheme.typography.h6)
        Text(
            "useSnapshotCache=$snapshotCache; retainedPages=${configuration.retainedPages}; " +
                "posts=${configuration.posts}; columns=${configuration.columns}; " +
                "detailRows=${configuration.detailRows}; " +
                "imageComplexity=${configuration.imageComplexity}; " +
                "dynamicMedia=${configuration.dynamicMedia}; clips=${configuration.clips}; " +
                "nestedPager=${configuration.nestedPager}; ${configuration.frames} frames"
        )
        if (configuration.waitForProfiler) {
            PixelixProfilerControls(profilerPhase)
        }
        Button(enabled = !isRunning, onClick = { runId++ }) { Text("Run Pixelix-like benchmark") }
        Text(
            if (isRunning) {
                "Running deterministic timeline pager scroll…"
            } else {
                result?.summary(snapshotCache) ?: "Click Run, or add &autoRun=true to the URL."
            }
        )
        Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colors.onSurface)) {
            PixelixHome(
                pagerState = pagerState,
                configuration = configuration,
                animationTick = animationTick,
                drawCounter = drawCounter,
            )
        }
    }
}

@Composable
private fun PixelixProfilerControls(phase: PixelixProfilerPhase) {
    val instructions =
        when (phase) {
            PixelixProfilerPhase.Idle,
            PixelixProfilerPhase.WarmingUp -> "Warming up before the measured run…"
            PixelixProfilerPhase.MeasurementReady ->
                "Start recording in Chrome Performance, then click Start measured run."
            PixelixProfilerPhase.Running -> "Measured run in progress…"
            PixelixProfilerPhase.MeasurementComplete ->
                "Measured run complete. Stop Chrome recording, then click Finish benchmark."
            PixelixProfilerPhase.Finished -> "Profiler measurement finished."
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(instructions)
        when (phase) {
            PixelixProfilerPhase.MeasurementReady ->
                Button(
                    onClick = { setPixelixBenchmarkAttribute("data-benchmark-command", "start") }
                ) {
                    Text("Start measured run")
                }
            PixelixProfilerPhase.MeasurementComplete ->
                Button(
                    onClick = { setPixelixBenchmarkAttribute("data-benchmark-command", "finish") }
                ) {
                    Text("Finish benchmark")
                }
            else -> Unit
        }
    }
}

@Composable
private fun PixelixHome(
    pagerState: PagerState,
    configuration: PixelixBenchmarkConfiguration,
    animationTick: State<Int>,
    drawCounter: PixelixDrawCounter,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pixelix timelines") }, backgroundColor = Color(0xFF24303A))
        }
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding).background(Color(0xFFEEF1F3))) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                repeat(PixelixPageCount) { page ->
                    Tab(
                        selected = pagerState.currentPage == page,
                        onClick = {},
                        text = { Text(PixelixTimelineNames[page]) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = configuration.retainedPages,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                PixelixTimeline(
                    timeline = page,
                    configuration = configuration,
                    animationTick = animationTick,
                    drawCounter = drawCounter,
                )
            }
        }
    }
}

@Composable
private fun PixelixTimeline(
    timeline: Int,
    configuration: PixelixBenchmarkConfiguration,
    animationTick: State<Int>,
    drawCounter: PixelixDrawCounter,
) {
    val posts = remember(configuration.posts) { List(configuration.posts) { it } }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(configuration.columns),
        contentPadding = PaddingValues(8.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier.fillMaxSize().drawWithContent {
                drawCounter.timelineRecordings++
                drawContent()
            },
    ) {
        items(posts, key = { "$timeline-$it" }) { post ->
            PixelixPost(
                timeline = timeline,
                post = post,
                configuration = configuration,
                animationTick = animationTick,
                drawCounter = drawCounter,
            )
        }
    }
}

@Composable
private fun PixelixPost(
    timeline: Int,
    post: Int,
    configuration: PixelixBenchmarkConfiguration,
    animationTick: State<Int>,
    drawCounter: PixelixDrawCounter,
) {
    val postShape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth()
            .pixelixClip(configuration.clips, postShape)
            .background(Color.White)
            .drawWithContent {
                drawCounter.postRecordings++
                drawContent()
            }
            .padding(vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp)
                    .pixelixClip(configuration.clips, CircleShape)
                    .background(pixelixColor(timeline, post))
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Account $timeline-$post", style = MaterialTheme.typography.subtitle2)
                Text("A few moments ago", style = MaterialTheme.typography.caption)
            }
            Box(
                Modifier.size(28.dp)
                    .pixelixClip(configuration.clips, CircleShape)
                    .background(Color(0xFFE3E7EA))
                    .alpha(0.55f)
            )
        }
        Spacer(Modifier.height(8.dp))
        PixelixMedia(
            timeline = timeline,
            post = post,
            configuration = configuration,
            animationTick = animationTick,
            drawCounter = drawCounter,
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(configuration.detailRows) { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp)
                            .pixelixClip(configuration.clips, CircleShape)
                            .background(pixelixColor(timeline + row, post))
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.weight(1f)
                            .height(if (row % 2 == 0) 7.dp else 5.dp)
                            .pixelixClip(configuration.clips, RoundedCornerShape(50))
                            .background(Color(0xFFD7DDE1))
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { action ->
                    Box(
                        Modifier.size(24.dp)
                            .pixelixClip(configuration.clips, CircleShape)
                            .background(if (action == 0) Color(0xFFDCEBF6) else Color(0xFFE8EBED))
                            .alpha(if (action == 3) 0.55f else 1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelixMedia(
    timeline: Int,
    post: Int,
    configuration: PixelixBenchmarkConfiguration,
    animationTick: State<Int>,
    drawCounter: PixelixDrawCounter,
) {
    if (configuration.nestedPager) {
        val mediaPagerState = rememberPagerState { PixelixMediaPageCount }
        HorizontalPager(
            state = mediaPagerState,
            userScrollEnabled = false,
            pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth().height((190 + post % 3 * 24).dp),
        ) { mediaPage ->
            PixelixMediaTile(
                timeline = timeline,
                post = post,
                mediaPage = mediaPage,
                configuration = configuration,
                animationTick = animationTick.takeIf { configuration.dynamicMedia && post == 0 },
                drawCounter = drawCounter,
            )
        }
    } else {
        PixelixMediaTile(
            timeline = timeline,
            post = post,
            mediaPage = 0,
            configuration = configuration,
            animationTick = animationTick.takeIf { configuration.dynamicMedia && post == 0 },
            drawCounter = drawCounter,
            modifier = Modifier.fillMaxWidth().height((190 + post % 3 * 24).dp),
        )
    }
}

@Composable
private fun PixelixMediaTile(
    timeline: Int,
    post: Int,
    mediaPage: Int,
    configuration: PixelixBenchmarkConfiguration,
    animationTick: State<Int>?,
    drawCounter: PixelixDrawCounter,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .padding(horizontal = 12.dp)
            .zIndex(2f)
            .pixelixClip(configuration.clips, shape)
            .background(Color(0xFFCDD8DF))
    ) {
        Canvas(
            Modifier.fillMaxSize().drawWithContent {
                drawCounter.mediaRecordings++
                drawContent()
            }
        ) {
            val tick = animationTick?.value ?: 0
            drawRect(pixelixColor(timeline + mediaPage, post))
            repeat(configuration.imageComplexity) { index ->
                val stripeHeight = size.height / configuration.imageComplexity
                val phase = if (animationTick == null) 0 else (tick + index) % 7
                val inset = phase * 0.35f
                drawRect(
                    color = Color.White.copy(alpha = 0.05f + (index % 4) * 0.025f),
                    topLeft = Offset(inset, index * stripeHeight + inset),
                    size =
                        Size(
                            (size.width - inset * 2).coerceAtLeast(1f),
                            (stripeHeight - inset).coerceAtLeast(1f),
                        ),
                )
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(10.dp)
                .zIndex(3f)
                .pixelixClip(configuration.clips, CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("${mediaPage + 1}/$PixelixMediaPageCount", color = Color.White)
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(10.dp).zIndex(3f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(3) { tag ->
                Box(
                    Modifier.size(width = (28 + tag * 6).dp, height = 16.dp)
                        .pixelixClip(configuration.clips, RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
        }
        if (animationTick != null) {
            Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp)) {
                val progress = (animationTick.value % 240) / 240f
                drawRect(Color.Black.copy(alpha = 0.2f))
                drawRect(Color(0xFF6EC1FF), size = Size(size.width * progress, size.height))
            }
        }
    }
}

private suspend fun runPixelixBenchmark(
    state: PagerState,
    configuration: PixelixBenchmarkConfiguration,
    drawCounter: PixelixDrawCounter,
    onFrame: () -> Unit,
    onProfilerPhaseChange: (PixelixProfilerPhase) -> Unit,
): PixelixBenchmarkResult {
    state.scrollToPage(PixelixInitialPage)
    while (state.layoutInfo.pageSize == 0) {
        withFrameNanos {}
    }
    repeat(3) { withFrameNanos {} }

    repeat(configuration.warmupFrames) { frame ->
        onFrame()
        state.dispatchRawDelta(pixelixPagerDelta(frame, state.layoutInfo.pageSize, configuration))
        withFrameNanos {}
    }

    drawCounter.reset()
    if (configuration.waitForProfiler) {
        setPixelixBenchmarkAttribute("data-benchmark-state", "measurement-ready")
        onProfilerPhaseChange(PixelixProfilerPhase.MeasurementReady)
        while (pixelixBenchmarkAttribute("data-benchmark-command") != "start") {
            withFrameNanos {}
        }
        setPixelixBenchmarkAttribute("data-benchmark-command", "")
    }
    setPixelixBenchmarkAttribute("data-benchmark-state", "running")
    onProfilerPhaseChange(PixelixProfilerPhase.Running)
    markPixelixBenchmarkStart()
    var previousFrameNanos = withFrameNanos { it }
    val frameDurationsMillis = DoubleArray(configuration.frames)
    var absoluteScrollDistance = 0f
    repeat(configuration.frames) { frame ->
        onFrame()
        absoluteScrollDistance +=
            abs(
                state.dispatchRawDelta(
                    pixelixPagerDelta(frame, state.layoutInfo.pageSize, configuration)
                )
            )
        val frameNanos = withFrameNanos { it }
        frameDurationsMillis[frame] = (frameNanos - previousFrameNanos) / 1_000_000.0
        previousFrameNanos = frameNanos
    }
    repeat(2) { withFrameNanos {} }
    markPixelixBenchmarkEnd()
    if (configuration.waitForProfiler) {
        setPixelixBenchmarkAttribute("data-benchmark-state", "measurement-complete")
        onProfilerPhaseChange(PixelixProfilerPhase.MeasurementComplete)
        while (pixelixBenchmarkAttribute("data-benchmark-command") != "finish") {
            withFrameNanos {}
        }
        setPixelixBenchmarkAttribute("data-benchmark-command", "")
    }
    onProfilerPhaseChange(PixelixProfilerPhase.Finished)

    val sortedDurations = frameDurationsMillis.sortedArray()
    return PixelixBenchmarkResult(
        frames = configuration.frames,
        elapsedMillis = frameDurationsMillis.sum(),
        medianFrameMillis = pixelixPercentile(sortedDurations, 0.50),
        p95FrameMillis = pixelixPercentile(sortedDurations, 0.95),
        p99FrameMillis = pixelixPercentile(sortedDurations, 0.99),
        maxFrameMillis = sortedDurations.last(),
        pageWidthsScrolled = absoluteScrollDistance / state.layoutInfo.pageSize,
        timelineRecordings = drawCounter.timelineRecordings,
        postRecordings = drawCounter.postRecordings,
        mediaRecordings = drawCounter.mediaRecordings,
        configuration = configuration,
    )
}

private fun pixelixPagerDelta(
    frame: Int,
    pageSize: Int,
    configuration: PixelixBenchmarkConfiguration,
): Float {
    val direction = if ((frame / configuration.framesPerPage) % 2 == 0) -1f else 1f
    return direction * pageSize / configuration.framesPerPage
}

private fun pixelixPercentile(sortedValues: DoubleArray, percentile: Double): Double {
    val index = ((sortedValues.size - 1) * percentile).toInt()
    return sortedValues[index]
}

private fun Modifier.pixelixClip(enabled: Boolean, shape: Shape): Modifier =
    if (enabled) clip(shape) else this

private fun pixelixColor(first: Int, second: Int): Color {
    return PixelixColors[(first * 7 + second * 3).mod(PixelixColors.size)]
}

private enum class PixelixProfilerPhase {
    Idle,
    WarmingUp,
    MeasurementReady,
    Running,
    MeasurementComplete,
    Finished,
}

private data class PixelixBenchmarkConfiguration(
    val frames: Int,
    val warmupFrames: Int,
    val framesPerPage: Int,
    val retainedPages: Int,
    val posts: Int,
    val columns: Int,
    val detailRows: Int,
    val imageComplexity: Int,
    val dynamicMedia: Boolean,
    val clips: Boolean,
    val nestedPager: Boolean,
    val autoRun: Boolean,
    val waitForProfiler: Boolean,
) {
    companion object {
        fun from(queryParams: Map<String, String>) =
            PixelixBenchmarkConfiguration(
                frames = queryParams["frames"]?.toIntOrNull()?.coerceIn(30, 5_000) ?: 1_200,
                warmupFrames =
                    queryParams["warmupFrames"]?.toIntOrNull()?.coerceIn(0, 1_000) ?: 180,
                framesPerPage =
                    queryParams["framesPerPage"]?.toIntOrNull()?.coerceIn(10, 600) ?: 120,
                retainedPages = queryParams["retainedPages"]?.toIntOrNull()?.coerceIn(0, 3) ?: 3,
                posts = queryParams["posts"]?.toIntOrNull()?.coerceIn(3, 500) ?: 60,
                columns = queryParams["columns"]?.toIntOrNull()?.coerceIn(1, 4) ?: 2,
                detailRows = queryParams["detailRows"]?.toIntOrNull()?.coerceIn(0, 20) ?: 4,
                imageComplexity =
                    queryParams["imageComplexity"]?.toIntOrNull()?.coerceIn(1, 200) ?: 12,
                dynamicMedia = queryParams["dynamicMedia"] == "true",
                clips = queryParams["clips"] != "false",
                nestedPager = queryParams["nestedPager"] != "false",
                autoRun = queryParams["autoRun"] == "true",
                waitForProfiler = queryParams["waitForProfiler"] == "true",
            )
    }
}

private data class PixelixBenchmarkResult(
    val frames: Int,
    val elapsedMillis: Double,
    val medianFrameMillis: Double,
    val p95FrameMillis: Double,
    val p99FrameMillis: Double,
    val maxFrameMillis: Double,
    val pageWidthsScrolled: Float,
    val timelineRecordings: Int,
    val postRecordings: Int,
    val mediaRecordings: Int,
    val configuration: PixelixBenchmarkConfiguration,
) {
    fun summary(snapshotCache: Boolean): String =
        "cache=$snapshotCache: median=${medianFrameMillis.pixelixOneDecimal()} ms, " +
            "p95=${p95FrameMillis.pixelixOneDecimal()} ms, " +
            "p99=${p99FrameMillis.pixelixOneDecimal()} ms, " +
            "max=${maxFrameMillis.pixelixOneDecimal()} ms; " +
            "pageWidthsScrolled=${pageWidthsScrolled.toDouble().pixelixOneDecimal()}; " +
            "recordings timeline=$timelineRecordings, post=$postRecordings, " +
            "media=$mediaRecordings"

    fun toJson(snapshotCache: Boolean): String =
        """
        {"snapshotCache":$snapshotCache,"frames":$frames,"elapsedMillis":${elapsedMillis.pixelixOneDecimal()},"medianFrameMillis":${medianFrameMillis.pixelixOneDecimal()},"p95FrameMillis":${p95FrameMillis.pixelixOneDecimal()},"p99FrameMillis":${p99FrameMillis.pixelixOneDecimal()},"maxFrameMillis":${maxFrameMillis.pixelixOneDecimal()},"pageWidthsScrolled":${pageWidthsScrolled.toDouble().pixelixOneDecimal()},"timelineRecordings":$timelineRecordings,"postRecordings":$postRecordings,"mediaRecordings":$mediaRecordings,"retainedPages":${configuration.retainedPages},"posts":${configuration.posts},"columns":${configuration.columns},"detailRows":${configuration.detailRows},"imageComplexity":${configuration.imageComplexity},"dynamicMedia":${configuration.dynamicMedia},"clips":${configuration.clips},"nestedPager":${configuration.nestedPager}}
        """
            .trimIndent()
}

private class PixelixDrawCounter(
    var timelineRecordings: Int = 0,
    var postRecordings: Int = 0,
    var mediaRecordings: Int = 0,
) {
    fun reset() {
        timelineRecordings = 0
        postRecordings = 0
        mediaRecordings = 0
    }
}

private fun Double.pixelixOneDecimal(): Double = round(this * 10.0) / 10.0

private fun setPixelixBenchmarkAttribute(name: String, value: String) {
    document.documentElement?.setAttribute(name, value)
}

private fun pixelixBenchmarkAttribute(name: String): String? =
    document.documentElement?.getAttribute(name)

internal expect fun markPixelixBenchmarkStart()

internal expect fun markPixelixBenchmarkEnd()
