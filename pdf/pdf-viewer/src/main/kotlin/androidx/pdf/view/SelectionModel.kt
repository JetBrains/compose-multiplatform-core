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

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.pdf.PdfDocument
import androidx.pdf.content.PageSelection

/** Value class containing all data necessary to display UI related to content selection */
@SuppressLint("BanParcelableUsage")
internal class SelectionModel
@VisibleForTesting
internal constructor(
    val selection: Selection,
    val startBoundary: UiSelectionBoundary,
    val endBoundary: UiSelectionBoundary
) : Parcelable {
    constructor(
        parcel: Parcel
    ) : this(
        selection = textSelectionFromParcel(parcel, TextSelection::class.java.classLoader),
        startBoundary = UiSelectionBoundary(parcel),
        endBoundary = UiSelectionBoundary(parcel),
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        (selection as TextSelection).writeToParcel(dest, flags)
        startBoundary.writeToParcel(dest, flags)
        endBoundary.writeToParcel(dest, flags)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is SelectionModel) return false

        if (other.selection != selection) return false
        if (other.startBoundary != startBoundary) return false
        if (other.endBoundary != endBoundary) return false
        return true
    }

    override fun hashCode(): Int {
        var result = selection.hashCode()
        result = 31 * result + startBoundary.hashCode()
        result = 31 * result + endBoundary.hashCode()
        return result
    }

    companion object {
        /** Produces a [SelectionModel] from a single [PageSelection] on a single page */
        // TODO(b/386398335) Add support for creating a SelectionModel from selections on 2 pages
        fun fromSinglePageSelection(pageSelection: PageSelection): SelectionModel {
            val startPoint =
                requireNotNull(pageSelection.start.point) { "PageSelection is missing start point" }
            val stopPoint =
                requireNotNull(pageSelection.stop.point) { "PageSelection is missing end point" }
            return SelectionModel(
                pageSelection.toViewSelection(),
                UiSelectionBoundary(
                    PdfPoint(
                        pageSelection.page,
                        PointF(startPoint.x.toFloat(), startPoint.y.toFloat())
                    ),
                    pageSelection.start.isRtl
                ),
                UiSelectionBoundary(
                    PdfPoint(
                        pageSelection.page,
                        PointF(stopPoint.x.toFloat(), stopPoint.y.toFloat())
                    ),
                    pageSelection.stop.isRtl
                ),
            )
        }

        /**
         * Returns a [Selection] as exposed in the [PdfView] API from a [PageSelection] as produced
         * by the [PdfDocument] API
         */
        private fun PageSelection.toViewSelection(): Selection {
            val flattenedBounds =
                this.selectedTextContents.map { it.bounds }.flatten().map { PdfRect(this.page, it) }
            val concatenatedText = this.selectedTextContents.joinToString(" ") { it.text }
            return TextSelection(concatenatedText, flattenedBounds)
        }

        @JvmField
        val CREATOR =
            object : Parcelable.Creator<SelectionModel> {
                override fun createFromParcel(parcel: Parcel): SelectionModel {
                    return SelectionModel(parcel)
                }

                override fun newArray(size: Int): Array<SelectionModel?> {
                    return arrayOfNulls(size)
                }
            }
    }
}

/**
 * Represents a selection boundary that includes the page on which it exists and the point at which
 * it exists (as [PdfPoint]), as well as the direction of the selection ([isRtl] is true if the
 * selection was made in a right-to-left direction).
 */
@SuppressLint("BanParcelableUsage")
internal class UiSelectionBoundary(val location: PdfPoint, val isRtl: Boolean) : Parcelable {
    constructor(
        parcel: Parcel
    ) : this(
        pdfPointFromParcel(parcel, PdfPoint::class.java.classLoader),
        parcel.readBoolean(),
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        location.writeToParcel(dest, flags)
        dest.writeBoolean(isRtl)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is UiSelectionBoundary) return false

        if (other.location != this.location) return false
        if (other.isRtl != this.isRtl) return false
        return true
    }

    override fun hashCode(): Int {
        var result = location.hashCode()
        result = 31 * result + isRtl.hashCode()
        return result
    }

    companion object CREATOR : Parcelable.Creator<UiSelectionBoundary> {
        override fun createFromParcel(parcel: Parcel): UiSelectionBoundary {
            return UiSelectionBoundary(parcel)
        }

        override fun newArray(size: Int): Array<UiSelectionBoundary?> {
            return arrayOfNulls(size)
        }
    }
}
