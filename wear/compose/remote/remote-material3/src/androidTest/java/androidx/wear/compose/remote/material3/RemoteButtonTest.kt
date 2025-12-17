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

package androidx.wear.compose.remote.material3

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.remote.creation.CreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.painter.painterRemoteBitmap
import androidx.compose.remote.creation.compose.capture.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemotePaddingValues
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rememberRemoteBitmapValue
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.player.compose.test.utils.screenshot.TargetPlayer
import androidx.compose.remote.player.compose.test.utils.screenshot.rule.RemoteComposeScreenshotTestRule
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.wear.compose.remote.material3.Material3ImageTest.Companion.createImage
import androidx.wear.compose.remote.material3.previews.RemoteButtonEnabled
import androidx.wear.compose.remote.material3.previews.RemoteButtonWithBorder
import androidx.wear.compose.remote.material3.previews.RemoteButtonWithIcon
import androidx.wear.compose.remote.material3.previews.RemoteButtonWithIconAndSecondaryLabel
import androidx.wear.compose.remote.material3.previews.RemoteButtonWithSecondaryLabel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@SdkSuppress(minSdkVersion = 35, maxSdkVersion = 35)
@SuppressLint("UnrememberedMutableState")
@RunWith(JUnit4::class)
class RemoteButtonTest {
    @get:Rule
    val remoteComposeTestRule =
        RemoteComposeScreenshotTestRule(
            moduleDirectory = SCREENSHOT_GOLDEN_DIRECTORY,
            targetPlayer = TargetPlayer.View,
        )
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val creationDisplayInfo =
        CreationDisplayInfo(500, 500, context.resources.displayMetrics.densityDpi)

