/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.pdf.selection

import android.content.Context
import androidx.pdf.selection.model.TextSelection

/**
 * Manages the retrieval and caching of context menu items for various types of selections within a
 * PDF viewer.
 *
 * This class serves as a centralized manager to provide context-specific menu options when a user
 * interacts with selected content. It currently optimizes for [TextSelection] by caching the
 * previously generated menu items.
 *
 * @property context The [Context] used to initialize underlying menu providers, such as
 *   [TextSelectionMenuProvider].
 */
internal class SelectionMenuManager(private val context: Context) {

    /**
     * Caches the most recent [TextSelection] and its generated [ContextMenuComponent] list.
     *
     * This optimization prevents redundant computations for identical text selections, especially
     * during frequent UI updates like zooming, scrolling, or orientation changes.
     */
    private data class SelectionCache(
        val textSelection: TextSelection,
        val menuItems: List<ContextMenuComponent>,
    )

    private var cachedSelection: SelectionCache? = null
    private val textSelectionMenuProvider = TextSelectionMenuProvider(context)

    suspend fun getSelectionMenuItems(selection: Selection): List<ContextMenuComponent> {
        return when (selection) {
            is TextSelection -> {
                // Check if the current selection is already cached
                cachedSelection?.let { localCachedSelection ->
                    if (localCachedSelection.textSelection == selection) {
                        return localCachedSelection.menuItems
                    }
                }
                // If it's a new selection or no selection cached, compute and cache it
                val newMenuItems = textSelectionMenuProvider.getMenuItems(selection)
                cachedSelection = SelectionCache(selection, newMenuItems)
                newMenuItems
            }
            else -> emptyList()
        }
    }
}
