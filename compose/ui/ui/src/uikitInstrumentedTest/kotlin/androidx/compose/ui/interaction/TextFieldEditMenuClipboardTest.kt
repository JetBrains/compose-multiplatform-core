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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
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
    fun testTextFieldContextMenu_CopyThenPasteReplacesSelectedWord() =
        runClipboardMenuTest { textFieldKind ->
            val copiedWord = "copyable"
            val initialText = "$copiedWord $TargetWord"
            val expectedText = "$copiedWord $copiedWord"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = initialText,
            )
            UIPasteboard.generalPasteboard().string = null

            openToolbarForWord(xFraction = FirstWordPosition)
            tapContextMenuButton("Copy")

            waitUntilContextMenuClosed("Copy")

            openToolbarForWord(xFraction = SecondWordPosition)
            tapContextMenuButton("Paste")

            waitUntil("Text field should replace target word with copied word") {
                content.text() == expectedText
            }
            assertEquals(copiedWord, UIPasteboard.generalPasteboard().string)
        }

    @Test
    fun testTextFieldContextMenu_CutThenPasteReplacesSelectedWord() =
        runClipboardMenuTest { textFieldKind ->
            val cutWord = "cut"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = "left $cutWord $TargetWord",
            )
            UIPasteboard.generalPasteboard().string = null

            openToolbarForWord(xFraction = MiddleWordPosition)
            tapContextMenuButton("Cut")

            waitUntil("Text field should remove cut word") {
                content.text() == "left  $TargetWord"
            }

            waitUntilContextMenuClosed("Cut")

            openToolbarForWord(xFraction = LastWordPosition)
            tapContextMenuButton("Paste")

            waitUntil("Text field should paste cut word over selected word") {
                content.text() == "left  $cutWord"
            }
            assertEquals(cutWord, UIPasteboard.generalPasteboard().string)
        }

    @Test
    fun testTextFieldContextMenu_PasteOverSelectionReplacesSelectedText() =
        runClipboardMenuTest { textFieldKind ->
            val pastedWord = "Kotlin"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = "Hello $TargetWord",
            )
            UIPasteboard.generalPasteboard().string = pastedWord

            openToolbarForWord(xFraction = SecondWordPosition)
            tapContextMenuButton("Paste")

            waitUntil("Text field should replace selected word with pasteboard text") {
                content.text() == "Hello $pastedWord"
            }
            assertEquals(pastedWord, UIPasteboard.generalPasteboard().string)
        }

    @Test
    fun testTextFieldContextMenu_CopyPasteMixedLatinJapaneseText() =
        runClipboardMenuTest { textFieldKind ->
            val mixedText = "Tokyo\u6771\u4EAC"
            val initialText = "$mixedText $TargetWord"
            val expectedText = "$mixedText $mixedText"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = initialText,
            )
            UIPasteboard.generalPasteboard().string = null

            openToolbarForWord(xFraction = FirstWordPosition)
            tapContextMenuButton("Copy")

            waitUntilContextMenuClosed("Copy")

            openToolbarForWord(xFraction = SecondWordPosition)
            tapContextMenuButton("Paste")

            waitUntil("Text field should replace target word with mixed Latin/Japanese text") {
                content.text() == expectedText
            }
            assertEquals(mixedText, UIPasteboard.generalPasteboard().string)
        }

    @Test
    fun testTextFieldContextMenu_PasteAtCollapsedCursorInsertsText() =
        runClipboardMenuTest { textFieldKind ->
            val pastedWord = "Kotlin"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = "Hello ",
            )
            UIPasteboard.generalPasteboard().string = pastedWord

            openToolbarForCollapsedCursor(xFraction = TextEndPosition)
            tapContextMenuButton("Paste")

            waitUntil("Text field should insert pasteboard text at cursor") {
                content.text() == "Hello $pastedWord"
            }
            assertEquals(pastedWord, UIPasteboard.generalPasteboard().string)
        }

    @Test
    fun testTextFieldContextMenu_CopyPasteArabicRtlWord() =
        runClipboardMenuTest { textFieldKind ->
            val copiedWord = "\u0645\u0631\u062D\u0628\u0627"
            val initialText = "start $copiedWord $TargetWord"
            val expectedText = "start $copiedWord $copiedWord"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = initialText,
            )
            UIPasteboard.generalPasteboard().string = null

            openToolbarForWord(xFraction = MiddleWordPosition)
            tapContextMenuButton("Copy")

            waitUntilContextMenuClosed("Copy")

            openToolbarForWord(xFraction = LastWordPosition)
            tapContextMenuButton("Paste")

            waitUntil("Text field should replace target word with copied Arabic word") {
                content.text() == expectedText
            }
            assertEquals(copiedWord, UIPasteboard.generalPasteboard().string)
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
    ): TextFieldContent =
        when (textFieldKind) {
            EditableTextFieldKind.BasicTextField -> setBasicTextFieldContent(initialText)
            EditableTextFieldKind.BasicTextField2 -> setBasicTextField2Content(initialText)
        }

    private fun UIKitInstrumentedTest.setBasicTextFieldContent(
        initialText: String,
    ): TextFieldContent {
        val textFieldValue = mutableStateOf(TextFieldValue(initialText))
        setTextFieldContent { focusRequester ->
            BasicTextField(
                value = textFieldValue.value,
                onValueChange = { textFieldValue.value = it },
                modifier = textFieldModifier(focusRequester),
            )
        }
        return TextFieldContent(
            text = { textFieldValue.value.text },
        )
    }

    private fun UIKitInstrumentedTest.setBasicTextField2Content(
        initialText: String,
    ): TextFieldContent {
        val textFieldState = TextFieldState(initialText)
        setTextFieldContent { focusRequester ->
            BasicTextField(
                state = textFieldState,
                modifier = textFieldModifier(focusRequester),
            )
        }
        return TextFieldContent(
            text = { textFieldState.text.toString() },
        )
    }

    private fun UIKitInstrumentedTest.setTextFieldContent(
        content: @Composable (FocusRequester) -> Unit,
    ) {
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                content(focusRequester)
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }
    }

    private fun textFieldModifier(focusRequester: FocusRequester): Modifier =
        Modifier
            .testTag(TextFieldTag)
            .focusRequester(focusRequester)

    private fun UIKitInstrumentedTest.openToolbarForWord(xFraction: Float) {
        findNodeWithTag(TextFieldTag).tap()
        delay(DoubleTapPreparationDelayMillis)
        doubleTapTextField(xFraction = xFraction)
        waitForContextMenu()
    }

    private fun UIKitInstrumentedTest.openToolbarForCollapsedCursor(xFraction: Float) {
        val tapPoint = pointInNode(TextFieldTag, xFraction = xFraction, yFraction = 0.5f)
        tap(tapPoint)
        delay(LongPressPreparationDelayMillis)
        tap(tapPoint)
        waitForContextMenu()
    }

    private fun UIKitInstrumentedTest.doubleTapTextField(xFraction: Float) {
        val tapPoint = pointInNode(TextFieldTag, xFraction = xFraction, yFraction = 0.5f)
        tap(tapPoint)
        delay(ManualDoubleTapIntervalDelayMillis)
        tap(tapPoint)
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

    private fun UIKitInstrumentedTest.waitUntilContextMenuClosed(label: String) {
        waitUntil("$label menu should close before opening another menu") {
            findNodeWithLabelOrNull(label) == null
        }
        delay(ContextMenuDismissAnimationDelayMillis)
    }

    private data class TextFieldContent(
        val text: () -> String,
    )

    private enum class EditableTextFieldKind {
        BasicTextField,
        BasicTextField2,
    }

    private companion object {
        private const val TextFieldTag = "TextField"
        private const val TargetWord = "target"

        private const val FirstWordPosition = 0.2f
        private const val SecondWordPosition = 0.8f
        private const val MiddleWordPosition = 0.43f
        private const val LastWordPosition = 0.75f
        private const val TextEndPosition = 0.95f

        private const val DoubleTapPreparationDelayMillis = 500L
        private const val ManualDoubleTapIntervalDelayMillis = 50L
        private const val LongPressPreparationDelayMillis = 500L
        private const val ContextMenuDismissAnimationDelayMillis = 150L
    }
}
