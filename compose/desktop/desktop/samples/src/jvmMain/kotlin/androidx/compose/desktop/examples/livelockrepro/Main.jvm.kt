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

package androidx.compose.desktop.examples.livelockrepro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.runApplicationBlocking
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/*
 * Reproduction of a measure/layout livelock: a LazyColumn item whose Modifier.layout block
 * queries the child's maxIntrinsicWidth on EVERY measure, inside a size-to-content parent
 * that reads an intrinsic reported upward from item measurement. Observed downstream: after
 * the first stable pass, one item is remeasured forever and the main thread pins at 100%.
 *
 * A watchdog thread samples the total item-measure count once per second and prints a
 * machine-readable verdict:
 *   VERDICT: LIVELOCK  (exit 1) - measures keep climbing after the warmup window
 *   VERDICT: QUIET     (exit 0) - layout settled
 *
 * Run with `:compose:desktop:desktop:samples:runLivelockRepro` (frame isolation off) or
 * `runLivelockReproIsolated` (on).
 */

private const val ITEM_COUNT = 20
private const val WARMUP_SECONDS = 5
private const val DECISION_WINDOW_SECONDS = 5
private const val MAX_RUNTIME_SECONDS = 40
private const val CLIMB_THRESHOLD_PER_SECOND = 100L

private val totalItemMeasures = AtomicLong(0)

@Composable
fun IntrinsicRemeasureLivelockRepro() {
    // Widest item intrinsic width seen so far - reported upward so a size-to-content parent
    // can size to it WITHOUT delegating intrinsics to LazyColumn (which throws). Mirrors the
    // original code, where a popup sized itself to the list's content width.
    var maxSeenIntrinsicWidth by remember { mutableStateOf(0) }

    // Size-to-content parent: width(IntrinsicSize.Max) reads the reported intrinsic below.
    Box(Modifier.width(IntrinsicSize.Max)) {
        Box(Modifier.reportMaxIntrinsicWidth { maxSeenIntrinsicWidth }) {
            LazyColumn {
                items(ITEM_COUNT) { i ->
                    val measureCount = remember { intArrayOf(0) }
                    Box(
                        Modifier.layout { measurable, constraints ->
                            val n = ++measureCount[0]
                            totalItemMeasures.incrementAndGet()
                            if (n == 1 || n % 200 == 0) println("item $i measured ${n}x")
                            val placeable = measurable.measure(constraints)

                            // *** TRIGGER: intrinsic query on EVERY measure. Replace with
                            // `val iw = placeable.width` and the loop disappears. ***
                            val iw = measurable.maxIntrinsicWidth(constraints.maxHeight)

                            if (iw > maxSeenIntrinsicWidth) maxSeenIntrinsicWidth = iw
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                    ) {
                        // Content that itself computes intrinsics, mirroring the deep
                        // intrinsic recursion seen in the original stack.
                        Row(Modifier.height(IntrinsicSize.Max)) {
                            Text("Item $i")
                            Spacer(Modifier.width(8.dp))
                            Text("detail ".repeat(i % 5 + 1))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Layout modifier that reports [width] as its max intrinsic width, so a size-to-content
 * parent can query it without hitting LazyColumn's unsupported intrinsics.
 */
private fun Modifier.reportMaxIntrinsicWidth(width: () -> Int): Modifier =
    this then ReportMaxIntrinsicWidthElement(width)

private data class ReportMaxIntrinsicWidthElement(
    val width: () -> Int,
) : ModifierNodeElement<ReportMaxIntrinsicWidthNode>() {
    override fun create() = ReportMaxIntrinsicWidthNode(width)

    override fun update(node: ReportMaxIntrinsicWidthNode) {
        node.width = width
    }
}

private class ReportMaxIntrinsicWidthNode(
    var width: () -> Int,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val p = measurable.measure(constraints)
        return layout(p.width, p.height) { p.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int = width()
}

private fun startWatchdog() {
    thread(isDaemon = true, name = "livelock-watchdog") {
        var last = 0L
        var quietSeconds = 0
        repeat(MAX_RUNTIME_SECONDS) { second ->
            Thread.sleep(1000)
            val current = totalItemMeasures.get()
            val delta = current - last
            last = current
            println("[livelock-repro] t=${second + 1}s item measures/s = $delta (total $current)")
            if (second + 1 <= WARMUP_SECONDS) return@repeat
            if (delta == 0L) quietSeconds++ else quietSeconds = 0
            if (quietSeconds >= DECISION_WINDOW_SECONDS) {
                println("VERDICT: QUIET (settled, total $current measures)")
                exitProcess(0)
            }
            if (delta >= CLIMB_THRESHOLD_PER_SECOND && second + 1 >= WARMUP_SECONDS + DECISION_WINDOW_SECONDS) {
                println("VERDICT: LIVELOCK (still measuring ${delta}/s after ${second + 1}s)")
                exitProcess(1)
            }
        }
        println("VERDICT: UNDECIDED (low-rate remeasuring after ${MAX_RUNTIME_SECONDS}s)")
        exitProcess(2)
    }
}

fun main() {
    println(
        "[livelock-repro] compose.frameIsolation=" +
            System.getProperty("compose.frameIsolation").toBoolean()
    )
    startWatchdog()
    runApplicationBlocking(
        identifier = System.getProperty("kdt.application.identifier")
            ?: "compose-livelock-repro",
    ) {
        Window(
            onCloseRequested = { _ -> exitProcess(0) },
            configure = {
                title = "Intrinsic Remeasure Livelock"
                requestSize(DpSize(600.dp, 700.dp))
            },
        ) {
            IntrinsicRemeasureLivelockRepro()
        }
    }
}
