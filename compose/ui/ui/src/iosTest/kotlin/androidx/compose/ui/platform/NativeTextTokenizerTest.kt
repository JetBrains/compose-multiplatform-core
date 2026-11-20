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

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.IosComposeUiTextFlags
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.UIKit.UIResponder
import platform.UIKit.UITextGranularity
import platform.UIKit.UITextStorageDirectionBackward
import platform.UIKit.UITextStorageDirectionForward

/**
 * The [TextInputStringTokenizer] backed by the iOS text tokenizer
 * ([IosComposeUiTextFlags.isNativeTextTokenizerEnabled]).
 */
@OptIn(ExperimentalTextApi::class)
class NativeTextTokenizerTest {
    private val initialFlagValue = IosComposeUiTextFlags.isNativeTextTokenizerEnabled

    @AfterTest
    fun tearDown() {
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = initialFlagValue
    }

    private fun tokenizer(text: String) =
        TextInputStringTokenizer(textInput = UIResponder()) { text }

    private fun TextInputStringTokenizer.next(position: Int, granularity: UITextGranularity) =
        (positionFromPosition(
            TextInputPosition(position), granularity, UITextStorageDirectionForward
        ) as TextInputPosition).position

    private fun TextInputStringTokenizer.previous(position: Int, granularity: UITextGranularity) =
        (positionFromPosition(
            TextInputPosition(position), granularity, UITextStorageDirectionBackward
        ) as TextInputPosition).position

    private fun TextInputStringTokenizer.isBoundary(
        position: Int,
        granularity: UITextGranularity
    ) = isPositionAtBoundary(TextInputPosition(position), granularity, UITextStorageDirectionForward)

    @Test
    fun wordGranularitySegmentsChinese() {
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = true
        val tokenizer = tokenizer("我在学习中文")
        val word = UITextGranularity.UITextGranularityWord

        assertEquals(4, tokenizer.next(2, word))
        assertEquals(4, tokenizer.next(3, word))
        assertEquals(6, tokenizer.next(4, word))
        assertEquals(6, tokenizer.next(6, word))
        assertEquals(2, tokenizer.previous(4, word))
        assertEquals(2, tokenizer.previous(3, word))
        assertEquals(0, tokenizer.previous(0, word))
        assertTrue(tokenizer.isBoundary(2, word))
        assertFalse(tokenizer.isBoundary(3, word))
        assertTrue(tokenizer.isBoundary(6, word))
        assertFalse(tokenizer.isBoundary(7, word))
    }

    @Test
    fun sentenceAndLineGranularities() {
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = true
        val text = "Hello world.  How are you?"
        val tokenizer = tokenizer(text)
        val sentence = UITextGranularity.UITextGranularitySentence
        val line = UITextGranularity.UITextGranularityLine

        // Segments include the trailing whitespace.
        assertEquals(text.indexOf("How"), tokenizer.next(0, sentence))
        assertEquals(text.length, tokenizer.next(text.indexOf("How"), sentence))
        assertEquals(0, tokenizer.previous(text.indexOf("How"), sentence))
        assertTrue(tokenizer.isBoundary(text.indexOf("How"), sentence))
        assertFalse(tokenizer.isBoundary(text.indexOf("world"), sentence))

        assertEquals(text.indexOf("world"), tokenizer.next(0, line))
        assertEquals(text.indexOf("world"), tokenizer.previous(text.indexOf("How"), line))
        assertTrue(tokenizer.isBoundary(text.indexOf("are"), line))
        assertFalse(tokenizer.isBoundary(text.indexOf("are") + 1, line))
    }

    @Test
    fun characterGranularityKeepsGraphemeClusters() {
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = true
        val family = "👨‍👩‍👧"
        val text = "a$family\r\nb"
        val tokenizer = tokenizer(text)
        val character = UITextGranularity.UITextGranularityCharacter
        val crlf = text.indexOf('\r')

        assertEquals(1, tokenizer.next(0, character))
        assertEquals(crlf, tokenizer.next(1, character))
        assertEquals(1, tokenizer.previous(crlf, character))
        assertFalse(tokenizer.isBoundary(2, character))
        // CR LF is a single character.
        assertEquals(crlf + 2, tokenizer.next(crlf, character))
        assertEquals(crlf, tokenizer.previous(crlf + 2, character))
        assertFalse(tokenizer.isBoundary(crlf + 1, character))
    }

    @Test
    fun paragraphGranularity() = forBothTokenizers {
        val text = "first line\nsecond line"
        val tokenizer = tokenizer(text)
        val paragraph = UITextGranularity.UITextGranularityParagraph
        val newline = text.indexOf('\n')

        assertEquals(newline, tokenizer.next(2, paragraph))
        assertEquals(newline, tokenizer.next(newline, paragraph))
        assertEquals(text.length, tokenizer.next(newline + 1, paragraph))
        assertEquals(newline + 1, tokenizer.previous(text.length, paragraph))
        assertEquals(0, tokenizer.previous(newline, paragraph))
        assertTrue(tokenizer.isBoundary(newline, paragraph))
        assertTrue(tokenizer.isBoundary(newline + 1, paragraph))
        assertFalse(tokenizer.isBoundary(2, paragraph))
        // Out of range positions are clamped.
        assertEquals(text.length, tokenizer.next(text.length + 3, paragraph))
        assertFalse(tokenizer.isBoundary(text.length + 1, paragraph))
    }

    @Test
    fun latinWordsMatchIcu() {
        val text = "Hello, world! It's 3.14 o'clock now."
        val word = UITextGranularity.UITextGranularityWord

        fun boundaries(): List<Any> {
            val tokenizer = tokenizer(text)
            return (0..text.length).map {
                Triple(
                    tokenizer.next(it, word),
                    tokenizer.previous(it, word),
                    tokenizer.isBoundary(it, word)
                )
            }
        }

        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = false
        val icu = boundaries()
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = true
        val native = boundaries()

        assertEquals(icu, native)
    }

    private fun forBothTokenizers(block: () -> Unit) {
        for (isNative in listOf(true, false)) {
            IosComposeUiTextFlags.isNativeTextTokenizerEnabled = isNative
            block()
        }
    }
}
