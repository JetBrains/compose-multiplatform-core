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

package androidx.compose.ui.graphics.platform

import androidx.annotation.IntRange
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * Backend-provided implementation of a [GraphicsLayer], supplied by the registered graphics backend.
 * It owns the platform draw primitive and only forwards property and drawing operations; the
 * layer-tree bookkeeping (outline resolution, child dependencies, release accounting) is kept in
 * [GraphicsLayer] itself, mirroring the Android `GraphicsLayerImpl` split.
 */
@InternalComposeUiApi
interface PlatformGraphicsLayer {
    /** Backs [GraphicsLayer.compositingStrategy]. */
    var compositingStrategy: CompositingStrategy
    /** Backs [GraphicsLayer.pivotOffset]. */
    var pivotOffset: Offset
    /** Backs [GraphicsLayer.alpha]. */
    var alpha: Float
    /** Backs [GraphicsLayer.scaleX]. */
    var scaleX: Float
    /** Backs [GraphicsLayer.scaleY]. */
    var scaleY: Float
    /** Backs [GraphicsLayer.translationX]. */
    var translationX: Float
    /** Backs [GraphicsLayer.translationY]. */
    var translationY: Float
    /** Backs [GraphicsLayer.shadowElevation]. */
    var shadowElevation: Float
    /** Backs [GraphicsLayer.ambientShadowColor]. */
    var ambientShadowColor: Color
    /** Backs [GraphicsLayer.spotShadowColor]. */
    var spotShadowColor: Color
    /** Backs [GraphicsLayer.blendMode]. */
    var blendMode: BlendMode
    /** Backs [GraphicsLayer.colorFilter]. */
    var colorFilter: ColorFilter?
    /** Backs [GraphicsLayer.rotationX]. */
    var rotationX: Float
    /** Backs [GraphicsLayer.rotationY]. */
    var rotationY: Float
    /** Backs [GraphicsLayer.rotationZ]. */
    var rotationZ: Float
    /** Backs [GraphicsLayer.cameraDistance]. */
    var cameraDistance: Float
    /** Backs [GraphicsLayer.renderEffect]. */
    var renderEffect: RenderEffect?

    /** Position the layer's content bounds. */
    fun setBounds(topLeft: IntOffset, size: IntSize)

    /**
     * Apply the resolved [outline] (in layer-local coordinates) to the platform layer. A `null`
     * [outline] means no clip/shadow geometry is needed.
     */
    fun setOutline(outline: Outline?, clip: Boolean)

    /** Record the drawing commands of [layer] using [block]. */
    fun record(
        density: Density,
        layoutDirection: LayoutDirection,
        layer: GraphicsLayer,
        block: DrawScope.() -> Unit,
    )

    /** Draw the recorded content into [canvas]. */
    fun draw(canvas: Canvas)

    /** Release the platform draw primitive backing this layer. */
    fun discardDisplayList()

    /** @see GraphicsLayer.setOutsets */
    fun setOutsets(
        @IntRange(from = 0) left: Int,
        @IntRange(from = 0) top: Int,
        @IntRange(from = 0) right: Int,
        @IntRange(from = 0) bottom: Int,
    )
}
