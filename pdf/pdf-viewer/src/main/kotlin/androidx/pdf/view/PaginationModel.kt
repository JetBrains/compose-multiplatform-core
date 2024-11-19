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

import android.graphics.Point
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.Range
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stores the size and position of PDF pages. All dimensions and coordinates should be assumed to be
 * content coordinates and not View coordinates, unless symbol names and / or comments indicate
 * otherwise. Not thread safe; access only from the main thread.
 *
 * TODO(b/376135419) - Adapt implementation to work when page dimensions are not loaded sequentially
 */
@MainThread
@Suppress("BanParcelableUsage")
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class PaginationModel(val pageSpacingPx: Int, val numPages: Int) : Parcelable {

    init {
        require(numPages >= 0) { "Empty PDF!" }
        require(pageSpacingPx >= 0) { "Invalid spacing!" }
    }

    /** The estimated total height of this PDF */
    val totalEstimatedHeight: Int
        get() = computeEstimatedHeight()

    /** The maximum width of any page known to this model */
    var maxWidth: Int = 0

    private var _reach: Int = -1

    /** The last page whose dimensions are known to this model, 0-indexed */
    val reach: Int
        get() = _reach

    /** The dimensions of all pages known to this model, as [Point] */
    private val pages = Array(numPages) { UNKNOWN_SIZE }

    /** The top position of each page known to this model */
    private val pagePositions = IntArray(numPages) { -1 }.apply { this[0] = 0 }

    /**
     * The estimated height of any page not known to this model, i.e. the average height of all
     * pages known to this model
     */
    private var averagePageHeight = 0

    /** The total height, excluding page spacing, of all pages known to this model. */
    private var accumulatedPageHeight = 0

    /**
     * Pre-allocated Rect to avoid mutating [Rect] passed to us, without allocating a new one.
     * Notably this class is used on the drawing path and should avoid excessive allocations.
     */
    private val tmpVisibleArea = Rect()

    /**
     * The top position of each page known to this model, as a synthetic [List] to conveniently use
     * with APIs like [Collections.binarySearch]
     */
    private val pageTops: List<Int> =
        object : AbstractList<Int>() {
            override val size: Int
                get() = reach + 1

            override fun get(index: Int): Int {
                return pagePositions[index]
            }
        }

    /**
     * The bottom position of each page known to this model, as a synthetic [List] to conveniently
     * use with APIs like [Collections.binarySearch]
     */
    private val pageBottoms: List<Int> =
        object : AbstractList<Int>() {
            override val size: Int
                get() = reach + 1

            override fun get(index: Int): Int {
                return pagePositions[index] + pages[index].y
            }
        }

    constructor(parcel: Parcel) : this(parcel.readInt(), parcel.readInt()) {
        _reach = parcel.readInt()
        averagePageHeight = parcel.readInt()
        accumulatedPageHeight = parcel.readInt()
        maxWidth = parcel.readInt()
        parcel.readIntArray(pagePositions)
        parcel.readTypedArray(pages, Point.CREATOR)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(pageSpacingPx)
        parcel.writeInt(numPages)
        parcel.writeInt(_reach)
        parcel.writeInt(averagePageHeight)
        parcel.writeInt(accumulatedPageHeight)
        parcel.writeInt(maxWidth)
        parcel.writeIntArray(pagePositions)
        parcel.writeTypedArray(pages, flags)
    }

    /** Adds [pageNum] to this model at [pageSize] */
    fun addPage(pageNum: Int, pageSize: Point) {
        require(pageNum in 0 until numPages) { "Page out of range" }
        require(pageSize.y >= 0 && pageSize.x >= 0) { "Negative size page" }
        // Edge case: missing pages. This model expects pages to be added sequentially
        for (i in maxOf(_reach, 0) until pageNum) {
            if (pages[i] == UNKNOWN_SIZE) {
                pages[i] = pageSize
            }
        }
        if (pageSize.x > maxWidth) {
            maxWidth = pageSize.x
        }
        pages[pageNum] = pageSize
        // Defensive: never set _reach to a smaller value, if pages are loaded out of order
        _reach = max(_reach, pageNum)
        accumulatedPageHeight += pageSize.y
        averagePageHeight = accumulatedPageHeight / maxOf(_reach + 1, 1)

        if (pageNum > 0) {
            pagePositions[pageNum] =
                pagePositions[pageNum - 1] + pages[pageNum - 1].y + pageSpacingPx
        }
    }

    /** Returns the size of [pageNum] in content coordinates */
    fun getPageSize(pageNum: Int): Point {
        require(pageNum in 0 until numPages) { "Page out of range" }
        return pages[pageNum]
    }

    /**
     * Returns a [Range] between the first and last pages that should be visible between
     * [viewportTop] and [viewportBottom], which are expected to be the top and bottom content
     * coordinates of the viewport.
     */
    fun getPagesInViewport(viewportTop: Int, viewportBottom: Int): Range<Int> {
        // If the viewport is below all pages, return an empty range at the bottom of this model
        if (reach > 0 && viewportTop > pageBottoms[reach - 1]) {
            return Range(min(reach, numPages - 1), min(reach, numPages - 1))
        }
        // If the viewport is above all pages, return an empty range at the top of this model
        if (viewportBottom < pageTops[0]) {
            return Range(0, 0)
        }
        val rangeStart = abs(Collections.binarySearch(pageBottoms, viewportTop) + 1)
        val rangeEnd = abs(Collections.binarySearch(pageTops, viewportBottom) + 1) - 1

        if (rangeEnd < rangeStart) {
            val midPoint = Collections.binarySearch(pageTops, (viewportTop + viewportBottom) / 2)
            val page = maxOf(abs(midPoint + 1) - 1, 0)
            return Range(page, page)
        }

        return Range(rangeStart, rangeEnd)
    }

    /** Returns the location of the page in content coordinates */
    fun getPageLocation(pageNum: Int, viewport: Rect): Rect {
        // We care about the intersection between what's visible and the coordinates of this model
        tmpVisibleArea.set(viewport)
        tmpVisibleArea.intersect(0, 0, maxWidth, totalEstimatedHeight)
        val page = pages[pageNum]
        var left = 0
        var right: Int = maxWidth
        val top = pagePositions[pageNum]
        val bottom = top + page.y
        // this page is smaller than the view's width, it may slide left or right.
        if (page.x < tmpVisibleArea.width()) {
            // page is smaller than the view: center (but respect min left margin)
            left = Math.max(left, tmpVisibleArea.left + (tmpVisibleArea.width() - page.x) / 2)
        } else {
            // page is larger than view: scroll proportionally between the margins.
            if (tmpVisibleArea.right > right) {
                left = right - page.x
            } else if (tmpVisibleArea.left > left) {
                left = tmpVisibleArea.left * (right - page.x) / (right - tmpVisibleArea.width())
            }
        }
        right = left + page.x

        val ret = Rect(left, top, right, bottom)
        return ret
    }

    private fun computeEstimatedHeight(): Int {
        return if (_reach < 0) {
            0
        } else if (_reach == numPages - 1) {
            val lastPageHeight = pages[_reach].y
            pagePositions[_reach] + lastPageHeight + (pageSpacingPx)
            // Otherwise, we have to guess
        } else {
            val totalKnownHeight = pagePositions[_reach] + pages[_reach].y
            val estimatedRemainingHeight =
                (averagePageHeight + pageSpacingPx) * (numPages - _reach - 1)
            totalKnownHeight + estimatedRemainingHeight
        }
    }

    companion object {
        /** Sentinel value for the size of a page unknown to this model */
        val UNKNOWN_SIZE = Point(-1, -1)

        @JvmField
        val CREATOR: Parcelable.Creator<PaginationModel> =
            object : Parcelable.Creator<PaginationModel> {
                override fun createFromParcel(parcel: Parcel): PaginationModel {
                    return PaginationModel(parcel)
                }

                override fun newArray(size: Int): Array<PaginationModel?> {
                    return arrayOfNulls(size)
                }
            }
    }

    override fun describeContents(): Int {
        return 0
    }
}
