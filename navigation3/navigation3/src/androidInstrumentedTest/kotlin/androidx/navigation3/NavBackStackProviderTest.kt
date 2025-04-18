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

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.kruth.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class NavBackStackProviderTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun callWrapperFunctions() {
        var calledWrapBackStack = false
        var calledWrapContent = false
        val provider =
            createTestNavLocalProvider<Any>(
                provideToBackStack = { _, content ->
                    calledWrapBackStack = true
                    content.invoke()
                },
                provideToEntry = { _ -> calledWrapContent = true }
            )

        composeTestRule.setContent {
            NavBackStackProvider(
                backStack = listOf("something"),
                localProviders = listOf(provider),
                entryProvider = { NavEntry("something") {} }
            ) { records ->
                records.last().content.invoke("something")
            }
        }

        assertThat(calledWrapBackStack).isTrue()
        assertThat(calledWrapContent).isTrue()
    }

    @Test
    fun callWrapperFunctionsOnce() {
        var calledWrapBackStackCount = 0
        var calledWrapContentCount = 0
        val provider =
            createTestNavLocalProvider<Any>(
                provideToBackStack = { _, content ->
                    calledWrapBackStackCount++
                    content.invoke()
                },
                provideToEntry = { _ -> calledWrapContentCount++ }
            )

        composeTestRule.setContent {
            NavBackStackProvider(
                backStack = listOf("something"),
                localProviders = listOf(provider, provider),
                entryProvider = { NavEntry("something") {} }
            ) { records ->
                records.last().content.invoke("something")
            }
        }

        assertThat(calledWrapBackStackCount).isEqualTo(1)
        assertThat(calledWrapContentCount).isEqualTo(1)
    }

    @Test
    fun wrapperFunctionsCallOrder() {
        var callOrder = -1
        var backStackProvider: Int = -1
        var entryProvider: Int = -1
        val provider =
            createTestNavLocalProvider<Any>(
                provideToBackStack = { _, content ->
                    backStackProvider = ++callOrder
                    content.invoke()
                },
                provideToEntry = { entry ->
                    entryProvider = ++callOrder
                    entry.content.invoke(entry.key)
                }
            )

        lateinit var backStack: MutableList<Any>

        composeTestRule.setContent {
            backStack = remember { mutableStateListOf("something") }
            NavBackStackProvider(
                backStack = backStack,
                localProviders = listOf(provider),
                entryProvider = { NavEntry("something") {} }
            ) { entries ->
                entries.lastOrNull()?.content?.invoke("something")
            }
        }

        composeTestRule.waitForIdle()

        assertThat(backStackProvider).isEqualTo(0)
        assertThat(entryProvider).isEqualTo(1)
    }

    @Test
    fun nestedWrapperFunctionsCallOrder() {
        var callOrder = -1
        var outerBackStackProvider: Int = -1
        var outerEntryProvider: Int = -1
        var innerBackStackProvider: Int = -1
        var innerEntryProvider: Int = -1
        val innerProvider =
            createTestNavLocalProvider<Any>(
                provideToBackStack = { _, content ->
                    innerBackStackProvider = ++callOrder
                    content.invoke()
                },
                provideToEntry = { entry ->
                    innerEntryProvider = ++callOrder
                    entry.content.invoke(entry.key)
                }
            )

        val outerProvider =
            createTestNavLocalProvider<Any>(
                provideToBackStack = { _, content ->
                    outerBackStackProvider = ++callOrder
                    content.invoke()
                },
                provideToEntry = { entry ->
                    outerEntryProvider = ++callOrder
                    entry.content.invoke(entry.key)
                }
            )

        lateinit var backStack: MutableList<Any>

        composeTestRule.setContent {
            backStack = remember { mutableStateListOf("something") }
            NavBackStackProvider(
                backStack = backStack,
                localProviders = listOf(outerProvider, innerProvider),
                entryProvider = { NavEntry("something") {} }
            ) { entries ->
                entries.lastOrNull()?.content?.invoke("something")
            }
        }

        composeTestRule.waitForIdle()

        assertThat(outerBackStackProvider).isEqualTo(0)
        assertThat(innerBackStackProvider).isEqualTo(1)
        assertThat(outerEntryProvider).isEqualTo(2)
        assertThat(innerEntryProvider).isEqualTo(3)
    }

    @Test
    fun wrapperFunctionsDisposeOrder() {
        var callOrder = -1
        var backStackProvider: Int = -1
        var entryProvider: Int = -1
        val provider =
            createTestNavLocalProvider(
                provideToBackStack = { backStack, content ->
                    DisposableEffect(backStack.lastOrNull()) {
                        onDispose { backStackProvider = ++callOrder }
                    }
                    content.invoke()
                },
                provideToEntry = { entry ->
                    DisposableEffect(entry.key) { onDispose { entryProvider = ++callOrder } }
                    entry.content.invoke(entry.key)
                }
            )

        lateinit var backStack: MutableList<Any>
        composeTestRule.setContent {
            backStack = remember { mutableStateListOf("something") }
            NavBackStackProvider(
                backStack = backStack,
                localProviders =
                    listOf(
                        provider,
                    ),
                entryProvider = { NavEntry("something") {} }
            ) { entries ->
                entries.lastOrNull()?.content?.invoke("something")
            }
        }

        composeTestRule.runOnIdle { backStack.clear() }
        composeTestRule.waitForIdle()

        assertThat(entryProvider).isEqualTo(0)
        assertThat(backStackProvider).isEqualTo(1)
    }

    @Test
    fun nestedWrapperFunctionsDisposeOrder() {
        var callOrder = -1
        var outerBackStackProvider: Int = -1
        var outerEntryProvider: Int = -1
        var innerBackStackProvider: Int = -1
        var innerEntryProvider: Int = -1
        val innerProvider =
            createTestNavLocalProvider(
                provideToBackStack = { backStack, content ->
                    DisposableEffect(backStack.lastOrNull()) {
                        onDispose { innerBackStackProvider = ++callOrder }
                    }
                    content.invoke()
                },
                provideToEntry = { entry ->
                    DisposableEffect(entry.key) { onDispose { innerEntryProvider = ++callOrder } }
                    entry.content.invoke(entry.key)
                }
            )

        val outerProvider =
            createTestNavLocalProvider(
                provideToBackStack = { backStack, content ->
                    DisposableEffect(backStack.lastOrNull()) {
                        onDispose { outerBackStackProvider = ++callOrder }
                    }
                    content.invoke()
                },
                provideToEntry = { entry ->
                    DisposableEffect(entry.key) { onDispose { outerEntryProvider = ++callOrder } }
                    entry.content.invoke(entry.key)
                }
            )

        lateinit var backStack: MutableList<Any>

        composeTestRule.setContent {
            backStack = remember { mutableStateListOf("something") }
            NavBackStackProvider(
                backStack = backStack,
                localProviders = listOf(outerProvider, innerProvider),
                entryProvider = { NavEntry("something") {} }
            ) { entries ->
                entries.lastOrNull()?.content?.invoke("something")
            }
        }

        composeTestRule.waitForIdle()
        backStack.clear()
        composeTestRule.waitForIdle()

        assertThat(innerEntryProvider).isEqualTo(0)
        assertThat(outerEntryProvider).isEqualTo(1)
        assertThat(innerBackStackProvider).isEqualTo(2)
        assertThat(outerBackStackProvider).isEqualTo(3)
    }
}
