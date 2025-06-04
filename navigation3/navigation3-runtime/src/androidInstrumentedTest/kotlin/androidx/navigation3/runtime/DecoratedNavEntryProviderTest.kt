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

package androidx.navigation3.runtime

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.kruth.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class DecoratedNavEntryProviderTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun callDecorator() {
        var calledWrapContent = false

        val decorator = createTestNavEntryDecorator<Any> { entry -> calledWrapContent = true }

        composeTestRule.setContent {
            DecoratedNavEntryProvider(
                backStack = listOf("something"),
                entryDecorators = listOf(decorator),
                entryProvider = { NavEntry("something") {} },
            ) { records ->
                records.last().content.invoke("something")
            }
        }

        assertThat(calledWrapContent).isTrue()
    }

    @Test
    fun callDecoratorOnce() {
        var calledWrapContentCount = 0

        val decorator = createTestNavEntryDecorator<Any> { entry -> calledWrapContentCount++ }

        composeTestRule.setContent {
            DecoratedNavEntryProvider(
                backStack = listOf("something"),
                entryDecorators = listOf(decorator, decorator),
                entryProvider = { NavEntry("something") {} },
            ) { records ->
                records.last().content.invoke("something")
            }
        }

        composeTestRule.runOnIdle { assertThat(calledWrapContentCount).isEqualTo(1) }
    }

    @Test
    fun nestedDecoratorsCallOrder() {
        var callOrder = -1
        var outerEntryDecorator: Int = -1
        var innerEntryDecorator: Int = -1
        val innerDecorator =
            createTestNavEntryDecorator<Any> { entry ->
                innerEntryDecorator = ++callOrder
                entry.content.invoke(entry.key)
            }

        val outerDecorator =
            createTestNavEntryDecorator<Any> { entry ->
                outerEntryDecorator = ++callOrder
                entry.content.invoke(entry.key)
            }

        composeTestRule.setContent {
            DecoratedNavEntryProvider(
                backStack = listOf("something"),
                entryDecorators = listOf(outerDecorator, innerDecorator),
                entryProvider = { NavEntry("something") {} },
            ) { entries ->
                entries.lastOrNull()?.content?.invoke("something")
            }
        }

        composeTestRule.waitForIdle()

        assertThat(outerEntryDecorator).isEqualTo(0)
        assertThat(innerEntryDecorator).isEqualTo(1)
    }

    @Test
    fun decoratorsOnPop() {
        val poppedEntries = mutableListOf<Int>()
        val decorator =
            createTestNavEntryDecorator<Any>(onPop = { key -> poppedEntries.add(key as Int) }) {
                entry ->
                entry.content.invoke(entry.key)
            }
        lateinit var backStack: SnapshotStateList<Int>
        composeTestRule.setContent {
            backStack = mutableStateListOf(1, 2)
            DecoratedNavEntryProvider(
                backStack = backStack,
                entryDecorators = listOf(decorator),
                entryProvider = { key ->
                    when (key) {
                        1 -> NavEntry(1) {}
                        2 -> NavEntry(2) {}
                        3 -> NavEntry(3) {}
                        else -> error("Invalid Key")
                    }
                },
            ) { entries ->
                entries.lastOrNull()?.let { it.content.invoke(it.key) }
            }
        }
        composeTestRule.runOnIdle { backStack.removeAt(backStack.lastIndex) }

        composeTestRule.waitForIdle()
        assertThat(poppedEntries).containsExactly(2)

        backStack.add(3)

        composeTestRule.waitForIdle()
        assertThat(backStack).containsExactly(1, 3).inOrder()

        backStack.removeAt(backStack.lastIndex)

        composeTestRule.waitForIdle()
        assertThat(backStack).containsExactly(1)
        assertThat(poppedEntries).containsExactly(2, 3).inOrder()
    }

    @Test
    fun decoratorsOnPopOrder() {
        var count = -1
        var outerPop = -1
        var innerPop = -1
        val innerDecorator =
            createTestNavEntryDecorator<Any>(onPop = { _ -> innerPop = ++count }) { entry ->
                entry.content.invoke(entry.key)
            }
        val outerDecorator =
            createTestNavEntryDecorator<Any>(onPop = { _ -> outerPop = ++count }) { entry ->
                entry.content.invoke(entry.key)
            }
        lateinit var backStack: SnapshotStateList<Int>
        composeTestRule.setContent {
            backStack = mutableStateListOf(1, 2)
            DecoratedNavEntryProvider(
                backStack = backStack,
                entryDecorators = listOf(outerDecorator, innerDecorator),
                entryProvider = { key ->
                    when (key) {
                        1 -> NavEntry(1) {}
                        2 -> NavEntry(2) {}
                        else -> error("Invalid Key")
                    }
                },
            ) { entries ->
                entries.lastOrNull()?.let { it.content.invoke(it.key) }
            }
        }
        composeTestRule.runOnIdle { backStack.removeAt(1) }

        composeTestRule.waitForIdle()
        assertThat(innerPop).isEqualTo(0)
        assertThat(outerPop).isEqualTo(1)
    }

    @Test
    fun decoratorsOnPopForNeverRenderedEntries() {
        val entriesOnPop = mutableListOf<String>()
        val entriesRendered = mutableListOf<String>()

        val decorator =
            createTestNavEntryDecorator<Any>(onPop = { key -> entriesOnPop.add(key as String) }) {
                entry ->
                entry.content.invoke(entry.key)
            }
        lateinit var backStack: SnapshotStateList<String>
        composeTestRule.setContent {
            backStack = mutableStateListOf("first", "second", "third")
            DecoratedNavEntryProvider(
                backStack = backStack,
                entryDecorators = listOf(decorator),
                entryProvider = { key ->
                    when (key) {
                        "first" -> NavEntry("first") { entriesRendered.add(it) }
                        "second" -> NavEntry("second") { entriesRendered.add(it) }
                        "third" -> NavEntry("third") { entriesRendered.add(it) }
                        else -> error("Invalid Key")
                    }
                },
            ) { entries ->
                entries.lastOrNull()?.let { it.content.invoke(it.key) }
            }
        }

        assertThat(entriesRendered).containsExactly("third")
        assertThat(entriesOnPop).isEmpty()

        composeTestRule.runOnIdle {
            backStack.removeAt(2)
            backStack.removeAt(1)
        }

        composeTestRule.waitForIdle()
        assertThat(entriesRendered).containsExactly("third", "first")
        assertThat(entriesOnPop).containsExactly("third", "second").inOrder()
    }

    @Test
    fun onPopCalledForNewlyAddedDecorator() {
        val decoratorPopCallback = mutableListOf<String>()
        val decorator1 =
            createTestNavEntryDecorator<Any>(
                onPop = { key -> decoratorPopCallback.add("decorator1") }
            ) { entry ->
                entry.content.invoke(entry.key)
            }
        val decorator2 =
            createTestNavEntryDecorator<Any>(
                onPop = { key -> decoratorPopCallback.add("decorator2") }
            ) { entry ->
                entry.content.invoke(entry.key)
            }
        val backStack = mutableStateListOf(1, 2)
        val decorators = mutableStateListOf(decorator1)
        composeTestRule.setContent {
            DecoratedNavEntryProvider(
                backStack = backStack,
                entryDecorators = decorators,
                entryProvider = { key ->
                    when (key) {
                        1 -> NavEntry(1) {}
                        2 -> NavEntry(2) {}
                        else -> error("Invalid Key")
                    }
                },
            ) { entries ->
                entries.lastOrNull()?.let { it.content.invoke(it.key) }
            }
        }

        composeTestRule.waitForIdle()

        assertThat(backStack).containsExactly(1, 2)
        assertThat(decoratorPopCallback).isEmpty()
        decorators.add(decorator2)

        composeTestRule.waitForIdle()

        assertThat(decoratorPopCallback).isEmpty()
        backStack.removeAt(backStack.lastIndex)

        composeTestRule.waitForIdle()
        assertThat(backStack).containsExactly(1)
        assertThat(decoratorPopCallback).containsExactly("decorator2", "decorator1").inOrder()
    }

    @Test
    fun onPopCalledForRemovedDecorator() {
        val decoratorPopCallback = mutableListOf<String>()
        val decorator1 =
            createTestNavEntryDecorator<Any>(
                onPop = { key -> decoratorPopCallback.add("decorator1") }
            ) { entry ->
                entry.content.invoke(entry.key)
            }
        val decorator2 =
            createTestNavEntryDecorator<Any>(
                onPop = { key -> decoratorPopCallback.add("decorator2") }
            ) { entry ->
                entry.content.invoke(entry.key)
            }
        val backStack = mutableStateListOf(1, 2)
        val decorators = mutableStateListOf(decorator1, decorator2)
        composeTestRule.setContent {
            DecoratedNavEntryProvider(
                backStack = backStack,
                entryDecorators = decorators,
                entryProvider = { key ->
                    when (key) {
                        1 -> NavEntry(1) {}
                        2 -> NavEntry(2) {}
                        else -> error("Invalid Key")
                    }
                },
            ) { entries ->
                entries.lastOrNull()?.let { it.content.invoke(it.key) }
            }
        }

        composeTestRule.waitForIdle()

        assertThat(backStack).containsExactly(1, 2)
        assertThat(decoratorPopCallback).isEmpty()
        decorators.remove(decorator2)

        composeTestRule.waitForIdle()

        assertThat(decoratorPopCallback).isEmpty()
        backStack.removeAt(backStack.lastIndex)

        composeTestRule.waitForIdle()
        assertThat(backStack).containsExactly(1)
        assertThat(decoratorPopCallback).containsExactly("decorator2", "decorator1").inOrder()
    }

    @Test
    fun entryReWrappedWithNewlyAddedDecorator() {
        var dec1Wrapped = 0
        var dec2Wrapped = 0
        val decorator1 =
            createTestNavEntryDecorator<Any> {
                dec1Wrapped++
                it.content.invoke(it.key)
            }
        val decorator2 =
            createTestNavEntryDecorator<Any> {
                dec2Wrapped++
                it.content.invoke(it.key)
            }
        val decorators = mutableStateListOf(decorator1)
        composeTestRule.setContent {
            DecoratedNavEntryProvider(
                backStack = listOf("something"),
                entryDecorators = decorators,
                entryProvider = { NavEntry("something") {} },
            ) { entries ->
                entries.lastOrNull()?.let { it.content.invoke(it.key) }
            }
        }
        composeTestRule.waitForIdle()
        assertThat(dec1Wrapped).isEqualTo(1)
        assertThat(dec2Wrapped).isEqualTo(0)

        decorators.add(decorator2)
        composeTestRule.waitForIdle()

        assertThat(dec1Wrapped).isEqualTo(2)
        assertThat(dec2Wrapped).isEqualTo(1)
    }

    @Test
    fun entryReWrappedWithRemovedDecorator() {
        var dec1Wrapped = 0
        var dec2Wrapped = 0
        val decorator1 =
            createTestNavEntryDecorator<Any> {
                dec1Wrapped++
                it.content.invoke(it.key)
            }
        val decorator2 =
            createTestNavEntryDecorator<Any> {
                dec2Wrapped++
                it.content.invoke(it.key)
            }
        val decorators = mutableStateListOf(decorator1, decorator2)
        composeTestRule.setContent {
            DecoratedNavEntryProvider(
                backStack = listOf("something"),
                entryDecorators = decorators,
                entryProvider = { NavEntry("something") {} },
            ) { entries ->
                entries.lastOrNull()?.let { it.content.invoke(it.key) }
            }
        }
        composeTestRule.waitForIdle()
        assertThat(dec1Wrapped).isEqualTo(1)
        assertThat(dec2Wrapped).isEqualTo(1)

        decorators.remove(decorator1)
        composeTestRule.waitForIdle()

        assertThat(dec1Wrapped).isEqualTo(1)
        assertThat(dec2Wrapped).isEqualTo(2)
    }

    @Test
    fun decoratorsWrapAddedEntry() {
        val wrappedEntries = mutableListOf<String>()
        val decorator = createTestNavEntryDecorator {
            wrappedEntries.add(it.key)
            it.content.invoke(it.key)
        }
        val backStack = mutableStateListOf("first")
        composeTestRule.setContent {
            DecoratedNavEntryProvider(
                backStack = backStack,
                entryDecorators = listOf(decorator),
                entryProvider = {
                    when (it) {
                        "first" -> NavEntry("first") {}
                        "second" -> NavEntry("second") {}
                        else -> error("Unknown key")
                    }
                },
            ) { entries ->
                entries.lastOrNull()?.let { it.content.invoke(it.key) }
            }
        }

        composeTestRule.waitForIdle()
        assertThat(backStack).containsExactly("first")
        assertThat(wrappedEntries).containsExactly("first")

        backStack.add("second")
        composeTestRule.waitForIdle()

        assertThat(backStack).containsExactly("first", "second")
        assertThat(wrappedEntries).containsExactly("first", "second")
    }
}
