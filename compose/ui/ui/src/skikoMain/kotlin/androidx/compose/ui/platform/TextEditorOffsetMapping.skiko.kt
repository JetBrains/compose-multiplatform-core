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
import androidx.compose.ui.text.TextRange

/**
 * Provides bidirectional offset mapping between original and transformed text.
 */
@ExperimentalComposeUiApi
interface TextEditorOffsetMapping {
    /**
     * Maps a [range] in the original text to the corresponding range in the transformed text.
     */
    fun mapToTransformed(range: TextRange): TextRange

    /**
     * Maps a [range] in the transformed text to the corresponding range in the original text.
     */
    fun mapFromTransformed(range: TextRange): TextRange

    @ExperimentalComposeUiApi
    companion object {
        /** The mapping for the text that isn't transformed. */
        val Identity: TextEditorOffsetMapping = object : TextEditorOffsetMapping {
            override fun mapToTransformed(range: TextRange): TextRange = range

            override fun mapFromTransformed(range: TextRange): TextRange = range
        }
    }
}

internal fun TextEditorOffsetMapping.mapToTransformed(offset: Int): Int =
    mapToTransformed(TextRange(offset)).min

internal fun TextEditorOffsetMapping.mapFromTransformed(offset: Int): Int =
    mapFromTransformed(TextRange(offset)).min
