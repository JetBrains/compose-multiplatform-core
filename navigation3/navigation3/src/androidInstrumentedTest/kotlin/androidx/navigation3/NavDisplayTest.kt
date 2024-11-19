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

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.kruth.assertThat
import androidx.savedstate.SavedStateRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertWithMessage
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class NavDisplayTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun testContentShown() {
        composeTestRule.setContent {
            val manager = rememberNavWrapperManager(emptyList())
            NavDisplay(backstack = mutableStateListOf(first), wrapperManager = manager) {
                Record(first) { Text(first) }
            }
        }

        assertThat(composeTestRule.onNodeWithText(first).isDisplayed()).isTrue()
    }

    @Test
    fun testContentChanged() {
        lateinit var backstack: MutableList<Any>
        composeTestRule.setContent {
            backstack = remember { mutableStateListOf(first) }
            val manager = rememberNavWrapperManager(emptyList())
            NavDisplay(backstack = backstack, wrapperManager = manager) {
                when (it) {
                    first -> Record(first) { Text(first) }
                    second -> Record(second) { Text(second) }
                    else -> error("Invalid key passed")
                }
            }
        }

        assertThat(composeTestRule.onNodeWithText(first).isDisplayed()).isTrue()

        composeTestRule.runOnIdle { backstack.add(second) }

        assertThat(composeTestRule.onNodeWithText(first).isDisplayed()).isFalse()
        assertThat(composeTestRule.onNodeWithText(second).isDisplayed()).isTrue()
    }

    @Test
    fun testDialog() {
        lateinit var backstack: MutableList<Any>
        composeTestRule.setContent {
            backstack = remember { mutableStateListOf(first) }
            val manager = rememberNavWrapperManager(emptyList())
            NavDisplay(
                backstack = backstack,
                wrapperManager = manager,
                onBack = {
                    // removeLast requires API 35
                    backstack.removeAt(backstack.size - 1)
                }
            ) {
                when (it) {
                    first -> Record(first) { Text(first) }
                    second -> Record(second, NavDisplay.isDialog(true)) { Text(second) }
                    else -> error("Invalid key passed")
                }
            }
        }

        assertThat(composeTestRule.onNodeWithText(first).isDisplayed()).isTrue()

        composeTestRule.runOnIdle { backstack.add(second) }

        // Both first and second should be showing if we are on a dialog.
        assertThat(composeTestRule.onNodeWithText(first).isDisplayed()).isTrue()
        assertThat(composeTestRule.onNodeWithText(second).isDisplayed()).isTrue()
    }

    @Test
    fun testOnBack() {
        lateinit var onBackDispatcher: OnBackPressedDispatcher
        lateinit var backstack: MutableList<Any>
        composeTestRule.setContent {
            onBackDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            backstack = remember { mutableStateListOf(first) }
            val manager = rememberNavWrapperManager(emptyList())
            NavDisplay(
                backstack = backstack,
                wrapperManager = manager,
                onBack = {
                    // removeLast requires API 35
                    backstack.removeAt(backstack.size - 1)
                }
            ) {
                when (it) {
                    first -> Record(first) { Text(first) }
                    second -> Record(second) { Text(second) }
                    else -> error("Invalid key passed")
                }
            }
        }

        assertThat(composeTestRule.onNodeWithText(first).isDisplayed()).isTrue()

        composeTestRule.runOnIdle { backstack.add(second) }

        assertThat(composeTestRule.onNodeWithText(second).isDisplayed()).isTrue()

        composeTestRule.runOnIdle { onBackDispatcher.onBackPressed() }

        assertThat(composeTestRule.onNodeWithText(first).isDisplayed()).isTrue()
    }

    @Test
    fun testStateOfInactiveContentIsRestoredWhenWeGoBackToIt() {
        var increment = 0
        var numberOnScreen1 = -1
        lateinit var backstack: MutableList<Any>
        composeTestRule.setContent {
            backstack = remember { mutableStateListOf(first) }
            val manager = rememberNavWrapperManager(emptyList())
            NavDisplay(backstack = backstack, wrapperManager = manager) {
                when (it) {
                    first -> Record(first) { numberOnScreen1 = rememberSaveable { increment++ } }
                    second -> Record(second) {}
                    else -> error("Invalid key passed")
                }
            }
        }

        composeTestRule.runOnIdle {
            assertWithMessage("Initial number should be 0").that(numberOnScreen1).isEqualTo(0)
            numberOnScreen1 = -1
            backstack.add(second)
        }

        composeTestRule.runOnIdle {
            assertWithMessage("The number should be -1").that(numberOnScreen1).isEqualTo(-1)
            // removeLast requires API 35
            backstack.removeAt(backstack.size - 1)
        }

        composeTestRule.runOnIdle {
            assertWithMessage("The number should be restored").that(numberOnScreen1).isEqualTo(0)
        }
    }

    @Test
    fun testIndividualSavedStateRegistries() {
        lateinit var mainRegistry: SavedStateRegistry
        lateinit var registry1: SavedStateRegistry
        lateinit var registry2: SavedStateRegistry
        lateinit var backstack: MutableList<Any>
        composeTestRule.setContent {
            mainRegistry = LocalSavedStateRegistryOwner.current.savedStateRegistry
            backstack = remember { mutableStateListOf(first) }
            val manager = rememberNavWrapperManager(listOf(SavedStateNavContentWrapper))
            NavDisplay(backstack = backstack, wrapperManager = manager) {
                when (it) {
                    first ->
                        Record(first) {
                            registry1 = LocalSavedStateRegistryOwner.current.savedStateRegistry
                        }
                    second ->
                        Record(second) {
                            registry2 = LocalSavedStateRegistryOwner.current.savedStateRegistry
                        }
                    else -> error("Invalid key passed")
                }
            }
        }

        composeTestRule.runOnIdle {
            assertWithMessage("Registries should be unique")
                .that(mainRegistry)
                .isNotEqualTo(registry1)
            backstack.add(second)
        }

        composeTestRule.runOnIdle {
            assertWithMessage("Registries should be unique").that(registry2).isNotEqualTo(registry1)
        }
    }
}

private const val first = "first"
private const val second = "second"
