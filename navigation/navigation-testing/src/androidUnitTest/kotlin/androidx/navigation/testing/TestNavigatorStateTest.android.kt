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

import androidx.navigation.NavDestination
import androidx.navigation.Navigator

@Navigator.Name("test")
internal actual class TestNavigator : Navigator<NavDestination>() {
    override fun createDestination(): NavDestination = NavDestination(this)
}

@Navigator.Name("test")
internal actual class TestTransitionNavigator : Navigator<NavDestination>() {
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

@Navigator.Name("test")
internal actual class FloatingWindowTestNavigator : Navigator<FloatingTestDestination>() {
    override fun createDestination(): FloatingTestDestination = FloatingTestDestination(this)
}
