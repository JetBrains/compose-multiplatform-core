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

package androidx.compose.ui.text.intl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Covers https://youtrack.jetbrains.com/issue/CMP-10359
class PlatformLocaleWebTest {

    @Test
    fun localeListFromLanguageTags_returnsFallbackWhenListIsEmpty() {
        val list = localeListFromLanguageTags(emptyList())
        assertEquals(1, list.size)
        assertEquals("en-001", list[0].toLanguageTag())
    }

    @Test
    fun localeListFromLanguageTags_returnsFallbackWhenAllTagsAreMalformed() {
        val list = localeListFromLanguageTags(listOf("!!bad!!", "not a tag"))
        assertEquals(1, list.size)
        assertEquals("en-001", list[0].toLanguageTag())
    }

    @Test
    fun localeListFromLanguageTags_dropsMalformedTagsButKeepsValidOnes() {
        val list = localeListFromLanguageTags(listOf("!!bad!!", "en-US", "de-DE"))
        assertEquals(2, list.size)
        assertEquals("en-US", list[0].toLanguageTag())
        assertEquals("de-DE", list[1].toLanguageTag())
    }

    @Test
    fun localeCurrent_doesNotThrow() {
        // The concrete value depends on the browser, but Locale.current must never throw or
        // return an empty list even if navigator.languages is missing or unusual.
        val current = Locale.current
        assertTrue(current.toLanguageTag().isNotEmpty())
    }
}
