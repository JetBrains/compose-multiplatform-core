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

package androidx.compose.ui.platform.accessibility

import androidx.collection.MutableIntObjectMap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.util.fastForEach

/**
 * Plans the children of one DOM parent, flattening structural non-group wrappers while preserving
 * semantics ownership scopes. Calls [action] in reverse traversal order so a depth-first traversal
 * can push the nodes onto a stack. Reachability is inherited through any flattened wrappers.
 */
internal fun SemanticsNode.forEachA11YChildInReverseTraversalOrder(
    children: List<SemanticsNode>,
    parentReachability: A11YReachability,
    action: (node: SemanticsNode, parentReachability: A11YReachability, isContainer: Boolean) -> Unit,
) {
    if (children.isEmpty()) return

    val traversalNodes = MutableIntObjectMap<TraversalNode>()
    children.fastForEach { child ->
        traversalNodes[child.id] = TraversalNode(child, parentReachability)
    }
    // Unreachable nodes remain in the DOM as inert nodes. Placement filtering is done
    // before traversal so prefetched, unplaced lazy items cannot affect the ordering.
    val reversedChildren = subtreeSortedByGeometryGroupingForWeb(
        isFocusableContainer = { checkNotNull(traversalNodes[it.id]).isExposed },
        listToSort = children,
        boundsOf = { checkNotNull(traversalNodes[it.id]).bounds },
        childrenOf = { node ->
            val entry = checkNotNull(traversalNodes[node.id])
            if (entry.isContainer) {
                // This node owns a DOM subtree. Its children are planned once it is synced.
                emptyList()
            } else {
                node.placedA11YChildren().also { descendants ->
                    descendants.fastForEach { child ->
                        traversalNodes[child.id] = TraversalNode(child, entry.reachability)
                    }
                }
            }
        }
    ).asReversed()
    reversedChildren.fastForEach { child ->
        val entry = checkNotNull(traversalNodes[child.id])
        action(child, entry.parentReachability, entry.isContainer)
    }
}

internal fun SemanticsNode.placedA11YChildren(): List<SemanticsNode> =
    replacedChildren.filter { child ->
        // Prefetched lazy items may be attached and measured while retaining a reused slot's
        // old position. They must not participate in geometry sorting or DOM synchronization.
        // Fake semantics nodes have no attached layout node and use their parent's geometry.
        child.isFake || child.layoutNode.let { it.isAttached && it.isPlaced }
    }

/**
 * The common sorter produces a flat sequence. On web, nodes with their own semantics retain
 * their DOM subtree so ARIA state, actions, scrolling, and text/link ownership still apply to
 * their descendants. Structural non-group wrappers let their descendants join the outer sort.
 */
private class TraversalNode(
    node: SemanticsNode,
    val parentReachability: A11YReachability,
) {
    val reachability = node.a11yReachability(parentReachability)
    private val config = node.unmergedConfig
    // Test tags keep an identifiable DOM node, but do not make a non-group wrapper a scope.
    // Visual metadata from drawing modifiers does not create an accessibility scope either.
    private val hasOwnSemantics = config.any {
        it.key != SemanticsProperties.IsTraversalGroup &&
            it.key != SemanticsProperties.TraversalIndex &&
            it.key != SemanticsProperties.TestTag &&
            it.key != SemanticsProperties.Shape &&
            it.key != SemanticsProperties.BackgroundColor
    }
    val isExposed = node.isFake || config.isMergingSemanticsOfDescendants ||
        config.isClearingSemantics || hasOwnSemantics ||
        config.contains(SemanticsProperties.TestTag)
    val isContainer = node.isFake || config.isMergingSemanticsOfDescendants ||
        config.isClearingSemantics || hasOwnSemantics ||
        config.getOrElse(SemanticsProperties.IsTraversalGroup) { false } && isExposed
    val bounds: Rect by lazy(LazyThreadSafetyMode.NONE) {
        if (parentReachability == A11YReachability.ReachableByScroll) {
            node.unclippedBoundsInWindowForWeb()
        } else {
            node.boundsInWindow
        }
    }
}
