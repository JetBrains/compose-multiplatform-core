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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SkiaBackedCanvas
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.prepareTransformationMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toSkia
import androidx.compose.ui.graphics.toSkiaRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Point3

actual class GraphicsLayer internal constructor() {
    private val pictureDrawScope = CanvasDrawScope()
    private val pictureRecorder = PictureRecorder()
    private var picture: Picture? = null

    // Composable state marker for tracking drawing invalidations.
    private val drawState = mutableStateOf(Unit, neverEqualPolicy())

    private var matrixDirty = true
    private val matrix = Matrix()

    actual var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto

    private var internalOutline: Outline? = null
    private var outlineDirty = true
    private var roundRectOutlineTopLeft: Offset = Offset.Zero
    private var roundRectOutlineSize: Size = Size.Unspecified
    private var roundRectCornerRadius: Float = 0f
    private var outlinePath: Path? = null

    private var parentLayerUsages = 0
    private val childDependenciesTracker = ChildLayerDependenciesTracker()

    actual var topLeft: IntOffset = IntOffset.Zero
        set(value) {
            if (field != value) {
                field = value
                updateLayerConfiguration()
            }
        }

    actual var size: IntSize = IntSize.Zero
        private set

    actual var alpha: Float = 1f
        set(value) {
            field = value
            requestDraw()
        }

    actual var scaleX: Float = 1f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var scaleY: Float = 1f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var translationX: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var translationY: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var shadowElevation: Float = 0f
        set(value) {
            field = value
            requestDraw()
        }

    actual var rotationX: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var rotationY: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var rotationZ: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }

    actual var cameraDistance: Float = DefaultCameraDistance
        set(value) {
            field = value
            invalidateMatrix()
        }

    actual var renderEffect: RenderEffect? = null
        set(value) {
            field = value
            requestDraw()
        }

    private var density: Density = Density(1f)

    private fun invalidateMatrix(requestDraw: Boolean = true) {
        matrixDirty = true
        if (requestDraw) {
            requestDraw()
        }
    }

    private fun requestDraw() {
        drawState.value = Unit
    }

    private fun updateLayerConfiguration(requestDraw: Boolean = true) {
        this.outlineDirty = true
        invalidateMatrix(requestDraw)
    }

    actual fun record(
        density: Density,
        layoutDirection: LayoutDirection,
        size: IntSize,
        block: DrawScope.() -> Unit
    ) {
        // Close previous picture
        picture?.close()
        picture = null

        this.density = density
        this.size = size
        updateLayerConfiguration(
            // [record] doesn't change the state and should not explicitly request drawing
            // (happens only on the next frame) to avoid infinity invalidation loop.
            // It's designed to be handled externally.
            requestDraw = false
        )
        val bounds = size.toSize().toRect().toSkiaRect()
        val canvas = pictureRecorder.beginRecording(bounds)
        val skiaCanvas = canvas.asComposeCanvas() as SkiaBackedCanvas
        skiaCanvas.alphaMultiplier = if (compositingStrategy == CompositingStrategy.ModulateAlpha) {
            this@GraphicsLayer.alpha
        } else {
            1.0f
        }
        childDependenciesTracker.withTracking(
            onDependencyRemoved = { it.onRemovedFromParentLayer() }
        ) {
            pictureDrawScope.draw(
                density,
                layoutDirection,
                skiaCanvas,
                size.toSize(),
                this,
                block
            )
        }
        picture = pictureRecorder.finishRecordingAsPicture()
    }

    private fun addSubLayer(graphicsLayer: GraphicsLayer) {
        if (childDependenciesTracker.onDependencyAdded(graphicsLayer)) {
            graphicsLayer.onAddedToParentLayer()
        }
    }

    actual var clip: Boolean = false
        set(value) {
            field = value
            requestDraw()
        }

    private inline fun createOutlineWithPosition(
        outlineTopLeft: Offset,
        outlineSize: Size,
        block: (Offset, Size) -> Outline
    ): Outline {
        val targetSize = if (outlineSize.isUnspecified) {
            this.size.toSize()
        } else {
            outlineSize
        }
        return block(outlineTopLeft, targetSize)
    }

    private fun configureOutline(): Outline {
        var tmpOutline = internalOutline
        if (outlineDirty || tmpOutline == null) {
            val tmpPath = outlinePath
            tmpOutline = if (tmpPath != null) {
                Outline.Generic(tmpPath)
            } else {
                createOutlineWithPosition(
                    roundRectOutlineTopLeft,
                    roundRectOutlineSize
                ) { outlineTopLeft, outlineSize ->
                    if (roundRectCornerRadius > 0f) {
                        Outline.Rounded(
                            RoundRect(
                                left = outlineTopLeft.x,
                                top = outlineTopLeft.y,
                                right = outlineTopLeft.x + outlineSize.width,
                                bottom = outlineTopLeft.y + outlineSize.height,
                                cornerRadius = CornerRadius(roundRectCornerRadius)
                            )
                        )
                    } else {
                        Outline.Rectangle(
                            Rect(
                                left = outlineTopLeft.x,
                                top = outlineTopLeft.y,
                                right = outlineTopLeft.x + outlineSize.width,
                                bottom = outlineTopLeft.y + outlineSize.height
                            )
                        )
                    }
                }
            }
            internalOutline = tmpOutline
            outlineDirty = false
        }
        return tmpOutline
    }

    internal actual fun draw(canvas: Canvas, parentLayer: GraphicsLayer?) {
        if (isReleased) return

        var restoreCount = 0
        parentLayer?.addSubLayer(this)

        // Read the state because any changes to the state should trigger re-drawing.
        drawState.value

        picture?.let {
            configureOutline()

            updateMatrix()

            canvas.save()
            restoreCount++

            canvas.concat(matrix)
            canvas.translate(topLeft.x.toFloat(), topLeft.y.toFloat())

            if (shadowElevation > 0) {
                drawShadow(canvas)
            }

            if (clip || shadowElevation > 0f) {
                canvas.save()
                restoreCount++

                when (val outline = internalOutline) {
                    is Outline.Rectangle ->
                        canvas.clipRect(outline.rect)
                    is Outline.Rounded ->
                        (canvas as SkiaBackedCanvas).clipRoundRect(outline.roundRect)
                    is Outline.Generic ->
                        canvas.clipPath(outline.path)
                    null -> {
                        canvas.clipRect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                    }
                }
            }

            val useLayer = requiresLayer()
            if (useLayer) {
                canvas.saveLayer(
                    Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()),
                    Paint().apply {
                        this.alpha = this@GraphicsLayer.alpha
                        this.asFrameworkPaint().apply {
                            this.imageFilter = this@GraphicsLayer.renderEffect?.asSkiaImageFilter()
                            this.colorFilter = this@GraphicsLayer.colorFilter?.asSkiaColorFilter()
                            this.blendMode = this@GraphicsLayer.blendMode.toSkia()
                        }
                    }
                )
                restoreCount++
            } else {
                canvas.save()
                restoreCount++
            }

            canvas.nativeCanvas.drawPicture(it, null, null)

            repeat(restoreCount) {
                canvas.restore()
            }
        }
    }

    private fun onAddedToParentLayer() {
        parentLayerUsages++
    }

    private fun onRemovedFromParentLayer() {
        parentLayerUsages--
        discardContentIfReleasedAndHaveNoParentLayerUsages()
    }

    internal fun release() {
        if (!isReleased) {
            isReleased = true
            discardContentIfReleasedAndHaveNoParentLayerUsages()
        }
    }

    actual var pivotOffset: Offset = Offset.Unspecified
        set(value) {
            field = value
            invalidateMatrix()
        }

    actual var blendMode: BlendMode = BlendMode.SrcOver
        set(value) {
            field = value
            requestDraw()
        }

    actual var colorFilter: ColorFilter? = null
        set(value) {
            field = value
            requestDraw()
        }

    private fun resetOutlineParams() {
        internalOutline = null
        outlinePath = null
        roundRectOutlineSize = Size.Unspecified
        roundRectOutlineTopLeft = Offset.Zero
        roundRectCornerRadius = 0f
        outlineDirty = true
    }

    actual fun setRoundRectOutline(
        topLeft: Offset,
        size: Size,
        cornerRadius: Float
    ) {
        resetOutlineParams()
        this.roundRectOutlineTopLeft = topLeft
        this.roundRectOutlineSize = size
        this.roundRectCornerRadius = cornerRadius
    }

    actual fun setPathOutline(path: Path) {
        resetOutlineParams()
        this.outlinePath = path
    }

    actual val outline: Outline
        get() = configureOutline()

    actual fun setRectOutline(
        topLeft: Offset,
        size: Size
    ) {
        setRoundRectOutline(topLeft, size, 0f)
    }

    private fun updateMatrix() {
        if (!matrixDirty) return

        val pivotX: Float
        val pivotY: Float
        if (pivotOffset.isUnspecified) {
            pivotX = size.width / 2f
            pivotY = size.height / 2f
        } else {
            pivotX = pivotOffset.x
            pivotY = pivotOffset.y
        }
        prepareTransformationMatrix(
            matrix = matrix,
            pivotX = pivotX,
            pivotY = pivotY,
            translationX = translationX,
            translationY = translationY,
            rotationX = rotationX,
            rotationY = rotationY,
            rotationZ = rotationZ,
            scaleX = scaleX,
            scaleY = scaleY,
            cameraDistance = cameraDistance
        )
        matrixDirty = false
    }

    actual var isReleased: Boolean = false
        private set

    private fun discardContentIfReleasedAndHaveNoParentLayerUsages() {
        if (isReleased && parentLayerUsages == 0) {
            picture?.close()
            pictureRecorder.close()

            // discarding means we don't draw children layer anymore and need to remove dependencies:
            childDependenciesTracker.removeDependencies {
                it.onRemovedFromParentLayer()
            }
        }
    }

    actual var ambientShadowColor: Color = Color.Black
        set(value) {
            field = value
            requestDraw()
        }

    actual var spotShadowColor: Color = Color.Black
        set(value) {
            field = value
            requestDraw()
        }

    private fun requiresLayer(): Boolean {
        val alphaNeedsLayer = alpha < 1f && compositingStrategy != CompositingStrategy.ModulateAlpha
        val hasColorFilter = colorFilter != null
        val hasBlendMode = blendMode != BlendMode.SrcOver
        val hasRenderEffect = renderEffect != null
        val offscreenBufferRequested = compositingStrategy == CompositingStrategy.Offscreen
        return alphaNeedsLayer || hasColorFilter || hasBlendMode || hasRenderEffect ||
            offscreenBufferRequested
    }

    private fun drawShadow(canvas: Canvas) = with(density) {
        val path = when (val tmpOutline = internalOutline) {
            is Outline.Rectangle -> Path().apply { addRect(tmpOutline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(tmpOutline.roundRect) }
            is Outline.Generic -> tmpOutline.path
            else -> return
        }

        val zParams = Point3(0f, 0f, shadowElevation)

        val lightPos = Point3(0f, -300.dp.toPx(), 600.dp.toPx())
        val lightRad = 800.dp.toPx()

        val ambientAlpha = 0.039f * alpha
        val spotAlpha = 0.19f * alpha
        val ambientColor = ambientShadowColor.copy(alpha = ambientAlpha)
        val spotColor = spotShadowColor.copy(alpha = spotAlpha)

        org.jetbrains.skia.ShadowUtils.drawShadow(
            canvas.nativeCanvas, path.asSkiaPath(), zParams, lightPos,
            lightRad,
            ambientColor.toArgb(),
            spotColor.toArgb(), alpha < 1f, false
        )
    }

    actual suspend fun toImageBitmap(): ImageBitmap =
        ImageBitmap(size.width, size.height).apply { draw(Canvas(this), null) }
}