    @Test
    fun button_enabled() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) { RemoteButtonEnabled() }
        }
    }

    @Test
    fun button_disabled() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(modifier = RemoteModifier.buttonSizeModifier(), enabled = false.rb) {
                    RemoteText("button_disabled".rs)
                }
            }
        }
    }

    @Test
    fun button_overrides_colors() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            val colors =
                RemoteButtonColors(
                    containerColor = RemoteColor(Color.Yellow),
                    contentColor = RemoteColor(Color.Cyan),
                    secondaryContentColor = RemoteColor(Color.Black),
                    iconColor = RemoteColor(Color.Black),
                    disabledContainerColor = RemoteColor(Color.Black),
                    disabledContentColor = RemoteColor(Color.Black),
                    disabledSecondaryContentColor = RemoteColor(Color.Black),
                    disabledIconColor = RemoteColor(Color.Black),
                )
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(modifier = RemoteModifier.buttonSizeModifier(), colors = colors) {
                    RemoteText("button_overrides_colors".rs)
                }
            }
        }
    }

    @Test
    fun button_overrides_padding() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    contentPadding = RemotePaddingValues(50.rdp),
                ) {
                    RemoteText("button_overrides_padding".rs)
                }
            }
        }
    }

    @Test
    fun button_overrides_size() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.size(180.rdp, 100.rdp),
                    contentPadding = RemotePaddingValues(0.rdp),
                ) {
                    RemoteText("button_overrides_size".rs)
                }
            }
        }
    }

    @Test
    fun button_overrides_textStyle() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    contentPadding = RemotePaddingValues(0.rdp),
                ) {
                    RemoteText(
                        "button_overrides_textStyle".rs,
                        color = null,
                        style =
                            RemoteMaterialTheme.typography.typography.labelSmall.copy(Color.Cyan),
                    )
                }
            }
        }
    }

    @Test
    fun button_with_border() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) { RemoteButtonWithBorder() }
        }
    }

    @Test
    fun button_with_circle_shape() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.size(150.rdp),
                    border = 8.rdp,
                    borderColor = RemoteColor(Color.Green),
                    shape = RemoteCircleShape,
                ) {
                    RemoteText("button_with_circle_shape".rs)
                }
            }
        }
    }

    @Test
    fun button_enabled_container_background_image() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            val backgroundImage =
                rememberRemoteBitmapValue(name = "backgroundImage") { createImage(200, 200) }
            Center(RemoteModifier.fillMaxSize()) {
                val containerPainter =
                    RemoteButtonDefaults.containerPainter(painterRemoteBitmap(backgroundImage))
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    containerPainter = containerPainter,
                ) {
                    RemoteText("image_background".rs)
                }
            }
        }
    }

    @Test
    fun button_disabled_container_background_image() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            val backgroundImage =
                rememberRemoteBitmapValue(name = "button_disabled_container_background_image") {
                    createImage(200, 200)
                }
            Center(RemoteModifier.fillMaxSize()) {
                val enabled = false.rb
                val containerPainter =
                    RemoteButtonDefaults.containerPainter(painterRemoteBitmap(backgroundImage))
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    enabled = enabled,
                    containerPainter = containerPainter,
                ) {
                    RemoteText("disable_image_background".rs)
                }
            }
        }
    }

    @Test
    fun button_with_icon_and_label_and_secondary_label() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) { RemoteButtonWithIconAndSecondaryLabel() }
        }
    }

    @Test
    fun button_with_icon_and_label() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) { RemoteButtonWithIcon() }
        }
    }

    @Test
    fun button_with_label_and_secondary_label() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) { RemoteButtonWithSecondaryLabel() }
        }
    }

    @Test
    fun button_enabled_click_modifier_is_added() {
        val expectedContent =
            """
DATA_TEXT<42> = "button_enabled"
ROOT [-2:-1] = [0.0, 0.0, 0.0, 0.0] VISIBLE
  ROW [-3:-1] = [0.0, 0.0, 73.5, 31.5] VISIBLE
    MODIFIERS
      HEIGHT_IN = [52.0, 3.4028235E38]
      WIDTH_IN = [12.0, 3.4028235E38]
      DRAW_CONTENT
      CLICK_MODIFIER
      SEMANTICS = SEMANTICS BUTTON
      PADDING = [36.75, 15.75, 36.75, 15.75]
    TEXT_LAYOUT [-5:-1] = [0.0, 0.0, 0.0, 0.0] VISIBLE (42:"null")
      MODIFIERS"""
                .trimIndent()
        runBlocking {
            val document =
                remoteComposeTestRule.captureDocument(context = context) {
                    RemoteButton(
                        modifier = RemoteModifier.buttonSizeModifier(),
                        enabled = true.rb,
                    ) {
                        RemoteText("button_enabled".rs)
                    }
                }
            val actualContent = document.displayHierarchy()

            assertEquals(expectedContent.normalizeWhiteSpace(), actualContent.normalizeWhiteSpace())
        }
    }

    @Test
    fun button_disabled_click_modifier_is_not_added() {
        val expectedContent =
            """
DATA_TEXT<42> = "button_disabled"
ROOT [-2:-1] = [0.0, 0.0, 0.0, 0.0] VISIBLE
  ROW [-3:-1] = [0.0, 0.0, 73.5, 31.5] VISIBLE
    MODIFIERS
      HEIGHT_IN = [52.0, 3.4028235E38]
      WIDTH_IN = [12.0, 3.4028235E38]
      DRAW_CONTENT
      SEMANTICS = SEMANTICS BUTTON disabled
      PADDING = [36.75, 15.75, 36.75, 15.75]
    TEXT_LAYOUT [-5:-1] = [0.0, 0.0, 0.0, 0.0] VISIBLE (42:"null")
      MODIFIERS"""
                .trimIndent()

        runBlocking {
            val document =
                remoteComposeTestRule.captureDocument(context = context) {
                    RemoteButton(
                        modifier = RemoteModifier.buttonSizeModifier(),
                        enabled = false.rb,
                    ) {
                        RemoteText("button_disabled".rs)
                    }
                }
            val actualContent = document.displayHierarchy()

            assertEquals(expectedContent.normalizeWhiteSpace(), actualContent.normalizeWhiteSpace())
        }
    }

    // Replace all sequences of whitespace (including newlines, tabs) with a single space. Then
    // trim leading/trailing spaces from the whole string
    private fun String.normalizeWhiteSpace() = this.replace(Regex("``s+"), " ").trim()

    @Composable
    @RemoteComposable
    private fun Center(
        modifier: RemoteModifier,
        content: @Composable @RemoteComposable () -> Unit,
    ) {
        RemoteBox(
            modifier,
            horizontalAlignment = RemoteAlignment.CenterHorizontally,
            verticalArrangement = RemoteArrangement.Center,
            content = content,
        )
    }
}
