/*
 * Copyright 2021 The Android Open Source Project
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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

expect sealed class PlatformFont() : Font {
    abstract val identity: String
    abstract val variationSettings: FontVariation.Settings

    /** Used by the registered font backend to key typefaces. */
    @InternalComposeUiApi
    val cacheKey: String
}

/**
 * A Font that's already installed in the system.
 *
 * @param identity Unique identity for a font. Used internally to distinguish fonts.
 * @param weight The weight of the font. The system uses this to match a font to a font request
 * that is given in a [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses this to match a font to a
 * font request that is given in a [androidx.compose.ui.text.SpanStyle].
 *
 * @see FontFamily
 */
@ExperimentalTextApi
class SystemFont(
    override val identity: String,
    override val weight: FontWeight = FontWeight.Normal,
    override val style: FontStyle = FontStyle.Normal,
    override val variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
) : PlatformFont() {

    constructor(
        identity: String,
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal
    ) : this(identity, weight, style, variationSettings = FontVariation.Settings())

    override fun toString(): String {
        return "SystemFont(identity='$identity', weight=$weight, style=$style, variationSettings=${variationSettings.settings})"
    }
}

/**
 * Defines a Font using a byte array with loaded font data.
 *
 * @param identity Unique identity for a font. Used internally to distinguish fonts.
 * @param getData should return Byte array with loaded font data.
 * @param weight The weight of the font. The system uses this to match a font to a font request
 * that is given in a [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses this to match a font to a
 * font request that is given in a [androidx.compose.ui.text.SpanStyle].
 *
 * @see FontFamily
 */
class LoadedFont @InternalComposeUiApi constructor(
    override val identity: String,
    internal val getData: () -> ByteArray,
    override val weight: FontWeight,
    override val style: FontStyle,
    override val variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
) : PlatformFont() {

    @OptIn(InternalComposeUiApi::class)
    constructor(
        identity: String,
        getData: () -> ByteArray,
        weight: FontWeight,
        style: FontStyle
    ) : this(identity, getData, weight, style, FontVariation.Settings())

    @ExperimentalTextApi
    override val loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking

    val data: ByteArray get() = getData()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoadedFont) return false
        if (identity != other.identity) return false
        if (weight != other.weight) return false
        if (style != other.style) return false
        return variationSettings.settings == other.variationSettings.settings
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + variationSettings.settings.hashCode()
        return result
    }

    override fun toString(): String {
        return "LoadedFont(identity='$identity', weight=$weight, style=$style, variationSettings=${variationSettings.settings})"
    }
}
