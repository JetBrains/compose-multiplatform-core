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

package androidx.compose.ui.viewinterop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutNode
import androidx.compose.ui.platform.InspectorInfo

/**
 * Add this modifier directly to a [UIKitView] or [UIKitViewController] component to request
 * remeasurement of the underlying interop view.
 *
 * A single requester can be used with multiple [UIKitView] or [UIKitViewController] nodes.
 * Calling [InteropRemeasureRequester.requestRemeasure] will remeasure all currently
 * registered targets.
 *
 * Applying this modifier to nodes other than [UIKitView] or [UIKitViewController] is a no-op.
 */
@ExperimentalComposeUiApi
fun Modifier.remeasureRequester(remeasureRequester: InteropRemeasureRequester): Modifier =
    this then InteropRemeasureRequesterModifierElement(remeasureRequester)

private data class InteropRemeasureRequesterModifierElement(
    val remeasureRequester: InteropRemeasureRequester
): ModifierNodeElement<InteropRemeasureRequesterNode>() {
    override fun create(): InteropRemeasureRequesterNode =
        InteropRemeasureRequesterNode(remeasureRequester)

    override fun update(node: InteropRemeasureRequesterNode) {
        if (node.remeasureRequester === remeasureRequester) return
        node.unregisterIfNeeded()
        node.remeasureRequester = remeasureRequester
        node.registerIfNeeded()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "uikitInteropRemeasureRequester"
        properties["uikitInteropRemeasureRequester"] = remeasureRequester
    }
}

internal interface InteropRemeasureRequesterModifierNode: DelegatableNode

private class InteropRemeasureRequesterNode(
    var remeasureRequester: InteropRemeasureRequester
): InteropRemeasureRequesterModifierNode, Modifier.Node() {

    private var registered: Boolean = false

    override fun onAttach() {
        super.onAttach()
        registerIfNeeded()
    }

    override fun onDetach() {
        unregisterIfNeeded()
        super.onDetach()
    }

    fun registerIfNeeded() {
        if (registered) return
        if (!isAttachedToInteropLayoutNode()) return
        remeasureRequester.remeasureRequesterNodes += this
        registered = true
    }

    fun unregisterIfNeeded() {
        if (!registered) return
        remeasureRequester.remeasureRequesterNodes -= this
        registered = false
    }

    private fun DelegatableNode.isAttachedToInteropLayoutNode(): Boolean =
        requireLayoutNode().interopViewFactoryHolder is InteropLayoutNodeHolder
}

/**
 * Internal marker interface implemented by interop layoutNode holders for UIKit interop.
 *
 * Used to ensure that [Modifier.remeasureRequester] only registers targets that correspond to
 * UIKit interop elements, and does not accidentally remeasure arbitrary Compose nodes.
 */
internal interface InteropLayoutNodeHolder
