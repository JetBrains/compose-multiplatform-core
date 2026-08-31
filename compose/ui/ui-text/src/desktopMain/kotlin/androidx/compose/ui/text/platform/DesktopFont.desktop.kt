/*
 * Copyright 2020 The Android Open Source Project
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
import java.io.File

actual sealed class PlatformFont : Font {
    actual abstract val identity: String
    actual abstract val variationSettings: FontVariation.Settings
    @InternalComposeUiApi
    actual val cacheKey: String
        get() = "${this::class.qualifiedName}|$identity|weight=${weight.weight}|style=$style"
}

/**
 * Defines a Font using a resource name.
 *
 * @param name The resource name in classpath.
 * @param weight The weight of the font. The system uses this to match a
 *     font to a font request that is given in a
 *     [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses
 *     this to match a font to a font request that is given in a
 *     [androidx.compose.ui.text.SpanStyle].
 * @see FontFamily
 */

class ResourceFont @InternalComposeUiApi constructor(
    val name: String,
    override val weight: FontWeight = FontWeight.Normal,
    override val style: FontStyle = FontStyle.Normal,
    override val variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
) : PlatformFont() {

    @OptIn(InternalComposeUiApi::class)
    constructor(
        name: String,
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal
    ) : this(name, weight, style, FontVariation.Settings(weight, style))

    override val identity
        get() = name

    @ExperimentalTextApi
    override val loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResourceFont

        if (name != other.name) return false
        if (weight != other.weight) return false
        if (style != other.style) return false
        return variationSettings.settings == other.variationSettings.settings
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + variationSettings.hashCode()
        return result
    }

    override fun toString(): String {
        return "ResourceFont(name='$name', weight=$weight, style=$style, variationSettings=${variationSettings.settings})"
    }
}

/**
 * Defines a Font using a file path.
 *
 * @param file File path to font.
 * @param weight The weight of the font. The system uses this to match a
 *     font to a font request that is given in a
 *     [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses
 *     this to match a font to a font request that is given in a
 *     [androidx.compose.ui.text.SpanStyle].
 * @see FontFamily
 */
class FileFont @InternalComposeUiApi constructor(
    val file: File,
    override val weight: FontWeight = FontWeight.Normal,
    override val style: FontStyle = FontStyle.Normal,
    override val variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
) : PlatformFont() {

    @OptIn(InternalComposeUiApi::class)
    constructor(
        file: File,
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal,
    ) : this(file, weight, style, FontVariation.Settings())

    override val identity
        get() = file.toString()

    @ExperimentalTextApi
    override val loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileFont

        if (file != other.file) return false
        if (weight != other.weight) return false
        if (style != other.style) return false
        return variationSettings.settings == other.variationSettings.settings
    }

    override fun hashCode(): Int {
        var result = file.hashCode()
        result = 31 * result + weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + variationSettings.hashCode()
        return result
    }

    override fun toString(): String {
        return "FileFont(file=$file, weight=$weight, style=$style, variationSettings=${variationSettings.settings})"
    }
}
