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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.SchedulingDispatcherFixture
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reproducer for AIR-6691: an entity-gated overlay whose content re-reads the entity crashes on
 * close because the content subcomposition recomposes against the just-deleted entity BEFORE the
 * gate scope removes it.
 *
 * Model (close to Fleet's OverlayHost -> dialog content):
 *  - `overlayHost` is a SubcomposeLayout; its subcomposition holds a GATE `if (entity != null)`
 *    (mirrors WindowView's `for (dialog in windowLayoutEntity.dialogs)`).
 *  - inside the gate, a nested `BoxWithConstraints` subcomposition (mirrors the BoxWithConstraints
 *    in the crash's composition stack) whose content RE-READS `entity` (mirrors goToPanel reading
 *    `gotoPanel.scopes` required attrs).
 *
 * Deleting `entity` (setting it null) invalidates BOTH the gate scope and the content scope. If
 * recomposition were ordered gate-before-content, the gate would drop the content and it would
 * never observe the null. The bug: the content subcomposition recomposes standalone against the
 * null. We detect that with [staleReads] — it must stay 0.
 *
 * The second test adds a gate host whose own measure reads a size state. One transaction
 * deletes the entity and changes that size, which dirties the gate host's measure. That
 * measure-pending state is what makes the outer subcomposition wait. The nested one would
 * otherwise recompose on its own.
 */
class SubcomposeLayoutStaleRecomposeTest {

    @Composable
    private fun subHost(content: @Composable () -> Unit) {
        SubcomposeLayout { constraints ->
            val placeables = subcompose(Unit, content).map { it.measure(constraints) }
            val w = placeables.maxOfOrNull { it.width } ?: 0
            val h = placeables.maxOfOrNull { it.height } ?: 0
            layout(w, h) { placeables.forEach { it.place(0, 0) } }
        }
    }

    // Like [subHost] but with a FIXED (constraints-filling) measure, independent of content size —
    // this is the BoxWithConstraints-style Box measure policy. Hypothesis: a content-only state
    // change then never dirties the host's measure, so measurePending stays false and the content
    // takes the standalone recompose path (the bug), unlike [subHost]'s content-dependent measure.
    @Composable
    private fun fixedSubHost(content: @Composable () -> Unit) {
        SubcomposeLayout { constraints ->
            val placeables = subcompose(Unit, content).map { it.measure(constraints) }
            val w = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
            layout(w, h) { placeables.forEach { it.place(0, 0) } }
        }
    }

    // Like [subHost], but the measure policy captures [size]. A change to [size] recomposes this
    // host, and apply then installs a new measure policy. That install requests a remeasure of
    // the node, which is what marks THIS host's measure pending. The read inside the measure does
    // not set the flag. Closing an editor tab does the same to Fleet's overlay host. So the gate
    // defers the outer subcomposition while the nested one still recomposes.
    @Composable
    private fun sizedSubHost(size: Int, content: @Composable () -> Unit) {
        SubcomposeLayout { constraints ->
            val placeables = subcompose(Unit, content).map { it.measure(constraints) }
            layout(size, size) { placeables.forEach { it.place(0, 0) } }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `content subcomposition does not recompose against a deleted entity before the gate removes it`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val staleReads = intArrayOf(0)
        // [nestedComposes] and [nestedRemovals] pin that the nested subcomposition really ran and
        // really went away. Without them a pass that never touches it keeps staleReads at 0.
        val nestedComposes = intArrayOf(0)
        val nestedRemovals = intArrayOf(0)
        var entity by mutableStateOf<String?>("present")
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                subHost {                             // overlay host subcomposition (A) — like OverlayHost
                    if (entity != null) {              // GATE (reads entity) in A — like the dialogs `for` loop
                        fixedSubHost {                 // nested subcomposition (C) — BoxWithConstraints-style fixed measure
                            nestedComposes[0]++
                            DisposableEffect(Unit) { onDispose { nestedRemovals[0]++ } }
                            if (entity == null) {      // content re-reads entity in C — like goToPanel's scopes read
                                staleReads[0]++        // <-- the bug: composed while the entity is deleted
                            }
                        }
                    }
                }
            }
            scene.render(0)
            assertEquals(0, staleReads[0], "sanity: no stale read before deletion")
            assertEquals(1, nestedComposes[0], "sanity: the nested subcomposition must have run")
            assertEquals(0, nestedRemovals[0], "sanity: it is still present before the deletion")

            // "Delete" the entity: invalidates the gate AND the content read.
            Snapshot.withMutableSnapshot { entity = null }

            scene.render(16_000_000)
            scene.render(32_000_000) // a second frame to flush any deferred subcomposition pass
            assertEquals(
                1,
                nestedComposes[0],
                "the nested subcomposition must not compose again after the deletion",
            )
            assertEquals(
                1,
                nestedRemovals[0],
                "the gate must remove the nested subcomposition, so a deferral that never " +
                    "delivers fails here",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
        assertEquals(
            0,
            staleReads[0],
            "content subcomposition recomposed against a deleted entity before the gate removed it",
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `content subcomposition waits when the gate host has a measure pending`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val staleReads = intArrayOf(0)
        // [nestedComposes] and [nestedRemovals] pin that the nested subcomposition really ran and
        // really went away. Without them a pass that never touches it keeps staleReads at 0.
        val nestedComposes = intArrayOf(0)
        val nestedRemovals = intArrayOf(0)
        var entity by mutableStateOf<String?>("present")
        var hostSize by mutableStateOf(50)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                sizedSubHost(hostSize) {          // gate host (A): its measure reads hostSize
                    if (entity != null) {          // GATE in A, like WindowView's dialogs loop
                        fixedSubHost {             // nested (C), like Resizable's BoxWithConstraints
                            nestedComposes[0]++
                            DisposableEffect(Unit) { onDispose { nestedRemovals[0]++ } }
                            if (entity == null) {
                                staleReads[0]++    // like goToPanel reading gotoPanel.scopes
                            }
                        }
                    }
                }
            }
            scene.render(0)
            assertEquals(0, staleReads[0], "sanity: no stale read before deletion")
            assertEquals(1, nestedComposes[0], "sanity: the nested subcomposition must have run")
            assertEquals(0, nestedRemovals[0], "sanity: it is still present before the deletion")

            // One transaction deletes the entity AND dirties the gate host's measure. A is then
            // gated when wave 2 evaluates it, while C is not.
            Snapshot.withMutableSnapshot {
                entity = null
                hostSize = 60
            }
            scene.render(16_000_000)
            scene.render(32_000_000) // a second frame to flush any deferred subcomposition pass
            assertEquals(
                1,
                nestedComposes[0],
                "the nested subcomposition must not compose again after the deletion",
            )
            assertEquals(
                1,
                nestedRemovals[0],
                "the gate host's measure must reach the nested subcomposition and remove it",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
        assertEquals(
            0,
            staleReads[0],
            "the nested subcomposition recomposed against a deleted entity while its gate host " +
                "was waiting for measure",
        )
    }
}
