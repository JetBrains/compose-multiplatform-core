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

import org.jetbrains.skia.Typeface as SkTypeface

/**
 * Font for use on Skiko-based platforms (Desktop, iOS, macOS, Web).
 *
 * All [SkikoFont] produce an [org.jetbrains.skia.Typeface] which may be used to draw text.
 * This is the main low-level API for introducing a new Font description to Compose on
 * Skiko-based platforms for both blocking and async load.
 *
 * You may subclass this to add new types of font descriptions that may be used in
 * [FontListFontFamily]. For example, you can add a [FontLoadingStrategy.Blocking] font that returns
 * a Typeface from a local resource not supported by an existing [Font]. Or, you can create an
 * [FontLoadingStrategy.Async] font that loads a font file from a server.
 *
 * Subclasses should implement [equals] and [hashCode] based on the values that uniquely identify
 * the font (typically [identity], [weight], [style], [loadingStrategy], and [variationSettings]).
 * Prefer globally unique [identity] values across all [SkikoFont] subclasses, because the internal
 * registration key is shared under a common `SkikoFont|` prefix.
 *
 * @param loadingStrategy loading strategy this font will provide in fallback chains
 * @param typefaceLoader a loader that knows how to load this [SkikoFont], may be shared between
 *   several fonts
 * @param variationSettings the settings that will be applied to this font, if supported by the font.
 *   Defaults to empty settings. Prefer passing [FontVariation.Settings] built from [weight] and
 *   [style] (for example `FontVariation.Settings(weight, style)`) when those axes should be applied
 *   to variable fonts.
 */
abstract class SkikoFont(
    final override val loadingStrategy: FontLoadingStrategy,
    val typefaceLoader: TypefaceLoader,
    /**
     * The settings that will be applied to this font, if supported by the font.
     *
     * If the font does not support a [FontVariation.Setting], it has no effect.
     *
     * Unlike Android's AndroidFont, subclasses must **not** apply these settings themselves. The
     * framework applies them after [TypefaceLoader] returns. Loaders should return an unvaried
     * typeface.
     */
    val variationSettings: FontVariation.Settings = FontVariation.Settings()
) : Font {

    /**
     * Unique identity for this font's source (file, URL, asset id, etc.).
     *
     * Used as the unvaried load cache key via [baseCacheKey]: fonts that share the same [identity]
     * share one TypefaceLoader result. Use a distinct [identity] for each distinct font file;
     * do not rely on [weight], [style], or [variationSettings] to select different sources.
     *
     * Also combined with [weight], [style], and [variationSettings] into [cacheKey] for
     * font-collection registration / paragraph aliasing after variation is applied.
     */
    abstract val identity: String

    /**
     * Cache key for the unvaried typeface produced by [TypefaceLoader].
     *
     * Equal to `"SkikoFont|$identity"`. Weight, style, and variation settings are intentionally
     * omitted so a single variable-font file can be loaded once and then cloned for different
     * axes / matching metadata. [TypefaceLoader] implementations must select the source from
     * [identity] (and any loader-private stable fields), not from weight, style, or variation.
     */
    internal val baseCacheKey: String
        get() = "SkikoFont|$identity"

    /**
     * Registration / alias key for this font after variation settings are applied.
     *
     * Includes [baseCacheKey], [weight], [style], and [variationSettings] so different matching
     * metadata and axis settings do not collide in the font collection.
     */
    internal val cacheKey: String
        get() =
            "$baseCacheKey|weight=${weight.weight}|style=$style|" +
                "variationSettings=${variationSettings.settings}"

    /**
     * Loader for loading a [SkikoFont] and producing an [org.jetbrains.skia.Typeface].
     *
     * This interface is not intended to be used by application developers for text display. To load
     * a typeface for display use [FontFamily.Resolver].
     *
     * [TypefaceLoader] allows the introduction of new types of font descriptors for use in
     * [FontListFontFamily]. A [TypefaceLoader] allows a new subclass of [SkikoFont] to be used by
     * normal compose text rendering methods.
     *
     * Examples of new types of fonts that [TypefaceLoader] can add:
     * - [FontLoadingStrategy.Blocking] [Font] that loads Typeface from a local resource not
     *   supported by an existing font
     * - [FontLoadingStrategy.OptionalLocal] [Font] that "loads" a platform Typeface only available
     *   on some platforms.
     * - [FontLoadingStrategy.Async] [Font] that loads a font from a backend via a network request.
     *
     * During resolution from [FontFamily.Resolver], a [SkikoFont] subclass will be queried for
     * an appropriate loader.
     *
     * The loader attached to an instance of a [SkikoFont] is only required to be able to load
     * that instance, though it is advised to create one loader for all instances of the same
     * subclass and share them between [SkikoFont] instances to avoid allocations or allow
     * caching.
     *
     * Loaders should return an *unvaried* typeface: [FontVariation.Settings] from the [SkikoFont]
     * are applied by the framework after loading completes.
     *
     * Select the font source from [SkikoFont.identity] (and loader-private fields). Do not branch
     * on [SkikoFont.weight], [SkikoFont.style], or [SkikoFont.variationSettings] to pick different
     * files; those are applied after load for matching and variation cloning.
     */
    interface TypefaceLoader {
        /**
         * Immediately load the font in a blocking manner such that it will be available this frame.
         *
         * This method will be called on a UI-critical thread, however it has been determined that
         * this font is required for the current frame. This method is allowed to perform small
         * amounts of I/O to load a font file from a local source.
         *
         * This method should never perform expensive I/O operations, such as loading from a remote
         * source. If expensive operations are required to complete the font, this method may choose
         * to throw. Note that this method will never be called for fonts with
         * [FontLoadingStrategy.Async].
         *
         * It is possible for [loadBlocking] to be called for the same instance of [SkikoFont] in
         * parallel. Implementations should support parallel concurrent loads, or de-dup.
         *
         * @param font the font to load which contains this loader as [SkikoFont.typefaceLoader]
         * @return [org.jetbrains.skia.Typeface] for loaded font, or null if the font fails to load
         */
        fun loadBlocking(font: SkikoFont): SkTypeface?

        /**
         * Asynchronously load the font, from either local or remote sources such that it will cause
         * text reflow when loading completes.
         *
         * This method will be called on a UI-critical thread, and should not block the thread for
         * font loading from sources slower than the local filesystem. More expensive loads should
         * dispatch to an appropriate thread.
         *
         * This method is always called in a timeout context and must return its final value within
         * 15 seconds. If the Typeface is not resolved within 15 seconds, the async load is
         * cancelled and considered a permanent failure. Implementations should use structured
         * concurrency to cooperatively cancel work.
         *
         * Compose does not know what resources are required to satisfy a font load. Subclasses
         * implementing [FontLoadingStrategy.Async] behavior should ensure requests are de-duped for
         * the same resource.
         *
         * It is possible for [awaitLoad] to be called for the same instance of [SkikoFont] in
         * parallel. Implementations should support parallel concurrent loads, or de-dup.
         *
         * @param font the font to load which contains this loader as [SkikoFont.typefaceLoader]
         * @return [org.jetbrains.skia.Typeface] for loaded font, or null if not available
         */
        suspend fun awaitLoad(font: SkikoFont): SkTypeface?
    }
}
