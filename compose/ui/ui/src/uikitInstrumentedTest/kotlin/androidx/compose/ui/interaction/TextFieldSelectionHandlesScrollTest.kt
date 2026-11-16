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

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.selectionHandles
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.test.utils.wait
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class TextFieldSelectionHandlesScrollTest {

    @Test
    fun selectionHandlesPositionsAfterLazyColumnScroll() = runUIKitInstrumentedTest {
        val focusRequester = FocusRequester()
        setContent {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            LazyColumn(Modifier.fillMaxSize()) {
                items(100) { index ->
                    var text by remember { mutableStateOf("${index}1234") }
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        @Suppress("DEPRECATION")
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 22.sp),
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                platformImeOptions = PlatformImeOptions { usingNativeTextInput(false) }
                            ),
                            modifier = if (index == 3) {
                                Modifier
                                    .alignByBaseline()
                                    .focusRequester(focusRequester)
                                    .testTag(TextFieldTag)
                            } else {
                                Modifier.alignByBaseline()
                            },
                        )
                    }
                }
            }
        }

        // Show selection handles
        findNodeWithTag(TextFieldTag).multiTapCharacter(offset = 2, count = 2)
        waitForIdle()

        val initialHandles = assertNotNull(
            selectionHandles(),
            "expected the double tap on '11234' to show the selection handles",
        )
        val initialStartHandlePosition = initialHandles.start.grabPoint
        val initialEndHandlePosition = initialHandles.end.grabPoint

        touchDown(DpOffset(screenSize.center.x, LazyColumnScrollDistance))
            .dragBy(dy = -LazyColumnScrollDistance)
            .wait(50.milliseconds)
            .up()
        waitForIdle()
        touchDown(DpOffset(screenSize.center.x, 100.dp))
            .dragBy(dy = LazyColumnScrollDistance)
            .wait(50.milliseconds)
            .up()
        waitForIdle()

        val handles = assertNotNull(
            selectionHandles(),
            "the selection handles should be shown again after scrolling back",
        )
        assertEquals(
            initialStartHandlePosition,
            handles.start.grabPoint,
            "the start handle should be back at its initial position $initialStartHandlePosition",
        )
        assertEquals(
            initialEndHandlePosition,
            handles.end.grabPoint,
            "the end handle should be back at its initial position $initialEndHandlePosition",
        )
    }

    companion object {
        private const val TextFieldTag = "textField"
        private val LazyColumnScrollDistance = 300.dp
    }
}
