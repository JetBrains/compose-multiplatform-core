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

import androidx.compose.ui.uikit.utils.CMPTextSegmentUnit
import androidx.compose.ui.uikit.utils.CMPTextSegmenter
import kotlin.native.concurrent.ThreadLocal
import kotlinx.cinterop.useContents

@OptIn(ExperimentalTextApi::class)
internal actual fun platformWordSegmenter(): PlatformSegmenter? =
    if (IosComposeUiTextFlags.isNativeTextTokenizerEnabled) NativeWordSegmenter else null

/**
 * Word segmentation with the iOS text tokenizer, which, unlike the ICU bundled with Skia, has the
 * dictionaries for CJK word segmentation.
 *
 * Thread-local, because the native segmenter is not thread-safe. Sharing it between paragraphs
 * is much cheaper than a tokenizer per paragraph: rebinding it to another text is an order of
 * magnitude faster than creating a new one, and repeated queries on the same text are cheap.
 */
@ThreadLocal
private object NativeWordSegmenter : PlatformSegmenter {
    private val segmenter = CMPTextSegmenter(CMPTextSegmentUnit.CMPTextSegmentUnitWord)

    override fun segmentAt(text: String, offset: Int): TextRange =
        segmenter.segmentRangeAtIndex(offset.toLong(), text).useContents {
            TextRange(start = location.toInt(), end = (location + length).toInt())
        }
}
