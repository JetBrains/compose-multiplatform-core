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
import androidx.compose.ui.test.findNodeWithLabel
import androidx.compose.ui.test.getAccessibilityTree
import androidx.compose.ui.test.postAccessibilityElementFocused
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertNotNull

class AccessibilityFrameClockTest {
    private val UIKitInstrumentedTest.choreographer: FrameChoreographer
        get() = assertNotNull(frameChoreographer, "frameChoreographer is null")

    private fun UIKitInstrumentedTest.assertChoreographerIsIdle() {
        waitForIdle()
        // The display link pauses itself two frames after the last frame request,
        // so it never pauses while something keeps requesting frames.
        waitUntil("Choreographer should pause while content is idle") {
            choreographer.isDisplayLinkPaused
        }
    }

    @Test
    fun testAccessibilityTreeReadKeepsChoreographerIdle() = runUIKitInstrumentedTest {
        setContent {
            Column {
                Text("Text A")
                Text("Text B")
            }
        }

        getAccessibilityTree()

        assertChoreographerIsIdle()
    }

    @Test
    fun testScrollToFocusedElementRunsOnChoreographerFrames() = runUIKitInstrumentedTest {
        // Real speed, so the running scroll is observed before it ends.
        animationSpeed = UIKitInstrumentedTest.RealAnimationSpeed
        val scrollState = ScrollState(initial = 0)
        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                repeat(50) {
                    Text("Row $it", modifier = Modifier.height(100.dp))
                }
            }
        }

        getAccessibilityTree()
        assertChoreographerIsIdle()

        postAccessibilityElementFocused(findNodeWithLabel("Row 40").element)
        waitUntil("The scroll should run on choreographer frames") {
            scrollState.isScrollInProgress && !choreographer.isDisplayLinkPaused
        }
        waitUntil("The scroll to the focused element does not finish") {
            scrollState.value > 0 && !scrollState.isScrollInProgress
        }

        assertChoreographerIsIdle()
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

        getAccessibilityTree()
        waitForIdle()

        postAccessibilityElementFocused(findNodeWithLabel("Row 40").element)
        waitUntil("The scroll to the focused element does not start") {
            scrollState.isScrollInProgress
        }

        stopComposeScene()

        assertChoreographerIsIdle()
    }
}
