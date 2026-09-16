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

/**
 * Font backend supplied by the registered backend. ui-text keeps the resolver wiring
 * (`FontFamilyResolverImpl`, the typeface adapters, the request caches — all internal to ui-text)
 * backend-agnostic, and adapts this seam into the internal [PlatformFontLoader] via
 * [createPlatformFontFamilyResolver]. Values typed as [Any] are opaque backend handles that only the
 * backend interprets (the platform font-load result and the platform font collection).
 */
@InternalComposeUiApi
interface PlatformTypefacesLoader {
    /** @see PlatformFontLoader.loadBlocking */
    fun loadBlocking(font: Font): Any?

    /** @see PlatformFontLoader.awaitLoad */
    suspend fun awaitLoad(font: Font): Any?

    /** @see PlatformFontLoader.cacheKey */
    val cacheKey: Any?

    /** Resolves a system/loaded typeface for [fontFamily]; result is wrapped into a TypefaceResult. */
    fun loadPlatformTypes(
        fontFamily: FontFamily,
        fontWeight: FontWeight,
        fontStyle: FontStyle,
    ): Any

    /** The platform font collection used for paragraph building. */
    val fontCollection: Any
}

/** Adapts the public [PlatformTypefacesLoader] seam to the internal [PlatformFontLoader]. */
@OptIn(InternalComposeUiApi::class)
internal class PlatformFontLoaderAdapter(
    val backend: PlatformTypefacesLoader,
) : PlatformFontLoader {
    override fun loadBlocking(font: Font): Any? = backend.loadBlocking(font)

    override suspend fun awaitLoad(font: Font): Any? = backend.awaitLoad(font)

    override val cacheKey: Any?
        get() = backend.cacheKey
}

/**
 * Returns the [PlatformTypefacesLoader] backing [this] resolver, or `null` if it was not built by
 * [createPlatformFontFamilyResolver]. Used by the backend to reach the platform font collection.
 */
@InternalComposeUiApi
fun FontFamily.Resolver.platformTypefacesLoader(): PlatformTypefacesLoader? {
    val loader = (this as? FontFamilyResolverImpl)?.platformFontLoader
    return (loader as? PlatformFontLoaderAdapter)?.backend
}
