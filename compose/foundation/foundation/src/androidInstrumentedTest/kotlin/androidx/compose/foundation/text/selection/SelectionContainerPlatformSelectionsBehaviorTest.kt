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

package androidx.compose.foundation.text.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.PlatformSelectionBehaviorCommonTestCases
import androidx.compose.foundation.text.selection.gestures.util.longPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class SelectionContainerPlatformSelectionsBehaviorTest() :
    PlatformSelectionBehaviorCommonTestCases() {
    private var _selection: MutableState<Selection?>? = null

    @Composable
    override fun Content(text: String, textStyle: TextStyle, modifier: Modifier) {
        var selection by remember { mutableStateOf<Selection?>(null).also { _selection = it } }
        SelectionContainer(selection = selection, onSelectionChange = { selection = it }) {
            BasicText(text = text, modifier = modifier, style = textStyle)
        }
    }

    override val selection: TextRange
        get() {
            val currentSelection = _selection?.value ?: return TextRange.Zero
            // Only one BasicText is in SelectionContainer for relevant tests.
            return TextRange(currentSelection.start.offset, currentSelection.end.offset)
        }

    @Test
    fun longPress_multipleBasicText_callSuggestSelectionForLongPressOrDoubleClick() {
        var selection by mutableStateOf<Selection?>(null)

        rule.setTextFieldTestContent {
            SelectionContainer(selection = selection, onSelectionChange = { selection = it }) {
                Column {
                    BasicText(text = "abc def", style = defaultTextStyle)
                    BasicText(
                        text = "ghi",
                        modifier = Modifier.testTag(TAG),
                        style = defaultTextStyle,
                    )
                    BasicText(text = "jkl mno", style = defaultTextStyle)
                }
            }
        }

        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(center)
            up()
        }

        rule.waitForIdle()

        assertThat(selection!!.start.offset).isEqualTo(0)
        assertThat(selection!!.end.offset).isEqualTo(3)
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("ghi")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(0, 3))
    }

    @Test
    fun longPress_multipleBasicText_doesApplySuggestedRange() {
        var selection by mutableStateOf<Selection?>(null)

        rule.setTextFieldTestContent {
            SelectionContainer(selection = selection, onSelectionChange = { selection = it }) {
                Column {
                    BasicText(
                        text = "abc def",
                        modifier = Modifier.testTag(TAG),
                        style = defaultTextStyle,
                    )
                    BasicText(text = "ghi", style = defaultTextStyle)
                    BasicText(text = "jkl mno", style = defaultTextStyle)
                }
            }
        }

        testPlatformSelectionBehaviors?.suggestedSelection = TextRange(0, 7)

        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = fontSize.toPx() * 5, y = fontSize.toPx() / 2))
            up()
        }

        rule.waitForIdle()

        assertThat(selection!!.start.offset).isEqualTo(0)
        assertThat(selection!!.end.offset).isEqualTo(7)
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc def")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(4, 7))
    }
}
