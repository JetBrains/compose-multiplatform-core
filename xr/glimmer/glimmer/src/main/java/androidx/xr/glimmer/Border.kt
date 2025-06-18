/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.xr.glimmer

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Border drawing and caching logic from androidx.compose.foundation.border, moved out of a draw
 * node. The same instance of this class can be used to create new drawing lambdas for different
 * borders (such as if parameters change), but only the most recently created lambda will draw
 * correctly due to the re-use of cached objects. To draw multiple different borders concurrently,
 * create a new instance of this class for each border.
 */
internal class BorderLogic {
    // BorderCache object that is lazily allocated depending on the type of shape
    // This object is only used for generic shapes and rounded rectangles with different corner
    // radius sizes.
    // Note: Extension functions that use BorderCache are part of this class.
    private var borderCache: BorderCache? = null

    /** Creates a drawing lambda that will draw a border with the given parameters. */
    internal fun createDrawBorder(
        drawScope: DrawScope,
        // TODO: change to a lambda to allow efficiently animating width
        width: Dp,
        brush: Brush,
        shape: Shape,
    ): DrawScope.() -> Unit =
        with(drawScope) {
            val hasValidBorderParams = width.toPx() >= 0f && size.minDimension > 0f
            return if (!hasValidBorderParams) {
                {}
            } else {
                val strokeWidthPx =
                    min(
                        if (width == Dp.Hairline) 1f else ceil(width.toPx()),
                        ceil(size.minDimension / 2),
                    )
                val halfStroke = strokeWidthPx / 2
                val topLeft = Offset(halfStroke, halfStroke)
                val borderSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
                // The stroke is larger than the drawing area so just draw a full shape instead
                val fillArea = (strokeWidthPx * 2) > size.minDimension
                when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Generic ->
                        createDrawGenericBorder(brush, outline, fillArea, strokeWidthPx)

                    is Outline.Rounded ->
                        createDrawRoundRectBorder(
                            brush,
                            outline,
                            topLeft,
                            borderSize,
                            fillArea,
                            strokeWidthPx,
                        )

                    is Outline.Rectangle ->
                        createDrawRectBorder(brush, topLeft, borderSize, fillArea, strokeWidthPx)
                }
            }
        }

    /**
     * Border implementation for generic paths. Note it is possible to be given paths that do not
     * make sense in the context of a border (ex. a figure 8 path or a non-enclosed shape) We do not
     * handle that here as we expect developers to give us enclosed, non-overlapping paths.
     */
    private fun createDrawGenericBorder(
        brush: Brush,
        outline: Outline.Generic,
        fillArea: Boolean,
        strokeWidth: Float,
    ): DrawScope.() -> Unit {
        if (fillArea) {
            return { drawPath(outline.path, brush = brush) }
        } else {
            // Optimization, if we are only drawing a solid color border, we only need an alpha8
            // mask as we can draw the mask with a tint.
            // Otherwise we need to allocate a full ImageBitmap and draw it normally
            val config: ImageBitmapConfig
            val colorFilter: ColorFilter?
            if (brush is SolidColor) {
                config = ImageBitmapConfig.Alpha8
                // The brush is drawn into the mask with the corresponding color including the
                // alpha channel so when we tint we should not apply the alpha as it would end up
                // modulating it twice
                colorFilter = ColorFilter.tint(brush.value.copy(alpha = 1f))
            } else {
                config = ImageBitmapConfig.Argb8888
                colorFilter = null
            }

            val pathBounds = outline.path.getBounds()
            // Create a mask path that includes a rectangle with the original path cut out of it.
            // Note: borderCache is part of the class that defines this extension function.
            if (borderCache == null) {
                borderCache = BorderCache()
            }
            val maskPath =
                borderCache!!.obtainPath().apply {
                    reset()
                    addRect(pathBounds)
                    op(this, outline.path, PathOperation.Difference)
                }

            val pathBoundsSize =
                IntSize(ceil(pathBounds.width).toInt(), ceil(pathBounds.height).toInt())

            val updateBitmap: DrawScope.() -> ImageBitmap = {
                with(borderCache!!) {
                    // Draw into offscreen bitmap with the size of the path
                    // We need to draw into this intermediate bitmap to act as a layer
                    // and make sure that the clearing logic does not generate underdraw
                    // into the target we are rendering into
                    drawBorderCache(pathBoundsSize, config) {
                        // Paths can have offsets, so translate to keep the drawn path
                        // within the bounds of the mask bitmap
                        translate(-pathBounds.left, -pathBounds.top) {
                            // Draw the path with a stroke width twice the provided value.
                            // Because strokes are centered, this will draw both and inner and
                            // outer stroke with the desired stroke width
                            drawPath(
                                path = outline.path,
                                brush = brush,
                                style = Stroke(strokeWidth * 2),
                            )

                            // Scale the canvas slightly to cover the background that may be visible
                            // after clearing the outer stroke
                            scale((size.width + 1) / size.width, (size.height + 1) / size.height) {
                                // Remove the outer stroke by clearing the inverted mask path
                                drawPath(
                                    path = maskPath,
                                    brush = brush,
                                    blendMode = BlendMode.Clear,
                                )
                            }
                        }
                    }
                }
            }

            var cacheImageBitmap: ImageBitmap? = null

            return {
                translate(pathBounds.left, pathBounds.top) {
                    // If the brush is a shader brush, internal properties (such as translation
                    // matrix) can change when re-drawn, even though the brush instance is the same.
                    // We don't have a way to know this, so just defensively re-draw the bitmap
                    // if the brush is a shader brush.
                    val imageBitmapNeedsUpdate = brush is ShaderBrush
                    val bitmap =
                        if (imageBitmapNeedsUpdate) {
                            updateBitmap()
                        } else {
                            if (cacheImageBitmap == null) {
                                cacheImageBitmap = updateBitmap()
                            }
                            cacheImageBitmap
                        }
                    drawImage(bitmap, srcSize = pathBoundsSize, colorFilter = colorFilter)
                }
            }
        }
    }

    /** Border implementation for simple rounded rects and those with different corner radii */
    private fun createDrawRoundRectBorder(
        brush: Brush,
        outline: Outline.Rounded,
        topLeft: Offset,
        borderSize: Size,
        fillArea: Boolean,
        strokeWidth: Float,
    ): DrawScope.() -> Unit {
        if (outline.roundRect.isSimple) {
            val cornerRadius = outline.roundRect.topLeftCornerRadius
            val halfStroke = strokeWidth / 2
            val borderStroke = Stroke(strokeWidth)
            return {
                when {
                    fillArea -> {
                        // If the drawing area is smaller than the stroke being drawn
                        // drawn all around it just draw a filled in rounded rect
                        drawRoundRect(brush, cornerRadius = cornerRadius)
                    }
                    cornerRadius.x < halfStroke -> {
                        // If the corner radius is smaller than half of the stroke width
                        // then the interior curvature of the stroke will be a sharp edge
                        // In this case just draw a normal filled in rounded rect with the
                        // desired corner radius but clipping out the interior rectangle
                        clipRect(
                            strokeWidth,
                            strokeWidth,
                            size.width - strokeWidth,
                            size.height - strokeWidth,
                            clipOp = ClipOp.Difference,
                        ) {
                            drawRoundRect(brush, cornerRadius = cornerRadius)
                        }
                    }
                    else -> {
                        // Otherwise draw a stroked rounded rect with the corner radius
                        // shrunk by half of the stroke width. This will ensure that the
                        // outer curvature of the rounded rectangle will have the desired
                        // corner radius.
                        drawRoundRect(
                            brush = brush,
                            topLeft = topLeft,
                            size = borderSize,
                            cornerRadius = cornerRadius.shrink(halfStroke),
                            style = borderStroke,
                        )
                    }
                }
            }
        } else {
            // Note: borderCache is part of the class that defines this extension function.
            if (borderCache == null) {
                borderCache = BorderCache()
            }
            val path = borderCache!!.obtainPath()
            val roundedRectPath =
                createRoundRectPath(path, outline.roundRect, strokeWidth, fillArea)
            return { drawPath(roundedRectPath, brush = brush) }
        }
    }
}

