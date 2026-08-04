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

package noria.ui.core

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
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * One contribution of a [Modifier.testData] or [Modifier.markTestSubtree] call.
 *
 * Noria emitted one childless meta node per call, so the flattened stream had one entry per node.
 * That granularity is load-bearing: [filterAllTestNodesByPath] classifies a node as enter, exit or
 * payload in a mutually exclusive `when`, and a node carrying both a marker and a payload would
 * silently lose the payload.
 */
sealed interface TestDataEntry {
    data class Payload<T : Any>(val key: TestDataKey<T>, val value: T) : TestDataEntry

    data class Subtree(val id: String) : TestDataEntry
}

/**
 * All test-data entries contributed to one layout node, **outermost modifier first**.
 *
 * A single list-valued key rather than one key per [TestDataKey]: per-key properties collapse onto
 * the shared [androidx.compose.ui.semantics.SemanticsConfiguration] and would lose both the
 * per-call granularity and the marker/payload interleaving within a chain.
 */
val TestDataEntries: SemanticsPropertyKey<List<TestDataEntry>> =
    SemanticsPropertyKey("noria.ui.core.testData")

internal data class TestDataElement(val entry: TestDataEntry) :
    ModifierNodeElement<TestDataNode>() {
    override fun create(): TestDataNode = TestDataNode(entry)

    override fun update(node: TestDataNode) {
        node.entry = entry
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "testData"
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
