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
import android.util.Range
import androidx.pdf.PdfDocument
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PageLayoutManagerTest {
    private val pdfDocument =
        mock<PdfDocument> {
            on { pageCount } doReturn TOTAL_PAGES
            onBlocking { getPageInfo(any()) } doAnswer
                { invocationOnMock ->
                    PdfDocument.PageInfo(
                        pageNum = invocationOnMock.getArgument(0),
                        height = 200,
                        width = 100
                    )
                }
        }

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var paginationManager: PageLayoutManager
    private val errorFlow = MutableSharedFlow<Throwable>()

    @Before
    fun setup() {
        // Required because loadPageDimensions jumps to the main thread to update PaginationModel
        Dispatchers.setMain(testDispatcher)
        paginationManager = PageLayoutManager(pdfDocument, testScope, errorFlow = errorFlow)
    }

    @Test
    fun onViewportChanged() = runTest {
        // Start collecting from PaginationManager#visiblePages
        val visiblePageValues = mutableListOf<PagesInViewport>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            paginationManager.visiblePages.toList(visiblePageValues)
        }
        // Add some pages to the manager and wait for their dimensions to load
        paginationManager.increaseReach(20)
        testScope.testScheduler.runCurrent()
        assertThat(paginationManager.reach).isEqualTo(20)

        // Change the viewport twice
        paginationManager.onViewportChanged(scrollY = 0, height = 1000, zoom = 1.0f)
        paginationManager.onViewportChanged(scrollY = 1000, height = 1000, zoom = 1.0f)

        // We expect to collect 3 values: the default [0, 0] value and two updates based on two
        // viewport changes
        assertThat(visiblePageValues.size).isEqualTo(3)
        assertThat(visiblePageValues[0]).isEqualTo(PagesInViewport(Range(0, 0)))
        // These assertions are deliberately coarse so as not to test the implementation details of
        // PaginationModel here
        assertThat(visiblePageValues[1].pages.upper).isGreaterThan(0)
        assertThat(visiblePageValues[2].pages.upper).isGreaterThan(visiblePageValues[1].pages.upper)
    }

    @Test
    fun onViewportChanged_noChange() = runTest {
        // Start collecting from PaginationManager#visiblePages
        val visiblePageValues = mutableListOf<PagesInViewport>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            paginationManager.visiblePages.toList(visiblePageValues)
        }
        // Add some pages to the manager and wait for their dimensions to load
        paginationManager.increaseReach(20)
        testScope.testScheduler.runCurrent()
        assertThat(paginationManager.reach).isEqualTo(20)

        // Change the viewport twice, but to the same value
        paginationManager.onViewportChanged(scrollY = 0, height = 1000, zoom = 1.0f)
        paginationManager.onViewportChanged(scrollY = 0, height = 1000, zoom = 1.0f)

        // We expect to collect 2 values: the default [0, 0] value and one update based on one
        // viewport change
        assertThat(visiblePageValues.size).isEqualTo(2)
        assertThat(visiblePageValues[0]).isEqualTo(PagesInViewport(Range(0, 0)))
        // This assertion is deliberately coarse so as not to test the implementation details of
        // PaginationModel here
        assertThat(visiblePageValues[1].pages.upper).isGreaterThan(0)
    }

    @Test
    fun onViewportChanged_prefetchPages() = runTest {
        // Start collecting from PaginationManager#visiblePages
        val visiblePageValues = mutableListOf<PagesInViewport>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            paginationManager.visiblePages.toList(visiblePageValues)
        }
        // Add some pages to the manager and wait for their dimensions to load
        paginationManager.increaseReach(5)
        testScope.testScheduler.runCurrent()
        assertThat(paginationManager.reach).isEqualTo(5)

        // Update the viewport, for simplicity's sake to a value that fits all the currently
        // measured pages
        paginationManager.onViewportChanged(scrollY = 0, height = 1200, zoom = 1.0f)

        // Make sure we've fetched all currently measured & visible pages + the page prefetch radius
        testScope.testScheduler.runCurrent()
        assertThat(paginationManager.reach).isEqualTo(5 + PageLayoutManager.DEFAULT_PREFETCH_RADIUS)
    }

    @Test
    fun increaseReach_belowCurrentReach() = runTest {
        // Start collecting from PaginationManager#dimensions
        val dimensionsValues = mutableListOf<Pair<Int, Point>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            paginationManager.dimensions.toList(dimensionsValues)
        }

        // Increase reach to 10 and make sure we requested and collected 11 values
        paginationManager.increaseReach(10)
        testScope.testScheduler.runCurrent()
        verify(pdfDocument, times(11)).getPageInfo(any())
        assertThat(paginationManager.reach).isEqualTo(10)
        assertThat(dimensionsValues.size).isEqualTo(11)

        // Decrease reach to 8 and make sure we didn't request or collect any *more* values, and
        // that our reach remains 10
        paginationManager.increaseReach(8)
        testScope.testScheduler.runCurrent()
        verify(pdfDocument, times(11)).getPageInfo(any())
        assertThat(paginationManager.reach).isEqualTo(10)
        assertThat(dimensionsValues.size).isEqualTo(11)
    }

    @Test
    fun increaseReach_aboveCurrentReach() = runTest {
        // Start collecting from PaginationManager#dimensions
        val dimensionsValues = mutableListOf<Pair<Int, Point>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            paginationManager.dimensions.toList(dimensionsValues)
        }

        // Increase reach to 10 and make sure we requested and collected 11 values
        paginationManager.increaseReach(10)
        testScope.testScheduler.runCurrent()
        verify(pdfDocument, times(11)).getPageInfo(any())
        assertThat(paginationManager.reach).isEqualTo(10)
        assertThat(dimensionsValues.size).isEqualTo(11)

        // Increase reach to 20 and make sure we requested and collected 21 total values
        paginationManager.increaseReach(20)
        testScope.testScheduler.runCurrent()
        verify(pdfDocument, times(21)).getPageInfo(any())
        assertThat(paginationManager.reach).isEqualTo(20)
        assertThat(dimensionsValues.size).isEqualTo(21)
    }

    @Test
    fun increaseReach_toEnd() = runTest {
        // Start collecting from PaginationManager#dimensions
        val dimensionsValues = mutableListOf<Pair<Int, Point>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            paginationManager.dimensions.toList(dimensionsValues)
        }

        // Increase reach beyond the end of the document, and make sure we requested and collected
        // only the appropriate number of values
        paginationManager.increaseReach(TOTAL_PAGES + 10)
        testScope.testScheduler.runCurrent()
        verify(pdfDocument, times(TOTAL_PAGES)).getPageInfo(any())
        assertThat(paginationManager.reach).isEqualTo(TOTAL_PAGES - 1)
        assertThat(dimensionsValues.size).isEqualTo(TOTAL_PAGES)
    }
}

private const val TOTAL_PAGES = 100
