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

package androidx.compose.foundation.text

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterLevelCursorPlacementTest {
    @Test
    fun cjkPunctuation_requiresCharacterLevelCursorPlacement() {
        val text = "a\u3001\u3002\uFF01z"

        assertTrue(text.requiresCharacterLevelCursorPlacement(1))
        assertTrue(text.requiresCharacterLevelCursorPlacement(2))
        assertTrue(text.requiresCharacterLevelCursorPlacement(3))
    }

    @Test
    fun cjkScriptsAndFullWidthForms_requireCharacterLevelCursorPlacement() {
        assertTrue("\u4E2D".requiresCharacterLevelCursorPlacement(0))
        assertTrue("\u3042".requiresCharacterLevelCursorPlacement(0))
        assertTrue("\u30A2".requiresCharacterLevelCursorPlacement(0))
        assertTrue("\uD55C".requiresCharacterLevelCursorPlacement(0))
        assertTrue("\uFF21".requiresCharacterLevelCursorPlacement(0))
        assertTrue("\uD840\uDC00".requiresCharacterLevelCursorPlacement(0))
    }

    @Test
    fun ideographicSpace_doesNotRequireCharacterLevelCursorPlacement() {
        assertFalse("\u3000".requiresCharacterLevelCursorPlacement(0))
    }

    @Test
    fun latinAsciiPunctuationAndEmoji_keepCupertinoWordPlacement() {
        assertFalse("a".requiresCharacterLevelCursorPlacement(0))
        assertFalse(",".requiresCharacterLevelCursorPlacement(0))
        assertFalse("\uD83D\uDE42".requiresCharacterLevelCursorPlacement(0))
    }

    @Test
    fun invalidIndex_doesNotRequireCharacterLevelCursorPlacement() {
        assertFalse("\u4E2D".requiresCharacterLevelCursorPlacement(-1))
        assertFalse("\u4E2D".requiresCharacterLevelCursorPlacement(1))
    }
}
