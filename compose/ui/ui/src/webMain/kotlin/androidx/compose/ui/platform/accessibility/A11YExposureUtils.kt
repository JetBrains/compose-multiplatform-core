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

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.NodeCoordinator
import androidx.compose.ui.node.Nodes
import androidx.compose.ui.node.requireCoordinator
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * A reachable DOM scroll viewport and the area its descendants can occupy before scrolling. Bounds
 * are in [coordinates], with only the available scroll directions extended. The other axes retain
 * the viewport's reachable portion, so partial overlap is sufficient.
 *
 * Web has no reliable equivalent of iOS accessibility focus. A container is reachable when it is
 * exposed, including through an outer scroll context. Waiting for DOM focus or a scroll event would
 * prevent AT from discovering the offscreen item needed to initiate scrolling.
 */
internal class A11YScrollContext(
    val coordinates: NodeCoordinator,
    private val reachableBounds: Rect,
) {
    fun reachableBoundsOf(source: LayoutCoordinates): Rect =
        // Stop BEFORE this viewport's own clip, but retain every intermediate clip, including
        // clips on layout nodes without semantics and deeper modifiers on this same LayoutNode.
        coordinates.localBoundingBoxOf(source, clipBounds = true).intersect(reachableBounds)
}

internal fun SemanticsNode.isHiddenForA11Y(scrollContext: A11YScrollContext?): Boolean {
    // Genuinely zero-sized semantics keep their existing exposure. A hidden ancestor is handled
    // separately by the traversal and still dominates this exception and visible descendants.
    if (size.width == 0 || size.height == 0 || !boundsInRoot.isEmpty) return false
    val coordinates = findCoordinatorToGetBounds() ?: return true
    return scrollContext == null || scrollContext.reachableBoundsOf(coordinates).isEmpty
}

internal fun SemanticsNode.createA11YScrollContext(
    config: SemanticsConfiguration,
    parentContext: A11YScrollContext?,
): A11YScrollContext? {
    if (!config.isDomA11YScrollContainer()) return null
    val coordinates = findScrollViewport() ?: return null
    val ancestor = parentContext?.coordinates ?: coordinates.findRootCoordinates()
    val bounds =
        parentContext?.reachableBoundsOf(coordinates)
            ?: ancestor.localBoundingBoxOf(coordinates, clipBounds = true)
    if (bounds.isEmpty) return A11YScrollContext(coordinates, Rect.Zero)

    // Transfer the reachable portion back into this viewport. In particular, an offscreen inner
    // scroller keeps the portion reachable through the outer scroller, rather than empty root
    // bounds.
    val transform = Matrix()
    coordinates.transformFrom(ancestor, transform)
    val viewport =
        transform
            .map(bounds)
            .intersect(
                Rect(0f, 0f, coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
            )
    if (viewport.isEmpty) return A11YScrollContext(coordinates, Rect.Zero)

    val horizontal = config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
    val vertical = config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
    return A11YScrollContext(
        coordinates,
        Rect(
            if (horizontal.canScroll(backwards = true)) Float.NEGATIVE_INFINITY else viewport.left,
            if (vertical.canScroll(backwards = true)) Float.NEGATIVE_INFINITY else viewport.top,
            if (horizontal.canScroll(backwards = false)) Float.POSITIVE_INFINITY
            else viewport.right,
            if (vertical.canScroll(backwards = false)) Float.POSITIVE_INFINITY else viewport.bottom,
        ),
    )
}

private fun ScrollAxisRange?.canScroll(backwards: Boolean): Boolean {
    if (this == null) return false
    // Lazy collections report estimated offsets, not exact pixel distances. Use the ranges for
    // direction availability, not as a pixel limit on which retained items can be discovered.
    return if (backwards != reverseScrolling) value() > 0f else value() < maxValue()
}

private fun SemanticsNode.findScrollViewport(): NodeCoordinator? =
    Snapshot.withoutReadObservation {
        // The node's bounds coordinator can be outside the scroll clip (e.g. lazy-list semantics).
        // Resolve the effective ScrollBy provider once per container/sync, never per descendant.
        // As in semantics collapsing, the outermost non-null action wins and clear semantics stops
        // the search. NodeChain traversal also visits delegated semantics modifiers.
        layoutNode.nodes.headToTail(Nodes.Semantics) { modifier ->
            val localConfig = SemanticsConfiguration()
            with(modifier) { localConfig.applySemantics() }
            if (localConfig.getOrNull(SemanticsActions.ScrollBy)?.action != null) {
                return@withoutReadObservation modifier.requireCoordinator(Nodes.Semantics)
            }
            if (modifier.shouldClearDescendantSemantics) {
                return@withoutReadObservation findCoordinatorToGetBounds()
            }
        }
        // A merged semantic node can inherit the scroll action from a descendant.
        findCoordinatorToGetBounds()
    }
