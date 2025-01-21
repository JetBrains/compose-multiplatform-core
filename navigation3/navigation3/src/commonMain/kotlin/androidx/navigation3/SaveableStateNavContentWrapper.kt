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

import androidx.collection.MutableObjectIntMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

/**
 * Wraps the content of a [NavRecord] with a [SaveableStateHolder.SaveableStateProvider] to ensure
 * that calls to [rememberSaveable] within the content work properly and that state can be saved.
 *
 * This [NavContentWrapper] is the only one that is **required** as saving state is considered a
 * non-optional feature.
 */
public class SaveableStateNavContentWrapper : NavContentWrapper {
    private var savedStateHolder: SaveableStateHolder? = null
    private val refCount: MutableObjectIntMap<Any> = MutableObjectIntMap()
    private var backstackSize = 0

    @Composable
    override fun WrapBackStack(backStack: List<Any>) {
        DisposableEffect(key1 = backStack) {
            refCount.clear()
            onDispose {}
        }

        savedStateHolder = rememberSaveableStateHolder()
        backstackSize = backStack.size
        backStack.forEach { key ->
            DisposableEffect(key1 = key) {
                refCount[key] = refCount.getOrDefault(key, 0).plus(1)
                onDispose {
                    if (refCount[key] == 0) {
                        savedStateHolder!!.removeState(key)
                    } else {
                        refCount[key] =
                            refCount
                                .getOrElse(key) {
                                    error(
                                        "Attempting to incorrectly dispose of backstack state in " +
                                            "SaveableStateNavContentWrapper"
                                    )
                                }
                                .minus(1)
                    }
                }
            }
        }
    }

    @Composable
    public override fun <T : Any> WrapContent(record: NavRecord<T>) {
        val key = record.key
        DisposableEffect(key1 = key) {
            refCount[key] = refCount.getOrDefault(key, 0).plus(1)
            onDispose {
                if (refCount[key] == 0) {
                    savedStateHolder?.removeState(key)
                } else {
                    refCount[key] =
                        refCount
                            .getOrElse(key) {
                                error(
                                    "Attempting to incorrectly dispose of state associated with " +
                                        "key $key in SaveableStateNavContentWrapper"
                                )
                            }
                            .minus(1)
                }
            }
        }

        val id: Int = rememberSaveable(key) { key.hashCode() + backstackSize }
        savedStateHolder?.SaveableStateProvider(id) { record.content.invoke(key) }
    }
}
