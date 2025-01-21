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

import android.graphics.PointF
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.annotation.RestrictTo

/**
 * Represents a rectangle in PDF coordinates, where [pageNum] indicates a PDF page, and [pageRect]
 * indicates a [RectF] in PDF points within the page, with the origin existing at the top left
 * corner of the page.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class PdfRect(public val pageNum: Int, public val pageRect: RectF) {

    init {
        require(pageNum >= 0) { "Invalid negative page" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is PdfRect) return false

        if (other.pageNum != pageNum) return false
        if (other.pageRect != pageRect) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pageNum.hashCode()
        result = 31 * result + pageRect.hashCode()
        return result
    }

    override fun toString(): String {
        return "PdfRect: page $pageNum pageRect $pageRect"
    }
}

/**
 * Represents a point in PDF coordinates, where [pageNum] indicates a 0-indexed PDF page, and
 * [pagePoint] indicates a [PointF] in PDF points within the page, with the origin existing at the
 * top left corner of the page.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class PdfPoint(public val pageNum: Int, public val pagePoint: PointF) {

    init {
        require(pageNum >= 0) { "Invalid negative page" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is PdfPoint) return false

        if (other.pageNum != pageNum) return false
        if (other.pagePoint != pagePoint) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pageNum.hashCode()
        result = 31 * result + pagePoint.hashCode()
        return result
    }

    override fun toString(): String {
        return "PdfPoint: page $pageNum pagePoint $pagePoint"
    }
}

/** Represents an [area] that should be highlighted with [color]. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class Highlight(public val area: PdfRect, @ColorInt public val color: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is Highlight) return false

        if (other.area != this.area) return false
        if (other.color != this.color) return false

        return true
    }

    override fun hashCode(): Int {
        var result = area.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }

    override fun toString(): String {
        return "Highlight: area $area color $color"
    }
}
