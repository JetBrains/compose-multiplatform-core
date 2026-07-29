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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle as SkFontStyle
import org.jetbrains.skia.FontWeight as SkFontWeight
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.Typeface as SkTypeface

@OptIn(ExperimentalTextApi::class)
internal actual fun loadTypeface(font: Font): SkTypeface {
    if (font !is PlatformFont) {
        throw IllegalArgumentException("Unsupported font type: $font")
    }
    val typeface = when (font) {
        is ResourceFont -> typefaceResource(font.name)
        is FileFont -> FontMgr.default.makeFromFile(font.file.toString())
        is LoadedFont -> FontMgr.default.makeFromData(Data.makeFromBytes(font.data))
        is SystemFont -> FontMgr.default.matchFamilyStyle(font.identity, font.skFontStyle)
    } ?: (FontMgr.default.legacyMakeTypeface(font.identity, font.skFontStyle)
        ?: error("loadTypeface legacyMakeTypeface failed"))
    return typeface.cloneWithVariationSettings(font.variationSettings)
}

private fun typefaceResource(resourceName: String): SkTypeface {
    val contextClassLoader = Thread.currentThread().contextClassLoader!!
    val resource = contextClassLoader.getResourceAsStream(resourceName)
        ?: (::typefaceResource.javaClass).getResourceAsStream(resourceName)
        ?: error("Can't load font from $resourceName")

    val bytes = resource.use { it.readAllBytes() }
    return FontMgr.default.makeFromData(Data.makeFromBytes(bytes))!!
}

private val Font.skFontStyle: SkFontStyle
    get() = SkFontStyle(
        weight = SkFontWeight(weight.weight),
        width = FontWidth.NORMAL,
        slant = if (style == FontStyle.Italic) FontSlant.ITALIC else FontSlant.UPRIGHT
    )

internal actual fun currentPlatform(): Platform {
    val name = System.getProperty("os.name")
    return when {
        name.startsWith("Linux") -> Platform.Linux
        name.startsWith("Win") -> Platform.Windows
        name == "Mac OS X" -> Platform.MacOS
        else -> Platform.Unknown
    }
}

/**
 * Creates a Font using a resource name.
 *
 * @param resource The resource name in classpath.
 * @param weight The weight of the font. The system uses this to match a font to a font request that
 * is given in a [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses this to match a font to a
 * font request that is given in a [androidx.compose.ui.text.SpanStyle].
 * @see FontFamily
 */
@OptIn(InternalComposeUiApi::class)
fun Font(
    resource: String,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal
): Font = ResourceFont(resource, weight, style, FontVariation.Settings())

@OptIn(InternalComposeUiApi::class)
fun Font(
    resource: String,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style)
): Font = ResourceFont(resource, weight, style, variationSettings)

/**
 * Creates a Font using a file path.
 *
 * @param file File path to font.
 * @param weight The weight of the font. The system uses this to match a font to a font request that
 * is given in a [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses this to match a font to a
 * font request that is given in a [androidx.compose.ui.text.SpanStyle].
 * @see FontFamily
 */
@OptIn(InternalComposeUiApi::class)
fun Font(
    file: File,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal
): Font = FileFont(file, weight, style, FontVariation.Settings())

@OptIn(InternalComposeUiApi::class)
fun Font(
    file: File,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style)
): Font = FileFont(file, weight, style, variationSettings)
