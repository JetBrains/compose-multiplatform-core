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

package androidx.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.collections.LinkedHashSet

/**
 * Wraps the content of a [NavEntry] with a [SaveableStateHolder.SaveableStateProvider] to ensure
 * that calls to [rememberSaveable] within the content work properly and that state can be saved.
 *
 * This [NavLocalProvider] is the only one that is **required** as saving state is considered a
 * non-optional feature.
 */
public object SaveableStateNavLocalProvider : NavLocalProvider {

    @Composable
    override fun ProvideToBackStack(backStack: List<Any>, content: @Composable () -> Unit) {
        val localInfo = remember { SaveableStateNavLocalInfo() }
        DisposableEffect(key1 = backStack) { onDispose { localInfo.refCount.clear() } }

        localInfo.savedStateHolder = rememberSaveableStateHolder()
        backStack.forEachIndexed { index, key ->
            // We update here as part of composition to ensure the value is available to
            // ProvideToEntry
            localInfo.refCount.getOrPut(key) { LinkedHashSet<Int>() }.add(getIdForKey(key, index))
            DisposableEffect(key1 = key) {
                // We update here at the end of composition in case the backstack changed and
                // everything was cleared.
                localInfo.refCount
                    .getOrPut(key) { LinkedHashSet<Int>() }
                    .add(getIdForKey(key, index))
                onDispose {
                    // If the backStack count is less than the refCount for the key, remove the
                    // state since that means we removed a key from the backstack, and set the
                    // refCount to the backstack count.
                    val backstackCount = backStack.count { it == key }
                    val lastKeyCount = localInfo.refCount[key]?.size ?: 0
                    if (backstackCount < lastKeyCount) {
                        // The set of the ids associated with this key
                        @Suppress("PrimitiveInCollection") // The order of the element matters
                        val idsSet = localInfo.refCount[key]!!
                        val id = idsSet.last()
                        idsSet.remove(id)
                        if (!localInfo.idsInComposition.contains(id)) {
                            localInfo.savedStateHolder!!.removeState(id)
                        }
                    }
                    // If the refCount is 0, remove the key from the refCount.
                    if (localInfo.refCount[key]?.isEmpty() == true) {
                        localInfo.refCount.remove(key)
                    }
                }
            }
        }

        CompositionLocalProvider(LocalSaveableStateNavLocalInfo provides localInfo) {
            content.invoke()
        }
    }

    @Composable
    public override fun <T : Any> ProvideToEntry(entry: NavEntry<T>) {
        val localInfo = LocalSaveableStateNavLocalInfo.current
        val key = entry.key
        // Tracks whether the key is changed
        var keyChanged = false
        var id: Int =
            rememberSaveable(key) {
                keyChanged = true
                localInfo.refCount[key]!!.last()
            }
        id =
            rememberSaveable(localInfo.refCount[key]?.size) {
                // if the key changed, use the current id
                // If the key was not changed, and the current id is not in composition or on the
                // back
                // stack then update the id with the last item from the backstack with the
                // associated
                // key. This ensures that we can handle duplicates, both consecutive and
                // non-consecutive
                if (
                    !keyChanged &&
                        (!localInfo.idsInComposition.contains(id) ||
                            localInfo.refCount[key]?.contains(id) == true)
                ) {
                    localInfo.refCount[key]!!.last()
                } else {
                    id
                }
            }
        keyChanged = false
        DisposableEffect(key1 = key) {
            localInfo.idsInComposition.add(id)
            onDispose {
                if (localInfo.idsInComposition.remove(id) && !localInfo.refCount.contains(key)) {
                    localInfo.savedStateHolder!!.removeState(id)
                    // If the refCount is 0, remove the key from the refCount.
                    if (localInfo.refCount[key]?.isEmpty() == true) {
                        localInfo.refCount.remove(key)
                    }
                }
            }
        }

        localInfo.savedStateHolder?.SaveableStateProvider(id) { entry.content.invoke(key) }
    }
}

internal val LocalSaveableStateNavLocalInfo =
    staticCompositionLocalOf<SaveableStateNavLocalInfo> {
        error(
            "CompositionLocal LocalSaveableStateNavLocalInfo not present. You must call " +
                "ProvideToBackStack before calling ProvideToEntry."
        )
    }

internal class SaveableStateNavLocalInfo {
    internal var savedStateHolder: SaveableStateHolder? = null
    internal val refCount: MutableMap<Any, LinkedHashSet<Int>> = mutableMapOf()
    @Suppress("PrimitiveInCollection") // The order of the element matters
    internal val idsInComposition: LinkedHashSet<Int> = LinkedHashSet<Int>()
}

internal fun getIdForKey(key: Any, count: Int): Int = 31 * key.hashCode() + count
