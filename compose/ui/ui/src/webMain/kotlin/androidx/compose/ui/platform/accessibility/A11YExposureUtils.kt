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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties

internal enum class A11YReachability {
    /* The node is not clipped and must be reachable by AT */
    DirectlyReachable,

    /* The node can be brought into view by scrolling, and it must remain reachable so AT can scroll to it too */
    ReachableByScroll,

    /* The node is fully clipped and AT must not interact with it */
    Unreachable,
    ;

    @Suppress("NOTHING_TO_INLINE")
    inline fun isReachable(): Boolean = this != Unreachable
}

/**
 * The reachability of an a11y node depends on:
 * - its ancestors' reachability - it automatically inherits `Unreachable` froms its ancestors
 * - its own clipping - fully clipped node must not be reachable
 * - whether one of its ancestors is a reachable scrollable container (then the node is reachable even if fully offscreen/clipped)
 */
internal fun SemanticsNode.a11yReachability(
    parentReachability: A11YReachability,
    config: SemanticsConfiguration = this.config,
): A11YReachability = when (parentReachability) {
    A11YReachability.Unreachable,
    A11YReachability.ReachableByScroll -> parentReachability // just inherit from the parent

    A11YReachability.DirectlyReachable -> {
        val isFullyClipped = size.width != 0 && size.height != 0 && boundsInRoot.isEmpty
        when {
            isFullyClipped -> A11YReachability.Unreachable
            config.isScrollContainer() -> A11YReachability.ReachableByScroll
            else -> parentReachability
        }
    }
}

/**
 * Returns true if this [SemanticsNode] acts as a container in the A11Y DOM tree, keeping its
 * descendants nested under its own HTML element instead of flattening them into the parent group.
 *
 * A node with its own A11Y semantics (e.g., `contentDescription`, role, scrollability, or text)
 * remains a DOM container while flattening any non-traversal layout wrappers inside it.
 */
internal fun SemanticsNode.isA11YContainerBoundary(
    config: SemanticsConfiguration = this.config,
): Boolean =
    unmergedConfig.getOrElse(SemanticsProperties.IsTraversalGroup) { false } ||
        unmergedConfig.isMergingSemanticsOfDescendants ||
        config.hasOwnA11YSemantics()

/**
 * Returns true if this [SemanticsConfiguration] carries semantics that are exposed on an A11Y DOM
 * element (excluding layout/ordering-only properties like [SemanticsProperties.IsTraversalGroup],
 * [SemanticsProperties.TraversalIndex], and [SemanticsProperties.TestTag]).
 */
internal fun SemanticsConfiguration.hasOwnA11YSemantics(): Boolean {
    val roleId = getRoleId()
    return roleId != AriaRoleId.Unknown ||
        getAriaLabel() != null ||
        getAriaLive(roleId) != null ||
        contains(SemanticsProperties.Text) ||
        contains(SemanticsProperties.EditableText) ||
        contains(SemanticsProperties.Disabled) ||
        contains(SemanticsProperties.Selected) ||
        contains(SemanticsProperties.ToggleableState) ||
        contains(SemanticsProperties.ProgressBarRangeInfo) ||
        contains(SemanticsProperties.IsDialog) ||
        contains(SemanticsActions.OnClick) ||
        isScrollContainer()
}