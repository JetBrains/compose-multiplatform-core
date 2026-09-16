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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.AccessibilityTestNode
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.BasicTextFieldType
import androidx.compose.ui.test.utils.TestHandle
import androidx.compose.ui.test.utils.TestSelectionHandleAnchor
import androidx.compose.ui.test.utils.selectionHandles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.UIKit.endEditing

class TextFieldSelectionHandlesTest {

    @Test fun selectionHandles_ltr_btf1() = runUIKitInstrumentedTest { checkSelectionHandlesLtr(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun selectionHandles_ltr_btf2() = runUIKitInstrumentedTest { checkSelectionHandlesLtr(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun selectionHandles_ltr_nitiBtf1() = runUIKitInstrumentedTest { checkSelectionHandlesLtr(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun selectionHandles_ltr_nitiBtf2() = runUIKitInstrumentedTest { checkSelectionHandlesLtr(BasicTextFieldType.V2, nativeInput = true) }

    @Test fun noSelectionHandlesForCaret_ltr_btf1() = runUIKitInstrumentedTest { checkNoSelectionHandlesForCaretLtr(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun noSelectionHandlesForCaret_ltr_btf2() = runUIKitInstrumentedTest { checkNoSelectionHandlesForCaretLtr(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun noSelectionHandlesForCaret_ltr_nitiBtf1() = runUIKitInstrumentedTest { checkNoSelectionHandlesForCaretLtr(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun noSelectionHandlesForCaret_ltr_nitiBtf2() = runUIKitInstrumentedTest { checkNoSelectionHandlesForCaretLtr(BasicTextFieldType.V2, nativeInput = true) }

    @Test fun dragEndSelectionHandle_ltr_btf1() = runUIKitInstrumentedTest { checkDragEndSelectionHandleLtr(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun dragEndSelectionHandle_ltr_btf2() = runUIKitInstrumentedTest { checkDragEndSelectionHandleLtr(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun dragEndSelectionHandle_ltr_nitiBtf1() = runUIKitInstrumentedTest { checkDragEndSelectionHandleLtr(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun dragEndSelectionHandle_ltr_nitiBtf2() = runUIKitInstrumentedTest { checkDragEndSelectionHandleLtr(BasicTextFieldType.V2, nativeInput = true) }

    @Test fun dragStartSelectionHandle_ltr_btf1() = runUIKitInstrumentedTest { checkDragStartSelectionHandleLtr(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun dragStartSelectionHandle_ltr_btf2() = runUIKitInstrumentedTest { checkDragStartSelectionHandleLtr(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun dragStartSelectionHandle_ltr_nitiBtf1() = runUIKitInstrumentedTest { checkDragStartSelectionHandleLtr(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun dragStartSelectionHandle_ltr_nitiBtf2() = runUIKitInstrumentedTest { checkDragStartSelectionHandleLtr(BasicTextFieldType.V2, nativeInput = true) }

    @Test fun dragEndSelectionHandleAcrossLines_ltr_btf1() = runUIKitInstrumentedTest { checkDragEndSelectionHandleAcrossLinesLtr(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun dragEndSelectionHandleAcrossLines_ltr_btf2() = runUIKitInstrumentedTest { checkDragEndSelectionHandleAcrossLinesLtr(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun dragEndSelectionHandleAcrossLines_ltr_nitiBtf1() = runUIKitInstrumentedTest { checkDragEndSelectionHandleAcrossLinesLtr(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun dragEndSelectionHandleAcrossLines_ltr_nitiBtf2() = runUIKitInstrumentedTest { checkDragEndSelectionHandleAcrossLinesLtr(BasicTextFieldType.V2, nativeInput = true) }

    @Test fun dragStartSelectionHandleAcrossLines_ltr_btf1() = runUIKitInstrumentedTest { checkDragStartSelectionHandleAcrossLinesLtr(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun dragStartSelectionHandleAcrossLines_ltr_btf2() = runUIKitInstrumentedTest { checkDragStartSelectionHandleAcrossLinesLtr(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun dragStartSelectionHandleAcrossLines_ltr_nitiBtf1() = runUIKitInstrumentedTest { checkDragStartSelectionHandleAcrossLinesLtr(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun dragStartSelectionHandleAcrossLines_ltr_nitiBtf2() = runUIKitInstrumentedTest { checkDragStartSelectionHandleAcrossLinesLtr(BasicTextFieldType.V2, nativeInput = true) }

    @Test fun crossSelectionHandles_ltr_btf1() = runUIKitInstrumentedTest { checkCrossSelectionHandlesLtr(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun crossSelectionHandles_ltr_btf2() = runUIKitInstrumentedTest { checkCrossSelectionHandlesLtr(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun crossSelectionHandles_ltr_nitiBtf1() = runUIKitInstrumentedTest { checkCrossSelectionHandlesLtr(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun crossSelectionHandles_ltr_nitiBtf2() = runUIKitInstrumentedTest { checkCrossSelectionHandlesLtr(BasicTextFieldType.V2, nativeInput = true) }
}

private const val FIELD_TAG = "textField"

private const val TEXT = "The quick brown fox jumps"

private const val THREE_LINE_TEXT = "The quick brown fox\njumps over the lazy dog\nand back to the kennel"

private val SECOND_LINE_BREAK_OFFSET = THREE_LINE_TEXT.lastIndexOf('\n')

private fun UIKitInstrumentedTest.getActiveTextFieldNode(): AccessibilityTestNode =
    findNodeWithTag(FIELD_TAG)

private fun String.rangeOf(word: String, from: Int = 0): TextRange {
    val start = indexOf(word, from)
    require(start >= 0) { "'$word' is not in \"$this\"" }
    return TextRange(start, start + word.length)
}

private val TextRange.middle: Int get() = (min + max) / 2

private fun UIKitInstrumentedTest.dropNativeSelection() {
    viewController.view.endEditing(force = true)
    // TODO: CMP-10641
    delay(500)
}

private fun UIKitInstrumentedTest.setUpField(
    text: String,
    btf: BasicTextFieldType,
    nativeInput: Boolean,
): () -> TextRange {
    val ime = KeyboardOptions(
        // Disable autocorrect/spell-check so the native iOS replacement-suggestion bubble does not
        // pop over the selection during the test (spellCheckingType defaults to follow this).
        autoCorrectEnabled = false,
        platformImeOptions = PlatformImeOptions { usingNativeTextInput(nativeInput) }
    )
    val focusRequester = FocusRequester()
    return when (btf) {
        BasicTextFieldType.V1 -> {
            val value = mutableStateOf(TextFieldValue(text))
            setContent {
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Box(Modifier.fillMaxSize()) {
                    BasicTextField(
                        value = value.value,
                        onValueChange = { value.value = it },
                        keyboardOptions = ime,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .focusRequester(focusRequester)
                            .testTag(FIELD_TAG),
                    )
                }
            }
            ({ value.value.selection })
        }
        BasicTextFieldType.V2 -> {
            val state = TextFieldState(text)
            setContent {
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Box(Modifier.fillMaxSize()) {
                    BasicTextField(
                        state = state,
                        keyboardOptions = ime,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .focusRequester(focusRequester)
                            .testTag(FIELD_TAG),
                    )
                }
            }
            ({ state.selection })
        }
    }
}

private fun UIKitInstrumentedTest.checkSelectionHandlesLtr(btf: BasicTextFieldType, nativeInput: Boolean) {
    val selection = setUpField(TEXT, btf, nativeInput)
    val quick = TEXT.rangeOf("quick")

    getActiveTextFieldNode().multiTapCharacter(offset = quick.middle, count = 2)
    waitForIdle()

    val handles = assertNotNull(
        selectionHandles(),
        "expected 'quick' selected at $quick, got ${selection()}",
    )
    assertEquals(
        listOf(TestSelectionHandleAnchor.Left, TestSelectionHandleAnchor.Right),
        listOf(handles.start.info.anchor, handles.end.info.anchor),
        "the start handle should be drawn on the left of the selection and the end handle on its right",
    )
    if (nativeInput) dropNativeSelection()
}

private fun UIKitInstrumentedTest.checkNoSelectionHandlesForCaretLtr(btf: BasicTextFieldType, nativeInput: Boolean) {
    val selection = setUpField(TEXT, btf, nativeInput)

    getActiveTextFieldNode().tapCharacter(offset = TEXT.rangeOf("quick").middle)
    waitForIdle()

    val caret = selection()
    assertTrue(caret.collapsed, "expected a caret after the tap, got $caret")
    assertNull(selectionHandles(), "a caret must expose no selection handles")
}

private fun UIKitInstrumentedTest.checkDragEndSelectionHandleLtr(btf: BasicTextFieldType, nativeInput: Boolean) {
    val selection = setUpField(TEXT, btf, nativeInput)
    val fox = TEXT.rangeOf("fox")

    getActiveTextFieldNode().multiTapCharacter(offset = TEXT.rangeOf("quick").middle, count = 2)
    waitForIdle()
    val before = selection()
    assertTrue(!before.collapsed, "expected a word selection, got $before")

    // Aimed at a word boundary — word acceleration, see dragSelectionHandle.
    getActiveTextFieldNode().dragSelectionHandle(TestHandle.SelectionEnd, toOffset = fox.max)
    waitForIdle()

    val after = selection()
    assertEquals(fox.max, after.max, "end handle drag should extend selection to the end of 'fox': $before -> $after")
    assertEquals(before.min, after.min, "start should not move: $before -> $after")
    if (nativeInput) dropNativeSelection()
}

private fun UIKitInstrumentedTest.checkDragStartSelectionHandleLtr(btf: BasicTextFieldType, nativeInput: Boolean) {
    val selection = setUpField(TEXT, btf, nativeInput)
    val quick = TEXT.rangeOf("quick")

    getActiveTextFieldNode().multiTapCharacter(offset = TEXT.rangeOf("brown").middle, count = 2)
    waitForIdle()
    val before = selection()
    assertTrue(!before.collapsed, "expected a word selection, got $before")

    // Aimed at a word boundary — word acceleration, see dragSelectionHandle.
    getActiveTextFieldNode().dragSelectionHandle(TestHandle.SelectionStart, toOffset = quick.min)
    waitForIdle()

    val after = selection()
    assertEquals(quick.min, after.min, "start handle drag should extend selection to the start of 'quick': $before -> $after")
    assertEquals(before.max, after.max, "end should not move: $before -> $after")
    if (nativeInput) dropNativeSelection()
}

private fun UIKitInstrumentedTest.checkDragEndSelectionHandleAcrossLinesLtr(btf: BasicTextFieldType, nativeInput: Boolean) {
    val selection = setUpField(THREE_LINE_TEXT, btf, nativeInput)
    val quick = THREE_LINE_TEXT.rangeOf("quick")
    val theOnLastLine = THREE_LINE_TEXT.rangeOf("the", from = SECOND_LINE_BREAK_OFFSET)

    getActiveTextFieldNode().multiTapCharacter(offset = quick.middle, count = 2)
    waitForIdle()
    val before = selection()
    assertTrue(!before.collapsed, "expected a word selection on the first line, got $before")

    // Aimed at a word boundary — word acceleration, see dragSelectionHandle.
    getActiveTextFieldNode().dragSelectionHandle(TestHandle.SelectionEnd, toOffset = theOnLastLine.max)
    waitForIdle()

    val after = selection()
    assertEquals(theOnLastLine.max, after.max, "end handle should cross both line breaks and land on the end of 'the': $before -> $after")
    assertEquals(before.min, after.min, "start should not move: $before -> $after")

    val handles = assertNotNull(selectionHandles(), "expected the selection $after to show handles")
    assertTrue(
        handles.start.grabPoint.y < getActiveTextFieldNode().characterPosition(offset = quick.middle).y,
        "start handle should sit above the first line, was ${handles.start.grabPoint}",
    )
    assertTrue(
        handles.end.grabPoint.y > getActiveTextFieldNode().characterPosition(offset = theOnLastLine.max).y,
        "end handle should sit below the third line, was ${handles.end.grabPoint}",
    )
    if (nativeInput) dropNativeSelection()
}

private fun UIKitInstrumentedTest.checkDragStartSelectionHandleAcrossLinesLtr(btf: BasicTextFieldType, nativeInput: Boolean) {
    val selection = setUpField(THREE_LINE_TEXT, btf, nativeInput)
    val quick = THREE_LINE_TEXT.rangeOf("quick")
    val theOnLastLine = THREE_LINE_TEXT.rangeOf("the", from = SECOND_LINE_BREAK_OFFSET)

    getActiveTextFieldNode().multiTapCharacter(offset = theOnLastLine.middle, count = 2)
    waitForIdle()
    val before = selection()
    assertTrue(!before.collapsed, "expected a word selection on the third line, got $before")

    // Aimed at a word boundary — word acceleration, see dragSelectionHandle.
    getActiveTextFieldNode().dragSelectionHandle(TestHandle.SelectionStart, toOffset = quick.min)
    waitForIdle()

    val after = selection()
    assertEquals(quick.min, after.min, "start handle should cross both line breaks and land on the start of 'quick': $before -> $after")
    assertEquals(before.max, after.max, "end should not move: $before -> $after")
    if (nativeInput) dropNativeSelection()
}

private fun UIKitInstrumentedTest.checkCrossSelectionHandlesLtr(btf: BasicTextFieldType, nativeInput: Boolean) {
    val selection = setUpField(TEXT, btf, nativeInput)

    getActiveTextFieldNode().multiTapCharacter(offset = TEXT.rangeOf("brown").middle, count = 2)
    waitForIdle()
    val before = selection()
    assertTrue(!before.collapsed, "expected a word selection, got $before")

    // Past the other edge, into the first word of the text.
    getActiveTextFieldNode().dragSelectionHandle(TestHandle.SelectionEnd, toOffset = TEXT.rangeOf("The").middle)
    waitForIdle()

    val after = selection()
    assertTrue(!after.collapsed, "crossing the handles should keep a selection, got $after")
    assertTrue(after.min < before.min, "the dragged edge should have crossed the other one: $before -> $after")
    assertEquals(before.min, after.max, "the fixed edge moved: $before -> $after")

    val handles = assertNotNull(selectionHandles(), "expected the crossed selection $after to show handles")
    assertEquals(
        setOf(TestSelectionHandleAnchor.Left, TestSelectionHandleAnchor.Right),
        setOf(handles.start.info.anchor, handles.end.info.anchor),
        "a crossed selection should still be drawn with a leading and a trailing handle",
    )
    if (nativeInput) dropNativeSelection()
}
