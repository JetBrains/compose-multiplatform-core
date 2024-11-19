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

package androidx.navigation3.samples

import androidx.annotation.Sampled
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.ViewModelStoreNavContentWrapper
import androidx.navigation3.AnimatedNavDisplay
import androidx.navigation3.NavDisplay
import androidx.navigation3.Record
import androidx.navigation3.SavedStateNavContentWrapper
import androidx.navigation3.rememberNavWrapperManager

@Sampled
@Composable
fun BasicNav() {
    val backStack = rememberMutableStateListOf(Profile)
    val manager =
        rememberNavWrapperManager(
            listOf(SavedStateNavContentWrapper, ViewModelStoreNavContentWrapper)
        )
    NavDisplay(
        backstack = backStack,
        wrapperManager = manager,
        onBack = { backStack.removeLast() },
    ) { key ->
        when (key) {
            Profile -> {
                Record(Profile) {
                    val viewModel = viewModel<ProfileViewModel>()
                    Profile(viewModel, { backStack.add(it) }) { backStack.removeLast() }
                }
            }
            Scrollable -> {
                Record(Scrollable) { Scrollable({ backStack.add(it) }) { backStack.removeLast() } }
            }
            Dialog -> {
                Record(Dialog, featureMap = NavDisplay.isDialog(true)) { third ->
                    DialogContent { backStack.removeLast() }
                }
            }
            is Dashboard -> {
                Record(key) { dashboardArgs ->
                    val userId = dashboardArgs.userId
                    Dashboard(userId, onBack = { backStack.removeLast() })
                }
            }
            else -> {
                Record(Unit) { Text(text = "Invalid Key") }
            }
        }
    }
}

class ProfileViewModel : ViewModel() {
    val name = "no user"
}

@Sampled
@Composable
fun AnimatedNav() {
    val backStack = rememberMutableStateListOf(Profile)
    val manager =
        rememberNavWrapperManager(
            listOf(SavedStateNavContentWrapper, ViewModelStoreNavContentWrapper)
        )
    AnimatedNavDisplay(
        backstack = backStack,
        wrapperManager = manager,
        onBack = { backStack.removeLast() },
    ) { key ->
        when (key) {
            Profile -> {
                Record(
                    Profile,
                    AnimatedNavDisplay.transition(
                        slideInHorizontally { it },
                        slideOutHorizontally { it }
                    )
                ) {
                    val viewModel = viewModel<ProfileViewModel>()
                    Profile(viewModel, { backStack.add(it) }) { backStack.removeLast() }
                }
            }
            Scrollable -> {
                Record(
                    Scrollable,
                    AnimatedNavDisplay.transition(
                        slideInHorizontally { it },
                        slideOutHorizontally { it }
                    )
                ) {
                    Scrollable({ backStack.add(it) }) { backStack.removeLast() }
                }
            }
            Dialog -> {
                Record(Dialog, featureMap = NavDisplay.isDialog(true)) {
                    DialogContent { backStack.removeLast() }
                }
            }
            is Dashboard -> {
                Record(
                    key,
                    AnimatedNavDisplay.transition(
                        slideInHorizontally { it },
                        slideOutHorizontally { it }
                    )
                ) { dashboardArgs ->
                    val userId = dashboardArgs.userId
                    Dashboard(userId, onBack = { backStack.removeLast() })
                }
            }
            else -> {
                Record(Unit) { Text(text = "Invalid Key") }
            }
        }
    }
}
