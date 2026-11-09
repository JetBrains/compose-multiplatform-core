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

import androidx.collection.IntObjectMap
import androidx.collection.intObjectMapOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.min

/**
 * Web port of `SemanticsNode.sortByGeometryGroupings` from
 * `compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/semantics/SemanticsSort.kt`.
 *
 * Uses [boundsOf] throughout row grouping and comparison. Web needs unclipped bounds
 * for offscreen nodes that remain reachable by scrolling, whose clipped bounds may be empty.
 *
 * TODO: Support caller-provided bounds in the common sorter, upstream it and reuse it here:
 * https://youtrack.jetbrains.com/issue/CMP-11119
 */
internal fun SemanticsNode.sortByGeometryGroupingsForWeb(
    parentListToSort: List<SemanticsNode>,
    isFocusableContainer: (SemanticsNode) -> Boolean = { false },
    containerChildrenMapping: IntObjectMap<List<SemanticsNode>> = intObjectMapOf(),
    boundsOf: (SemanticsNode) -> Rect,
): List<SemanticsNode> {
    val layoutIsRtl = layoutInfo.layoutDirection == LayoutDirection.Rtl
    val rowGroupings = ArrayList<Pair<Rect, MutableList<SemanticsNode>>>(parentListToSort.size / 2)

    for (entryIndex in 0..parentListToSort.lastIndex) {
        val currEntry = parentListToSort[entryIndex]
        if (entryIndex == 0 || !placedEntryRowOverlaps(rowGroupings, currEntry, boundsOf)) {
            rowGroupings.add(Pair(boundsOf(currEntry), mutableListOf(currEntry)))
        }
    }

    rowGroupings.sortWith(TopBottomBoundsComparator)

    val comparator =
        (if (layoutIsRtl) RtlBoundsComparator(boundsOf) else LtrBoundsComparator(boundsOf))
            .thenBy(LayoutNode.ZComparator) { it.layoutNode }
            .thenBy { it.id }
    val returnList = ArrayList<SemanticsNode>()
    rowGroupings.forEach { row ->
        row.second.sortWith(comparator)
        returnList.addAll(row.second)
    }

    returnList.sortWith { a, b ->
        a.unmergedConfig
            .getOrElse(SemanticsProperties.TraversalIndex) { 0f }
            .compareTo(b.unmergedConfig.getOrElse(SemanticsProperties.TraversalIndex) { 0f })
    }

    var i = 0
    while (i <= returnList.lastIndex) {
        val containerChildren = containerChildrenMapping[returnList[i].id]
        if (containerChildren != null) {
            if (!isFocusableContainer(returnList[i])) {
                returnList.removeAt(i)
            } else {
                i += 1
            }
            returnList.addAll(i, containerChildren)
            i += containerChildren.size
        } else {
            i += 1
        }
    }
    return returnList
}

private fun placedEntryRowOverlaps(
    rowGroupings: ArrayList<Pair<Rect, MutableList<SemanticsNode>>>,
    node: SemanticsNode,
    boundsOf: (SemanticsNode) -> Rect,
): Boolean {
    val entryBounds = boundsOf(node)
    val entryTopCoord = entryBounds.top
    val entryBottomCoord = entryBounds.bottom
    val entryIsEmpty = entryTopCoord >= entryBottomCoord

    for (currIndex in 0..rowGroupings.lastIndex) {
        val currRect = rowGroupings[currIndex].first
        val groupIsEmpty = currRect.top >= currRect.bottom
        val groupOverlapsEntry =
            !entryIsEmpty &&
                !groupIsEmpty &&
                max(entryTopCoord, currRect.top) < min(entryBottomCoord, currRect.bottom)

        if (groupOverlapsEntry) {
            val newRect =
                currRect.intersect(0f, entryTopCoord, Float.POSITIVE_INFINITY, entryBottomCoord)
            rowGroupings[currIndex] = Pair(newRect, rowGroupings[currIndex].second)
            rowGroupings[currIndex].second.add(node)
            return true
        }
    }
    return false
}

private class LtrBoundsComparator(
    private val boundsOf: (SemanticsNode) -> Rect,
) : Comparator<SemanticsNode> {
    override fun compare(a: SemanticsNode, b: SemanticsNode): Int {
        val ab = boundsOf(a)
        val bb = boundsOf(b)
        var result = ab.left.compareTo(bb.left)
        if (result != 0) return result
        result = ab.top.compareTo(bb.top)
        if (result != 0) return result
        result = ab.bottom.compareTo(bb.bottom)
        if (result != 0) return result
        return ab.right.compareTo(bb.right)
    }
}

private class RtlBoundsComparator(
    private val boundsOf: (SemanticsNode) -> Rect,
) : Comparator<SemanticsNode> {
    override fun compare(a: SemanticsNode, b: SemanticsNode): Int {
        val ab = boundsOf(a)
        val bb = boundsOf(b)
        var result = bb.right.compareTo(ab.right)
        if (result != 0) return result
        result = ab.top.compareTo(bb.top)
        if (result != 0) return result
        result = ab.bottom.compareTo(bb.bottom)
        if (result != 0) return result
        return bb.left.compareTo(ab.left)
    }
}

private object TopBottomBoundsComparator : Comparator<Pair<Rect, MutableList<SemanticsNode>>> {
    override fun compare(
        a: Pair<Rect, MutableList<SemanticsNode>>,
        b: Pair<Rect, MutableList<SemanticsNode>>,
    ): Int {
        val result = a.first.top.compareTo(b.first.top)
        if (result != 0) return result
        return a.first.bottom.compareTo(b.first.bottom)
    }
}

internal fun SemanticsNode.unclippedBoundsInWindowForWeb(): Rect =
    findCoordinatorToGetBounds()
        ?.takeIf { it.isAttached }
        ?.boundsInWindow(clipBounds = false)
        ?: Rect.Zero
