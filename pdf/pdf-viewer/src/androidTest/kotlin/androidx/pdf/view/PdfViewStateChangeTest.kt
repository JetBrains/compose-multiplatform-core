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
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PdfViewStateChangeTest {
    @After
    fun tearDown() {
        PdfViewTestActivity.onCreateCallback = {}
    }

    @Test
    fun restorePositionState() = runTest {
        val pdfDocument = FakePdfDocument(List(10) { Point(100, 200) })
        withContext(Dispatchers.Main) { setupPdfView(500, 1000, pdfDocument) }

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            // Scroll to page 3, and make sure it's visible
            pdfDocument.waitForLayout(untilPage = 4)
            Espresso.onView(withId(PDF_VIEW_ID)).scrollToPage(3)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 3, visiblePages = 1)

            // Recreate and make sure we're still on page 3
            // PdfDocument will be set naturally following recreation by the onCreate callback
            // configured in setupPdfView
            recreate()

            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 3, visiblePages = 1)
            close()
        }
    }

    @Test
    fun restorePositionState_afterPdfDocument() = runTest {
        val pdfDocument = FakePdfDocument(List(10) { Point(100, 200) })
        // Don't supply PdfDocument to setupPdfView, as it will cause that document to be set each
        // time our Activity is created. In this case, we'd like to test recreating the Activity
        // without setting a PdfDocument.
        withContext(Dispatchers.Main) { setupPdfView(500, 1000, fakePdfDocument = null) }

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            // Set PdfDocument, scroll to page 3, and make sure it's visible
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID)?.also { it.pdfDocument = pdfDocument }
            }
            pdfDocument.waitForLayout(untilPage = 4)
            Espresso.onView(withId(PDF_VIEW_ID)).scrollToPage(3)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 3, visiblePages = 1)

            // Recreate without setting PdfDocument check we're in the default state WRT page
            // visibility
            recreate()
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 0)

            // Set PdfDocument, and make sure we restore to page 3
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID)?.also { it.pdfDocument = pdfDocument }
            }
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 3, visiblePages = 1)
            close()
        }
    }

    @Test
    fun dontRestorePositionState_withDifferentDocument() = runTest {
        val pdfDocument =
            FakePdfDocument(
                pages = List(10) { Point(100, 200) },
                uri = Uri.parse("content://my.app/my.pdf")
            )
        // Don't supply PdfDocument to setupPdfView, as it will cause that document to be set each
        // time our Activity is created. In this case, we'd like to test recreating the Activity
        // and setting a *different* PdfDocument
        withContext(Dispatchers.Main) { setupPdfView(500, 1000, fakePdfDocument = null) }

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            // Set PdfDocument, scroll to page 3, and make sure it's visible
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID)?.also { it.pdfDocument = pdfDocument }
            }
            pdfDocument.waitForLayout(untilPage = 4)
            Espresso.onView(withId(PDF_VIEW_ID)).scrollToPage(3)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 3, visiblePages = 1)

            // Recreate without setting PdfDocument check we're in the default state WRT page
            // visibility
            recreate()
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 0)

            // Set a *new* PdfDocument, and make sure we *don't* restore position state from the
            // previous document
            val differentDocument =
                FakePdfDocument(
                    pages = List(15) { Point(50, 150) },
                    uri = Uri.parse("content://another.app/pdf.pdf")
                )
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID)?.also {
                    it.pdfDocument = differentDocument
                }
            }
            Espresso.onView(withId(PDF_VIEW_ID)).checkPagesAreVisible(firstVisiblePage = 0)
            close()
        }
    }

    @Test
    fun recreate_withoutPdfDocument() = runTest {
        withContext(Dispatchers.Main) { setupPdfView(500, 1000, fakePdfDocument = null) }
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            // Recreate without ever setting a document on PdfView initially
            recreate()

            // Set PdfDocument on the new PdfView instance, and make sure we can interact with it
            val pdfDocument = FakePdfDocument(List(10) { Point(100, 200) })
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID)?.also { it.pdfDocument = pdfDocument }
            }

            pdfDocument.waitForLayout(untilPage = 4)
            Espresso.onView(withId(PDF_VIEW_ID)).scrollToPage(3)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 3, visiblePages = 1)
            close()
        }
    }

    @Test
    fun resetDocument() = runTest {
        val pdfDocument =
            FakePdfDocument(
                pages = List(10) { Point(100, 200) },
                uri = Uri.parse("content://my.app/my.pdf")
            )
        withContext(Dispatchers.Main) { setupPdfView(500, 1000, pdfDocument) }

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            // Scroll to page 3, and make sure it's visible
            pdfDocument.waitForLayout(untilPage = 4)
            Espresso.onView(withId(PDF_VIEW_ID)).scrollToPage(3)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 3, visiblePages = 1)

            // Set a different PdfDocument
            val differentDocument =
                FakePdfDocument(
                    List(10) { Point(100, 500) },
                    uri = Uri.parse("content://browser/downloads/menu.pdf")
                )
            onActivity { activity ->
                activity.findViewById<PdfView>(PDF_VIEW_ID)?.also {
                    it.pdfDocument = differentDocument
                }
            }
            // First, make sure the View resets its state
            Espresso.onView(withId(PDF_VIEW_ID)).checkPagesAreVisible(firstVisiblePage = 0)
            // Then, make sure we can interact with the new document: scroll to page 3, and make
            // sure it's visible
            pdfDocument.waitForLayout(untilPage = 3)
            Espresso.onView(withId(PDF_VIEW_ID)).scrollToPage(3)
            Espresso.onView(withId(PDF_VIEW_ID))
                .checkPagesAreVisible(firstVisiblePage = 3, visiblePages = 1)

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