/**
 * Helper object that handles lazily allocating and re-using objects to render the border into an
 * offscreen ImageBitmap
 */
private class BorderCache(
    private var imageBitmap: ImageBitmap? = null,
    private var canvas: androidx.compose.ui.graphics.Canvas? = null,
    private var canvasDrawScope: CanvasDrawScope? = null,
    private var borderPath: Path? = null,
) {
    inline fun DrawScope.drawBorderCache(
        borderSize: IntSize,
        config: ImageBitmapConfig,
        block: DrawScope.() -> Unit,
    ): ImageBitmap {

        var targetImageBitmap = imageBitmap
        var targetCanvas = canvas
        // If we previously had allocated a full Argb888 ImageBitmap but are only requiring
        // an alpha mask, just re-use the same ImageBitmap instead of allocating a new one
        val compatibleConfig =
            targetImageBitmap?.config == ImageBitmapConfig.Argb8888 ||
                config == targetImageBitmap?.config
        if (
            targetImageBitmap == null ||
                targetCanvas == null ||
                size.width > targetImageBitmap.width ||
                size.height > targetImageBitmap.height ||
                !compatibleConfig
        ) {
            targetImageBitmap =
                ImageBitmap(borderSize.width, borderSize.height, config = config).also {
                    imageBitmap = it
                }
            targetCanvas =
                androidx.compose.ui.graphics.Canvas(targetImageBitmap).also { canvas = it }
        }

        val targetDrawScope = canvasDrawScope ?: CanvasDrawScope().also { canvasDrawScope = it }
        val drawSize = borderSize.toSize()
        targetDrawScope.draw(this, layoutDirection, targetCanvas, drawSize) {
            // Clear the previously rendered portion within this ImageBitmap as we could
            // be re-using it
            drawRect(color = Color.Black, size = drawSize, blendMode = BlendMode.Clear)
            block()
        }
        targetImageBitmap.prepareToDraw()
        return targetImageBitmap
    }

    fun obtainPath(): Path = borderPath ?: Path().also { borderPath = it }
}

