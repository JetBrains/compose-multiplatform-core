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

package androidx.compose.ui.text

/**
 * Word segmentation of the platform, used instead of the ICU bundled with Skia.
 */
internal interface PlatformSegmenter {
    /**
     * Returns the word segment of [text] containing the character at [offset], with the semantics
     * of ICU's word break iterator: words, whitespace runs and punctuation are separate segments.
     *
     * [offset] must be in `[0, text.length)`.
     */
    fun segmentAt(text: String, offset: Int): TextRange
}

/**
 * Returns the [PlatformSegmenter] for the calling thread, or `null` to use Skia's word
 * boundaries.
 */
internal expect fun platformWordSegmenter(): PlatformSegmenter?
