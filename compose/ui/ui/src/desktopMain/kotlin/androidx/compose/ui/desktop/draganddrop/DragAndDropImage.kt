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

package androidx.compose.ui.desktop.draganddrop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal class DragAndDropImage(
    private val size: Size,
    private val density: Density,
    private val layoutDirection: LayoutDirection,
    private val drawDragDecoration: DrawScope.() -> Unit
) {

    /**
     * The decoration is drawn at [size]; [canvasSize] can be larger to pad the bitmap, leaving the
     * extra edge transparent. On macOS this is used to round the canvas up to even pixels, which
     * works around blurry drag images (AIR-5862).
     */
    internal fun encodeToPng(canvasSize: Size = size): ByteArray? {
        val imageBitmap = ImageBitmap(
            width = canvasSize.width.roundToInt(),
            height = canvasSize.height.roundToInt()
        )
        val canvas = Canvas(imageBitmap)
        val canvasScope = CanvasDrawScope()
        canvasScope.draw(density, layoutDirection, canvas, size, drawDragDecoration)
        return imageBitmap.encodeToBytes(EncodedImageFormat.PNG, 100)
    }

    private fun ImageBitmap.encodeToBytes(format: EncodedImageFormat, quality: Int): ByteArray? {
        return Image.makeFromBitmap(this.asSkiaBitmap()).encodeToData(format, quality)?.bytes
    }
}