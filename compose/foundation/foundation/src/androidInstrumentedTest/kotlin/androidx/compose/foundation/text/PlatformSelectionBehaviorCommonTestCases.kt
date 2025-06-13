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

package androidx.compose.foundation.text

import android.content.Context
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.PlatformSelectionBehaviors
import androidx.compose.foundation.text.selection.PlatformSelectionBehaviorsFactory
import androidx.compose.foundation.text.selection.SelectedTextType
import androidx.compose.foundation.text.selection.gestures.util.longPress
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
abstract class PlatformSelectionBehaviorCommonTestCases : FocusedWindowTest {
    @get:Rule val rule = createComposeRule()
    internal val TAG = "SelectableText"

    internal val fontSize = 10.sp

    internal val defaultTextStyle = TextStyle(fontFamily = TEST_FONT_FAMILY, fontSize = fontSize)

    internal var testPlatformSelectionBehaviors: TestPlatformSelectionBehaviors? = null

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupClass() {
            ComposeFoundationFlags.isSmartSelectionEnabled = true
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            ComposeFoundationFlags.isSmartSelectionEnabled = false
        }
    }

    @Before
    fun setup() {
        testPlatformSelectionBehaviors = TestPlatformSelectionBehaviors()
        PlatformSelectionBehaviorsFactory =
            {
                coroutineContext,
                context: Context,
                selectionType: SelectedTextType,
                localeList: LocaleList? ->
                testPlatformSelectionBehaviors as PlatformSelectionBehaviors
            }
    }

    /** The composable component to be tested, which should be BTF1, BTF2 or SelectionContainer. */
    @Composable abstract fun Content(text: String, textStyle: TextStyle, modifier: Modifier)

    abstract val selection: TextRange

    @Test
    fun longPress_singleLine_callsSuggestSelectionForLongPressOrDoubleClick() {
        rule.setTextFieldTestContent {
            Content(
                text = "abc def ghi",
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(200.dp),
            )
        }

        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = 5 * fontSize.toPx(), y = fontSize.toPx() / 2))
            up()
        }

        rule.waitForIdle()

        assertThat(selection).isEqualTo(TextRange(4, 7))
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc def ghi")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(4, 7))
    }

    @Test
    fun longPress_multiline_callsSuggestSelectionForLongPressOrDoubleClick() {
        rule.setTextFieldTestContent {
            Content(
                text = "abc def ghi",
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(100.dp),
            )
        }

        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = 5 * fontSize.toPx(), y = fontSize.toPx() / 2))
            up()
        }

        rule.waitForIdle()

        assertThat(selection).isEqualTo(TextRange(4, 7))
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc def ghi")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(4, 7))
    }

    @Test
    fun longPress_dragToChangeSelection_notCallSuggestSelectionForLongPressOrDoubleClick() {
        rule.setTextFieldTestContent {
            Content(
                text = "abc def ghi",
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(200.dp),
            )
        }

        // Long press to select "abc" first and then drag to select " def",
        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = fontSize.toPx() * 2, y = fontSize.toPx() / 2))
            moveTo(Offset(x = fontSize.toPx() * 5, y = fontSize.toPx() / 2))
            up()
        }

        rule.waitForIdle()

        assertThat(selection).isEqualTo(TextRange(0, 7))
        assertThat(testPlatformSelectionBehaviors?.text).isNull()
        assertThat(testPlatformSelectionBehaviors?.selection).isNull()
    }

    @Test
    fun longPress_dragButNotChangeSelection_callSuggestSelectionForLongPressOrDoubleClick() {
        rule.setTextFieldTestContent {
            Content(
                text = "abc def ghi",
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(200.dp),
            )
        }

        // Long press to select "abc" first and then drag to the offset after character "c", the
        // selection shouldn't update.
        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = fontSize.toPx() * 2, y = fontSize.toPx() / 2))
            moveTo(Offset(x = fontSize.toPx() * 3, y = fontSize.toPx() / 2))
            up()
        }

        rule.waitForIdle()

        assertThat(selection).isEqualTo(TextRange(0, 3))
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc def ghi")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(0, 3))
    }

    @Test
    fun longPress_doesApplySuggestedRange() {
        val state = TextFieldState("abc def ghi")
        val suggestedSelection = TextRange(1, 5)
        testPlatformSelectionBehaviors?.suggestedSelection = suggestedSelection

        rule.setTextFieldTestContent {
            BasicTextField(
                state = state,
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(200.dp),
            )
        }

        // Long press to select "abc".
        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = fontSize.toPx() * 2, y = fontSize.toPx() / 2))
            up()
        }

        rule.waitForIdle()

        assertThat(state.selection).isEqualTo(suggestedSelection)
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc def ghi")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(0, 3))
    }
}

internal class TestPlatformSelectionBehaviors : PlatformSelectionBehaviors {
    var text: String? = null
    var selection: TextRange? = null

    var suggestedSelection: TextRange? = null

    override suspend fun suggestSelectionForLongPressOrDoubleClick(
        text: CharSequence,
        selection: TextRange,
    ): TextRange? {
        this.text = text.toString()
        this.selection = selection
        return suggestedSelection
    }
}
