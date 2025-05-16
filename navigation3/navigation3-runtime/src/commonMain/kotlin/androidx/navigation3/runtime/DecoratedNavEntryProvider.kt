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

@file:Suppress("UNCHECKED_CAST")

package androidx.navigation3.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.jvm.JvmSuppressWildcards

/**
 * Function that provides all of the [NavEntry]s wrapped with the given [NavEntryDecorator]s. It is
 * responsible for executing the functions provided by each [NavEntryDecorator] appropriately.
 *
 * Note: the order in which the [NavEntryDecorator]s are added to the list determines their scope,
 * i.e. a [NavEntryDecorator] added earlier in a list has its data available to those added later.
 *
 * @param backStack the list of keys that represent the backstack
 * @param entryDecorators the [NavEntryDecorator]s that are providing data to the content
 * @param entryProvider a function that returns the [NavEntry] for a given key
 * @param content the content to be displayed
 */
@Composable
public fun <T : Any> DecoratedNavEntryProvider(
    backStack: List<T>,
    entryProvider: (key: T) -> NavEntry<out T>,
    entryDecorators: List<@JvmSuppressWildcards NavEntryDecorator<*>> =
        listOf(rememberSavedStateNavEntryDecorator()),
    content: @Composable (List<NavEntry<T>>) -> Unit
) {
    // Kotlin does not know these things are compatible so we need this explicit cast
    // to ensure our lambda below takes the correct type
    entryProvider as (T) -> NavEntry<T>

    // Generates a list of entries that are wrapped with the given providers
    val entries =
        backStack.map {
            val entry = entryProvider.invoke(it)
            decorateEntry(entry, entryDecorators as List<NavEntryDecorator<T>>)
        }

    // Provides the entire backstack to the previously wrapped entries
    val initial: @Composable () -> Unit = remember(entries) { { content(entries) } }

    PrepareBackStack(backStack, entryDecorators, initial)
}

/**
 * Wraps a [NavEntry] with the list of [NavEntryDecorator] in the order that the decorators were
 * added to the list.
 *
 * Invokes pop callback for popped entries that had pop animations and thus could not be cleaned up
 * by [PrepareBackStack]. PrepareBackStack has no access to animation state so we rely on this
 * function to call onPop when animation finishes.
 */
@Composable
internal fun <T : Any> decorateEntry(
    entry: NavEntry<T>,
    decorators: List<NavEntryDecorator<T>>,
): NavEntry<T> {
    val initial =
        object : NavEntryWrapper<T>(entry) {
            override val content: @Composable ((T) -> Unit) = {
                val key = entry.key
                // Tracks whether the key is changed
                var keyChanged = false
                val localInfo = LocalNavEntryDecoratorLocalInfo.current
                val keyIds = localInfo.keyIds[key]
                val lastId = keyIds!!.last()
                var id: Int =
                    rememberSaveable(keyIds.last()) {
                        keyChanged = true
                        lastId
                    }
                id =
                    rememberSaveable(keyIds.size) {
                        // if the key changed, use the current id
                        // If the key was not changed, and the current id is not in composition
                        // or on
                        // the
                        // back
                        // stack then update the id with the last item from the backstack with
                        // the
                        // associated
                        // key. This ensures that we can handle duplicates, both consecutive and
                        // non-consecutive
                        if (
                            !keyChanged &&
                                (!localInfo.idsInComposition.contains(id) || keyIds.contains(id))
                        ) {
                            lastId
                        } else {
                            id
                        }
                    }

                keyChanged = false

                // store onPop for every decorator that has ever decorated this entry
                // so that onPop will be called for newly added or removed decorators as well
                val popCallbacks = remember { LinkedHashSet<(Any) -> Unit>() }

                DisposableEffect(key1 = key) {
                    localInfo.idsInComposition.add(id)
                    onDispose {
                        val notInComposition = localInfo.idsInComposition.remove(id)
                        val popped = !localInfo.keyIds.contains(key)
                        if (notInComposition && popped) {
                            // we reverse the scopes before popping to imitate the order
                            // of onDispose calls if each scope/decorator had their own
                            // onDispose
                            // calls for clean up
                            // convert to mutableList first for backwards compat.
                            popCallbacks.toMutableList().reversed().forEach { it(key) }
                            // If the refCount is 0, remove the key from the refCount.
                            if (localInfo.keyIds[key]?.isEmpty() == true) {
                                localInfo.keyIds.remove(key)
                            }
                        }
                    }
                }
                decorators.distinct().forEach { decorator -> popCallbacks.add(decorator.onPop) }
                DecorateNavEntry(entry, decorators)
            }
        }
    return initial
}

