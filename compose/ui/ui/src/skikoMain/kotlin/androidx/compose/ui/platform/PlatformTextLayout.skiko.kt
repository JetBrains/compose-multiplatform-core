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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntSize

/**
 * The geometry of the text of a text field, as the platform input method reads it: where the
 * lines and the caret are and which character is at a position, in the coordinates of the text.
 *
 * The default implementation, `PlatformTextLayout(textLayoutResult)`, proxies to a
 * [TextLayoutResult]. A text field whose layout is not described by a [TextLayoutResult]
 * implements this interface.
 *
 * @see PlatformTextInputMethodRequest.textLayout
 */
@ExperimentalComposeUiApi
interface PlatformTextLayout {
    /** The size of the text, in pixels. */
    val size: IntSize

    /** The length of the text that was laid out. */
    val textLength: Int

    /** The number of lines. */
    val lineCount: Int

    /** The rectangle of the caret at [offset]. */
    fun getCursorRect(offset: Int): Rect

    /** The offset of the character closest to [position]. */
    fun getOffsetForPosition(position: Offset): Int

    /** The range of the word at [offset]. */
    fun getWordBoundary(offset: Int): TextRange

    /** The line that holds [offset]. */
    fun getLineForOffset(offset: Int): Int

    /** The offset of the first character of the line [lineIndex]. */
    fun getLineStart(lineIndex: Int): Int

    /** The offset after the last character of the line [lineIndex]. */
    fun getLineEnd(lineIndex: Int): Int

    /** The left edge of the line [lineIndex]. */
    fun getLineLeft(lineIndex: Int): Float

    /** The right edge of the line [lineIndex]. */
    fun getLineRight(lineIndex: Int): Float

    /** The top of the line [lineIndex]. */
    fun getLineTop(lineIndex: Int): Float

    /** The bottom of the line [lineIndex]. */
    fun getLineBottom(lineIndex: Int): Float

    /**
     * The horizontal position of the caret at [offset]. At the boundary between a left-to-right
     * and a right-to-left run, [usePrimaryDirection] picks the position for the direction of the
     * paragraph.
     */
    fun getHorizontalPosition(offset: Int, usePrimaryDirection: Boolean): Float
}

/** Creates the default [PlatformTextLayout], which proxies every query to [textLayoutResult]. */
@ExperimentalComposeUiApi
fun PlatformTextLayout(textLayoutResult: TextLayoutResult): PlatformTextLayout =
    TextLayoutResultPlatformTextLayout(textLayoutResult)

@OptIn(ExperimentalComposeUiApi::class)
private class TextLayoutResultPlatformTextLayout(
    private val textLayoutResult: TextLayoutResult
) : PlatformTextLayout {
    override val size: IntSize
        get() = textLayoutResult.size

    override val textLength: Int
        get() = textLayoutResult.layoutInput.text.length

    override val lineCount: Int
        get() = textLayoutResult.lineCount

    override fun getCursorRect(offset: Int): Rect = textLayoutResult.getCursorRect(offset)

    override fun getOffsetForPosition(position: Offset): Int =
        textLayoutResult.getOffsetForPosition(position)

    override fun getWordBoundary(offset: Int): TextRange = textLayoutResult.getWordBoundary(offset)

    override fun getLineForOffset(offset: Int): Int = textLayoutResult.getLineForOffset(offset)

    override fun getLineStart(lineIndex: Int): Int = textLayoutResult.getLineStart(lineIndex)

    override fun getLineEnd(lineIndex: Int): Int = textLayoutResult.getLineEnd(lineIndex)

    override fun getLineLeft(lineIndex: Int): Float = textLayoutResult.getLineLeft(lineIndex)

    override fun getLineRight(lineIndex: Int): Float = textLayoutResult.getLineRight(lineIndex)

    override fun getLineTop(lineIndex: Int): Float = textLayoutResult.getLineTop(lineIndex)

    override fun getLineBottom(lineIndex: Int): Float = textLayoutResult.getLineBottom(lineIndex)

    override fun getHorizontalPosition(offset: Int, usePrimaryDirection: Boolean): Float =
        textLayoutResult.getHorizontalPosition(offset, usePrimaryDirection)
}
