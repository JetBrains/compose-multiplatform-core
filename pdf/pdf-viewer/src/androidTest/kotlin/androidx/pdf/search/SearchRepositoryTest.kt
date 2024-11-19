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

package androidx.pdf.search

import android.util.SparseArray
import androidx.core.util.isEmpty
import androidx.core.util.isNotEmpty
import androidx.pdf.content.PageMatchBounds
import androidx.pdf.search.model.SearchResults
import androidx.pdf.search.model.SelectedSearchResult
import androidx.pdf.view.FakePdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SearchRepositoryTest {

    /**
     * Creates fake search results and combine them in [SparseArray].
     *
     * @param matches: page number where a match is found.
     */
    private fun createFakeSearchResults(vararg matches: Int): SparseArray<List<PageMatchBounds>> {
        val results: SparseArray<List<PageMatchBounds>> = SparseArray()
        matches.forEach { pageNum ->
            val newPageResult =
                results.get(pageNum, listOf()).toMutableList().also {
                    it.add(PageMatchBounds(bounds = listOf(), textStartIndex = 0))
                }
            results.append(pageNum, newPageResult)
        }
        return results
    }

    @Test
    fun testSearchDocument_resultsOnCurrentVisiblePage() = runTest {
        val fakeResults = createFakeSearchResults(1, 5, 5, 10)
        val fakePdfDocument = FakePdfDocument(searchResults = fakeResults)

        with(SearchRepository(fakePdfDocument)) {
            // search document
            searchDocument(query = "test", currentVisiblePage = 5)

            val results = searchResults.value
            // Assert results exists on 3 pages
            assertEquals(3, results.results.size())
            assertEquals(5, selectedSearchResult.value?.pageNum)
            assertEquals(0, selectedSearchResult.value?.currentIndex)

            // fetch next result
            next()
            // Assert selectedSearchResult point to next result on same page
            assertEquals(5, selectedSearchResult.value?.pageNum)
            assertEquals(1, selectedSearchResult.value?.currentIndex)

            // fetch next result
            next()
            // Assert selectedSearchResult point to next result on next page
            // in forward direction
            assertEquals(10, selectedSearchResult.value?.pageNum)
            assertEquals(0, selectedSearchResult.value?.currentIndex)

            // fetch next result
            next()
            // Assert selectedSearchResult point to next result cyclically
            assertEquals(1, selectedSearchResult.value?.pageNum)
            assertEquals(0, selectedSearchResult.value?.currentIndex)

            // fetch previous result
            prev()
            // Assert selectedSearchResult point to previous result cyclically
            assertEquals(10, selectedSearchResult.value?.pageNum)
            assertEquals(0, selectedSearchResult.value?.currentIndex)
        }
    }

    @Test
    fun testSearchDocument_allResultsAfterCurrentVisiblePage() = runTest {
        val fakeResults = createFakeSearchResults(1, 5, 5, 10)
        val fakePdfDocument = FakePdfDocument(searchResults = fakeResults)

        with(SearchRepository(fakePdfDocument)) {
            // search document
            searchDocument(query = "test", currentVisiblePage = 7)

            val results = searchResults.value
            // Assert results exists on 3 pages
            assertEquals(3, results.results.size())
            assertEquals(10, selectedSearchResult.value?.pageNum)
            assertEquals(0, selectedSearchResult.value?.currentIndex)

            // fetch next result
            next()
            // Assert selectedSearchResult point to next result cyclically
            assertEquals(1, selectedSearchResult.value?.pageNum)
            assertEquals(0, selectedSearchResult.value?.currentIndex)
        }
    }

    @Test
    fun testSearchDocument_allResultsBeforeCurrentVisiblePage() = runTest {
        val fakeResults = createFakeSearchResults(1, 5, 5, 10)
        val fakePdfDocument = FakePdfDocument(searchResults = fakeResults)

        with(SearchRepository(fakePdfDocument)) {
            // search document
            searchDocument(query = "test", currentVisiblePage = 11)

            val results = searchResults.value
            // Assert results exists on 3 pages
            assertEquals(3, results.results.size())
            // Assert selectedSearchResult point to next result cyclically
            assertEquals(1, selectedSearchResult.value?.pageNum)
            assertEquals(0, selectedSearchResult.value?.currentIndex)

            // fetch next result
            next()
            // Assert selectedSearchResult point to next result on next page
            assertEquals(5, selectedSearchResult.value?.pageNum)
            assertEquals(0, selectedSearchResult.value?.currentIndex)
        }
    }

    @Test
    fun testSearchDocument_noMatchingResults() = runTest {
        val fakeResults = createFakeSearchResults()
        val fakePdfDocument = FakePdfDocument(searchResults = fakeResults)

        with(SearchRepository(fakePdfDocument)) {
            // search document
            searchDocument(query = "test", currentVisiblePage = 11)

            val results = searchResults.value

            // Assert no results returned
            assertEquals(0, results.results.size())
        }
    }

    @Test(expected = NoSuchElementException::class)
    fun testPrevOperation_noMatchingResults() = runTest {
        val fakeResults = createFakeSearchResults()
        val fakePdfDocument = FakePdfDocument(searchResults = fakeResults)

        with(SearchRepository(fakePdfDocument)) {
            // search document
            searchDocument(query = "test", currentVisiblePage = 11)

            val results = searchResults.value
            assertEquals(0, results.results.size())

            // fetch previous result, should throw [NoSuchElementException]
            prev()
        }
    }

    @Test(expected = NoSuchElementException::class)
    fun testNextOperation_noMatchingResults() = runTest {
        val fakeResults = createFakeSearchResults()
        val fakePdfDocument = FakePdfDocument(searchResults = fakeResults)

        with(SearchRepository(fakePdfDocument)) {
            // search document
            searchDocument(query = "test", currentVisiblePage = 10)

            val results = searchResults.value
            assertEquals(0, results.results.size())

            // fetch next result, should throw [NoSuchElementException]
            next()
        }
    }

    @Test
    fun testClearRepository() = runTest {
        val fakeResults = createFakeSearchResults(1, 5, 5, 10)
        val fakePdfDocument = FakePdfDocument(searchResults = fakeResults)

        with(SearchRepository(fakePdfDocument)) {
            // search document
            searchDocument(query = "test", currentVisiblePage = 11)

            assertEquals(3, searchResults.value.results.size())

            // clear results
            clearSearchResults()

            // assert results are cleared
            assertTrue(searchResults.value.results.isEmpty())
        }
    }

    @Test
    fun testSettingStateToRepository() = runTest {
        val fakeResults = createFakeSearchResults()
        val fakePdfDocument = FakePdfDocument(searchResults = fakeResults)
        val currentVisiblePage = 11

        with(SearchRepository(fakePdfDocument)) {
            // search document
            searchDocument(query = "test", currentVisiblePage = currentVisiblePage)

            // assert there are no results
            assertEquals(0, searchResults.value.results.size())

            // set results
            setState(
                searchResults = SearchResults("test", createFakeSearchResults(1, 5, 5, 10)),
                selectedSearchResult = SelectedSearchResult(5, 1),
                currentVisiblePage = currentVisiblePage
            )

            // assert results are set
            assertTrue(searchResults.value.results.isNotEmpty())
            assertEquals(5, selectedSearchResult.value?.pageNum)
            assertEquals(1, selectedSearchResult.value?.currentIndex)
        }
    }

    @Test(expected = NoSuchElementException::class)
    fun testSettingEmptyResultsToRepository() = runTest {
        val fakeResults = createFakeSearchResults()
        val fakePdfDocument = FakePdfDocument(searchResults = fakeResults)
        val currentVisiblePage = 11

        with(SearchRepository(fakePdfDocument)) {
            // search document
            searchDocument(query = "test", currentVisiblePage = currentVisiblePage)

            // assert there are no results
            assertEquals(0, searchResults.value.results.size())

            // set results
            setState(
                searchResults = SearchResults("test", SparseArray()),
                selectedSearchResult = null,
                currentVisiblePage = currentVisiblePage
            )

            // fetch next result, should throw [NoSuchElementException]
            next()
        }
    }
}
