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

package androidx.compose.ui.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.findFocusedUITextInput
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.ComposeUIView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UITextInputProtocol

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
class NestedComposeTextFieldFocusTest {
    @Test
    fun focusMovesToTextFieldInNestedComposeUIView() = runUIKitInstrumentedTest {
        var outerFocused = false
        var nestedFocused = false

        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                UIKitView(
                    factory = {
                        ComposeUIView(
                            configure = { enforceStrictPlistSanityCheck = false }
                        ) {
                            FocusReportingTextField(
                                value = NestedFieldText,
                                onFocusChanged = { nestedFocused = it }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .testTag(NestedFieldHostTag),
                    properties = UIKitInteropProperties(placedAsOverlay = true)
                )

                FocusReportingTextField(
                    modifier = Modifier.testTag(OuterFieldTag),
                    value = OuterFieldText,
                    onFocusChanged = { outerFocused = it }
                )
            }
        }

        findNodeWithTag(OuterFieldTag).tap()
        waitUntil("Outer text field should be focused after tap") {
            outerFocused && !nestedFocused
        }
        assertEquals(OuterFieldText, findFocusedUITextInput()?.text)

        findNodeWithTag(NestedFieldHostTag).tap()
        waitUntil("Nested text field should take focus and release the outer text field") {
            nestedFocused && !outerFocused
        }
        assertEquals(NestedFieldText, findFocusedUITextInput()?.text)

        assertTrue(nestedFocused)
        assertFalse(outerFocused)
    }

    @Composable
    private fun FocusReportingTextField(
        modifier: Modifier = Modifier,
        value: String,
        onFocusChanged: (Boolean) -> Unit
    ) {
        BasicTextField(
            value = value,
            onValueChange = {},
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(8.dp)
                .border(1.dp, Color.Black)
                .padding(8.dp)
                .onFocusChanged { onFocusChanged(it.isFocused) }
        )
    }

    private val UITextInputProtocol.text: String?
        get() {
            val range = textRangeFromPosition(beginningOfDocument, endOfDocument) ?: return null
            return textInRange(range)
        }

    private companion object {
        const val OuterFieldTag = "OuterField"
        const val NestedFieldHostTag = "NestedFieldHost"
        const val OuterFieldText = "outer field"
        const val NestedFieldText = "nested field"
    }
}
