/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.graphics.layer

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SkiaBackedCanvas
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.materializeSkiaPath
import androidx.compose.ui.graphics.platform.PlatformGraphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.skiaImageFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toSkia
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Path as SkPath
import org.jetbrains.skia.Point
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skiko.node.RenderNode

@OptIn(InternalComposeUiApi::class)
internal class SkikoGraphicsLayer(
    renderNode: RenderNode,
) : PlatformGraphicsLayer {
    private var renderNode: RenderNode? = renderNode
    private val pictureDrawScope = CanvasDrawScope()

    private var topLeft: IntOffset = IntOffset.Zero
    private var size: IntSize = IntSize.Zero

    private var outsetLeft: Int = 0
    private var outsetTop: Int = 0
    private var outsetRight: Int = 0
    private var outsetBottom: Int = 0
    private var cachedLayerPaint: SkPaint? = null

    private var cachedOutline: Outline? = null
    private var cachedClip: Boolean = false

    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
        set(value) {
            field = value
            updateLayerProperties()
        }

    override var pivotOffset: Offset = Offset.Unspecified
        set(value) {
            field = value
            updateRenderNodePivot()
        }

    override var alpha: Float = 1f
        set(value) {
            field = value
            renderNode?.alpha = value
            updateLayerProperties()
        }

    override var scaleX: Float = 1f
        set(value) {
            field = value
            renderNode?.scaleX = value
        }

    override var scaleY: Float = 1f
        set(value) {
            field = value
            renderNode?.scaleY = value
        }

    override var translationX: Float = 0f
        set(value) {
            field = value
            renderNode?.translationX = value
        }

    override var translationY: Float = 0f
        set(value) {
            field = value
            renderNode?.translationY = value
        }

    override var shadowElevation: Float = 0f
        set(value) {
            field = value
            renderNode?.shadowElevation = value
        }

    override var ambientShadowColor: Color = Color.Black
        set(value) {
            field = value
            renderNode?.ambientShadowColor = value.toArgb()
        }

    override var spotShadowColor: Color = Color.Black
        set(value) {
            field = value
            renderNode?.spotShadowColor = value.toArgb()
        }

    override var blendMode: BlendMode = BlendMode.SrcOver
        set(value) {
            field = value
            updateLayerProperties()
        }

    override var colorFilter: ColorFilter? = null
        set(value) {
            field = value
            updateLayerProperties()
        }

    override var rotationX: Float = 0f
        set(value) {
            field = value
            renderNode?.rotationX = value
        }

    override var rotationY: Float = 0f
        set(value) {
            field = value
            renderNode?.rotationY = value
        }

    override var rotationZ: Float = 0f
        set(value) {
            field = value
            renderNode?.rotationZ = value
        }

    override var cameraDistance: Float = DefaultCameraDistance
        set(value) {
            field = value
            renderNode?.cameraDistance = value
        }

    override var renderEffect: RenderEffect? = null
        set(value) {
            field = value
            updateLayerProperties()
        }

    override fun setBounds(topLeft: IntOffset, size: IntSize) {
        this.topLeft = topLeft
        this.size = size
        updateRenderNodeBounds()
        updateRenderNodePivot()
    }

    override fun setOutline(outline: Outline?, clip: Boolean) {
        cachedOutline = outline
        cachedClip = clip
        applyOutline()
    }

    private fun applyOutline() {
        val renderNode = renderNode ?: return
        val outline = cachedOutline
        if (outline == null) {
            renderNode.clip = false
            renderNode.setClipPath(null)
        } else {
            renderNode.clip = cachedClip
            // The recorded content and the render node bounds are shifted by the outsets (see
            // record()/updateRenderNodeBounds()), so the clip outline has to be shifted to match.
            val dx = outsetLeft.toFloat()
            val dy = outsetTop.toFloat()
            when (outline) {
                is Outline.Rectangle -> renderNode.setClipRect(
                    outline.rect.left + dx,
                    outline.rect.top + dy,
                    outline.rect.right + dx,
                    outline.rect.bottom + dy,
                    antiAlias = true
                )
                is Outline.Rounded -> renderNode.setClipRRect(
                    outline.roundRect.left + dx,
                    outline.roundRect.top + dy,
                    outline.roundRect.right + dx,
                    outline.roundRect.bottom + dy,
                    floatArrayOf(
                        outline.roundRect.topLeftCornerRadius.x,
                        outline.roundRect.topLeftCornerRadius.y,
                        outline.roundRect.topRightCornerRadius.x,
                        outline.roundRect.topRightCornerRadius.y,
                        outline.roundRect.bottomRightCornerRadius.x,
                        outline.roundRect.bottomRightCornerRadius.y,
                        outline.roundRect.bottomLeftCornerRadius.x,
                        outline.roundRect.bottomLeftCornerRadius.y
                    ),
                    antiAlias = true
                )
                is Outline.Generic -> renderNode.setClipPath(
                    updatePathOutline(outline.path),
                    antiAlias = true
                )
            }
        }
    }

    private fun updatePathOutline(path: Path): SkPath =
        if (hasOutsets()) {
            Path().apply { addPath(path, outsetOffset()) }
        } else {
            path
        }.materializeSkiaPath()

    override fun record(
        density: Density,
        layoutDirection: LayoutDirection,
        layer: GraphicsLayer,
        block: DrawScope.() -> Unit,
    ) {
        val renderNode = renderNode ?: return
        val recordingCanvas = renderNode.beginRecording()
        try {
            val composeCanvas = recordingCanvas.asComposeCanvas() as SkiaBackedCanvas
            if (outsetLeft > 0 || outsetTop > 0) {
                composeCanvas.save()
                composeCanvas.translate(outsetLeft.toFloat(), outsetTop.toFloat())
            }
            pictureDrawScope.draw(
                density = density,
                layoutDirection = layoutDirection,
                canvas = composeCanvas,
                size = layer.size.toSize(),
                graphicsLayer = layer,
                block = block,
            )
            if (outsetLeft > 0 || outsetTop > 0) {
                composeCanvas.restore()
            }
        } finally {
            renderNode.endRecording()
        }
    }

    override fun draw(canvas: Canvas) {
        renderNode?.drawInto(canvas.skiaCanvas)
    }

    override fun discardDisplayList() {
        renderNode?.close()
        renderNode = null
    }

    override fun setOutsets(left: Int, top: Int, right: Int, bottom: Int) {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) {
            "Outsets cannot be negative! Left: $left, Top: $top, Right: $right, Bottom: $bottom"
        }
        if (left != outsetLeft || top != outsetTop || right != outsetRight || bottom != outsetBottom) {
            outsetLeft = left
            outsetTop = top
            outsetRight = right
            outsetBottom = bottom
            updateRenderNodeBounds()
            updateRenderNodePivot()
            // Re-apply the clip so its outset shift matches the new outsets even when the outline
            // itself did not change.
            applyOutline()
        }
    }

    private fun updateRenderNodeBounds() {
        renderNode?.bounds = SkRect.makeXYWH(
            topLeft.x.toFloat() - outsetLeft,
            topLeft.y.toFloat() - outsetTop,
            size.width.toFloat() + outsetLeft + outsetRight,
            size.height.toFloat() + outsetTop + outsetBottom
        )
    }

    private fun updateRenderNodePivot() {
        val renderNode = renderNode ?: return
        renderNode.pivot =
            if (pivotOffset.isUnspecified) {
                Point(
                    size.width / 2f + outsetLeft,
                    size.height / 2f + outsetTop
                )
            } else {
                Point(
                    pivotOffset.x + outsetLeft,
                    pivotOffset.y + outsetTop
                )
            }
    }

    private fun outsetOffset(): Offset = Offset(outsetLeft.toFloat(), outsetTop.toFloat())

    private fun updateLayerProperties() {
        val paint = if (requiresLayer()) {
            SkPaint().also {
                it.setAlphaf(alpha)
                it.imageFilter = renderEffect?.skiaImageFilter
                it.colorFilter = colorFilter?.asSkiaColorFilter()
                it.blendMode = blendMode.toSkia()
            }
        } else {
            null
        }
        cachedLayerPaint = paint
        renderNode?.layerPaint = paint
    }

    private fun hasOutsets() = outsetLeft > 0 || outsetTop > 0 || outsetRight > 0 || outsetBottom > 0

    private fun requiresLayer(): Boolean {
        val alphaNeedsLayer = alpha < 1f && compositingStrategy != CompositingStrategy.ModulateAlpha
        val hasColorFilter = colorFilter != null
        val hasBlendMode = blendMode != BlendMode.SrcOver
        val hasRenderEffect = renderEffect != null
        val offscreenBufferRequested = compositingStrategy == CompositingStrategy.Offscreen
        return alphaNeedsLayer || hasColorFilter || hasBlendMode || hasRenderEffect ||
            offscreenBufferRequested
    }
}
