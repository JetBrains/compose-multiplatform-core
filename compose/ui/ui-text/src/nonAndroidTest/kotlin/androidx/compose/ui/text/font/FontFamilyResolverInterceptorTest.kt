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

package androidx.compose.ui.text.font

import androidx.compose.ui.InternalComposeUiApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalComposeUiApi::class)
class FontFamilyResolverInterceptorTest {

    private object NoopFontLoader : PlatformFontLoader {
        override fun loadBlocking(font: Font): Any? = null

        override suspend fun awaitLoad(font: Font): Any? = null

        override val cacheKey: Any? = null
    }

    /** Mirrors what the iOS interceptor does when the "Bold Text" setting is enabled. */
    private class WeightAdjustingInterceptor(
        private val adjustment: Int
    ) : PlatformResolveInterceptor {
        override fun interceptFontWeight(fontWeight: FontWeight): FontWeight =
            FontWeight((fontWeight.weight + adjustment).coerceIn(1, 1000))
    }

    @Test
    fun interceptedFontWeight_reportsTheWeightUsedForResolution() {
        val resolver = FontFamilyResolverImpl(NoopFontLoader, WeightAdjustingInterceptor(200))

        assertEquals(600, resolver.interceptedFontWeight(FontWeight.Normal).weight)
        assertEquals(900, resolver.interceptedFontWeight(FontWeight.Bold).weight)
    }

    @Test
    fun interceptedFontWeight_isIdentityWithoutAPlatformInterceptor() {
        val resolver = FontFamilyResolverImpl(NoopFontLoader)

        assertEquals(FontWeight.Normal, resolver.interceptedFontWeight(FontWeight.Normal))
        assertEquals(FontWeight.Bold, resolver.interceptedFontWeight(FontWeight.Bold))
    }
}
