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

package androidx.pdf.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.pdf.PdfDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** A single PDF page that knows how to render and draw itself */
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class Page(
    /** The 0-based index of this page in the PDF */
    private val pageNum: Int,
    /** The size of this PDF page, in content coordinates */
    private val size: Point,
    /** The [PdfDocument] this [Page] belongs to */
    private val pdfDocument: PdfDocument,
    /** The [CoroutineScope] to use for background work */
    private val backgroundScope: CoroutineScope,
    /** A function to call when the [PdfView] hosting this [Page] ought to invalidate itself */
    private val onPageUpdate: () -> Unit,
) {
    init {
        require(pageNum >= 0) { "Invalid negative page" }
    }

    /**
     * Pre-allocated [Paint] to draw [Highlight]s, color is changed at drawing time to the value
     * defined by the [Highlight]
     */
    private val highlightPaint = Paint().apply { style = Paint.Style.FILL }

    /**
     * Pre-allocated [RectF] used to represent the View-coordinate location of a [Highlight] during
     * drawing
     */
    private val highlightRect = RectF()

    private var isVisible: Boolean = false
    private var renderedZoom: Float? = null
    @VisibleForTesting internal var renderBitmapJob: Job? = null
    @VisibleForTesting internal var bitmap: Bitmap? = null

    fun setVisible(zoom: Float) {
        isVisible = true
        maybeUpdateBitmaps(zoom)
    }

    fun setInvisible() {
        isVisible = false
        renderBitmapJob?.cancel()
        renderBitmapJob = null
        bitmap = null
        renderedZoom = null
    }

    fun draw(canvas: Canvas, locationInView: Rect, highlights: List<Highlight>) {
        if (bitmap == null) {
            canvas.drawRect(locationInView, BLANK_PAINT)
            return
        }
        bitmap?.let { canvas.drawBitmap(it, /* src= */ null, locationInView, BMP_PAINT) }
        for (highlight in highlights) {
            // Highlight locations are defined in content coordinates, compute their location
            // in View coordinates using locationInView
            highlightRect.set(highlight.area.pageRect)
            highlightRect.offset(locationInView.left.toFloat(), locationInView.top.toFloat())
            highlightPaint.color = highlight.color
            canvas.drawRect(highlightRect, highlightPaint)
        }
    }

    private fun maybeUpdateBitmaps(zoom: Float) {
        // If we're actively rendering or have rendered a bitmap for the current zoom level, there's
        // no need to refresh bitmaps
        if (renderedZoom?.equals(zoom) == true && (bitmap != null || renderBitmapJob != null)) {
            return
        }
        renderBitmapJob?.cancel()
        // If we're not visible, don't bother fetching new bitmaps
        if (!isVisible) return
        fetchNewBitmap(zoom)
    }

    private fun fetchNewBitmap(zoom: Float) {
        val bitmapSource = pdfDocument.getPageBitmapSource(pageNum)
        renderBitmapJob =
            backgroundScope.launch {
                ensureActive()
                val width = (size.x * zoom).toInt()
                val height = (size.y * zoom).toInt()
                renderedZoom = zoom
                bitmap = bitmapSource.getBitmap(Size(width, height))
                ensureActive()
                onPageUpdate.invoke()
            }
        renderBitmapJob?.invokeOnCompletion { renderBitmapJob = null }
    }
}

/** Constant [Paint]s used in drawing */
@VisibleForTesting internal val BMP_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)
@VisibleForTesting
internal val BLANK_PAINT =
    Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
