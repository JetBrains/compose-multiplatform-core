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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.Nodes
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.visitLocalAncestors
import androidx.compose.ui.node.visitLocalDescendants
import androidx.compose.ui.platform.InspectorInfo

/**
 * One contribution of a [Modifier.testDataEntry] call.
 *
 * The granularity is one entry per call, never a map merged per element: a consumer classifies an
 * entry as a subtree marker or a payload in a mutually exclusive `when`, and an entry carrying both
 * would silently lose the payload.
 *
 * [Payload.key] and [Payload.value] are deliberately untyped. This module publishes the mechanism
 * only; the typed vocabulary of keys, and whatever tree or query API is built on top of the
 * flattened stream, belongs to the consumer that owns those names.
 */
sealed interface TestDataEntry {
    /** An arbitrary key/value pair attached to one element. */
    data class Payload(val key: Any, val value: Any) : TestDataEntry

    /**
     * Opens a named region. Emitted by [flattenTestDataEntries] where the region starts, before
     * that element's own payloads, and closed by a matching [SubtreeEnd] after the element's entire
     * descendant range.
     */
    data class Subtree(val id: String) : TestDataEntry

    /**
     * Closes the [Subtree] with the same [id].
     *
     * Synthesized by [flattenTestDataEntries] on the way back out, so a consumer can map opens and
     * closes onto its own bracketing without tracking depth itself. Authoring one directly is a
     * misuse: it is published and flattened like any other entry, which puts an unmatched close in
     * the stream.
     */
    data class SubtreeEnd(val id: String) : TestDataEntry
}

/**
 * All test-data entries contributed to one layout node, **outermost modifier first**.
 *
 * A single list-valued key rather than one key per payload key: per-key properties collapse onto
 * the shared [SemanticsConfiguration] and would lose both the per-call granularity and the
 * marker/payload interleaving within a chain.
 */
val TestDataEntries: SemanticsPropertyKey<List<TestDataEntry>> =
    SemanticsPropertyKey("androidx.compose.ui.semantics.testDataEntries")

/**
 * Whether [Modifier.testDataEntry] contributes anything.
 *
 * Off by default. A host that runs UI tests turns it on at application init.
 *
 * Test data is published through Compose semantics, so a changed value invalidates its layout
 * node's semantics configuration. Payloads that are not stable — a raw `LayoutCoordinates`
 * republished per layout for every visible tree cell, a freshly allocated lambda per recomposition
 * — would make that cost real in production, where test data has no consumer at all. With the flag
 * off the modifier is the identity, which removes the cost entirely.
 *
 * Read at modifier-construction time, so it must be set before the first composition that uses it.
 * Changing it afterwards affects only modifiers constructed later.
 *
 * **Stricter constraint in practice:** window backends gate their `SemanticsOwnerListener` on this
 * flag — `get() = if (TestDataMode.isEnabled) this else null` — and owner registration is
 * event-driven and happens exactly once per owner: a scene's main owner registers when the scene is
 * constructed, and each layer registers on attach and unregisters on close. There is no
 * re-registration path. That means this flag must be correct *before any window or scene layer is
 * constructed*, which is stricter than "before the first composition": flipping it to `true` after
 * a window has already attached leaves that window's entire subtree permanently invisible, because
 * `onSemanticsOwnerAppended` already fired with a `null` listener for that owner and is never
 * retried. Conversely, flipping it to `false` while a layer is live and then letting that layer
 * detach leaves a stale [SemanticsOwner] registered forever, since `onSemanticsOwnerRemoved` will
 * also observe a `null` listener at that point.
 */
object TestDataMode {
    var isEnabled: Boolean = false
}

/**
 * Attaches [entry] to this element, to be read back through [flattenTestDataEntries].
 *
 * Returns the receiver unchanged when [TestDataMode.isEnabled] is `false`.
 */
fun Modifier.testDataEntry(entry: TestDataEntry): Modifier =
    if (!TestDataMode.isEnabled) this else this then TestDataElement(entry)

internal data class TestDataElement(val entry: TestDataEntry) :
    ModifierNodeElement<TestDataNode>() {
    override fun create(): TestDataNode = TestDataNode(entry)

    override fun update(node: TestDataNode) {
        node.entry = entry
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "testDataEntry"
        properties["entry"] = entry
    }
}

internal class TestDataNode(entry: TestDataEntry) : Modifier.Node(), SemanticsModifierNode {

