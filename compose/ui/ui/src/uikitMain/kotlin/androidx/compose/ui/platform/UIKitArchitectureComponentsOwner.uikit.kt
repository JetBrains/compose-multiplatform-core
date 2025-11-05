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

package androidx.compose.ui.platform

import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.enableSavedStateHandles
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.savedState

internal class UIKitArchitectureComponentsOwner : DefaultArchitectureComponentsOwner() {
    private var _savedStateController: SavedStateRegistryController? = null
    override val savedStateController: SavedStateRegistryController
        get() = _savedStateController ?: error("SavedStateRegistryController is not initialized")

    fun initSavedStateController(savedState: SavedState?) {
        _savedStateController = SavedStateRegistryController.create(this)

        savedStateController.performAttach()
        savedStateController.performRestore(savedState)
        enableSavedStateHandles()
    }

    fun saveState(): SavedState {
        val savedState = savedState()
        savedStateController.performSave(savedState)
        _savedStateController = null
        return savedState
    }

    fun onLifecycleState(state: State) {
        lifecycle.currentState = state
        if (state == State.DESTROYED) {
            viewModelStore.clear()
        }
    }
}
