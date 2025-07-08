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

import androidx.compose.ui.window.ApplicationForegroundStateListener
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

internal class UIKitStateOwner(
    savedState: SavedState? = null,
): LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    override val lifecycle = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    var isViewAppeared = false
        set(value) {
            field = value
            updateLifecycleState()
        }
    var isAppForeground = ApplicationForegroundStateListener.isApplicationForeground
        set(value) {
            field = value
            updateLifecycleState()
        }
    var isAppActive = isAppForeground
        set(value) {
            field = value
            updateLifecycleState()
        }

    private var isDisposed = false

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(savedState)
        enableSavedStateHandles()
        updateLifecycleState()
    }

    /**
     * Saves the current UI state into a [SavedState] object. The returned state can be used
     * to restore the UI state later by passing it to the constructor's [savedState] parameter.
     *
     * @return A [SavedState] object containing the current UI state.
     */
    fun saveState(): SavedState {
        val state = androidx.savedstate.savedState()
        savedStateController.performSave(state)
        return state
    }

    fun dispose() {
        isDisposed = true
        viewModelStore.clear()
        updateLifecycleState()
    }

    private fun updateLifecycleState() {
        lifecycle.currentState = when {
            isDisposed -> State.DESTROYED
            isViewAppeared && isAppForeground && isAppActive -> State.RESUMED
            isViewAppeared && isAppForeground && !isAppActive -> State.STARTED
            else -> State.CREATED
        }
    }
}