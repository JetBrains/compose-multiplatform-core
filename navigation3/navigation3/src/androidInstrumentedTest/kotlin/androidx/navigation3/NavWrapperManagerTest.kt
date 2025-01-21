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

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.kruth.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class NavWrapperManagerTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun callWrapperFunctions() {
        var calledWrapBackStack = false
        var calledWrapContent = false
        val wrapper =
            object : NavContentWrapper {
                @Composable
                override fun WrapBackStack(backStack: List<Any>) {
                    calledWrapBackStack = true
                }

                @Composable
                override fun <T : Any> WrapContent(record: NavRecord<T>) {
                    calledWrapContent = true
                }
            }

        val manager = NavWrapperManager(listOf(wrapper))

        composeTestRule.setContent {
            manager.PrepareBackStack(listOf("something"))
            manager.ContentForRecord(NavRecord("myKey") {})
        }

        assertThat(calledWrapBackStack).isTrue()
        assertThat(calledWrapContent).isTrue()
    }

    @Test
    fun callWrapperFunctionsOnce() {
        var calledWrapBackStackCount = 0
        var calledWrapContentCount = 0
        val wrapper =
            object : NavContentWrapper {
                @Composable
                override fun WrapBackStack(backStack: List<Any>) {
                    calledWrapBackStackCount++
                }

                @Composable
                override fun <T : Any> WrapContent(record: NavRecord<T>) {
                    calledWrapContentCount++
                }
            }

        val manager = NavWrapperManager(listOf(wrapper, wrapper))

        composeTestRule.setContent {
            manager.PrepareBackStack(listOf("something"))
            manager.ContentForRecord(NavRecord("myKey") {})
        }

        assertThat(calledWrapBackStackCount).isEqualTo(1)
        assertThat(calledWrapContentCount).isEqualTo(1)
    }
}
