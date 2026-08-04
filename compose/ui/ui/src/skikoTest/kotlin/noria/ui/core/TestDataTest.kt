package noria.ui.core

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
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SchedulingDispatcherFixture
import androidx.compose.ui.unit.dp
import noria.foundation.layout.MainOverlayHostKey
import noria.foundation.layout.OverlayHost
import noria.foundation.layout.overlay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class TestDataTest {

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

    /**
     * Renders [content] in a headless scene and hands the block its semantics owners.
     *
     * Renders twice: some content (e.g. `noria.foundation.layout.OverlayHost`) only subcomposes
     * once its host has been placed, which requires a layout pass to have already happened, so a
     * single render is not enough to observe it.
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

    private fun Collection<SemanticsOwner>.publishedEntries(): List<List<TestDataEntry>> =
        flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .mapNotNull { it.config.getOrNull(TestDataEntries) }

    private val Key = TestDataKey<String>("k")
    private val Key2 = TestDataKey<String>("k2")

    @Test
    fun `testData publishes its entry into the unmerged semantics tree`() = withScene(
        content = { Box(Modifier.size(10.dp).testData(Key, "v")) }
    ) { owners ->
        assertEquals(
            listOf(listOf(TestDataEntry.Payload(Key, "v"))),
            owners.publishedEntries(),
        )
    }

    @Test
    fun `entries on one chain are published together, outermost first`() = withScene(
        content = { Box(Modifier.size(10.dp).testData(Key, "a").testData(Key2, "b")) }
    ) { owners ->
        assertEquals(
            listOf(
                listOf(
                    TestDataEntry.Payload(Key, "a"),
                    TestDataEntry.Payload(Key2, "b"),
                )
            ),
            owners.publishedEntries(),
        )
    }

    @Test
    fun `a subtree marker before a payload places the payload inside it`() = withScene(
        content = { Box(Modifier.size(10.dp).markTestSubtree("s").testData(Key, "a")) }
    ) { owners ->
        assertEquals(
            listOf(
                listOf(
                    TestDataEntry.Subtree("s"),
                    TestDataEntry.Payload(Key, "a"),
                )
            ),
            owners.publishedEntries(),
        )
    }

    @Test
    fun `a subtree marker after a payload places the payload outside it`() = withScene(
        content = { Box(Modifier.size(10.dp).testData(Key, "a").markTestSubtree("s")) }
    ) { owners ->
        assertEquals(
            listOf(
                listOf(
                    TestDataEntry.Payload(Key, "a"),
                    TestDataEntry.Subtree("s"),
                )
            ),
            owners.publishedEntries(),
        )
    }

    private fun uiRootOf(owners: Collection<SemanticsOwner>) = UIRoot { owners }

    private fun UIRoot.stream(): List<Pair<String, Any?>> =
        getAllTestNodes().map { node ->
            val (key, value) = node.data.entries.single()
            key.id to value
        }.toList()

    @Test
    fun `exit markers are emitted after the entire subtree`() = withScene(
        content = {
            Box(Modifier.size(50.dp).markTestSubtree("outer")) {
                Box(Modifier.size(10.dp).testData(Key, "inner"))
            }
        }
    ) { owners ->
        assertEquals(
            listOf(
                "test-data/enter-subtree" to "outer",
                "k" to "inner",
                "test-data/exit-subtree" to "outer",
            ),
            uiRootOf(owners).stream(),
        )
    }

    @Test
    fun `stacked markers close in reverse order`() = withScene(
        content = { Box(Modifier.size(10.dp).markTestSubtree("a").markTestSubtree("b")) }
    ) { owners ->
        assertEquals(
            listOf(
                "test-data/enter-subtree" to "a",
                "test-data/enter-subtree" to "b",
                "test-data/exit-subtree" to "b",
                "test-data/exit-subtree" to "a",
            ),
            uiRootOf(owners).stream(),
        )
    }

    @Test
    fun `traversal is depth-first pre-order with siblings in order`() = withScene(
        content = {
            Column(Modifier.testData(Key, "root")) {
                Box(Modifier.size(10.dp).testData(Key, "first"))
                Box(Modifier.size(10.dp).testData(Key, "second"))
            }
        }
    ) { owners ->
        assertEquals(
            listOf("k" to "root", "k" to "first", "k" to "second"),
            uiRootOf(owners).stream(),
        )
    }

    @Test
    fun `two testData calls on one chain become two single-entry nodes in chain order`() = withScene(
        content = { Box(Modifier.size(10.dp).testData(Key, "a").testData(Key2, "b")) }
    ) { owners ->
        val nodes = uiRootOf(owners).getAllTestNodes().toList()
        assertEquals(listOf("a", "b"), nodes.map { it.data.values.single() })
        assertTrue(nodes.all { it.data.size == 1 }, "each node must carry exactly one entry: $nodes")
    }

    @Test
    fun `the same key on two elements yields two nodes`() = withScene(
        content = {
            Column {
                Box(Modifier.size(10.dp).testData(Key, "one"))
                Box(Modifier.size(10.dp).testData(Key, "two"))
            }
        }
    ) { owners ->
        assertEquals(listOf("one", "two"), uiRootOf(owners).getAllTestNodes().map { it.data[Key] }.toList())
    }

    @Test
    fun `filterAllTestNodesByPath collects across repeated sibling subtrees`() = withScene(
        content = {
            Column {
                repeat(3) { i ->
                    Box(Modifier.size(10.dp).markTestSubtree("item").testData(Key, "v$i"))
                }
            }
        }
    ) { owners ->
        val values = uiRootOf(owners).getAllTestNodes().toList()
            .filterAllTestNodesByPath(listOf("item"))
            .mapNotNull { it.data[Key] }
        assertEquals(listOf("v0", "v1", "v2"), values)
    }

    @Test
    fun `filterTestNodesByPath slices a nested path`() = withScene(
        content = {
            Box(Modifier.size(50.dp).markTestSubtree("outer")) {
                Box(Modifier.size(20.dp).markTestSubtree("inner").testData(Key, "hit"))
            }
        }
    ) { owners ->
        val values = uiRootOf(owners).getAllTestNodes().toList()
            .filterTestNodesByPath(listOf("outer", "inner"))
            .mapNotNull { it.data[Key] }
        assertEquals(listOf("hit"), values)
    }

    @Test
    fun `values are returned by reference, not copied`() {
        val live = object {}
        val liveKey = TestDataKey<Any>("live")
        withScene(content = { Box(Modifier.size(10.dp).testData(liveKey, live)) }) { owners ->
            val found = uiRootOf(owners).getAllTestNodes().firstNotNullOf { it.data[liveKey] }
            assertSame(live, found)
        }
    }

    @Test
    fun `a value change is reflected after recomposition`() {
        var value by mutableStateOf("before")
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent { Box(Modifier.size(10.dp).testData(Key, value)) }
            scene.render()
            assertEquals("before", uiRootOf(scene.semanticsOwners).getAllTestNodes().firstNotNullOf { it.data[Key] })
            value = "after"
            scene.render()
            assertEquals("after", uiRootOf(scene.semanticsOwners).getAllTestNodes().firstNotNullOf { it.data[Key] })
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
                Box(Modifier.size(10.dp).let { if (present) it.testData(Key, "v") else it })
            }
            scene.render()
            assertEquals(1, uiRootOf(scene.semanticsOwners).getAllTestNodes().count())
            present = false
            scene.render()
            assertEquals(0, uiRootOf(scene.semanticsOwners).getAllTestNodes().count())
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
                        item { Box(Modifier.size(10.dp).testData(Key, "row")) }
                    }
                }
            }
            scene.render()
            assertEquals(1, uiRootOf(scene.semanticsOwners).getAllTestNodes().count())
            visible = false
            // LazyColumn doesn't move the departed item's node into the reuse pool on the very next
            // frame; it takes a few measure passes for `layoutNode.isDeactivated` to flip. Render
            // until the stream is empty rather than hard-coding a frame count, so this test doesn't
            // start failing outright if a future Compose version needs a different number of passes.
            var excluded = false
            for (i in 0 until 10) {
                scene.render()
                if (uiRootOf(scene.semanticsOwners).getAllTestNodes().count() == 0) {
                    excluded = true
                    break
                }
            }
            assertEquals(true, excluded, "deactivated (reuse-pool) content must not appear in the stream")

            // The exclusion above must specifically be the deactivation path, not the row having
            // been disposed outright: the row's semantics node should still physically exist when
            // deactivated nodes are included, carrying no test-data (cleared on deactivation).
            val rowStillExists = scene.semanticsOwners.any { owner ->
                owner.getAllSemanticsNodes(mergingEnabled = false, skipDeactivatedNodes = false)
                    .any { it.layoutNode.isDeactivated }
            }
            assertEquals(true, rowStillExists, "row must still exist in the tree, merely deactivated")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `dumpToString indents by subtree depth`() = withScene(
        content = {
            Box(Modifier.size(50.dp).markTestSubtree("outer")) {
                Box(Modifier.size(10.dp).testData(Key, "leaf"))
            }
        }
    ) { owners ->
        assertEquals(
            """
            Container Node: outer
              Leaf Node: k
            """.trimIndent() + "\n",
            uiRootOf(owners).dumpToString(),
        )
    }

    @Test
    fun `getAllTestNodes is re-iterable and immune to owner-set mutation during iteration`() {
        // Two owners must already be present when the sequence starts consuming
        // `semanticsOwners()`: `SnapshotStateSet.iterator()` freezes its own enumeration source (a
        // persistent set snapshot) at call time, and only re-validates a modification counter on a
        // *subsequent* `next()` call. With a single owner, `hasNext()` would already (correctly)
        // report "no more elements" from that frozen snapshot before any such revalidation — so the
        // crash needs a second owner already queued up when the mutation lands, exactly like a
        // window that already owns a popup layer when a further layer attaches or detaches mid-poll.
        val scene1 = ImageComposeScene(width = 200, height = 200)
        val scene2 = ImageComposeScene(width = 50, height = 50)
        val scene3 = ImageComposeScene(width = 50, height = 50)
        try {
            scene1.setContent {
                Box(Modifier.size(50.dp).markTestSubtree("outer")) {
                    Box(Modifier.size(10.dp).testData(Key, "inner"))
                }
            }
            scene1.render()
            scene1.render()
            scene2.setContent { Box(Modifier.size(10.dp).testData(Key, "second")) }
            scene2.render()
            scene3.setContent { Box(Modifier.size(10.dp).testData(Key, "third")) }
            scene3.render()

            val owners = mutableStateSetOf<SemanticsOwner>().apply {
                addAll(scene1.semanticsOwners)
                addAll(scene2.semanticsOwners)
            }
            val uiRoot = UIRoot { owners }

            // Re-iterability: `getAllTestNodes()` returns a plain `kotlin.sequences.sequence {}`,
            // not a constrained-once one, so consuming it twice must yield the same nodes both times.
            val sequence = uiRoot.getAllTestNodes()
            val first = sequence.map { it.data.entries.single().toPair() }.toList()
            val second = sequence.map { it.data.entries.single().toPair() }.toList()
            assertEquals(first, second)
            assertEquals(4, first.size) // Enter("outer"), ("k", "inner"), Exit("outer"), ("k", "second")

            // Simulate a further `ComposeSceneLayer`-based popup attaching (or detaching) while a
            // consumer is part-way through the sequence — exactly `waitForTestData`'s polling
            // pattern. A `for` loop held open across `sequence {}`'s lazy yields, still mid-way
            // through the first owner's subtree, would surface this as a
            // ConcurrentModificationException once it reaches the already-queued second owner;
            // snapshotting the owners eagerly must not.
            val iterator = uiRoot.getAllTestNodes().iterator()
            iterator.next() // First node of whichever owner iterates first; the other owner is
                            // already queued up behind it, which is what makes the CME reachable.
            owners.addAll(scene3.semanticsOwners)
            val rest = generateSequence { if (iterator.hasNext()) iterator.next() else null }.toList()
            assertEquals(3, rest.size) // The remaining three nodes, across both original owners.
        } finally {
            scene1.close()
            scene2.close()
            scene3.close()
        }
    }

    @Test
    fun `with the flag off testData is the identity modifier`() {
        TestDataMode.isEnabled = false
        val modifier = Modifier.testData(Key, "v")
        assertSame(Modifier, modifier)
    }

    @Test
    fun `with the flag off reading the tree fails loudly`() {
        TestDataMode.isEnabled = false
        val scene = ImageComposeScene(width = 200, height = 200)
        try {
            scene.setContent { Box(Modifier.size(10.dp)) }
            scene.render()
            val error = assertFailsWith<IllegalStateException> {
                uiRootOf(scene.semanticsOwners).getAllTestNodes().toList()
            }
            assertTrue(error.message!!.contains("TestDataMode.isEnabled"))
        } finally {
            scene.close()
        }
    }

    /**
     * KNOWN DIVERGENCE FROM NORIA — deliberately asserts the *current*, non-Noria behaviour.
     *
     * Noria's `Modifier.overlay` emitted its focus node at the anchor
     * (`fleet/noria/ui/srcCommonMain/noria/foundation/layout/Overlay.kt:74-95`), so popup test data
     * sat inside the anchor's `markTestSubtree` and path-scoped queries into popups worked. This
     * fork's `OverlayHost` subcomposes overlays as siblings under the host box after `content()`
     * (`noria/foundation/layout/OverlayHost.kt:59-116`), so the popup's data lands after the
     * anchor's `Exit` marker instead.
     *
     * If splicing is implemented later — having `OverlayState` record its anchor so the flattener
     * descends into anchored overlays at the anchor — this test should be inverted, not deleted.
     */
    @Test
    fun overlayContentIsNotNestedUnderItsAnchor_knownDivergenceFromNoria() = withScene(
        content = {
            OverlayHost(MainOverlayHostKey) {
                Box(
                    Modifier
                        .size(20.dp)
                        .markTestSubtree("anchor")
                        .overlay { Box(Modifier.size(5.dp).testData(Key, "popup")) }
                )
            }
        }
    ) { owners ->
        val stream = uiRootOf(owners).stream()
        val exitIndex = stream.indexOfFirst { it == "test-data/exit-subtree" to "anchor" }
        val popupIndex = stream.indexOfFirst { it == "k" to "popup" }
        assertTrue(exitIndex >= 0, "anchor subtree should be in the stream: $stream")
        assertTrue(popupIndex >= 0, "popup data should be in the stream: $stream")
        assertTrue(
            popupIndex > exitIndex,
            "popup data is expected OUTSIDE the anchor subtree today; if this now fails, " +
                "overlay splicing has landed and this test should be inverted. Stream: $stream",
        )
    }
}
