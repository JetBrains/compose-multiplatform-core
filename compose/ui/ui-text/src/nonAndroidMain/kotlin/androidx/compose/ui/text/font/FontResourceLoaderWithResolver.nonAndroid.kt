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

package androidx.compose.ui.text.font

import androidx.compose.ui.InternalComposeUiApi

/**
 * Seam implemented by the backend's deprecated `FontLoader` (a [Font.ResourceLoader]) so this
 * bridge can hand back its [FontFamily.Resolver] without referencing the backend-specific loader
 * type.
 */
@InternalComposeUiApi
interface FontResourceLoaderWithResolver {
    val fontFamilyResolver: FontFamily.Resolver
}

@Suppress("DEPRECATION")
@OptIn(InternalComposeUiApi::class)
@Deprecated("This exists to bridge existing Font.ResourceLoader APIs, and should be " +
    "removed with them",
    replaceWith = ReplaceWith("createFontFamilyResolver()"),
)
internal actual fun createFontFamilyResolver(
    fontResourceLoader: Font.ResourceLoader
): FontFamily.Resolver {
    if (fontResourceLoader !is FontResourceLoaderWithResolver)
        throw IllegalArgumentException("Unexpected type: $fontResourceLoader must be FontLoader")
    return fontResourceLoader.fontFamilyResolver
}
