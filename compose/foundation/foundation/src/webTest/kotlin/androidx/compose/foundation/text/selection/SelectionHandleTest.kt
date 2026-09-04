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

package androidx.compose.foundation.text.selection

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.Handle
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertTrue

private const val TextFieldTag = "textField"

private fun isSelectionHandle(handle: Handle) =
    SemanticsMatcher("is $handle") { node ->
        node.config.getOrNull(SelectionHandleInfoKey)?.handle == handle
    }

/**
 * Selection handles are hosted in a [androidx.compose.ui.window.Popup] that captures the gesture,
 * but consumes nothing until a drag starts. On web the "did Compose consume this touch?" answer
 * decides whether the corresponding touch events are `preventDefault`ed; when they aren't, the
 * browser's synthetic mouse events move DOM focus off the backing input and hide the virtual
 * keyboard. See https://youtrack.jetbrains.com/issue/CMP-10621
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class SelectionHandleTest {

    @Suppress("INVISIBLE_REFERENCE")
    @Test
    fun tapOnSelectionHandleIsConsumed() = runSkikoComposeUiTest {
        selectedTextField()

        val handleCenter = onNode(isSelectionHandle(Handle.SelectionStart), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center

        val press = scene.sendTouchEvent(PointerEventType.Press, handleCenter, pressed = true)
        val release = scene.sendTouchEvent(PointerEventType.Release, handleCenter, pressed = false)

        assertTrue(press.anyChangeConsumed, "press on a selection handle must be consumed")
        assertTrue(release.anyChangeConsumed, "release on a selection handle must be consumed")
    }

    /** Guards the [androidx.compose.ui.input.pointer.PointerEventPass.Final] detail: consuming in
     *  an earlier pass would cancel the handle's own drag detection. */
    @Test
    fun selectionHandleIsStillDraggable() = runSkikoComposeUiTest {
        val state = selectedTextField()

        onNode(isSelectionHandle(Handle.SelectionEnd), useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
            up()
        }
        waitForIdle()

        assertTrue(state.selection.end > 5, "dragging the end handle must extend the selection")
    }

    /** Focuses the field with touch (handles only show in touch mode) and selects "hello". */
    private fun SkikoComposeUiTest.selectedTextField(): TextFieldState {
        val state = TextFieldState("hello world")
        setContent {
            SetInitialTouchInputMode()
            BasicTextField(state = state, modifier = Modifier.testTag(TextFieldTag).fillMaxWidth())
        }
        onNodeWithTag(TextFieldTag).performTouchInput {
            down(center)
            up()
        }
        waitForIdle()
        onNodeWithTag(TextFieldTag).assertIsFocused()

        state.edit { selection = TextRange(0, 5) }
        waitForIdle()
        onAllNodes(SemanticsMatcher.keyIsDefined(SelectionHandleInfoKey)).assertCountEquals(2)

        return state
    }

    /**
     * Selection handles are only composed while `TextFieldSelectionState.isInTouchMode`, which
     * `TextFieldDecoratorModifier.applyCurrentInputMode` resets on focus unless the current input
     * mode is [InputMode.Touch]. The test harness never requests it: `RootNodeOwner.onPointerInput`
     * only does so for events with a non-null button, unlike the web platform wiring that requests
     * it for every touch event.
     */
    @Composable
    private fun SetInitialTouchInputMode() {
        val inputModeManager = LocalInputModeManager.current
        LaunchedEffect(inputModeManager) {
            inputModeManager.requestInputMode(InputMode.Touch)
        }
    }
}

@OptIn(InternalComposeUiApi::class)
private fun ComposeScene.sendTouchEvent(
    eventType: PointerEventType,
    position: Offset,
    pressed: Boolean,
) = sendPointerEvent(
    eventType = eventType,
    pointers = listOf(ComposeScenePointer(PointerId(1), position, pressed, PointerType.Touch)),
)