/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.material3

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class LabelTest {

    // Regression test for https://youtrack.jetbrains.com/issue/CMP-8708
    // When a Slider is dragged by its thumb, the thumb's hoverable emits a HoverInteraction.Exit
    // as the pointer leaves the thumb bounds *while the drag is still in progress*. The label must
    // stay visible as long as a press or drag interaction is still active, instead of being
    // dismissed by that mid-drag hover exit.
    @Test
    fun label_staysVisible_whenHoverExitsWhileStillPressedOrDragging() = runComposeUiTest {
        val interactionSource = MutableInteractionSource()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            Label(
                label = { Text(text = "label", modifier = Modifier.testTag("label")) },
                interactionSource = interactionSource,
            ) {
                Box(Modifier.size(48.dp).testTag("anchor"))
            }
        }

        // The label is hidden until the anchor is interacted with.
        onNodeWithTag("label").assertDoesNotExist()

        // Emit the exact interaction sequence produced when dragging a Slider thumb: hover the
        // thumb, press, start dragging, then exit the hover while the drag is ongoing.
        val hoverEnter = HoverInteraction.Enter()
        scope.launch {
            interactionSource.emit(hoverEnter)
            interactionSource.emit(PressInteraction.Press(Offset.Zero))
            interactionSource.emit(DragInteraction.Start())
            interactionSource.emit(HoverInteraction.Exit(hoverEnter))
        }
        waitForIdle()

        // A press and a drag are still active, so the label must remain visible.
        onNodeWithTag("label").assertIsDisplayed()
    }
}