/** Border implementation for rectangular borders */
private fun DrawScope.createDrawRectBorder(
    brush: Brush,
    topLeft: Offset,
    borderSize: Size,
    fillArea: Boolean,
    strokeWidthPx: Float,
): DrawScope.() -> Unit {
    // If we are drawing a rectangular stroke, just offset it by half the stroke
    // width as strokes are always drawn centered on their geometry.
    // If the border is larger than the drawing area, just fill the area with a
    // solid rectangle
    val rectTopLeft = if (fillArea) Offset.Zero else topLeft
    val size = if (fillArea) size else borderSize
    val style = if (fillArea) Fill else Stroke(strokeWidthPx)
    return { drawRect(brush = brush, topLeft = rectTopLeft, size = size, style = style) }
}

/**
 * Helper method that creates a round rect with the inner region removed by the given stroke width
 */
private fun createRoundRectPath(
    targetPath: Path,
    roundedRect: RoundRect,
    strokeWidth: Float,
    fillArea: Boolean,
): Path =
    targetPath.apply {
        reset()
        addRoundRect(roundedRect)
        if (!fillArea) {
            val insetPath =
                Path().apply { addRoundRect(createInsetRoundedRect(strokeWidth, roundedRect)) }
            op(this, insetPath, PathOperation.Difference)
        }
    }

private fun createInsetRoundedRect(widthPx: Float, roundedRect: RoundRect) =
    RoundRect(
        left = widthPx,
        top = widthPx,
        right = roundedRect.width - widthPx,
        bottom = roundedRect.height - widthPx,
        topLeftCornerRadius = roundedRect.topLeftCornerRadius.shrink(widthPx),
        topRightCornerRadius = roundedRect.topRightCornerRadius.shrink(widthPx),
        bottomLeftCornerRadius = roundedRect.bottomLeftCornerRadius.shrink(widthPx),
        bottomRightCornerRadius = roundedRect.bottomRightCornerRadius.shrink(widthPx),
    )

/**
 * Helper method to shrink the corner radius by the given value, clamping to 0 if the resultant
 * corner radius would be negative
 */
private fun CornerRadius.shrink(value: Float): CornerRadius =
    CornerRadius(max(0f, this.x - value), max(0f, this.y - value))
