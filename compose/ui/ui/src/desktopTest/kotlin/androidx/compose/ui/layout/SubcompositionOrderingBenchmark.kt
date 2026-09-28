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

package androidx.compose.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.test.SchedulingDispatcherFixture
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpSize
import androidx.compose.ui.unit.toSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import noria.foundation.layout.MainOverlayHostKey
import noria.foundation.layout.OverlayHost
import noria.foundation.layout.overlay
import org.jetbrains.skia.Color as SkiaColor
import org.jetbrains.skia.Surface

/**
 * Frame cost of the subcomposition ordering, not a correctness test: it prints numbers and asserts
 * only that each scenario really animates. Compare its output between two builds of the runtime and
 * ui modules on the same machine; the absolute numbers mean nothing on their own.
 *
 * Each scenario drives a scene through [WARMUP_FRAMES] frames, then measures [MEASURED_FRAMES]
 * more. One state is written before every frame. The scene is assembled as [ImageComposeScene]
 * assembles its own, and a frame runs the same steps as [ImageComposeScene.render], so that each
 * step can be timed on its own. Per frame it records the wall time of the whole frame, of the
 * frame recomposer's frame (recompose and apply), and of the scene's measure and layout (which is
 * where a slot composes when its host re-runs it). Counting layout modifiers record how often the
 * relevant hosts measure per frame.
 *
 * Lines start with `BENCH` so they can be taken from the test report.
 */
class SubcompositionOrderingBenchmark {

    private class Counter {
        var value = 0
    }

