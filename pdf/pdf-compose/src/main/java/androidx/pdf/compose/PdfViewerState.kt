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

package androidx.pdf.compose

import android.graphics.PointF
import android.graphics.Rect
import android.util.SparseArray
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.FrequentlyChangingValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.util.keyIterator
import androidx.pdf.view.PdfPoint
import androidx.pdf.view.PdfView

/** Creates a [PdfViewerState] that is remembered across compositions using [remember] */
@Composable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun rememberPdfViewerState(): PdfViewerState = remember { PdfViewerState() }

/**
 * A state object that can be hoisted to observe and control [PdfViewer] zoom, scroll, and content
 * position.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PdfViewerState() {
    internal var pdfView: PdfView? = null
        set(value) {
            if (field === value) return
            pdfViewObserver?.let { field?.removeOnViewportChangedListener(it) }
            firstVisiblePage = 0
            visiblePagesCount = 0
            zoom = PdfView.DEFAULT_INIT_ZOOM
            pageOffsetsState.clear()
            field = value
            pdfViewObserver = PdfViewObserver().also { field?.addOnViewportChangedListener(it) }
        }

    private var pdfViewObserver: PdfViewObserver? = null

    /** The first page in the viewport, including partially-visible pages. 0-indexed. */
    @get:FrequentlyChangingValue
    public var firstVisiblePage: Int by mutableIntStateOf(0)
        private set

    /** The number of pages visible in the viewport, including partially visible pages. */
    @get:FrequentlyChangingValue
    public var visiblePagesCount: Int by mutableIntStateOf(0)
        private set

    /**
     * The zoom level of this view, as a factor of the content's natural size with when 1 pixel is
     * equal to 1 PDF point.
     */
    @get:FrequentlyChangingValue
    public var zoom: Float by mutableFloatStateOf(PdfView.DEFAULT_INIT_ZOOM)
        private set

    /** The [Offset] of each visible page, keyed by 0-indexed page number. */
    // No mutableState*Of infrastructure for primitive collection types
    @Suppress("PrimitiveInCollection")
    public val pageOffsets: Map<Int, Offset>
        @FrequentlyChangingValue get() = pageOffsetsState

    // No mutableState*Of infrastructure for primitive collection types
    @Suppress("PrimitiveInCollection")
    private val pageOffsetsState = mutableStateMapOf<Int, Offset>()

    /**
     * Returns the [PdfPoint] corresponding to [offset] in Compose coordinates, or null if no PDF
     * content has been laid out at this Offset.
     *
     * Returns null if this [PdfViewerState] is not yet associated with a [PdfViewer], or if the
     * [PdfViewer] is not associated with a [androidx.pdf.PdfDocument]
     */
    public fun toPdfPoint(offset: Offset): PdfPoint? {
        return pdfView?.viewToPdfPoint(PointF(offset.x, offset.y))
    }

    /**
     * Returns the View coordinate location of [pdfPoint], or null if that PDF content has not been
     * laid out yet.
     *
     * Returns null if this [PdfViewerState] is not yet associated with a [PdfViewer], or if the
     * [PdfViewer] is not associated with a [androidx.pdf.PdfDocument]
     */
    public fun toOffset(pdfPoint: PdfPoint): Offset? {
        return pdfView?.pdfToViewPoint(pdfPoint)?.toOffset()
    }

    private inner class PdfViewObserver() : PdfView.OnViewportChangedListener {

        override fun onViewportChanged(
            firstVisiblePage: Int,
            visiblePagesCount: Int,
            pageLocations: SparseArray<Rect>,
            zoomLevel: Float
        ) {
            this@PdfViewerState.firstVisiblePage = firstVisiblePage
            this@PdfViewerState.visiblePagesCount = visiblePagesCount
            zoom = zoomLevel

            // Clear no longer visible pages
            for (page in pageOffsetsState.keys) {
                if (!pageLocations.contains(page)) {
                    pageOffsetsState.remove(page)
                }
            }
            // Add or update new or existing pages
            for (page in pageLocations.keyIterator()) {
                pageOffsetsState.put(page, pageLocations.get(page).toOffset())
            }
        }
    }
}

private fun PointF.toOffset() = Offset(this.x, this.y)

private fun Rect.toOffset() = Offset(this.left.toFloat(), this.top.toFloat())
