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

package androidx.wear.compose.material3.lazy

import androidx.compose.runtime.Composable
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastLastOrNull
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope

/**
 * Receiver scope for [ResponsiveTransformingLazyColumn].
 *
 * This scope extends [TransformingLazyColumnScope] by allowing you to specify the
 * [ResponsiveItemType] for each item. This item type information is used by the parent component to
 * automatically calculate and apply the correct responsive padding to the top and bottom of the
 * list, adhering to Wear OS Material Design guidelines.
 *
 * If `contentPadding` is provided to the [ResponsiveTransformingLazyColumn], the maximum of the
 * responsive padding and the provided `contentPadding` will be used.
 */
public sealed interface ResponsiveTransformingLazyColumnScope {

    /**
     * Adds [count] items to the column, applying the padding according to the [itemType].
     *
     * @param count the number of items.
     * @param key a factory of stable and unique keys representing the item. Using the same key for
     *   multiple items is not allowed. Type of the key should be saveable via Bundle on Android. If
     *   null is passed the position in the list will represent the key. When you specify the key
     *   the scroll position will be maintained based on the key, which means if you add/remove
     *   items before the current visible item the item with the given key will be kept as the first
     *   visible one.
     * @param contentType a factory of the content types for the item. The item compositions of the
     *   same type could be reused more efficiently. Note that null is a valid type and items of
     *   such type will be considered compatible.
     * @param itemType a factory to determine the [ResponsiveItemType] of the content based on the
     *   index. The types of the first and last items in the list determine the amount of responsive
     *   padding applied to the top and bottom of the list respectively. Defaults to
     *   [ResponsiveItemType.Default].
     * @param content the content of the item. The item is aware of its position in the list.
     */
    public fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        itemType: (index: Int) -> ResponsiveItemType = { ResponsiveItemType.Default },
        content: @Composable TransformingLazyColumnItemScope.(index: Int) -> Unit,
    )

    /**
     * Adds a single item to the column, applying the padding according to the [itemType].
     *
     * @param key a stable and unique key representing the item. Using the same key for multiple
     *   items is not allowed. Type of the key should be saveable via Bundle on Android. If null is
     *   passed the position in the list will represent the key. When you specify the key the scroll
     *   position will be maintained based on the key, which means if you add/remove items before
     *   the current visible item the item with the given key will be kept as the first visible one.
     * @param contentType the type of the content of the item. The item compositions of the same
     *   type could be reused more efficiently. Note that null is a valid type and items of such
     *   type will be considered compatible.
     * @param itemType the [ResponsiveItemType] of the content, e.g. [ResponsiveItemType.Card]. This
     *   type determines the responsive padding that will be added if this is the first or last item
     *   in the list. Defaults to [ResponsiveItemType.Default].
     * @param content the content of the item.
     */
    public fun item(
        key: Any? = null,
        contentType: Any? = null,
        itemType: ResponsiveItemType = ResponsiveItemType.Default,
        content: @Composable TransformingLazyColumnItemScope.() -> Unit,
    )
}

/**
 * Adds a list of items.
 *
 * @param items the data list
 * @param key a factory of stable and unique keys representing the item. Using the same key for
 *   multiple items in the [ResponsiveTransformingLazyColumn] is not allowed. Type of the key should
 *   be saveable via Bundle on Android. If null is passed the position in the list will represent
 *   the key. When you specify the key the scroll position will be maintained based on the key,
 *   which means if you add/remove items before the current visible item the item with the given key
 *   will be kept as the first visible one.
 * @param contentType a factory of the content types for the item. The item compositions of the same
 *   type could be reused more efficiently. Note that null is a valid type and items of such type
 *   will be considered compatible.
 * @param itemType a factory of the [ResponsiveItemType]s for the item. The types of the first and
 *   last items in the list determine the amount of responsive padding applied to the top and bottom
 *   of the list respectively. Defaults to [ResponsiveItemType.Default].
 * @param itemContent the content displayed by a single item
 */
