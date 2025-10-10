/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.navigation.compose

import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.kruth.assertWithMessage
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.testing.TestNavigatorState
import androidx.testutils.TestNavigator
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NavBackStackEntryProviderTest {

    @Test
    fun testViewModelStoreOwnerProvided() = runComposeUiTestOnUiThread {
        val backStackEntry = createBackStackEntry()
        var viewModelStoreOwner: ViewModelStoreOwner? = null

        setContent {
            val saveableStateHolder = rememberSaveableStateHolder()
            backStackEntry.LocalOwnersProvider(saveableStateHolder) {
                viewModelStoreOwner = LocalViewModelStoreOwner.current
            }
        }

        assertWithMessage("ViewModelStoreOwner is provided by $backStackEntry")
            .that(viewModelStoreOwner)
            .isEqualTo(backStackEntry)
    }

    @Test
    fun testLifecycleOwnerProvided() = runComposeUiTestOnUiThread {
        val backStackEntry = createBackStackEntry()
        var lifecycleOwner: LifecycleOwner? = null

        setContent {
            val saveableStateHolder = rememberSaveableStateHolder()
            backStackEntry.LocalOwnersProvider(saveableStateHolder) {
                lifecycleOwner = LocalLifecycleOwner.current
            }
        }

        assertWithMessage("LifecycleOwner is provided by $backStackEntry")
            .that(lifecycleOwner)
            .isEqualTo(backStackEntry)
    }
}

internal fun createBackStackEntry(): NavBackStackEntry {
    val testNavigator = TestNavigator()
    val testNavigatorState = TestNavigatorState()
    testNavigator.onAttach(testNavigatorState)
    val backStackEntry =
        testNavigatorState.createBackStackEntry(testNavigator.createDestination(), null)
    // We navigate to move the NavBackStackEntry to the correct state
    testNavigator.navigate(listOf(backStackEntry), null, null)
    return backStackEntry
}
