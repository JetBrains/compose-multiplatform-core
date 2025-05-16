/*
 * Copyright 2025 The Android Open Source Project
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
package androidx.xr.glimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.testutils.assertAgainstGolden
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.test.screenshot.matchers.MSSIMMatcher
import androidx.xr.glimmer.samples.ColorsSample
import androidx.xr.glimmer.samples.TypographySample
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class GlimmerThemeScreenshotTest() {

    @get:Rule val rule = createComposeRule()

    @get:Rule val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_DIRECTORY)

    @Test
    fun colors() {
        setGlimmerThemeContent { ColorsSample() }
        assertRootAgainstGolden("glimmerTheme_colors")
    }

    @Test
    fun typography() {
        setGlimmerThemeContent { TypographySample() }
        assertRootAgainstGolden("glimmerTheme_typography")
    }

    private fun assertRootAgainstGolden(goldenName: String) {
        rule
            .onRoot()
            .captureToImage()
            .assertAgainstGolden(screenshotRule, goldenName, MSSIMMatcher(0.995))
    }

    private fun setGlimmerThemeContent(content: @Composable () -> Unit) {
        rule.setContent {
            GlimmerTheme { Box(Modifier.background(GlimmerTheme.colors.surface)) { content() } }
        }
    }
}
