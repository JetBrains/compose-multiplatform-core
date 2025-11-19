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

package androidx.compose.remote.player.compose.creation.compose.state

import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rememberRemoteFloat
import androidx.compose.remote.player.compose.SCREENSHOT_GOLDEN_DIRECTORY
import androidx.compose.remote.player.compose.test.utils.screenshot.TargetPlayer
import androidx.compose.remote.player.compose.test.utils.screenshot.rule.RemoteComposeScreenshotTestRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@SdkSuppress(minSdkVersion = 35, maxSdkVersion = 35)
@RunWith(JUnit4::class)
class RemoteStateTest {
    @get:Rule
    val composeTestRule =
        RemoteComposeScreenshotTestRule(
            moduleDirectory = SCREENSHOT_GOLDEN_DIRECTORY,
            targetPlayer = TargetPlayer.Compose,
        )

    @Test
    fun testNamedFloat() {
        var widthId = 0
        var configurableWidthId = 0

        composeTestRule.runTest {
            RemoteColumn(modifier = RemoteModifier.size(100.dp)) {
                val width = rememberRemoteFloat { componentWidth() }

                val configurableWidth = rememberRemoteFloat(name = "configurableWidth") { width }

                RemoteText(RemoteString("Width: ") + width.toRemoteString(3, 0))
                RemoteText(
                    RemoteString("Configurable Width: ") + configurableWidth.toRemoteString(3, 0)
                )

                widthId = Utils.idFromNan(width.id)
                configurableWidthId = Utils.idFromNan(configurableWidth.id)
            }
        }

        assertThat(widthId).isNotEqualTo(configurableWidthId)
    }
}
