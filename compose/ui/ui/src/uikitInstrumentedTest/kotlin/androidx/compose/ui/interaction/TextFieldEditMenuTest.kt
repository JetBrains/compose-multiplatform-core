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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.assertVisibleInContainer
import androidx.compose.ui.test.findNodeWithLabel
import androidx.compose.ui.test.findNodeWithLabelOrNull
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.isContextMenuVisible
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.tapContextMenuButton
import androidx.compose.ui.test.utils.BasicTextFieldType
import androidx.compose.ui.test.utils.findFirstDescendant
import androidx.compose.ui.test.utils.isLoupeView
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.test.waitForContextMenu
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import platform.UIKit.UIPasteboard

class TextFieldEditMenuTest {
    @Test
    fun testTextFieldToolbarAppearance() = runTextFieldTest { _, nativeTextInput, textFieldType ->
        UIPasteboard.generalPasteboard().string = "Paste text"
        val state = TestTextFieldState(
            text = "Hello-LongLongLongLongLongLong-text",
            textFieldType = textFieldType
        )
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                TestTextField(
                    state = state,
                    modifier = textFieldModifier(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        platformImeOptions = PlatformImeOptions {
                            usingNativeTextInput(nativeTextInput)
                        }
                    )
                )
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }

        openToolbar(textFieldTag = "TextField")

