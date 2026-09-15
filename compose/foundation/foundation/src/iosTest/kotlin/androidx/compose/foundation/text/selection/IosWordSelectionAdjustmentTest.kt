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

package androidx.compose.foundation.text.selection

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosWordSelectionAdjustmentTest {
    @Test
    fun chineseUsesLinguisticWordBoundary() {
        assertEquals(TextRange(4, 6), iosWordBoundary("我在学习中文", 5))
    }

    @Test
    fun japaneseUsesLinguisticWordBoundary() {
        assertEquals(TextRange(3, 5), iosWordBoundary("これは日本語です", 4))
    }

    @Test
    fun cjkAfterEmojiUsesUtf16Offsets() {
        assertEquals(TextRange(6, 8), iosWordBoundary("😀我在学习中文", 7))
    }

    @Test
    fun latinUsesLinguisticWordBoundary() {
        assertEquals(TextRange(10, 18), iosWordBoundary("this is a language", 12))
    }

    @Test
    fun whitespaceFallsBackToLayoutBoundary() {
        assertNull(iosWordBoundary("word word", 4))
    }
}
