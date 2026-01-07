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

package androidx.pdf.annotation.highlights.utils

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import androidx.pdf.PdfDocument
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.content.PdfPageTextContent

/** Applies a [Matrix] transformation to this point, returning a new [PointF]. */
internal fun PointF.applyTransform(transform: Matrix): PointF {
    val pointArr = floatArrayOf(this.x, this.y)
    transform.mapPoints(pointArr)
    return PointF(pointArr[0], pointArr[1])
}

/** Calculates the union bounding box of all rectangles. */
internal fun List<RectF>.computeBoundingBox(): RectF {
    if (isEmpty()) return RectF()

    val boundingBox = RectF(this[0])
    for (i in 1 until size) {
        boundingBox.union(this[i])
    }
    return boundingBox
}

/** Converts a list of [RectF] bounds into [PathPdfObject]s. */
internal fun List<RectF>.toPathPdfObjects(color: Int): List<PathPdfObject> {
    return map { rect ->
        PathPdfObject(
            brushColor = color,
            brushWidth = 0f,
            inputs =
                listOf(
                    PathPdfObject.PathInput(rect.left, rect.top),
                    PathPdfObject.PathInput(rect.right, rect.top),
                    PathPdfObject.PathInput(rect.right, rect.bottom),
                    PathPdfObject.PathInput(rect.left, rect.bottom),
                    PathPdfObject.PathInput(rect.left, rect.top),
                ),
        )
    }
}

/**
 * Calculates the rectangular bounds of text selection on a page.
 *
 * @param pageNum The 0-based index of the page.
 * @param startPdfPoint The starting point of the selection in PDF coordinates.
 * @param currentPdfPoint The current (end) point of the selection in PDF coordinates.
 * @return A list of [RectF] for the selected text, or an empty list if no text is selected.
 */
internal suspend fun PdfDocument.calculateHighlightRects(
    pageNum: Int,
    startPdfPoint: PointF,
    currentPdfPoint: PointF,
): List<RectF> {
    //  TODO(b/470235782): Handle exceptions from the remote PDF document service
    val selection =
        getSelectionBounds(pageNum, startPdfPoint, currentPdfPoint) ?: return emptyList()

    return selection.selectedContents.filterIsInstance<PdfPageTextContent>().flatMap { it.bounds }
}
