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
import androidx.annotation.RestrictTo
import androidx.pdf.content.PageMatchBounds
import androidx.pdf.search.model.SelectedSearchResult

/**
 * A cyclic iterator implementation over SparseArray.
 *
 * @param searchData: search result over which [CyclicSparseArrayIterator] will iterate.
 * @param visiblePage: current visible page to the user, used to init current result.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class CyclicSparseArrayIterator(
    private val searchData: SparseArray<List<PageMatchBounds>>,
    private val visiblePage: Int
) {
    /** represents list of all the pages where search results are present. */
    private val pageNumList: List<Int> = List(searchData.size()) { searchData.keyAt(it) }

    /** Total pages where result were found. */
    private val totalPages: Int = pageNumList.size

    /** Index of pageNum in [pageNumList]. */
    private var pageNumIndex: Int

    /** Index of result selected on current page. */
    private var searchIndexOnPage: Int

    init {
        if (totalPages == 0) {
            throw IllegalArgumentException("Search data must not be empty")
        }
        val (pageIndex, searchIndex) = findInitialMatch(visiblePage)
        pageNumIndex = pageIndex
        searchIndexOnPage = searchIndex
    }

    /** Get the current state of selected search result. */
    fun current(): SelectedSearchResult {
        val currentPageNum = pageNumList[pageNumIndex]
        return SelectedSearchResult(currentPageNum, searchIndexOnPage)
    }

    /** Move to the nex element in the current page, or to the next page cyclically. */
    fun next(): SelectedSearchResult {
        if (totalPages == 0) {
            throw NoSuchElementException("No elements to iterate.")
        }

        val currentPageNum = pageNumList[pageNumIndex]
        val resultsOnPage = searchData.get(currentPageNum)

        // Move to the next result in the current page
        searchIndexOnPage = (searchIndexOnPage + 1) % resultsOnPage.size

        // If we're at the end of the current page, move to the next page
        if (searchIndexOnPage == 0) {
            pageNumIndex = (pageNumIndex + 1) % totalPages
        }

        return current()
    }

    /** Move to the previous element in the page list, or to the previous page cyclically. */
    fun prev(): SelectedSearchResult {
        if (totalPages == 0) {
            throw NoSuchElementException("No elements to iterate.")
        }

        val currentPageNum = pageNumList[pageNumIndex]
        val resultsOnPage = searchData.get(currentPageNum)

        // Move to the previous item in the current page
        searchIndexOnPage = (searchIndexOnPage - 1 + resultsOnPage.size) % resultsOnPage.size

        // If we're at the beginning of the current page, move to the previous page
        if (searchIndexOnPage == resultsOnPage.size - 1) {
            pageNumIndex = (pageNumIndex - 1 + totalPages) % totalPages
        }

        return current()
    }

    /**
     * Find the closest page from current visible page in forward direction. Since the page list is
     * sorted, we can utilize binary search.
     *
     * @param currentPageNum: current visible page.
     */
    private fun findInitialMatch(currentPageNum: Int): Pair<Int, Int> {
        // Perform binary search to find the closest page in forward direction
        val indexOfPage = pageNumList.binarySearch(currentPageNum)

        return if (indexOfPage >= 0) {
            // Search results exists on current page, return it
            Pair(indexOfPage, INITIAL_INDEX_ON_PAGE)
        } else {
            // If not found, find the position where it should be inserted
            val insertionPoint = -(indexOfPage + 1)

            // If the insertion point is out of bounds, return the first page having results (wrap
            // around)
            if (insertionPoint >= pageNumList.size) {
                Pair(0, INITIAL_INDEX_ON_PAGE)
            } else {
                // Otherwise, return the page at the insertion point (the next closest page)
                Pair(insertionPoint, INITIAL_INDEX_ON_PAGE)
            }
        }
    }

    companion object {
        private const val INITIAL_INDEX_ON_PAGE = 0
    }
}
