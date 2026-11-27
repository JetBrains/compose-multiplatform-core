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

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.UnplacedAwareModifierNode
import androidx.compose.ui.node.findNearestAncestor

/**
 * Registers an overlay interop holder with the scene layer that contains it.
 */
internal fun Modifier.sceneInteropLayerAware(
    isEnabled: Boolean,
    holder: IosInteropElementHolder<*>
): Modifier =
    if (isEnabled) this then SceneInteropLayerAwareModifierElement(holder) else this

private data class SceneInteropLayerAwareModifierElement(
    val holder: IosInteropElementHolder<*>,
) : ModifierNodeElement<SceneInteropLayerAwareModifierNode>() {
    override fun create() = SceneInteropLayerAwareModifierNode(holder)

    override fun update(node: SceneInteropLayerAwareModifierNode) {}
}

private class SceneInteropLayerAwareModifierNode(
    private val holder: IosInteropElementHolder<*>,
) : Modifier.Node(),
    LayoutAwareModifierNode,
    UnplacedAwareModifierNode,
    GlobalPositionAwareModifierNode {
    private var isPlaced = false
    private var layerNode: SceneInteropLayerModifierNode? = null

    override fun onPlaced(coordinates: LayoutCoordinates) {
        val currentLayerNode =
            findNearestAncestor(SceneInteropLayerNodeKey) as? SceneInteropLayerModifierNode
        if (currentLayerNode != layerNode) {
            layerNode?.unregisterHolder(holder)
            layerNode = currentLayerNode
        }
        currentLayerNode?.registerHolder(holder)

        isPlaced = true
    }

    override fun onUnplaced() {
        layerNode?.unregisterHolder(holder)
        layerNode = null
        isPlaced = false
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        // Global-position callbacks can arrive before the placement callback. Register the layer
        // then so the first layout can already update the interop mask.
        if (!isPlaced) {
            onPlaced(coordinates)
        }
    }

    override fun onDetach() {
        if (isPlaced) {
            onUnplaced()
        }
        layerNode = null
        super.onDetach()
    }
}
