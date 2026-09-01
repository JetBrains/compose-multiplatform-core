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

package androidx.navigation3.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

// Like a native UINavigationController, the default push must enter from the trailing edge
// (physical right in LTR, physical left in RTL) and the default pop must reveal the previous
// entry from the leading edge.
@OptIn(ExperimentalTestApi::class)
class NavDisplayLayoutDirectionTest {

    @Test
    fun defaultPushEntersFromTrailingEdgeInLtr() {
        val incomingLeft = incomingEdgeOffset(LayoutDirection.Ltr, pop = false)
        assertTrue(
            incomingLeft > 0f,
            "LTR push should enter from the right edge (left bound > 0), was $incomingLeft",
        )
    }

    @Test
    fun defaultPushEntersFromTrailingEdgeInRtl() {
        val incomingLeft = incomingEdgeOffset(LayoutDirection.Rtl, pop = false)
        assertTrue(
            incomingLeft < 0f,
            "RTL push should enter from the left edge (left bound < 0), was $incomingLeft",
        )
    }

    @Test
    fun defaultPopRevealsPreviousEntryFromLeadingEdgeInLtr() {
        val incomingLeft = incomingEdgeOffset(LayoutDirection.Ltr, pop = true)
        assertTrue(
            incomingLeft < 0f,
            "LTR pop should reveal the previous entry from the left (left bound < 0), " +
                "was $incomingLeft",
        )
    }

    @Test
    fun defaultPopRevealsPreviousEntryFromLeadingEdgeInRtl() {
        val incomingLeft = incomingEdgeOffset(LayoutDirection.Rtl, pop = true)
        assertTrue(
            incomingLeft > 0f,
            "RTL pop should reveal the previous entry from the right (left bound > 0), " +
                "was $incomingLeft",
        )
    }

    private fun incomingEdgeOffset(layoutDirection: LayoutDirection, pop: Boolean): Float {
        var extreme = 0f
        runComposeUiTest {
            val backStack =
                if (pop) mutableStateListOf(FIRST, SECOND) else mutableStateListOf(FIRST)
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    NavDisplay(backStack) { key ->
                        NavEntry(key) { Box(Modifier.fillMaxSize().testTag(key.toString())) }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            val incoming = if (pop) FIRST else SECOND
            runOnIdle { if (pop) backStack.removeAt(backStack.lastIndex) else backStack.add(SECOND) }
            repeat(16) {
                mainClock.advanceTimeBy(8)
                extreme = maxByAbs(extreme, incomingLeftBoundOrZero(incoming))
            }
        }
        return extreme
    }

    private fun ComposeUiTest.incomingLeftBoundOrZero(tag: String): Float {
        val nodes = onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (nodes.isEmpty()) return 0f
        return onAllNodesWithTag(tag)[0].getUnclippedBoundsInRoot().left.value
    }

    private fun maxByAbs(a: Float, b: Float): Float = if (abs(b) > abs(a)) b else a

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
    }
}
