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

import androidx.annotation.RestrictTo
import androidx.core.util.isEmpty
import androidx.core.util.isNotEmpty
import androidx.pdf.PdfDocument
import androidx.pdf.search.model.SearchResults
import androidx.pdf.search.model.SelectedSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Repository responsible for searching over PDF documents.
 *
 * This repository handles the business logic for searching within PDF files, including:
 * - Initiating search operation using the [PdfDocument] interface, and selecting the initial search
 *   result based upon current visible page.
 * - Managing search results and providing navigation through them.
 *
 * The search results are exposed as a [StateFlow], allowing observers to react to changes in the
 * search results in a reactive manner.
 *
 * @param pdfDocument: Interface to interact with pdf document
 * @param dispatcher: The [CoroutineDispatcher] to use for performing the search operation. Defaults
 *   to Dispatcher.IO.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class SearchRepository(
    private val pdfDocument: PdfDocument,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _searchResults: MutableStateFlow<SearchResults> = MutableStateFlow(SearchResults())

    /** Stream of search results for a given query. */
    val searchResults: StateFlow<SearchResults>
        get() = _searchResults.asStateFlow()

    private val _selectedSearchResult: MutableStateFlow<SelectedSearchResult?> =
        MutableStateFlow(null)

    /** Stream of selected search results. */
    val selectedSearchResult: StateFlow<SelectedSearchResult?>
        get() = _selectedSearchResult.asStateFlow()

    private lateinit var cyclicIterator: CyclicSparseArrayIterator

    /**
     * Initiates search over pdf document
     *
     * @param query: The search query string.
     * @param currentVisiblePage: Provides current visible document page, which is required to
     *   search from specific page and to calculate initial [selectedSearchResult]
     *
     * Results would be updated to [searchResults] in the coroutine collecting the flow.
     */
    suspend fun searchDocument(query: String, currentVisiblePage: Int) {
        if (query.isEmpty()) return

        // Clear the existing results
        clearSearchResults()

        // search should be a background work, move execution on to provided [dispatcher]
        // to make [searchDocument] main-safe
        val currentResult =
            withContext(dispatcher) {
                SearchResults(
                    searchQuery = query,
                    results =
                        pdfDocument.searchDocument(
                            query = query,
                            pageRange = IntRange(start = 0, endInclusive = pdfDocument.pageCount)
                        )
                )
            }

        // update results
        _searchResults.update { currentResult }

        if (currentResult.results.isNotEmpty()) {
            // Init cyclic iterator
            cyclicIterator = CyclicSparseArrayIterator(currentResult.results, currentVisiblePage)

            // update initial selection
            _selectedSearchResult.update { cyclicIterator.current() }
        }
    }

    /**
     * Iterate through searchResults in backward direction.
     *
     * Results would be updated to [selectedSearchResult] in the coroutine collecting the flow.
     *
     * Throws [NoSuchElementException] is search results are empty.
     */
    suspend fun prev() {
        if (searchResults.value.results.isEmpty())
            throw NoSuchElementException("Iteration not possible over empty results")

        _selectedSearchResult.update { cyclicIterator.prev() }
    }

    /**
     * Iterate through searchResults in forward direction.
     *
     * Results would be updated to [selectedSearchResult] in the coroutine collecting the flow.
     *
     * Throws [NoSuchElementException] is search results are empty.
     */
    suspend fun next() {
        if (searchResults.value.results.isEmpty())
            throw NoSuchElementException("Iteration not possible over empty results")

        _selectedSearchResult.update { cyclicIterator.next() }
    }

    /**
     * Resets [searchResults] and [selectedSearchResult] to initial state. This would be required to
     * handle close/cancel action.
     */
    fun clearSearchResults() {
        _searchResults.update { SearchResults() }
        _selectedSearchResult.update { null }
    }

    /**
     * Set [searchResults] and [selectedSearchResult] flows to provided value.
     *
     * This should be utilized when result state is already available(muck like in restore scenario)
     */
    fun setState(
        searchResults: SearchResults,
        selectedSearchResult: SelectedSearchResult?,
        currentVisiblePage: Int
    ) {
        _searchResults.update { searchResults }
        _selectedSearchResult.update { selectedSearchResult }
        // initiate iterator is results are not empty
        if (searchResults.results.isNotEmpty())
            cyclicIterator = CyclicSparseArrayIterator(searchResults.results, currentVisiblePage)
    }
}
