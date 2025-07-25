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

package androidx.compose.foundation.text.contextmenu.builder

import androidx.collection.mutableObjectListOf
import androidx.compose.foundation.text.contextmenu.data.PlatformIcon
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuComponent
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.modifier.addTextContextMenuComponents

/**
 * Scope for building a text context menu in
 * [Modifier.addTextContextMenuComponents][addTextContextMenuComponents]. See member functions for
 * how to add context menu components to this scope as part of the
 * [Modifier.addTextContextMenuComponents][addTextContextMenuComponents] modifier.
 */
// TODO(grantapher-cm-api-publicize) Make class public
internal class TextContextMenuBuilderScope internal constructor() {
    private val components = mutableObjectListOf<TextContextMenuComponent>()
    private val filters = mutableObjectListOf<(TextContextMenuComponent) -> Boolean>()

    /**
     * Build the current state of this into a [TextContextMenuData]. This applies a few filters to
     * the components:
     * * No back-to-back separators.
     * * No heading or tailing separators.
     * * Applies [filters] to each component, excluding separators.
     */
    internal fun build(): TextContextMenuData {
        val resultList = mutableObjectListOf<TextContextMenuComponent>()

        var headIsSeparator = true
        var previous: TextContextMenuComponent? = null
        components.forEach { current ->
            // remove heading separators
            if (headIsSeparator && current === TextContextMenuSeparator) return@forEach
            headIsSeparator = false

            // remove back-to-back separators
            if (current.isSeparator && previous.isSeparator) return@forEach

            // apply `filters` unless a component is a separator
            if (!current.isSeparator && filters.any { filter -> !filter(current) }) return@forEach

            resultList += current
            previous = current
        }

        // remove the remaining trailing separator, if there is one.
        if (resultList.lastOrNull().isSeparator) {
            @Suppress("Range") // lastIndex will not be -1 because the list cannot be empty
            resultList.removeAt(resultList.lastIndex)
        }

        @Suppress("AsCollectionCall") // need to use asList as this enters a public api.
        return TextContextMenuData(components = resultList.asList())
    }

    /**
     * Adds a [filter] to be applied to each component in [components] when the [build] method is
     * called.
     *
     * @param filter A predicate that determines whether a [TextContextMenuComponent] should be
     *   added to the final list of components. This filter will never receive a
     *   [TextContextMenuSeparator] since they are excluded from filtering.
     */
    internal fun addFilter(filter: (TextContextMenuComponent) -> Boolean) {
        filters += filter
    }

    // TODO(grantapher-cm-api-publicize) add AddItemToTextContextMenuAndroid sample
    /**
     * Adds an item to the list of text context menu components.
     *
     * @param key A unique key that identifies this item. Used to identify context menu items in the
     *   context menu. It is advisable to use a `data object` as a key here.
     * @param label string to display as the text of the item.
     * @param leadingIcon Icon that precedes the label in the context menu. Setting this to null
     *   means that it will not be displayed.
     * @param onClick Action to perform upon the item being clicked/pressed.
     */
    fun item(
        key: Any,
        label: String,
        leadingIcon: PlatformIcon? = null,
        onClick: TextContextMenuSession.() -> Unit,
    ) {
        components += TextContextMenuItem(key, label, leadingIcon, onClick)
    }

    /**
     * Adds a separator to the list of text context menu components. Successive separators will be
     * combined into a single separator.
     */
    fun separator() {
        components += TextContextMenuSeparator
    }
}

private val TextContextMenuComponent?.isSeparator: Boolean
    get() = this === TextContextMenuSeparator
