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

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Provides the content of a [NavRecord] with a [SavedStateRegistryOwner] and provides that
 * [SavedStateRegistryOwner] as a [LocalSavedStateRegistryOwner] so that it is available within the
 * content.
 */
public object SavedStateNavContentWrapper : NavContentWrapper {

    @Composable
    override fun <T : Any> WrapContent(record: NavRecord<T>) {
        val key = record.key
        val childRegistry by
            rememberSaveable(
                key,
                stateSaver =
                    Saver(
                        save = { it.savedState },
                        restore = { RecordSavedStateRegistry().apply { savedState = it } }
                    )
            ) {
                mutableStateOf(RecordSavedStateRegistry())
            }

        CompositionLocalProvider(LocalSavedStateRegistryOwner provides childRegistry) {
            record.content.invoke(key)
        }

        DisposableEffect(key1 = key) {
            childRegistry.savedStateRegistryController.performAttach()
            childRegistry.savedStateRegistryController.performRestore(childRegistry.savedState)
            childRegistry.lifecycle.currentState = Lifecycle.State.RESUMED
            onDispose {
                val bundle = Bundle()
                childRegistry.savedStateRegistryController.performSave(bundle)
                childRegistry.savedState = bundle
                childRegistry.lifecycle.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}

private class RecordSavedStateRegistry : SavedStateRegistryOwner {
    override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)
    val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    var savedState: Bundle? = null
}
