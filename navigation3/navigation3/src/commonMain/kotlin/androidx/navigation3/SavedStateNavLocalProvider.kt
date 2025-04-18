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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.savedState

/**
 * Provides the content of a [NavEntry] with a [SavedStateRegistryOwner] and provides that
 * [SavedStateRegistryOwner] as a [LocalSavedStateRegistryOwner] so that it is available within the
 * content.
 */
public object SavedStateNavLocalProvider : NavLocalProvider {

    @Composable
    override fun <T : Any> ProvideToEntry(entry: NavEntry<T>) {
        val key = entry.key
        val childRegistry by
            rememberSaveable(
                key,
                stateSaver =
                    Saver(
                        save = { it.savedState },
                        restore = { EntrySavedStateRegistry().apply { savedState = it } }
                    )
            ) {
                mutableStateOf(EntrySavedStateRegistry())
            }

        CompositionLocalProvider(LocalSavedStateRegistryOwner provides childRegistry) {
            entry.content.invoke(key)
        }

        DisposableEffect(key1 = key) {
            childRegistry.lifecycle.currentState = Lifecycle.State.RESUMED
            onDispose {
                val savedState = savedState()
                childRegistry.savedStateRegistryController.performSave(savedState)
                childRegistry.savedState = savedState
                childRegistry.lifecycle.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}

private class EntrySavedStateRegistry : SavedStateRegistryOwner {
    override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)
    val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    var savedState: SavedState? = null

    init {
        savedStateRegistryController.performRestore(savedState)
    }
}