    private fun Modifier.countMeasures(counter: Counter): Modifier = layout { measurable, c ->
        counter.value++
        val placeable = measurable.measure(c)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    private fun colorOf(value: Int): Color = Color(0xFF000000.toInt() or ((value * 7919) and 0xFFFFFF))

    private class Result(
        val name: String,
        val renderNanos: LongArray,
        val recomposeNanos: LongArray,
        val layoutNanos: LongArray,
        val measuresPerFrame: Map<String, Double>,
    )

    @OptIn(InternalComposeUiApi::class)
    private fun runScenario(
        name: String,
        width: Int,
        height: Int,
        counters: Map<String, Counter>,
        tick: (Int) -> Unit,
        content: @Composable () -> Unit,
        verify: (lastTick: Int) -> Unit,
    ): Result {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val size = IntSize(width, height)
        val density = Density(1f)
        val surface = Surface.makeRasterN32Premul(width, height)
        val frameRecomposer = FrameRecomposer(Dispatchers.Unconfined)
        val windowInfo =
            WindowInfoImpl().apply {
                isWindowFocused = true
                containerSize = size
                containerDpSize = size.toSize().toDpSize(density)
            }
        val platformContext =
            object : PlatformContext by PlatformContext.Empty() {
                override val windowInfo: WindowInfo
                    get() = windowInfo
            }
        val scene =
            CanvasLayersComposeScene(
                frameRecomposer = frameRecomposer,
                density = density,
                size = size,
                platformContext = platformContext,
            )
        val canvas = surface.canvas.asComposeCanvas()
        // The step durations of the last frame, in the order of ImageComposeScene.render.
        var recomposeElapsed = 0L
        var layoutElapsed = 0L
        fun renderFrame(nanoTime: Long) {
            surface.canvas.clear(SkiaColor.TRANSPARENT)
            val recomposeStart = System.nanoTime()
            frameRecomposer.performFrame(nanoTime)
            val layoutStart = System.nanoTime()
            scene.measureAndLayout()
            val drawStart = System.nanoTime()
            recomposeElapsed = layoutStart - recomposeStart
            layoutElapsed = drawStart - layoutStart
            scene.draw(canvas)
            surface.makeImageSnapshot().close()
        }
        val total = WARMUP_FRAMES + MEASURED_FRAMES
        val render = LongArray(MEASURED_FRAMES)
        val recompose = LongArray(MEASURED_FRAMES)
        val layout = LongArray(MEASURED_FRAMES)
        val measureTotals = counters.keys.associateWith { 0L }.toMutableMap()
        try {
            scene.setContent(content = content)
            renderFrame(0)
            renderFrame(FRAME_NANOS)
            for (frame in 1..total) {
                Snapshot.withMutableSnapshot { tick(frame) }
                counters.values.forEach { it.value = 0 }
                val start = System.nanoTime()
                renderFrame((frame + 1) * FRAME_NANOS)
                val elapsed = System.nanoTime() - start
                val index = frame - WARMUP_FRAMES - 1
                if (index >= 0) {
                    render[index] = elapsed
                    recompose[index] = recomposeElapsed
                    layout[index] = layoutElapsed
                    counters.forEach { (key, counter) ->
                        measureTotals[key] = measureTotals.getValue(key) + counter.value
                    }
                }
            }
            verify(total)
        } finally {
            scene.close()
            frameRecomposer.close()
            surface.close()
            scheduling.uninstall()
        }
        return Result(
            name,
            render,
            recompose,
            layout,
            measureTotals.mapValues { it.value.toDouble() / MEASURED_FRAMES },
        )
    }

    private fun report(result: Result) {
        fun LongArray.median() = sorted()[size / 2] / 1000.0
        fun LongArray.p95() = sorted()[(size * 95) / 100] / 1000.0
        val measures =
            result.measuresPerFrame.entries.joinToString(" ") { (k, v) -> "%s=%.2f".format(k, v) }
        println(
            "BENCH %-28s render med=%7.1fus p95=%7.1fus | recompose med=%7.1fus p95=%7.1fus | layout med=%7.1fus p95=%7.1fus | measures/frame %s"
                .format(
                    result.name,
                    result.renderNanos.median(),
                    result.renderNanos.p95(),
                    result.recomposeNanos.median(),
                    result.recomposeNanos.p95(),
                    result.layoutNanos.median(),
                    result.layoutNanos.p95(),
                    measures,
                )
        )
    }

    @Composable
    private fun BenchRow(value: Int) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(colorOf(value)))
    }

    private fun lazyColumnScenario(name: String, everyItemAnimates: Boolean): Result {
        var tick by mutableIntStateOf(0)
        val lazyMeasures = Counter()
        val animatedComposes = Counter()
        var animatedSaw = -1
        var composesDuringRun = 0
        return runScenario(
            name = name,
            width = 200,
            height = 500,
            counters = mapOf("lazyColumn" to lazyMeasures, "animatedItemComposes" to animatedComposes),
            tick = { tick = it },
            content = {
                LazyColumn(Modifier.fillMaxSize().countMeasures(lazyMeasures)) {
                    items(count = 200) { index ->
                        if (everyItemAnimates || index == 10) {
                            val value = tick
                            if (index == 10) {
                                animatedComposes.value++
                                composesDuringRun++
                                animatedSaw = value
                            }
                            BenchRow(value + index)
                        } else {
                            BenchRow(index)
                        }
                    }
                }
            },
            verify = { last ->
                assertEquals(last, animatedSaw, "the animated item must show the last tick")
                assertTrue(
                    composesDuringRun >= WARMUP_FRAMES + MEASURED_FRAMES,
                    "the animated item must compose on every frame, composed $composesDuringRun",
                )
            },
        )
    }

    @Test
    fun lazyColumnOneItemAnimates() {
        report(lazyColumnScenario("lazyColumn/oneItemAnimates", everyItemAnimates = false))
    }

    @Test
    fun lazyColumnEveryItemAnimates() {
        report(lazyColumnScenario("lazyColumn/everyItemAnimates", everyItemAnimates = true))
    }

    @Test
    fun boxWithConstraintsContentAnimates() {
        var tick by mutableIntStateOf(0)
        val hostMeasures = Counter()
        val contentComposes = Counter()
        var saw = -1
        report(
            runScenario(
                name = "boxWithConstraints/content",
                width = 200,
                height = 500,
                counters = mapOf("host" to hostMeasures, "contentComposes" to contentComposes),
                tick = { tick = it },
                content = {
                    BoxWithConstraints(Modifier.fillMaxSize().countMeasures(hostMeasures)) {
                        val value = tick
                        contentComposes.value++
                        saw = value
                        Column {
                            repeat(20) { Box(Modifier.size(10.dp).background(colorOf(value + it))) }
                        }
                    }
                },
                verify = { last -> assertEquals(last, saw, "the content must show the last tick") },
            )
        )
    }

    // The same content without a SubcomposeLayout: the noise floor of the two builds, which share
    // this path.
    @Test
    fun controlColumnContentAnimates() {
        var tick by mutableIntStateOf(0)
        val hostMeasures = Counter()
        var saw = -1
        report(
            runScenario(
                name = "control/columnContent",
                width = 200,
                height = 500,
                counters = mapOf("host" to hostMeasures),
                tick = { tick = it },
                content = {
                    Box(Modifier.fillMaxSize().countMeasures(hostMeasures)) {
                        val value = tick
                        saw = value
                        Column {
                            repeat(20) { Box(Modifier.size(10.dp).background(colorOf(value + it))) }
                        }
                    }
                },
                verify = { last -> assertEquals(last, saw, "the content must show the last tick") },
            )
        )
    }

    // Fleet's shape: a busy host, remeasured on every frame because its measure policy captures
    // the tick, whose slot anchors an overlay under an OverlayHost. The overlay reads the tick too,
    // so it must compose after the anchor's slot on every frame.
    @Test
    fun overlayAnchoredInBusyHost() {
        var tick by mutableIntStateOf(0)
        val busyHostMeasures = Counter()
        val overlayHostMeasures = Counter()
        var overlaySaw = -1
        var torn = 0
        report(
            runScenario(
                name = "overlay/busyHost",
                width = 200,
                height = 500,
                counters = mapOf("busyHost" to busyHostMeasures, "overlayHost" to overlayHostMeasures),
                tick = { tick = it },
                content = {
                    OverlayHost(
                        MainOverlayHostKey,
                        modifier = Modifier.fillMaxSize().countMeasures(overlayHostMeasures),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            val current = tick
                            SubcomposeLayout(Modifier.countMeasures(busyHostMeasures)) { _ ->
                                val placeables =
                                    subcompose(Unit) {
                                            val captured = tick
                                            Column {
                                                repeat(20) {
                                                    Box(
                                                        Modifier.size(10.dp)
                                                            .background(colorOf(captured + it))
                                                    )
                                                }
                                                Spacer(
                                                    Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                                        val fresh = tick
                                                        if (captured != fresh) torn++
                                                        overlaySaw = fresh
                                                        Box(
                                                            Modifier.size(20.dp)
                                                                .background(colorOf(fresh))
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                        .map { it.measure(Constraints.fixed(50, 250)) }
                                layout(50 + current % 2, 250) {
                                    placeables.forEach { it.place(0, 0) }
                                }
                            }
                        }
                    }
                },
                verify = { last ->
                    assertEquals(last, overlaySaw, "the overlay must show the last tick")
                    assertEquals(0, torn, "the overlay must not compose ahead of its anchor")
                },
            )
        )
    }

    private companion object {
        const val WARMUP_FRAMES = 300
        const val MEASURED_FRAMES = 400
        const val FRAME_NANOS = 16_000_000L
    }
}
