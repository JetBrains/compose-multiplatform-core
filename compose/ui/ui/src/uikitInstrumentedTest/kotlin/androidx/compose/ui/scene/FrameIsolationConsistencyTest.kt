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

package androidx.compose.ui.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DataSource
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.invalidateDependants
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The frame-consistency invariant probe (D2 of the iOS frame-isolation spec): a
 * background writer commits `left == right` pairs into snapshot state and a minimal
 * map DataSource; probes compare compose-time captures against draw-time reads.
 * Flag-on: zero tears. Flag-off: tears observed (and the within-compose control
 * stays zero in both modes).
 */
internal class FrameIsolationConsistencyTest {

    private class Probe(val name: String) {
        var checks = 0
        var tears = 0
        fun check(expected: Long?, actual: Long?) {
            checks++
            if (expected != actual) tears++
        }
    }

    /**
     * Minimal Kotlin/Native port of the desktop reference MapDataSource
     * (frameconsistency sample), simplified to the probe's needs: a SINGLE
     * background writer swaps an immutable published map (@Volatile suffices —
     * no read-modify-write contention); begin/endTransaction run main-thread-only,
     * so the pin is a plain var with a nesting depth counter.
     */
    private class MapDataSource : DataSource {
        @Volatile
        private var published: Map<String, Long> = emptyMap()

        // Main-thread confined (scene thread): the currently pinned view, if any.
        private var pinnedView: Map<String, Long>? = null

        fun read(key: String): Long? {
            DataSource.recordDependency(key)
            return (pinnedView ?: published)[key]
        }

        /** Background-writer path: swap-publish and invalidate dependants. */
        fun update(vararg entries: Pair<String, Long>) {
            published = published + entries
            entries.forEach { DataSource.recordChange(it.first) }
            invalidateDependants(entries.map { it.first }.toSet())
        }

        override fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)?,
            block: () -> T,
        ): T = block()

        // Flag-off per-pass pin: reads within one pass are self-consistent,
        // cross-pass reads may tear (the legacy granularity).
        override fun <T> withTransaction(block: () -> T): T {
            val previous = pinnedView
            pinnedView = published
            return try {
                block()
            } finally {
                pinnedView = previous
            }
        }

        override fun advanceGlobalSnapshot(): Set<Any> = emptySet()

        override fun takeSnapshot(): DataSource.Snapshot = object : DataSource.Snapshot {
            private val base = published
            private var depth = 0
            private var outerView: Map<String, Long>? = null

            override fun makeCurrent(): Any? = null

            override fun restoreCurrent(previous: Any?) {}

            override fun beginTransaction(): Any? {
                if (depth++ == 0) {
                    outerView = pinnedView
                    pinnedView = base
                }
                return null
            }

            override fun endTransaction(frame: Any?, cause: Throwable?) {
                if (--depth == 0) {
                    pinnedView = outerView
                    outerView = null
                }
            }

            override fun dispose() {}
        }
    }

    private class Harness {
        val left = mutableStateOf(0L)
        val right = mutableStateOf(0L)
        val mapSource = MapDataSource()

        val composeVsDraw = Probe("composeVsDraw")
        val mapComposeVsDraw = Probe("mapComposeVsDraw")
        val withinDraw = Probe("withinDraw")
        val withinCompose = Probe("withinCompose")

        @Volatile
        var commits = 0L

        fun allProbes() = listOf(composeVsDraw, mapComposeVsDraw, withinDraw, withinCompose)
        fun realTears() = composeVsDraw.tears + mapComposeVsDraw.tears + withinDraw.tears
    }

    private fun UIKitInstrumentedTest.runProbe(
        isolationEnabled: Boolean,
        assertions: UIKitInstrumentedTest.(Harness) -> Unit,
    ) {
        val harness = Harness()
        val writerScope = CoroutineScope(Dispatchers.Default + Job())
        try {
            // Any earlier test in the bundle that created a container pinned the process-wide
            // flag; reset the application seam so this probe's container can apply its own value
            // without tripping the divergence check (this test owns flag hygiene via the
            // finally-reset in each @Test).
            resetFrameIsolationFlagApplicationForTests()
            setContent(configure = {
                dataSourceContext = DataSourceContext(harness.mapSource)
                isFrameIsolationEnabled = isolationEnabled
            }) {
                with(harness) {
                    val composedLeft = left.value
                    val composedMapLeft = mapSource.read("left")
                    withinCompose.check(left.value, right.value)
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            composeVsDraw.check(composedLeft, right.value)
                            mapComposeVsDraw.check(composedMapLeft, mapSource.read("right"))
                            repeat(200) { withinDraw.check(left.value, right.value) }
                        }
                    )
                }
            }

            writerScope.launch {
                var n = 0L
                while (isActive) {
                    n++
                    Snapshot.withMutableSnapshot {
                        harness.left.value = n
                        harness.right.value = n
                    }
                    harness.mapSource.update("left" to n, "right" to n)
                    harness.commits = n
                }
            }

            assertions(harness)
        } finally {
            writerScope.cancel()
            waitForIdle()
        }
    }

    @Test
    fun isolationOnHasZeroTears() = runUIKitInstrumentedTest {
        try {
            runProbe(isolationEnabled = true) { harness ->
                UIKitInstrumentedTest.waitUntil(
                    "probes exercised under load (commits + redraws flowing)",
                    timeoutMillis = 20_000,
                ) {
                    rootRedrawer?.setNeedsRedraw()
                    harness.commits > 20_000 && harness.composeVsDraw.checks > 120
                }
                for (probe in harness.allProbes()) {
                    assertTrue(probe.checks > 0, "${probe.name} was never exercised")
                }
                assertEquals(
                    0, harness.realTears(),
                    "flag-on tears: " +
                        harness.allProbes().joinToString { "${it.name}=${it.tears}/${it.checks}" }
                )
                assertEquals(0, harness.withinCompose.tears, "control probe tore")
            }
        } finally {
            resetFrameIsolationFlagApplicationForTests()
        }
    }

    @Test
    fun isolationOffTears() = runUIKitInstrumentedTest {
        try {
            runProbe(isolationEnabled = false) { harness ->
                UIKitInstrumentedTest.waitUntil(
                    "a tear observed without isolation",
                    timeoutMillis = 30_000,
                ) {
                    rootRedrawer?.setNeedsRedraw()
                    harness.realTears() > 0
                }
                assertEquals(0, harness.withinCompose.tears, "control probe tore")
            }
        } finally {
            resetFrameIsolationFlagApplicationForTests()
        }
    }
}