        if (nativeTextInput) {
            // Native Text Input does not show Select All menu when selection is not empty
            verifyContextMenuItemsVisible(listOf("Cut", "Copy", "Paste"))
        } else {
            verifyContextMenuItemsVisible(listOf("Cut", "Copy", "Paste", "Select All"))
        }
    }

    @Test
    fun testBasicTextFieldToolbarInteraction() = runTextFieldTest(usingNativeTextInput = false) { _, _, textFieldKind ->
        val state = TestTextFieldState(
            text = "Hello-LongLongLongLongLongLong-text",
            textFieldType = textFieldKind
        )
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                TestTextField(
                    state = state,
                    modifier = textFieldModifier(focusRequester)
                )
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }

        openToolbar(textFieldTag = "TextField")

        verifyFullToolbarPresent()
    }

    @Test
    fun testBasicTextFieldToolbarNewContextMenu() = runContextMenuTest(true) {
        UIPasteboard.generalPasteboard().string = "Paste text"
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                @Suppress("DEPRECATION")
                TextField("Hello-LongLongLongLongLong-text", {}, modifier = textFieldModifier(focusRequester))
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }

        openToolbar(textFieldTag = "TextField")

        verifyFullToolbarPresent()
    }

    @Test
    fun testBasicTextField2ToolbarNewContextMenu() = runContextMenuTest(true) {
        UIPasteboard.generalPasteboard().string = "Paste text"
        val textFieldState = TextFieldState("Hello-LongLongLongLongLongLong-text")
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                BasicTextField(textFieldState, modifier = textFieldModifier(focusRequester))
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }

        openToolbar(textFieldTag = "TextField")

        verifyFullToolbarPresent()
    }

    @Test
    fun testBasicTextFieldLongPressShowsContextMenu() = runTextFieldTest(
        usingNativeTextInput = false
    ) { _, nativeTextInput, textFieldKind ->
        UIPasteboard.generalPasteboard().string = "Paste text"
        val state = TestTextFieldState(
            text = "Text",
            selection = TextRange(4,4),
            textFieldType = textFieldKind
        )
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                TestTextField(
                    state = state,
                    keyboardOptions = KeyboardOptions(platformImeOptions = PlatformImeOptions {
                        usingNativeTextInput(nativeTextInput)
                    }),
                    modifier = textFieldModifier(focusRequester)
                )
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }

        // A long press positions the cursor and, on release, reveals the context menu.
        longPressNodeWithTagAndAwaitContextMenu("TextField")

        waitForContextMenu()
        findNodeWithLabel("Paste").assertVisibleInContainer()

        // A short tap elsewhere dismisses the context menu.
        findNodeWithTag("TextField").tap()
        waitUntil("Context menu should be hidden") {
            findNodeWithLabelOrNull("Paste") == null
        }

        // A tap again brings the context menu back.
        longPressNodeWithTagAndAwaitContextMenu("TextField")
        findNodeWithLabel("Paste").assertVisibleInContainer()
    }

    @Test
    fun testEditableCollapsedClipboardText() =
        runTextFieldTest(usingNativeTextInput = false) { _, _, textFieldKind ->
            UIPasteboard.generalPasteboard().string = "Paste text"
            setTextFieldContent(
                textFieldType = textFieldKind,
                initialValue = TextFieldValue("Text", TextRange(4, 4)),
                readOnly = false
            )

            longPressNodeWithTagAndAwaitContextMenu("TextField")
            verifyContextMenuItemsVisible(labels = listOf("Paste", "Select", "Select All"))

            verifyContextMenuItemsHidden(labels = listOf("Cut", "Copy"))
        }

    @Test
    fun testEditableCollapsedClipboardEmpty() =
        runTextFieldTest(usingNativeTextInput = false) { _, _, textFieldKind ->
            UIPasteboard.generalPasteboard().string = null
            setTextFieldContent(
                textFieldType = textFieldKind,
                initialValue = TextFieldValue("Text", TextRange(4, 4)),
                readOnly = false
            )

            longPressNodeWithTagAndAwaitContextMenu("TextField")
            verifyContextMenuItemsVisible(listOf("Select", "Select All"))

            verifyContextMenuItemsHidden(labels = listOf("Cut", "Copy", "Paste"))
        }

    @Test
    fun testEditablePartialSelectionClipboardText() =
        runTextFieldTest(usingNativeTextInput = false) { _, _, textFieldKind ->
            UIPasteboard.generalPasteboard().string = "Paste text"
            setTextFieldContent(
                textFieldType = textFieldKind,
                initialValue = TextFieldValue(PARTIAL_SELECTION_TEXT),
                readOnly = false
            )

            openToolbar("TextField")
            verifyContextMenuItemsVisible(labels = listOf("Cut", "Copy", "Paste", "Select All"))
            verifyContextMenuItemsHidden(labels = listOf("Select"))
        }

    @Test
    fun testEditableFullSelectionClipboardTextBasicTextField() = runTextFieldTest(
        usingNativeTextInput = false,
        textFieldType = BasicTextFieldType.V1,
    ) { _, _, textFieldType ->
        runTestEditableFullSelectionClipboardText(textFieldType)
    }

    @Test
    fun testEditableFullSelectionClipboardTextBasicTextField2OldContextMenu() = runTextFieldTest(
        newContextMenuEnabled = false,
        usingNativeTextInput = false,
        textFieldType = BasicTextFieldType.V2,
    ) { _, _, textFieldType ->
        runTestEditableFullSelectionClipboardText(textFieldType)
    }

    @Test
    @Ignore // CMP-10301: Menu is not shown after tap on Select All
    fun testEditableFullSelectionClipboardTextBasicTextField2NewContextMenu() = runTextFieldTest(
        newContextMenuEnabled = true,
        usingNativeTextInput = false,
        textFieldType = BasicTextFieldType.V2,
    ) { _, _, textFieldType ->
        runTestEditableFullSelectionClipboardText(textFieldType)
    }

    private fun UIKitInstrumentedTest.runTestEditableFullSelectionClipboardText(
        textFieldType: BasicTextFieldType
    ) {
        UIPasteboard.generalPasteboard().string = "Paste text"
        val textFieldState = TestTextFieldState(
            text = "Text",
            selection = TextRange(4, 4),
            textFieldType = textFieldType
        )
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                TestTextField(
                    state = textFieldState,
                    modifier = textFieldModifier(focusRequester)
                )
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }

        longPressNodeWithTagAndAwaitContextMenu("TextField")
        tapContextMenuButton("Select All")
        waitUntil("Text field should be fully selected") {
            textFieldState.isFullySelected()
        }

        val visible = listOf("Cut", "Copy", "Paste")
        val hidden = listOf("Select", "Select All")

        waitUntil("Context menu should update for full selection") {
            visible.all { findNodeWithLabelOrNull(it) != null } &&
                hidden.all { findNodeWithLabelOrNull(it) == null }
        }

        verifyContextMenuItemsVisible(labels = visible)
        verifyContextMenuItemsHidden(labels = hidden)
    }

    @Test
    fun testReadOnlyCollapsedClipboardText() =
        runTextFieldTest(usingNativeTextInput = false) { _, _, textFieldType ->
            UIPasteboard.generalPasteboard().string = "Paste text"
            setTextFieldContent(
                textFieldType = textFieldType,
                initialValue = TextFieldValue("Text", TextRange(4, 4)),
                readOnly = true
            )

            longPressNodeWithTagAndAwaitContextMenu("TextField")
            verifyContextMenuItemsVisible(labels = listOf("Select", "Select All"))
            verifyContextMenuItemsHidden(labels = listOf("Cut", "Copy", "Paste"))
        }

    @Test
    fun testReadOnlyPartialSelectionClipboardText() =
        runTextFieldTest(usingNativeTextInput = false) { _, _, textFieldType ->
            UIPasteboard.generalPasteboard().string = "Paste text"
            setTextFieldContent(
                textFieldType = textFieldType,
                initialValue = TextFieldValue(PARTIAL_SELECTION_TEXT),
                readOnly = true
            )

            openToolbar("TextField")
            verifyContextMenuItemsVisible(labels = listOf("Copy", "Select All"))
            verifyContextMenuItemsHidden(labels = listOf("Cut", "Paste", "Select"))
        }

    @Test
    fun testTapsCountingWithMultiTouch() = runUIKitInstrumentedTest {
        var touchesDown = 0
        var touchesUp = 0

        setContent {
            Column {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .testTag("Box 1")
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    event.changes.forEach { change ->
                                        if (change.changedToDown()) {
                                            touchesDown++
                                        } else if (change.changedToUp()) {
                                            touchesUp++
                                        }
                                    }
                                }
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .testTag("Box 2")
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    awaitPointerEvent(pass = PointerEventPass.Initial)
                                }
                            }
                        }
                )
            }
        }

        val tap1 = findNodeWithTag("Box 1").touchDown()
        val tap2 = findNodeWithTag("Box 2").touchDown()

        assertEquals(1, touchesDown)
        assertEquals(0, touchesUp)

        tap1.dragBy(dx = 20.dp, duration = 0.1.seconds)
        tap2.dragBy(dx = 20.dp, duration = 0.1.seconds)

        tap1.up()
        tap2.up()
        waitForIdle()

        assertEquals(1, touchesDown)
        assertEquals(1, touchesUp)
    }

    @Test
    fun testComposePanelClearFocusOnMouseDownEnabledFlag() = runUIKitInstrumentedTest {
        val focusRequester = FocusRequester()
        var textFieldIsFocused = false

        setContent(
            configure = {
                isClearFocusOnMouseDownEnabled = true
            }
        ) {
            Column {
                BasicTextField(
                    state = rememberTextFieldState(),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            textFieldIsFocused = it.isFocused
                        }
                )
                Box(Modifier.size(100.dp).testTag("box"))
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        assertTrue(textFieldIsFocused)

        // No focus changes after tap
        findNodeWithTag("box").tap()
        waitForIdle()
        assertTrue(textFieldIsFocused)

        // Clear focus on a click
        findNodeWithTag("box").click()
        waitForIdle()
        assertFalse(textFieldIsFocused)
    }

    @Test
    fun testComposePanelClearFocusOnMouseDownDisabledFlag() = runUIKitInstrumentedTest {
        val focusRequester = FocusRequester()
        var textFieldIsFocused = false

        setContent(
            configure = {
                isClearFocusOnMouseDownEnabled = false
            }
        ) {
            Column {
                BasicTextField(
                    state = rememberTextFieldState(),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            textFieldIsFocused = it.isFocused
                        }
                )
                Box(Modifier.size(100.dp).testTag("box"))
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        assertTrue(textFieldIsFocused)

        // No focus changes after tap
        findNodeWithTag("box").tap()
        waitForIdle()
        assertTrue(textFieldIsFocused)

        // No focus changes after click
        findNodeWithTag("box").click()
        waitForIdle()
        assertTrue(textFieldIsFocused)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun testBasicTextFieldCustomContextMenuItems() = runTextFieldTest(
        newContextMenuEnabled = true,
        usingNativeTextInput = false,
    ) { _, _, textFieldType ->
        var customItemClicked = false
        val state = TestTextFieldState(
            text = "Hello-LongLongLongLongLongLong-text",
            textFieldType = textFieldType,
        )
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                TestTextField(
                    state = state,
                    modifier = textFieldModifier(focusRequester)
                        .appendTextContextMenuComponents {
                            item(key = "CustomKey", label = "Custom Action") {
                                customItemClicked = true
                                close()
                            }
                        }
                        .filterTextContextMenuComponents { component ->
                            // Remove all other items to make sure the custom action won't be hidden
                            component.key == "CustomKey"
                        }
                )
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }

        openToolbar(textFieldTag = "TextField")

        findNodeWithLabel("Custom Action").let {
            it.assertVisibleInContainer()
            assertTrue(it.isAccessibilityElement ?: false)
        }

        // Tap custom item and verify it was clicked
        tapContextMenuButton("Custom Action")
        waitUntil("Custom item should be clicked") {
            customItemClicked
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun testSelectionContainerCustomContextMenuItems() = runContextMenuTest(newContextMenuEnabled = true) {
        var customItemClicked = false
        setContent {
            Column(modifier = Modifier.safeDrawingPadding()) {
                SelectionContainer(
                    modifier = Modifier
                        .testTag("SelectionContainer")
                        .appendTextContextMenuComponents {
                            item(key = "CustomKey", label = "Custom Action") {
                                customItemClicked = true
                                close()
                            }
                        }
                        .filterTextContextMenuComponents { component ->
                            // Remove all other items to make sure the custom action won't be hidden
                            component.key == "CustomKey"
                        }
                ) {
                    Text("Hello-LongLongLongLongLongLong-text")
                }
            }
        }

        openToolbar("SelectionContainer")

        findNodeWithLabel("Custom Action").let {
            it.assertVisibleInContainer()
            assertTrue(it.isAccessibilityElement ?: false)
        }

        // Tap custom item and verify it was clicked
        tapContextMenuButton("Custom Action")
        waitUntil("Custom item should be clicked") {
            customItemClicked
        }
    }

    @Test
    fun testSelectionContainerToolbar() = runContextMenuTest(newContextMenuEnabled = false) {
        lateinit var selectionState: SelectionState
        setContent {
            selectionState = rememberSelectionState()
            Column(modifier = Modifier.safeDrawingPadding()) {
                SelectionContainer(
                    state = selectionState,
                    modifier = Modifier.testTag("SelectionContainer")
                ) {
                    Text(SELECTION_CONTAINER_TEXT)
                }
            }
        }

        // A double tap in the middle of the text selects the word under it.
        findNodeWithTag("SelectionContainer").doubleTap()
        waitForContextMenu()

        assertEquals(
            listOf(SELECTION_CONTAINER_MIDDLE_WORD),
            selectionState.selectedTexts.map { it.text },
            "The word in the middle of the text should be selected"
        )

        verifyContextMenuItemsVisible(labels = listOf("Copy"))
        verifyContextMenuItemsHidden(labels = listOf("Cut", "Paste"))

        assertEquals(0.dp, keyboardHeight, "Software keyboard should stay hidden")
    }

    @Suppress("DEPRECATION")
    @Test
    fun testSelectionContainerToolbarWithTextField() = runContextMenuTest(newContextMenuEnabled = false) {
        lateinit var selectionState: SelectionState
        setContent {
            selectionState = rememberSelectionState()
            SelectionContainer(state = selectionState) {
                Column(modifier = Modifier.safeDrawingPadding()) {
                    @Suppress("DEPRECATION")
                    TextField(value = PARTIAL_SELECTION_TEXT, onValueChange = {})
                    Text(SELECTION_CONTAINER_TEXT, modifier = Modifier.testTag("SelectionText"))
                }
            }
        }

        // A double tap in the middle of the text selects the word under it.
        findNodeWithTag("SelectionText").doubleTap()
        waitForContextMenu()

        assertEquals(
            listOf(SELECTION_CONTAINER_MIDDLE_WORD),
            selectionState.selectedTexts.map { it.text },
            "The word in the middle of the text should be selected"
        )

        verifyContextMenuItemsVisible(labels = listOf("Copy"))
        verifyContextMenuItemsHidden(labels = listOf("Cut", "Paste"))

        assertEquals(0.dp, keyboardHeight, "Software keyboard should stay hidden")
    }

    private fun UIKitInstrumentedTest.openToolbar(textFieldTag: String) {
        findNodeWithTag(textFieldTag).focusThenDoubleTap()
        waitForContextMenu()
    }

    private fun textFieldModifier(focusRequester: FocusRequester): Modifier =
        Modifier
            .testTag("TextField")
            .focusRequester(focusRequester)

    private fun UIKitInstrumentedTest.longPressNodeWithTagAndAwaitContextMenu(textFieldTag: String) {
        val touch = findNodeWithTag(textFieldTag).touchDown()
        waitUntil("Awaiting context menu or loupe") {
            isContextMenuVisible || isLoupeVisible
        }
        touch.up()
        waitForContextMenu()
    }

    private val UIKitInstrumentedTest.isLoupeVisible: Boolean get() =
        findFirstDescendant { it.isLoupeView } != null

    private fun UIKitInstrumentedTest.setTextFieldContent(
        textFieldType: BasicTextFieldType,
        initialValue: TextFieldValue,
        readOnly: Boolean,
    ) {
        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.safeDrawingPadding()) {
                val state = remember {
                    TestTextFieldState(
                        text = initialValue.text,
                        selection = initialValue.selection,
                        textFieldType = textFieldType
                    )
                }
                TestTextField(
                    state = state,
                    modifier = textFieldModifier(focusRequester),
                    readOnly = readOnly
                )
            }
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
        }
    }

    private companion object {
        private const val SELECTION_CONTAINER_MIDDLE_WORD = "LongLongLongLongLongLong"
        private const val SELECTION_CONTAINER_TEXT = "Hello-$SELECTION_CONTAINER_MIDDLE_WORD-text"
        private const val PARTIAL_SELECTION_TEXT = "accomplishment extraordinary magnificent establishment"
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun runContextMenuTest(
        newContextMenuEnabled: Boolean,
        testBlock: UIKitInstrumentedTest.() -> Unit
    ) = runUIKitInstrumentedTest {
        val oldValue = ComposeFoundationFlags.isNewContextMenuEnabled
        ComposeFoundationFlags.isNewContextMenuEnabled = newContextMenuEnabled
        try {
            testBlock()
        } finally {
            ComposeFoundationFlags.isNewContextMenuEnabled = oldValue
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun runTextFieldTest(
        newContextMenuEnabled: Boolean? = null,
        usingNativeTextInput: Boolean? = null,
        textFieldType: BasicTextFieldType? = null,
        testBlock: UIKitInstrumentedTest.(
            newContextMenuEnabled: Boolean,
            usingNativeTextInput: Boolean,
            textFieldType: BasicTextFieldType
        ) -> Unit
    ) {
        val oldValue = ComposeFoundationFlags.isNewContextMenuEnabled

        val newContextMenus = newContextMenuEnabled?.let(::listOf) ?: listOf(false, true)
        val nativeTextInputs = usingNativeTextInput?.let(::listOf) ?: listOf(false, true)
        val types = textFieldType?.let(::listOf) ?: BasicTextFieldType.entries

        val variations = newContextMenus.flatMap { menu ->
            nativeTextInputs.flatMap { native ->
                types.map { type -> Triple(menu, native, type) }
            }
        }

        try {
            for ((newContextMenuEnabled, usingNativeTextInput, type) in variations) {
                ComposeFoundationFlags.isNewContextMenuEnabled = newContextMenuEnabled
                println("Debug: newContextMenuEnabled=$newContextMenuEnabled, usingNativeTextInput=$usingNativeTextInput, textFieldType=$type")
                runUIKitInstrumentedTest {
                    testBlock(newContextMenuEnabled, usingNativeTextInput, type)
                }
            }
        } finally {
            ComposeFoundationFlags.isNewContextMenuEnabled = oldValue
        }
    }

    internal class TestTextFieldState(
        text: String = "",
        selection: TextRange = TextRange.Zero,
        val textFieldType: BasicTextFieldType
    ) {
        val value = mutableStateOf(TextFieldValue(text, selection))
        val state = TextFieldState(text, selection)

        val text: String
            get() = if (textFieldType == BasicTextFieldType.V1) value.value.text else state.text.toString()

        val selection: TextRange
            get() = if (textFieldType == BasicTextFieldType.V1) value.value.selection else state.selection

        fun isFullySelected(): Boolean = if (textFieldType == BasicTextFieldType.V1) {
            value.value.selection.start == 0 && value.value.selection.end == value.value.text.length
        } else {
            state.selection.start == 0 && state.selection.end == state.text.length
        }
    }

    @Suppress("DEPRECATION")
    @Composable
    internal fun TestTextField(
        state: TestTextFieldState,
        modifier: Modifier = Modifier,
        keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
        readOnly: Boolean = false
    ) {
        if (state.textFieldType == BasicTextFieldType.V1) {
            BasicTextField(
                value = state.value.value,
                onValueChange = { state.value.value = it },
                modifier = modifier,
                keyboardOptions = keyboardOptions,
                readOnly = readOnly
            )
        } else {
            BasicTextField(
                state = state.state,
                modifier = modifier,
                keyboardOptions = keyboardOptions,
                readOnly = readOnly
            )
        }
    }

    private fun UIKitInstrumentedTest.verifyContextMenuItemsVisible(labels: List<String>) {
        labels.forEach { label ->
            findNodeWithLabel(label).let {
                it.assertVisibleInContainer()
                assertTrue(it.isAccessibilityElement ?: false)
            }
        }
    }

    private fun UIKitInstrumentedTest.verifyContextMenuItemsHidden(labels: List<String>) {
        labels.forEach { label ->
            assertNull(
                findNodeWithLabelOrNull(label),
                "Context menu item \"$label\" should be hidden"
            )
        }
    }

    private fun UIKitInstrumentedTest.verifyFullToolbarPresent() {
        verifyContextMenuItemsVisible(listOf("Cut", "Copy", "Paste", "Select All"))
    }
}
