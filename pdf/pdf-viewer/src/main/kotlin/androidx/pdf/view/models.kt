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
import android.os.Parcel
import androidx.annotation.ColorInt
import androidx.annotation.RestrictTo
import androidx.core.os.ParcelCompat

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
 * Writes a [PdfRect] to [dest] with [flags].
 *
 * Not part of the public API because public APIs cannot be [android.os.Parcelable]
 */
internal fun PdfRect.writeToParcel(dest: Parcel, flags: Int) {
    dest.writeInt(pageNum)
    dest.writeParcelable(pageRect, flags)
}

/**
 * Reads a [PdfRect] from [source], using [loader].
 *
 * Not part of the public API because public APIs cannot be [android.os.Parcelable]
 */
internal fun pdfRectFromParcel(source: Parcel, loader: ClassLoader?): PdfRect {
    val pageNum = source.readInt()
    val rect = requireNotNull(ParcelCompat.readParcelable(source, loader, RectF::class.java))
    return PdfRect(pageNum, rect)
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

/**
 * Writes a [PdfPoint] to [dest] with [flags].
 *
 * Not part of the public API because public APIs cannot be [android.os.Parcelable]
 */
internal fun PdfPoint.writeToParcel(dest: Parcel, flags: Int) {
    dest.writeInt(pageNum)
    dest.writeParcelable(pagePoint, flags)
}

/**
 * Reads a [PdfPoint] from [source], using [loader].
 *
 * Not part of the public API because public APIs cannot be [android.os.Parcelable]
 */
internal fun pdfPointFromParcel(source: Parcel, loader: ClassLoader?): PdfPoint {
    val pageNum = source.readInt()
    val pagePoint = requireNotNull(ParcelCompat.readParcelable(source, loader, PointF::class.java))
    return PdfPoint(pageNum, pagePoint)
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

@RestrictTo(RestrictTo.Scope.LIBRARY)
/** Represents PDF content that has been selected */
public interface Selection {
    /**
     * The [PdfRect] bounds of this selection. May contain multiple [PdfRect] if this selection
     * spans multiple discrete areas within the PDF. Consider for example any selection spanning
     * multiple pages, or a text selection spanning multiple lines on the same page.
     */
    public val bounds: List<PdfRect>
}

/** Represents text content that has been selected */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class TextSelection(public val text: String, override val bounds: List<PdfRect>) :
    Selection {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is TextSelection) return false

        if (other.text != this.text) return false
        if (other.bounds != this.bounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + bounds.hashCode()
        return result
    }

    override fun toString(): String {
        return "TextSelection: text $text bounds $bounds"
    }
}

/**
 * Writes a [TextSelection] to [dest] with [flags].
 *
 * Not part of the public API because public APIs cannot be [android.os.Parcelable]
 */
internal fun TextSelection.writeToParcel(dest: Parcel, flags: Int) {
    dest.writeString(text)
    dest.writeInt(bounds.size)
    for (bound in bounds) {
        bound.writeToParcel(dest, flags)
    }
}

/**
 * Reads a [TextSelection] from [source], using [loader].
 *
 * Not part of the public API because public APIs cannot be [android.os.Parcelable]
 */
internal fun textSelectionFromParcel(source: Parcel, loader: ClassLoader?): TextSelection {
    val text = requireNotNull(source.readString())
    val boundsSize = source.readInt()
    val bounds = mutableListOf<PdfRect>()
    for (i in 0 until boundsSize) {
        bounds.add(pdfRectFromParcel(source, loader))
    }
    return TextSelection(text, bounds.toList())
}
