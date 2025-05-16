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
import android.graphics.Rect
import android.util.SparseArray
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
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PdfViewPaginationTest {
    var topPageMarginPx: Int = 0
    var pageMarginPx: Int = 0

    @After
    fun tearDown() {
        PdfViewTestActivity.onCreateCallback = {}
    }

    @Test
    fun testPageVisibility() = runTest {
        // Layout at 500x1000, and expect to see pages [0, 4] at 500x200
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 200) })
        setupPdfView(width = 500, height = 1000, pdfDocument)

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            pdfDocument.waitForLayout(untilPage = 4)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 5)
            close()
        }
    }

    @Test
    fun testPageVisibility_withoutPdfDocument() {
        setupPdfView(500, height = 1000, fakePdfDocument = null)

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
        setupPdfView(width = 500, height = 1000, pdfDocument)
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
        setupPdfView(width = 1000, height = 2000, pdfDocument)

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            pdfDocument.waitForLayout(untilPage = 3)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 4)

            // Scroll until the viewport spans [500, 2500] vertically and expect to see pages [1, 4]
            Espresso.onView(withId(PDF_VIEW_ID)).scrollByY(500 + topPageMarginPx)
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
        setupPdfView(width = 100, height = 550, pdfDocument)

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
        setupPdfView(width = 500, height = 1000, pdfDocument)
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
        setupPdfView(width = 500, height = 1000, pdfDocument)
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

    @Test
    fun testViewportListener_instantScroll() = runTest {
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
        setupPdfView(width = 500, height = 1000, pdfDocument)
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            val listener = PdfViewportListener()
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID).addOnViewportChangedListener(listener)
            }

            Espresso.onView(withId(PDF_VIEW_ID)).scrollToPage(9)
            pdfDocument.waitForLayout(untilPage = 9)

            assertThat(listener.firstVisiblePage).isEqualTo(9)
            assertThat(listener.firstPageLocation).isEqualTo(Rect(0, 0, 500, 1000))
            assertThat(listener.zoomLevel).isEqualTo(1.0F)
            assertThat(listener.updates).isEqualTo(1)

            onActivity { activity ->
                activity
                    .findViewById<PdfView>(PDF_VIEW_ID)
                    .removeOnViewportChangedListener(listener)
            }
            close()
        }
    }

    @Test
    fun testViewportListener_smoothScroll() = runTest {
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
        setupPdfView(width = 500, height = 1000, pdfDocument)
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            val listener = PdfViewportListener()
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID).addOnViewportChangedListener(listener)
            }

            // Scroll smoothly past page 0
            Espresso.onView(withId(PDF_VIEW_ID))
                .smoothScrollBy(totalPixels = 1000 + topPageMarginPx, numSteps = 10)
            pdfDocument.waitForLayout(untilPage = 1)

            assertThat(listener.firstVisiblePage).isEqualTo(1)
            assertThat(listener.firstPageLocation)
                .isEqualTo(Rect(0, pageMarginPx, 500, 1000 + pageMarginPx))
            assertThat(listener.zoomLevel).isEqualTo(1.0F)
            assertThat(listener.updates).isEqualTo(10)

            onActivity { activity ->
                activity
                    .findViewById<PdfView>(PDF_VIEW_ID)
                    .removeOnViewportChangedListener(listener)
            }
            close()
        }
    }

    @Test
    fun testViewportListener_instantZoom() = runTest {
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
        setupPdfView(width = 500, height = 1000, pdfDocument)
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            val listener = PdfViewportListener()
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID).addOnViewportChangedListener(listener)
            }

            val zoom = 5.0F
            Espresso.onView(withId(PDF_VIEW_ID)).zoomTo(zoom)

            assertThat(listener.firstVisiblePage).isEqualTo(0)
            assertThat(listener.firstPageLocation)
                .isEqualTo(
                    Rect(
                        0,
                        (topPageMarginPx * zoom).roundToInt(),
                        (500F * zoom).roundToInt(),
                        ((1000F + topPageMarginPx) * zoom).roundToInt()
                    )
                )
            assertThat(listener.zoomLevel).isWithin(0.01F).of(zoom)
            assertThat(listener.updates).isEqualTo(1)

            onActivity { activity ->
                activity
                    .findViewById<PdfView>(PDF_VIEW_ID)
                    .removeOnViewportChangedListener(listener)
            }
            close()
        }
    }

    @Test
    fun testViewportListener_smoothZoom() = runTest {
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
        setupPdfView(width = 500, height = 1000, pdfDocument)
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            val listener = PdfViewportListener()
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID).addOnViewportChangedListener(listener)
            }

            val zoom = 5.0F
            Espresso.onView(withId(PDF_VIEW_ID)).smoothZoomTo(zoom, numSteps = 10)

            assertThat(listener.firstVisiblePage).isEqualTo(0)
            assertThat(listener.firstPageLocation)
                .isEqualTo(
                    Rect(
                        0,
                        (topPageMarginPx * zoom).roundToInt(),
                        (500F * zoom).roundToInt(),
                        ((1000F + topPageMarginPx) * zoom).roundToInt()
                    )
                )
            assertThat(listener.zoomLevel).isWithin(0.01F).of(zoom)
            assertThat(listener.updates).isEqualTo(10)

            onActivity { activity ->
                activity
                    .findViewById<PdfView>(PDF_VIEW_ID)
                    .removeOnViewportChangedListener(listener)
            }
            close()
        }
    }

    @Test
    fun testViewportListener_addRemove() = runTest {
        val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
        setupPdfView(width = 500, height = 1000, pdfDocument)
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            val listenerA = PdfViewportListener()
            val listenerB = PdfViewportListener()
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID).addOnViewportChangedListener(listenerA)
                activity.findViewById<PdfView>(PDF_VIEW_ID).addOnViewportChangedListener(listenerB)
            }

            // Scroll smoothly past page 0
            Espresso.onView(withId(PDF_VIEW_ID))
                .smoothScrollBy(totalPixels = 1000 + topPageMarginPx, numSteps = 10)
            pdfDocument.waitForLayout(untilPage = 1)

            assertThat(listenerA.firstVisiblePage).isEqualTo(1)
            assertThat(listenerB.firstVisiblePage).isEqualTo(1)
            assertThat(listenerA.firstPageLocation)
                .isEqualTo(Rect(0, pageMarginPx, 500, 1000 + pageMarginPx))
            assertThat(listenerB.firstPageLocation)
                .isEqualTo(Rect(0, pageMarginPx, 500, 1000 + pageMarginPx))
            assertThat(listenerA.zoomLevel).isEqualTo(1.0F)
            assertThat(listenerB.zoomLevel).isEqualTo(1.0F)
            assertThat(listenerA.updates).isEqualTo(10)
            assertThat(listenerB.updates).isEqualTo(10)

            onActivity { activity ->
                activity
                    .findViewById<PdfView>(PDF_VIEW_ID)
                    .removeOnViewportChangedListener(listenerB)
            }

            // Scroll smoothly past page 1
            Espresso.onView(withId(PDF_VIEW_ID))
                .smoothScrollBy(totalPixels = 1000 + pageMarginPx, numSteps = 10)
            pdfDocument.waitForLayout(untilPage = 1)

            assertThat(listenerA.firstVisiblePage).isEqualTo(2)
            assertThat(listenerA.firstPageLocation)
                .isEqualTo(Rect(0, pageMarginPx, 500, 1000 + pageMarginPx))
            assertThat(listenerA.zoomLevel).isEqualTo(1.0F)
            assertThat(listenerA.updates).isEqualTo(20)
            // No updates for removed listener
            assertThat(listenerB.updates).isEqualTo(10)

            onActivity { activity ->
                activity
                    .findViewById<PdfView>(PDF_VIEW_ID)
                    .removeOnViewportChangedListener(listenerA)
            }
            close()
        }
    }

    @Test
    fun testCoordinateTranslation() =
        runTest(timeout = 30.seconds) {
            val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
            setupPdfView(width = 500, height = 1000, pdfDocument)
            with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
                pdfDocument.waitForLayout(untilPage = 1)
                val pdfToViewPoints =
                    mutableListOf(
                        PdfPoint(pageNum = 0, pagePoint = PointF(0F, 0F)) to
                            PointF(0F, 0F + topPageMarginPx),
                        PdfPoint(pageNum = 0, pagePoint = PointF(250F, 500F)) to
                            PointF(250F, 500F + topPageMarginPx),
                        PdfPoint(pageNum = 0, pagePoint = PointF(499F, 999F)) to
                            PointF(499F, 999F + topPageMarginPx),
                        PdfPoint(pageNum = 1, pagePoint = PointF(0F, 0F)) to
                            PointF(0F, 1000F + pageMarginPx + topPageMarginPx),
                        PdfPoint(pageNum = 1, pagePoint = PointF(250F, 500F)) to
                            PointF(250F, 1500F + pageMarginPx + topPageMarginPx),
                        PdfPoint(pageNum = 1, pagePoint = PointF(499F, 999F)) to
                            PointF(499F, 1999F + pageMarginPx + topPageMarginPx),
                    )
                onActivity { activity ->
                    val pdfView = activity.findViewById<PdfView>(PDF_VIEW_ID)
                    for (pointPair in pdfToViewPoints) {
                        val viewPoint = pdfView.pdfToViewPoint(pointPair.first)
                        val pdfPoint = pdfView.viewToPdfPoint(pointPair.second)

                        assertThat(viewPoint).isEqualTo(pointPair.second)
                        assertThat(pdfPoint).isEqualTo(pointPair.first)
                    }
                }
            }
        }

    @Test
    fun testCoordinateTranslation_afterScroll() =
        runTest(timeout = 60.seconds) {
            val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
            setupPdfView(width = 500, height = 1000, pdfDocument)
            with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
                Espresso.onView(withId(PDF_VIEW_ID)).scrollToPage(3)
                pdfDocument.waitForLayout(untilPage = 3)

                val pdfToViewPoints =
                    mutableListOf(
                        PdfPoint(pageNum = 3, pagePoint = PointF(0F, 0F)) to PointF(0F, 0F),
                        PdfPoint(pageNum = 3, pagePoint = PointF(250F, 500F)) to PointF(250F, 500F),
                        PdfPoint(pageNum = 3, pagePoint = PointF(499F, 999F)) to PointF(499F, 999F),
                        PdfPoint(pageNum = 4, pagePoint = PointF(0F, 0F)) to
                            PointF(0F, 1000F + pageMarginPx),
                        PdfPoint(pageNum = 4, pagePoint = PointF(250F, 500F)) to
                            PointF(250F, 1500F + pageMarginPx),
                        PdfPoint(pageNum = 4, pagePoint = PointF(499F, 999F)) to
                            PointF(499F, 1999F + pageMarginPx),
                    )
                onActivity { activity ->
                    val pdfView = activity.findViewById<PdfView>(PDF_VIEW_ID)
                    for (pointPair in pdfToViewPoints) {
                        val viewPoint = pdfView.pdfToViewPoint(pointPair.first)
                        val pdfPoint = pdfView.viewToPdfPoint(pointPair.second)

                        assertThat(viewPoint).isEqualTo(pointPair.second)
                        assertThat(pdfPoint).isEqualTo(pointPair.first)
                    }
                }
            }
        }

    @Test
    fun testCoordinateTranslation_afterZoom() =
        runTest(timeout = 60.seconds) {
            val pdfDocument = FakePdfDocument(List(10) { Point(500, 1000) })
            setupPdfView(width = 500, height = 1000, pdfDocument)
            with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
                val zoom = 2.0F
                Espresso.onView(withId(PDF_VIEW_ID)).zoomTo(zoom)

                val pdfToViewPoints =
                    mutableListOf(
                        PdfPoint(pageNum = 0, pagePoint = PointF(0F, 0F)) to
                            PointF(0F, (0F + topPageMarginPx) * zoom),
                        PdfPoint(pageNum = 0, pagePoint = PointF(250F, 500F)) to
                            PointF(250F * zoom, (500F + topPageMarginPx) * zoom),
                        PdfPoint(pageNum = 0, pagePoint = PointF(499F, 999F)) to
                            PointF(499F * zoom, (999F + topPageMarginPx) * zoom),
                        PdfPoint(pageNum = 1, pagePoint = PointF(0F, 0F)) to
                            PointF(0F, (1000F + pageMarginPx + topPageMarginPx) * zoom),
                        PdfPoint(pageNum = 1, pagePoint = PointF(250F, 500F)) to
                            PointF(250F * zoom, (1500F + pageMarginPx + topPageMarginPx) * zoom),
                        PdfPoint(pageNum = 1, pagePoint = PointF(499F, 999F)) to
                            PointF(499F * zoom, (1999F + pageMarginPx + topPageMarginPx) * zoom),
                    )

                onActivity { activity ->
                    val pdfView = activity.findViewById<PdfView>(PDF_VIEW_ID)
                    for (pointPair in pdfToViewPoints) {
                        val viewPoint = pdfView.pdfToViewPoint(pointPair.first)
                        val pdfPoint = pdfView.viewToPdfPoint(pointPair.second)

                        assertThat(viewPoint).isEqualTo(pointPair.second)
                        assertThat(pdfPoint).isEqualTo(pointPair.first)
                    }
                }
            }
        }

    /** Create, measure, and layout a [PdfView] at the specified [width] and [height] */
    private fun setupPdfView(width: Int, height: Int, fakePdfDocument: FakePdfDocument?) {
        PdfViewTestActivity.onCreateCallback = { activity ->
            pageMarginPx = activity.getDimensions(R.dimen.page_spacing).roundToInt()
            topPageMarginPx = activity.getDimensions(R.dimen.top_page_margin).roundToInt()
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
}

/**
 * Implementation of [PdfView.OnViewportChangedListener] that captures the most recent values
 * received as well as the number of updates
 */
private class PdfViewportListener : PdfView.OnViewportChangedListener {
    var firstVisiblePage: Int = -1
        private set

    var firstPageLocation: Rect = Rect(-1, -1, -1, -1)
        private set

    var zoomLevel: Float = -1F
        private set

    var updates = 0
        private set

    override fun onViewportChanged(
        firstVisiblePage: Int,
        visiblePagesCount: Int,
        pageLocations: SparseArray<Rect>,
        zoomLevel: Float
    ) {
        this.firstVisiblePage = firstVisiblePage
        this.firstPageLocation = Rect(pageLocations.get(firstVisiblePage))
        this.zoomLevel = zoomLevel
        updates++
    }
}

/** Arbitrary fixed ID for PdfView */
private const val PDF_VIEW_ID = 123456789
