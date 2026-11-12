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
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach

/**
 * Hoists children of accessibility-empty wrappers so they can be sorted with their siblings by
 * geometry. Traversal groups, nodes exposing accessibility information, and leaves are retained.
 *
 * Example with text aligned in rows in a left-to-right layout:
 * ```kotlin
 * Row {
 *     Column(Modifier.testTag("left")) {
 *         Text("A1")
 *         Text("A2")
 *     }
 *     Column(Modifier.testTag("right")) {
 *         Text("B1")
 *         Text("B2")
 *     }
 * }
 * ```
 * Without traversal groups: columns are flattened; subsequent geometry sorting gives row order.
 * Simplified HTML under the nearest exposed ancestor:
 * ```html
 * <div>A1</div>
 * <div>B1</div>
 * <div>A2</div>
 * <div>B2</div>
 * ```
 *
 * With `Modifier.semantics { isTraversalGroup = true }` on both columns: groups are retained,
 * giving column order:
 * ```html
 * <div id="left">
 *     <div>A1</div>
 *     <div>A2</div>
 * </div>
 * <div id="right">
 *     <div>B1</div>
 *     <div>B2</div>
 * </div>
 * ```
 *
 * [hoistedReachabilities] preserves the reachability inherited through removed wrappers.
 */
internal fun List<SemanticsNode>.flattenA11YWrappers(
    parentReachability: A11YReachability,
    hoistedReachabilities: MutableIntObjectMap<A11YReachability>,
): List<SemanticsNode> {
    if (!fastAny { it.isA11YWrapper() }) return this

    val flattened = ArrayList<SemanticsNode>(size)

    fun collect(nodes: List<SemanticsNode>, reachability: A11YReachability, hoisted: Boolean) {
        nodes.fastForEach { node ->
            if (node.isA11YWrapper()) {
                collect(node.placedA11YChildren(), node.a11yReachability(reachability), hoisted = true)
            } else {
                if (hoisted) hoistedReachabilities[node.id] = reachability
                flattened.add(node)
            }
        }
    }

    collect(this, parentReachability, hoisted = false)
    return flattened
}

/**
 * "Wrapper" here means a structural semantics node that contains children but exposes nothing
 * meaningful to accessibility itself
 */
private fun SemanticsNode.isA11YWrapper(): Boolean =
    !isFake &&
        !unmergedConfig.getOrElse(SemanticsProperties.IsTraversalGroup) { false } &&
        !unmergedConfig.isMergingSemanticsOfDescendants &&
        !unmergedConfig.containsImportantForAccessibility() &&
        replacedChildren.isNotEmpty()

internal fun SemanticsNode.placedA11YChildren(): List<SemanticsNode> =
    replacedChildren.filter { child ->
        // Lazy lists prefetch upcoming items by composing and measuring them before placement.
        // These items have no position in the list yet, and a reused layout node may still contain
        // coordinates from its previous item. Exclude them so stale bounds cannot affect the order
        // or position of elements in the accessibility DOM. Placed offscreen items remain eligible.
        // Synthetic ("fake") semantics nodes expose a parent's role or content description using
        // the parent's bounds; their own layout nodes are not attached or placed.
        child.isFake || child.layoutNode.let { it.isAttached && it.isPlaced }
    }
