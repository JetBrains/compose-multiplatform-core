/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.ui.node

import androidx.collection.MutableObjectIntMap
import androidx.collection.mutableObjectIntMapOf
import androidx.compose.ui.internal.checkPrecondition

private val DepthComparator: Comparator<LayoutNode> =
    object : Comparator<LayoutNode> {
        override fun compare(a: LayoutNode, b: LayoutNode): Int {
            val depthDiff = a.depth.compareTo(b.depth)
            if (depthDiff != 0) {
                return depthDiff
            }
            return a.hashCode().compareTo(b.hashCode())
        }
    }

/**
 * The set of [LayoutNode]s which orders items by their [LayoutNode.depth] and allows
 * modifications(additions and removals) while we iterate through it via [popEach]. While
 * [LayoutNode] is added to the set it should always be:
 * 1) attached [LayoutNode.isAttached] == true
 * 2) maintaining the same [LayoutNode.depth] as any of this modifications can break the
 *    comparator's contract which can cause to not find the item in the tree set, which we
 *    previously added.
 */
internal class DepthSortedSet(private val extraAssertions: Boolean) {
    // stores the depth used when the node was added into the set so we can assert it wasn't
    // changed since then. we need to enforce this as changing the depth can break the contract
    // used in comparator for building the tree in TreeSet.
    // Created and used only when extraAssertions == true
    private var mapOfOriginalDepth: MutableObjectIntMap<LayoutNode>? = null

    private val set = SortedSet(DepthComparator)

    fun contains(node: LayoutNode): Boolean {
        val contains = set.contains(node)
        if (extraAssertions) {
            checkPrecondition(contains == safeMapOfOriginalDepth().containsKey(node)) {
                "inconsistency in TreeSet"
            }
        }
        return contains
    }

    fun add(node: LayoutNode) {
        checkPrecondition(node.isAttached) { "DepthSortedSet.add called on an unattached node" }
        if (extraAssertions) {
            val map = safeMapOfOriginalDepth()
            val usedDepth = map.getOrDefault(node, Int.MAX_VALUE)
            if (usedDepth == Int.MAX_VALUE) {
                map[node] = node.depth
            } else {
                checkPrecondition(usedDepth == node.depth) { "invalid node depth" }
            }
        }
        set.add(node)
    }

    fun remove(node: LayoutNode): Boolean {
        checkPrecondition(node.isAttached) { "DepthSortedSet.remove called on an unattached node" }
        val contains = set.remove(node)
        if (extraAssertions) {
            val map = safeMapOfOriginalDepth()
            if (map.contains(node)) {
                val usedDepth = map[node]
                map.remove(node)
                checkPrecondition(usedDepth == if (contains) node.depth else Int.MAX_VALUE) {
                    "invalid node depth"
                }
            }
        }
        return contains
    }

    fun pop(): LayoutNode {
        val node = set.first()
        remove(node)
        return node
    }

    inline fun popEach(crossinline block: (LayoutNode) -> Unit) {
        while (isNotEmpty()) {
            val node = pop()
            block(node)
        }
    }

    fun isEmpty(): Boolean = set.isEmpty()

    @Suppress("NOTHING_TO_INLINE") inline fun isNotEmpty(): Boolean = !isEmpty()

    private fun safeMapOfOriginalDepth(): MutableObjectIntMap<LayoutNode> {
        if (mapOfOriginalDepth == null) {
            mapOfOriginalDepth = mutableObjectIntMapOf()
        }
        return mapOfOriginalDepth!!
    }

    override fun toString(): String {
        return set.toString()
    }
}

internal class DepthSortedSetsForDifferentPasses(extraAssertions: Boolean) {
    /**
     * [outOfLookaheadScopeSet] contains nodes that are out of lookaheadScope.
     *
     * The invalidation order is always: [outOfLookaheadScopeSet] then [lookaheadSet] before
     * [approachSet].
     *
     * When no LookaheadScope is present in the tree, the latter two sets will be empty, all the
     * invalidations will come from nodes in the [outOfLookaheadScopeSet].
     */
    private val outOfLookaheadScopeSet = DepthSortedSet(extraAssertions)

    /** [lookaheadSet] contains nodes that require lookahead invalidation */
    private val lookaheadSet = DepthSortedSet(extraAssertions)

    /**
     * [approachSet] contains nodes that only requires approach invalidations. Only nodes in a
     * LookaheadScope can ever be put in this set.
     */
    private val approachSet = DepthSortedSet(extraAssertions)

    /**
     * Checks if the given node exists in the corresponding set based on the provided
     * [affectsLookahead].
     */
    fun contains(node: LayoutNode, affectsLookahead: Boolean): Boolean {
        val constainsInLookahead = lookaheadSet.contains(node)
        return if (affectsLookahead) {
            constainsInLookahead
        } else {
            constainsInLookahead ||
                outOfLookaheadScopeSet.contains(node) ||
                approachSet.contains(node)
        }
    }

    /** Checks if the node exists in either set. */
    fun contains(node: LayoutNode): Boolean =
        outOfLookaheadScopeSet.contains(node) ||
            lookaheadSet.contains(node) ||
            approachSet.contains(node)

    /**
     * Adds the given node to the corresponding set based on whether its lookahead
     * measurement/placement should be invalidated.
     *
     * Note: When [affectsLookahead] is true, both lookahead and main measure/layout will be
     * triggered as needed (i.e. if the FooPending flag is dirty). Otherwise, lookahead
     * remeasurement/relayout will be skipped.
     */
    fun add(node: LayoutNode, affectsLookahead: Boolean) {
        if (affectsLookahead) {
            lookaheadSet.add(node)
            approachSet.add(node)
        } else {
            if (node.lookaheadRoot != null) {
                // In a lookahead scope && the invalidation doesn't affect lookahead, therefore
                // the node needs to be added to approachSet.
                approachSet.add(node)
            } else {
                // Not in a lookahead scope
                outOfLookaheadScopeSet.add(node)
            }
        }
    }

    /** Remove the [node] from all the sets. */
    fun remove(node: LayoutNode): Boolean {
        val removedFromNonLookaheadSet = outOfLookaheadScopeSet.remove(node)
        val removedFromLookaheadSet = lookaheadSet.remove(node)
        return approachSet.remove(node) || removedFromLookaheadSet || removedFromNonLookaheadSet
    }

    /**
     * Pops nodes that require lookahead remeasurement/replacement first until the lookaheadSet is
     * empty, before handling nodes that only require invalidation for the main pass.
     */
    inline fun popEach(crossinline block: (node: LayoutNode, affectsLookahead: Boolean) -> Unit) {
        // Sequence for invalidation: outOfLookaheadScopeSet, lookaheadSet, approachSet
        while (true) {
            val affectsLookahead: Boolean
            val node =
                if (outOfLookaheadScopeSet.isNotEmpty()) {
                    affectsLookahead = false
                    outOfLookaheadScopeSet.pop()
                } else {
                    if (lookaheadSet.isNotEmpty()) {
                        affectsLookahead = true
                        lookaheadSet.pop()
                    } else if (approachSet.isNotEmpty()) {
                        affectsLookahead = false
                        approachSet.pop()
                    } else {
                        break
                    }
                }
            block(node, affectsLookahead)
        }
    }

    fun isEmpty(): Boolean =
        outOfLookaheadScopeSet.isEmpty() && approachSet.isEmpty() && lookaheadSet.isEmpty()

    fun isEmpty(affectsLookahead: Boolean): Boolean =
        if (affectsLookahead) lookaheadSet.isEmpty()
        else outOfLookaheadScopeSet.isEmpty() && approachSet.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()
}
