/*
 * Copyright 2026 The Android Open Source Project
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

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package androidx.compose.mpp.demo

import androidx.collection.IntObjectMapStats
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestResult

@OptIn(ExperimentalTestApi::class)
class IntObjectMapProfileTest {
    @Test
    fun reportDemoMapPeakSizes(): TestResult {
        val firstInstance = IntObjectMapStats.instanceCount
        return runComposeUiTest {
            lateinit var navController: NavHostController
            val app = App()
            setContent {
                navController = rememberNavController()
                app.Content(navController)
            }
            awaitIdle()

            repeat(3) { iteration ->
                runOnIdle { navController.navigate("Example1") }
                mainClock.advanceTimeBy(600)
                awaitIdle()
                runOnIdle { assertEquals("Example1", navController.currentDestination?.route) }

                onNodeWithText("Press me!").performClick()
                onNodeWithText("I am TextField").performTextReplacement("map-profile-$iteration")
                awaitIdle()

                runOnIdle { assertTrue(navController.popBackStack()) }
                mainClock.advanceTimeBy(600)
                awaitIdle()
                runOnIdle { assertEquals(MainScreen.title, navController.currentDestination?.route) }
            }

            val stats = IntObjectMapStats.snapshot(firstInstance)
            assertTrue(stats.count > 0, "No maps recorded; check the collection dependency substitution")
            assertTrue((stats.max ?: 0) > 0, "No map insertions were recorded")
            println("INT_OBJECT_MAP_STATS demo-main-example1-three-round-trips: $stats")
            println("INT_OBJECT_MAP_PEAK_HISTOGRAM ${stats.peakSizes.groupingBy { it }.eachCount()}")
        }
    }
}
