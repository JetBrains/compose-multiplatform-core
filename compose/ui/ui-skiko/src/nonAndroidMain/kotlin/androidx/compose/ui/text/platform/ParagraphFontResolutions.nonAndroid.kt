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

import androidx.compose.runtime.State
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight

/**
 * Resolves fonts and tracks asynchronous font changes via [hasStaleResolvedFonts],
 * so text can be remeasured when the fonts change.
 */
internal class ParagraphFontResolutions(
    private val fontFamilyResolver: FontFamily.Resolver,
) {
    private val resolutions = mutableMapOf<FontRequest, FontResolution>()

    fun resolve(
        fontFamily: FontFamily?,
        fontWeight: FontWeight,
        fontStyle: FontStyle,
        fontSynthesis: FontSynthesis,
    ): FontLoadResult {
        val request = FontRequest(fontFamily, fontWeight, fontStyle, fontSynthesis)
        // Keep the fonts used for intrinsic measurement for the lifetime of these intrinsics.
        // Subsequent paragraph builds must neither resolve again after cache eviction nor replace
        // the initial value after an async update. New fonts require new intrinsics.
        return resolutions.getOrPut(request) {
            val result = fontFamilyResolver.resolve(fontFamily, fontWeight, fontStyle, fontSynthesis)
            FontResolution(result, result.value as FontLoadResult)
        }.initialFont
    }

    val hasStaleResolvedFonts: Boolean
        get() = resolutions.values.any { it.state.value !== it.initialFont }

    private data class FontRequest(
        val fontFamily: FontFamily?,
        val fontWeight: FontWeight,
        val fontStyle: FontStyle,
        val fontSynthesis: FontSynthesis,
    )

    private class FontResolution(val state: State<Any>, val initialFont: FontLoadResult)
}
