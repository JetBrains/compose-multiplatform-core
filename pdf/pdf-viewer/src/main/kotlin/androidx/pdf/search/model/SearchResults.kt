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

package androidx.pdf.search.model

import android.util.SparseArray
import androidx.annotation.RestrictTo
import androidx.pdf.content.PageMatchBounds

/** Model class to hold search results over pdf document for a search query. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class SearchResults(
    /**
     * search query provided to initiate search
     *
     * By default it will be empty string.
     */
    val searchQuery: String = "",
    /**
     * search results in pdf document for [searchQuery]
     *
     * By default it will be initialized to empty [SparseArray].
     */
    val results: SparseArray<List<PageMatchBounds>> = SparseArray()
)
