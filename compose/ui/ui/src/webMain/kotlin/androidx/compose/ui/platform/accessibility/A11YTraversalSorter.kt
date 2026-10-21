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

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.UnmergedConfigComparator
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import org.w3c.dom.HTMLElement

/** Orders the target DOM while retaining parents whose descendants stay contiguous. */
internal class A11YTraversalSorter(
    private val parentToChildren: MutableScatterMap<HTMLElement, MutableList<HTMLElement>>,
    private val childToParent: MutableScatterMap<HTMLElement, HTMLElement>,
    private val semanticsNodes: ScatterMap<HTMLElement, SemanticsNode>,
) {
    fun sort(root: HTMLElement, onReparent: (HTMLElement, HTMLElement) -> Unit) {
        val rootNode = semanticsNodes[root] ?: return
        // Text fragments and links must remain interleaved in text order.
        if (rootNode.config.hasNonEditableText()) return
        val children = parentToChildren[root] ?: return
        val candidates = mutableListOf<TraversalItem>()
        val transparentChildren = MutableScatterMap<HTMLElement, List<HTMLElement>>()

        fun collect(element: HTMLElement) {
            val node = semanticsNodes[element] ?: return
            val descendants = parentToChildren[element]
            if (!node.unmergedConfig.isTransparentForTraversal()) {
                candidates.add(TraversalItem(node, element))
                sort(element, onReparent)
            } else {
                // Empty decorative nodes must not affect the geometry grouping either.
                transparentChildren[element] = descendants.orEmpty()
                descendants?.fastForEach { collect(it) }
            }
        }
        children.fastForEach { collect(it) }

        val sorted = rootNode.sortTraversalItems(candidates)
        if (candidates.indices.all { candidates[it] === sorted[it] }) return

        val ranges = MutableScatterMap<HTMLElement, TraversalRange>()
        sorted.forEachIndexed { index, item ->
            ranges[item.element] = TraversalRange(index, index, 1)
        }

        fun rangeOf(element: HTMLElement): TraversalRange {
            ranges[element]?.let { return it }
            var first = Int.MAX_VALUE
            var last = Int.MIN_VALUE
            var count = 0
            transparentChildren[element]!!.fastForEach { child ->
                val range = rangeOf(child)
                first = minOf(first, range.first)
                last = maxOf(last, range.last)
                count += range.count
            }
            return TraversalRange(first, last, count).also { ranges[element] = it }
        }
        children.fastForEach { rangeOf(it) }

        val reorderedChildren = MutableScatterMap<HTMLElement, MutableList<HTMLElement>>()
        reorderedChildren[root] = mutableListOf()

        fun place(element: HTMLElement, parent: HTMLElement) {
            reorderedChildren.getOrPut(parent) { mutableListOf() }.add(element)
            if (childToParent[element] !== parent) {
                childToParent[element] = parent
                onReparent(element, parent)
            }

            transparentChildren[element]?.let { descendants ->
                reorderedChildren[element] = mutableListOf()
                val range = ranges[element]!!
                // A non-group wrapper cannot contain interleaved traversal items: move those
                // children up so DOM order can follow the sorted order across its siblings.
                val childrenParent = if (range.count == 0 || range.last - range.first + 1 == range.count) {
                    element
                } else {
                    parent
                }
                descendants.fastForEach { place(it, childrenParent) }
            }
        }
        children.fastForEach { place(it, root) }
        reorderedChildren.forEach { parent, reordered ->
            reordered.sortBy { ranges[it]!!.first }
            parentToChildren[parent] = reordered
        }
    }

    private class TraversalRange(val first: Int, val last: Int, val count: Int)
}

private class TraversalItem(val node: SemanticsNode, val element: HTMLElement) {
    // Offscreen scroll content remains reachable on Web, so it must be sorted by its full bounds.
    val bounds = Rect(node.positionInWindow, node.size.toSize())
}

private class TraversalRow(
    var top: Float,
    var bottom: Float,
    val items: MutableList<TraversalItem>,
)

/** Matches semantics geometry grouping, using unclipped bounds for scrollable content. */
private fun SemanticsNode.sortTraversalItems(items: List<TraversalItem>): List<TraversalItem> {
    val rows = mutableListOf<TraversalRow>()
    items.fastForEach { item ->
        val bounds = item.bounds
        val row = rows.firstOrNull {
            maxOf(it.top, bounds.top) < minOf(it.bottom, bounds.bottom)
        }
        if (row == null) {
            rows.add(TraversalRow(bounds.top, bounds.bottom, mutableListOf(item)))
        } else {
            row.top = maxOf(row.top, bounds.top)
            row.bottom = minOf(row.bottom, bounds.bottom)
            row.items.add(item)
        }
    }
    rows.sortWith(compareBy<TraversalRow> { it.top }.thenBy { it.bottom })

    val rtl = layoutInfo.layoutDirection == LayoutDirection.Rtl
    val comparator = compareBy<TraversalItem> { if (rtl) -it.bounds.right else it.bounds.left }
        .thenBy { it.bounds.top }
        .thenBy { it.bounds.bottom }
        .thenBy { if (rtl) -it.bounds.left else it.bounds.right }
        .thenBy(LayoutNode.ZComparator) { it.node.layoutNode }
        .thenBy { it.node.id }

    val sorted = mutableListOf<TraversalItem>()
    rows.fastForEach { row ->
        row.items.sortWith(comparator)
        sorted.addAll(row.items)
    }
    sorted.sortWith { a, b -> UnmergedConfigComparator(a.node, b.node) }
    return sorted
}

@Suppress("DEPRECATION")
private fun SemanticsConfiguration.isTransparentForTraversal(): Boolean =
    // Material surfaces still use IsContainer. An explicit IsTraversalGroup takes precedence.
    (getOrNull(SemanticsProperties.IsTraversalGroup)
        ?: getOrNull(SemanticsProperties.IsContainer)) != true &&
        !isMergingSemanticsOfDescendants &&
        all {
            it.key == SemanticsProperties.IsTraversalGroup ||
                it.key == SemanticsProperties.IsContainer ||
                it.key == SemanticsProperties.TraversalIndex ||
                it.key == SemanticsProperties.TestTag ||
                it.key == SemanticsProperties.Shape ||
                it.key == SemanticsProperties.BackgroundColor
        }
