/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.wear.compose.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.VerticalPager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * [hierarchicalFocus] is used to coordinate focus within an app, which can have multiple screens
 * and/or layers within a screen, and updates focus as the user interacts with the app. In most
 * cases, this is automatically handled by Wear Compose components and no action is necessary.
 *
 * This is done automatically by [BasicSwipeToDismissBox], [HorizontalPager], [VerticalPager] and
 * PickerGroup. This modifier is useful if you implement a custom component that needs to direct
 * focus to one of several children, like a custom Pager, a Tabbed layout, etc.
 *
 * [hierarchicalFocus]s can be nested to form a focus tree, with an implicit root. Within the focus
 * tree, components that need to request focus can do so using [Modifier.hierarchicalFocusRequester]
 * (either providing their own [FocusRequester] and adding their own [Modifier.focusRequester], or
 * by using the parameter-less version) We call those nodes which have [hierarchicalFocusRequester]
 * leaves in the tree. Note that ScalingLazyColumn and TransformingLazyColumn is using it already,
 * so there is no need to be add it explicitly.
 *
 * When focus changes, the focus tree is examined and the leaf which has all its [hierarchicalFocus]
 * ancestors with focusEnabled = true will request focus. If no such leaf exists, the focus will be
 * cleared.
 *
 * Example usage:
 *
 * @sample androidx.wear.compose.foundation.samples.HierarchicalFocusSample
 *
 * Sample using nested [hierarchicalFocus]:
 *
 * @sample androidx.wear.compose.foundation.samples.HierarchicalFocus2Levels
 * @param focusEnabled Pass true when this sub tree of the focus tree is active and may require the
 *   focus - otherwise, pass false. For example, a pager can apply this modifier to each page's
 *   content with a call to [hierarchicalFocus], marking only the current page as focus enabled.
 * @param onFocusChange optional, a lambda to be invoked when the focus state on this branch of the
 *   focus tree changes.
 */
public fun Modifier.hierarchicalFocus(
    focusEnabled: Boolean,
    onFocusChange: ((Boolean) -> Unit)? = null
): Modifier {
    return this.then(
        HierarchicalFocusCoordinatorModifierElement(
            focusEnabled = focusEnabled,
            activeFocus = false,
            onFocusChange
        )
    )
}

/**
 * This Modifier defines leaf nodes in the focus tree defined by [hierarchicalFocus].
 *
 * This modifier will request focus on the provided [FocusRequester] when this is the leaf with all
 * its ancestor [hierarchicalFocus] nodes with focusEnabled = true. If you want to observe changes
 * to the focus state of the focus tree, use the onFocusChange parameter on [hierarchicalFocus]
 *
 * The parameterless [hierarchicalFocusRequester] is a convenient alternative that avoids the need
 * to both define a focus requester yourself then add the [focusRequester] modifier.
 *
 * @param focusRequester the [FocusRequester] to request focus on.
 */
public fun Modifier.hierarchicalFocusRequester(focusRequester: FocusRequester) =
    this.then(
        HierarchicalFocusCoordinatorModifierElement(focusEnabled = true, activeFocus = true) {
            if (it) focusRequester.requestFocus()
        }
    )

/**
 * This parameter-less overload of [hierarchicalFocusRequester] avoids the need to define a focus
 * requester yourself, and it also adds a [Modifier.focusRequester], given that it would not
 * otherwise be available outside of this modifier. If you need to share the [FocusRequester] with
 * other modifiers (typically [Modifier.rotaryScrollable]), use the overload of
 * [hierarchicalFocusRequester] with a focusRequester parameter and apply [Modifier.focusRequester]
 * if necessary.
 */
@Composable
public fun Modifier.hierarchicalFocusRequester() =
    remember { FocusRequester() }
        .let { focusRequester ->
            this.then(
                    HierarchicalFocusCoordinatorModifierElement(
                        focusEnabled = true,
                        activeFocus = true
                    ) {
                        if (it) focusRequester.requestFocus()
                    }
                )
                .focusRequester(focusRequester)
        }

@Deprecated(
    "Replaced by Modifier.hierarchicalFocusRequester(), use that instead",
    level = DeprecationLevel.WARNING // TODO: b/369332589 - Make hidden in a follow up cl
)
@Composable
public fun rememberActiveFocusRequester(): FocusRequester =
    remember { FocusRequester() }
        .also { focusRequester -> Box(Modifier.hierarchicalFocusRequester(focusRequester)) }

@Deprecated(
    "Replaced by Modifier.hierarchicalFocus(), use that instead",
    level = DeprecationLevel.WARNING // TODO: b/369332589 - Make hidden in a follow up cl
)
@Composable
public fun HierarchicalFocusCoordinator(
    requiresFocus: () -> Boolean,
    content: @Composable () -> Unit
) {
    Box(Modifier.hierarchicalFocus(requiresFocus())) { content() }
}

