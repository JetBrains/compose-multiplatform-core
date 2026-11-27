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

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect

/** Coordinates scene interop layers with flat overlay UIKit interop. */
internal class SceneInteropLayerCoordinator(
    private val finalFrameUpdateScheduler: FinalFrameUpdateScheduler
) {
    private val layerBounds = mutableMapOf<SceneInteropLayerModifierNode, IntRect>()
    private val holdersByLayer =
        mutableMapOf<SceneInteropLayerModifierNode, MutableSet<IosInteropElementHolder<*>>>()
    private val layerByHolder =
        mutableMapOf<IosInteropElementHolder<*>, SceneInteropLayerModifierNode>()

    fun updateLayer() {
        scheduleUpdate()
    }

    fun updateLayerBounds(layer: SceneInteropLayerModifierNode, coordinates: LayoutCoordinates) {
        val root = coordinates.findRootCoordinates()
        val bounds = root.localBoundingBoxOf(coordinates, clipBounds = false).roundToIntRect()
        if (layerBounds[layer] == bounds) return

        layerBounds[layer] = bounds
        scheduleUpdate()
    }

    fun removeLayer(layer: SceneInteropLayerModifierNode) {
        val holders = holdersByLayer.remove(layer)
        if (holders != null) {
            holders.forEach { holder -> layerByHolder.remove(holder) }
        }
        val hadBounds = layerBounds.remove(layer) != null
        if (holders != null || hadBounds) scheduleUpdate()
    }

    fun updateHolder(holder: IosInteropElementHolder<*>, layer: SceneInteropLayerModifierNode) {
        val previousLayer = layerByHolder[holder]
        val isAlreadyRegistered =
            previousLayer == layer && holdersByLayer[layer]?.contains(holder) == true
        if (isAlreadyRegistered) return

        previousLayer?.let { currentLayer ->
            holdersByLayer[currentLayer]?.let { holders ->
                holders.remove(holder)
                if (holders.isEmpty()) holdersByLayer.remove(currentLayer)
            }
        }

        holdersByLayer.getOrPut(layer) { mutableSetOf() }.add(holder)
        layerByHolder[holder] = layer
        scheduleUpdate()
    }

    fun unregisterHolder(holder: IosInteropElementHolder<*>) {
        val layer = layerByHolder.remove(holder) ?: return
        holdersByLayer[layer]?.let { holders ->
            holders.remove(holder)
            if (holders.isEmpty()) holdersByLayer.remove(layer)
        }
        scheduleUpdate()
    }

    fun dispose() {
        holdersByLayer.values.forEach { holders ->
            holders.forEach { holder -> holder.updateSceneLayerMask(emptyList()) }
        }
        holdersByLayer.clear()
        layerByHolder.clear()
        layerBounds.clear()
        finalFrameUpdateScheduler.cancel()
    }

    private fun scheduleUpdate() {
        finalFrameUpdateScheduler.schedule { applyMasks() }
    }

    private fun applyMasks() {
        holdersByLayer.forEach { (layer, holders) ->
            val occluderRects = buildList {
                layerBounds.forEach { (otherLayer, bounds) ->
                    if (otherLayer.occludesLowerLayer(layer)) {
                        add(bounds)
                    }
                }
            }

            holders.forEach { holder ->
                if (holder.placedAsOverlay) {
                    holder.updateSceneLayerMask(occluderRects)
                }
            }
        }
    }
}