/**
 * Sets up logic to track changes to the backstack and invokes the [DecoratedNavEntryProvider]
 * content.
 *
 * Invokes pop callback for popped entries that:
 * 1. are not animating (i.e. no pop animations) AND / OR
 * 2. have never been composed (i.e. never invoked with [DecorateNavEntry])
 */
@Composable
internal fun PrepareBackStack(
    backStack: List<Any>,
    decorators: List<NavEntryDecorator<*>>,
    content: @Composable (() -> Unit),
) {
    val localInfo = remember { NavEntryDecoratorLocalInfo() }

    DisposableEffect(key1 = backStack) { onDispose { localInfo.keyIds.clear() } }

    backStack.forEachIndexed { index, key ->
        val id = getIdForEntry(key, index)
        localInfo.keyIds.getOrPut(key) { LinkedHashSet() }.add(id)

        // store onPop for every decorator has ever decorated this key
        // so that onPop will be called for newly added or removed decorators as well
        val popCallbacks = remember(key) { LinkedHashSet<(Any) -> Unit>() }
        decorators.distinct().forEach { popCallbacks.add(it.onPop) }

        DisposableEffect(key) {
            // We update here as part of composition to ensure the value is available to
            // ProvideToEntry
            localInfo.keyIds.getOrPut(key) { LinkedHashSet() }.add(id)
            onDispose {
                // If the backStack count is less than the refCount for the key, remove the
                // state since that means we removed a key from the backstack, and set the
                // refCount to the backstack count.
                val backstackCount = backStack.count { it == key }
                val lastKeyCount = localInfo.keyIds[key]?.size ?: 0
                if (backstackCount < lastKeyCount) {
                    // if popped, remove id from set of ids for this key
                    localInfo.keyIds[key]!!.remove(id)
                    // run onPop callback
                    if (!localInfo.idsInComposition.contains(id)) {
                        // we reverse the order before popping to imitate the order
                        // of onDispose calls if each scope/decorator had their own onDispose
                        // calls for clean up. convert to mutableList first for backwards compat.
                        popCallbacks.toMutableList().reversed().forEach { it(key) }
                    }
                }
                // If the refCount is 0, remove the key from the refCount.
                if (localInfo.keyIds[key]?.isEmpty() == true) {
                    localInfo.keyIds.remove(key)
                }
            }
        }
    }
    CompositionLocalProvider(LocalNavEntryDecoratorLocalInfo provides localInfo) { content() }
}

private class NavEntryDecoratorLocalInfo {
    val keyIds: MutableMap<Any, LinkedHashSet<Int>> = mutableMapOf()
    @Suppress("PrimitiveInCollection") // The order of the element matters
    val idsInComposition: LinkedHashSet<Int> = LinkedHashSet<Int>()
    val popCallbacks: LinkedHashMap<Int, (key: Any) -> Unit> = LinkedHashMap()

    fun populatePopMap(decorators: List<NavEntryDecorator<*>>) {
        decorators.reversed().forEach { decorator ->
            popCallbacks.getOrPut(decorator.hashCode(), decorator::onPop)
        }
    }
}

private val LocalNavEntryDecoratorLocalInfo =
    staticCompositionLocalOf<NavEntryDecoratorLocalInfo> {
        error(
            "CompositionLocal LocalProviderLocalInfo not present. You must call " +
                "ProvideToBackStack before calling ProvideToEntry."
        )
    }

private fun getIdForEntry(key: Any, count: Int): Int = 31 * key.hashCode() + count
