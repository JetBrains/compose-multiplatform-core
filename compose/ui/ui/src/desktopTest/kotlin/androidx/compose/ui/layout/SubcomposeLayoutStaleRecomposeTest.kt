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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionServices
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.ParentDrivenHost
import androidx.compose.runtime.ParentDrivenHosting
import androidx.compose.runtime.ParentDrivenHostingKey
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.NodeCoordinator
import androidx.compose.ui.node.Owner
import androidx.compose.ui.platform.DefaultUiApplier
import androidx.compose.ui.platform.LocalPlatformPrefetchScheduler
import androidx.compose.ui.platform.PlatformPrefetchRequest
import androidx.compose.ui.platform.PlatformPrefetchRequestScope
import androidx.compose.ui.platform.PlatformPrefetchScheduler
import androidx.compose.ui.scene.ComposeSceneFeatureFlags
import androidx.compose.ui.test.SchedulingDispatcherFixture
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlin.concurrent.thread
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
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

    // Test-only local for the gate tests: the root provides the value it also captures.
    private val LocalGateTestValue = compositionLocalOf { -1 }

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

    // Like [tickingFixedSubHost], and sets [measuring] while its measure subcomposes, so a test
    // can tell whether a composition runs inside that measure.
    @Composable
    private fun flaggedTickingFixedSubHost(
        tick: Int,
        measuring: BooleanArray,
        content: @Composable () -> Unit,
    ) {
        SubcomposeLayout { _ ->
            val childConstraints = Constraints.fixed(50, 50)
            measuring[0] = true
            val placeables =
                try {
                    subcompose(Unit, content).map { it.measure(childConstraints) }
                } finally {
                    measuring[0] = false
                }
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
            // left to its host's measure when the recompose block reaches it, while C is not.
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
     * therefore always sorts before the slot in the recompose block. This test puts the anchor inside a
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
     * context chain. So the middle composition goes to its host's measure, and the leaf waits for
     * it. The
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
     * The recompose block leaves both slots to their hosts' measures, because both hosts are
     * busy. So the recompose block cannot order them. The measure pass has to. The deep slot writes [producedByEnclosing]
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
     * the leaf reads [tick]. The leaf's invalidation climbs the context chain, so the shallow slot
     * goes to its host's measure and the leaf waits for it. The shallow host measures first and finds
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
     * A busy slot whose host its parent measures and places in the lookahead pass only. The host
     * is never placed in the main pass and no placed parent measures it there, so only its
     * lookahead measure is scheduled. That measure re-runs the
     * slot with fresh captures, so the slot must be held back rather than recomposed standalone,
     * and a composition nested in it must wait for it and still deliver in the same frame.
     *
     * The host's measure hands the slot a new content lambda that captures the value the
     * enclosing composition read. Recomposed standalone, the slot would pair the old lambda's
     * capture with its own fresh read. The nested composition has no host and compares its fresh
     * read with what the slot captured.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a busy slot of a host placed only in the lookahead pass is held and delivers its waiter`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        val producedBySlot = intArrayOf(-1)
        val slotTornReads = intArrayOf(0)
        val nestedTornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val hostLookaheadMeasures = intArrayOf(0)
        val hostMainMeasures = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                LookaheadScope {
                    Layout(
                        content = {
                            // The policy captures tick, so a change marks this host's lookahead
                            // measure pending.
                            val captured = tick
                            SubcomposeLayout { _ ->
                                if (isLookingAhead) {
                                    hostLookaheadMeasures[0]++
                                } else {
                                    hostMainMeasures[0]++
                                }
                                val placeables =
                                    subcompose(Unit) {
                                            if (captured != tick) slotTornReads[0]++
                                            producedBySlot[0] = captured
                                            val context = rememberCompositionContext()
                                            DisposableEffect(context) {
                                                val gateless = Composition(NoNodeApplier(), context)
                                                gateless.setContent {
                                                    val fresh = tick
                                                    if (producedBySlot[0] != fresh) {
                                                        nestedTornReads[0]++
                                                    }
                                                    nestedSaw[0] = fresh
                                                }
                                                onDispose { gateless.dispose() }
                                            }
                                        }
                                        .map { it.measure(Constraints.fixed(50, 50)) }
                                layout(50 + captured, 50) { placeables.forEach { it.place(0, 0) } }
                            }
                        }
                    ) { measurables, constraints ->
                        // Measured and placed in the lookahead pass, and not at all in the main
                        // one.
                        val placeables =
                            if (isLookingAhead) {
                                measurables.map { it.measure(constraints) }
                            } else {
                                emptyList()
                            }
                        layout(100, 100) { placeables.forEach { it.place(0, 0) } }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(0, nestedSaw[0], "sanity: the nested composition must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                val lookaheadMeasuresBefore = hostLookaheadMeasures[0]
                scene.render((frame + 1) * 16_000_000L)
                assertTrue(
                    hostLookaheadMeasures[0] > lookaheadMeasuresBefore,
                    "sanity: frame $frame must measure the host in the lookahead pass",
                )
                assertEquals(
                    0,
                    hostMainMeasures[0],
                    "sanity: the host must never be measured in the main pass",
                )
                assertEquals(
                    0,
                    slotTornReads[0],
                    "frame $frame: the slot recomposed standalone against its host's old capture",
                )
                assertEquals(
                    0,
                    nestedTornReads[0],
                    "frame $frame: the nested composition composed before its enclosing slot",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the nested composition did not deliver while its enclosing " +
                        "host was measure-pending in the lookahead pass only",
                )
            }

            repeat(3) { scene.render((12 + it) * 16_000_000L) }
            assertFalse(scene.hasInvalidations(), "the scene must go idle after the busy frames")
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
     * The middle slot's content is stable and reads nothing. It is due at all only because an
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
     * standalone in the recompose block. Its recompose drops the anchor, and the overlay's
     * composition must be skipped even though the recompose block reaches it later in the same
     * pass.
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
        overlayForcesRemeasure: Boolean = false,
    ) {
        val previousSkip = ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled
        ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled = skipOnDeactivation
        try {
            assertLazyRowOverlayNeverReadsItsDeletedEntityWithCurrentFlags(
                rowReadsEntity,
                overlayForcesRemeasure,
            )
        } finally {
            ComposeRuntimeFlags.isNestedCompositionSkipOnDeactivationEnabled = previousSkip
        }
    }

    // [overlayForcesRemeasure] makes the overlay content force a remeasure of an unrelated node
    // from a `SideEffect` in the frame of the deletion. A slot that composes on its own at the end
    // of a layout pass applies there, so the forced remeasure runs a layout of a single node,
    // and the layout-completed listeners, in the middle of the listeners of the full pass.
    private fun assertLazyRowOverlayNeverReadsItsDeletedEntityWithCurrentFlags(
        rowReadsEntity: Boolean,
        overlayForcesRemeasure: Boolean = false,
    ) {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var entities by mutableStateOf(listOf("a", "b"))
        var deleted by mutableStateOf(emptySet<String>())
        val staleReads = intArrayOf(0)
        val overlayComposes = intArrayOf(0)
        val forcing = booleanArrayOf(false)
        val forcedRemeasures = intArrayOf(0)
        val unrelatedNode = arrayOfNulls<Remeasurement>(1)
        val remeasurementModifier =
            object : RemeasurementModifier {
                override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                    unrelatedNode[0] = remeasurement
                }
            }
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                if (overlayForcesRemeasure) Box(Modifier.size(1.dp).then(remeasurementModifier))
                OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(entities, key = { it }) { entity ->
                            if (rowReadsEntity && entity in deleted) staleReads[0]++
                            Spacer(
                                Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                    overlayComposes[0]++
                                    if (entity in deleted) staleReads[0]++
                                    if (overlayForcesRemeasure) {
                                        SideEffect {
                                            if (forcing[0]) {
                                                forcedRemeasures[0]++
                                                unrelatedNode[0]!!.forceRemeasure()
                                            }
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
            scene.render(32_000_000)
            assertEquals(2, overlayComposes[0], "sanity: both rows' overlays must have run")

            forcing[0] = true
            Snapshot.withMutableSnapshot {
                entities = listOf("a")
                deleted = setOf("b")
            }
            scene.render(48_000_000)
            forcing[0] = false
            scene.render(64_000_000)

            if (overlayForcesRemeasure) {
                assertTrue(forcedRemeasures[0] > 0, "sanity: an overlay must force a remeasure")
            }
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

    // The same four shapes with an overlay whose apply forces a remeasure. The overlays of both
    // rows compose on their own at the end of the full pass; the surviving row's overlay must
    // not make the deleted row hand back what waits for it before every slot of the pass has
    // decided whether it waits.

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay on a lazy row never composes against the row's deleted entity when an overlay forces a remeasure`() {
        assertLazyRowOverlayNeverReadsItsDeletedEntity(
            rowReadsEntity = false,
            overlayForcesRemeasure = true,
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a lazy row and its overlay never compose against the row's deleted entity when an overlay forces a remeasure`() {
        assertLazyRowOverlayNeverReadsItsDeletedEntity(
            rowReadsEntity = true,
            overlayForcesRemeasure = true,
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an overlay on a lazy row stays safe without the deactivation skip when an overlay forces a remeasure`() {
        assertLazyRowOverlayNeverReadsItsDeletedEntity(
            rowReadsEntity = false,
            skipOnDeactivation = false,
            overlayForcesRemeasure = true,
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a lazy row and its overlay stay safe without the deactivation skip when an overlay forces a remeasure`() {
        assertLazyRowOverlayNeverReadsItsDeletedEntity(
            rowReadsEntity = true,
            skipOnDeactivation = false,
            overlayForcesRemeasure = true,
        )
    }

    /**
     * A host can also run a slot only through a paused precomposition, as a lazy list's prefetch
     * does, without using it in measure. Its next measure does not re-run such a slot, so the
     * recomposer does not hand it to the host: it recomposes it, and a gateless composition in it,
     * in the frame of the change, as stock Compose does. A paused precomposition applied later
     * then finds nothing to deliver.
     *
     * The host precomposes slot `a` without using it in measure. A busy frame changes the slot
     * and the gateless composition in it.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a waiter of a slot run only by a paused precomposition delivers in the frame of the change`() {
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
            assertEquals(
                1,
                leafSaw[0],
                "the waiter of a slot the host does not use must deliver in the frame",
            )

            runPausedPrecomposition()
            assertEquals(1, leafSaw[0], "a later paused precomposition keeps the delivered value")
            repeat(3) { scene.render((2 + it) * 16_000_000L) }
            assertFalse(scene.hasInvalidations(), "the scene must go idle")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A precomposed slot the host never uses in measure. Its host would not re-run it, so the
     * recomposer does not hand it over, and a composition waiting for it is not handed to it
     * either: both recompose in the frame of the change, and the scene then goes idle. The slot
     * a host takes and then stops using is pinned by the reuse test at the end of this file.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a waiter of a precomposed slot its host does not use delivers in the frame of the change`() {
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
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                // The policy captures tick, so a change marks this host measure-pending. It never
                // uses slot `a` itself.
                SubcomposeLayout(state) { _ -> layout(10 + tick, 10) {} }
            }
            scene.render(0)
            val handle = state.precompose("a", slotContent)
            assertEquals(0, leafSaw[0], "sanity: the gateless composition must have run")

            Snapshot.withMutableSnapshot {
                tick = 1
                leafValue = 1
            }
            scene.render(16_000_000)
            assertEquals(
                1,
                leafSaw[0],
                "the waiter of a slot its host does not use must deliver in the frame",
            )

            repeat(3) { scene.render((3 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the waiter has delivered",
            )
            handle.dispose()
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A precomposed slot its host does not use, nested in a slot whose host is busy. Its own host
     * declines it, so the busy slot takes it as a waiter when the frame changes the root. Its
     * host's measure would not re-run it, because the host does not use it: so its release must
     * not ask that host to measure, and it composes on its own, as the recomposer composes a slot
     * its host does not use, in the frame of the change.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a precomposed slot nested in a busy slot delivers in the frame of the change without a measure of its host`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var leafValue by mutableStateOf(0)
        val leafSaw = intArrayOf(-1)
        val innerMeasures = intArrayOf(0)
        val state = SubcomposeLayoutState()
        // Created outside composition, so a recompose of the root gives neither host new content
        // nor a new measure policy.
        val slotContent: @Composable () -> Unit = { leafSaw[0] = leafValue }
        val innerPolicy: SubcomposeMeasureScope.(Constraints) -> MeasureResult = {
            innerMeasures[0]++
            layout(10, 10) {}
        }
        val busyContent: @Composable () -> Unit = {
            SubcomposeLayout(state, measurePolicy = innerPolicy)
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent { tickingFixedSubHost(tick, busyContent) }
            scene.render(0)
            val handle = state.precompose("a", slotContent)
            assertEquals(0, leafSaw[0], "sanity: the precomposed slot must have composed")

            for (frame in 1..3) {
                Snapshot.withMutableSnapshot {
                    tick = frame
                    leafValue = frame
                }
                innerMeasures[0] = 0
                scene.render(frame * 16_000_000L)
                assertEquals(
                    frame,
                    leafSaw[0],
                    "frame $frame: the precomposed slot must deliver in the frame",
                )
                assertEquals(0, innerMeasures[0], "frame $frame: its host must not measure for it")
            }

            repeat(3) { scene.render((4 + it) * 16_000_000L) }
            assertFalse(scene.hasInvalidations(), "the scene must go idle")
            handle.dispose()
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The same for a lazy list's prefetched item under a busy host: the list precomposed it
     * ahead of a scroll and does not use it in its measure yet. The item must deliver its change
     * in the frame of the change, and the list must not measure for it.
     */
    @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
    @Test
    fun `a prefetched lazy item under a busy host delivers in the frame of the change without a measure of its list`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val requests = ArrayDeque<PlatformPrefetchRequest>()
        val prefetchScheduler =
            object : PlatformPrefetchScheduler {
                override fun scheduleHighPriorityPrefetch(request: PlatformPrefetchRequest) {
                    requests += request
                }

                override fun scheduleLowPriorityPrefetch(request: PlatformPrefetchRequest) {
                    requests += request
                }
            }
        val unlimitedTime =
            object : PlatformPrefetchRequestScope {
                override fun availableTimeNanos(): Long = Long.MAX_VALUE
            }
        fun runPrefetches() {
            while (requests.isNotEmpty()) {
                val request = requests.removeFirst()
                while (with(request) { unlimitedTime.execute() }) {}
            }
        }
        var tick by mutableStateOf(0)
        var itemValue by mutableStateOf(0)
        val itemSaw = IntArray(20) { -1 }
        val listMeasures = intArrayOf(0)
        val listState = LazyListState()
        // Created outside composition, so a recompose of the root gives the busy host no new
        // content, and the list no new modifier.
        val listModifier =
            Modifier.fillMaxSize().layout { measurable, constraints ->
                listMeasures[0]++
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        val busyContent: @Composable () -> Unit = {
            CompositionLocalProvider(LocalPlatformPrefetchScheduler provides prefetchScheduler) {
                LazyColumn(listModifier, state = listState) {
                    items(20) { index ->
                        itemSaw[index] = itemValue
                        Spacer(Modifier.size(10.dp))
                    }
                }
            }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent { tickingFixedSubHost(tick, busyContent) }
            scene.render(0)
            listState.dispatchRawDelta(1f)
            scene.render(16_000_000)
            runPrefetches()
            val prefetched = listState.layoutInfo.visibleItemsInfo.last().index + 1
            assertEquals(0, itemSaw[prefetched], "sanity: the list must have prefetched an item")

            for (frame in 1..3) {
                Snapshot.withMutableSnapshot {
                    tick = frame
                    itemValue = frame
                }
                listMeasures[0] = 0
                scene.render((frame + 1) * 16_000_000L)
                assertEquals(
                    prefetched,
                    listState.layoutInfo.visibleItemsInfo.last().index + 1,
                    "sanity: frame $frame: the prefetched item must still be out of view",
                )
                assertEquals(
                    frame,
                    itemSaw[prefetched],
                    "frame $frame: the prefetched item must deliver in the frame",
                )
                assertEquals(0, listMeasures[0], "frame $frame: the list must not measure for it")
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * Three slots nested in composition order: the busy slot A encloses S, and S encloses O. Their
     * hosts sit in the reverse node order, O's the shallowest and A's the deepest, as an overlay's
     * host sits above its anchor's. The measure pass handles pending nodes shallowest first.
     *
     * S and O read no state; their hosts' measures supply a new content lambda per value, so only
     * A is held back in the recompose block. The measure holds O and then S behind A, because both
     * have a new lambda. A's report refreshes both hosts, and O's host is measured first again. At
     * that point S has not composed yet. O must wait for S rather than compose ahead of it, so a
     * slot held at measure time has to be pending itself, not only its anchor. S writes
     * [producedByS] while it composes; O compares it with the value its own lambda captured.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot nested in a slot held at measure time waits for it`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var anchorContext by mutableStateOf<CompositionContext?>(null)
        var middleContext by mutableStateOf<CompositionContext?>(null)
        val producedByS = intArrayOf(-1)
        // One lambda per value, so a refresh measure supplies the same lambda as the held one.
        val middleContentByTick = HashMap<Int, @Composable () -> Unit>()
        val innerContentByTick = HashMap<Int, @Composable () -> Unit>()
        val tornReads = intArrayOf(0)
        val innerSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                Box(Modifier.fillMaxSize()) {
                    // O's host, the shallowest.
                    middleContext?.let { context ->
                        SubcomposeLayout(compositionContext = context) { _ ->
                            val t = tick
                            val content =
                                innerContentByTick.getOrPut(t) {
                                    val slot: @Composable () -> Unit = {
                                        innerSaw[0] = t
                                        if (producedByS[0] != t) tornReads[0]++
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
                        // S's host, deeper than O's and shallower than A's.
                        anchorContext?.let { context ->
                            SubcomposeLayout(compositionContext = context) { _ ->
                                val t = tick
                                val content =
                                    middleContentByTick.getOrPut(t) {
                                        val slot: @Composable () -> Unit = {
                                            producedByS[0] = t
                                            val middle = rememberCompositionContext()
                                            SideEffect { middleContext = middle }
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
                    }
                    // A's host, the deepest and busy.
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize()) {
                                tickingFixedSubHost(tick) {
                                    @Suppress("UNUSED_EXPRESSION")
                                    tick
                                    val anchor = rememberCompositionContext()
                                    SideEffect { anchorContext = anchor }
                                }
                            }
                        }
                    }
                }
            }
            // Each host appears once the slot enclosing it publishes its context.
            repeat(4) { scene.render(it * 16_000_000L) }
            assertEquals(0, innerSaw[0], "sanity: the innermost slot must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 3) * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the innermost slot composed before the slot enclosing it, " +
                        "which was held at measure time",
                )
                assertEquals(
                    frame,
                    innerSaw[0],
                    "frame $frame: the innermost slot did not compose its host's new content",
                )
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A slot whose host is measure-pending but will not be measured: its parent no longer
     * measures or places it, as with a hidden pane. A slot held for that measure would never be
     * re-run, and no layout would expire it, so it and everything nested in it would wait for
     * good. It must instead recompose standalone at once, as it does in stock Compose, and so
     * must a composition nested in it. The scene must then go idle.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot whose host will not be measured recomposes at once`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var shown by mutableStateOf(true)
        var tick by mutableStateOf(0)
        var slotValue by mutableStateOf(0)
        var leafValue by mutableStateOf(0)
        val slotSaw = intArrayOf(-1)
        val leafSaw = intArrayOf(-1)
        // Created outside composition, so only its own reads invalidate it.
        val slotContent: @Composable () -> Unit = {
            slotSaw[0] = slotValue
            val context = rememberCompositionContext()
            DisposableEffect(context) {
                val gateless = Composition(NoNodeApplier(), context)
                gateless.setContent { leafSaw[0] = leafValue }
                onDispose { gateless.dispose() }
            }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                Layout(content = { tickingFixedSubHost(tick, slotContent) }) {
                    measurables,
                    constraints ->
                    if (shown) {
                        val placeables = measurables.map { it.measure(constraints) }
                        layout(100, 100) { placeables.forEach { it.place(0, 0) } }
                    } else {
                        layout(100, 100) {}
                    }
                }
            }
            scene.render(0)
            assertEquals(0, slotSaw[0], "sanity: the slot must have run")
            assertEquals(0, leafSaw[0], "sanity: the gateless composition must have run")

            // The host stays composed, but is neither measured nor placed any more.
            Snapshot.withMutableSnapshot { shown = false }
            scene.render(16_000_000)

            // The tick reinstalls the host's policy, which marks it measure-pending, but the
            // layout does not schedule it.
            Snapshot.withMutableSnapshot {
                tick = 1
                slotValue = 1
                leafValue = 1
            }
            scene.render(32_000_000)
            assertEquals(
                1,
                slotSaw[0],
                "the slot of a host that will not be measured must recompose at once",
            )
            assertEquals(
                1,
                leafSaw[0],
                "a composition nested in the slot of a host that will not be measured must deliver",
            )

            repeat(3) { scene.render((3 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the slot has recomposed",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A layout can complete between the recompose that holds a slot back and the scene's layout,
     * when a remeasure of a single node is forced, as a scroll animation does. That layout is not
     * a full pass, so it does not expire the busy slot; an owner that cannot tell the two apart
     * expires it early. Either way the composition waiting for it must be released when the slot
     * composes in the scene's layout, in the same frame, and never compose before it; an early
     * expiry may only cost a recompose with nothing to do on the next frame.
     *
     * The root composition's `SideEffect` forces a remeasure of an unrelated node on every busy
     * frame. It runs in the root's apply, after the recompose that hands the busy slot and its
     * waiter to the layout pass, and before the scene's layout. A `SideEffect` of a composition
     * under a host runs inside its host's layout pass, where a forced remeasure is not allowed.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a waiter of a busy slot delivers in the same frame when a single node is remeasured first`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        val producedByEnclosing = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val forcedRemeasures = intArrayOf(0)
        // The unrelated node whose remeasure is forced.
        val unrelatedNode = arrayOfNulls<Remeasurement>(1)
        val remeasurementModifier =
            object : RemeasurementModifier {
                override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                    unrelatedNode[0] = remeasurement
                }
            }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                Box(Modifier.size(10.dp).then(remeasurementModifier))
                val t = tick
                SideEffect {
                    if (t > 0) {
                        forcedRemeasures[0]++
                        unrelatedNode[0]!!.forceRemeasure()
                    }
                }
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
                    frame,
                    forcedRemeasures[0],
                    "sanity: frame $frame must force a remeasure after the holds",
                )
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the gateless composition composed before its enclosing slot",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the gateless composition did not deliver in the frame a " +
                        "single node was remeasured",
                )
            }

            repeat(3) { scene.render((12 + it) * 16_000_000L) }
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
     * A SubcomposeLayout inside a [Popup] that is anchored in a busy host's slot. The popup has
     * its own owner, which the scene lays out after the main one, and its own composition, which
     * has no host. The popup's slot must still wait for the anchor's slot, across the owners, and
     * deliver in the same frame.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot in a popup anchored in a busy slot composes after it on every busy frame`() {
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
                    Popup {
                        fixedSubHost {
                            val fresh = tick
                            if (producedByEnclosing[0] != fresh) tornReads[0]++
                            nestedSaw[0] = fresh
                        }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(0, nestedSaw[0], "sanity: the popup's slot must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 1) * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the popup's slot composed before the slot it is anchored in",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the popup's slot did not deliver while the anchor's host was " +
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
     * A full-layout listener that precomposes a slot runs inside the layout delegate's call of
     * those listeners, where the hosts' expiries run too. If the slot is held there, behind an
     * enclosing slot that is still pending, its host registers for expiry from inside that call.
     * The registration must still be called, and the host's expiry must keep working: a slot it
     * is handed on a later frame and does not re-run must expire, so the composition waiting for
     * that slot delivers in that frame.
     *
     * The enclosing slot is a slot its host uses, whose host is not remeasured, and which a
     * composition without a host nested in it waits for: the root composition reads what that
     * composition reads, so it recomposes in the same frame, and the composition's captures can be
     * stale. So the enclosing slot is still pending when the listeners run, and expires there. The listener is registered before the frame's recompose,
     * so it runs before the expiry of the enclosing slot's host. A listener of the other kind, as
     * a legacy `OnPlacedModifier` registers, runs after every expiry, so the slot would not be
     * held there.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot held from inside a layout-completed listener leaves its host's expiry working`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var anchorValue by mutableStateOf(0)
        var leafValue by mutableStateOf(0)
        var anchorContext by mutableStateOf<CompositionContext?>(null)
        val anchorWaiterSaw = intArrayOf(-1)
        val leafSaw = intArrayOf(-1)
        val heldContentComposed = intArrayOf(0)
        val owner = arrayOfNulls<Owner>(1)
        val hostState = SubcomposeLayoutState()
        // Created outside composition, so a recompose of the root does not update them.
        val anchorContent: @Composable () -> Unit = {
            val context = rememberCompositionContext()
            SideEffect { anchorContext = context }
            DisposableEffect(context) {
                val gateless = Composition(NoNodeApplier(), context)
                gateless.setContent { anchorWaiterSaw[0] = anchorValue }
                onDispose { gateless.dispose() }
            }
        }
        val slotContent: @Composable () -> Unit = {
            val context = rememberCompositionContext()
            DisposableEffect(context) {
                val gateless = Composition(NoNodeApplier(), context)
                gateless.setContent { leafSaw[0] = leafValue }
                onDispose { gateless.dispose() }
            }
        }
        val firstContent: @Composable () -> Unit = {}
        val secondContent: @Composable () -> Unit = { heldContentComposed[0]++ }
        val rootSaw = intArrayOf(-1, -1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                // The root reads both values, so a change recomposes it with a change to apply,
                // and the compositions nested in the hosts below wait for their slots.
                val observedAnchor = anchorValue
                val observedLeaf = leafValue
                SideEffect {
                    rootSaw[0] = observedAnchor
                    rootSaw[1] = observedLeaf
                }
                Box(Modifier.onPlaced { owner[0] = (it as NodeCoordinator).layoutNode.owner })
                // Neither policy captures anything that changes, so neither host is remeasured.
                SubcomposeLayout { _ ->
                    subcompose("anchor", anchorContent).forEach {
                        it.measure(Constraints.fixed(10, 10))
                    }
                    layout(10, 10) {}
                }
                anchorContext?.let { context ->
                    SubcomposeLayout(context, state = hostState) { _ ->
                        subcompose("waited", slotContent).forEach {
                            it.measure(Constraints.fixed(10, 10))
                        }
                        layout(10, 10) {}
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            hostState.precompose("held", firstContent)
            assertEquals(0, anchorWaiterSaw[0], "sanity: the anchor's waiter must have run")
            assertEquals(0, leafSaw[0], "sanity: the gateless composition must have run")

            val listenerRan = booleanArrayOf(false)
            owner[0]!!.registerOnFullLayoutCompletedListener(
                object : Owner.OnLayoutCompletedListener {
                    override fun onLayoutComplete() {
                        listenerRan[0] = true
                        hostState.precompose("held", secondContent)
                    }
                }
            )
            Snapshot.withMutableSnapshot { anchorValue = 1 }
            scene.render(32_000_000)
            assertTrue(listenerRan[0], "sanity: the listener must have run")
            assertEquals(
                0,
                heldContentComposed[0],
                "sanity: the listener's precomposition is held behind the pending slot",
            )
            assertEquals(1, anchorWaiterSaw[0], "sanity: the anchor's expiry releases its waiter")
            scene.render(48_000_000)

            Snapshot.withMutableSnapshot { leafValue = 1 }
            scene.render(64_000_000)
            assertEquals(
                1,
                leafSaw[0],
                "the waiter of a slot its host did not re-run must deliver in the frame, after " +
                    "a slot of the same host was held from inside a listener",
            )

            repeat(3) { scene.render((5 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the waiter has delivered",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A slot held behind a busy slot, whose own host will not be measured: its parent no longer
     * measures or places it, as with a hidden pane. The recompose block consumed the slot's change
     * when it held it, and the refresh that the busy slot's report requests does not schedule the
     * slot's host. The host is outside any layout pass, so the release at the end of the busy
     * slot's layout pass must recompose the slot in place: it delivers in the same frame, after the
     * busy slot, not on a later frame and not stale until its host is shown again, and the scene
     * then goes idle.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a waiter whose host will not be measured delivers after the slot it waits for reports`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var shown by mutableStateOf(true)
        var tick by mutableStateOf(0)
        var slotValue by mutableStateOf(0)
        val producedByEnclosing = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val slotSaw = intArrayOf(-1)
        // Created outside composition, so only its own reads invalidate it.
        val slotContent: @Composable () -> Unit = {
            if (producedByEnclosing[0] != tick) tornReads[0]++
            slotSaw[0] = slotValue
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                tickingFixedSubHost(tick) {
                    producedByEnclosing[0] = tick
                    Layout(content = { fixedSubHost(slotContent) }) { measurables, constraints ->
                        if (shown) {
                            val placeables = measurables.map { it.measure(constraints) }
                            layout(50, 50) { placeables.forEach { it.place(0, 0) } }
                        } else {
                            layout(50, 50) {}
                        }
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(0, slotSaw[0], "sanity: the slot must have run")

            // The slot's host stays composed, but is neither measured nor placed any more.
            Snapshot.withMutableSnapshot { shown = false }
            scene.render(32_000_000)
            scene.render(48_000_000)

            Snapshot.withMutableSnapshot {
                tick = 1
                slotValue = 1
            }
            scene.render(64_000_000)
            assertEquals(0, tornReads[0], "the slot composed before the busy slot enclosing it")
            assertEquals(
                1,
                slotSaw[0],
                "a slot whose host will not be measured must deliver in the frame the slot it " +
                    "waits for reports",
            )

            repeat(3) { scene.render((5 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the slot has delivered",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A slot held behind a busy slot, whose own host is measuring when the busy slot reports:
     * the busy slot's host sits inside the other host's main slot, and the other host
     * subcomposes the waiting slot first, as an overlay host that subcomposes its overlays before
     * its content would. The refresh that the report requests is swallowed, because that host is
     * measuring and has passed the waiting slot already. The slot must not compose inside that
     * measure. Its release waits for the end of the layout pass, which refreshes its host, and
     * the draw's trailing layout pass re-runs it: it delivers in the same frame, and the scene
     * then goes idle.
     *
     * The waiting slot's composition is nested in the busy slot, and the busy slot's host sits in
     * the other host's main slot. The other host's composition context changes to the busy
     * slot's after its main slot exists, so that main slot keeps the root's and there is no
     * cycle.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a waiter whose host is measuring when the slot it waits for reports delivers in the same frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var overlayValue by mutableStateOf(0)
        var overlayOn by mutableStateOf(false)
        var anchorContext by mutableStateOf<CompositionContext?>(null)
        val overlaySaw = intArrayOf(-1)
        // Created outside composition, so only their own reads invalidate them.
        val overlayContent: @Composable () -> Unit = { overlaySaw[0] = overlayValue }
        val mainContent: @Composable () -> Unit = {
            tickingFixedSubHost(tick) {
                @Suppress("UNUSED_EXPRESSION")
                tick
                val anchor = rememberCompositionContext()
                SideEffect { anchorContext = anchor }
            }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                val rootContext = rememberCompositionContext()
                SubcomposeLayout(anchorContext ?: rootContext) { constraints ->
                    // The policy reads tick, so a change marks this host measure-pending too,
                    // and its measure measures the busy slot's host.
                    val placeables =
                        if (overlayOn) {
                            subcompose("overlay", overlayContent).map {
                                it.measure(Constraints.fixed(10, 10))
                            }
                        } else {
                            emptyList()
                        } + subcompose("main", mainContent).map { it.measure(constraints) }
                    layout(60 + tick, 60) { placeables.forEach { it.place(0, 0) } }
                }
            }
            repeat(2) { scene.render(it * 16_000_000L) }
            Snapshot.withMutableSnapshot { overlayOn = true }
            repeat(2) { scene.render((2 + it) * 16_000_000L) }
            assertEquals(0, overlaySaw[0], "sanity: the waiting slot must have run")

            Snapshot.withMutableSnapshot {
                tick = 1
                overlayValue = 1
            }
            scene.render(64_000_000)
            assertEquals(
                1,
                overlaySaw[0],
                "a slot whose host was measuring when the slot it waits for reported must " +
                    "deliver in the same frame",
            )

            repeat(3) { scene.render((5 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the waiting slot has delivered",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The shallow-slot shape, with a remeasure of a single node forced between the recompose that
     * holds both slots and the scene's layout. That remeasure is not a full layout pass, so it
     * must expire neither slot. If it expired them, the shallow host, which the measure pass
     * reaches first, would find the deep slot no longer pending and compose its own slot ahead of
     * it, against the value the deep slot produced on the frame before.
     *
     * The root composition's `SideEffect` forces a remeasure of an unrelated node on every busy
     * frame, in the root's apply, after the recompose that hands both slots to the layout pass and
     * before the scene's layout.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot hosted shallower than its enclosing slot keeps its order when a single node is remeasured first`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var enclosingContext by mutableStateOf<CompositionContext?>(null)
        val producedByEnclosing = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val forcedRemeasures = intArrayOf(0)
        val unrelatedNode = arrayOfNulls<Remeasurement>(1)
        val remeasurementModifier =
            object : RemeasurementModifier {
                override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                    unrelatedNode[0] = remeasurement
                }
            }
        val nestedContent: @Composable () -> Unit = {
            val fresh = tick
            if (producedByEnclosing[0] != fresh) tornReads[0]++
            nestedSaw[0] = fresh
        }
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(10.dp).then(remeasurementModifier))
                    val t = tick
                    SideEffect {
                        if (t > 0) {
                            forcedRemeasures[0]++
                            unrelatedNode[0]!!.forceRemeasure()
                        }
                    }
                    // The shallow host. Its policy reads tick, so it is measure-pending on every
                    // busy frame too.
                    enclosingContext?.let { context ->
                        SubcomposeLayout(compositionContext = context) { _ ->
                            val placeables =
                                subcompose(Unit, nestedContent).map {
                                    it.measure(Constraints.fixed(10, 10))
                                }
                            layout(10 + tick, 10) { placeables.forEach { it.place(0, 0) } }
                        }
                    }
                    // The deep, busy host.
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
            repeat(3) { scene.render(it * 16_000_000L) }
            assertEquals(0, nestedSaw[0], "sanity: the shallow slot must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 2) * 16_000_000L)
                assertEquals(
                    frame,
                    forcedRemeasures[0],
                    "sanity: frame $frame must force a remeasure after the holds",
                )
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the shallow slot composed before the deep slot enclosing it, " +
                        "after a remeasure of a single node",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the shallow slot did not deliver in the frame",
                )
            }

            repeat(3) { scene.render((13 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once both hosts settle",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A remeasure of a single node can measure a host after the recompose that handed it a slot
     * and leave nothing else to measure. Here that measure stops using the slot and keeps it for
     * reuse, so the host never re-runs it. The slot must still expire at the scene's layout,
     * although that layout has no node to measure, instead of leaving what waits for it to an
     * unrelated layout. A slot its host stopped using hands its waiters back to the recomposer.
     * What the waiter then does is the deactivation skip's business, not this test's: it treats
     * a composition created in deactivated content as its owner's to tear down.
     */
    @OptIn(ExperimentalComposeUiApi::class, InternalComposeApi::class)
    @Test
    fun `a slot left pending by a remeasure of its host alone still expires at the scene's layout`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var useSlot by mutableStateOf(true)
        var leafValue by mutableStateOf(0)
        val leafSaw = intArrayOf(-1)
        val forcedRemeasures = intArrayOf(0)
        val slotContext = arrayOfNulls<CompositionContext>(1)
        val state = SubcomposeLayoutState(SubcomposeSlotReusePolicy(1))
        val hostRemeasurement = arrayOfNulls<Remeasurement>(1)
        val remeasurementModifier =
            object : RemeasurementModifier {
                override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                    hostRemeasurement[0] = remeasurement
                }
            }
        // Created outside composition, so a recompose of the root does not update it.
        val slotContent: @Composable () -> Unit = {
            val context = rememberCompositionContext()
            SideEffect { slotContext[0] = context }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        // Created outside the slot's content, so keeping the slot for reuse does not dispose it.
        var waiter: Composition? = null
        try {
            scene.setContent {
                val use = useSlot
                SideEffect {
                    if (!use) {
                        forcedRemeasures[0]++
                        hostRemeasurement[0]!!.forceRemeasure()
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    // The policy captures the flag, so a change marks this host measure-pending.
                    // Its size never changes, so its remeasure leaves its parent nothing to do.
                    SubcomposeLayout(state, Modifier.then(remeasurementModifier)) { _ ->
                        if (use) {
                            subcompose("a", slotContent).forEach {
                                it.measure(Constraints.fixed(10, 10))
                            }
                        }
                        layout(10, 10) {}
                    }
                }
            }
            scene.render(0)
            waiter =
                Composition(NoNodeApplier(), slotContext[0]!!).apply {
                    setContent { leafSaw[0] = leafValue }
                }
            val slot = hostingOf(waiter!!).enclosingHosted!!.host as ParentDrivenSlot
            assertEquals(0, leafSaw[0], "sanity: the waiter must have run")

            Snapshot.withMutableSnapshot {
                useSlot = false
                leafValue = 1
            }
            scene.render(16_000_000)
            assertEquals(1, forcedRemeasures[0], "sanity: the host's remeasure must be forced")
            assertFalse(
                slot.isReportDue,
                "a slot left pending by a remeasure of its host alone must expire at the " +
                    "scene's layout and release its waiter",
            )

            repeat(3) { scene.render((2 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the waiter has delivered",
            )
        } finally {
            waiter?.dispose()
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A busy slot's report inside its host's measure releases a composition that has no host. It
     * must not compose inside that measure, where its reads would run while the host is measuring
     * and an exception it throws would leave the measure half done. It composes once the host's
     * layout pass has ended, after the busy slot, and still in the same frame.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a waiter without a host composes after its slot's host has measured, in the same frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        val measuring = booleanArrayOf(false)
        val producedByEnclosing = intArrayOf(-1)
        val composedInsideMeasure = intArrayOf(0)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                flaggedTickingFixedSubHost(tick, measuring) {
                    producedByEnclosing[0] = tick
                    val context = rememberCompositionContext()
                    DisposableEffect(context) {
                        val gateless = Composition(NoNodeApplier(), context)
                        gateless.setContent {
                            val fresh = tick
                            // Its first composition runs in the slot's apply, inside the measure.
                            if (fresh > 0 && measuring[0]) composedInsideMeasure[0]++
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
                    composedInsideMeasure[0],
                    "frame $frame: the gateless composition composed inside its slot's host's " +
                        "measure",
                )
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the gateless composition composed before its enclosing slot",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the gateless composition did not deliver in the frame",
                )
            }

            repeat(3) { scene.render((12 + it) * 16_000_000L) }
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
     * A busy slot releases, from inside its host's measure, a composition without a host and then
     * a slot nested in it, whose host is idle and would measure later in this pass if refreshed.
     * The composition without a host composes only once the pass has ended, so the nested slot
     * must wait for that too: refreshed at once, it would compose ahead of the composition it is
     * nested in, against the value that one produced on the frame before. It is refreshed at the
     * end of the pass instead, and the draw's trailing layout pass composes it in the same frame.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot released after a waiter without a host does not compose ahead of it`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var gatelessContext by mutableStateOf<CompositionContext?>(null)
        val producedByGateless = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        // Created outside composition, so only its own reads invalidate it.
        val nestedContent: @Composable () -> Unit = {
            val fresh = tick
            if (producedByGateless[0] != fresh) tornReads[0]++
            nestedSaw[0] = fresh
        }
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                Box(Modifier.fillMaxSize()) {
                    // The nested slot's host. Nothing dirties its measure, and its slot is nested
                    // in the composition without a host.
                    gatelessContext?.let { context ->
                        SubcomposeLayout(compositionContext = context) { _ ->
                            val placeables =
                                subcompose(Unit, nestedContent).map {
                                    it.measure(Constraints.fixed(10, 10))
                                }
                            layout(10, 10) { placeables.forEach { it.place(0, 0) } }
                        }
                    }
                    tickingFixedSubHost(tick) {
                        @Suppress("UNUSED_EXPRESSION")
                        tick
                        val context = rememberCompositionContext()
                        DisposableEffect(context) {
                            val gateless = Composition(NoNodeApplier(), context)
                            gateless.setContent {
                                producedByGateless[0] = tick
                                val inner = rememberCompositionContext()
                                SideEffect { gatelessContext = inner }
                            }
                            onDispose { gateless.dispose() }
                        }
                    }
                }
            }
            // The nested slot's host appears once the gateless composition publishes its context.
            repeat(3) { scene.render(it * 16_000_000L) }
            assertEquals(0, nestedSaw[0], "sanity: the nested slot must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 2) * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the nested slot composed before the composition without a " +
                        "host it is nested in",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the nested slot did not deliver in the frame",
                )
            }

            repeat(3) { scene.render((13 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the busy host settles",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The shape of the test above, with the nested slot's host measured again later in the same
     * pass, because its parent's measure gives it new constraints. The slot's release waits for
     * the end of the pass, after the composition without a host it is nested in. So that measure
     * must keep the slot's old content rather than compose its change ahead of that composition,
     * and the release at the end of the pass then delivers it in the same frame.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot whose release waits for the end of the pass is not composed by its host before then`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var gatelessContext by mutableStateOf<CompositionContext?>(null)
        val producedByGateless = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val hostMeasures = intArrayOf(0)
        // Created outside composition, so only its own reads invalidate it.
        val nestedContent: @Composable () -> Unit = {
            val fresh = tick
            if (producedByGateless[0] != fresh) tornReads[0]++
            nestedSaw[0] = fresh
        }
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                Box(Modifier.fillMaxSize()) {
                    // The busy host, shallower than the nested slot's host, so the measure pass
                    // reaches it first.
                    tickingFixedSubHost(tick) {
                        @Suppress("UNUSED_EXPRESSION")
                        tick
                        val context = rememberCompositionContext()
                        DisposableEffect(context) {
                            val gateless = Composition(NoNodeApplier(), context)
                            gateless.setContent {
                                producedByGateless[0] = tick
                                val inner = rememberCompositionContext()
                                SideEffect { gatelessContext = inner }
                            }
                            onDispose { gateless.dispose() }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            // Its measure reads tick, so it measures the nested slot's host with
                            // new constraints on every busy frame. The host itself is not
                            // measure-pending when the frame recomposes.
                            Layout(
                                content = {
                                    gatelessContext?.let { context ->
                                        SubcomposeLayout(compositionContext = context) {
                                            constraints ->
                                            hostMeasures[0]++
                                            val placeables =
                                                subcompose(Unit, nestedContent).map {
                                                    it.measure(Constraints.fixed(10, 10))
                                                }
                                            layout(constraints.maxWidth, 10) {
                                                placeables.forEach { it.place(0, 0) }
                                            }
                                        }
                                    }
                                }
                            ) { measurables, _ ->
                                val childConstraints = Constraints.fixed(20 + tick, 10)
                                val placeables = measurables.map { it.measure(childConstraints) }
                                layout(100, 100) { placeables.forEach { it.place(0, 0) } }
                            }
                        }
                    }
                }
            }
            // The nested slot's host appears once the gateless composition publishes its context.
            repeat(3) { scene.render(it * 16_000_000L) }
            assertEquals(0, nestedSaw[0], "sanity: the nested slot must have run")

            for (frame in 1..10) {
                val measuresBefore = hostMeasures[0]
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 2) * 16_000_000L)
                assertTrue(
                    hostMeasures[0] >= measuresBefore + 2,
                    "sanity: frame $frame must measure the nested slot's host in the pass and " +
                        "again after it",
                )
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the nested slot's host composed it before the composition " +
                        "without a host it is nested in",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the nested slot did not deliver in the frame",
                )
            }

            repeat(3) { scene.render((13 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the busy host settles",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A waiter released at the end of a layout pass can lay out again from inside that release,
     * as a composition whose `SideEffect` forces a remeasure does. A report in that nested layout
     * leaves more waiters for the end of its pass, and so runs the release again from inside
     * itself. Every waiter must still be released exactly once, those left first before those
     * added.
     *
     * Recording waiters stand in for the released compositions, so the test can count releases.
     * The first and the second force a remeasure of the host when released, as such a
     * `SideEffect` would, after making another of the host's slots due to release a waiter of its
     * own.
     */
    @OptIn(ExperimentalComposeUiApi::class, InternalComposeApi::class)
    @Test
    fun `a release that lays out again releases every waiter once`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val ids = listOf("a", "b", "c")
        val slotContexts = HashMap<String, CompositionContext>()
        val contents =
            ids.associateWith { id ->
                val content: @Composable () -> Unit = {
                    val context = rememberCompositionContext()
                    SideEffect { slotContexts[id] = context }
                }
                content
            }
        val hostRemeasurement = arrayOfNulls<Remeasurement>(1)
        val remeasurementModifier =
            object : RemeasurementModifier {
                override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                    hostRemeasurement[0] = remeasurement
                }
            }
        val slots = HashMap<String, ParentDrivenSlot>()
        val released = mutableListOf<String>()
        // Makes [id]'s slot due to release [waiter] when its host next re-runs it, and lays out.
        fun releaseFromNestedLayout(id: String, waiter: ParentDrivenHosting) {
            val slot = slots.getValue(id)
            slot.pending = true
            assertTrue(
                slot.onWaiterInvalidated(waiter, enclosingRecomposed = true),
                "sanity: the slot must take the waiter",
            )
            hostRemeasurement[0]!!.forceRemeasure()
        }
        val second = RecordingHosting(whenRecomposedNow = { released += "second" })
        val fourth = RecordingHosting(whenRecomposedNow = { released += "fourth" })
        val first =
            RecordingHosting(
                whenRecomposedNow = {
                    released += "first"
                    if (released.count { it == "first" } == 1) releaseFromNestedLayout("b", second)
                }
            )
        val third =
            RecordingHosting(
                whenRecomposedNow = {
                    released += "third"
                    if (released.count { it == "third" } == 1) releaseFromNestedLayout("c", fourth)
                }
            )
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                SubcomposeLayout(Modifier.then(remeasurementModifier)) { _ ->
                    ids.forEach { id ->
                        subcompose(id, contents.getValue(id)).forEach {
                            it.measure(Constraints.fixed(10, 10))
                        }
                    }
                    layout(10, 10) {}
                }
            }
            scene.render(0)
            ids.forEach { id ->
                val probe = Composition(NoNodeApplier(), slotContexts.getValue(id))
                slots[id] = hostingOf(probe).enclosingHosted!!.host as ParentDrivenSlot
                probe.dispose()
            }

            // Slot `a` is due to release two waiters without a host. Its report inside the host's
            // forced measure leaves them for the end of that pass, where the first one lays out
            // again and the report of `b` in that layout adds the second waiter, and so on.
            val slotA = slots.getValue("a")
            slotA.pending = true
            assertTrue(
                slotA.onWaiterInvalidated(first, enclosingRecomposed = true),
                "sanity: the slot must take the first waiter",
            )
            assertTrue(
                slotA.onWaiterInvalidated(third, enclosingRecomposed = true),
                "sanity: the slot must take the third waiter",
            )
            hostRemeasurement[0]!!.forceRemeasure()

            assertEquals(
                listOf("first", "third", "second", "fourth"),
                released,
                "every waiter must be released once, in the order it was left for the release",
            )
            assertEquals(
                listOf(1, 1, 1, 1),
                listOf(first, second, third, fourth).map { it.recomposeNowCalls },
            )
            ids.forEach { id ->
                assertFalse(slots.getValue(id).isReportDue, "slot $id must be left holding nothing")
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A composition without a host in the main owner, created with a busy slot's context, whose
     * content has a `SubcomposeLayout`. That host's own measure is dirty on every busy frame, so
     * the recompose block leaves its slot to its own host, and the slot is in no waiter list. The
     * busy slot's report releases the composition without a host at the end of the layout pass. The
     * nested slot's host measures later in that pass, and its slot must wait for that release
     * rather than compose ahead of it, against the value the composition without a host produced
     * on the frame before. It then delivers in the same frame.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot nested through a composition without a host waits for it when its own host holds it`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var container by mutableStateOf<LayoutNode?>(null)
        val producedByGateless = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        // Created outside composition, so only its own reads invalidate it.
        val nestedContent: @Composable () -> Unit = {
            val fresh = tick
            if (producedByGateless[0] != fresh) tornReads[0]++
            nestedSaw[0] = fresh
        }
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                Box(Modifier.fillMaxSize()) {
                    // The busy host, shallower than the nested slot's host, so the measure pass
                    // reaches it first.
                    tickingFixedSubHost(tick) {
                        @Suppress("UNUSED_EXPRESSION")
                        tick
                        val context = rememberCompositionContext()
                        container?.let { node ->
                            DisposableEffect(node, context) {
                                // Emits into the container below, in this owner.
                                val gateless = Composition(DefaultUiApplier(node), context)
                                gateless.setContent {
                                    producedByGateless[0] = tick
                                    // Its policy reads tick, so its own measure is dirty on
                                    // every busy frame.
                                    SubcomposeLayout { _ ->
                                        val placeables =
                                            subcompose(Unit, nestedContent).map {
                                                it.measure(Constraints.fixed(10, 10))
                                            }
                                        layout(10 + tick, 10) {
                                            placeables.forEach { it.place(0, 0) }
                                        }
                                    }
                                }
                                onDispose { gateless.dispose() }
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            // Measures and places whatever the gateless composition inserts.
                            Layout(
                                content = {},
                                modifier =
                                    Modifier.onPlaced {
                                        container = (it as NodeCoordinator).layoutNode
                                    },
                            ) { measurables, constraints ->
                                val placeables = measurables.map { it.measure(constraints) }
                                layout(100, 100) { placeables.forEach { it.place(0, 0) } }
                            }
                        }
                    }
                }
            }
            // The gateless composition appears once the container is placed.
            repeat(3) { scene.render(it * 16_000_000L) }
            assertEquals(0, nestedSaw[0], "sanity: the nested slot must have run")

            for (frame in 1..10) {
                Snapshot.withMutableSnapshot { tick = frame }
                scene.render((frame + 2) * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the nested slot composed before the composition without a " +
                        "host it is nested in",
                )
                assertEquals(
                    frame,
                    nestedSaw[0],
                    "frame $frame: the nested slot did not deliver in the frame",
                )
            }

            repeat(3) { scene.render((13 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the busy host settles",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A release that fails part way, as `recomposeNow` reports when a released waiter throws in
     * the resilient mode: the release stops, and recovery recomposes the rest. The slot that
     * released must be left neither pending nor holding waiters, so that no later report
     * releases the rest into the failed state and nothing waits behind the slot for good.
     */
    @OptIn(InternalComposeApi::class)
    @Test
    fun `a release that fails part way stops and leaves the slot holding nothing`() {
        val slots = ParentDrivenSlots(LayoutNode())
        val slot = ParentDrivenSlot(slots, RecordingHosting()) { true }
        val failing = RecordingHosting(recomposes = false)
        val rest = RecordingHosting()
        // Pending, as a slot handed to its host is, with two waiters behind it. The host is not
        // attached, so the waiters are added directly rather than handed over.
        slots.markPending(slot)
        slot.addWaiter(failing)
        slot.addWaiter(rest)

        slot.reportCurrent()
        assertEquals(1, failing.recomposeNowCalls, "the report must release the first waiter")
        assertEquals(0, rest.recomposeNowCalls, "the release must stop at the failure")
        assertFalse(slot.isReportDue, "the slot must be neither pending nor keep waiters")

        slot.reportCurrent()
        assertEquals(0, rest.recomposeNowCalls, "a later report must not release the rest")
        assertEquals(
            0,
            failing.recomposeLaterCalls + rest.recomposeLaterCalls,
            "nor queue a fallback for them; recovery recomposes everything",
        )
    }

    /**
     * The same failure in a scene, from a released waiter that throws. The scene's recomposer
     * runs in the resilient mode, and its recovery replaces every composition, the waiter that
     * threw included. The scene must keep working after it: the slot and its waiters compose the
     * current values, the next busy frame holds both waiters and the slot's report releases
     * them, and the scene goes idle. A recovery that fails on what the failing release left
     * behind stops the scene's recomposer for good, and no later change is delivered.
     *
     * The scene's coroutine context dispatches, as a window's does, so the recovery runs at the
     * start of the next frame and not inside the failing release. The release comes from the end
     * of the layout pass in which the busy slot reported.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a scene recovers from a waiter that throws when it is released`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var leafValue by mutableStateOf(0)
        val saw = IntArray(2) { -1 }
        val failures = intArrayOf(0)
        val rootComposes = intArrayOf(0)
        val state = SubcomposeLayoutState()
        // Created outside composition, so a recompose of the root does not update it.
        val slotContent: @Composable () -> Unit = {
            @Suppress("UNUSED_EXPRESSION")
            tick
            val context = rememberCompositionContext()
            DisposableEffect(context) {
                val created =
                    List(2) { index ->
                        Composition(NoNodeApplier(), context).also { gateless ->
                            gateless.setContent {
                                val fresh = leafValue
                                // The first waiter released at value 1 throws, once.
                                if (fresh == 1 && failures[0] == 0) {
                                    failures[0]++
                                    throw IllegalStateException("a failing waiter")
                                }
                                saw[index] = fresh
                            }
                        }
                    }
                onDispose { created.forEach { it.dispose() } }
            }
        }
        // Runs nothing by itself: the scene's frame dispatcher runs what is dispatched to it at
        // the start of each frame.
        val scene =
            ImageComposeScene(
                width = 100,
                height = 100,
                coroutineContext = StandardTestDispatcher(),
            )
        try {
            scene.setContent {
                SideEffect { rootComposes[0]++ }
                // The policy captures tick, so a change marks this host measure-pending.
                SubcomposeLayout(state) { _ ->
                    subcompose("a", slotContent).forEach { it.measure(Constraints.fixed(10, 10)) }
                    layout(10 + tick, 10) {}
                }
            }
            scene.render(0)
            assertEquals(listOf(0, 0), saw.toList(), "sanity: both waiters must have run")

            // The frame's recompose runs the root; the waiters wait for the slot, whose report
            // releases them at the end of the layout pass, where the first one throws.
            Snapshot.withMutableSnapshot {
                tick = 1
                leafValue = 1
            }
            val rootComposesBeforeFailure = rootComposes[0]
            scene.render(16_000_000)
            assertEquals(1, failures[0], "sanity: a waiter must have thrown when released")
            assertEquals(
                rootComposesBeforeFailure,
                rootComposes[0],
                "sanity: the recovery must not run inside the failing release",
            )

            repeat(2) { scene.render((2 + it) * 16_000_000L) }
            assertTrue(
                rootComposes[0] > rootComposesBeforeFailure,
                "sanity: the next frame must run the recovery",
            )
            assertEquals(
                listOf(1, 1),
                saw.toList(),
                "after recovery both waiters must compose the current value",
            )

            Snapshot.withMutableSnapshot {
                tick = 2
                leafValue = 2
            }
            scene.render(64_000_000)
            assertEquals(
                listOf(2, 2),
                saw.toList(),
                "after recovery the slot's report must release both waiters in the frame",
            )

            repeat(3) { scene.render((5 + it) * 16_000_000L) }
            assertFalse(
                scene.hasInvalidations(),
                "the scene must go idle once the waiters have delivered",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A slot deactivated for reuse while it is pending: the recompose hands it to its host, and
     * the measure that follows stops using it and keeps it for reuse. The slot expires at that
     * layout, which composes nothing and hands the composition waiting for it back to the
     * recomposer. When the host reuses the slot for a new slot id, it composes for that id, and
     * must be left not pending and holding nothing, so nothing is held behind a slot that has
     * moved on.
     */
    @OptIn(ExperimentalComposeUiApi::class, InternalComposeApi::class)
    @Test
    fun `a slot reused for a new id after it expired while held reports for the new id`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var tick by mutableStateOf(0)
        var leafValue by mutableStateOf(0)
        var slotIds by mutableStateOf(listOf("a"))
        val leafSaw = intArrayOf(-1)
        val slotContext = arrayOfNulls<CompositionContext>(1)
        val slotComposes = intArrayOf(0)
        val state = SubcomposeLayoutState(SubcomposeSlotReusePolicy(1))
        // Created outside composition, so a recompose of the root does not update it.
        val slotContent: @Composable () -> Unit = {
            @Suppress("UNUSED_EXPRESSION")
            tick
            val context = rememberCompositionContext()
            SideEffect {
                slotComposes[0]++
                slotContext[0] = context
            }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        // Created outside the slot's content, so the deactivation does not dispose it.
        var waiter: Composition? = null
        try {
            scene.setContent {
                val ids = slotIds
                // The policy captures tick, so a change marks this host measure-pending.
                SubcomposeLayout(state) { _ ->
                    ids.forEach { id ->
                        subcompose(id, slotContent).forEach { it.measure(Constraints.fixed(10, 10)) }
                    }
                    layout(10 + tick, 10) {}
                }
            }
            scene.render(0)
            waiter =
                Composition(NoNodeApplier(), slotContext[0]!!).apply {
                    setContent { leafSaw[0] = leafValue }
                }
            val slot = hostingOf(waiter).enclosingHosted!!.host as ParentDrivenSlot
            assertEquals(0, leafSaw[0], "sanity: the waiter must have run")

            Snapshot.withMutableSnapshot {
                tick = 1
                leafValue = 1
                slotIds = emptyList()
            }
            scene.render(16_000_000)
            assertFalse(slot.pending, "the slot must expire at the layout that stopped using it")
            assertFalse(slot.isReportDue, "the expiry must release the slot's waiters")
            repeat(2) { scene.render((2 + it) * 16_000_000L) }

            val composesBeforeReuse = slotComposes[0]
            Snapshot.withMutableSnapshot { slotIds = listOf("b") }
            scene.render(64_000_000)
            val reusedContext = slotContext[0]!!
            val probe = Composition(NoNodeApplier(), reusedContext)
            try {
                assertTrue(
                    slotComposes[0] > composesBeforeReuse &&
                        hostingOf(probe).enclosingHosted!!.host === slot,
                    "sanity: the host must have reused the slot's composition for the new id",
                )
            } finally {
                probe.dispose()
            }
            assertFalse(
                slot.isReportDue,
                "the reused slot must be left not pending and holding nothing",
            )

            repeat(3) { scene.render((5 + it) * 16_000_000L) }
            assertFalse(scene.hasInvalidations(), "the scene must go idle after the reuse")
        } finally {
            waiter?.dispose()
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A node at the top of the tree whose measure reads [tick], so the frame's layout measures it
     * before any host nested one level deeper, and sets [layoutStarted] when it does. A
     * `SideEffect` can then tell whether its composition applied before the frame's layout or in
     * it. Its size never changes, so measuring it costs its parent nothing.
     */
    @Composable
    private fun LayoutProbe(tick: () -> Int, layoutStarted: BooleanArray) {
        Box(
            Modifier.layout { measurable, constraints ->
                    tick()
                    layoutStarted[0] = true
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .size(1.dp)
        )
    }

    /**
     * A slot whose own state changes, with nothing above it changed or due to lay out, has
     * nothing stale to wait for: its host would re-run it with the same content. So it
     * recomposes in the frame's recompose block, before the layout, as stock Compose recomposes
     * it, and its host does not measure. A lazy item's own animation produces this frame.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot whose own state changes alone recomposes before the layout and its host does not measure`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var value by mutableStateOf(0)
        var probe by mutableStateOf(0)
        val layoutStarted = booleanArrayOf(false)
        val appliedInLayout = mutableListOf<Boolean>()
        val saw = intArrayOf(-1)
        val hostMeasures = intArrayOf(0)
        // Created outside composition, so nothing gives the host new content.
        val content: @Composable () -> Unit = {
            saw[0] = value
            SideEffect { appliedInLayout += layoutStarted[0] }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                LayoutProbe({ probe }, layoutStarted)
                Box {
                    SubcomposeLayout { constraints ->
                        hostMeasures[0]++
                        val placeables = subcompose(Unit, content).map { it.measure(constraints) }
                        layout(10, 10) { placeables.forEach { it.place(0, 0) } }
                    }
                }
            }
            scene.render(0)
            assertEquals(0, saw[0], "sanity: the slot must have composed")

            for (frame in 1..3) {
                Snapshot.withMutableSnapshot {
                    value = frame
                    probe = frame
                }
                layoutStarted[0] = false
                hostMeasures[0] = 0
                appliedInLayout.clear()
                scene.render(frame * 16_000_000L)
                assertEquals(frame, saw[0], "frame $frame: the slot must deliver in the frame")
                assertEquals(
                    listOf(false),
                    appliedInLayout,
                    "frame $frame: the slot must recompose once, before the layout",
                )
                assertEquals(0, hostMeasures[0], "frame $frame: its host must not measure")
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A slot whose enclosing composition recomposed earlier in the same recompose block can be
     * given new content by that composition's apply, which runs after the block. So it must not
     * recompose in the block: it is handed to the layout pass, and composes there with the
     * content its enclosing composition supplies, never pairing the value that content captured
     * with a fresher read.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot whose enclosing composition changed in the frame composes in the layout, after it`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var value by mutableStateOf(0)
        var probe by mutableStateOf(0)
        val layoutStarted = booleanArrayOf(false)
        val appliedInLayout = mutableListOf<Boolean>()
        val saw = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                LayoutProbe({ probe }, layoutStarted)
                val captured = value
                Box {
                    fixedSubHost {
                        val fresh = value
                        if (captured != fresh) tornReads[0]++
                        saw[0] = fresh
                        SideEffect { appliedInLayout += layoutStarted[0] }
                    }
                }
            }
            scene.render(0)
            assertEquals(0, saw[0], "sanity: the slot must have composed")

            for (frame in 1..3) {
                Snapshot.withMutableSnapshot {
                    value = frame
                    probe = frame
                }
                layoutStarted[0] = false
                appliedInLayout.clear()
                scene.render(frame * 16_000_000L)
                assertEquals(0, tornReads[0], "frame $frame: the slot composed ahead of its content")
                assertEquals(frame, saw[0], "frame $frame: the slot must deliver in the frame")
                assertEquals(
                    listOf(true),
                    appliedInLayout,
                    "frame $frame: the slot must compose once, in the layout",
                )
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A slot handed to the layout pass because its enclosing composition changed does not cost
     * its host a measure when that composition's apply gives the host nothing new. The slot's
     * content did not change, so once the layout has shown that nothing re-supplies it, it
     * composes on its own at the end of the pass, in the same frame.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot handed over because its enclosing composition changed does not make its host measure`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var other by mutableStateOf(0)
        var value by mutableStateOf(0)
        var probe by mutableStateOf(0)
        val layoutStarted = booleanArrayOf(false)
        val appliedInLayout = mutableListOf<Boolean>()
        val saw = intArrayOf(-1)
        val otherSaw = intArrayOf(-1)
        val hostMeasures = intArrayOf(0)
        // Created outside composition, so a recompose of the root does not give the host new
        // content.
        val content: @Composable () -> Unit = {
            saw[0] = value
            SideEffect { appliedInLayout += layoutStarted[0] }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                LayoutProbe({ probe }, layoutStarted)
                otherSaw[0] = other
                Box {
                    SubcomposeLayout { constraints ->
                        hostMeasures[0]++
                        val placeables = subcompose(Unit, content).map { it.measure(constraints) }
                        layout(10, 10) { placeables.forEach { it.place(0, 0) } }
                    }
                }
            }
            scene.render(0)
            assertEquals(0, saw[0], "sanity: the slot must have composed")

            for (frame in 1..3) {
                Snapshot.withMutableSnapshot {
                    other = frame
                    value = frame
                    probe = frame
                }
                layoutStarted[0] = false
                hostMeasures[0] = 0
                appliedInLayout.clear()
                scene.render(frame * 16_000_000L)
                assertEquals(frame, otherSaw[0], "sanity: frame $frame: the root must recompose")
                assertEquals(frame, saw[0], "frame $frame: the slot must deliver in the frame")
                assertEquals(
                    listOf(true),
                    appliedInLayout,
                    "frame $frame: the slot must compose once, in the layout",
                )
                assertEquals(0, hostMeasures[0], "frame $frame: its host must not measure")
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    // Like [measureReadingHost], but the value its measure passes into the slot's content is
    // [captured], a parameter: the host's measure policy captures it, so the new value reaches the
    // slot only once the enclosing composition's apply has installed the new policy and the host
    // has measured. Given a [content] that stays the same instance, as a lazy list's item content
    // or one created outside composition does, nothing but that measure tells the slot that its
    // value changed. A content lambda written in the enclosing composition would tell it: it is a
    // new block on every recompose, and updating it invalidates the slot directly.
    @Composable
    private fun capturingHost(captured: Int, content: @Composable (captured: Int) -> Unit) {
        SubcomposeLayout { constraints ->
            val placeables = subcompose(Unit) { content(captured) }.map { it.measure(constraints) }
            layout(50, 50) { placeables.forEach { it.place(0, 0) } }
        }
    }

    /**
     * A composition local provided above a host, with a value that its host's measure also passes
     * into the slot. The enclosing composition's recompose writes the new local while it
     * composes, and the recomposer then queues every composition that read it, so the slot
     * reaches the recomposer with that write as its only change, before anything recorded an
     * invalidation for it. It must still go to the layout pass, because its enclosing
     * composition changed, and compose there once, after its host's measure has passed the new
     * value, never pairing the new local with the value its host passed before.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot reading a composition local provided above its host composes in the layout, after it`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var value by mutableStateOf(0)
        var probe by mutableStateOf(0)
        val layoutStarted = booleanArrayOf(false)
        val appliedInLayout = mutableListOf<Boolean>()
        val saw = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        // Created outside composition, so it stays the same instance.
        val content: @Composable (Int) -> Unit = { passed ->
            val local = LocalGateTestValue.current
            if (local != passed) tornReads[0]++
            saw[0] = local
            SideEffect { appliedInLayout += layoutStarted[0] }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                LayoutProbe({ probe }, layoutStarted)
                val captured = value
                CompositionLocalProvider(LocalGateTestValue provides captured) {
                    Box { capturingHost(captured, content) }
                }
            }
            scene.render(0)
            assertEquals(0, saw[0], "sanity: the slot must have composed")

            for (frame in 1..3) {
                Snapshot.withMutableSnapshot {
                    value = frame
                    probe = frame
                }
                layoutStarted[0] = false
                appliedInLayout.clear()
                scene.render(frame * 16_000_000L)
                assertEquals(0, tornReads[0], "frame $frame: the slot composed ahead of its content")
                assertEquals(frame, saw[0], "frame $frame: the slot must deliver in the frame")
                assertEquals(
                    listOf(true),
                    appliedInLayout,
                    "frame $frame: the slot must compose once, in the layout",
                )
                // Its host delivered the change, so nothing is left for another frame.
                assertFalse(scene.hasInvalidations(), "frame $frame: the frame must leave no work")
            }

            repeat(3) { scene.render((4 + it) * 16_000_000L) }
            assertFalse(scene.hasInvalidations(), "the scene must go idle")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The same for a state the enclosing composition writes while it composes, as
     * `rememberUpdatedState` does, and that the slot reads: the slot reaches the recomposer only
     * through that write, while its host's measure passes it the value the state is updated to.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot reading a state its enclosing composition updates while composing composes in the layout, after it`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var value by mutableStateOf(0)
        var probe by mutableStateOf(0)
        val layoutStarted = booleanArrayOf(false)
        val appliedInLayout = mutableListOf<Boolean>()
        val saw = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val updated = arrayOfNulls<State<Int>>(1)
        // Created outside composition, so it stays the same instance.
        val content: @Composable (Int) -> Unit = { passed ->
            val fresh = updated[0]!!.value
            if (fresh != passed) tornReads[0]++
            saw[0] = fresh
            SideEffect { appliedInLayout += layoutStarted[0] }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                LayoutProbe({ probe }, layoutStarted)
                val captured = value
                updated[0] = rememberUpdatedState(captured)
                Box { capturingHost(captured, content) }
            }
            scene.render(0)
            assertEquals(0, saw[0], "sanity: the slot must have composed")

            for (frame in 1..3) {
                Snapshot.withMutableSnapshot {
                    value = frame
                    probe = frame
                }
                layoutStarted[0] = false
                appliedInLayout.clear()
                scene.render(frame * 16_000_000L)
                assertEquals(0, tornReads[0], "frame $frame: the slot composed ahead of its content")
                assertEquals(frame, saw[0], "frame $frame: the slot must deliver in the frame")
                assertEquals(
                    listOf(true),
                    appliedInLayout,
                    "frame $frame: the slot must compose once, in the layout",
                )
                // Its host delivered the change, so nothing is left for another frame.
                assertFalse(scene.hasInvalidations(), "frame $frame: the frame must leave no work")
            }

            repeat(3) { scene.render((4 + it) * 16_000_000L) }
            assertFalse(scene.hasInvalidations(), "the scene must go idle")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    // The host of the three hazard tests below: its measure reads [measured] itself, outside any
    // composition, and passes it into its slot's content. Only the owner's snapshot observer sees
    // that read, so a change to it marks the host measure-pending when the transaction is
    // applied, not when a composition applies.
    @Composable
    private fun measureReadingHost(
        measured: () -> Int,
        hostMeasures: IntArray,
        content: @Composable (captured: Int) -> Unit,
    ) {
        SubcomposeLayout { constraints ->
            hostMeasures[0]++
            val captured = measured()
            val placeables = subcompose(Unit) { content(captured) }.map { it.measure(constraints) }
            layout(50, 50) { placeables.forEach { it.place(0, 0) } }
        }
    }

    /**
     * The hazard of deciding before the apply stage. A host whose measure reads a state itself
     * and passes it into its slot's content gets its measure invalidation from the owner's
     * snapshot observer, not from a composition's apply. The same transaction invalidates a slot
     * nested under that host, which reads a state written with it. The nested slot must go to the
     * layout pass and compose after the host's measure has re-supplied the value. That works only
     * if the host is already measure-pending when the recompose block reaches the nested slot:
     * the owner's observer runs when the transaction is applied, on the thread that applies it,
     * and the frame's recompose block runs after that.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot below a host whose measure reads a state written with it composes after that measure`() {
        assertSlotBelowMeasureReadingHostComposesAfterItsMeasure()
    }

    /**
     * The same with frame isolation on. The owner's snapshot observer and the recomposer then
     * both take the transaction when the scene rotates its frame snapshot, at the start of the
     * frame and before its recompose, so the host is measure-pending by then too.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot below a host whose measure reads a state written with it composes after that measure with frame isolation`() {
        val previous = ComposeSceneFeatureFlags.isFrameIsolationEnabled
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = true
        try {
            assertSlotBelowMeasureReadingHostComposesAfterItsMeasure()
        } finally {
            ComposeSceneFeatureFlags.isFrameIsolationEnabled = previous
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun assertSlotBelowMeasureReadingHostComposesAfterItsMeasure() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var measured by mutableStateOf(0)
        var fresh by mutableStateOf(0)
        val hostMeasures = intArrayOf(0)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                measureReadingHost({ measured }, hostMeasures) { captured ->
                    fixedSubHost {
                        val value = fresh
                        if (captured != value) tornReads[0]++
                        nestedSaw[0] = value
                    }
                }
            }
            scene.render(0)
            assertEquals(0, nestedSaw[0], "sanity: the nested slot must have composed")

            for (frame in 1..5) {
                Snapshot.withMutableSnapshot {
                    measured = frame
                    fresh = frame
                }
                hostMeasures[0] = 0
                scene.render(frame * 16_000_000L)
                assertEquals(1, hostMeasures[0], "sanity: frame $frame: the host must measure")
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the nested slot composed before its host's measure re-supplied it",
                )
                assertEquals(frame, nestedSaw[0], "frame $frame: the nested slot must deliver")
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The same hazard for a composition without a host in the slot: it waits for the slot, which
     * its host's measure re-runs, and composes after it.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a composition without a host below a host whose measure reads a state written with it composes after that measure`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var measured by mutableStateOf(0)
        var fresh by mutableStateOf(0)
        val hostMeasures = intArrayOf(0)
        val producedByEnclosing = intArrayOf(-1)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                measureReadingHost({ measured }, hostMeasures) { captured ->
                    producedByEnclosing[0] = captured
                    val context = rememberCompositionContext()
                    DisposableEffect(context) {
                        val gateless = Composition(NoNodeApplier(), context)
                        gateless.setContent {
                            val value = fresh
                            if (producedByEnclosing[0] != value) tornReads[0]++
                            nestedSaw[0] = value
                        }
                        onDispose { gateless.dispose() }
                    }
                }
            }
            scene.render(0)
            assertEquals(0, nestedSaw[0], "sanity: the nested composition must have composed")

            for (frame in 1..5) {
                Snapshot.withMutableSnapshot {
                    measured = frame
                    fresh = frame
                }
                hostMeasures[0] = 0
                scene.render(frame * 16_000_000L)
                assertEquals(1, hostMeasures[0], "sanity: frame $frame: the host must measure")
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the nested composition composed before its slot was re-run",
                )
                assertEquals(frame, nestedSaw[0], "frame $frame: the nested composition must deliver")
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The same hazard with the transaction applied on another thread before the frame. The
     * owner's observer does not run there: it posts its work to the scene's effect queue, which
     * the frame drains before its recompose block. So the host is measure-pending by then too.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot below a host whose measure reads a state written with it on another thread composes after that measure`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var measured by mutableStateOf(0)
        var fresh by mutableStateOf(0)
        val hostMeasures = intArrayOf(0)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                measureReadingHost({ measured }, hostMeasures) { captured ->
                    fixedSubHost {
                        val value = fresh
                        if (captured != value) tornReads[0]++
                        nestedSaw[0] = value
                    }
                }
            }
            scene.render(0)
            assertEquals(0, nestedSaw[0], "sanity: the nested slot must have composed")

            for (frame in 1..5) {
                thread {
                        Snapshot.withMutableSnapshot {
                            measured = frame
                            fresh = frame
                        }
                    }
                    .join()
                hostMeasures[0] = 0
                scene.render(frame * 16_000_000L)
                assertEquals(1, hostMeasures[0], "sanity: frame $frame: the host must measure")
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the nested slot composed before its host's measure re-supplied it",
                )
                assertEquals(frame, nestedSaw[0], "frame $frame: the nested slot must deliver")
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A host can also re-supply a slot's content from what it measured in another of its slots,
     * as `Scaffold` passes its top bar's height to its content. That re-supply is not visible
     * before the layout, neither before nor after the apply: the other slot's change reaches the
     * host only when the layout measures that slot and finds its size changed. A slot nested in
     * the re-supplied one that reads a state written with the other slot's is then waiting for
     * nothing when the recompose block reaches it.
     *
     * Stock Compose behaves the same way, and this is its behaviour here: nothing enclosing the
     * nested slot recomposed, and no host above it is scheduled, so it recomposes before the
     * layout, as stock recomposes it, and pairs the fresh state with the size the sibling slot
     * had in the previous frame. The layout then re-runs the re-supplied slot, whose new content
     * invalidates the nested one, and that is served in the next frame: the nested slot is
     * corrected one frame later, or torn in every frame while the state keeps changing. Every
     * state read comes from one snapshot, and the size is a layout output, which stock never
     * hands to a composition that recomposes on its own, so under stock's contract this is the
     * usual one-frame lag of a value derived from layout, not a read of inconsistent snapshot
     * state. It stays a torn read by the stricter rule this file pins for captures.
     *
     * Handing every invalidated slot to its host's layout pass orders it, at the cost of a host
     * measure per change. Re-running, after the pass, a composition declined in the frame whose
     * enclosing slot the pass re-ran would correct it in the same frame instead, without that
     * cost, but the torn composition would still run first.
     */
    @Ignore(
        "Stock-equivalent: a re-supply from a sibling slot's measured size is visible only in " +
            "the layout, so the nested slot recomposes before it with the previous frame's size " +
            "and is corrected in the next frame, as in stock Compose"
    )
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot below a host that re-supplies it from a sibling slot's size composes after that measure`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var width by mutableStateOf(10)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        // The bar reads the width in composition, so its own slot recomposes, and the new width
        // reaches the host only through the bar's measured size.
        val barContent: @Composable () -> Unit = {
            val w = width
            Layout(content = {}) { _, _ -> layout(w, 10) {} }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                SubcomposeLayout { constraints ->
                    val bar = subcompose("bar", barContent).map { it.measure(Constraints()) }
                    val barWidth = bar.maxOf { it.width }
                    val body =
                        subcompose("body") {
                                fixedSubHost {
                                    val value = width
                                    if (barWidth != value) tornReads[0]++
                                    nestedSaw[0] = value
                                }
                            }
                            .map { it.measure(constraints) }
                    layout(100, 100) {
                        bar.forEach { it.place(0, 0) }
                        body.forEach { it.place(0, 10) }
                    }
                }
            }
            scene.render(0)
            assertEquals(10, nestedSaw[0], "sanity: the nested slot must have composed")

            for (frame in 1..3) {
                Snapshot.withMutableSnapshot { width = 10 + frame }
                scene.render(frame * 16_000_000L)
                assertEquals(
                    0,
                    tornReads[0],
                    "frame $frame: the nested slot composed before its host re-supplied it",
                )
                assertEquals(10 + frame, nestedSaw[0], "frame $frame: the nested slot must deliver")
            }
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * The shape above, pinned at what stock Compose does with it, so that nothing makes it worse:
     * after each change of the sibling slot's size, the nested slot composes against the old size
     * at most once, and composes with the new one by the next frame with nothing to change. Every
     * frame is followed by one such frame here, and the scene then goes idle.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot below a host that re-supplies it from a sibling slot's size is corrected by the next frame`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var width by mutableStateOf(10)
        val tornReads = intArrayOf(0)
        val nestedSaw = intArrayOf(-1)
        val nestedSawBarWidth = intArrayOf(-1)
        // The bar reads the width in composition, so its own slot recomposes, and the new width
        // reaches the host only through the bar's measured size.
        val barContent: @Composable () -> Unit = {
            val w = width
            Layout(content = {}) { _, _ -> layout(w, 10) {} }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                SubcomposeLayout { constraints ->
                    val bar = subcompose("bar", barContent).map { it.measure(Constraints()) }
                    val barWidth = bar.maxOf { it.width }
                    val body =
                        subcompose("body") {
                                fixedSubHost {
                                    val value = width
                                    if (barWidth != value) tornReads[0]++
                                    nestedSaw[0] = value
                                    nestedSawBarWidth[0] = barWidth
                                }
                            }
                            .map { it.measure(constraints) }
                    layout(100, 100) {
                        bar.forEach { it.place(0, 0) }
                        body.forEach { it.place(0, 10) }
                    }
                }
            }
            scene.render(0)
            assertEquals(10, nestedSaw[0], "sanity: the nested slot must have composed")

            for (frame in 1..3) {
                Snapshot.withMutableSnapshot { width = 10 + frame }
                scene.render((2 * frame - 1) * 16_000_000L)
                scene.render(2 * frame * 16_000_000L)
                assertTrue(
                    tornReads[0] <= frame,
                    "frame $frame: the nested slot must compose against the old size at most " +
                        "once per change, but did ${tornReads[0]} times in $frame changes",
                )
                assertEquals(10 + frame, nestedSaw[0], "frame $frame: the nested slot must deliver")
                assertEquals(
                    10 + frame,
                    nestedSawBarWidth[0],
                    "frame $frame: the nested slot must have the new size by the next frame",
                )
            }

            repeat(3) { scene.render((7 + it) * 16_000_000L) }
            assertFalse(scene.hasInvalidations(), "the scene must go idle")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A host that stays scheduled although its owner's full layout passes do not measure it: its
     * parent measured it once and afterwards only places what that measure returned, and its own
     * remeasure is requested while the parent is measure-pending, so the pass leaves it to the
     * parent. A slot handed to it is left for its measure once. When a full pass after that
     * still finds the host scheduled, and the slot pending, the slot composes on its own rather
     * than waiting for that measure again: every such wait forces another full pass, so without
     * the limit the slot would never deliver and the scene would never go idle.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a slot whose host stays scheduled without being measured composes on its own after one more full pass`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        var other by mutableStateOf(0)
        var value by mutableStateOf(0)
        var parentTick by mutableStateOf(0)
        val otherSaw = intArrayOf(-1)
        val saw = intArrayOf(-1)
        val requestHostMeasure = booleanArrayOf(false)
        val hostNode = arrayOfNulls<LayoutNode>(1)
        val hostModifier =
            object : RemeasurementModifier {
                override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                    // A layout node is its own remeasurement.
                    hostNode[0] = remeasurement as LayoutNode
                }
            }
        var measuredOnce: Placeable? = null
        // Created outside composition, so a recompose of the root gives neither node new content
        // nor a new measure policy.
        val content: @Composable () -> Unit = { saw[0] = value }
        val hostPolicy: SubcomposeMeasureScope.(Constraints) -> MeasureResult = { constraints ->
            val placeables = subcompose(Unit, content).map { it.measure(constraints) }
            layout(10, 10) { placeables.forEach { it.place(0, 0) } }
        }
        val parentContent: @Composable () -> Unit = {
            SubcomposeLayout(hostModifier, measurePolicy = hostPolicy)
        }
        val parentPolicy = MeasurePolicy { measurables, constraints ->
            // Read in measure, so a change marks this node measure-pending when it is applied.
            @Suppress("UNUSED_EXPRESSION") parentTick
            val placeable =
                measuredOnce ?: measurables.single().measure(constraints).also { measuredOnce = it }
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                otherSaw[0] = other
                SideEffect { if (requestHostMeasure[0]) hostNode[0]!!.requestRemeasure() }
                Layout(content = parentContent, measurePolicy = parentPolicy)
            }
            scene.render(0)
            assertEquals(0, saw[0], "sanity: the slot must have composed")

            requestHostMeasure[0] = true
            for (frame in 1..3) {
                Snapshot.withMutableSnapshot {
                    other = frame
                    value = frame
                    parentTick = frame
                }
                scene.render(frame * 16_000_000L)
                assertEquals(frame, otherSaw[0], "sanity: frame $frame: the root must recompose")
                assertTrue(
                    hostNode[0]!!.measurePending,
                    "sanity: frame $frame: the host must be left measure-pending",
                )
                assertEquals(frame, saw[0], "frame $frame: the slot must deliver in the frame")
            }

            requestHostMeasure[0] = false
            repeat(3) { scene.render((4 + it) * 16_000_000L) }
            assertFalse(scene.hasInvalidations(), "the scene must go idle")
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    // A composition's side without a composition behind it: it records what a slot asks of it,
    // and runs [whenRecomposedNow] where a composition would compose and apply.
    @OptIn(InternalComposeApi::class)
    private class RecordingHosting(
        private val recomposes: Boolean = true,
        private val whenRecomposedNow: () -> Unit = {},
    ) : ParentDrivenHosting {
        var recomposeNowCalls = 0
        var recomposeLaterCalls = 0

        override var host: ParentDrivenHost? = null

        override fun installHost(host: ParentDrivenHost) {
            this.host = host
        }

        override val enclosingHosted: ParentDrivenHosting?
            get() = null

        override fun recomposeNow(): Boolean {
            recomposeNowCalls++
            whenRecomposedNow()
            return recomposes
        }

        override fun recomposeLater() {
            recomposeLaterCalls++
        }
    }

    // A composition's side of the ordering, as the slots reach it.
    @OptIn(InternalComposeApi::class)
    private fun hostingOf(composition: Composition): ParentDrivenHosting =
        (composition as CompositionServices).getCompositionService(ParentDrivenHostingKey)!!

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
