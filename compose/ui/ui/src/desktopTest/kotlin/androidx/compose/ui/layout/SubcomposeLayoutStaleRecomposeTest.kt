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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SchedulingDispatcherFixture
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import noria.foundation.layout.MainOverlayHostKey
import noria.foundation.layout.OverlayHost
import noria.foundation.layout.overlay

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

    // Test-only local for the resolution test below. It provides a different value at the host
    // and at the anchor, so a regression to host resolution reads the wrong value.
    private val LocalOverlayTestValue = compositionLocalOf { "unprovided" }

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

    // Control helper. The child's constraints MOVE with [size], so a change dirties the child's own
    // measure as well as this host's. Measure must descend into the child.
    @Composable
    private fun resizingSubHost(size: Int, content: @Composable () -> Unit) {
        SubcomposeLayout { _ ->
            val childConstraints = Constraints.fixed(size, size)
            val placeables = subcompose(Unit, content).map { it.measure(childConstraints) }
            layout(size, size) { placeables.forEach { it.place(0, 0) } }
        }
    }

    // Experiment helper. The policy captures [tick], so a change reinstalls it and marks THIS host's
    // measure pending, the same mechanism [sizedSubHost] uses. The child is always measured with the
    // same fixed constraints, so the child's own measure is never dirtied by the change.
    @Composable
    private fun tickingFixedSubHost(tick: Int, content: @Composable () -> Unit) {
        SubcomposeLayout { _ ->
            val childConstraints = Constraints.fixed(50, 50)
            val placeables = subcompose(Unit, content).map { it.measure(childConstraints) }
            layout(50 + tick, 50) { placeables.forEach { it.place(0, 0) } }
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

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `control - the measure cascade recovers a nested host whose constraints change`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val seen = intArrayOf(-1)
        var hostSize by mutableStateOf(50)
        var counter by mutableStateOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                resizingSubHost(hostSize) { fixedSubHost { seen[0] = counter } }
            }
            scene.render(0)
            assertEquals(0, seen[0], "sanity: the first frame shows the initial value")

            Snapshot.withMutableSnapshot {
                hostSize = 60
                counter = 1
            }
            scene.render(16_000_000)

            assertEquals(
                1,
                seen[0],
                "control: the outer refresh moves the inner constraints, so measure descends and " +
                    "the cascade recovers the nested composition inside frame 1",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the measure cascade does not reach a nested host whose constraints never change`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val seen = intArrayOf(-1)
        var tick by mutableStateOf(0)
        var counter by mutableStateOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                tickingFixedSubHost(tick) { fixedSubHost { seen[0] = counter } }
            }
            scene.render(0)
            assertEquals(0, seen[0], "sanity: the first frame shows the initial value")

            Snapshot.withMutableSnapshot {
                tick = 1
                counter = 1
            }

            scene.render(16_000_000)
            val afterFrame1 = seen[0]
            scene.render(32_000_000)
            val afterFrame2 = seen[0]

            assertEquals(
                1,
                afterFrame2,
                "the nested composition must deliver on some path by frame 2; a failure here means " +
                    "neither the cascade nor the re-arm recovered it",
            )
            // Measured on 2026-09-21, not assumed. The outer host is measure-pending, so the
            // measure pass refreshes it. The inner host is neither measure-pending nor
            // constraint-changed, so its own measure policy never runs and its slot is never
            // re-subcomposed. Nothing recovers the nested composition inside frame 1.
            //
            // This is why the re-arm exists. A failure here is good news: it means the cascade now
            // reaches this host, and the re-arm, its counter and its bound may be removable. See
            // docs/superpowers/specs/2026-09-21-rearm-necessity-experiment.md
            assertEquals(
                0,
                afterFrame1,
                "the measure cascade must not reach a nested host whose constraints never change; " +
                    "if it does, re-evaluate whether the re-arm is still needed",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * [OverlayHost] subcomposes an overlay slot with the anchor's own
     * [androidx.compose.runtime.CompositionContext]. [Modifier.overlay] captures that context at
     * its call site. This makes the anchor the slot's ordering parent, while the host is what
     * removes the slot.
     *
     * The anchor is always a composition-descendant of the host, so its depth is always
     * greater. The slot's depth is greater still, because its parent is the anchor. So
     * depth(host) is less than depth(anchor), which is less than depth(slot), always. The host
     * therefore always sorts before the slot in wave 2. This test puts the anchor inside a
     * nested [fixedSubHost], a different composition from the host. One transaction deletes the
     * entity that gates the anchor and that the overlay content re-reads. The overlay content
     * must never observe the deleted value; a [staleReads] count above zero means the host did
     * not remove the slot before it recomposed.
     *
     * This test guards one outcome: the overlay content never reads the removed entity in this
     * shape. The disposal cascade delivers that outcome. The wave 2 depth sort plays no part in
     * it. When the host stops calling the nested subcomposition, disposal cancels the overlay
     * slot before it can recompose. The test cannot tell the two mechanisms apart. It would
     * also pass before the depth-ordering change. Keep it as a regression guard. Do not read it
     * as proof that depth ordering closed the overlay gap.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay anchored inside a nested composition is ordered under its host`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val staleReads = intArrayOf(0)
        // [anchorComposes] and [overlayComposes] pin that the anchor's own composition and the
        // overlay's content composition both really ran, and [anchorRemovals] and
        // [overlayRemovals] pin that both really went away. Without them a pass that never
        // touches either one keeps staleReads at 0.
        val anchorComposes = intArrayOf(0)
        val anchorRemovals = intArrayOf(0)
        val overlayComposes = intArrayOf(0)
        val overlayRemovals = intArrayOf(0)
        var entity by mutableStateOf<String?>("present")
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    if (entity != null) {          // the host's own gate, like WindowView's dialogs loop
                        fixedSubHost {              // the anchor's composition, DIFFERENT from the host's
                            anchorComposes[0]++
                            DisposableEffect(Unit) { onDispose { anchorRemovals[0]++ } }
                            Spacer(
                                Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                    overlayComposes[0]++
                                    DisposableEffect(Unit) { onDispose { overlayRemovals[0]++ } }
                                    if (entity == null) {
                                        staleReads[0]++ // the bug: composed while entity is deleted
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // The overlay does not exist until the host reports coordinates, which onPlaced sets
            // on the first layout pass. The overlay composes one frame later.
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(0, staleReads[0], "sanity: no stale read before deletion")
            assertEquals(1, anchorComposes[0], "sanity: the anchor's composition must have run")
            assertEquals(1, overlayComposes[0], "sanity: the overlay's content must have run")
            assertEquals(0, anchorRemovals[0], "sanity: the anchor is still present before the deletion")
            assertEquals(0, overlayRemovals[0], "sanity: the overlay is still present before the deletion")

            // "Delete" the entity: it invalidates the host's gate AND the overlay content's read.
            Snapshot.withMutableSnapshot { entity = null }

            scene.render(32_000_000)
            scene.render(48_000_000) // a second frame to flush any deferred subcomposition pass
            assertEquals(
                1,
                overlayComposes[0],
                "the overlay content must not compose again after the deletion",
            )
            assertEquals(
                1,
                anchorRemovals[0],
                "the host's gate must remove the anchor's own subtree",
            )
            assertEquals(
                1,
                overlayRemovals[0],
                "the host must remove the overlay slot, so a deferral that never delivers fails here",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
        assertEquals(
            0,
            staleReads[0],
            "the overlay content recomposed against the deleted entity before the host removed it",
        )
    }

    /**
     * Pins the [noria.foundation.layout.OverlayHandle] contract: an overlay's content composes
     * with the anchor's [androidx.compose.runtime.CompositionContext], so it resolves the
     * anchor's composition locals and not the host's.
     *
     * [LocalOverlayTestValue] gets a different value at the [OverlayHost] and at the anchor. A
     * regression to host resolution would read the host's value, so this test would then fail.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay resolves the anchor's composition local`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var overlayComposes = 0
        var seenValue: String? = null
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                CompositionLocalProvider(LocalOverlayTestValue provides "host value") {
                    OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalOverlayTestValue provides "anchor value") {
                            Spacer(
                                Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                    overlayComposes++
                                    seenValue = LocalOverlayTestValue.current
                                }
                            )
                        }
                    }
                }
            }

            // The overlay does not exist until the host reports coordinates, which onPlaced sets
            // on the first layout pass. The overlay composes one frame later.
            scene.render(0)
            scene.render(16_000_000)

            assertEquals(1, overlayComposes, "sanity: the overlay content must have run")
            assertEquals(
                "anchor value",
                seenValue,
                "an overlay must resolve the composition local from the anchor, not the host",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }
}
