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

import android.graphics.RectF
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.pdf.R
import androidx.pdf.view.fastscroll.getDimensions
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@LargeTest
class AccessibilityPageHelperTest {
    private lateinit var pdfView: PdfView
    private lateinit var activityScenario: ActivityScenario<PdfViewTestActivity>

    private val pdfDocument = FakePdfDocument.newInstance()

    @Before
    fun setupPdfView() {
        // Setup the test activity to host the PdfView
        PdfViewTestActivity.onCreateCallback = { activity ->
            val container =
                FrameLayout(activity).apply {
                    addView(
                        PdfView(activity).apply {
                            this.pdfDocument = pdfDocument
                            id = PDF_VIEW_ID
                        },
                        ViewGroup.LayoutParams(100, 1000)
                    )
                }
            activity.setContentView(container)
        }

        activityScenario =
            ActivityScenario.launch(PdfViewTestActivity::class.java).onActivity { activity ->
                pdfView = activity.findViewById<PdfView>(PDF_VIEW_ID)
                requireNotNull(pdfView) { "PdfView must not be null." }
                pdfView.isTouchExplorationEnabled = true
                pdfView.pdfDocument = pdfDocument
            }
    }

    @After
    fun closeActivityScenario() {
        activityScenario.close()

        // Reset the fast scroller visibility
        pdfView.forcedFastScrollVisibility = true
        pdfView.lastFastScrollerVisibility = false
    }

    @Test
    fun getVirtualViewAt_returnsCorrectPageAndLink() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        val topPageMargin = pdfView.context.getDimensions(R.dimen.top_page_margin)

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 2)

        // Test cases with content coordinates and expected page indices
        val testCases =
            listOf(
                Triple(25f, 25f, 0), // Maps to page 0
                Triple(25f, 250f, 1), // Maps to page 1
                Triple(0f, topPageMargin, 0), // Maps to the very start of the first page
                Triple(0f, 100f, 0), // Edge of the first page
                Triple(110f, 25f, -1), // Outside valid page bounds
                Triple(-10f, -10f, -1), // Outside viewport
                Triple(50f, 40f, 10), // Goto Link
                Triple(50f, 70f, 11) // External Link
            )

        testCases.forEach { (x, y, expectedPage) ->
            val adjustedX = PdfView.toViewCoord(x, pdfView.zoom, pdfView.scrollX)
            val adjustedY = PdfView.toViewCoord(y, pdfView.zoom, pdfView.scrollY)

            assertThat(accessibilityPageHelper.getVirtualViewAt(adjustedX, adjustedY))
                .isEqualTo(expectedPage)
        }
    }

    @Test
    fun getVisibleVirtualViews_returnsCorrectPagesAndLinks() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 4)
        Espresso.onView(withId(PDF_VIEW_ID))
            .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 5)

        // Enabling Fast Scroller Visibility
        pdfView.forcedFastScrollVisibility = true
        pdfView.lastFastScrollerVisibility = true

        val visiblePagesAndLinks = mutableListOf<Int>()
        accessibilityPageHelper.getVisibleVirtualViews(visiblePagesAndLinks)
        assertThat(visiblePagesAndLinks).isEqualTo(listOf(0, 1, 2, 3, 4, 10, 11, 1000002, 1000003))
    }

    @Test
    fun onPopulateNodeForVirtualView_setsCorrectContentDescriptionAndFocusability() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 4)
        Espresso.onView(withId(PDF_VIEW_ID))
            .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 5)

        val testCases =
            listOf(
                1 to "Page 2: Sample text for page 2",
                10 to "Go to page 5",
                11 to "Link: www.example.com"
            )
        testCases.forEach { (virtualViewId, expectedDescription) ->
            val node = mock(AccessibilityNodeInfoCompat::class.java)
            accessibilityPageHelper.onPopulateNodeForVirtualView(virtualViewId, node)

            verify(node).contentDescription = expectedDescription
            verify(node).isFocusable = true
        }
    }

    @Test
    fun onPopulateNodeForVirtualView_setsCorrectBounds() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        val topPageMargin = pdfView.context.getDimensions(R.dimen.top_page_margin)

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 0)

        val testCases =
            listOf(
                0 to RectF(0f, topPageMargin, 100f, 200f + topPageMargin),
                10 to RectF(25f, 30f + topPageMargin, 75f, 50f + topPageMargin),
                11 to RectF(25f, 60f + topPageMargin, 75f, 80f + topPageMargin)
            )

        testCases.forEach { (virtualViewId, boundsInParent) ->
            val node = mock(AccessibilityNodeInfoCompat::class.java)
            val expectedBounds =
                accessibilityPageHelper.scalePageBounds(boundsInParent, pdfView.zoom)

            accessibilityPageHelper.onPopulateNodeForVirtualView(virtualViewId, node)
            verify(node).let {
                accessibilityPageHelper.setBoundsInScreenFromBoundsInParent(node, expectedBounds)
            }
        }
    }

    @Test
    fun onPageTextReady_updatesAccessibilityNode() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 1)

        val node = mock(AccessibilityNodeInfoCompat::class.java)
        var virtualViewId = 0 // Page 1

        accessibilityPageHelper.onPageTextReady(virtualViewId)

        // Verify content description is set as expected for Page 1
        accessibilityPageHelper.onPopulateNodeForVirtualView(virtualViewId, node)
        verify(node).contentDescription = "Page 1: Sample text for page 1"

        // Verify default content description for non-visible page
        virtualViewId = 7 // Page 8
        accessibilityPageHelper.onPopulateNodeForVirtualView(virtualViewId, node)
        verify(node).contentDescription = "Page 8" // Default value
    }

    @Test
    fun onPopulateNodeForVirtualView_fastScrollerThumb() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 0)

        // Enabling Fast Scroller Visibility
        pdfView.forcedFastScrollVisibility = true
        pdfView.lastFastScrollerVisibility = true

        val node = mock(AccessibilityNodeInfoCompat::class.java)
        val thumbVirtualId = AccessibilityPageHelper.FAST_SCROLLER_OFFSET + 1
        accessibilityPageHelper.onPopulateNodeForVirtualView(thumbVirtualId, node)
        verify(node).contentDescription = "Scroll Bar"
        verify(node).isFocusable = true
    }

    @Test
    fun onPopulateNodeForVirtualView_fastScrollerPageIndicator() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 0)

        // Enabling Fast Scroller Visibility
        pdfView.forcedFastScrollVisibility = true
        pdfView.lastFastScrollerVisibility = true

        val node = mock(AccessibilityNodeInfoCompat::class.java)
        val pageIndicatorVirtualId = AccessibilityPageHelper.FAST_SCROLLER_OFFSET + 2
        accessibilityPageHelper.onPopulateNodeForVirtualView(pageIndicatorVirtualId, node)
        verify(node).isFocusable = true
    }

    @Test
    fun getVisibleVirtualViews_fastScrollerElements() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 4)
        Espresso.onView(withId(PDF_VIEW_ID))
            .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 5)

        // Enabling Fast Scroller Visibility
        pdfView.forcedFastScrollVisibility = true
        pdfView.lastFastScrollerVisibility = true

        val visibleViews = mutableListOf<Int>()
        accessibilityPageHelper.getVisibleVirtualViews(visibleViews)

        assertThat(visibleViews).contains(AccessibilityPageHelper.FAST_SCROLLER_OFFSET + 1)
        assertThat(visibleViews).contains(AccessibilityPageHelper.FAST_SCROLLER_OFFSET + 2)
    }
}

/** Arbitrary fixed ID for PdfView */
private const val PDF_VIEW_ID = 123456789
