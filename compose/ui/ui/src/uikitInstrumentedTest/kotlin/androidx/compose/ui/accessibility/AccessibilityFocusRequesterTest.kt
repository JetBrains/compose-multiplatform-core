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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.AccessibilityMediator
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.findNodeWithLabel
import androidx.compose.ui.test.getAccessibilityTree
import androidx.compose.ui.test.runUIKitInstrumentedTest
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalNativeApi::class)
class AccessibilityFocusRequesterTest {
    @Test
    fun testFocusRequesterMovesAccessibilityFocus() = runUIKitInstrumentedTest {
        val focusRequesterA = FocusRequester()
        val focusRequesterB = FocusRequester()

        setContent {
            Column {
                Text(
                    "Text A", modifier = Modifier
                        .focusRequester(focusRequesterA)
                        .focusable()
                )
                Text(
                    "Text B", modifier = Modifier
                        .focusRequester(focusRequesterB)
                        .focusable()
                )
            }
        }

        // Trigger accessibility.
        getAccessibilityTree()
        waitForIdle()

        focusRequesterB.requestFocus()
        waitForIdle()

        val buttonBElement = findNodeWithLabel("Text B").element
        assertEquals(
            expected = buttonBElement,
            actual = AccessibilityMediator.lastFocusedElementForTests?.value
        )

        focusRequesterA.requestFocus()
        waitForIdle()

        val buttonAElement = findNodeWithLabel("Text A").element
        assertEquals(
            expected = buttonAElement,
            actual = AccessibilityMediator.lastFocusedElementForTests?.value
        )
    }

    @Test
    fun testFocusRequesterSelectsFirstFocusableElement() = runUIKitInstrumentedTest {
        val focusRequester = FocusRequester()

        setContent {
            Column(
                modifier = Modifier
                    .testTag("ContentBox")
                    .focusRequester(focusRequester)
                    .focusable()
            ) {
                Box {
                    Text("Content 1")
                }
                Text("Content 2")
            }
        }

        // Trigger accessibility.
        getAccessibilityTree()
        waitForIdle()

        focusRequester.requestFocus()
        waitForIdle()

        val content1Element = findNodeWithLabel("Content 1").element
        assertEquals(
            expected = content1Element,
            actual = AccessibilityMediator.lastFocusedElementForTests?.value
        )
    }
}
