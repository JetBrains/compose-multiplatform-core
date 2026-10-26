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

package androidx.compose.ui.accessibility

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.FrameChoreographer
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.assertVisibleInContainer
import androidx.compose.ui.test.findNodeWithLabel
import androidx.compose.ui.test.getAccessibilityTree
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIAccessibilityElementFocusedNotification
import platform.UIKit.UIAccessibilityFocusedElementKey
import platform.darwin.NSObject

class AccessibilityFrameClockTest {
    private fun UIKitInstrumentedTest.withChoreographerTicks(block: (TickCounter) -> Unit) {
        val choreographer = frameChoreographer
        assertNotNull(choreographer, "frameChoreographer is null")

        val ticks = TickCounter()
        choreographer.addListener(ticks)
        try {
            block(ticks)
        } finally {
            choreographer.removeListener(ticks)
        }
    }

    private fun postAccessibilityElementFocused(element: NSObject?) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = UIAccessibilityElementFocusedNotification,
            `object` = null,
            userInfo = mapOf(UIAccessibilityFocusedElementKey to element)
        )
    }

    private fun UIKitInstrumentedTest.assertChoreographerIsIdle(ticks: TickCounter) {
        waitForIdle()
        delay(100)

        val ticksBefore = ticks.count
        delay(500)
        assertEquals(
            ticksBefore,
            ticks.count,
            "Choreographer should not tick while content is idle"
        )
    }

    @Test
    fun testAccessibilityTreeReadKeepsChoreographerIdle() = runUIKitInstrumentedTest {
        setContent {
            Column {
                Text("Text A")
                Text("Text B")
            }
        }

        withChoreographerTicks { ticks ->
            getAccessibilityTree()

            assertChoreographerIsIdle(ticks)
        }
    }

    @Test
    fun testScrollToFocusedElementRunsOnChoreographerFrames() = runUIKitInstrumentedTest {
        val scrollState = ScrollState(initial = 0)
        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                repeat(50) {
                    Text("Row $it", modifier = Modifier.height(100.dp))
                }
            }
        }

        withChoreographerTicks { ticks ->
            getAccessibilityTree()
            assertChoreographerIsIdle(ticks)

            val ticksBeforeScroll = ticks.count
            postAccessibilityElementFocused(findNodeWithLabel("Row 40").element)
            waitUntil("The scroll to the focused element does not finish") {
                scrollState.value > 0 && !scrollState.isScrollInProgress
            }

            assertTrue(
                ticks.count > ticksBeforeScroll,
                "The scroll should run on choreographer frames"
            )
            findNodeWithLabel("Row 40").assertVisibleInContainer()
            assertChoreographerIsIdle(ticks)
        }
    }

    @Test
    fun testSceneStopDuringScrollLeavesChoreographerIdle() = runUIKitInstrumentedTest {
        // Real speed, so the scroll is still running when the scene stops.
        animationSpeed = UIKitInstrumentedTest.RealAnimationSpeed
        val scrollState = ScrollState(initial = 0)
        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                repeat(50) {
                    Text("Row $it", modifier = Modifier.height(100.dp))
                }
            }
        }

        withChoreographerTicks { ticks ->
            getAccessibilityTree()
            waitForIdle()

            postAccessibilityElementFocused(findNodeWithLabel("Row 40").element)
            waitUntil("The scroll to the focused element does not start") {
                scrollState.isScrollInProgress
            }

            stopComposeScene()

            assertChoreographerIsIdle(ticks)
        }
    }
}

private class TickCounter : FrameChoreographer.Listener {
    var count = 0
        private set

    override fun onDisplayLinkTick() {
        count++
    }
}
