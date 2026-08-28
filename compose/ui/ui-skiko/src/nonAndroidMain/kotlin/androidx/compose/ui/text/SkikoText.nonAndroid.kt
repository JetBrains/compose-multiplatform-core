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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.SkiaFontLoader
import androidx.compose.ui.text.font.createPlatformFontFamilyResolver
import androidx.compose.ui.text.platform.Platform
import androidx.compose.ui.text.PlatformParagraph
import androidx.compose.ui.text.platform.PlatformText
import androidx.compose.ui.text.platform.SkikoParagraphIntrinsics
import androidx.compose.ui.text.platform.currentPlatform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.coroutines.CoroutineContext

/**
 * Skiko-side text implementation entry point, registered via
 * [androidx.compose.ui.platform.registerSkikoComposeImplementation].
 */
@OptIn(InternalComposeUiApi::class)
internal object SkikoText : PlatformText {
    override fun createParagraph(
        text: String,
        style: TextStyle,
        annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
        placeholders: List<AnnotatedString.Range<Placeholder>>,
        maxLines: Int,
        overflow: TextOverflow,
        constraints: Constraints,
        density: Density,
        fontFamilyResolver: FontFamily.Resolver,
    ): PlatformParagraph = SkikoParagraph(
        SkikoParagraphIntrinsics(
            text = text,
            style = style,
            annotations = annotations,
            placeholders = placeholders,
            density = density,
            fontFamilyResolver = fontFamilyResolver,
        ),
        maxLines,
        overflow,
        constraints,
    )

    override fun createParagraph(
        paragraphIntrinsics: ParagraphIntrinsics,
        maxLines: Int,
        overflow: TextOverflow,
        constraints: Constraints,
    ): PlatformParagraph =
        SkikoParagraph(
            paragraphIntrinsics as SkikoParagraphIntrinsics,
            maxLines,
            overflow,
            constraints,
        )

    override fun createParagraphIntrinsics(
        text: String,
        style: TextStyle,
        annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
        placeholders: List<AnnotatedString.Range<Placeholder>>,
        density: Density,
        fontFamilyResolver: FontFamily.Resolver,
    ): ParagraphIntrinsics = SkikoParagraphIntrinsics(
        text = text,
        style = style,
        annotations = annotations,
        placeholders = placeholders,
        density = density,
        fontFamilyResolver = fontFamilyResolver,
    )

    override fun createFontFamilyResolver(): FontFamily.Resolver =
        createPlatformFontFamilyResolver(SkiaFontLoader())

    @OptIn(ExperimentalTextApi::class)
    override fun createFontFamilyResolver(coroutineContext: CoroutineContext): FontFamily.Resolver =
        createPlatformFontFamilyResolver(SkiaFontLoader(), coroutineContext)

    override fun findPrecedingBreak(text: String, index: Int): Int =
        findSkikoPrecedingBreak(text, index)

    override fun findFollowingBreak(text: String, index: Int): Int =
        findSkikoFollowingBreak(text, index)

    @OptIn(ExperimentalTextApi::class)
    override val defaultFontRasterizationSettings: FontRasterizationSettings by lazy {
        when (currentPlatform()) {
            Platform.Windows -> FontRasterizationSettings(
                subpixelPositioning = true,
                // Most UIs still use ClearType on Windows, so we should match this
                // We temporarily disabled `SubpixelAntiAlias` until we figure out
                // how to properly retrieve default OS settings
                smoothing = FontSmoothing.AntiAlias,
                hinting = FontHinting.Normal, // None would trigger some potentially unwanted behavior, but everything else is forced into Normal on Windows
                autoHintingForced = false,
            )

            Platform.Linux, Platform.Unknown -> FontRasterizationSettings(
                subpixelPositioning = true,
                smoothing = FontSmoothing.AntiAlias,
                hinting = FontHinting.Slight, // Most distributions use Slight now by default
                autoHintingForced = false,
            )

            Platform.Android -> FontRasterizationSettings(
                subpixelPositioning = true,
                smoothing = FontSmoothing.AntiAlias,
                hinting = FontHinting.Slight,
                autoHintingForced = false,
            )

            Platform.MacOS, Platform.IOS, Platform.TvOS, Platform.WatchOS -> FontRasterizationSettings(
                subpixelPositioning = true,
                smoothing = FontSmoothing.AntiAlias, // macOS doesn't support SubpixelAntiAlias anymore as of Catalina
                hinting = FontHinting.Normal, // Completely ignored on macOS
                autoHintingForced = false, // Completely ignored on macOS
            )
        }
    }
}
