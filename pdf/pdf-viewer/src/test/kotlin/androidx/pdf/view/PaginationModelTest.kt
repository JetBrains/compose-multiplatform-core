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
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@SmallTest
@RunWith(RobolectricTestRunner::class)
class PaginationModelTest {
    private val NUM_PAGES = 250
    private val PAGE_SPACING_PX = 5
    private lateinit var paginationModel: PaginationModel

    @Before
    fun setup() {
        paginationModel = PaginationModel(pageSpacingPx = PAGE_SPACING_PX, numPages = NUM_PAGES)
    }

    @Test
    fun invalidConstructorArguments() {
        assertThrows(IllegalArgumentException::class.java) {
            PaginationModel(pageSpacingPx = -1, numPages = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaginationModel(pageSpacingPx = 10, numPages = -1)
        }
    }

    @Test
    fun propertyDefaults_withNoPagesAdded() {
        assertThat(paginationModel.reach).isEqualTo(-1)
        assertThat(paginationModel.maxWidth).isEqualTo(0)
        assertThat(paginationModel.totalEstimatedHeight).isEqualTo(0)
    }

    @Test
    fun propertyValues_withSomePagesAdded() {
        var totalHeight = 0
        var maxWidth = 0
        val rng = Random(System.currentTimeMillis())
        val knownPages = NUM_PAGES / 2

        for (i in 0 until knownPages) {
            val pageSize = Point(rng.nextInt(50, 100), rng.nextInt(100, 200))
            maxWidth = max(maxWidth, pageSize.x)
            totalHeight += pageSize.y
            paginationModel.addPage(i, pageSize)
        }

        assertThat(paginationModel.reach).isEqualTo(knownPages - 1)
        // Accumulated height of all added pages, plus page spacing * known pages
        val totalKnownHeight = totalHeight + PAGE_SPACING_PX * (knownPages - 1)
        // (Average height of all known pages + page spacing) * unknown pages
        val estimatedRemainingHeight =
            ((totalHeight / knownPages) + PAGE_SPACING_PX) * (NUM_PAGES - knownPages)
        assertThat(paginationModel.totalEstimatedHeight)
            .isEqualTo(estimatedRemainingHeight + totalKnownHeight)
        assertThat(paginationModel.maxWidth).isEqualTo(maxWidth)
    }

    @Test
    fun propertyValues_withAllPagesAdded() {
        var totalHeight = 0
        var maxWidth = 0
        val rng = Random(System.currentTimeMillis())
        for (i in 0 until NUM_PAGES) {
            val pageSize = Point(rng.nextInt(50, 100), rng.nextInt(100, 200))
            maxWidth = max(maxWidth, pageSize.x)
            totalHeight += pageSize.y
            paginationModel.addPage(i, pageSize)
        }

        assertThat(paginationModel.reach).isEqualTo(NUM_PAGES - 1)
        assertThat(paginationModel.totalEstimatedHeight)
            .isEqualTo(totalHeight + PAGE_SPACING_PX * NUM_PAGES)
        assertThat(paginationModel.maxWidth).isEqualTo(maxWidth)
    }

    @Test
    fun rejectInvalidPage() {
        assertThrows(IllegalArgumentException::class.java) {
            paginationModel.addPage(0, Point(100, -1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            paginationModel.addPage(0, Point(-1, 100))
        }
        assertThrows(IllegalArgumentException::class.java) {
            paginationModel.addPage(-1, Point(100, 200))
        }
        assertThrows(IllegalArgumentException::class.java) {
            paginationModel.addPage(NUM_PAGES + 10, Point(100, 200))
        }
    }

    @Test
    fun getPageSize() {
        val sizeRng = Random(System.currentTimeMillis())
        val sizes =
            List(size = 3) { _ -> Point(sizeRng.nextInt(100, 200), sizeRng.nextInt(100, 200)) }

        sizes.forEachIndexed { pageNum, size -> paginationModel.addPage(pageNum, size) }

        sizes.forEachIndexed { pageNum, size ->
            assertThat(paginationModel.getPageSize(pageNum)).isEqualTo(size)
        }
    }

    @Test
    fun getPageSize_invalidPageNum() {
        assertThrows(IllegalArgumentException::class.java) { paginationModel.getPageSize(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            paginationModel.getPageSize(NUM_PAGES + 10)
        }
    }

    @Test
    fun getPagesInViewport_viewportAboveAllPages() {
        val pageSize = Point(100, 200)
        paginationModel.addPage(0, pageSize)
        paginationModel.addPage(1, pageSize)
        paginationModel.addPage(2, pageSize)

        val visiblePages =
            paginationModel.getPagesInViewport(viewportTop = -100, viewportBottom = 0)

        // When the viewport is above the top of this model, we expect an empty range at the
        // beginning of this model
        assertThat(visiblePages.upper).isEqualTo(0)
        assertThat(visiblePages.lower).isEqualTo(0)
    }

    @Test
    fun getPagesInViewport_viewportBelowAllPages() {
        val pageSize = Point(100, 200)
        paginationModel.addPage(0, pageSize)
        paginationModel.addPage(1, pageSize)
        paginationModel.addPage(2, pageSize)
        val contentBottom = pageSize.y * 3 + PAGE_SPACING_PX * 5

        val visiblePages =
            paginationModel.getPagesInViewport(
                viewportTop = contentBottom + 10,
                viewportBottom = contentBottom + 100
            )

        // When the viewport is below the end of this model, we expect an empty range at the last
        // known page
        assertThat(visiblePages.upper).isEqualTo(2)
        assertThat(visiblePages.lower).isEqualTo(2)
    }

    @Test
    fun getPagesInViewport_allPagesVisible() {
        val pageSize = Point(100, 200)
        paginationModel.addPage(0, pageSize)
        paginationModel.addPage(1, pageSize)
        paginationModel.addPage(2, pageSize)
        val contentBottom = pageSize.y * 3 + PAGE_SPACING_PX * 5

        val visiblePages =
            paginationModel.getPagesInViewport(viewportTop = 0, viewportBottom = contentBottom + 10)

        assertThat(visiblePages.upper).isEqualTo(2)
        assertThat(visiblePages.lower).isEqualTo(0)
    }

    @Test
    fun getPagesInViewport_onePagePartiallyVisible() {
        val pageSize = Point(100, 200)
        paginationModel.addPage(0, pageSize)
        paginationModel.addPage(1, pageSize)
        paginationModel.addPage(2, pageSize)

        val visiblePages =
            paginationModel.getPagesInViewport(viewportTop = 235, viewportBottom = 335)

        assertThat(visiblePages.upper).isEqualTo(1)
        assertThat(visiblePages.lower).isEqualTo(1)
    }

    @Test
    fun getPagesInViewport_twoPagesPartiallyVisible() {
        val pageSize = Point(100, 200)
        paginationModel.addPage(0, pageSize)
        paginationModel.addPage(1, pageSize)
        paginationModel.addPage(2, pageSize)

        val visiblePages =
            paginationModel.getPagesInViewport(viewportTop = 235, viewportBottom = 455)

        assertThat(visiblePages.upper).isEqualTo(2)
        assertThat(visiblePages.lower).isEqualTo(1)
    }

    @Test
    fun getPagesInViewport_multiplePagesVisible() {
        val pageSize = Point(100, 200)
        paginationModel.addPage(0, pageSize)
        paginationModel.addPage(1, pageSize)
        paginationModel.addPage(2, pageSize)
        paginationModel.addPage(3, pageSize)

        val visiblePages =
            paginationModel.getPagesInViewport(viewportTop = 210, viewportBottom = 840)

        assertThat(visiblePages.upper).isEqualTo(3)
        assertThat(visiblePages.lower).isEqualTo(1)
    }

    /**
     * Add 3 pages of differing sizes to the model. Set the visible area to cover the whole model.
     * Largest page should span (0, model width). Smaller pages should be placed in the middle of
     * the model horizontally. Pages should get consistent vertical spacing.
     */
    @Test
    fun getPageLocation_viewportCoversAllPages() {
        val smallSize = Point(200, 100)
        val mediumSize = Point(400, 200)
        val largeSize = Point(800, 400)
        paginationModel.addPage(0, smallSize)
        paginationModel.addPage(1, mediumSize)
        paginationModel.addPage(2, largeSize)
        val viewport = Rect(0, 0, 800, 800)

        val expectedSmLocation = Rect(300, 0, 500, 100)
        assertThat(paginationModel.getPageLocation(0, viewport)).isEqualTo(expectedSmLocation)

        val expectedMdLocation = Rect(200, 100 + PAGE_SPACING_PX, 600, 300 + PAGE_SPACING_PX)
        assertThat(paginationModel.getPageLocation(1, viewport)).isEqualTo(expectedMdLocation)

        val expectedLgLocation =
            Rect(0, 300 + (PAGE_SPACING_PX * 2), 800, 700 + (PAGE_SPACING_PX * 2))
        assertThat(paginationModel.getPageLocation(2, viewport)).isEqualTo(expectedLgLocation)
    }

    /**
     * Add 3 pages of differing sizes to the model. Set the visible area to the bottom left corner
     * of this model. Page 0 is not visible, page 1 should shift left to fit the maximum amount of
     * content on-screen, and page 2 should span [0, model width]
     */
    @Test
    fun getPageLocation_shiftPagesLargerThanViewportLeft() {
        val smallSize = Point(200, 100)
        val mediumSize = Point(400, 200)
        val largeSize = Point(800, 400)
        paginationModel.addPage(0, smallSize)
        paginationModel.addPage(1, mediumSize)
        paginationModel.addPage(2, largeSize)
        // A 300x200 section in the bottom-left corner of this model
        val viewport = Rect(0, 250, 200, 800)

        val expectedMdLocation = Rect(0, 100 + PAGE_SPACING_PX, 400, 300 + PAGE_SPACING_PX)
        assertThat(paginationModel.getPageLocation(1, viewport)).isEqualTo(expectedMdLocation)

        val expectedLgLocation =
            Rect(0, 300 + (PAGE_SPACING_PX * 2), 800, 700 + (PAGE_SPACING_PX * 2))
        assertThat(paginationModel.getPageLocation(2, viewport)).isEqualTo(expectedLgLocation)
    }

    /**
     * Add 3 pages of differing sizes to the model. Set the visible area to the bottom right corner
     * of this model. Page 0 is not visible, page 1 should shift right to fit the maximum amount of
     * content on-screen, and page 2 should span [0, model width]
     */
    @Test
    fun getPageLocation_shiftPagesLargerThanViewportRight() {
        val smallSize = Point(200, 100)
        val mediumSize = Point(400, 200)
        val largeSize = Point(800, 400)
        paginationModel.addPage(0, smallSize)
        paginationModel.addPage(1, mediumSize)
        paginationModel.addPage(2, largeSize)
        // A 300x200 section in the bottom-right corner of this model
        val viewport = Rect(600, 250, 800, 800)

        val expectedMdLocation = Rect(400, 100 + PAGE_SPACING_PX, 800, 300 + PAGE_SPACING_PX)
        assertThat(paginationModel.getPageLocation(1, viewport)).isEqualTo(expectedMdLocation)

        val expectedLgLocation =
            Rect(0, 300 + (PAGE_SPACING_PX * 2), 800, 700 + (PAGE_SPACING_PX * 2))
        assertThat(paginationModel.getPageLocation(2, viewport)).isEqualTo(expectedLgLocation)
    }

    @Test
    fun parcelable() {
        val sizeRng = Random(System.currentTimeMillis())
        val sizes =
            List(size = 3) { _ -> Point(sizeRng.nextInt(100, 200), sizeRng.nextInt(100, 200)) }
        sizes.forEachIndexed { pageNum, size -> paginationModel.addPage(pageNum, size) }

        val parcel = Parcel.obtain()
        paginationModel.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val newPaginationModel = PaginationModel.CREATOR.createFromParcel(parcel)

        assertThat(newPaginationModel.totalEstimatedHeight)
            .isEqualTo(paginationModel.totalEstimatedHeight)
        assertThat(newPaginationModel.reach).isEqualTo(paginationModel.reach)
        assertThat(newPaginationModel.maxWidth).isEqualTo(paginationModel.maxWidth)

        for (i in 0 until paginationModel.reach) {
            assertThat(newPaginationModel.getPageSize(i)).isEqualTo(paginationModel.getPageSize(i))
        }
    }
}
