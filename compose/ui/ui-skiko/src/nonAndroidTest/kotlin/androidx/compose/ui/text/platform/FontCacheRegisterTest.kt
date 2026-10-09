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

package androidx.compose.ui.text.platform

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.InternalTextApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.test.IgnoreJsTarget
import kotlinx.test.IgnoreWasmTarget
import org.jetbrains.skia.FontStyle as SkFontStyle
import org.jetbrains.skia.Typeface as SkTypeface

@OptIn(ExperimentalTextApi::class, InternalTextApi::class)
@IgnoreJsTarget
@IgnoreWasmTarget
class FontCacheRegisterTest {

    @Test
    fun register_returnsResultWithTypeface() {
        val cache = FontCache()
        val typeface = SkTypeface.makeEmpty()
        val result = cache.register(typeface, "test-key")
        assertNotNull(result.typeface)
    }

    @Test
    fun register_returnsResultWithCorrectAlias() {
        val cache = FontCache()
        val typeface = SkTypeface.makeEmpty()
        val result = cache.register(typeface, "my-unique-key")
        assertEquals(1, result.aliases.size)
        assertEquals("my-unique-key", result.aliases.first())
    }

    @Test
    fun register_sameKey_reusesCachedTypeface() {
        val cache = FontCache()
        val typeface1 = SkTypeface.makeEmpty()
        val typeface2 = SkTypeface.makeEmpty()

        val result1 = cache.register(typeface1, "same-key")
        val result2 = cache.register(typeface2, "same-key")

        assertSame(result1.typeface, result2.typeface)
    }

    @Test
    fun register_differentKeys_storesSeparateTypefaces() {
        val cache = FontCache()
        val typeface1 = SkTypeface.makeEmpty()
        val typeface2 = SkTypeface.makeEmpty()

        val result1 = cache.register(typeface1, "key-1")
        val result2 = cache.register(typeface2, "key-2")

        assertEquals("key-1", result1.aliases.first())
        assertEquals("key-2", result2.aliases.first())
    }

    @Test
    fun register_typefaceIsUsableInFontCollection() {
        val cache = FontCache()
        val typeface = SkTypeface.makeEmpty()
        val key = "registered-font-key"

        cache.register(typeface, key)

        val found = cache.fonts.findTypefaces(arrayOf(key), SkFontStyle.NORMAL)
        assertNotNull(found)
        assertTrue(found.isNotEmpty(), "Registered typeface should be findable in FontCollection")
    }

    @Test
    fun register_multipleCallsSameKey_onlyRegistersOnce() {
        val cache = FontCache()
        val typeface = SkTypeface.makeEmpty()
        val key = "dedup-key"

        val result1 = cache.register(typeface, key)
        val result2 = cache.register(typeface, key)
        val result3 = cache.register(typeface, key)

        assertSame(result1.typeface, result2.typeface)
        assertSame(result2.typeface, result3.typeface)
    }

    @Test
    fun put_doesNotRegisterInFontCollection() {
        val cache = FontCache()
        val typeface = SkTypeface.makeEmpty()
        val key = "unvaried-only-key"

        val stored = cache.put(key, typeface)
        assertSame(typeface, stored)
        assertSame(typeface, cache.get(key))

        // Default font managers may still resolve *some* face for an unknown family name, so
        // assert our exact instance is not registered under the unvaried key.
        val found = cache.fonts.findTypefaces(arrayOf(key), SkFontStyle.NORMAL)
        val registeredOurFace = found?.any { it === typeface } == true
        assertTrue(
            !registeredOurFace,
            "put() must not register the typeface in FontCollection"
        )
    }

    @Test
    fun get_returnsNullWhenMissing() {
        val cache = FontCache()
        assertNull(cache.get("missing"))
    }

    @Test
    fun put_sameKey_reusesCachedTypeface() {
        val cache = FontCache()
        val typeface1 = SkTypeface.makeEmpty()
        val typeface2 = SkTypeface.makeEmpty()
        assertSame(typeface1, cache.put("k", typeface1))
        assertSame(typeface1, cache.put("k", typeface2))
        assertSame(typeface1, cache.get("k"))
    }
}
