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
import androidx.compose.ui.platform.AccessibilityMediator
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.assertVisibleInContainer
import androidx.compose.ui.test.findNodeWithLabel
import androidx.compose.ui.test.getAccessibilityTree
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIAccessibilityElementFocusedNotification
import platform.UIKit.UIAccessibilityFocusedElementKey
import platform.darwin.NSObject

class AccessibilityDisplayLinkTest {
    private class DisplayLinkRecorder {
        var isRunning = false
            private set
        var startCount = 0
            private set

        fun onRunningChanged(isRunning: Boolean) {
            this.isRunning = isRunning
            if (isRunning) {
                startCount++
            }
        }
    }

    private fun runDisplayLinkTest(
        testBlock: UIKitInstrumentedTest.(DisplayLinkRecorder) -> Unit
    ) = runUIKitInstrumentedTest {
        val recorder = DisplayLinkRecorder()
        AccessibilityMediator.onDisplayLinkRunningChangedForTests = recorder::onRunningChanged
        try {
            testBlock(recorder)
        } finally {
            AccessibilityMediator.onDisplayLinkRunningChangedForTests = null
        }
    }

    private fun postAccessibilityElementFocused(element: NSObject?) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = UIAccessibilityElementFocusedNotification,
            `object` = null,
            userInfo = mapOf(UIAccessibilityFocusedElementKey to element)
        )
    }

    @Test
    fun testAccessibilityTreeReadDoesNotStartDisplayLink() = runDisplayLinkTest { displayLink ->
        setContent {
            Column {
                Text("Text A")
                Text("Text B")
            }
        }

        getAccessibilityTree()
        waitForIdle()

        assertEquals(0, displayLink.startCount)
        assertFalse(displayLink.isRunning)
    }

    @Test
    fun testDisplayLinkRunsOnlyWhileScrollingToFocusedElement() = runDisplayLinkTest { displayLink ->
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
        assertFalse(displayLink.isRunning)

        postAccessibilityElementFocused(findNodeWithLabel("Row 40").element)
        assertTrue(displayLink.isRunning)

        waitUntil("The display link is not invalidated after the scroll") {
            !displayLink.isRunning
        }
        waitForIdle()

        assertEquals(1, displayLink.startCount)
        assertTrue(scrollState.value > 0)
        findNodeWithLabel("Row 40").assertVisibleInContainer()
    }

    @Test
    fun testDisplayLinkIsInvalidatedWhenSceneStopsDuringScroll() = runDisplayLinkTest { displayLink ->
        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(ScrollState(initial = 0))) {
                repeat(50) {
                    Text("Row $it", modifier = Modifier.height(100.dp))
                }
            }
        }

        getAccessibilityTree()
        waitForIdle()

        postAccessibilityElementFocused(findNodeWithLabel("Row 40").element)
        assertTrue(displayLink.isRunning)

        stopComposeScene()

        waitUntil("The display link is not invalidated after the scene is disposed") {
            !displayLink.isRunning
        }
    }
}
