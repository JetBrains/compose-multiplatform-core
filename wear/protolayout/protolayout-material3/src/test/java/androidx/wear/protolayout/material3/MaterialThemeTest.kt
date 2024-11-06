/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.wear.protolayout.material3

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.tokens.ColorTokens
import androidx.wear.protolayout.material3.tokens.ShapeTokens
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.internal.DoNotInstrument

@RunWith(AndroidJUnit4::class)
@DoNotInstrument
class MaterialThemeTest {
    @Test
    fun defaultMaterialTheme_returnsTokenDefaults() {
        val defaultTheme = MaterialTheme()

        for (i in 0 until Typography.TOKEN_COUNT) {
            val fontStyle: LayoutElementBuilders.FontStyle =
                defaultTheme.getFontStyleBuilder(i).build()
            val textStyle = Typography.fromToken(i)
            assertThat(fontStyle.preferredFontFamilies).isEmpty()
            assertThat(fontStyle.size!!.value).isEqualTo(textStyle.size.value)
            assertThat(fontStyle.letterSpacing!!.value).isEqualTo(textStyle.letterSpacing.value)
            assertThat(fontStyle.settings).isEqualTo(textStyle.fontSettings)
        }

        assertThat(defaultTheme.colorScheme.primaryDim.argb).isEqualTo(ColorTokens.PRIMARY_DIM)
        assertThat(defaultTheme.shapes.medium.toProto())
            .isEqualTo(ShapeTokens.CORNER_MEDIUM.toProto())
    }

    @Test
    fun customMaterialTheme_overrideColor_returnsOverriddenValue() {
        assertThat(
                MaterialTheme(colorScheme = ColorScheme(error = argb(Color.MAGENTA)))
                    .colorScheme
                    .error
                    .argb
            )
            .isEqualTo(Color.MAGENTA)
    }

    @Test
    fun customMaterialTheme_colorNotOverridden_returnsDefaultValue() {
        // Provides a custom color scheme with an overridden color.
        assertThat(
                MaterialTheme(colorScheme = ColorScheme(secondary = argb(Color.MAGENTA)))
                    .colorScheme
                    .onError
                    .argb
            )
            .isEqualTo(ColorTokens.ON_ERROR)
    }
}