public inline fun <T> ResponsiveTransformingLazyColumnScope.items(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemType: (index: Int, item: T) -> ResponsiveItemType = { _, _ ->
        ResponsiveItemType.Default
    },
    crossinline itemContent: @Composable TransformingLazyColumnItemScope.(item: T) -> Unit,
): Unit =
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(index, items[index]) } else null,
        contentType = { index -> contentType(index, items[index]) },
        itemType = { index -> itemType(index, items[index]) },
    ) {
        itemContent(items[it])
    }

/**
 * Adds a list of items where the content of an item is aware of its index.
 *
 * @param items the data list
 * @param key a factory of stable and unique keys representing the item. Using the same key for
 *   multiple items in the [ResponsiveTransformingLazyColumn] is not allowed. Type of the key should
 *   be saveable via Bundle on Android. If null is passed the position in the list will represent
 *   the key. When you specify the key the scroll position will be maintained based on the key,
 *   which means if you add/remove items before the current visible item the item with the given key
 *   will be kept as the first visible one.
 * @param contentType a factory of the content types for the item. The item compositions of the same
 *   type could be reused more efficiently. Note that null is a valid type and items of such type
 *   will be considered compatible.
 * @param itemType a factory of the [ResponsiveItemType]s for the item. The types of the first and
 *   last items in the list determine the amount of responsive padding applied to the top and bottom
 *   of the list respectively. Defaults to [ResponsiveItemType.Default].
 * @param itemContent the content displayed by a single item
 */
public inline fun <T> ResponsiveTransformingLazyColumnScope.itemsIndexed(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemType: (index: Int, item: T) -> ResponsiveItemType = { _, _ ->
        ResponsiveItemType.Default
    },
    crossinline itemContent:
        @Composable
        TransformingLazyColumnItemScope.(index: Int, item: T) -> Unit,
): Unit =
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(index, items[index]) } else null,
        contentType = { index -> contentType(index, items[index]) },
        itemType = { index -> itemType(index, items[index]) },
    ) {
        itemContent(it, items[it])
    }

internal sealed class ContentDefinition {
    abstract val count: Int

    data class Item(
        val key: Any?,
        val contentType: Any?,
        val itemType: ResponsiveItemType,
        val content: @Composable TransformingLazyColumnItemScope.() -> Unit,
    ) : ContentDefinition() {
        override val count: Int = 1
    }

    data class Items(
        override val count: Int,
        val key: ((index: Int) -> Any)?,
        val contentType: (index: Int) -> Any?,
        val itemTypeProvider: (index: Int) -> ResponsiveItemType,
        val content: @Composable TransformingLazyColumnItemScope.(index: Int) -> Unit,
    ) : ContentDefinition()
}

internal class ResponsiveTransformingLazyColumnScopeImpl : ResponsiveTransformingLazyColumnScope {
    internal val definitions = mutableListOf<ContentDefinition>()

    val firstItemType: ResponsiveItemType?
        get() =
            definitions
                .fastFirstOrNull { it.count > 0 }
                ?.let { first ->
                    when (first) {
                        is ContentDefinition.Item -> first.itemType
                        is ContentDefinition.Items -> first.itemTypeProvider(0)
                    }
                }

    val lastItemType: ResponsiveItemType?
        get() =
            definitions
                .fastLastOrNull { it.count > 0 }
                ?.let { last ->
                    when (last) {
                        is ContentDefinition.Item -> last.itemType
                        is ContentDefinition.Items -> last.itemTypeProvider(last.count - 1)
                    }
                }

    override fun item(
        key: Any?,
        contentType: Any?,
        itemType: ResponsiveItemType,
        content: @Composable TransformingLazyColumnItemScope.() -> Unit,
    ) {
        definitions.add(ContentDefinition.Item(key, contentType, itemType, content))
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemType: (index: Int) -> ResponsiveItemType,
        content: @Composable TransformingLazyColumnItemScope.(index: Int) -> Unit,
    ) {
        definitions.add(ContentDefinition.Items(count, key, contentType, itemType, content))
    }

    // This function will be called by the parent to render the captured content.
    fun content(scope: TransformingLazyColumnScope) {
        definitions.fastForEach { def ->
            when (def) {
                is ContentDefinition.Item ->
                    scope.item(def.key, def.contentType) { def.content(this) }
                is ContentDefinition.Items -> {
                    scope.items(def.count, def.key, def.contentType, def.content)
                }
            }
        }
    }
}
