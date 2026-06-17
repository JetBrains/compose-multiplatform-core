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
import androidx.compose.ui.test.findNodeWithLabelOrNull
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.tapContextMenuButton
import androidx.compose.ui.test.waitForContextMenu
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

            waitUntil("Pasteboard should contain copied word") {
                UIPasteboard.generalPasteboard().string == copiedWord
            }

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

            waitUntil("Text field should remove cut word and copy it to pasteboard") {
                content.text() == "left  $TargetWord" &&
                    UIPasteboard.generalPasteboard().string == cutWord
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
    fun testTextFieldContextMenu_PasteOverSelectionPreservesMixedLatinJapaneseText() =
        runClipboardMenuTest { textFieldKind ->
            val mixedText = "Tokyo\u6771\u4EAC"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = "Hello $TargetWord",
            )
            UIPasteboard.generalPasteboard().string = mixedText

            openToolbarForWord(xFraction = SecondWordPosition)
            tapContextMenuButton("Paste")

            waitUntil("Text field should paste exact mixed Latin/Japanese text") {
                content.text() == "Hello $mixedText"
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
    fun testTextFieldContextMenu_SelectAllSelectsMixedLatinJapaneseText() =
        runClipboardMenuTest { textFieldKind ->
            val mixedText = "Tokyo\u6771\u4EAC"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = mixedText,
            )
            UIPasteboard.generalPasteboard().string = ClipboardSentinel

            openToolbarForWord(xFraction = FirstWordPosition)
            tapContextMenuButton("Select All")

            waitUntil("Text field should select the whole mixed Latin/Japanese text") {
                content.selection() == TextRange(0, mixedText.length)
            }
            assertEquals(ClipboardSentinel, UIPasteboard.generalPasteboard().string)
        }

    @Test
    fun testTextFieldContextMenu_SelectAllThenCopyCopiesWholeText() =
        runClipboardMenuTest { textFieldKind ->
            val text = "Hello $TargetWord"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = text,
            )
            UIPasteboard.generalPasteboard().string = null

            openToolbarForWord(xFraction = FirstWordPosition)
            tapContextMenuButton("Select All")

            waitUntil("Text field should select all text") {
                content.selection() == TextRange(0, text.length)
            }

            tapContextMenuButton("Copy")

            waitUntil("Pasteboard should contain all selected text") {
                UIPasteboard.generalPasteboard().string == text
            }
            assertEquals(text, content.text())
        }

    @Test
    fun testTextFieldContextMenu_SelectAllThenCutRemovesWholeText() =
        runClipboardMenuTest { textFieldKind ->
            val text = "Hello $TargetWord"
            val content = setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = text,
            )
            UIPasteboard.generalPasteboard().string = null

            openToolbarForWord(xFraction = FirstWordPosition)
            tapContextMenuButton("Select All")

            waitUntil("Text field should select all text") {
                content.selection() == TextRange(0, text.length)
            }

            tapContextMenuButton("Cut")

            waitUntil("Text field should remove all selected text and copy it to pasteboard") {
                content.text().isEmpty() &&
                    UIPasteboard.generalPasteboard().string == text
            }
        }

    @Test
    fun testTextFieldContextMenu_PasteIsNotShownWhenClipboardIsEmpty() =
        runClipboardMenuTest { textFieldKind ->
            UIPasteboard.generalPasteboard().string = null
            setTextFieldContent(
                textFieldKind = textFieldKind,
                initialText = "Hello $TargetWord",
            )

            openToolbarForWord(xFraction = SecondWordPosition)

            assertNull(findNodeWithLabelOrNull("Paste"))
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

            waitUntil("Pasteboard should contain copied Arabic word") {
                UIPasteboard.generalPasteboard().string == copiedWord
            }

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
    ): TextFieldContent {
        var text = { initialText }
        var selection = { TextRange.Zero }
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                when (textFieldKind) {
                    EditableTextFieldKind.BasicTextField -> {
                        val textFieldValue = remember {
                            mutableStateOf(TextFieldValue(initialText))
                        }
                        text = { textFieldValue.value.text }
                        selection = { textFieldValue.value.selection }
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
                        selection = { textFieldState.selection }
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
        return TextFieldContent(
            text = text,
            selection = selection,
        )
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
        val selection: () -> TextRange,
    )

    private enum class EditableTextFieldKind {
        BasicTextField,
        BasicTextField2,
    }

    private companion object {
        private const val TextFieldTag = "TextField"
        private const val ClipboardSentinel = "Clipboard sentinel"
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
