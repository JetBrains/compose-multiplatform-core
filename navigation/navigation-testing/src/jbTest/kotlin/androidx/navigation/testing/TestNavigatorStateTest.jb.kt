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

package androidx.navigation.testing

import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.testing.TestNavigatorStateTest.FloatingTestDestination

private const val TEST_NAVIGATOR_NAME = "test"

internal actual class TestNavigator : Navigator<NavDestination>(TEST_NAVIGATOR_NAME) {
    override fun createDestination(): NavDestination = NavDestination(this)
}

internal actual class TestTransitionNavigator : Navigator<NavDestination>(TEST_NAVIGATOR_NAME) {
    private val testLifecycleOwner = TestLifecycleOwner()
    actual val testLifecycle = testLifecycleOwner.lifecycle

    override fun createDestination(): NavDestination = NavDestination(this)

    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ) {
        entries.forEach { entry ->
            state.pushWithTransition(entry)
        }
    }

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        state.popWithTransition(popUpTo, savedState)
    }
}

internal actual class FloatingWindowTestNavigator : Navigator<FloatingTestDestination>(TEST_NAVIGATOR_NAME) {
    override fun createDestination(): FloatingTestDestination = FloatingTestDestination(this)
}
