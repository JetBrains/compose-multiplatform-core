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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.assertVisibleInContainer
import androidx.compose.ui.test.findFocusedUITextInput
import androidx.compose.ui.test.findNodeWithLabel
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.findNodeWithTagOrNull
import androidx.compose.ui.test.firstNodeOrNull
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.tapContextMenuButton
import androidx.compose.ui.test.utils.findFirstDescendant
import androidx.compose.ui.test.utils.hold
import androidx.compose.ui.test.utils.isLoupeView
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.test.waitForContextMenu
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.OSVersion
import org.jetbrains.skiko.available
import platform.UIKit.UIPasteboard

class SelectionContainerInteractionTest {

    @Test
    fun testSelectionContainer_LongPressSelectsWord() = runUIKitInstrumentedTest {
        val selectionState = SelectionState()
        val text = "accomplishment"

        setSelectionContainerContent(state = selectionState, text = text)

        findNodeWithTag(Tag).longPressAndReleaseAfterLoupe()

        waitUntil("SelectionContainer should select the word after long press") {
            selectionState.selectedText() == text
        }

        assertEquals(
            listOf(text),
            selectionState.selectedTexts.map { it.text },
        )
    }

    @Test
    fun testSelectionContainer_DoubleTapSelectsWord() = runUIKitInstrumentedTest {
        val selectionState = SelectionState()
        val text = "accomplishment"

        setSelectionContainerContent(state = selectionState, text = text)

        findNodeWithTag(Tag).focusThenDoubleTap()
        waitUntil("SelectionContainer should select the word after double tap") {
            selectionState.selectedText() == text
        }

        assertEquals(
            listOf(text),
            selectionState.selectedTexts.map { it.text },
        )
    }

    @Test
    fun testSelectionContainer_LongPressDragExtendsSelectionAcrossLines() =
        runUIKitInstrumentedTest {
            val selectionState = SelectionState()
            val firstLine = "accomplishment"

            setSelectionContainerContent(
                state = selectionState,
                text = "$firstLine\nmagnificent",
                contentWidth = 160.dp,
            )

            longPressAndDrag(
                startTag = Tag,
                endTag = Tag,
                startXFraction = 0.12f,
                startYFraction = 0.25f,
                endXFraction = 0.80f,
                endYFraction = 0.75f,
            )

            waitUntil("SelectionContainer should extend the selection across lines") {
                val selectedText = selectionState.selectedText()
                selectedText.contains("\n") &&
                    selectedText.substringAfter('\n').isNotEmpty()
            }

            val selectedText = selectionState.selectedText()
            assertTrue(
                selectedText.startsWith(firstLine),
                "Expected selection to start from the first line, but got: $selectedText",
            )
        }

    @Test
    fun testSelectionContainer_LongPressDragExtendsSelectionAcrossMultipleBasicTexts() =
        runUIKitInstrumentedTest {
            val selectionState = SelectionState()
            val firstText = "accomplishment"
            val secondText = "magnificent"

            setSelectionContainerContent(state = selectionState) {
                Column {
                    BasicText(
                        text = firstText,
                        modifier = Modifier.width(SelectableTextWidth).testTag(FirstTextTag),
                    )
                    BasicText(
                        text = secondText,
                        modifier = Modifier.width(SelectableTextWidth).testTag(SecondTextTag),
                    )
                }
            }

            longPressAndDrag(
                startTag = FirstTextTag,
                endTag = SecondTextTag,
                startXFraction = 0.02f,
                endXFraction = 0.98f,
            )

            waitUntil("SelectionContainer should extend selection across multiple BasicTexts") {
                selectionState.selectedText() == firstText + secondText
            }

            assertEquals(
                listOf(firstText, secondText),
                selectionState.selectedTexts.map { it.text },
            )
        }

    @Test
    fun testSelectionContainer_LongPressDragSkipsDisableSelectionSubtree() =
        runUIKitInstrumentedTest {
            val selectionState = SelectionState()
            val textBeforeDisabled = "accomplishment"
            val textAfterDisabled = "remarkable"
            val textAfterDisabledTag = "SelectionContainerTextAfterDisabled"

            setSelectionContainerContent(state = selectionState) {
                Column {
                    BasicText(
                        text = textBeforeDisabled,
                        modifier = Modifier.width(SelectableTextWidth).testTag(FirstTextTag),
                    )
                    DisableSelection {
                        BasicText(
                            text = "hidden",
                            modifier = Modifier.width(SelectableTextWidth),
                        )
                    }
                    BasicText(
                        text = textAfterDisabled,
                        modifier =
                            Modifier.width(SelectableTextWidth).testTag(textAfterDisabledTag),
                    )
                }
            }

            longPressAndDrag(
                startTag = FirstTextTag,
                endTag = textAfterDisabledTag,
                startXFraction = 0.02f,
                endXFraction = 0.98f,
            )

            waitUntil("SelectionContainer should skip DisableSelection content during drag") {
                selectionState.selectedText() == textBeforeDisabled + textAfterDisabled
            }

            assertEquals(
                listOf(textBeforeDisabled, textAfterDisabled),
                selectionState.selectedTexts.map { it.text },
            )
        }

