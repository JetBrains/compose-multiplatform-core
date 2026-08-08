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

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `content subcomposition does not recompose against a deleted entity before the gate removes it`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val staleReads = intArrayOf(0)
        var entity by mutableStateOf<String?>("present")
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                subHost {                             // overlay host subcomposition (A) — like OverlayHost
                    if (entity != null) {              // GATE (reads entity) in A — like the dialogs `for` loop
                        fixedSubHost {                 // nested subcomposition (C) — BoxWithConstraints-style fixed measure
                            if (entity == null) {      // content re-reads entity in C — like goToPanel's scopes read
                                staleReads[0]++        // <-- the bug: composed while the entity is deleted
                            }
                        }
                    }
                }
            }
            scene.render(0)
            assertEquals(0, staleReads[0], "sanity: no stale read before deletion")

            // "Delete" the entity: invalidates the gate AND the content read.
            Snapshot.withMutableSnapshot { entity = null }

            scene.render(16_000_000)
            scene.render(32_000_000) // a second frame to flush any deferred subcomposition pass
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
}
