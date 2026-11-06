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
import androidx.compose.ui.test.assertVisibleInContainer
import androidx.compose.ui.test.findNodeWithLabel
import androidx.compose.ui.test.postAccessibilityElementFocused
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

class AccessibilityScrollTest {
    @Test
    fun testFocusedElementIsScrolledIntoView() = runUIKitInstrumentedTest {
        val scrollState = ScrollState(initial = 0)
        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                repeat(50) {
                    Text("Row $it", modifier = Modifier.height(100.dp))
                }
            }
        }

        postAccessibilityElementFocused(findNodeWithLabel("Row 40").element)
        waitUntil("The focused element should be scrolled into view") {
            scrollState.value > 0 && !scrollState.isScrollInProgress
        }

        findNodeWithLabel("Row 40").assertVisibleInContainer()
    }
}
