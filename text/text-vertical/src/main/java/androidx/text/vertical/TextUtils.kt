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

package androidx.text.vertical

import android.text.Spanned

/**
 * Iterates through spans of a specific type within a given range of a CharSequence.
 *
 * @param text The CharSequence
 * @param start The inclusive starting index.
 * @param end The exclusive ending index.
 * @param consumer A lambda function that will be called for each segment containing spans.
 */
internal inline fun <reified T> forEachSpan(
    text: CharSequence,
    start: Int,
    end: Int,
    crossinline consumer: (Int, Int, Array<T>) -> Unit,
) {
    if (text !is Spanned) {
        consumer(start, end, arrayOf())
        return
    }

    var spanI = start
    while (spanI < end) {
        val next = text.nextSpanTransition(spanI, end, T::class.java)
        val spans = text.getSpans(spanI, next, T::class.java)
        consumer(spanI, next, spans)
        spanI = next
    }
}

/**
 * Iterates over the code points within a specified range of a [CharSequence].
 *
 * @param text The CharSequence
 * @param start The inclusive starting index.
 * @param end The exclusive ending index.
 * @param consumer A lambda function that will be called for each code points.
 */
internal inline fun fotEachCodePoints(
    text: CharSequence,
    start: Int,
    end: Int,
    crossinline consumer: (Int, Int) -> Unit,
) {
    var i = start
    while (i < end) {
        val cp = Character.codePointAt(text, i)
        consumer(i, cp)
        i += Character.charCount(cp)
    }
}

/**
 * Extension function to iterate over paragraphs within a CharSequence.
 *
 * This function iterates through the specified range of the CharSequence, identifying paragraphs
 * based on newline characters ('\n'). For each paragraph found, it invokes the provided block with
 * the start and end indices of the paragraph.
 *
 * @param start The inclusive starting index.
 * @param end The exclusive ending index.
 * @param block A lambda function that takes two Int parameters representing the start and end
 *   indices of a paragraph.
 */
internal inline fun CharSequence.forEachParagraph(
    start: Int,
    end: Int,
    crossinline block: (Int, Int) -> Unit,
) {
    var paraStart = start
    while (paraStart < end) {
        var paraEnd = indexOf('\n', paraStart) // TODO support other paragraph separator
        if (paraEnd == -1 || paraEnd >= end) {
            paraEnd = end
        } else {
            // Include the paragraph separator in the current paragraph.
            paraEnd++
        }

        block(paraStart, paraEnd)

        paraStart = paraEnd
    }
}
