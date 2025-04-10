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
import android.graphics.PointF
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.pdf.R
import androidx.pdf.view.fastscroll.getDimensions
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PdfViewPaginationTest {
    @After
    fun tearDown() {
        PdfViewTestActivity.onCreateCallback = {}
    }

    @Test
    fun testPageVisibility() = runTest {
        // Layout at 500x1000, and expect to see pages [0, 4] at 500x200
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 200) })
        setupPdfView(500, 1000, pdfDocument)

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            pdfDocument.waitForLayout(untilPage = 4)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 5)
            close()
        }
    }

    @Test
    fun testPageVisibility_withoutPdfDocument() {
        setupPdfView(500, 1000, null)

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 0)
            close()
        }
    }

    @Test
    fun testPageVisibility_onSizeDecreased() = runTest {
        // Layout at 500x1000 initially, and expect to see pages [0, 3] at 500x300
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 300) })
        setupPdfView(500, 1000, pdfDocument)
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            pdfDocument.waitForLayout(untilPage = 3)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 4)

            // Reduce size to 100x200, which will update the zoom according to new width
            // i.e. 100/500 -> 0.2 clamped to min zoom = 0.25.
            // With 0.25% zoom, expect to see pages [0, 2] at 100x200(each page height = 300 * 0.25
            // = 75)
            onActivity { activity ->
                activity.findViewById<View>(PDF_VIEW_ID).apply {
                    measure(
                        MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(200, MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, 100, 200)
                }
            }

            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 2)
            close()
        }
    }

    @Test
    fun testPageVisibility_onScrollChanged() = runTest {
        // Layout at 1000x2000 initially, and expect to see pages [0, 3] at 1000x500
        val pdfDocument = FakePdfDocument(List(10) { Point(1000, 500) })
        setupPdfView(1000, 2000, pdfDocument)

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            var topPageMargin = 0

            this.onActivity { activity ->
                topPageMargin = activity.getDimensions(R.dimen.top_page_margin).toInt()
            }

            pdfDocument.waitForLayout(untilPage = 3)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 4)

            // Scroll until the viewport spans [500, 2500] vertically and expect to see pages [1, 4]
            Espresso.onView(withId(PDF_VIEW_ID)).scrollByY(500 + topPageMargin)
            pdfDocument.waitForLayout(untilPage = 4)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 1, visiblePages = 4)

            close()
        }
    }

    @Test
    fun testPageVisibility_onZoomChanged() = runTest {
        // Layout at 100x550 initially, and expect to see pages [0, 5] at 100x80
        val pdfDocument = FakePdfDocument(List(10) { Point(100, 80) })
        setupPdfView(100, 550, pdfDocument)

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            pdfDocument.waitForLayout(untilPage = 5)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 6)

            // Set zoom to 2f and expect to see pages [0, 2]
            Espresso.onView(withId(PDF_VIEW_ID)).zoomTo(2f)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 3)

            close()
        }
    }

    @Test
    fun testScrollToPage() = runTest {
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
        setupPdfView(500, 1000, pdfDocument)
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {

            // Scroll to page 9
            Espresso.onView(withId(PDF_VIEW_ID)).scrollToPage(9)
            pdfDocument.waitForLayout(untilPage = 9)

            // Expect to see only page 9
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 9, visiblePages = 1)
            close()
        }
    }

    @Test
    fun testScrollToPosition() = runTest {
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
        setupPdfView(500, 1000, pdfDocument)
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {

            // Scroll to the top of page 9
            Espresso.onView(withId(PDF_VIEW_ID)).scrollToPosition(PdfPoint(9, PointF(0F, 0F)))
            pdfDocument.waitForLayout(untilPage = 9)

            // Expect to see pages 8 and 9
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 8, visiblePages = 2)
            close()
        }
    }
}

/** Create, measure, and layout a [PdfView] at the specified [width] and [height] */
private fun setupPdfView(width: Int, height: Int, fakePdfDocument: FakePdfDocument?) {
    PdfViewTestActivity.onCreateCallback = { activity ->
        val container = FrameLayout(activity)
        container.addView(
            PdfView(activity).apply {
                pdfDocument = fakePdfDocument
                id = PDF_VIEW_ID
            },
            ViewGroup.LayoutParams(width, height)
        )
        activity.setContentView(container)
    }
}

/** Arbitrary fixed ID for PdfView */
private const val PDF_VIEW_ID = 123456789
