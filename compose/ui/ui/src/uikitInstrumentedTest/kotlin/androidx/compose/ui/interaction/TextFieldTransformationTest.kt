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

@file:Suppress("DEPRECATION")

package androidx.compose.ui.interaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.AccessibilityTestNode
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findFocusedUITextInput
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.focusedUITextInput
import androidx.compose.ui.test.utils.BasicTextFieldType
import androidx.compose.ui.test.utils.TestTextField
import androidx.compose.ui.test.utils.TestTextFieldState
import androidx.compose.ui.test.utils.runTextFieldTest
import androidx.compose.ui.test.utils.selectionHandles
import androidx.compose.ui.test.utils.selectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.Foundation.NSMakeRange
import platform.UIKit.UITextInputProtocol
import platform.UIKit.UITextRange
import platform.UIKit.UITextSelectionRect
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
class TextFieldTransformationTest {

    @Test
    fun testTapInTheMiddle() = runTextFieldTransformationTest(
        usingNativeTextInput = true,
    ) { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "123456789012", textFieldType = textFieldType)
        setTransformedTextFieldContent(state, nativeTextInput)

        findNodeWithTag(TEXT_FIELD_TAG).tapCharacter(offset = 8)
        waitForIdle()

        assertTextInputState(state, TextRange(12))
    }

    @Test
    fun testTypingFilteredCharacter() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(
            text = "12345678",
            selection = TextRange(8),
            textFieldType = textFieldType,
            filter = { text -> text.all { it.isDigit() } }
        )
        setTransformedTextFieldContent(state, nativeTextInput)

        typeWithKeyboard("a")

        assertEquals("12345678", state.text)
        assertTextInputState(state, TextRange(8))

        typeWithKeyboard("9")

        assertEquals("123456789", state.text)
        assertTextInputState(state, TextRange(9))
    }

    @Test
    fun testInsertTextAfterSeparator() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "12345678", textFieldType = textFieldType)
        setTransformedTextFieldContent(state, nativeTextInput)

        setSelectedTextRange(TextRange(4))
        focusedUITextInput().insertText("0")
        waitForIdle()

        assertEquals("123405678", state.text)
        assertTextInputState(state, TextRange(5))
    }

    @Test
    fun testReplaceSelectionAcrossSeparator() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "123456789", textFieldType = textFieldType)
        setTransformedTextFieldContent(state, nativeTextInput)

        setSelectedTextRange(TextRange(3, 6))
        focusedUITextInput().insertText("0")
        waitForIdle()

        assertEquals("1230789", state.text)
        assertTextInputState(state, TextRange(4))
    }

    @Test
    fun testReplaceRange() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(
            text = "123456789",
            selection = TextRange(9),
            textFieldType = textFieldType
        )
        setTransformedTextFieldContent(state, nativeTextInput)

        val textInput = focusedUITextInput()
        textInput.replaceRange(textInput.textRange(TextRange(4, 6)), withText = "00")
        waitForIdle()

        assertEquals("123400789", state.text)
        assertTextInputState(state, state.selection)
    }

    @Test
    fun testMarkedText() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(
            text = "1234",
            selection = TextRange(4),
            textFieldType = textFieldType
        )
        setTransformedTextFieldContent(state, nativeTextInput)

        val textInput = focusedUITextInput()
        textInput.setMarkedText("567", selectedRange = NSMakeRange(3u, 0u))
        waitForIdle()

        assertEquals("1234567", state.text)
        assertEquals(TextRange(4, 7), textInput.markedRange)
        assertTextInputState(state, TextRange(7))

        textInput.unmarkText()
        waitForIdle()

        assertEquals(null, textInput.markedRange)
        assertTextInputState(state, TextRange(7))
    }

    @Test
    fun testPrefix() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "12", selection = TextRange(2), textFieldType = textFieldType)
        setTransformedTextFieldContent(
            state = state,
            nativeTextInput = nativeTextInput,
            outputTransformation = PrefixOutputTransformation,
            visualTransformation = PrefixVisualTransformation,
        )

        setSelectedTextRange(TextRange(0))
        typeWithKeyboard("0")

        assertEquals("012", state.text)
        assertTextInputState(state, TextRange(1))
    }

    @Test
    fun testCaretGeometry() = runTextFieldTransformationTest(
        // The geometry is provided to iOS by the native text input view only.
        usingNativeTextInput = true,
    ) { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "123456789", textFieldType = textFieldType)
        setTransformedTextFieldContent(state, nativeTextInput)

        val textInput = focusedUITextInput()
        var previousCaretX = Double.NEGATIVE_INFINITY
        for (offset in 0..9) {
            val position = textInput.positionFromPosition(textInput.beginningOfDocument, offset.toLong())!!
            val (x, y) = textInput.caretRectForPosition(position).useContents {
                (origin.x + size.width / 2) to (origin.y + size.height / 2)
            }
            assertTrue(x > previousCaretX, "Caret at $offset must be to the right of the previous one")
            previousCaretX = x

            val closestPosition = textInput.closestPositionToPoint(CGPointMake(x, y))!!
            assertEquals(
                offset,
                textInput.offsetFromPosition(textInput.beginningOfDocument, closestPosition).toInt(),
                "Closest position to the caret at $offset"
            )
        }

        // The point to the right of the text corresponds to the end of the document.
        val (x, y) = textInput.caretRectForPosition(textInput.endOfDocument).useContents {
            (origin.x + 100) to (origin.y + size.height / 2)
        }
        val closestPosition = textInput.closestPositionToPoint(CGPointMake(x, y))!!
        assertEquals(9, textInput.offsetFromPosition(textInput.beginningOfDocument, closestPosition).toInt())
    }

    @Test
    fun testSelectionEndingAtSeparator() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "123456789", textFieldType = textFieldType)
        setTransformedTextFieldContent(state, nativeTextInput)
        showComposeSelectionHandles(nativeTextInput)

        // "1234| 5678 9": the separator after the selection isn't highlighted.
        setSelectedTextRange(TextRange(0, 4))

        assertTextInputState(state, TextRange(0, 4))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(0, 4))

        // "1234 |5678| 9"
        state.updateSelection(TextRange(5, 8))
        waitForIdle()

        assertTextInputState(state, TextRange(5, 8))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(6, 9))
    }

    @Test
    fun testSelectionStartingAtSeparator() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "123456789", textFieldType = textFieldType)
        setTransformedTextFieldContent(state, nativeTextInput)
        showComposeSelectionHandles(nativeTextInput)

        // "1234| 567|8 9": the separator before the selection is highlighted.
        setSelectedTextRange(TextRange(4, 7))

        assertTextInputState(state, TextRange(4, 7))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(4, 8))

        // "1234| 5678| 9": only the leading separator is highlighted.
        state.updateSelection(TextRange(4, 8))
        waitForIdle()

        assertTextInputState(state, TextRange(4, 8))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(4, 9))
    }

    @Test
    fun testProgrammaticSelection() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "123456789", textFieldType = textFieldType)
        setTransformedTextFieldContent(state, nativeTextInput)
        showComposeSelectionHandles(nativeTextInput)

        state.updateSelection(TextRange(6))
        waitForIdle()

        assertTextInputState(state, TextRange(6))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(7))

        // "1|234 56|78 9"
        state.updateSelection(TextRange(1, 6))
        waitForIdle()

        assertTextInputState(state, TextRange(1, 6))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(1, 7))

        state.updateSelection(TextRange(9))
        waitForIdle()

        assertTextInputState(state, TextRange(9))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(11))
    }

    @Test
    fun testShorterTextTyping() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(
            text = "12-34",
            selection = TextRange(3),
            textFieldType = textFieldType
        )
        setShorterTextFieldContent(state, nativeTextInput)

        typeWithKeyboard("0")

        assertEquals("12-034", state.text)
        assertTextInputState(state, TextRange(4))
    }

    @Test
    fun testShorterTextSelectionFromTextInput() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "12-34-56", textFieldType = textFieldType)
        setShorterTextFieldContent(state, nativeTextInput)
        showComposeSelectionHandles(nativeTextInput)

        // "12|34|56" -> "12|-34-|56": the hidden dashes are a part of the selection.
        setSelectedTextRange(TextRange(2, 6))

        assertTextInputState(state, TextRange(2, 6))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(2, 4))
    }

    @Test
    fun testShorterTextProgrammaticSelection() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(text = "12-34-56", textFieldType = textFieldType)
        setShorterTextFieldContent(state, nativeTextInput)
        showComposeSelectionHandles(nativeTextInput)

        state.updateSelection(TextRange(4))
        waitForIdle()

        assertTextInputState(state, TextRange(4))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(3))

        state.updateSelection(TextRange(1, 7))
        waitForIdle()

        assertTextInputState(state, TextRange(1, 7))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(1, 5))

        // Only a hidden dash is selected: there is nothing to highlight.
        state.updateSelection(TextRange(2, 3))
        waitForIdle()

        assertTextInputState(state, TextRange(2, 3))
        assertSelectionHighlight(state, nativeTextInput, transformedRange = TextRange(2))
    }

    @Test
    fun testShorterTextDeleteBackward() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(
            text = "12-34",
            selection = TextRange(3),
            textFieldType = textFieldType
        )
        setShorterTextFieldContent(state, nativeTextInput)

        // Deletes the hidden dash.
        focusedUITextInput().deleteBackward()
        waitForIdle()

        assertEquals("1234", state.text)
        assertTextInputState(state, TextRange(2))
    }

    @Test
    fun testShorterTextReplaceSelection() = runTextFieldTransformationTest { nativeTextInput, textFieldType ->
        val state = TestTextFieldState(
            text = "12-34-56",
            selection = TextRange(1, 4),
            textFieldType = textFieldType
        )
        setShorterTextFieldContent(state, nativeTextInput)

        focusedUITextInput().insertText("0")
        waitForIdle()

        assertEquals("104-56", state.text)
        assertTextInputState(state, TextRange(2))
    }

    @Test
    fun testShorterTextCaretGeometry() = runTextFieldTransformationTest(
        // The geometry is provided to iOS by the native text input view only.
        usingNativeTextInput = true,
    ) { nativeTextInput, textFieldType ->
        val text = "12-34-56"
        val state = TestTextFieldState(text = text, textFieldType = textFieldType)
        setShorterTextFieldContent(state, nativeTextInput)

        val textInput = focusedUITextInput()
        var previousCaretX = Double.NEGATIVE_INFINITY
        for (offset in 0..text.length) {
            val position = textInput.positionFromPosition(textInput.beginningOfDocument, offset.toLong())!!
            val x = textInput.caretRectForPosition(position).useContents { origin.x + size.width / 2 }
            if (offset > 0 && text[offset - 1] == DASH) {
                // The dash isn't presented, so the caret doesn't move.
                assertEquals(previousCaretX, x, absoluteTolerance = 0.5, "Caret at $offset")
            } else {
                assertTrue(x > previousCaretX, "Caret at $offset must be to the right of the previous one")
            }
            previousCaretX = x
        }
    }

    internal fun runTextFieldTransformationTest(
        usingNativeTextInput: Boolean? = null,
        testBlock: UIKitInstrumentedTest.(
            usingNativeTextInput: Boolean,
            textFieldType: BasicTextFieldType
        ) -> Unit
    ) = runTextFieldTest(
        newContextMenuEnabled = false, // Context menu is not goal of this test
        usingNativeTextInput = usingNativeTextInput,
    ) { _, usingNativeTextInput, textFieldType ->
        testBlock(usingNativeTextInput, textFieldType)
    }

    private fun UIKitInstrumentedTest.setTransformedTextFieldContent(
        state: TestTextFieldState,
        nativeTextInput: Boolean,
        outputTransformation: OutputTransformation = DigitGroupsOutputTransformation,
        visualTransformation: VisualTransformation = DigitGroupsVisualTransformation,
    ) {
        val focusRequester = FocusRequester()
        setContent {
            Column(modifier = Modifier.safeDrawingPadding()) {
                TestTextField(
                    state = state,
                    modifier = Modifier
                        .testTag(TEXT_FIELD_TAG)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        // Prevents the native autocorrection from changing the text.
                        autoCorrectEnabled = false,
                        platformImeOptions = PlatformImeOptions {
                            usingNativeTextInput(nativeTextInput)
                        }
                    ),
                    inputTransformation = InputTransformation {
                        if (!state.filter(asCharSequence().toString())) {
                            revertAllChanges()
                        }
                    },
                    outputTransformation = outputTransformation,
                    visualTransformation = visualTransformation,
                )
            }
        }
        focusRequester.requestFocus()
        waitForIdle()
    }

    private fun UIKitInstrumentedTest.setShorterTextFieldContent(
        state: TestTextFieldState,
        nativeTextInput: Boolean,
    ) = setTransformedTextFieldContent(
        state = state,
        nativeTextInput = nativeTextInput,
        outputTransformation = NoDashesOutputTransformation,
        visualTransformation = NoDashesVisualTransformation,
    )

    private fun UIKitInstrumentedTest.setSelectedTextRange(range: TextRange) {
        val textInput = focusedUITextInput()
        textInput.setSelectedTextRange(textInput.textRange(range))
        waitForIdle()
    }

    /**
     * Compose shows the selection handles only after a selection gesture, so this double taps the
     * text field when the text input view doesn't draw the selection. It changes the selection.
     */
    private fun UIKitInstrumentedTest.showComposeSelectionHandles(nativeTextInput: Boolean) {
        if (nativeTextInput) return
        findNodeWithTag(TEXT_FIELD_TAG).multiTapCharacter(offset = 1, count = 2)
        waitForIdle()
    }

    /**
     * Checks the selection that is presented to the user: it must cover exactly the
     * [transformedRange] of the presented (transformed) text.
     */
    private fun UIKitInstrumentedTest.assertSelectionHighlight(
        state: TestTextFieldState,
        nativeTextInput: Boolean,
        transformedRange: TextRange,
    ) {
        if (nativeTextInput) {
            assertNativeSelection(transformedRange)
        } else {
            assertComposeSelection(state, transformedRange)
        }
    }

    /**
     * Checks the selection published in the semantics and the selection handles drawn by Compose.
     */
    private fun UIKitInstrumentedTest.assertComposeSelection(
        state: TestTextFieldState,
        transformedRange: TextRange,
    ) {
        val node = findNodeWithTag(TEXT_FIELD_TAG)
        // BasicTextField with TextFieldValue publishes the selection in the original text, and
        // decides whether to show the handles by it.
        val composeSelection =
            if (state.textFieldType == BasicTextFieldType.V1) state.selection else transformedRange
        assertEquals(composeSelection, selectionRange(node), "Selection in the semantics")

        val handles = selectionHandles()
        if (composeSelection.collapsed) {
            assertNull(handles, "No selection handles expected for $transformedRange")
            return
        }
        assertNotNull(handles, "Selection handles expected for $transformedRange")
        val layout = textLayoutResult(node)
        assertEquals(
            layout.getHorizontalPosition(transformedRange.min, usePrimaryDirection = true),
            handles.start.info.position.x,
            absoluteTolerance = 1f,
            "Start handle for $transformedRange"
        )
        assertEquals(
            layout.getHorizontalPosition(transformedRange.max, usePrimaryDirection = true),
            handles.end.info.position.x,
            absoluteTolerance = 1f,
            "End handle for $transformedRange"
        )
    }

    /** Checks the selection that iOS draws from the geometry of the native text input view. */
    private fun UIKitInstrumentedTest.assertNativeSelection(transformedRange: TextRange) {
        val textInput = focusedUITextInput()
        val view = textInput as UIView
        val selectedRange = textInput.selectedTextRange ?: error("No selected text range")
        val node = findNodeWithTag(TEXT_FIELD_TAG)
        val expectedLeft = node.characterPosition(transformedRange.min).x.value.toDouble()
        val expectedRight = node.characterPosition(transformedRange.max).x.value.toDouble()

        val rects = textInput.selectionRectsForRange(selectedRange).map {
            view.convertRect((it as UITextSelectionRect).rect, toView = null)
        }
        if (transformedRange.collapsed) {
            assertTrue(rects.isEmpty(), "No selection rects expected for $transformedRange")
            val caretX = view.convertRect(textInput.caretRectForPosition(selectedRange.start), toView = null)
                .useContents { origin.x + size.width / 2 }
            assertEquals(expectedLeft, caretX, absoluteTolerance = 1.0, "Caret for $transformedRange")
            return
        }

        val rect = rects.singleOrNull() ?: error("Single selection rect expected for $transformedRange, got $rects")
        rect.useContents {
            assertEquals(expectedLeft, origin.x, absoluteTolerance = 1.0, "Left edge of the selection for $transformedRange")
            assertEquals(expectedRight, origin.x + size.width, absoluteTolerance = 1.0, "Right edge of the selection for $transformedRange")
        }
    }

    /** The layout of the presented (transformed) text. */
    private fun UIKitInstrumentedTest.textLayoutResult(node: AccessibilityTestNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.semanticsNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull() ?: error("No text layout result")
    }

    /**
     * Checks that the text field has the [selection], and the text input view presents the original
     * text and the [selection] in it to iOS.
     */
    private fun UIKitInstrumentedTest.assertTextInputState(
        state: TestTextFieldState,
        selection: TextRange,
    ) {
        assertEquals(selection, state.selection, "Selection in the text field")
        val textInput = focusedUITextInput()
        assertEquals(state.text, textInput.text(TextRange(0, textInput.endOffset)), "Text presented to iOS")
        assertEquals(selection, textInput.selection, "Selection presented to iOS")
    }

    private val UITextInputProtocol.endOffset: Int
        get() = offsetFromPosition(beginningOfDocument, endOfDocument).toInt()

    private fun UITextInputProtocol.textRange(range: TextRange): UITextRange {
        val start = positionFromPosition(beginningOfDocument, range.start.toLong())!!
        val end = positionFromPosition(beginningOfDocument, range.end.toLong())!!
        return textRangeFromPosition(start, end)!!
    }

    private fun UITextInputProtocol.text(range: TextRange): String =
        textInRange(textRange(range)) ?: error("No text in $range")

    private fun UITextInputProtocol.toTextRange(range: UITextRange): TextRange = TextRange(
        offsetFromPosition(beginningOfDocument, range.start).toInt(),
        offsetFromPosition(beginningOfDocument, range.end).toInt()
    )

    private val UITextInputProtocol.selection: TextRange?
        get() = selectedTextRange?.let { toTextRange(it) }

    private val UITextInputProtocol.markedRange: TextRange?
        get() = markedTextRange?.let { toTextRange(it) }

    private companion object {
        const val TEXT_FIELD_TAG = "TextField"
        const val GROUP_SIZE = 4
        const val DASH = '-'
    }

    private object NoDashesOutputTransformation : OutputTransformation {
        override fun TextFieldBuffer.transformOutput() {
            for (index in length - 1 downTo 0) {
                if (charAt(index) == DASH) {
                    replace(index, index + 1, "")
                }
            }
        }
    }

    private object NoDashesVisualTransformation : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            val original = text.text
            val offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    offset - original.take(offset).count { it == DASH }

                // The cursor at the hidden dashes is placed before them.
                override fun transformedToOriginal(offset: Int): Int {
                    if (offset == 0) return 0
                    var presented = 0
                    original.forEachIndexed { index, char ->
                        if (char != DASH && ++presented == offset) return index + 1
                    }
                    return original.length
                }
            }
            return TransformedText(AnnotatedString(original.filterNot { it == DASH }), offsetMapping)
        }
    }

    private object DigitGroupsOutputTransformation : OutputTransformation {
        override fun TextFieldBuffer.transformOutput() {
            for (group in (length - 1) / GROUP_SIZE downTo 1) {
                insert(group * GROUP_SIZE, " ")
            }
        }
    }

    private object DigitGroupsVisualTransformation : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            val offsetMapping = object : OffsetMapping {
                // The cursor between groups is placed before the separator.
                override fun originalToTransformed(offset: Int): Int =
                    offset + maxOf(0, offset - 1) / GROUP_SIZE

                override fun transformedToOriginal(offset: Int): Int =
                    offset - offset / (GROUP_SIZE + 1)
            }
            return TransformedText(
                AnnotatedString(text.text.chunked(GROUP_SIZE).joinToString(" ")),
                offsetMapping
            )
        }
    }

    private object PrefixOutputTransformation : OutputTransformation {
        override fun TextFieldBuffer.transformOutput() {
            insert(0, "$")
        }
    }

    private object PrefixVisualTransformation : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            val offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset + 1

                override fun transformedToOriginal(offset: Int): Int = maxOf(0, offset - 1)
            }
            return TransformedText(AnnotatedString("$" + text.text), offsetMapping)
        }
    }
}
