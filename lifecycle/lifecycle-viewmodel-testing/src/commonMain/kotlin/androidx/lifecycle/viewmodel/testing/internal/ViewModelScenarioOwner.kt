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

package androidx.lifecycle.viewmodel.testing.internal

import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.testing.ViewModelScenario
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState

/**
 * An internal implementation of [ViewModelStoreOwner], [LifecycleOwner], and
 * [SavedStateRegistryOwner] used by [ViewModelScenario] as its default owner.
 *
 * This class provides the necessary infrastructure to manage a [ViewModel]'s lifecycle, saved
 * state, and associated registry for testing purposes.
 */
private class ViewModelScenarioOwner :
    ViewModelStoreOwner, LifecycleOwner, SavedStateRegistryOwner {

    override val viewModelStore = ViewModelStore()

    val lifecycleRegistry = LifecycleRegistry.createUnsafe(owner = this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    val savedStateRegistryController = SavedStateRegistryController.create(owner = this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry
}

/**
 * Creates a configured instance of [CreationExtras] for use in a [ViewModelScenario].
 *
 * This method sets up the necessary components to simulate and manage a [ViewModel]'s lifecycle and
 * saved state in a test environment.
 *
 * It initializes required keys for state restoration and lifecycle management.
 */
internal fun createScenarioExtras(
    initialExtras: CreationExtras = MutableCreationExtras(),
    restoredState: SavedState = savedState(),
    defaultArgs: SavedState = initialExtras[DEFAULT_ARGS_KEY] ?: savedState(),
): CreationExtras {
    val registryOwner = initialExtras[SAVED_STATE_REGISTRY_OWNER_KEY]
    require(registryOwner == null || registryOwner is ViewModelScenarioOwner) {
        "'SAVED_STATE_REGISTRY_OWNER_KEY' must be null or default, but was $registryOwner."
    }

    val storeOwner = initialExtras[VIEW_MODEL_STORE_OWNER_KEY]
    require(storeOwner == null || storeOwner is ViewModelScenarioOwner) {
        "'VIEW_MODEL_STORE_OWNER_KEY' must be null or default, but was $storeOwner."
    }

    val owner =
        ViewModelScenarioOwner().apply {
            savedStateRegistryController.performAttach()
            enableSavedStateHandles()
            savedStateRegistryController.performRestore(savedState = restoredState)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

    return MutableCreationExtras(initialExtras).apply {
        this[SAVED_STATE_REGISTRY_OWNER_KEY] = owner
        this[VIEW_MODEL_STORE_OWNER_KEY] = owner
        this[DEFAULT_ARGS_KEY] = defaultArgs
    }
}

/**
 * Saves the current state of [CreationExtras] associated with a [ViewModelScenario].
 *
 * This method captures the state of the [ViewModelScenarioOwner] from the
 * [SAVED_STATE_REGISTRY_OWNER_KEY] and encodes it using platform-specific serialization.
 */
internal fun saveScenarioExtras(extras: CreationExtras): SavedState {
    val owner = extras[SAVED_STATE_REGISTRY_OWNER_KEY] as ViewModelScenarioOwner

    val outState = savedState()
    owner.savedStateRegistryController.performSave(outState)

    return platformEncodeDecode(outState)
}

/**
 * Encodes and decodes a [SavedState] using platform-specific algorithms. It ensures platform
 * requirements during tests.
 */
internal expect fun platformEncodeDecode(savedState: SavedState): SavedState
