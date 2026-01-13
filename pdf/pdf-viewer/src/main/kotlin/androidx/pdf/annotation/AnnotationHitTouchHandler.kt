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

package androidx.pdf.annotation

import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.StampAnnotation
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Handles touch events to detect hits on existing annotations.
 *
 * This handler converts view coordinates to PDF page coordinates and checks for intersections with
 * annotations currently rendered by [AnnotationsView].
 */
internal class AnnotationHitTouchHandler() {

    private var onAnnotationHitListener: OnAnnotationHitListener? = null

    /** Registers a listener to be notified of annotation hit events. */
    fun setListener(listener: OnAnnotationHitListener) {
        onAnnotationHitListener = listener
    }

    /** Handles the touch event to perform hit detection. */
    internal fun handleTouch(annotationsView: AnnotationsView, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val pageInfoProvider = annotationsView.pageInfoProvider ?: return false
                val pageInfo =
                    pageInfoProvider.getPageInfoFromViewCoordinates(event.x, event.y)
                        ?: return false

                val hitAnnotation = findAnnotationAtPoint(annotationsView, pageInfo, event)

                if (hitAnnotation != null) {
                    // TODO(b/470857248): Replace with actual keyedPdfAnnotation from
                    // annotationsView.
                    val keyedPdfAnnotation = KeyedPdfAnnotation(KEY, hitAnnotation)
                    onAnnotationHitListener?.onAnnotationHit(keyedPdfAnnotation)
                    return true
                }
                false
            }
            else -> false
        }
    }

    /** Finds the top-most annotation at the given touch point using precise path intersection. */
    private fun findAnnotationAtPoint(
        annotationsView: AnnotationsView,
        pageInfo: PageInfoProvider.PageInfo,
        event: MotionEvent,
    ): PdfAnnotation? {
        // Use the system's touch slop for a density-aware tolerance.
        val touchSlop: Int = ViewConfiguration.get(annotationsView.context).scaledTouchSlop

        // Calculate the Touch Delta in PDF coordinates using touchSlop for tolerance.
        val touchRectView =
            RectF(
                event.x - touchSlop,
                event.y - touchSlop,
                event.x + touchSlop,
                event.y + touchSlop,
            )

        val touchRectPdf = RectF()
        pageInfo.viewToPageTransform.mapRect(touchRectPdf, touchRectView)
        val touchRegion = touchRectPdf.toRegion()

        val annotations =
            annotationsView.annotations.get(pageInfo.pageNum)?.annotations ?: return null

        // Iterate in reverse Z-order to find the top-most annotation.
        return annotations.asReversed().firstOrNull { annotation ->
            isAnnotationHit(annotation, touchRegion, touchRectPdf)
        }
    }

    /**
     * Determines if the given touch point (as a Region and RectF) intersects with an annotation.
     */
    private fun isAnnotationHit(
        annotation: PdfAnnotation,
        touchRegion: Region,
        touchRectPdf: RectF,
    ): Boolean {
        return when (annotation) {
            is StampAnnotation -> {
                // Fast Bounding Box check.
                if (RectF.intersects(annotation.bounds, touchRectPdf)) {
                    annotation.pdfObjects.any { pdfObject ->
                        if (pdfObject is PathPdfObject) {
                            val path =
                                Path().apply {
                                    pdfObject.inputs.forEachIndexed { index, input ->
                                        if (index == 0) moveTo(input.x, input.y)
                                        else lineTo(input.x, input.y)
                                    }
                                }

                            val pathRegion = Region()
                            val clip = annotation.bounds.toRegion()
                            pathRegion.setPath(path, clip)

                            // Intersect the path region with our touch square region.
                            return pathRegion.op(touchRegion, Region.Op.INTERSECT)
                        }
                        false
                    }
                }
                false
            }
            else -> false
        }
    }

    /** Extension to convert RectF to a Region with outward rounding. */
    private fun RectF.toRegion(): Region {
        return Region(
            floor(left).toInt(),
            floor(top).toInt(),
            ceil(right).toInt(),
            ceil(bottom).toInt(),
        )
    }

    private companion object {
        const val KEY = "key"
    }
}
