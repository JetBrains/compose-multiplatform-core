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

package androidx.compose.desktop.examples.frameconsistency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A frame-consistency stress sample.
 *
 * A background thread commits pairs of values ATOMICALLY - `left == right` at every
 * commit, for the built-in snapshot source and for an external [MapDataSource] alike - so
 * any observer with a consistent view can never see the two sides differ. Probes then
 * compare reads taken at every frame-related read point - composable body, measure pass,
 * draw pass, subcomposition composed during measure, frame-clock callbacks
 * (withFrameMillis bodies in the animation pump), LaunchedEffect resumptions,
 * rememberCoroutineScope tasks, and (interactively: hover/click the strip) pointer event
 * handlers plus the coroutines they launch - and count every violation as a TEAR.
 *
 * Without frame isolation each phase reads the live global state, so a commit landing
 * mid-frame tears every cross-phase pair (and even two reads within one measure or draw
 * pass) - the counters climb continuously. With frame isolation on, every phase of a frame
 * reads the same pinned view and a tear is impossible by construction: all counters must
 * stay at zero. The one CONTROL probe (two reads within one composable body) stays at zero
 * in BOTH modes, because stock recomposition is already snapshot-isolated per pass - it
 * proves the detector itself is honest.
 *
 * Run it via the Gradle tasks:
 *  - `:compose:desktop:desktop:desktop-samples:runFrameConsistency`         (isolation OFF)
 *  - `:compose:desktop:desktop:desktop-samples:runFrameConsistencyIsolated` (isolation ON)
 *
 * A summary line is printed every 2 seconds, so the proof also works headlessly from logs.
 */
private class Probe(
    val prefix: String,
    val name: String,
    val control: Boolean = false,
    val interactive: Boolean = false,
) {
    val tears = AtomicInteger(0)
    val checks = AtomicLong(0)

    fun check(expected: Any?, actual: Any?) {
        checks.incrementAndGet()
        if (expected != actual) {
            val n = tears.incrementAndGet()
            if (n <= 3 || n % 1000 == 0) {
                println("[frame-consistency:$prefix] TEAR #$n in \"$name\": $expected != $actual")
            }
        }
    }
}

/** Read-pair repetitions for the within-one-pass probes, to widen the detection window. */
private const val AMPLIFIED_READS = 1000

private val monospace = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

