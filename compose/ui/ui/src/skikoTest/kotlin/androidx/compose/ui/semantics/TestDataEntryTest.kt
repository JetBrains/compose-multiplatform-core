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

package androidx.compose.ui.semantics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.TestDataEntry.Payload
import androidx.compose.ui.semantics.TestDataEntry.Subtree
import androidx.compose.ui.semantics.TestDataEntry.SubtreeEnd
import androidx.compose.ui.test.SchedulingDispatcherFixture
import androidx.compose.ui.unit.dp
import noria.foundation.layout.MainOverlayHostKey
import noria.foundation.layout.OverlayHost
import noria.foundation.layout.overlay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class TestDataEntryTest {

    private val schedulingDispatcher = SchedulingDispatcherFixture()

    @BeforeTest
    fun setUp() {
        schedulingDispatcher.install()
        TestDataMode.isEnabled = true
    }

    @AfterTest
    fun tearDown() {
        TestDataMode.isEnabled = false
        schedulingDispatcher.uninstall()
    }

    private fun Modifier.payload(key: Any, value: Any) = testDataEntry(Payload(key, value))

    private fun Modifier.subtree(id: String) = testDataEntry(Subtree(id))

    /**
     * Renders [content] in a headless scene and hands the block its semantics owners.
     *
     * Renders twice: some content only subcomposes once its host has been placed, which requires a
     * layout pass to have already happened, so a single render is not enough to observe it.
     */
    private fun withScene(
        content: @Composable () -> Unit,
        block: (Collection<SemanticsOwner>) -> Unit,
    ) {
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent(content)
            scene.render()
            scene.render()
            block(scene.semanticsOwners)
        } finally {
            scene.close()
        }
    }

    /** What each layout node in the tree published, in tree order. */
    private fun Collection<SemanticsOwner>.publishedEntries(): List<List<TestDataEntry>> =
        flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .mapNotNull { it.config.getOrNull(TestDataEntries) }

    private fun Collection<SemanticsOwner>.flattened(): List<TestDataEntry> =
        flattenTestDataEntries().toList()

    // region publication

    @Test
    fun `an entry is published into the unmerged semantics tree`() = withScene(
        content = { Box(Modifier.size(10.dp).payload("k", "v")) }
    ) { owners ->
        assertEquals(listOf(listOf(Payload("k", "v"))), owners.publishedEntries())
    }

    @Test
    fun `entries on one chain are published together, outermost first`() = withScene(
        content = { Box(Modifier.size(10.dp).payload("k", "a").payload("k2", "b")) }
    ) { owners ->
        assertEquals(
            listOf(listOf(Payload("k", "a"), Payload("k2", "b"))),
            owners.publishedEntries(),
        )
    }

    @Test
    fun `a subtree marker before a payload places the payload inside it`() = withScene(
        content = { Box(Modifier.size(10.dp).subtree("s").payload("k", "a")) }
    ) { owners ->
        assertEquals(
            listOf(listOf(Subtree("s"), Payload("k", "a"))),
            owners.publishedEntries(),
        )
    }

    @Test
    fun `a subtree marker after a payload places the payload outside it`() = withScene(
        content = { Box(Modifier.size(10.dp).payload("k", "a").subtree("s")) }
    ) { owners ->
        assertEquals(
            listOf(listOf(Payload("k", "a"), Subtree("s"))),
            owners.publishedEntries(),
        )
    }

    // endregion

    // region flatten order

    @Test
    fun `a subtree closes after its entire descendant range`() = withScene(
        content = {
            Box(Modifier.size(50.dp).subtree("outer")) {
                Box(Modifier.size(10.dp).payload("k", "inner"))
            }
        }
    ) { owners ->
        assertEquals(
            listOf(Subtree("outer"), Payload("k", "inner"), SubtreeEnd("outer")),
            owners.flattened(),
        )
    }

    @Test
    fun `stacked subtrees close in reverse order`() = withScene(
        content = { Box(Modifier.size(10.dp).subtree("a").subtree("b")) }
    ) { owners ->
        assertEquals(
            listOf(Subtree("a"), Subtree("b"), SubtreeEnd("b"), SubtreeEnd("a")),
            owners.flattened(),
        )
    }

    @Test
    fun `traversal is depth-first pre-order with siblings in order`() = withScene(
        content = {
            Column(Modifier.payload("k", "root")) {
                Box(Modifier.size(10.dp).payload("k", "first"))
                Box(Modifier.size(10.dp).payload("k", "second"))
            }
        }
    ) { owners ->
        assertEquals(
            listOf(Payload("k", "root"), Payload("k", "first"), Payload("k", "second")),
            owners.flattened(),
        )
    }

    @Test
    fun `two calls on one chain stay two separate entries in chain order`() = withScene(
        content = { Box(Modifier.size(10.dp).payload("k", "a").payload("k2", "b")) }
    ) { owners ->
        assertEquals(listOf(Payload("k", "a"), Payload("k2", "b")), owners.flattened())
    }

    @Test
    fun `the same key on two elements yields two entries`() = withScene(
        content = {
            Column {
                Box(Modifier.size(10.dp).payload("k", "one"))
                Box(Modifier.size(10.dp).payload("k", "two"))
            }
        }
    ) { owners ->
        assertEquals(listOf(Payload("k", "one"), Payload("k", "two")), owners.flattened())
    }

    @Test
    fun `values are carried by reference, not copied`() {
        val live = object {}
        withScene(content = { Box(Modifier.size(10.dp).payload("live", live)) }) { owners ->
            val found = owners.flattened().filterIsInstance<Payload>().single().value
            assertSame(live, found)
        }
    }

    /**
     * Asserts the *current* behaviour, which diverges from the harness this mechanism replaces.
     *
     * There, a popup's focus node was emitted at its anchor, so popup entries landed inside the
     * anchor's subtree and path-scoped queries into popups worked. Here, [OverlayHost] subcomposes
     * overlays as siblings under the host box after its content, so a popup's entries land after
     * the anchor's [SubtreeEnd] instead.
     *
     * The divergence is a flatten-order property across a subcomposition boundary, which is exactly
     * the kind that changes silently. If splicing is implemented later — having the overlay record
     * its anchor so the flattener descends into anchored overlays at the anchor — this test should
     * be inverted, not deleted.
     */
    @Test
    fun `overlay content is not nested under its anchor`() = withScene(
        content = {
            OverlayHost(MainOverlayHostKey) {
                Box(
                    Modifier
                        .size(20.dp)
                        .subtree("anchor")
                        .overlay { Box(Modifier.size(5.dp).payload("k", "popup")) }
                )
            }
        }
    ) { owners ->
        val stream = owners.flattened()
        val endIndex = stream.indexOf(SubtreeEnd("anchor"))
        val popupIndex = stream.indexOf(Payload("k", "popup"))
        assertTrue(endIndex >= 0, "anchor subtree should be in the stream: $stream")
        assertTrue(popupIndex >= 0, "popup data should be in the stream: $stream")
        assertTrue(
            popupIndex > endIndex,
            "popup data is expected OUTSIDE the anchor subtree today; if this now fails, " +
                "overlay splicing has landed and this test should be inverted. Stream: $stream",
        )
    }

    // endregion

    // region invalidation

    @Test
    fun `a value change is reflected after recomposition`() {
        var value by mutableStateOf("before")
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent { Box(Modifier.size(10.dp).payload("k", value)) }
            scene.render()
            assertEquals(listOf(Payload("k", "before")), scene.semanticsOwners.flattened())
            value = "after"
            scene.render()
            assertEquals(listOf(Payload("k", "after")), scene.semanticsOwners.flattened())
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a conditionally removed entry disappears from the stream`() {
        var present by mutableStateOf(true)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                Box(Modifier.size(10.dp).let { if (present) it.payload("k", "v") else it })
            }
            scene.render()
            assertEquals(1, scene.semanticsOwners.flattened().size)
            present = false
            scene.render()
            assertEquals(0, scene.semanticsOwners.flattened().size)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `subcompose reuse-pool content does not appear`() {
        var visible by mutableStateOf(true)
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent {
                LazyColumn(Modifier.size(50.dp)) {
                    if (visible) {
                        item { Box(Modifier.size(10.dp).payload("k", "row")) }
                    }
                }
            }
            scene.render()
            assertEquals(1, scene.semanticsOwners.flattened().size)
            visible = false
            // LazyColumn doesn't move the departed item's node into the reuse pool on the very next
            // frame; it takes a few measure passes for `layoutNode.isDeactivated` to flip. Render
            // until the stream is empty rather than hard-coding a frame count, so this test doesn't
            // start failing outright if a future Compose version needs a different number of passes.
            var excluded = false
            for (i in 0 until 10) {
                scene.render()
                if (scene.semanticsOwners.flattened().isEmpty()) {
                    excluded = true
                    break
                }
            }
            assertTrue(excluded, "deactivated (reuse-pool) content must not appear in the stream")

            // The exclusion above must specifically be the deactivation path, not the row having
            // been disposed outright: the row's semantics node should still physically exist when
            // deactivated nodes are included, carrying no test data (cleared on deactivation).
            val rowStillExists = scene.semanticsOwners.any { owner ->
                owner.getAllSemanticsNodes(mergingEnabled = false, skipDeactivatedNodes = false)
                    .any { it.layoutNode.isDeactivated }
            }
            assertTrue(rowStillExists, "row must still exist in the tree, merely deactivated")
        } finally {
            scene.close()
        }
    }

    // endregion

    // region owner-set safety

    @Test
    fun `the stream is re-iterable and immune to owner-set mutation during iteration`() {
        // Two owners must already be present when the sequence starts consuming the collection:
        // `SnapshotStateSet.iterator()` freezes its own enumeration source (a persistent set
        // snapshot) at call time, and only re-validates a modification counter on a *subsequent*
        // `next()` call. With a single owner, `hasNext()` would already (correctly) report "no more
        // elements" from that frozen snapshot before any such revalidation — so the crash needs a
        // second owner already queued up when the mutation lands, exactly like a window that
        // already owns a popup layer when a further layer attaches or detaches mid-poll.
        val scene1 = ImageComposeScene(width = 200, height = 200)
        val scene2 = ImageComposeScene(width = 50, height = 50)
        val scene3 = ImageComposeScene(width = 50, height = 50)
        try {
            scene1.setContent {
                Box(Modifier.size(50.dp).subtree("outer")) {
                    Box(Modifier.size(10.dp).payload("k", "inner"))
                }
            }
            scene1.render()
            scene1.render()
            scene2.setContent { Box(Modifier.size(10.dp).payload("k", "second")) }
            scene2.render()
            scene3.setContent { Box(Modifier.size(10.dp).payload("k", "third")) }
            scene3.render()

            val owners = mutableStateSetOf<SemanticsOwner>().apply {
                addAll(scene1.semanticsOwners)
                addAll(scene2.semanticsOwners)
            }

            // Re-iterability: the result is a plain `kotlin.sequences.sequence {}`, not a
            // constrained-once one, so consuming it twice must yield the same entries both times.
            val sequence = owners.flattenTestDataEntries()
            val first = sequence.toList()
            val second = sequence.toList()
            assertEquals(first, second)
            assertEquals(
                listOf(
                    Subtree("outer"),
                    Payload("k", "inner"),
                    SubtreeEnd("outer"),
                    Payload("k", "second"),
                ),
                first,
            )

            // Simulate a further layer-based popup attaching (or detaching) while a consumer is
            // part-way through the sequence — exactly a polling read's pattern. A loop held open
            // across `sequence {}`'s lazy yields, still mid-way through the first owner's subtree,
            // would surface this as a ConcurrentModificationException once it reached the
            // already-queued second owner; snapshotting the owners eagerly must not.
            val iterator = owners.flattenTestDataEntries().iterator()
            iterator.next() // First entry of whichever owner iterates first; the other owner is
                            // already queued up behind it, which is what makes the CME reachable.
            owners.addAll(scene3.semanticsOwners)
            val rest = generateSequence { if (iterator.hasNext()) iterator.next() else null }.toList()
            assertEquals(3, rest.size) // The remaining three entries, across both original owners.
        } finally {
            scene1.close()
            scene2.close()
            scene3.close()
        }
    }

    // endregion

    // region gating

    @Test
    fun `with the flag off the modifier is the identity`() {
        TestDataMode.isEnabled = false
        assertSame(Modifier, Modifier.testDataEntry(Payload("k", "v")))
    }

    @Test
    fun `with the flag off nothing is published`() {
        TestDataMode.isEnabled = false
        withScene(content = { Box(Modifier.size(10.dp).payload("k", "v")) }) { owners ->
            assertEquals(emptyList<TestDataEntry>(), owners.flattened())
        }
    }

    // endregion
}
