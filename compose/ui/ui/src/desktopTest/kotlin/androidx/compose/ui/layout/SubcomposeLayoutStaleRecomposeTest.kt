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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.SchedulingDispatcherFixture
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
 *
 * The deferral that fixes this has to satisfy two properties at once, and the tests at the end of
 * this file pin both:
 *  - Order. A nested composition never composes before the composition enclosing it has
 *    refreshed. A torn read of a value the enclosing composition captured detects a violation.
 *    A slot that composes for the first time, or is reused for a new slot id, is exempt. Its
 *    host needs its measurables in the same measure, so it cannot wait.
 *  - Delivery. A nested composition still delivers its own changes while an enclosing host is
 *    measure-pending on every frame, as Fleet's editor host is.
 *
 * Each property is pinned in two topologies, because the node tree and the composition tree can
 * disagree. In plain nesting the nested host is a node descendant of the enclosing host. An
 * overlay's host instead sits under [OverlayHost], which is shallower in the node tree than the
 * anchor's host. The measure pass handles pending nodes shallowest first. So an ordering that
 * comes from the node tree holds for plain nesting and inverts for overlays.
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
    // this is the BoxWithConstraints-style Box measure policy. A content-only state change never
    // dirties this host's measure, so its gate stays open and its content takes the standalone
    // recompose path, unlike [subHost]'s content-dependent measure. An enclosing host's measure
    // does not reach it either, because its constraints do not change.
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

    // Models Fleet's editor host. The policy captures [tick], so a change reinstalls it and marks
    // THIS host's measure pending, the same mechanism [sizedSubHost] uses. Writing [tick] on every
    // frame keeps the host measure-pending on every frame. The child is always measured with the
    // same fixed constraints, so the host's measure never dirties the child's own measure.
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
    fun `a deferred nested host whose constraints never change delivers in the same frame`() {
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

            // The outer host's measure alone never reaches the inner host, because the inner
            // host is not measure-pending and its constraints do not change. So the deferral asks
            // the inner host to remeasure. Its measure runs after the outer host's, in the same
            // frame. A next-frame re-arm is not enough, because it starves while the outer host
            // stays pending on every frame. `nested subcomposition recomposes while an enclosing
            // host remeasures every frame` covers that case.
            assertEquals(
                1,
                seen[0],
                "the deferred nested composition must deliver inside frame 1, after its enclosing " +
                    "host's measure",
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
     * shape. Stock Compose delivers it, not the deferral. The host's recompose removes the group
     * that holds the nested subcomposition's composition context. Removing such a group reports
     * every composition created under it as removed, transitively, and the recomposer skips a
     * removed composition for the rest of the turn. So the overlay's composition, created from
     * the anchor's context inside the nested one, is skipped too. The measure pass then empties
     * its slot, because `isLive` is false. Keep it as a regression guard.
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

    /**
     * Guards the design at
     * docs/superpowers/specs/2026-09-18-overlay-same-frame-removal-design.md, section 4: the
     * overlay content must stop rendering in the same frame its anchor is disposed.
     *
     * The anchor sits in its own gate, independent of any entity the overlay content reads. Its
     * removal is a structural change to [OverlayHost]'s own [Box], so the [Box] remeasures every
     * child, including this overlay's [SubcomposeLayout]. Before the fix, that remeasure always
     * re-subcomposes the slot, because the content lambda is a fresh instance every measure call.
     * The structure has not changed, so the render count goes up again on this very frame, before
     * [OverlayHost]'s own composition ever sees the removal.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `overlay content stops rendering in the same frame its anchor is removed`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var showAnchor by mutableStateOf(true)
        val renderCount = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    if (showAnchor) {
                        Spacer(
                            Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                renderCount[0]++
                            }
                        )
                    }
                }
            }

            // The overlay does not exist until the host reports coordinates, which onPlaced sets
            // on the first layout pass. The overlay composes one frame later.
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(1, renderCount[0], "sanity: the overlay content must have rendered once")

            Snapshot.withMutableSnapshot { showAnchor = false }
            scene.render(32_000_000)

            assertEquals(
                1,
                renderCount[0],
                "the overlay content must not render again once its anchor is removed, not even " +
                    "on the same frame the removal happens",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * Guards the design's second promise: the overlay content's remembered state is forgotten in
     * the frame the anchor is disposed, not one frame later.
     *
     * Before the fix, only [OverlayHost]'s own composition disposes the slot's content, and it
     * needs a later pass to see the anchor's removal, so this needs a second [scene.render] call.
     * After the fix, measure empties the slot in this same frame, so one render call is enough.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `overlay content's remembered state is forgotten in the same frame its anchor is removed`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var showAnchor by mutableStateOf(true)
        val forgottenCount = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    if (showAnchor) {
                        Spacer(
                            Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                remember { Any() }
                                DisposableEffect(Unit) { onDispose { forgottenCount[0]++ } }
                            }
                        )
                    }
                }
            }

            scene.render(0)
            scene.render(16_000_000)
            assertEquals(0, forgottenCount[0], "sanity: the overlay content is still live")

            Snapshot.withMutableSnapshot { showAnchor = false }
            scene.render(32_000_000)

            assertEquals(
                1,
                forgottenCount[0],
                "the overlay content's remembered state must be forgotten on the same frame the " +
                    "anchor is removed, not one frame later",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * Guards section 6 against the new isLive branch in [OverlayHost]: the live path must keep
     * resolving composition locals from the anchor, not the host, across more than one measure
     * pass. [LocalOverlayTestValue] gets a different value at the host and at the anchor, so a
     * regression to host resolution would fail this test.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay resolves the anchor's composition local across repeated measures while live`() {
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

            // Three frames force the per-overlay SubcomposeLayout's measure to run more than
            // once, exercising the isLive branch repeatedly while the anchor stays live.
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)

            assertTrue(overlayComposes >= 1, "sanity: the overlay content must have run")
            assertEquals(
                "anchor value",
                seenValue,
                "the isLive-gated live branch must still resolve the composition local from the " +
                    "anchor, not the host",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * Guards the design end to end: an overlay whose anchor is added and removed leaves no node
     * behind, in the same frame the removal happens.
     *
     * [Modifier.onGloballyPositioned] captures the overlay content's own coordinates.
     * [LayoutCoordinates.isAttached] must be false as soon as the frame that disposes the anchor
     * completes, not one frame later.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay added and removed in the same frame leaves no node behind`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var showAnchor by mutableStateOf(true)
        var contentCoordinates: LayoutCoordinates? = null
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    if (showAnchor) {
                        Spacer(
                            Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                Box(
                                    Modifier.size(10.dp).onGloballyPositioned {
                                        contentCoordinates = it
                                    }
                                )
                            }
                        )
                    }
                }
            }

            scene.render(0)
            scene.render(16_000_000)
            val coordinates = contentCoordinates
            assertTrue(coordinates?.isAttached == true, "sanity: the overlay content is attached")

            Snapshot.withMutableSnapshot { showAnchor = false }
            scene.render(32_000_000)

            assertFalse(
                coordinates?.isAttached == true,
                "the overlay content's own node must be detached in the same frame the anchor " +
                    "is removed, so it leaves no node behind",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A nested subcomposition must recompose on its own state change even while an enclosing
     * subcomposition waits for its host's measure.
     *
     * This is Fleet's editor context menu. The editor is one big SubcomposeLayout whose measure
     * is pending on nearly every frame, and it subcomposes the popup anchor. The overlay host
     * subcomposes the menu below that, and the menu's list subcomposes each row. Hovering a row
     * writes the row's own state. The row's own host has no measure pending, so the row is ready
     * to recompose. The propagation defers it anyway, because an enclosing composition waits.
     *
     * The row must deliver its hover on the first busy frame after the write, not on some later
     * quiet frame. The enclosing host's measure never reaches the row's host on its own, because
     * the row's host is not measure-pending and its constraints never change. So a next-frame
     * re-arm alone would starve the row on every busy frame. The stretch then runs well past
     * MAX_CONSECUTIVE_DEFERRAL_RE_ARMS. That is the shape in which a capped re-arm alone would
     * lose the hover for good.
     *
     * The hover is written once, so this test does not show that the row keeps delivering later
     * writes. `a leaf below a middle slot with nothing to compose delivers on every busy frame`
     * writes on every one of 70 busy frames and pins that.
     *
     * The test drives the enclosing host the way the editor drives it: its measure policy
     * captures [tick], and its slot content reads [tick], so every frame both invalidates the
     * enclosing slot and marks the enclosing host's measure pending. This is plain nesting, where
     * the nested host is a node descendant of the enclosing host. The real menu sits in an
     * overlay. `an overlay-hosted row delivers its hover while the anchor's host remeasures every
     * frame` covers that topology.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `nested subcomposition recomposes while an enclosing host remeasures every frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var hovered by mutableStateOf(false)
        val nestedComposes = intArrayOf(0)
        val sawHovered = booleanArrayOf(false)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                tickingFixedSubHost(tick) {
                    // The enclosing slot reads tick, so every frame invalidates it, exactly as
                    // the editor's own state invalidates its popup anchor subcomposition.
                    @Suppress("UNUSED_EXPRESSION")
                    tick
                    fixedSubHost {
                        nestedComposes[0]++
                        if (hovered) sawHovered[0] = true
                    }
                }
            }
            scene.render(0)
            assertEquals(1, nestedComposes[0], "sanity: the nested subcomposition must have run")
            assertFalse(sawHovered[0], "sanity: not hovered yet")

            Snapshot.withMutableSnapshot { hovered = true }

            // Well past MAX_CONSECUTIVE_DEFERRAL_RE_ARMS, so a dropped invalidation shows up.
            repeat(70) { frame ->
                Snapshot.withMutableSnapshot { tick++ }
                scene.render((frame + 1) * 16_000_000L)
                if (frame == 0) {
                    assertTrue(
                        sawHovered[0],
                        "the nested subcomposition must observe its own state change on the " +
                            "first busy frame after the write",
                    )
                }
            }

            assertTrue(
                sawHovered[0],
                "the nested subcomposition never observed its own state change while the " +
                    "enclosing host kept a measure pending",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * Pins both properties of the deferral in plain nesting, on every frame of a busy stretch.
     *
     * The enclosing slot captures [tick] into the nested content. The nested content also reads
     * [tick] directly. If the nested composition runs before the enclosing one refreshes, the two
     * disagree, which is the torn read the gate exists to prevent. Order is asserted first, so a
     * failure names the property that broke. A nested composition that never runs keeps order
     * trivially, so delivery is asserted on every frame as well.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a nested subcomposition composes after its enclosing composition on every busy frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                tickingFixedSubHost(tick) {
                    val captured = tick
                    fixedSubHost {
                        val fresh = tick
                        if (captured != fresh) tornReads[0]++
                        nestedSaw[0] = fresh
                    }
                }
            }
            scene.render(0)
            assertEquals(0, nestedSaw[0], "sanity: the nested subcomposition must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render(frame * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the nested subcomposition composed before its enclosing " +
                        "composition refreshed the value it captured",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the nested subcomposition did not deliver while its " +
                        "enclosing host was measure-pending",
                )
            }

            repeat(3) { scene.render((11 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the enclosing host settles",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A middle slot with nothing of its own to compose sits between a busy host's slot and a leaf
     * slot. The middle content is one lambda instance, created outside composition, so the
     * enclosing slot's compose skips the middle host and never marks the middle slot changed. The
     * leaf's write still marks the middle composition invalid, because an invalidation climbs the
     * context chain. So wave 2 holds the middle composition back, and the leaf waits for it. The
     * middle host then finds nothing to compose in the middle slot. The leaf must still deliver on
     * the first busy frame after the write.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a leaf below a middle slot with nothing to compose delivers on the first busy frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var hovered by mutableStateOf(false)
        val middleComposes = intArrayOf(0)
        val leafSawHovered = booleanArrayOf(false)
        val middleContent: @Composable () -> Unit = {
            middleComposes[0]++
            fixedSubHost { if (hovered) leafSawHovered[0] = true }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                tickingFixedSubHost(tick) {
                    @Suppress("UNUSED_EXPRESSION")
                    tick
                    fixedSubHost(middleContent)
                }
            }
            scene.render(0)
            assertEquals(1, middleComposes[0], "sanity: the middle slot must have run")
            assertFalse(leafSawHovered[0], "sanity: not hovered yet")

            Snapshot.withMutableSnapshot { hovered = true }
            Snapshot.withMutableSnapshot { tick = 1 }
            scene.render(16_000_000)

            assertEquals(1, middleComposes[0], "sanity: the middle slot has nothing to compose")
            assertTrue(
                leafSawHovered[0],
                "the leaf did not deliver on the first busy frame after the write",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The topology of the test above, with the leaf's own state written on every busy frame, well
     * past MAX_CONSECUTIVE_DEFERRAL_RE_ARMS. The leaf must deliver on every one of those frames.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a leaf below a middle slot with nothing to compose delivers on every busy frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var leafValue by mutableStateOf(0)
        val middleComposes = intArrayOf(0)
        val leafSaw = intArrayOf(-1)
        val middleContent: @Composable () -> Unit = {
            middleComposes[0]++
            fixedSubHost { leafSaw[0] = leafValue }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                tickingFixedSubHost(tick) {
                    @Suppress("UNUSED_EXPRESSION")
                    tick
                    fixedSubHost(middleContent)
                }
            }
            scene.render(0)
            assertEquals(0, leafSaw[0], "sanity: the leaf slot must have run")

            for (frame in 1..70) {
                Snapshot.withMutableSnapshot {
                    tick = frame
                    leafValue = frame
                }
                scene.render(frame * 16_000_000L)
                assertEquals(
                    frame,
                    leafSaw[0],
                    "frame $frame: the leaf did not deliver while its enclosing host was " +
                        "measure-pending",
                )
            }
            assertEquals(1, middleComposes[0], "sanity: the middle slot has nothing to compose")

            repeat(3) { scene.render((71 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the enclosing host settles",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The overlay version of the test above. The composition tree is the same: the overlay's
     * composition is nested in the anchor's composition, which is the slot of a busy host. The
     * node tree is inverted: the overlay's host sits under [OverlayHost], shallower than the
     * anchor's host. The measure pass handles pending nodes shallowest first, so any order that
     * comes from the node tree runs the overlay first here.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay composes after the anchor's composition on every busy frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        val tornReads = intArrayOf(0)
        val overlaySaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    // Nest the busy host a few levels down, as the editor sits deep in a window.
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            tickingFixedSubHost(tick) {
                                val captured = tick
                                Spacer(
                                    Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                        val fresh = tick
                                        if (captured != fresh) tornReads[0]++
                                        overlaySaw[0] = fresh
                                    }
                                )
                            }
                        }
                    }
                }
            }
            // The overlay does not exist until the host reports coordinates, which onPlaced sets
            // on the first layout pass. The overlay composes one frame later.
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(0, overlaySaw[0], "sanity: the overlay's content must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 1) * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the overlay composed before the anchor's composition " +
                        "refreshed the value it captured",
                )
                assertEquals(
                    frame,
                    overlaySaw[0],
                    "frame $frame: the overlay did not deliver while the anchor's host was " +
                        "measure-pending",
                )
            }

            repeat(3) { scene.render((12 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the anchor's host settles",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * Fleet's editor context menu in its real topology. The editor's busy host subcomposes the
     * popup anchor. The overlay subcomposes the menu, and the menu's list subcomposes the row. The
     * row reads only its own hover state, so nothing refreshes it except its own invalidation.
     *
     * The chain is three compositions deep below the busy one, and it crosses the node-tree
     * inversion at the overlay. The hover must still show in the frame it was written.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay-hosted row delivers its hover while the anchor's host remeasures every frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var hovered by mutableStateOf(false)
        val rowComposes = intArrayOf(0)
        val hoverSeenOnFrame = intArrayOf(-1)
        val currentFrame = intArrayOf(0)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        tickingFixedSubHost(tick) {
                            // The editor's own state invalidates the anchor's slot on every frame.
                            @Suppress("UNUSED_EXPRESSION")
                            tick
                            Spacer(
                                Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                    fixedSubHost {
                                        rowComposes[0]++
                                        if (hovered && hoverSeenOnFrame[0] < 0) {
                                            hoverSeenOnFrame[0] = currentFrame[0]
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            assertTrue(rowComposes[0] >= 1, "sanity: the row must have run")

            Snapshot.withMutableSnapshot { hovered = true }

            // Well past MAX_CONSECUTIVE_DEFERRAL_RE_ARMS, so a dropped invalidation shows up too.
            for (frame in 1..70) {
                currentFrame[0] = frame
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 1) * 16_000_000L)
            }

            assertTrue(
                hoverSeenOnFrame[0] > 0,
                "the row never observed its hover while the anchor's host kept a measure pending",
            )
            assertEquals(
                1,
                hoverSeenOnFrame[0],
                "the row must observe its hover in the frame it was written, not later",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * Two hosts are measure-pending in the same frame, and their node order inverts their
     * composition order. The slot of the shallow host is nested in the slot of the deep, busy
     * host, which is how an overlay's slot is nested in its anchor's slot. The measure pass
     * measures the shallow host first.
     *
     * Wave 2 leaves both slots to their hosts' measures, because both gates are closed. So wave 2
     * cannot order them. The measure pass has to. The deep slot writes [producedByEnclosing]
     * while it composes. The shallow slot compares that value with its own fresh read.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot hosted shallower than its enclosing slot waits for it when both hosts remeasure`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var enclosingContext by mutableStateOf<CompositionContext?>(null)
        val producedByEnclosing = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                val nestedContent: @Composable () -> Unit = {
                    val fresh = tick
                    if (producedByEnclosing[0] != fresh) tornReads[0]++
                    nestedSaw[0] = fresh
                }
                Box(Modifier.fillMaxSize()) {
                    // The shallow host, like an overlay's host under OverlayHost. Its policy
                    // captures tick, so it is measure-pending on every frame too.
                    enclosingContext?.let { context ->
                        SubcomposeLayout(compositionContext = context) { _ ->
                            val placeables =
                                subcompose(Unit, nestedContent).map {
                                    it.measure(Constraints.fixed(10, 10))
                                }
                            layout(10 + tick, 10) { placeables.forEach { it.place(0, 0) } }
                        }
                    }
                    // The deep, busy host, like the editor.
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            tickingFixedSubHost(tick) {
                                producedByEnclosing[0] = tick
                                val context = rememberCompositionContext()
                                SideEffect { enclosingContext = context }
                            }
                        }
                    }
                }
            }
            // The shallow host appears once the deep slot publishes its context.
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertEquals(0, nestedSaw[0], "sanity: the shallow slot must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 2) * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the shallow slot composed before the deep slot enclosing it",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the shallow slot did not deliver while both hosts were " +
                        "measure-pending",
                )
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The shallow-slot shape, with a shallow slot that has nothing of its own to compose. Its
     * content is one lambda instance, created outside composition, that hosts a leaf slot. Only
     * the leaf reads [tick]. The leaf's invalidation climbs the context chain, so wave 2 holds the
     * shallow slot back and the leaf waits for it. The shallow host measures first and finds
     * nothing to compose. The shallow slot must still wait for the deep slot, and then release
     * the leaf, which compares what the deep slot produced with its own fresh read.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a leaf below a shallow slot with nothing to compose composes after the deep slot on every busy frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var enclosingContext by mutableStateOf<CompositionContext?>(null)
        val producedByEnclosing = intArrayOf(-1)
        val middleComposes = intArrayOf(0)
        val tornReads = intArrayOf(0)
        val leafSaw = intArrayOf(-1)
        val middleContent: @Composable () -> Unit = {
            middleComposes[0]++
            fixedSubHost {
                val fresh = tick
                if (producedByEnclosing[0] != fresh) tornReads[0]++
                leafSaw[0] = fresh
            }
        }
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                Box(Modifier.fillMaxSize()) {
                    enclosingContext?.let { context ->
                        SubcomposeLayout(compositionContext = context) { _ ->
                            val placeables =
                                subcompose(Unit, middleContent).map {
                                    it.measure(Constraints.fixed(10, 10))
                                }
                            layout(10 + tick, 10) { placeables.forEach { it.place(0, 0) } }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            tickingFixedSubHost(tick) {
                                producedByEnclosing[0] = tick
                                val context = rememberCompositionContext()
                                SideEffect { enclosingContext = context }
                            }
                        }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertEquals(0, leafSaw[0], "sanity: the leaf slot must have run")
            val middleComposesBefore = middleComposes[0]

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 2) * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the leaf composed before the deep slot enclosing it",
                )
                assertEquals(
                    frame,
                    leafSaw[0],
                    "frame $frame: the leaf did not deliver while both hosts were measure-pending",
                )
            }
            assertEquals(
                middleComposesBefore,
                middleComposes[0],
                "sanity: the shallow slot has nothing to compose",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The shallow-slot shape, with a shallow host that reuses slots, as a lazy list does. A quiet
     * frame switches the slot id and deactivates the released slot. The next busy frame switches
     * back, so the host takes the deactivated node for reuse while the deep slot is pending. A
     * reused slot composes for its new slot id like a new composition, so it is not held. Holding
     * it would leave its nodes deactivated, and the host's measure would fail on them.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a reused slot hosted shallower than a pending enclosing slot composes when both hosts remeasure`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var slotId by mutableStateOf(0)
        var enclosingContext by mutableStateOf<CompositionContext?>(null)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                val nestedContent: @Composable () -> Unit = {
                    nestedSaw[0] = tick
                    Spacer(Modifier.size(1.dp))
                }
                Box(Modifier.fillMaxSize()) {
                    enclosingContext?.let { context ->
                        SubcomposeLayout(
                            compositionContext = context,
                            state = remember { SubcomposeLayoutState(SubcomposeSlotReusePolicy(2)) },
                        ) { _ ->
                            val placeables =
                                subcompose(slotId, nestedContent).map {
                                    it.measure(Constraints.fixed(10, 10))
                                }
                            layout(10 + tick, 10) { placeables.forEach { it.place(0, 0) } }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            tickingFixedSubHost(tick) {
                                val context = rememberCompositionContext()
                                SideEffect { enclosingContext = context }
                            }
                        }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertEquals(0, nestedSaw[0], "sanity: the shallow slot must have run")

            var time = 48_000_000L
            for (frame in 1..10) {
                // A quiet frame: the deep slot is not pending. The released slot deactivates.
                Snapshot.withMutableSnapshot { slotId = 1 - slotId }
                scene.render(time)
                time += 16_000_000L
                scene.render(time)
                time += 16_000_000L
                // A busy frame: the host reuses the deactivated slot while the deep slot is pending.
                Snapshot.withMutableSnapshot {
                    tick = frame
                    slotId = 1 - slotId
                }
                scene.render(time)
                time += 16_000_000L
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the reused shallow slot did not deliver while both hosts were " +
                        "measure-pending",
                )
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The shallow-slot shape, with a slot whose content reads no state. The shallow host's measure
     * reads [tick] and passes it to a new content lambda. The slot has no invalidations, so only
     * the content change makes the host compose it. A held slot must then compose the new lambda
     * when its host is refreshed; recomposing the old one directly delivers nothing. The refresh
     * measure supplies the same lambda as the held measure, so a hold that already recorded the
     * new lambda would see no change and compose nothing.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a held slot composes the content its host supplies after the enclosing slot`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var enclosingContext by mutableStateOf<CompositionContext?>(null)
        val producedByEnclosing = intArrayOf(-1)
        // One lambda per value, so the refresh measure supplies the same lambda as the held one.
        val contentByTick = HashMap<Int, @Composable () -> Unit>()
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                Box(Modifier.fillMaxSize()) {
                    enclosingContext?.let { context ->
                        SubcomposeLayout(compositionContext = context) { _ ->
                            val t = tick
                            val content =
                                contentByTick.getOrPut(t) {
                                    val slot: @Composable () -> Unit = {
                                        nestedSaw[0] = t
                                        if (producedByEnclosing[0] != t) tornReads[0]++
                                    }
                                    slot
                                }
                            val placeables =
                                subcompose(Unit, content).map {
                                    it.measure(Constraints.fixed(10, 10))
                                }
                            layout(10 + t, 10) { placeables.forEach { it.place(0, 0) } }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            tickingFixedSubHost(tick) {
                                producedByEnclosing[0] = tick
                                val context = rememberCompositionContext()
                                SideEffect { enclosingContext = context }
                            }
                        }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertEquals(0, nestedSaw[0], "sanity: the shallow slot must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 2) * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the held slot composed before the deep slot enclosing it",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the held slot did not compose its host's new content",
                )
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A composition with no gate, nested in a busy host's slot. `rememberCompositionContext()`
     * plus `Composition(...)` creates one, as a Swing menu or a popup with its own composition
     * does. No host measure refreshes it, so only the runtime can deliver it, after its
     * enclosing slot.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a gateless composition in a busy host's slot composes after the slot on every busy frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        val producedByEnclosing = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                tickingFixedSubHost(tick) {
                    producedByEnclosing[0] = tick
                    val context = rememberCompositionContext()
                    DisposableEffect(context) {
                        val gateless = Composition(NoNodeApplier(), context)
                        gateless.setContent {
                            val fresh = tick
                            if (producedByEnclosing[0] != fresh) tornReads[0]++
                            nestedSaw[0] = fresh
                        }
                        onDispose { gateless.dispose() }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(0, nestedSaw[0], "sanity: the gateless composition must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 1) * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the gateless composition composed before its enclosing slot",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the gateless composition did not deliver while its enclosing " +
                        "host was measure-pending",
                )
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The plain-nesting order and delivery test, inside a [LookaheadScope]. A slot there is
     * composed from the lookahead measure, so the refresh must request a lookahead remeasure.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a nested subcomposition in a lookahead scope composes after its enclosing one on every busy frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                LookaheadScope {
                    tickingFixedSubHost(tick) {
                        val captured = tick
                        fixedSubHost {
                            val fresh = tick
                            if (captured != fresh) tornReads[0]++
                            nestedSaw[0] = fresh
                        }
                    }
                }
            }
            scene.render(0)
            assertEquals(0, nestedSaw[0], "sanity: the nested subcomposition must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render(frame * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the nested subcomposition composed before its enclosing one",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the nested subcomposition did not deliver in a lookahead scope",
                )
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A slot with nothing to compose releases its waiters from inside its host's measure. A
     * gateless waiter is then recomposed right there. Its reads must not become dependencies of
     * that measure. Otherwise every later write of the waiter's state remeasures the host, which
     * can be an expensive one, such as an overlay's or a lazy list's.
     *
     * The middle slot's content is stable and reads nothing. It reaches wave 2 only because an
     * invalidation of the gateless composition nested in it climbs the composition chain. The
     * busy frame both writes the gateless composition's state and invalidates its scope, and
     * either would climb. The middle content is created outside composition, because a lambda
     * that a recomposing parent re-creates would give the middle slot work of its own. The
     * middle host counts its measures.
     * After the busy frame that releases the gateless composition, a write of only the gateless
     * composition's state must not measure the middle host again.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a waiter released by a slot with nothing to compose does not subscribe its host's measure`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var leafValue by mutableStateOf(0)
        val middleMeasures = intArrayOf(0)
        val leafSaw = intArrayOf(-1)
        val leafScope = arrayOfNulls<RecomposeScope>(1)
        // Created outside composition, so a busy frame's recompose of the root does not update
        // it, and the middle slot keeps nothing to compose.
        val middleContent: @Composable () -> Unit = {
            val context = rememberCompositionContext()
            DisposableEffect(context) {
                val gateless = Composition(NoNodeApplier(), context)
                gateless.setContent {
                    leafScope[0] = currentRecomposeScope
                    leafSaw[0] = leafValue
                }
                onDispose { gateless.dispose() }
            }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                tickingFixedSubHost(tick) {
                    @Suppress("UNUSED_EXPRESSION")
                    tick
                    SubcomposeLayout { constraints ->
                        middleMeasures[0]++
                        val placeables =
                            subcompose(Unit, middleContent).map { it.measure(constraints) }
                        layout(10, 10) { placeables.forEach { it.place(0, 0) } }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(0, leafSaw[0], "sanity: the gateless composition must have run")

            // One busy frame: the gateless composition's change reaches the middle slot, which
            // has nothing to compose, and the middle host's measure releases it.
            Snapshot.withMutableSnapshot {
                tick = 1
                leafValue = 1
            }
            leafScope[0]!!.invalidate()
            scene.render(32_000_000)
            assertEquals(1, leafSaw[0], "sanity: the gateless composition must deliver")

            // A quiet frame, so the busy host settles.
            scene.render(48_000_000)
            val measuresBefore = middleMeasures[0]

            Snapshot.withMutableSnapshot { leafValue = 2 }
            scene.render(64_000_000)
            assertEquals(2, leafSaw[0], "sanity: the gateless composition must deliver again")
            assertEquals(
                measuresBefore,
                middleMeasures[0],
                "a write of only the gateless composition's state measured the middle host, so " +
                    "the release recorded the gateless composition's reads in that measure",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The anchor and the state that removes it live in one plain composition, and the overlay
     * content reads that same state, so the same change invalidates the content.
     *
     * Two mechanisms together keep the content from composing against it. The remover's
     * recompose drops the group that holds the anchor's composition context, which reports the
     * overlay's composition as removed, so the recomposer skips it for the rest of the turn. The
     * measure pass of the same frame then empties its slot, because `isLive` is false. The skip
     * lasts one turn only, so the test also checks that no later frame composes the content.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `overlay content that reads its anchor's removal state never composes against it`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var entity by mutableStateOf<String?>("present")
        val staleReads = intArrayOf(0)
        val overlayComposes = intArrayOf(0)
        val overlayRemovals = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    if (entity != null) {
                        Spacer(
                            Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                overlayComposes[0]++
                                DisposableEffect(Unit) { onDispose { overlayRemovals[0]++ } }
                                if (entity == null) staleReads[0]++
                            }
                        )
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(1, overlayComposes[0], "sanity: the overlay content must have run")

            Snapshot.withMutableSnapshot { entity = null }
            scene.render(32_000_000)

            assertEquals(0, staleReads[0], "the overlay content composed against its removal state")
            assertEquals(1, overlayRemovals[0], "the overlay content must be gone in this frame")

            // The recomposer skips a removed composition for one turn only. The content must
            // already be gone, so later frames cannot compose it either.
            scene.render(48_000_000)
            scene.render(64_000_000)
            assertEquals(0, staleReads[0], "the overlay content composed on a later frame")
            assertEquals(1, overlayComposes[0], "the overlay content must never compose again")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The same removal as the test above, but the anchor and its gate sit in a busy editor slot,
     * whose host is measure-pending on the frame of the removal. The slot composes in measure,
     * and the overlay's composition waits for it, because the slot encloses it.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `overlay content anchored in a busy slot never composes against its anchor's removal state`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var entity by mutableStateOf<String?>("present")
        val staleReads = intArrayOf(0)
        val overlayComposes = intArrayOf(0)
        val overlayRemovals = intArrayOf(0)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        tickingFixedSubHost(tick) {
                            @Suppress("UNUSED_EXPRESSION")
                            tick
                            if (entity != null) {
                                Spacer(
                                    Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                        overlayComposes[0]++
                                        DisposableEffect(Unit) {
                                            onDispose { overlayRemovals[0]++ }
                                        }
                                        if (entity == null) staleReads[0]++
                                    }
                                )
                            }
                        }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertTrue(overlayComposes[0] >= 1, "sanity: the overlay content must have run")

            Snapshot.withMutableSnapshot {
                tick = 1
                entity = null
            }
            scene.render(48_000_000)

            assertEquals(0, staleReads[0], "the overlay content composed against its removal state")
            assertEquals(1, overlayRemovals[0], "the overlay content must be gone in this frame")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * Fleet's Go To panel shape. The overlay content holds the gate, like WindowView's dialogs
     * loop, and the reader sits in a nested subcomposition below it, like the panel body inside
     * Resizable's BoxWithConstraints. The anchor itself stays. The overlay's composition is the
     * remover, and it encloses the reader, so the reader must not compose against the removal.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a reader nested below an overlay's own gate never composes against the removal`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var entity by mutableStateOf<String?>("present")
        val staleReads = intArrayOf(0)
        val readerComposes = intArrayOf(0)
        val readerRemovals = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    Spacer(
                        Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                            if (entity != null) {
                                fixedSubHost {
                                    readerComposes[0]++
                                    DisposableEffect(Unit) { onDispose { readerRemovals[0]++ } }
                                    if (entity == null) staleReads[0]++
                                }
                            }
                        }
                    )
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(1, readerComposes[0], "sanity: the reader must have run")

            Snapshot.withMutableSnapshot { entity = null }
            scene.render(32_000_000)

            assertEquals(0, staleReads[0], "the reader composed against the removal")
            assertEquals(1, readerRemovals[0], "the reader must be gone in this frame")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The anchor and its gate sit in a nested slot whose host is idle, so that slot recomposes
     * standalone in wave 2. Its recompose drops the anchor, and the overlay's composition must be
     * skipped even though wave 2 reaches it later in the same pass.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `overlay content anchored in an idle nested slot never composes against its anchor's removal state`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var entity by mutableStateOf<String?>("present")
        val staleReads = intArrayOf(0)
        val overlayComposes = intArrayOf(0)
        val overlayRemovals = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    fixedSubHost {
                        if (entity != null) {
                            Spacer(
                                Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                    overlayComposes[0]++
                                    DisposableEffect(Unit) { onDispose { overlayRemovals[0]++ } }
                                    if (entity == null) staleReads[0]++
                                }
                            )
                        }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertEquals(1, overlayComposes[0], "sanity: the overlay content must have run")

            Snapshot.withMutableSnapshot { entity = null }
            scene.render(48_000_000)
            scene.render(64_000_000)

            assertEquals(0, staleReads[0], "the overlay content composed against its removal state")
            assertEquals(1, overlayRemovals[0], "the overlay content must be gone")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * Composition is not the only phase that reads. The overlay content here also reads the
     * removal state in its measure policy and in its draw. Both must not run against it after the
     * anchor is removed, because the host empties the slot before the content is measured or
     * drawn.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `overlay content is neither measured nor drawn against its anchor's removal state`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var entity by mutableStateOf<String?>("present")
        val staleMeasures = intArrayOf(0)
        val staleDraws = intArrayOf(0)
        val measures = intArrayOf(0)
        val draws = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    if (entity != null) {
                        Spacer(
                            Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                Layout(
                                    content = {},
                                    modifier =
                                        Modifier.size(10.dp).drawBehind {
                                            draws[0]++
                                            if (entity == null) staleDraws[0]++
                                        },
                                ) { _, constraints ->
                                    measures[0]++
                                    if (entity == null) staleMeasures[0]++
                                    layout(constraints.minWidth, constraints.minHeight) {}
                                }
                            }
                        )
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertTrue(measures[0] >= 1, "sanity: the overlay content must have been measured")
            assertTrue(draws[0] >= 1, "sanity: the overlay content must have been drawn")

            Snapshot.withMutableSnapshot { entity = null }
            scene.render(48_000_000)
            scene.render(64_000_000)

            assertEquals(0, staleMeasures[0], "the overlay content measured against its removal")
            assertEquals(0, staleDraws[0], "the overlay content drew against its removal")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The reader is nested one composition below the overlay content, as the Go To panel body
     * sits in Resizable's BoxWithConstraints, and the anchor itself is removed by the state the
     * reader reads. Removing the anchor removes the group that holds the anchor's composition
     * context. That reports the overlay's composition as removed, and the report recurses into
     * every composition-context reference in its slot table. So the nested reader is skipped for
     * the rest of the turn as well.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a reader nested in overlay content never composes against its anchor's removal state`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var entity by mutableStateOf<String?>("present")
        val staleReads = intArrayOf(0)
        val readerComposes = intArrayOf(0)
        val readerRemovals = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    if (entity != null) {
                        Spacer(
                            Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                fixedSubHost {
                                    readerComposes[0]++
                                    DisposableEffect(Unit) { onDispose { readerRemovals[0]++ } }
                                    if (entity == null) staleReads[0]++
                                }
                            }
                        )
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertEquals(1, readerComposes[0], "sanity: the nested reader must have run")

            Snapshot.withMutableSnapshot { entity = null }
            scene.render(48_000_000)
            scene.render(64_000_000)

            assertEquals(0, staleReads[0], "the nested reader composed against its removal state")
            assertEquals(1, readerRemovals[0], "the nested reader must be gone")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The boundary of what ordering can do. The entity dies in one transaction, and the state
     * that hides its overlay changes only in a later one, for example because the gate is a
     * separate query that catches up a frame later. In between, nothing is due to remove the
     * content, so every rule in this file lets it compose against the dead entity. This test
     * pins that as the expected outcome. A read site that can see a retracted entity through a
     * lagging gate needs its own guard, such as `takeIfExists()`.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `overlay content composes against a dead entity while its gate lags a frame behind`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var entity by mutableStateOf<String?>("present")
        var gateOpen by mutableStateOf(true)
        val staleReads = intArrayOf(0)
        val overlayRemovals = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    if (gateOpen) {
                        Spacer(
                            Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                DisposableEffect(Unit) { onDispose { overlayRemovals[0]++ } }
                                if (entity == null) staleReads[0]++
                            }
                        )
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(0, staleReads[0], "sanity: no stale read while the entity lives")

            Snapshot.withMutableSnapshot { entity = null }
            scene.render(32_000_000)
            Snapshot.withMutableSnapshot { gateOpen = false }
            scene.render(48_000_000)

            assertEquals(
                1,
                staleReads[0],
                "with the gate a frame behind, the content composes against the dead entity once",
            )
            assertEquals(1, overlayRemovals[0], "the content goes once the gate catches up")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * An overlay anchored in a lazy list row, like a tooltip on a result row. One transaction
     * deletes the row's entity and drops it from the list. A lazy list does not remove a row
     * that leaves its items in composition. Its measure pass deactivates the row's slot for
     * reuse, which happens after the recompose stage. [rowReadsEntity] chooses whether the row
     * itself also reads the entity, or only the overlay content does.
     *
     * Deactivation is not removal, so the overlay host's gate would close only once snapshot
     * apply notifications arrive. Two mechanisms close that gap, and each closes it alone. The
     * composer skips compositions created under deactivated content for the rest of the turn,
     * behind [ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled]. And the anchor's
     * `onDispose` marks the overlay's host measure-pending at once, so the overlay's composition
     * waits for the measure that empties it. [skipOnDeactivation] turns the first one off, which
     * shows the second one alone is enough.
     */
    @OptIn(ExperimentalComposeApi::class, InternalComposeApi::class)
    private fun assertLazyRowOverlayNeverReadsItsDeletedEntity(
        rowReadsEntity: Boolean,
        skipOnDeactivation: Boolean = true,
    ) {
        val previousSkip = ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled
        ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled = skipOnDeactivation
        try {
            assertLazyRowOverlayNeverReadsItsDeletedEntityWithCurrentFlags(rowReadsEntity)
        } finally {
            ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled = previousSkip
        }
    }

    private fun assertLazyRowOverlayNeverReadsItsDeletedEntityWithCurrentFlags(
        rowReadsEntity: Boolean
    ) {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var entities by mutableStateOf(listOf("a", "b"))
        var deleted by mutableStateOf(emptySet<String>())
        val staleReads = intArrayOf(0)
        val overlayComposes = intArrayOf(0)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(entities, key = { it }) { entity ->
                            if (rowReadsEntity && entity in deleted) staleReads[0]++
                            Spacer(
                                Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                    overlayComposes[0]++
                                    if (entity in deleted) staleReads[0]++
                                }
                            )
                        }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            scene.render(32_000_000)
            assertEquals(2, overlayComposes[0], "sanity: both rows' overlays must have run")

            Snapshot.withMutableSnapshot {
                entities = listOf("a")
                deleted = setOf("b")
            }
            scene.render(48_000_000)
            scene.render(64_000_000)

            assertEquals(0, staleReads[0], "a row or its overlay composed against its deleted entity")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay on a lazy row never composes against the row's deleted entity`() {
        assertLazyRowOverlayNeverReadsItsDeletedEntity(rowReadsEntity = false)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a lazy row and its overlay never compose against the row's deleted entity`() {
        assertLazyRowOverlayNeverReadsItsDeletedEntity(rowReadsEntity = true)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay on a lazy row stays safe without the deactivation skip`() {
        assertLazyRowOverlayNeverReadsItsDeletedEntity(
            rowReadsEntity = false,
            skipOnDeactivation = false,
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a lazy row and its overlay stay safe without the deactivation skip`() {
        assertLazyRowOverlayNeverReadsItsDeletedEntity(
            rowReadsEntity = true,
            skipOnDeactivation = false,
        )
    }

    /**
     * A host can also re-run a slot through a paused precomposition, as a lazy list's prefetch
     * does. When the slot is held back, applying the paused composition is what brings it up to
     * date, so the compositions that wait for it must be released right then.
     *
     * The host precomposes slot `a` without using it in measure. On a busy frame the slot is held
     * back, and a gateless composition in it waits for it. The host then re-runs the slot through
     * a paused precomposition.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `applying a held slot's paused precomposition releases its waiters`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var leafValue by mutableStateOf(0)
        val leafSaw = intArrayOf(-1)
        val state = SubcomposeLayoutState()
        // Created outside composition, so a recompose of the root does not update it.
        val slotContent: @Composable () -> Unit = {
            @Suppress("UNUSED_EXPRESSION")
            tick
            val context = rememberCompositionContext()
            DisposableEffect(context) {
                val gateless = Composition(NoNodeApplier(), context)
                gateless.setContent { leafSaw[0] = leafValue }
                onDispose { gateless.dispose() }
            }
        }
        fun runPausedPrecomposition() {
            val paused = state.createPausedPrecomposition("a", slotContent)
            while (!paused.isComplete) paused.resume { false }
            paused.apply()
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                // The policy captures tick, so a change marks this host measure-pending. It never
                // uses slot `a` itself.
                SubcomposeLayout(state) { _ -> layout(10 + tick, 10) {} }
            }
            scene.render(0)
            runPausedPrecomposition()
            assertEquals(0, leafSaw[0], "sanity: the gateless composition must have run")

            Snapshot.withMutableSnapshot {
                tick = 1
                leafValue = 1
            }
            scene.render(16_000_000)
            assertEquals(0, leafSaw[0], "sanity: the waiter is held behind the precomposed slot")

            runPausedPrecomposition()
            assertEquals(
                1,
                leafSaw[0],
                "applying the held slot's paused precomposition must release its waiter at once",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    // An applier for a composition that emits no nodes. The gateless test needs only the
    // composition, not a tree.
    private class NoNodeApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) {}

        override fun insertBottomUp(index: Int, instance: Unit) {}

        override fun remove(index: Int, count: Int) {}

        override fun move(from: Int, to: Int, count: Int) {}

        override fun onClear() {}
    }
}
