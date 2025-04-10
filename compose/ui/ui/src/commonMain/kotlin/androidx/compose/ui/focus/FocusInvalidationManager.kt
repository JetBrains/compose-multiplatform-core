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

package androidx.compose.ui.focus

import androidx.collection.MutableScatterSet
import androidx.collection.mutableScatterSetOf
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusStateImpl.ActiveParent
import androidx.compose.ui.focus.FocusStateImpl.Inactive
import androidx.compose.ui.internal.checkPrecondition
import androidx.compose.ui.node.Nodes
import androidx.compose.ui.node.visitAncestors
import androidx.compose.ui.node.visitSelfAndChildren
import androidx.compose.ui.util.fastForEach

/**
 * The [FocusInvalidationManager] allows us to schedule focus related nodes for invalidation. These
 * nodes are invalidated after onApplyChanges. It does this by registering an onApplyChangesListener
 * when nodes are scheduled for invalidation.
 */
internal class FocusInvalidationManager(
    private val onRequestApplyChangesListener: (() -> Unit) -> Unit,
    private val invalidateOwnerFocusState: () -> Unit,
    private val rootFocusStateFetcher: () -> FocusState,
    private val activeFocusTargetNodeFetcher: () -> FocusTargetNode?
) {
    private val focusTargetNodes = mutableScatterSetOf<FocusTargetNode>()
    private val focusEventNodes = mutableScatterSetOf<FocusEventModifierNode>()
    private val focusTargetNodesLegacy = mutableListOf<FocusTargetNode>()
    private val focusEventNodesLegacy = mutableListOf<FocusEventModifierNode>()
    private val focusPropertiesNodesLegacy = mutableListOf<FocusPropertiesModifierNode>()
    private val focusTargetsWithInvalidatedFocusEventsLegacy = mutableListOf<FocusTargetNode>()

    private var isInvalidationScheduled = false

    fun scheduleInvalidation(node: FocusTargetNode) {
        if (@OptIn(ExperimentalComposeUiApi::class) ComposeUiFlags.isTrackFocusEnabled) {
            focusTargetNodes.scheduleInvalidation(node)
        } else {
            focusTargetNodesLegacy.scheduleInvalidationLegacy(node)
        }
    }

    fun scheduleInvalidation(node: FocusEventModifierNode) {
        if (@OptIn(ExperimentalComposeUiApi::class) ComposeUiFlags.isTrackFocusEnabled) {
            focusEventNodes.scheduleInvalidation(node)
        } else {
            focusEventNodesLegacy.scheduleInvalidationLegacy(node)
        }
    }

    fun scheduleInvalidation(node: FocusPropertiesModifierNode) {
        focusPropertiesNodesLegacy.scheduleInvalidationLegacy(node)
    }

    fun scheduleInvalidationForOwner() {
        setUpOnRequestApplyChangesListener()
    }

    fun hasPendingInvalidation(): Boolean {
        return if (@OptIn(ExperimentalComposeUiApi::class) ComposeUiFlags.isTrackFocusEnabled) {
            isInvalidationScheduled
        } else {
            focusTargetNodesLegacy.isNotEmpty() ||
                focusPropertiesNodesLegacy.isNotEmpty() ||
                focusEventNodesLegacy.isNotEmpty()
        }
    }

    private fun <T> MutableScatterSet<T>.scheduleInvalidation(node: T) {
        if (add(node)) {
            setUpOnRequestApplyChangesListener()
        }
    }

    private fun setUpOnRequestApplyChangesListener() {
        if (!isInvalidationScheduled) {
            onRequestApplyChangesListener.invoke(::invalidateNodes)
            isInvalidationScheduled = true
        }
    }

    private fun <T> MutableList<T>.scheduleInvalidationLegacy(node: T) {
        if (add(node)) {
            // If this is the first node scheduled for invalidation,
            // we set up a listener that runs after onApplyChanges.
            if (
                focusTargetNodesLegacy.size +
                    focusEventNodesLegacy.size +
                    focusPropertiesNodesLegacy.size == 1
            ) {
                onRequestApplyChangesListener.invoke(::invalidateNodes)
            }
        }
    }

    private fun invalidateNodes() {
        if (@OptIn(ExperimentalComposeUiApi::class) ComposeUiFlags.isTrackFocusEnabled) {
            invalidateNodesOptimized()
        } else {
            invalidateNodesLegacy()
        }
    }

    private fun invalidateNodesOptimized() {
        val activeFocusTargetNode = activeFocusTargetNodeFetcher()
        if (activeFocusTargetNode == null) {
            // If there is no active focus node, dispatch the Inactive state to event nodes.
            focusEventNodes.forEach { it.onFocusEvent(Inactive) }
        } else if (activeFocusTargetNode.isAttached) {
            if (focusTargetNodes.contains(activeFocusTargetNode)) {
                activeFocusTargetNode.invalidateFocus()
            }

            val activeFocusTargetNodeState = activeFocusTargetNode.focusState
            var traversedFocusTargetCount = 0
            activeFocusTargetNode.visitAncestors(
                Nodes.FocusTarget or Nodes.FocusEvent,
                includeSelf = true
            ) {
                // Keep track of whether we traversed past the first target node ancestor of the
                // active focus target node, so that all the subsequent event nodes are sent the
                // ActiveParent state rather than Active/Captured.
                if (it.isKind(Nodes.FocusTarget)) traversedFocusTargetCount++

                // Don't send events to event nodes that were not invalidated.
                if (it !is FocusEventModifierNode || !focusEventNodes.contains(it)) {
                    return@visitAncestors
                }

                // Event nodes that are between the active focus target and the first ancestor
                // target receive the Active/Captured state, while the event nodes further up
                // receive the ActiveParent state.
                if (traversedFocusTargetCount <= 1) {
                    it.onFocusEvent(activeFocusTargetNodeState)
                } else {
                    it.onFocusEvent(ActiveParent)
                }

                // Remove the event node from the list of invalidated nodes, so that we only send a
                // single event per node.
                focusEventNodes.remove(it)
            }

            // Send the Inactive state to the event nodes that are not in the active node ancestors.
            focusEventNodes.forEach { it.onFocusEvent(Inactive) }
        }

        invalidateOwnerFocusState()
        focusTargetNodes.clear()
        focusEventNodes.clear()
        isInvalidationScheduled = false
    }

    private fun invalidateNodesLegacy() {
        if (!rootFocusStateFetcher().hasFocus) {
            // If root doesn't have focus, skip full invalidation and default to the Inactive state.
            focusEventNodesLegacy.fastForEach { it.onFocusEvent(Inactive) }
            focusTargetNodesLegacy.fastForEach { node ->
                if (node.isAttached && !node.isInitialized()) {
                    node.initializeFocusState(Inactive)
                }
            }
            focusTargetNodesLegacy.clear()
            focusEventNodesLegacy.clear()
            focusPropertiesNodesLegacy.clear()
            focusTargetsWithInvalidatedFocusEventsLegacy.clear()
            invalidateOwnerFocusState()
            return
        }

        // Process all the invalidated FocusProperties nodes.
        focusPropertiesNodesLegacy.fastForEach {
            // We don't need to invalidate a focus properties node if it was scheduled for
            // invalidation earlier in the composition but was then removed.
            if (!it.node.isAttached) return@fastForEach

            it.visitSelfAndChildren(Nodes.FocusTarget) { focusTarget ->
                focusTargetNodesLegacy.add(focusTarget)
            }
        }
        focusPropertiesNodesLegacy.clear()

        // Process all the focus events nodes.
        focusEventNodesLegacy.fastForEach { focusEventNode ->
            // When focus nodes are removed, the corresponding focus events are scheduled for
            // invalidation. If the focus event was also removed, we don't need to invalidate it.
            // We call onFocusEvent with the default value, just to make it easier for the user,
            // so that they don't have to keep track of whether they caused a focused item to be
            // removed (Which would cause it to lose focus).
            if (!focusEventNode.node.isAttached) {
                focusEventNode.onFocusEvent(Inactive)
                return@fastForEach
            }

            var requiresUpdate = true
            var aggregatedNode = false
            var focusTarget: FocusTargetNode? = null
            focusEventNode.visitSelfAndChildren(Nodes.FocusTarget) {

                // If there are multiple focus targets associated with this focus event node,
                // we need to calculate the aggregated state.
                if (focusTarget != null) {
                    aggregatedNode = true
                }

                focusTarget = it

                // If the associated focus node is already scheduled for invalidation, it will
                // send an onFocusEvent if the invalidation causes a focus state change.
                // However this onFocusEvent was invalidated, so we have to ensure that we call
                // onFocusEvent even if the focus state didn't change.
                if (it in focusTargetNodesLegacy) {
                    requiresUpdate = false
                    focusTargetsWithInvalidatedFocusEventsLegacy.add(it)
                    return@visitSelfAndChildren
                }
            }

            if (requiresUpdate) {
                focusEventNode.onFocusEvent(
                    if (aggregatedNode) {
                        focusEventNode.getFocusState()
                    } else {
                        focusTarget?.focusState ?: Inactive
                    }
                )
            }
        }
        focusEventNodesLegacy.clear()

        // Process all the focus target nodes.
        focusTargetNodesLegacy.fastForEach {
            // We don't need to invalidate the focus target if it was scheduled for invalidation
            // earlier in the composition but was then removed.
            if (!it.isAttached) return@fastForEach

            val preInvalidationState = it.focusState
            it.invalidateFocus()
            if (
                preInvalidationState != it.focusState ||
                    it in focusTargetsWithInvalidatedFocusEventsLegacy
            ) {
                it.dispatchFocusCallbacks()
            }
        }
        focusTargetNodesLegacy.clear()
        // Clear the set so we can reuse it
        focusTargetsWithInvalidatedFocusEventsLegacy.clear()

        invalidateOwnerFocusState()

        checkPrecondition(focusPropertiesNodesLegacy.isEmpty()) {
            "Unprocessed FocusProperties nodes"
        }
        checkPrecondition(focusEventNodesLegacy.isEmpty()) { "Unprocessed FocusEvent nodes" }
        checkPrecondition(focusTargetNodesLegacy.isEmpty()) { "Unprocessed FocusTarget nodes" }
    }
}
