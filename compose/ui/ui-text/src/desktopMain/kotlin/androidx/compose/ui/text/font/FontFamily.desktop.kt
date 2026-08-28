/*
 * Copyright 2023 The Android Open Source Project
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

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.platform.SystemFont

/**
 * Load a [FontFamily] from a system font family name. If the [familyName]
 * doesn't match any available family in the system, the lookup will return
 * a fallback font family.
 *
 * If you're trying to use an AWT `java.awt.Font` in Compose, use the
 * `Font.asComposeFontFamily` function instead (in ui-skiko), which will take
 * care of some AWT-specific quirks, too. If you want to load a font family
 * embedded in the JetBrains Runtime, you can use `EmbeddedFontFamily` (in
 * ui-skiko).
 *
 * @param familyName The name of the system font family to load.
 * @return the requested system font family, or a fallback if [familyName]
 *     doesn't match any available system font family.
 */
@ExperimentalTextApi
@Stable
fun FontFamily(familyName: String): FontFamily =
    FontListFontFamily(
        listOf(
            SystemFont(familyName, FontWeight.W100, FontStyle.Normal),
            SystemFont(familyName, FontWeight.W200, FontStyle.Normal),
            SystemFont(familyName, FontWeight.W300, FontStyle.Normal),
            SystemFont(familyName, FontWeight.W400, FontStyle.Normal),
            SystemFont(familyName, FontWeight.W500, FontStyle.Normal),
            SystemFont(familyName, FontWeight.W600, FontStyle.Normal),
            SystemFont(familyName, FontWeight.W700, FontStyle.Normal),
            SystemFont(familyName, FontWeight.W800, FontStyle.Normal),
            SystemFont(familyName, FontWeight.W900, FontStyle.Normal),
            SystemFont(familyName, FontWeight.W100, FontStyle.Italic),
            SystemFont(familyName, FontWeight.W200, FontStyle.Italic),
            SystemFont(familyName, FontWeight.W300, FontStyle.Italic),
            SystemFont(familyName, FontWeight.W400, FontStyle.Italic),
            SystemFont(familyName, FontWeight.W500, FontStyle.Italic),
            SystemFont(familyName, FontWeight.W600, FontStyle.Italic),
            SystemFont(familyName, FontWeight.W700, FontStyle.Italic),
            SystemFont(familyName, FontWeight.W800, FontStyle.Italic),
            SystemFont(familyName, FontWeight.W900, FontStyle.Italic),
        )
    )
