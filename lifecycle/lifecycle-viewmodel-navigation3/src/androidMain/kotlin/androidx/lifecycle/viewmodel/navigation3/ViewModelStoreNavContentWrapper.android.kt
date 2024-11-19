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

package androidx.lifecycle.viewmodel.navigation3

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.NavContentWrapper
import androidx.navigation3.Record

/**
 * Provides the content of a [Record] with a [ViewModelStoreOwner] and provides that
 * [ViewModelStoreOwner] as a [LocalViewModelStoreOwner] so that it is available within the content.
 */
public object ViewModelStoreNavContentWrapper : NavContentWrapper {

    @Composable
    override fun WrapBackStack(backStack: List<Any>) {
        val recordViewModelStoreProvider = viewModel { RecordViewModel() }
        recordViewModelStoreProvider.ownerInBackStack.clear()
        recordViewModelStoreProvider.ownerInBackStack.addAll(backStack)
    }

    @Composable
    override fun <T : Any> WrapContent(record: Record<T>) {
        val key = record.key
        val recordViewModelStoreProvider = viewModel { RecordViewModel() }
        val viewModelStore = recordViewModelStoreProvider.viewModelStoreForKey(key)
        // This ensures we always keep viewModels on config changes.
        val activity = LocalActivity.current
        remember(key, viewModelStore) {
            object : RememberObserver {
                override fun onAbandoned() {
                    if (!recordViewModelStoreProvider.ownerInBackStack.contains(key)) {
                        disposeIfNotChangingConfiguration()
                    }
                }

                override fun onForgotten() {
                    if (!recordViewModelStoreProvider.ownerInBackStack.contains(key)) {
                        disposeIfNotChangingConfiguration()
                    }
                }

                override fun onRemembered() {}

                fun disposeIfNotChangingConfiguration() {
                    if (activity?.isChangingConfigurations != true) {
                        recordViewModelStoreProvider.removeViewModelStoreOwnerForKey(key)?.clear()
                    }
                }
            }
        }

        val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides
                object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
                    override val viewModelStore: ViewModelStore
                        get() = viewModelStore

                    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
                        get() = SavedStateViewModelFactory(null, savedStateRegistryOwner)

                    override val defaultViewModelCreationExtras: CreationExtras
                        get() =
                            MutableCreationExtras().also {
                                it[SAVED_STATE_REGISTRY_OWNER_KEY] = savedStateRegistryOwner
                                it[VIEW_MODEL_STORE_OWNER_KEY] = this
                            }
                }
        ) {
            record.content.invoke(key)
        }
    }
}

private class RecordViewModel : ViewModel() {
    private val owners = mutableMapOf<Any, ViewModelStore>()
    val ownerInBackStack = mutableListOf<Any>()

    fun viewModelStoreForKey(key: Any): ViewModelStore = owners.getOrPut(key) { ViewModelStore() }

    fun removeViewModelStoreOwnerForKey(key: Any): ViewModelStore? = owners.remove(key)

    override fun onCleared() {
        owners.forEach { (_, store) -> store.clear() }
    }
}
