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

package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.indirect.IndirectPointerInputChange
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.traverseAncestors

/**
 * Creates a [DelegatableNode] that attaches [gestureCoordinator] to allow high level gesture
 * coordination. A [gestureCoordinator] can be used by nodes that perform input handling (e.g. use
 * [androidx.compose.ui.node.PointerInputModifierNode] to allow further communication between
 * complex high level gestures.
 *
 * @param gestureCoordinator The [GestureCoordinator] to attach to the nodes tree.
 */
internal fun gestureNode(gestureCoordinator: GestureCoordinator): DelegatableNode =
    GestureNode(gestureCoordinator)

/** Allows high level gesture coordination between nodes that perform input handling. */
internal interface GestureCoordinator {
    /**
     * Allows this node to demonstrate interest over a pointer event change. Interest signals that a
     * node is interested in this event, and it needs additional information (e.g. time or more
     * events) in order to decide if it will consume it. At a given moment in a node's gesture
     * recognition process they can query their parent or children to see if they're also interested
     * in this specific change. If so they can decide to give priority to the other node in the
     * chain.
     *
     * @param event The [PointerInputChange] this node will react to.
     */
    fun isInterested(event: PointerInputChange): Boolean = false

    /**
     * Allows this node to demonstrate interest over an indirect pointer event change. Interest
     * signals that a node is interested in this event, and it needs additional information (e.g.
     * time or more events) in order to decide if it will consume it. At a given moment in a node's
     * gesture recognition process they can query their parent or children to see if they're also
     * interested in this specific change. If so they can decide to give priority to the other node
     * in the chain.
     *
     * @param event The [IndirectPointerInputChange] this node will react to.
     */
    fun isInterested(event: IndirectPointerInputChange): Boolean = false
}

/** Searches the tree for a parent [GestureCoordinator]. Returns null if there isn't one. */
internal val DelegatableNode.parentGestureCoordinator: GestureCoordinator?
    get() = (findNearestAncestor(GestureNode.TraverseKey) as? GestureNode)?.gestureCoordinator

/**
 * Executes [block] for all ancestors with registered gesture coordinators.
 *
 * Note: The parameter [block]'s return boolean value will determine if the traversal will continue
 * (true = continue, false = cancel).
 */
internal fun DelegatableNode.traverseAncestorGestureCoordinators(
    block: (GestureCoordinator) -> Boolean
) {
    traverseAncestors(GestureNode.TraverseKey) { node ->
        check(node is GestureNode) { "Node is not a GestureNode instance" }
        block(node.gestureCoordinator)
    }
}

/** Represents a Node that interprets and process gesture data. */
private class GestureNode(val gestureCoordinator: GestureCoordinator) :
    TraversableNode, DelegatableNode, Modifier.Node() {
    override val traverseKey: Any
        get() = TraverseKey

    companion object TraverseKey
}
