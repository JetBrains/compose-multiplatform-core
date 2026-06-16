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

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.assertVisibleInContainer
import androidx.compose.ui.test.findNodeWithLabel
import androidx.compose.ui.test.findNodeWithLabelOrNull
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.tapContextMenuButton
import androidx.compose.ui.test.waitForContextMenu
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.UIKit.UIPasteboard

class TextFieldEditMenuClipboardTest {

    @Test
    fun testTextFieldContextMenu_CopyThenPasteReplacesWord() =
        runClipboardMenuTest { textFieldKind ->
            UIPasteboard.generalPasteboard().string = "Clipboard sentinel"
            val text = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = "copyable target",
            )

            openToolbarForWord(tag = TextFieldTag, xFraction = 0.2f)
            findNodeWithLabel("Copy").assertVisibleInContainer()
            tapContextMenuButton("Copy")

            waitUntil("Pasteboard should contain copied text") {
                UIPasteboard.generalPasteboard().string == "copyable"
            }

            waitUntil("Copy menu should close before opening Paste menu") {
                findNodeWithLabelOrNull("Copy") == null
            }

            openToolbarForWord(tag = TextFieldTag, xFraction = 0.8f)
            findNodeWithLabel("Paste").assertVisibleInContainer()
            tapContextMenuButton("Paste")

            waitUntil("Text field should replace selected text with pasteboard text") {
                text() == "copyable copyable"
            }
            assertEquals("copyable", UIPasteboard.generalPasteboard().string)
        }

    @OptIn(ExperimentalFoundationApi::class)
    private fun runClipboardMenuTest(
        testBlock: UIKitInstrumentedTest.(EditableTextFieldKind) -> Unit
    ) {
        for (newContextMenuEnabled in listOf(false, true)) {
            for (textFieldKind in EditableTextFieldKind.entries) {
                runUIKitInstrumentedTest {
                    val previousValue = ComposeFoundationFlags.isNewContextMenuEnabled
                    ComposeFoundationFlags.isNewContextMenuEnabled = newContextMenuEnabled
                    try {
                        testBlock(textFieldKind)
                    } finally {
                        ComposeFoundationFlags.isNewContextMenuEnabled = previousValue
                    }
                }
            }
        }
    }

    private fun UIKitInstrumentedTest.setTextFieldContent(
        textFieldKind: EditableTextFieldKind,
        initialText: String,
    ): () -> String {
        var text = { initialText }
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                when (textFieldKind) {
                    EditableTextFieldKind.BasicTextField -> {
                        val textFieldValue = remember {
                            mutableStateOf(TextFieldValue(initialText))
                        }
                        text = { textFieldValue.value.text }
                        BasicTextField(
                            value = textFieldValue.value,
                            onValueChange = { textFieldValue.value = it },
                            modifier = textFieldModifier(focusRequester),
                        )
                    }

                    EditableTextFieldKind.BasicTextField2 -> {
                        val textFieldState = remember {
                            TextFieldState(initialText)
                        }
                        text = { textFieldState.text.toString() }
                        BasicTextField(
                            state = textFieldState,
                            modifier = textFieldModifier(focusRequester),
                        )
                    }
                }
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }
        return text
    }

    private fun textFieldModifier(focusRequester: FocusRequester): Modifier =
        Modifier
            .testTag(TextFieldTag)
            .focusRequester(focusRequester)

    private fun UIKitInstrumentedTest.openToolbarForWord(tag: String, xFraction: Float) {
        findNodeWithTag(tag).tap()
        delay(DoubleTapPreparationDelayMillis)
        val tapPoint = pointInNode(tag, xFraction = xFraction, yFraction = 0.5f)
        tap(tapPoint)
        delay(ManualDoubleTapIntervalDelayMillis)
        tap(tapPoint)
        waitForContextMenu()
    }

    private fun UIKitInstrumentedTest.pointInNode(
        tag: String,
        xFraction: Float,
        yFraction: Float,
    ): DpOffset {
        val frame = findNodeWithTag(tag).frame!!
        return DpOffset(
            x = frame.left + (frame.right - frame.left) * xFraction,
            y = frame.top + (frame.bottom - frame.top) * yFraction,
        )
    }

    private enum class EditableTextFieldKind {
        BasicTextField,
        BasicTextField2,
    }

    private companion object {
        private const val TextFieldTag = "TextField"
        private const val DoubleTapPreparationDelayMillis = 500L
        private const val ManualDoubleTapIntervalDelayMillis = 50L
    }
}
