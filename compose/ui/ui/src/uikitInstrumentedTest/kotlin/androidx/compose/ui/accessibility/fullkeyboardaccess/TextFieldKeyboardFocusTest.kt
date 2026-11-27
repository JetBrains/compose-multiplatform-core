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

package androidx.compose.ui.accessibility.fullkeyboardaccess

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.FullKeyboardAccessTest
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.focusedElement
import androidx.compose.ui.test.moveKeyboardFocusBackward
import androidx.compose.ui.test.moveKeyboardFocusForward
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.startKeyboardFocusAt
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.UIKit.accessibilityActivate

/**
 * Keyboard focus traversal with Full Keyboard Access, which moves the focus of the UIKit focus
 * system over the accessibility elements of the Compose scene.
 *
 * The focus system of an iPhone is enabled only while Full Keyboard Access is on, so these tests
 * are run by the `fullKeyboardAccessTestSuite` on a simulator with Full Keyboard Access activated
 * (see [FullKeyboardAccessTest] for how it is enabled).
 */
@FullKeyboardAccessTest
class TextFieldKeyboardFocusTest {

    // CMP-10817
    @Test
    fun testTabOrderReachesTextFieldTrailingActions() = runUIKitInstrumentedTest {
        setContent {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Button(onClick = {}, modifier = Modifier.testTag("top")) { Text("Top") }
                OutlinedTextField(
                    state = rememberTextFieldState(),
                    modifier = Modifier.fillMaxWidth().testTag("labelledField"),
                    label = { Text("Label") },
                    trailingIcon = {
                        Button(onClick = {}, modifier = Modifier.testTag("labelledAction")) {
                            Text("A")
                        }
                    },
                )
                OutlinedTextField(
                    state = rememberTextFieldState(),
                    modifier = Modifier.fillMaxWidth().testTag("unlabelledField"),
                    trailingIcon = {
                        Button(onClick = {}, modifier = Modifier.testTag("unlabelledAction")) {
                            Text("B")
                        }
                    },
                )
                val state = rememberTextFieldState()
                BasicTextField(
                    state = state,
                    modifier = Modifier.fillMaxWidth().testTag("basicField"),
                    decorator = OutlinedTextFieldDefaults.decorator(
                        state = state,
                        enabled = true,
                        lineLimits = TextFieldLineLimits.Default,
                        outputTransformation = null,
                        interactionSource = remember { MutableInteractionSource() },
                        trailingIcon = {
                            Button(onClick = {}, modifier = Modifier.testTag("basicAction")) {
                                Text("C")
                            }
                        },
                    ),
                )
                Button(onClick = {}, modifier = Modifier.testTag("bottom")) { Text("Bottom") }
            }
        }
        val focusSystem = startKeyboardFocusAt("top")

        val forward = listOf(
            "labelledField", "labelledAction",
            "unlabelledField", "unlabelledAction",
            "basicField", "basicAction",
            "bottom",
        )
        forward.forEach { expected ->
            moveKeyboardFocusForward(focusSystem)
            assertEquals<Any?>(
                expected = findNodeWithTag(expected).element,
                actual = focusSystem.focusedElement(),
                message = "Tab must visit every text field and its trailing action, and nothing else"
            )
        }
        (forward.reversed().drop(1) + "top").forEach { expected ->
            moveKeyboardFocusBackward(focusSystem)
            assertEquals<Any?>(
                expected = findNodeWithTag(expected).element,
                actual = focusSystem.focusedElement(),
                message = "Shift-Tab must visit the same elements in the reverse order"
            )
        }
    }

    // CMP-10817
    @Test
    fun testTabFromEditedTextFieldMovesToNextElement() = runUIKitInstrumentedTest {
        setContent {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(50.dp).focusable().testTag("first"))
                BasicTextField(
                    state = rememberTextFieldState("Text"),
                    modifier = Modifier.fillMaxWidth().testTag("field"),
                )
                Box(Modifier.size(50.dp).focusable().testTag("last"))
            }
        }
        val focusSystem = startKeyboardFocusAt("first")

        moveKeyboardFocusForward(focusSystem)
        assertEquals<Any?>(
            expected = findNodeWithTag("field").element,
            actual = focusSystem.focusedElement(),
            message = "Tab must reach the text field"
        )

        // Space in Full Keyboard Access activates the focused element: the text field starts editing.
        assertTrue(findNodeWithTag("field").element!!.accessibilityActivate())
        waitForIdle()

        moveKeyboardFocusForward(focusSystem)
        assertEquals<Any?>(
            expected = findNodeWithTag("last").element,
            actual = focusSystem.focusedElement(),
            message = "Tab must leave the edited text field for the next element"
        )
        moveKeyboardFocusBackward(focusSystem)
        assertEquals<Any?>(
            expected = findNodeWithTag("field").element,
            actual = focusSystem.focusedElement(),
            message = "Shift-Tab must come back the same way"
        )
        moveKeyboardFocusBackward(focusSystem)
        assertEquals<Any?>(
            expected = findNodeWithTag("first").element,
            actual = focusSystem.focusedElement(),
            message = "Shift-Tab must come back the same way"
        )
    }
}
