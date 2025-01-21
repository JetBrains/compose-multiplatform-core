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
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.pdf.PdfDocument
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PageTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val pdfDocument =
        mock<PdfDocument> {
            on { getPageBitmapSource(any()) } doAnswer
                { invocation ->
                    FakeBitmapSource(invocation.getArgument(0))
                }
        }

    private val canvasSpy = spy(Canvas())

    private var invalidationCounter = 0
    private val invalidationTracker: () -> Unit = { invalidationCounter++ }
    private lateinit var page: Page

    @Before
    fun setup() {
        // Cancel any work from previous tests, and reset tracking variables
        testDispatcher.cancelChildren()
        invalidationCounter = 0

        page = Page(0, size = PAGE_SIZE, pdfDocument, testScope, invalidationTracker)
    }

    @Test
    fun setVisible_fromInvisible() {
        // Set the page to visible
        page.setVisible(zoom = 1.0F)

        // Make sure we start and finish fetching a Bitmap
        assertThat(page.renderBitmapJob).isNotNull()
        testDispatcher.scheduler.runCurrent()
        assertThat(page.renderBitmapJob).isNull()

        // Make we fetched the right bitmap
        assertThat(page.bitmap).isNotNull()
        assertThat(page.bitmap?.width).isEqualTo(PAGE_SIZE.x)
        assertThat(page.bitmap?.height).isEqualTo(PAGE_SIZE.y)
    }

    @Test
    fun setVisible_fromVisible_noNewBitmaps() {
        // Set the page to visible once, and make sure we fetched the correct Bitmap
        page.setVisible(zoom = 1.0F)

        testDispatcher.scheduler.runCurrent()
        assertThat(page.bitmap).isNotNull()
        assertThat(page.bitmap?.width).isEqualTo(PAGE_SIZE.x)
        assertThat(page.bitmap?.height).isEqualTo(PAGE_SIZE.y)

        // Set the page to visible again, at the same zoom level, and make sure we don't fetch or
        // start fetching a new Bitmap
        page.setVisible(zoom = 1.0F)

        assertThat(page.renderBitmapJob).isNull()
        assertThat(page.bitmap).isNotNull()
        assertThat(page.bitmap?.width).isEqualTo(PAGE_SIZE.x)
        assertThat(page.bitmap?.height).isEqualTo(PAGE_SIZE.y)

        // 1 total invalidation from 1 Bitmap prepared
        assertThat(invalidationCounter).isEqualTo(1)
    }

    @Test
    fun setVisible_fromVisible_fetchNewBitmaps() {
        // Set the page to visible once, at 1.0 zoom, and make sure we fetched the correct Bitmap
        page.setVisible(zoom = 1.0F)

        testDispatcher.scheduler.runCurrent()
        assertThat(page.bitmap).isNotNull()
        assertThat(page.bitmap?.width).isEqualTo(PAGE_SIZE.x)
        assertThat(page.bitmap?.height).isEqualTo(PAGE_SIZE.y)
        assertThat(invalidationCounter).isEqualTo(1)

        // Set the page to visible again, but this time at 2.0 zoom, and make sure we fetch a
        // _new_ Bitmap
        page.setVisible(zoom = 2.0F)

        assertThat(page.renderBitmapJob).isNotNull()
        testDispatcher.scheduler.runCurrent()
        assertThat(page.bitmap).isNotNull()
        assertThat(page.bitmap?.width).isEqualTo(PAGE_SIZE.x * 2)
        assertThat(page.bitmap?.height).isEqualTo(PAGE_SIZE.y * 2)

        // 2 total invalidations from 2 Bitmaps prepared
        assertThat(invalidationCounter).isEqualTo(2)
    }

    @Test
    fun setInvisible() {
        // Set the page to visible, and make sure we start fetching a bitmap
        page.setVisible(zoom = 1.0F)
        assertThat(page.renderBitmapJob).isNotNull()

        // Set the page to invisible, make sure we stop fetching the bitmap, and make sure internal
        // state is updated appropriately
        page.setInvisible()
        testDispatcher.scheduler.runCurrent()
        assertThat(page.renderBitmapJob).isNull()
        assertThat(page.bitmap).isNull()

        // 0 invalidations, as the initial job to fetch a Bitmap should have been cancelled
        assertThat(invalidationCounter).isEqualTo(0)
    }

    @Test
    fun draw_withoutBitmap() {
        // Notably we don't call testDispatcher.scheduler.runCurrent(), so we start, but do not
        // finish, fetching a Bitmap
        page.setVisible(zoom = 1.5F)
        val locationInView = Rect(-60, 125, -60 + PAGE_SIZE.x, 125 + PAGE_SIZE.y)

        page.draw(canvasSpy, locationInView, listOf())

        verify(canvasSpy).drawRect(eq(locationInView), eq(BLANK_PAINT))
    }

    @Test
    fun draw_withBitmap() {
        page.setVisible(zoom = 1.5F)
        testDispatcher.scheduler.runCurrent()
        val locationInView = Rect(50, -100, 50 + PAGE_SIZE.x, -100 + PAGE_SIZE.y)

        page.draw(canvasSpy, locationInView, listOf())

        verify(canvasSpy)
            .drawBitmap(
                argThat { bitmap ->
                    bitmap.width == (PAGE_SIZE.x * 1.5).toInt() &&
                        bitmap.height == (PAGE_SIZE.y * 1.5).toInt()
                },
                isNull(),
                eq(locationInView),
                eq(BMP_PAINT)
            )
    }

    @Test
    fun draw_withHighlight() {
        page.setVisible(zoom = 1.5F)
        testDispatcher.scheduler.runCurrent()
        val leftEdgeInView = 650
        val topEdgeInView = -320
        val locationInView =
            Rect(
                leftEdgeInView,
                topEdgeInView,
                leftEdgeInView + PAGE_SIZE.x,
                topEdgeInView + PAGE_SIZE.y
            )
        val highlight = Highlight(PdfRect(pageNum = 0, RectF(10F, 0F, 30F, 20F)), Color.YELLOW)

        page.draw(canvasSpy, locationInView, listOf(highlight))

        // We expect to draw the highlight at its location in page coordinates offset by the page's
        // location in View
        val expectedHighlightLoc =
            RectF().apply {
                set(highlight.area.pageRect)
                offset(leftEdgeInView.toFloat(), topEdgeInView.toFloat())
            }
        verify(canvasSpy).drawRect(eq(expectedHighlightLoc), argThat { color == highlight.color })

        // Foot note: It's impossible to verify the behavior when drawing multiple highlights using
        // Mockito's Spy functionality, as it captures arguments by reference, and we re-use
        // Rect and Paint arguments to canvas.drawRect() to avoid allocations on the drawing path
    }
}

val PAGE_SIZE = Point(100, 150)

/**
 * Fake implementation of [PdfDocument.BitmapSource] that always produces a blank bitmap of the
 * requested size.
 */
private class FakeBitmapSource(override val pageNumber: Int) : PdfDocument.BitmapSource {

    override suspend fun getBitmap(scaledPageSizePx: Size, tileRegion: Rect?): Bitmap {
        return if (tileRegion != null) {
            Bitmap.createBitmap(tileRegion.width(), tileRegion.height(), Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(
                scaledPageSizePx.width,
                scaledPageSizePx.height,
                Bitmap.Config.ARGB_8888
            )
        }
    }

    override fun close() {
        /* no-op, fake */
    }
}