@Composable
internal fun FrameConsistencyApp(
    isolationEnabled: Boolean,
    mapSource: MapDataSource<String, Long>,
    logPrefix: String,
    // The APPLICATION composition's own read of the source (see Main.jvm.kt): rising values
    // here prove read tracking works in the scene-less app-level frame domain too.
    appLevelLeft: Long = 0L,
) = MaterialTheme {
    // --- the invariants: left == right at every commit, per source ---
    // The map source is application-owned: it was handed in once, at launch, as the
    // application-wide DataSourceContext - every window scene and the application
    // composition share it; no global registration and no per-window plumbing exist.
    val left = remember { mutableStateOf(0L) }
    val right = remember { mutableStateOf(0L) }

    val composeVsMeasure = remember { Probe(logPrefix, "compose vs measure") }
    val composeVsDraw = remember { Probe(logPrefix, "compose vs draw") }
    val composeVsSubcomposition = remember { Probe(logPrefix, "compose vs subcomposition") }
    val measureVsSubcomposition = remember { Probe(logPrefix, "measure vs subcomposition (ordering)") }
    val withinMeasure = remember { Probe(logPrefix, "within one measure pass") }
    val withinDraw = remember { Probe(logPrefix, "within one draw pass") }
    val withinFrameCallback = remember { Probe(logPrefix, "within one frame callback (withFrameMillis)") }
    val withinEffectTask = remember { Probe(logPrefix, "within one effect resumption") }
    val withinScopeTask = remember { Probe(logPrefix, "within one rememberCoroutineScope task") }
    val withinCompose = remember { Probe(logPrefix, "within one compose pass (control)", control = true) }
    val mapComposeVsDraw = remember { Probe(logPrefix, "compose vs draw (external DataSource)") }
    val mapComposeVsSubcomposition =
        remember { Probe(logPrefix, "compose vs subcomposition (external DataSource)") }
    val withinEffectTaskMap =
        remember { Probe(logPrefix, "within one effect resumption (external DataSource)") }
    val withinEventHandler =
        remember { Probe(logPrefix, "within one event handler (interactive)", interactive = true) }
    val withinEventHandlerMap =
        remember {
            Probe(
                logPrefix,
                "within one event handler (external DataSource, interactive)",
                interactive = true,
            )
        }
    val eventVsLaunchedTask =
        remember { Probe(logPrefix, "event handler vs launched coroutine (interactive)", interactive = true) }
    val probes = remember {
        listOf(
            composeVsMeasure,
            composeVsDraw,
            composeVsSubcomposition,
            measureVsSubcomposition,
            withinMeasure,
            withinDraw,
            withinFrameCallback,
            withinEffectTask,
            withinScopeTask,
            mapComposeVsDraw,
            mapComposeVsSubcomposition,
            withinEffectTaskMap,
            withinCompose,
            withinEventHandler,
            withinEventHandlerMap,
            eventVsLaunchedTask,
        )
    }

    val commits = remember { AtomicLong(0) }
    val frames = remember { AtomicLong(0) }

    // Mirrors the app-level read into the periodic log (the summary coroutine's closure
    // would otherwise capture the first composition's value and hide the rising proof).
    val appLevelForLog = remember { AtomicLong(0) }
    appLevelForLog.set(appLevelLeft)

    // --- the tear generator: a background thread committing left == right atomically ---
    var writerEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(writerEnabled) {
        if (!writerEnabled) return@LaunchedEffect
        // Let at least one pin rotation happen after the map source's registration (both
        // are effects of the first composition, but the scene's first cycle unit was taken
        // before either), so the very first commits already race a fully pinned frame.
        withFrameMillis {}
        withFrameMillis {}
        withContext(Dispatchers.Default) {
            var n = 0L
            while (isActive) {
                n += 1
                Snapshot.withMutableSnapshot {
                    left.value = n
                    right.value = n
                }
                mapSource.update("left" to n, "right" to n)
                commits.incrementAndGet()
                // ~5k commits/s: dozens of chances per frame to land between two reads.
                LockSupport.parkNanos(200_000)
            }
        }
    }

    // Drives a recomposition every frame, so every probe re-samples every frame.
    var frameTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameTick += 1 }
        }
    }

    // --- effect-coroutine probes ---
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                // Reads INSIDE the frame callback: this lambda runs inline in the
                // Recomposer's animation pump (the frame's first slice).
                repeat(AMPLIFIED_READS) { withinFrameCallback.check(left.value, right.value) }
            }
            // Reads in the continuation AFTER the frame callback: this resumes as an
            // effect-dispatcher task of its own (a slice, or a fold into the slice that
            // flushed it) - the LaunchedEffect body proper.
            repeat(AMPLIFIED_READS) { withinEffectTask.check(left.value, right.value) }
            withinEffectTaskMap.check(mapSource.read("left"), mapSource.read("right"))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            // A rememberCoroutineScope launch: the classic "do work from a callback"
            // pattern, dispatched as its own effect task.
            scope.launch {
                repeat(AMPLIFIED_READS) { withinScopeTask.check(left.value, right.value) }
            }
            // The delayed resumption exercises the dispatcher's delayed-task slice path.
            delay(1)
        }
    }

    // Periodic stdout summary, so the run tasks prove the point headlessly too. Interactive
    // probes report their check count alongside, so an unexercised row is visible as such.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            val summary =
                probes.joinToString(separator = ", ") {
                    if (it.interactive) "${it.name}=${it.tears.get()}/${it.checks.get()}"
                    else "${it.name}=${it.tears.get()}"
                }
            println(
                "[frame-consistency:$logPrefix] isolation=$isolationEnabled frames=${frames.get()} " +
                    "commits=${commits.get()} appLevelRead=${appLevelForLog.get()} tears: $summary"
            )
        }
    }

    // --- COMPOSE-time reads; frameTick keeps them fresh every frame ---
    frameTick
    val composedLeft = left.value
    val composedMapLeft = mapSource.read("left")
    repeat(AMPLIFIED_READS) { withinCompose.check(left.value, right.value) }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Banner(isolationEnabled)
        Column(Modifier.padding(12.dp)) {
            BasicText(
                "frames=${frames.get()}  atomic commits=${commits.get()}  " +
                    "committed pair=$composedLeft/${right.value}  map=${composedMapLeft ?: 0}  " +
                    "app-level read=$appLevelLeft",
                style = monospace,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(writerEnabled, onCheckedChange = { writerEnabled = it })
                BasicText(
                    "Background writer (commits left == right atomically, both sources)",
                    style = monospace,
                )
            }
            Button(onClick = { probes.forEach { it.tears.set(0) } }) {
                BasicText("Reset counters")
            }
            Spacer(Modifier.height(8.dp))
            probes.forEach { probe -> ProbeRow(probe, frameTick) }
            Spacer(Modifier.height(8.dp))

            // --- the probe host: its measure, draw, and subcomposition do the reads ---
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color(0xFFEEEEEE))
                    .layout { measurable, constraints ->
                        // MEASURE-time reads.
                        composeVsMeasure.check(composedLeft, right.value)
                        repeat(AMPLIFIED_READS) { withinMeasure.check(left.value, right.value) }
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .drawBehind {
                        // DRAW-time reads.
                        frames.incrementAndGet()
                        composeVsDraw.check(composedLeft, right.value)
                        mapComposeVsDraw.check(composedMapLeft, mapSource.read("right"))
                        repeat(AMPLIFIED_READS) { withinDraw.check(left.value, right.value) }
                        // Keep the pixels honest: a bar driven by the drawn value.
                        val fraction = (right.value % 512L) / 512f
                        drawRect(
                            color = Color(0xFF90CAF9),
                            size = Size(size.width * fraction, size.height),
                        )
                    }
            ) {
                SubcomposeLayout { constraints ->
                    // The parent's measure captures the value into the child content BY
                    // VALUE - the classic subcomposition-ordering hazard: a child
                    // recomposed out of order with its parent would compare a stale
                    // capture against its own fresh read.
                    val measureLeft = left.value
                    val placeable =
                        subcompose(Unit) {
                            val childRight = right.value
                            composeVsSubcomposition.check(composedLeft, childRight)
                            measureVsSubcomposition.check(measureLeft, childRight)
                            mapComposeVsSubcomposition.check(
                                composedMapLeft,
                                mapSource.read("right"),
                            )
                            BasicText(
                                "subcomposed leaf: fromMeasure=$measureLeft direct=$childRight",
                                style = monospace,
                            )
                        }
                            .first()
                            .measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
            }

            Spacer(Modifier.height(8.dp))
            // --- the interactive probes: pointer handlers are event-slice reads ---
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFFFFF9C4))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                // Reads inside the event handler itself (the event slice).
                                repeat(AMPLIFIED_READS) {
                                    withinEventHandler.check(left.value, right.value)
                                }
                                withinEventHandlerMap.check(
                                    mapSource.read("left"),
                                    mapSource.read("right"),
                                )
                                if (event.type == PointerEventType.Press) {
                                    // The handler's read must equal the read inside the
                                    // coroutines it launches: they flush before the event
                                    // slice ends, folding into the same transaction (and
                                    // the same pin). Several launches widen the window a
                                    // racing commit could exploit without isolation.
                                    val handlerSaw = left.value
                                    repeat(20) {
                                        scope.launch {
                                            eventVsLaunchedTask.check(handlerSaw, left.value)
                                        }
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(
                    "  hover to probe event handlers; click to probe handler-vs-launched-coroutine",
                    style = monospace,
                )
            }
        }
    }
}

@Composable
private fun Banner(isolationEnabled: Boolean) {
    val text =
        if (isolationEnabled) {
            "FRAME ISOLATION ON - every counter must stay at 0"
        } else {
            "FRAME ISOLATION OFF - tear counters climb; only the control stays at 0"
        }
    val color = if (isolationEnabled) Color(0xFF2E7D32) else Color(0xFFC62828)
    Box(Modifier.fillMaxWidth().background(color).padding(8.dp)) {
        BasicText(
            text,
            style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp),
        )
    }
}

@Composable
private fun ProbeRow(probe: Probe, frameTick: Long) {
    frameTick // recompose each frame so the atomic counters stay fresh on screen
    val count = probe.tears.get()
    val checks = probe.checks.get()
    val color =
        when {
            probe.interactive && checks == 0L -> Color(0xFF9E9E9E) // not exercised yet
            count == 0 -> Color(0xFF2E7D32)
            else -> Color(0xFFC62828)
        }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Box(Modifier.size(10.dp).background(color))
        BasicText(
            "  ${count.toString().padStart(7)} tears  ${checks.toString().padStart(12)} checks  ${probe.name}",
            style = monospace,
        )
    }
}
