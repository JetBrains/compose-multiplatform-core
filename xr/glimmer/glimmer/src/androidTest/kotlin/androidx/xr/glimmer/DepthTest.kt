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

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
// The expected min sdk is 35, but we test on 33 for wider device coverage (some APIs are not
// available below 33)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class DepthTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun layer2IsDrawnOnTopOfLayer1() {
        val depth =
            Depth(
                layer1 = Shadow(radius = 50.dp, color = Color.Blue, spread = 50.dp),
                layer2 = Shadow(radius = 25.dp, color = Color.Red, spread = 25.dp),
            )
        rule.setGlimmerThemeContent {
            Box(Modifier.testTag("depth").padding(100.dp).size(100.dp).depth(depth, RectangleShape))
        }
        rule.onNodeWithTag("depth").captureToImage().run {
            val map = toPixelMap()
            val center = map[width / 2, height / 2]
            val topMiddle = map[width / 2, height / 5]
            val middleLeft = map[width / 5, height / 2]
            // Center pixel should be red, as we draw layer2 (red) on top of layer1 (blue)
            assertThat(center).isEqualTo(Color.Red)
            // Outer pixels should be more blue than red, since layer1 has a larger radius and
            // spread
            assertThat(topMiddle.blue).isGreaterThan(topMiddle.red)
            assertThat(middleLeft.blue).isGreaterThan(middleLeft.red)
        }
    }
}