    @Test
    fun testSelectionContainer_CopyCopiesExactSelectedText() =
        runSelectionContainerContextMenuTest {
            UIPasteboard.generalPasteboard().string = "Clipboard sentinel"
            val selectionState = SelectionState()
            val firstWord = "copyable"
            val text = "$firstWord second"

            setSelectionContainerContent(state = selectionState, text = text)

            awaitNodeLaidOut(Tag)
            findNodeWithTag(Tag).openToolbarForLeadingWord(
                DoubleTapPreparationDelayMillis,
                ManualDoubleTapIntervalDelayMillis
            )
            waitUntil("SelectionContainer should create the expected word selection before Copy") {
                selectionState.selectedText() == firstWord
            }
            findNodeWithLabel("Copy").assertVisibleInContainer()
            tapContextMenuButton("Copy")

            waitUntil("Pasteboard should contain the copied SelectionContainer text") {
                UIPasteboard.generalPasteboard().string == firstWord
            }

            val selectionAfterCopy = selectionState.selectedText()
            assertTrue(
                selectionAfterCopy.isEmpty() || selectionAfterCopy == firstWord,
                "Expected SelectionContainer selection to either clear or preserve the copied word after Copy, but was: $selectionAfterCopy",
            )
        }

    @Test
    fun testSelectionContainer_EmptyTextDoesNotOpenMenu() = runSelectionContainerContextMenuTest {
        val selectionState = SelectionState()

        setSelectionContainerContent(state = selectionState, text = "")

        awaitNodeLaidOut(Tag)
        findNodeWithTag(Tag).longPress()
        waitForIdle()
        delay(EmptyTextLongPressSettleDelayMillis)

        assertTrue(
            selectionState.selectedTexts.isEmpty(),
            "Expected empty text content to produce no selection.",
        )
        assertNoContextMenu()
    }

    @Test
    fun testSelectionContainer_OpeningMenuDoesNotShowKeyboard() =
        runSelectionContainerContextMenuTest {
            val selectionState = SelectionState()

            setSelectionContainerContent(state = selectionState, text = "copyable second")

            awaitNodeLaidOut(Tag)
            findNodeWithTag(Tag).focusThenDoubleTap()
            waitForContextMenu()
            waitUntil("SelectionContainer should create a selection before menu open") {
                selectionState.selectedTexts.isNotEmpty()
            }

            findNodeWithLabel("Copy").assertVisibleInContainer()
            assertNull(
                findFocusedUITextInput(),
                "Expected SelectionContainer menu to open without focusing a UITextInput.",
            )
            assertEquals(0.dp, keyboardHeight)
        }

    private fun UIKitInstrumentedTest.setSelectionContainerContent(
        state: SelectionState,
        content: @Composable () -> Unit,
    ) {
        setContent {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                SelectionContainer(
                    state = state,
                    modifier = Modifier.align(Alignment.Center).testTag(Tag),
                ) {
                    content()
                }
            }
        }
    }

    private fun UIKitInstrumentedTest.setSelectionContainerContent(
        state: SelectionState,
        text: String,
        contentWidth: Dp = 320.dp,
    ) {
        setSelectionContainerContent(state = state) {
            BasicText(text = text, modifier = Modifier.width(contentWidth))
        }
    }

    private fun UIKitInstrumentedTest.awaitNodeLaidOut(tag: String) {
        waitUntil("Node with tag $tag should be laid out") {
            findNodeWithTagOrNull(tag)?.frame != null
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun runSelectionContainerContextMenuTest(testBlock: UIKitInstrumentedTest.() -> Unit) =
        runUIKitInstrumentedTest(params = listOf(false, true)) { newContextMenuEnabled ->
            val previousValue = ComposeFoundationFlags.isNewContextMenuEnabled
            ComposeFoundationFlags.isNewContextMenuEnabled = newContextMenuEnabled
            try {
                testBlock()
            } finally {
                ComposeFoundationFlags.isNewContextMenuEnabled = previousValue
            }
        }

    private fun UIKitInstrumentedTest.longPressAndDrag(
        startTag: String,
        endTag: String,
        startXFraction: Float,
        endXFraction: Float,
        startYFraction: Float = 0.5f,
        endYFraction: Float = 0.5f,
    ) {
        val startPoint = findNodeWithTag(startTag).pointInNode(startXFraction, startYFraction)
        val endPoint = findNodeWithTag(endTag).pointInNode(endXFraction, endYFraction)

        val touch = touchDown(startPoint)
        waitUntil("Selection loupe should appear after long press") {
            findFirstDescendant { it.isLoupeView } != null
        }
        touch.hold()
        delay(LongPressDragSettleDelayMillis)
        touch.dragTo(x = endPoint.x, y = endPoint.y, duration = 0.3.seconds)
        touch.up()
    }

    private fun UIKitInstrumentedTest.assertNoContextMenu() {
        assertNull(
            firstNodeOrNull { node ->
                node.element?.let { it::class.simpleName } == if (available(OS.Ios to OSVersion(16))) {
                    "_UIEditMenuContainerView"
                } else {
                    "UICalloutBar"
                }
            },
            "Expected no SelectionContainer context menu host to be present.",
        )
    }

    private fun SelectionState.selectedText(): String =
        selectedTexts.joinToString(separator = "") { it.text }

    private companion object {
        private const val Tag = "SelectionContainer"
        private const val FirstTextTag = "SelectionContainerFirstText"
        private const val SecondTextTag = "SelectionContainerSecondText"
        // Gives the first tap time to settle so the next doubleTap() is treated as a new gesture.
        private const val DoubleTapPreparationDelayMillis = 500L
        // Lets us observe that long-pressing empty text does not create a late menu or selection.
        private const val EmptyTextLongPressSettleDelayMillis = 500L
        // Keeps the two manual taps close enough for UIKit to recognize them as a double tap.
        private const val ManualDoubleTapIntervalDelayMillis = 50L
        // Gives long-press state a brief moment to settle before starting a drag extension.
        private const val LongPressDragSettleDelayMillis = 100L
        private val SelectableTextWidth = 160.dp
    }
}
