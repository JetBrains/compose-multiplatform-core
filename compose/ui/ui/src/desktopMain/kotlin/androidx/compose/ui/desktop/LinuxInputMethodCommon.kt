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

package androidx.compose.ui.desktop

import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.unit.DpRect
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Converts UTF-8 offset to UTF-16 offset.
 */
internal fun utf8OffsetToUtf16Offset(string: CharSequence, offset: UInt): UInt {
    if (offset == 0U) {
        return 0U
    }
    var utf8Offset = offset.toLong()
    var utf16Offset = 0U
    for (codePoint in string.codePoints()) {
        utf8Offset -= when {
            codePoint < 128 -> 1
            codePoint < 2048 -> 2
            codePoint < 65536 -> 3
            else -> 4
        }

        utf16Offset += 1U
        // Code points from the supplementary planes are encoded as a surrogate pair in utf-16,
        // meaning we'll have one extra utf-16 code unit for every code point in this range.
        if (codePoint >= 65536) utf16Offset += 1U

        if (utf8Offset <= 0) break
    }

    return utf16Offset
}

data class LinuxTextInputSurroundingText(
    val text: String,
    val cursorOffset: UInt,
    val selectionStartOffset: UInt,
) {
    internal fun convertByteOffsetsFromCursor(beforeLengthInBytes: UInt, afterLengthInBytes: UInt): Pair<UInt, UInt> {
        val beforeOffset = utf8OffsetToUtf16Offset(
            text.subSequence(max(0U, cursorOffset - beforeLengthInBytes).toInt(), cursorOffset.toInt())
                .reversed(),
            beforeLengthInBytes
        )
        val afterOffset = utf8OffsetToUtf16Offset(
            text.subSequence(
                cursorOffset.toInt(),
                min(text.length.toUInt(), cursorOffset + afterLengthInBytes).toInt()
            ),
            afterLengthInBytes
        )
        return Pair(beforeOffset, afterOffset)
    }
}

data class LinuxTextInputContext(
    val imeOptions: ImeOptions,
    val cursorRectangle: DpRect,
)

interface PlatformTextInputMethodRequestLinux : NativePlatformTextInputMethodRequest {
    fun commitText(text: String)

    fun handleTextChangedEvent(
        committedText: String?,
        composedText: String?,
        caretRangeInComposedText: TextRange?,
        deleteSurroundingText: Pair<UInt, UInt>?,
    )

    fun initialData(): Pair<LinuxTextInputContext, LinuxTextInputSurroundingText>?
    suspend fun waitForEditorStateChange(): Pair<LinuxTextInputContext, LinuxTextInputSurroundingText>
}

internal fun codepointFromOffset(s: String, offset: UInt): UShort {
    return s.codePointCount(0, offset.toInt()).toUShort()
}

class PlatformTextInputSessionLinux(
    coroutineScope: CoroutineScope,
    startInputMethod: (LinuxTextInputContext, LinuxTextInputSurroundingText) -> Unit,
    private val stopInputMethod: () -> Unit,
    private val onDataChanged: (LinuxTextInputContext, LinuxTextInputSurroundingText) -> Unit,
) : PlatformTextInputSessionScope<PlatformTextInputMethodRequestLinux>,
    CoroutineScope by coroutineScope {
    private val startInputMethodImpl = startInputMethod

    @Volatile
    var currentRequest: PlatformTextInputMethodRequestLinux? = null

    fun commitText(text: String) {
        currentRequest?.commitText(text)
    }

    override suspend fun startInputMethod(request: PlatformTextInputMethodRequestLinux): Nothing {
        currentRequest = request
        val job = launch {
            var previousContext: LinuxTextInputContext? = null
            var previousSurroundingText: LinuxTextInputSurroundingText? = null
            while (isActive) {
                val (context, surroundingText) = request.waitForEditorStateChange()
                if (context != previousContext || surroundingText != previousSurroundingText) {
                    previousContext = context
                    previousSurroundingText = surroundingText
                    onDataChanged(context, surroundingText)
                }
            }
        }
        try {
            val (context, surroundingText) = request.initialData()!!
            startInputMethodImpl(context, surroundingText)
            awaitCancellation()
        } finally {
            job.cancel()
            stopInputMethod()
            currentRequest = null
        }
    }
}