    /**
     * Snapshot state on purpose. [applySemantics] runs inside
     * `LayoutNode.calculateSemanticsConfiguration`'s `observeSemanticsReads`, so reading this here
     * puts semantics invalidation under precise snapshot observation instead of relying on element
     * replacement.
     */
    var entry: TestDataEntry by mutableStateOf(entry)

    /**
     * Test data must never influence which modifier determines a layout node's semantics bounds.
     * The interface default is `true`.
     */
    override val isImportantForBounds: Boolean get() = false

    override fun SemanticsPropertyReceiver.applySemantics() {
        this[TestDataEntries] = this@TestDataNode.localChainEntries()
    }
}

/**
 * Every entry contributed to this node's layout node, outermost modifier first.
 *
 * [SemanticsPropertyReceiver] is write-only, so entries cannot be accumulated across the chain by
 * read-modify-write. Instead every peer publishes the same complete list, which makes the result
 * independent of `LayoutNode.calculateSemanticsConfiguration`'s tail-to-head fold order. `n` is at
 * most three in practice.
 *
 * Reading peers' [TestDataNode.entry] here also establishes snapshot observations on them, which is
 * what makes a peer's change invalidate the whole published list.
 *
 * This is the reason the mechanism lives in `compose:ui:ui` rather than in the module that owns the
 * typed key vocabulary: `visitLocalAncestors`, `visitLocalDescendants` and [Nodes] are `internal`
 * and have no public substitute — `traverseAncestors` crosses layout nodes, so it cannot be scoped
 * to one node's own modifier chain.
 */
private fun TestDataNode.localChainEntries(): List<TestDataEntry> = buildList {
    // visitLocalAncestors walks toward the chain head, i.e. outward, so reverse it.
    val outerFirst = ArrayDeque<TestDataEntry>()
    visitLocalAncestors(Nodes.Semantics.mask) {
        if (it is TestDataNode) outerFirst.addFirst(it.entry)
    }
    addAll(outerFirst)
    add(entry)
    // visitLocalDescendants walks toward the tail, i.e. inward, already in order.
    visitLocalDescendants(Nodes.Semantics.mask) {
        if (it is TestDataNode) add(it.entry)
    }
}

/**
 * This owner's entries as a flat, depth-first pre-order stream.
 *
 * For each node: every entry in outermost-first modifier order — so a [TestDataEntry.Subtree]
 * written before a payload opens before it — then the node's entire subtree, then a
 * [TestDataEntry.SubtreeEnd] for each [TestDataEntry.Subtree] in reverse. That is what makes
 * `.testDataEntry(Subtree("x")).testDataEntry(Payload(k, v))` put the payload inside `x` while the
 * reverse chain puts it outside.
 *
 * The walk is over the **unmerged** tree, so a payload is never absorbed into a merging ancestor.
 *
 * Call the [Collection] overload when reading more than one owner. Looping this one over a live
 * owner set — a window's, say — re-creates the `ConcurrentModificationException` that overload
 * exists to prevent.
 */
fun SemanticsOwner.flattenTestDataEntries(): Sequence<TestDataEntry> =
    flattenTestDataEntries(unmergedRootSemanticsNode)

/**
 * Every owner's entries, concatenated in iteration order, so that layer-based popups are visible.
 *
 * The receiver is snapshotted eagerly, before the [Sequence] is built. A caller typically passes a
 * `SnapshotStateSet` that the window mutates as scene layers attach and detach; iterating it lazily
 * from inside the sequence would hold its iterator open across yields and throw
 * `ConcurrentModificationException` the moment a layer arrived mid-consumption. Snapshotting also
 * keeps repeated iteration of the returned sequence consistent — it is re-iterable, and every pass
 * sees the same owners.
 */
fun Collection<SemanticsOwner>.flattenTestDataEntries(): Sequence<TestDataEntry> {
    val owners = toList()
    return sequence {
        for (owner in owners) {
            yieldAll(owner.flattenTestDataEntries())
        }
    }
}

private fun flattenTestDataEntries(node: SemanticsNode): Sequence<TestDataEntry> = sequence {
    val entries = node.config.getOrNull(TestDataEntries).orEmpty()
    yieldAll(entries)
    for (child in node.children) {
        yieldAll(flattenTestDataEntries(child))
    }
    for (entry in entries.asReversed()) {
        if (entry is TestDataEntry.Subtree) {
            yield(TestDataEntry.SubtreeEnd(entry.id))
        }
    }
}
