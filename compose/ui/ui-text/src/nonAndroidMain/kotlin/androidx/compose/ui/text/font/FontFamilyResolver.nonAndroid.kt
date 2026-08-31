/*
 * Copyright 2022 The Android Open Source Project
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

@file:JvmName("FontFamilyResolver_sikioKt")

package androidx.compose.ui.text.font

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.platform.PlatformTextRegistry
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmName

/**
 * Create a new fontFamilyResolver for use outside of composition context
 *
 * Example usages:
 * - Before starting compose to preload fonts
 * - Creating Paragraph objects on background thread
 *
 * Usages inside of Composition should use LocalFontFamilyResolver.current
 */
@OptIn(InternalComposeUiApi::class)
fun createFontFamilyResolver(): FontFamily.Resolver {
    return PlatformTextRegistry.requireCurrent().createFontFamilyResolver()
}

/**
 * Create a new fontFamilyResolver for use outside of composition context with a coroutine context.
 *
 * Example usages:
 * - Before starting compose to preload fonts
 * - Creating Paragraph objects on background thread
 * - Configuring LocalFontFamilyResolver with a different CoroutineScope
 *
 * Usages inside of Composition should use LocalFontFamilyResolver.current
 *
 * Any [kotlinx.coroutines.CoroutineExceptionHandler] provided will be called with
 * exceptions related to fallback font loading. These exceptions are not fatal, and indicate
 * that font fallback continued to the next font load.
 *
 * If no [kotlinx.coroutines.CoroutineExceptionHandler] is provided, a default implementation will
 * be added that ignores all exceptions.
 *
 * @param coroutineContext context to launch async requests in during resolution.
 */
@ExperimentalTextApi
@OptIn(InternalComposeUiApi::class)
fun createFontFamilyResolver(coroutineContext: CoroutineContext): FontFamily.Resolver {
    return PlatformTextRegistry.requireCurrent().createFontFamilyResolver(coroutineContext)
}

/**
 * Builds the internal [FontFamilyResolverImpl] from the [backend] supplied by the registered
 * backend. All resolver internals stay in ui-text; the backend only provides the loader seam.
 */
@InternalComposeUiApi
fun createPlatformFontFamilyResolver(backend: PlatformTypefacesLoader): FontFamily.Resolver {
    return FontFamilyResolverImpl(
        PlatformFontLoaderAdapter(backend),
        createPlatformResolveInterceptor(),
    )
}

@OptIn(InternalComposeUiApi::class, ExperimentalTextApi::class)
@InternalComposeUiApi
fun createPlatformFontFamilyResolver(
    backend: PlatformTypefacesLoader,
    coroutineContext: CoroutineContext,
): FontFamily.Resolver {
    return FontFamilyResolverImpl(
        PlatformFontLoaderAdapter(backend),
        createPlatformResolveInterceptor(),
        GlobalTypefaceRequestCache,
        FontListFontFamilyTypefaceAdapter(
            GlobalAsyncTypefaceCache,
            coroutineContext
        )
    )
}

internal expect fun createPlatformResolveInterceptor(): PlatformResolveInterceptor
