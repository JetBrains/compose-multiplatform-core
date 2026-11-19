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

package androidx.compose.ui.platform

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSMakeRange
import platform.UIKit.UITextView

/**
 * The edit menu "Select" action ([selectTextNearCursor]) must select what it selects in
 * `UITextView`.
 */
class SelectActionTest {
    private val texts = listOf(
        "hello world",
        "hello  world",
        "hello \nworld",
        "hello\n  world",
        "hello\n\nworld",
        "  hello",
        "  \n  hello",
        "\nhello",
        "hello, world!",
        "it's 3.14 o'clock",
        "foo.bar e-mail",
        "...hello...",
        "a. b",
        "(hi) [there]",
        "hi\n,, there",
        "a ,, b",
        "Wait -- what? :) ok",
        "a\tb  \t c",
        "x\u00A0y",
        "abc123 456",
        "x  ",
        "   ",
        ", ,",
        "\n\n",
        "",
        "我在学习中文",
        "我在学习 中文。好的",
        "私は日本語を勉強しています。",
        "a \uD83D\uDC4D b",
    )

    private fun selectInTextView(text: String, cursor: Int): TextRange {
        val textView = UITextView(frame = CGRectMake(0.0, 0.0, 300.0, 300.0))
        textView.text = text
        textView.setSelectedRange(NSMakeRange(cursor.toULong(), 0u))
        textView.select(null)
        return textView.selectedRange.useContents {
            TextRange(location.toInt(), (location + length).toInt())
        }
    }

    private fun selectInComposeInput(text: String, cursor: Int): TextRange? {
        var selection: TextRange? = TextRange(cursor)
        val delegate = object : NativeTextEditingDelegate by DetachedTextEditingDelegate(text) {
            override fun getSelectedTextRange(): TextRange? = selection
            override fun setSelectedTextRange(range: TextRange?) {
                selection = range
            }
        }
        delegate.selectTextNearCursor()
        return selection
    }

    @Test
    fun matchesUITextView() {
        for (text in texts) {
            for (cursor in 0..text.length) {
                assertEquals(
                    selectInTextView(text, cursor),
                    selectInComposeInput(text, cursor),
                    "Select in '${text.escaped()}' at $cursor"
                )
            }
        }
    }

    @Test
    fun selectsWords() {
        assertEquals(TextRange(6, 11), selectInComposeInput("hello world", cursor = 8))
        assertEquals(TextRange(2, 4), selectInComposeInput("我在学习中文", cursor = 3))
        // Punctuation after the caret: the previous word.
        assertEquals(TextRange(5, 7), selectInComposeInput("我在学习 中文。", cursor = 7))
        // Spaces and punctuation between the previous word and the caret are selected too.
        assertEquals(TextRange(1, 5), selectInComposeInput("(hi) [there]", cursor = 5))
        // But not beyond the caret's paragraph.
        assertEquals(TextRange(6, 7), selectInComposeInput("hello\n  world", cursor = 7))
        // No word before the caret: up to the next word.
        assertEquals(TextRange(0, 3), selectInComposeInput("...hello", cursor = 0))
    }

    private fun String.escaped() = replace("\n", "\\n").replace("\t", "\\t")
}
