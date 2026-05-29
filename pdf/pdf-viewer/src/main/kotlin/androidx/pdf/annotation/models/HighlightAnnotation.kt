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

package androidx.pdf.annotation.models

import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RestrictTo

/**
 * Represents a Highlight Annotation in a PDF document.
 *
 * @property pageNum The page number (0-indexed) where this annotation is located.
 * @property bounds The list of bounding [RectF] of the annotation denoting areas on the page which
 *   are highlighted.
 * @property color The color of the highlight.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class HighlightAnnotation(
    pageNum: Int,
    public val bounds: List<RectF>,
    public val color: Int,
) : PdfAnnotation(pageNum) {

    private constructor(
        parcel: Parcel
    ) : this(
        pageNum = parcel.readInt(),
        bounds = parcel.createTypedArrayList(RectF.CREATOR)!!,
        color = parcel.readInt(),
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightAnnotation) return false

        return pageNum == other.pageNum && bounds == other.bounds && color == other.color
    }

    override fun hashCode(): Int {
        var result = bounds.hashCode()
        result = 31 * result + color
        return result
    }

    override fun describeContents(): Int = 0

    public fun writeHighlightAnnotationToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(pageNum)
        dest.writeTypedList(bounds)
        dest.writeInt(color)
    }

    internal companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<HighlightAnnotation> =
            object : Parcelable.Creator<HighlightAnnotation> {
                override fun createFromParcel(source: Parcel): HighlightAnnotation {
                    return HighlightAnnotation(source)
                }

                override fun newArray(size: Int): Array<HighlightAnnotation?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
