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

package androidx.compose.foundation.text.input.internal.selection

import android.content.Context
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.FocusedWindowTest
import androidx.compose.foundation.text.TEST_FONT_FAMILY
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.internal.CodepointTransformation
import androidx.compose.foundation.text.selection.PlatformSelectionBehaviors
import androidx.compose.foundation.text.selection.PlatformSelectionBehaviorsFactory
import androidx.compose.foundation.text.selection.SelectedTextType
import androidx.compose.foundation.text.selection.gestures.util.longPress
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
@OptIn(ExperimentalFoundationApi::class)
class TextFieldPlatformSelectionBehaviorsTest : FocusedWindowTest {
    @get:Rule val rule = createComposeRule()
    private val TAG = "BasicTextField"

    private val fontSize = 10.sp

    private val defaultTextStyle = TextStyle(fontFamily = TEST_FONT_FAMILY, fontSize = fontSize)

    private var testPlatformSelectionBehaviors: TestPlatformSelectionBehaviors? = null

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

    @Test
    fun longPress_singleLine_callsSuggestSelectionForLongPressOrDoubleClick() {
        val state = TextFieldState("abc def ghi")

        rule.setTextFieldTestContent {
            BasicTextField(
                state = state,
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(200.dp),
            )
        }

        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = 5 * fontSize.toPx(), y = fontSize.toPx() / 2))
            up()
        }

        assertThat(state.selection).isEqualTo(TextRange(4, 7))
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc def ghi")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(4, 7))
    }

    @Test
    fun longPress_multiline_callsSuggestSelectionForLongPressOrDoubleClick() {
        val state = TextFieldState("abc def ghi")

        rule.setTextFieldTestContent {
            BasicTextField(
                state = state,
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(100.dp),
            )
        }

        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = 5 * fontSize.toPx(), y = fontSize.toPx() / 2))
            up()
        }

        assertThat(state.selection).isEqualTo(TextRange(4, 7))
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc def ghi")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(4, 7))
    }

    @Test
    fun longPress_onEmptyRegion_notCallSuggestSelectionForLongPressOrDoubleClick() {
        val state = TextFieldState("abc def")

        rule.setTextFieldTestContent {
            BasicTextField(
                state = state,
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(100.dp),
            )
        }

        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(centerRight)
            up()
        }

        // Cursor at the end.
        assertThat(state.selection).isEqualTo(TextRange(7))
        assertThat(testPlatformSelectionBehaviors?.text).isNull()
        assertThat(testPlatformSelectionBehaviors?.selection).isNull()
    }

    @Test
    fun longPress_dragToChangeSelection_notCallSuggestSelectionForLongPressOrDoubleClick() {
        val state = TextFieldState("abc def ghi")

        rule.setTextFieldTestContent {
            BasicTextField(
                state = state,
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

        assertThat(state.selection).isEqualTo(TextRange(0, 7))
        assertThat(testPlatformSelectionBehaviors?.text).isNull()
        assertThat(testPlatformSelectionBehaviors?.selection).isNull()
    }

    @Test
    fun longPress_dragButNotChangeSelection_callSuggestSelectionForLongPressOrDoubleClick() {
        val state = TextFieldState("abc def ghi")

        rule.setTextFieldTestContent {
            BasicTextField(
                state = state,
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

        assertThat(state.selection).isEqualTo(TextRange(0, 3))
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

        // Long press to select "abc" first and then drag to the offset after character "c", the
        // selection shouldn't update.
        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = fontSize.toPx() * 2, y = fontSize.toPx() / 2))
            moveTo(Offset(x = fontSize.toPx() * 3, y = fontSize.toPx() / 2))
            up()
        }

        assertThat(state.selection).isEqualTo(suggestedSelection)
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc def ghi")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(0, 3))
    }

    @Test
    fun longPress_withOutputTransformation() {
        val state = TextFieldState("xxx")

        rule.setTextFieldTestContent {
            BasicTextField(
                state = state,
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(200.dp),
                outputTransformation =
                    object : OutputTransformation {
                        override fun TextFieldBuffer.transformOutput() {
                            insert(0, "abc ")
                            insert(length, " def")
                        }
                    },
            )
        }

        // The visual text is "abc xxx def"
        // Long press to select "xxx".
        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = fontSize.toPx() * 5, y = fontSize.toPx() / 2))
            up()
        }

        assertThat(state.selection).isEqualTo(TextRange(0, 3))
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc xxx def")
        // The prefix "abc " is also considered selected by OutputTransformation.
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(0, 7))
    }

    @Test
    fun longPress_withCodePointTransformation() {
        val state = TextFieldState("abc xxx def")

        rule.setTextFieldTestContent {
            BasicTextField(
                state = state,
                textStyle = defaultTextStyle,
                modifier = Modifier.testTag(TAG).width(200.dp),
                codepointTransformation =
                    object : CodepointTransformation {
                        override fun transform(codepointIndex: Int, codepoint: Int): Int =
                            if (codepoint == 'x'.code) '*'.code else codepoint
                    },
            )
        }

        // The visual text is "abc xxx def"
        // Long press to select "xxx".
        rule.onNodeWithTag(TAG).performTouchInput {
            longPress(Offset(x = fontSize.toPx() * 5, y = fontSize.toPx() / 2))
            up()
        }

        assertThat(state.selection).isEqualTo(TextRange(4, 7))
        assertThat(testPlatformSelectionBehaviors?.text).isEqualTo("abc *** def")
        assertThat(testPlatformSelectionBehaviors?.selection).isEqualTo(TextRange(4, 7))
    }
}

private class TestPlatformSelectionBehaviors : PlatformSelectionBehaviors {
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
