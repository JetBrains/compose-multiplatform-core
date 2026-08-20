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

package androidx.compose.ui.platform.webgl

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.Rect

/**
 * Draws the latest frame from [renderTarget].
 * Does nothing when [renderTarget] has no frame ready yet.
 *
 * Performs no GL rendering directly — only records a draw of the pre-rendered image.
 * Safe for graphics layers (`clip`, `blur`) and multiple draws per frame. Automatically
 * invalidates drawing on new frames without triggering recomposition.
 *
 * @param renderTarget Target surface to draw.
 * @param dstOffset Top-left destination offset in local coordinates.
 * @param dstSize Destination size (defaults to full draw bounds).
 * @param contentScale Scaling behavior when aspect ratios differ.
 */
@ExperimentalComposeUiApi
fun DrawScope.drawWebGLTexture(
    renderTarget: WebGLRenderTarget,
    dstOffset: Offset = Offset.Zero,
    dstSize: Size = size,
    contentScale: ContentScale = ContentScale.Crop,
) {
    // Schedules the next redraw once this frame's content is rendered, without recomposing.
    renderTarget.observeInvalidation()

    val image = renderTarget.image ?: return
    if (!dstSize.isSpecified || dstSize.width <= 0f || dstSize.height <= 0f) return

    val srcSize = Size(image.width.toFloat(), image.height.toFloat())
    if (srcSize.width <= 0f || srcSize.height <= 0f) return

    val scale = contentScale.computeScaleFactor(srcSize, dstSize)
    if (scale.scaleX <= 0f || scale.scaleY <= 0f) return

    // Per axis: when the scaled texture covers the destination, the source is cropped; when it does
    // not, the destination is inset. This yields the expected result for Crop, Fit, FillBounds,
    // Inside and None alike.
    val (srcX, srcWidth, dstX, dstWidth) =
        axis(srcSize.width, dstSize.width, scale.scaleX, dstOffset.x)
    val (srcY, srcHeight, dstY, dstHeight) =
        axis(srcSize.height, dstSize.height, scale.scaleY, dstOffset.y)

    drawIntoCanvas { canvas ->
        canvas.skiaCanvas.drawImageRect(
            image,
            Rect.makeXYWH(srcX, srcY, srcWidth, srcHeight),
            Rect.makeXYWH(dstX, dstY, dstWidth, dstHeight),
        )
    }
}

private data class AxisPlacement(
    val src: Float,
    val srcExtent: Float,
    val dst: Float,
    val dstExtent: Float,
)

private fun axis(
    srcExtent: Float,
    dstExtent: Float,
    scale: Float,
    dstOrigin: Float,
): AxisPlacement {
    val scaledExtent = srcExtent * scale
    return if (scaledExtent >= dstExtent) {
        val visibleSrcExtent = dstExtent / scale
        AxisPlacement(
            src = (srcExtent - visibleSrcExtent) / 2f,
            srcExtent = visibleSrcExtent,
            dst = dstOrigin,
            dstExtent = dstExtent,
        )
    } else {
        AxisPlacement(
            src = 0f,
            srcExtent = srcExtent,
            dst = dstOrigin + (dstExtent - scaledExtent) / 2f,
            dstExtent = scaledExtent,
        )
    }
}