@Deprecated(
    "Replaced by Modifier.hierarchicalFocusRequester() or Modifier.hierarchicalFocus(), use that instead",
    level = DeprecationLevel.WARNING // TODO: b/369332589 - Make hidden in a follow up cl
)
@Composable
public fun ActiveFocusListener(onFocusChanged: CoroutineScope.(Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    Box(Modifier.hierarchicalFocus(true) { scope.launch { onFocusChanged(it) } })
}

private const val HFCTraversalKey = "HFCTraversalKey"

private class HierarchicalFocusCoordinatorModifierElement(
    val focusEnabled: Boolean,
    val activeFocus: Boolean,
    val onFocusChanged: ((Boolean) -> Unit)?,
) : ModifierNodeElement<HierarchicalFocusCoordinatorModifierNode>() {
    override fun create(): HierarchicalFocusCoordinatorModifierNode =
        HierarchicalFocusCoordinatorModifierNode(focusEnabled, activeFocus, onFocusChanged)

    override fun update(node: HierarchicalFocusCoordinatorModifierNode) {
        node.onFocusChanged = onFocusChanged
        node.activeFocus = activeFocus
        node.focusEnabled = focusEnabled
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "ModifierNodeElement"
    }

    override fun equals(other: Any?) =
        other is HierarchicalFocusCoordinatorModifierElement &&
            focusEnabled == other.focusEnabled &&
            activeFocus == other.activeFocus &&
            onFocusChanged === other.onFocusChanged

    override fun hashCode() =
        (focusEnabled.hashCode() * 31 + onFocusChanged.hashCode()) * 31 + activeFocus.hashCode()
}

private class HierarchicalFocusCoordinatorModifierNode(
    focusEnabled: Boolean,
    var activeFocus: Boolean,
    var onFocusChanged: ((Boolean) -> Unit)?
) : Modifier.Node(), TraversableNode, CompositionLocalConsumerModifierNode {
    override val traverseKey = HFCTraversalKey

    var focusEnabled: Boolean = focusEnabled
        set(value) {
            if (value != focusEnabled) {
                field = value
                scheduleUpdateAfterTreeSettles()
            }
        }

    override fun onAttach() {
        super.onAttach()
        scheduleUpdateAfterTreeSettles()
    }

    // We only need to call FocusManager.clearFocus when the focused node leaves the composition.
    @Suppress("SuspiciousCompositionLocalModifierRead")
    override fun onDetach() {
        super.onDetach()
        if (lastActiveNodePath.remove(this)) {
            // This was the part of the active node chain, and it's now leaving the composition.
            onFocusChanged?.invoke(false)
            // See if there is some other node that got focus, if not, call clearFocus.
            if (lastActiveNodePath.none { it.focusEnabled && it.isAttached && it.activeFocus })
                currentValueOf(LocalFocusManager).clearFocus()
        }
    }

    private fun scheduleUpdateAfterTreeSettles() {
        changedNodes.add(this)
        sideEffect {
            // Once all changes are applied, process the potential candidates and see if changes to
            // focus need to be made. We do this once, on the first sideEffect that triggers.
            if (changedNodes.isNotEmpty()) {
                // Ensure that the last active node is a candidate, since when we add/remove top
                // level HFCSelectors they will otherwise trigger the wrong callbacks (we will
                // reach the innermost block, with an empty nextActiveNodePath).
                lastActiveNodePath.lastOrNull()?.let { node -> changedNodes.add(node) }

                val parentActiveNodes =
                    changedNodes.fastFilter { it.isAttached && it.parentChainActive() }

                // We only care about changes in the active part of the tree.
                if (parentActiveNodes.isNotEmpty()) {
                    val nextActiveNodePath =
                        parentActiveNodes
                            .fastFirstOrNull { it.focusEnabled }
                            ?.findActive()
                            ?.parentChain() ?: emptyList()

                    if (nextActiveNodePath != lastActiveNodePath) {
                        // Note that we assume the lists to be small (less than 5 elements, if this
                        // proves not the be the case, we can do something fancier (like assigning
                        // ids to each node, sorting and merging)
                        var focusSet = false
                        nextActiveNodePath.fastForEach { node ->
                            if (!lastActiveNodePath.contains(node)) {
                                // Gaining focus
                                node.onFocusChanged?.invoke(true)
                                if (node.activeFocus) focusSet = true
                            }
                        }

                        lastActiveNodePath.fastForEach { node ->
                            if (!nextActiveNodePath.contains(node)) {
                                // Losing focus
                                node.onFocusChanged?.invoke(false)
                            }
                        }

                        if (!focusSet) currentValueOf(LocalFocusManager).clearFocus()

                        lastActiveNodePath = nextActiveNodePath.toMutableList()
                    }
                }
                changedNodes.clear()
            }
        }
    }

    // Returns true iff all ancestors up to and including root have focus enabled,
    // not including this node.
    private fun parentChainActive(): Boolean {
        var node: HierarchicalFocusCoordinatorModifierNode? = this
        while (node != null) {
            node = node.findNearestAncestor()
            if (node?.focusEnabled == false) return false
        }
        return true
    }

    // Returns the path of nodes to the given one.
    private fun parentChain(): List<HierarchicalFocusCoordinatorModifierNode> =
        (findNearestAncestor()?.parentChain() ?: emptyList()) + this

    // Search on the subtree rooted at this node the first node that has focus enabled and has a
    // path of focus enabled nodes up to and including this node.
    // If this node has no children, it returns itself. If all children of this node have
    // focusEnabled == false, return null
    private fun findActive(): HierarchicalFocusCoordinatorModifierNode? {
        var hasChildren = false
        var activeNode: HierarchicalFocusCoordinatorModifierNode? = null
        traverseDescendants {
            hasChildren = true
            if (it.focusEnabled) {
                activeNode = it.findActive()
                TraverseDescendantsAction.CancelTraversal
            } else {
                TraverseDescendantsAction.SkipSubtreeAndContinueTraversal
            }
        }
        return if (!hasChildren) return this else activeNode
    }

    companion object {
        private var lastActiveNodePath: MutableList<HierarchicalFocusCoordinatorModifierNode> =
            mutableListOf()
        private val changedNodes: MutableList<HierarchicalFocusCoordinatorModifierNode> =
            mutableListOf()
    }
}
