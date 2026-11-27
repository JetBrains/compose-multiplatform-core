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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.InspectorInfo

/**
 * Creates a scope in which [sceneInteropLayer] instances share one z-index namespace.
 *
 * Containers such as Navigation 3 attach this modifier to their existing animated-content root. It
 * does not add a layout; it only owns the coordinator for the scene layers below it.
 */
@ExperimentalComposeUiApi
public fun Modifier.sceneInteropLayerRoot(): Modifier =
    this then SceneInteropLayerCoordinatorModifierElement

/**
 * Marks the modified layout as a scene layer for UIKit interop.
 *
 * The layer tracks its global bounds and z-order. Overlay UIKit interop in lower layers is masked
 * where a higher layer that occludes lower layers covers it.
 */
@ExperimentalComposeUiApi
public fun Modifier.sceneInteropLayer(occludesLowerLayers: Boolean, zIndex: Float): Modifier =
    this then
        SceneInteropLayerModifierElement(occludesLowerLayers = occludesLowerLayers, zIndex = zIndex)

internal const val SceneInteropLayerCoordinatorNodeKey =
    "androidx.compose.ui.node.SceneInteropLayerCoordinatorNodeKey"

internal const val SceneInteropLayerNodeKey =
    "androidx.compose.ui.node.SceneInteropLayerNodeKey"

private data object SceneInteropLayerCoordinatorModifierElement :
    ModifierNodeElement<SceneInteropLayerCoordinatorModifierNode>() {
    override fun create() = SceneInteropLayerCoordinatorModifierNode()

    override fun update(node: SceneInteropLayerCoordinatorModifierNode) {}

    override fun InspectorInfo.inspectableProperties() {
        name = "sceneInteropLayerRoot"
    }
}

internal class SceneInteropLayerCoordinatorModifierNode :
    Modifier.Node(), TraversableNode, CompositionLocalConsumerModifierNode {
    override val traverseKey = SceneInteropLayerCoordinatorNodeKey

    private var sceneLayerCoordinator: SceneInteropLayerCoordinator? = null

    fun sceneLayerCoordinator(): SceneInteropLayerCoordinator {
        sceneLayerCoordinator?.let {
            return it
        }

        val container = currentValueOf(LocalInteropContainer)

        return SceneInteropLayerCoordinator(
                finalFrameUpdateScheduler = container.createFinalFrameUpdateScheduler()
            )
            .also { sceneLayerCoordinator = it }
    }

    override fun onDetach() {
        sceneLayerCoordinator?.dispose()
        sceneLayerCoordinator = null
        super.onDetach()
    }
}

private data class SceneInteropLayerModifierElement(
    val occludesLowerLayers: Boolean,
    val zIndex: Float,
) : ModifierNodeElement<SceneInteropLayerModifierNode>() {
    override fun create() =
        SceneInteropLayerModifierNode(occludesLowerLayers = occludesLowerLayers, zIndex = zIndex)

    override fun update(node: SceneInteropLayerModifierNode) {
        node.updateProperties(occludesLowerLayers = occludesLowerLayers, zIndex = zIndex)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "sceneInteropLayer"
        properties["occludesLowerLayers"] = occludesLowerLayers
        properties["zIndex"] = zIndex
    }
}

internal class SceneInteropLayerModifierNode(
    private var occludesLowerLayers: Boolean,
    private var zIndex: Float,
) : Modifier.Node(), TraversableNode, LayoutAwareModifierNode, GlobalPositionAwareModifierNode {
    override val traverseKey = SceneInteropLayerNodeKey

    private var sceneLayerCoordinator: SceneInteropLayerCoordinator? = null

    internal fun occludesLowerLayer(otherLayer: SceneInteropLayerModifierNode): Boolean =
        occludesLowerLayers && zIndex > otherLayer.zIndex

    override fun onAttach() {
        super.onAttach()
        ensureSceneLayerCoordinator()
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        ensureSceneLayerCoordinator()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (ensureSceneLayerCoordinator()) {
            sceneLayerCoordinator?.updateLayerBounds(this, coordinates)
        }
    }

    override fun onDetach() {
        sceneLayerCoordinator?.removeLayer(this)
        sceneLayerCoordinator = null
        super.onDetach()
    }

    fun updateProperties(occludesLowerLayers: Boolean, zIndex: Float) {
        this.occludesLowerLayers = occludesLowerLayers
        this.zIndex = zIndex
        sceneLayerCoordinator?.updateLayer()
    }

    fun registerHolder(holder: IosInteropElementHolder<*>) {
        if (ensureSceneLayerCoordinator()) {
            sceneLayerCoordinator?.updateHolder(holder, this)
        }
    }

    fun unregisterHolder(holder: IosInteropElementHolder<*>) {
        sceneLayerCoordinator?.unregisterHolder(holder)
    }

    private fun ensureSceneLayerCoordinator(): Boolean {
        sceneLayerCoordinator?.let {
            return true
        }

        // The root can attach after this layer, so retry the lookup during placement callbacks.
        val root =
            findNearestAncestor(SceneInteropLayerCoordinatorNodeKey)
                as? SceneInteropLayerCoordinatorModifierNode
        val currentCoordinator = root?.sceneLayerCoordinator() ?: return false

        sceneLayerCoordinator = currentCoordinator
        return true
    }
}
