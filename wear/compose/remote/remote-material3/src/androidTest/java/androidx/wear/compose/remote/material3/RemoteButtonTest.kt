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
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rememberRemoteBitmapValue
import androidx.compose.remote.player.compose.test.utils.screenshot.TargetPlayer
import androidx.compose.remote.player.compose.test.utils.screenshot.rule.RemoteComposeScreenshotTestRule
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.wear.compose.remote.material3.Material3ImageTest.Companion.createImage
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
        CreationDisplayInfo(500, 500, context.resources.displayMetrics.density)

    @Test
    fun button_enabled() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    enabled = RemoteBoolean(true),
                    content = { RemoteText(RemoteString("button_enabled")) },
                )
            }
        }
    }

    @Test
    fun button_disabled() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    enabled = RemoteBoolean(false),
                ) {
                    RemoteText(RemoteString("button_disabled"))
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
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    contentPadding = RemotePaddingValues(40.rdp),
                    colors = colors,
                ) {
                    RemoteText(RemoteString("button_overrides_colors"))
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
                    contentPadding = RemotePaddingValues(150.rdp),
                ) {
                    RemoteText(RemoteString("button_overrides_padding"))
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
                    RemoteText(RemoteString("button_overrides_size"))
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
                        RemoteString("button_overrides_textStyle"),
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
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    border = 8.rdp,
                    borderColor = RemoteColor(Color.Green),
                ) {
                    RemoteText(RemoteString("button_with_border"))
                }
            }
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
                    RemoteText(RemoteString("button_with_circle_shape"))
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
                    RemoteText(RemoteString("image_background"))
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
                val enabled = RemoteBoolean(false)
                val containerPainter =
                    RemoteButtonDefaults.containerPainter(painterRemoteBitmap(backgroundImage))
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    enabled = enabled,
                    containerPainter = containerPainter,
                ) {
                    RemoteText(RemoteString("disable_image_background"))
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
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    icon = {
                        RemoteIcon(
                            imageVector = TestImageVectors.VolumeUp,
                            contentDescription = null,
                            tint = RemoteButtonDefaults.buttonColors().iconColor,
                        )
                    },
                    secondaryLabel = { RemoteText(RemoteString("secondaryLabel")) },
                    label = { RemoteText(RemoteString("label")) },
                )
            }
        }
    }

    @Test
    fun button_with_icon_and_label() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    icon = {
                        RemoteIcon(
                            imageVector = TestImageVectors.VolumeUp,
                            contentDescription = null,
                            tint = RemoteButtonDefaults.buttonColors().iconColor,
                        )
                    },
                    label = { RemoteText(RemoteString("label")) },
                )
            }
        }
    }

    @Test
    fun button_with_label_and_secondary_label() {
        remoteComposeTestRule.runScreenshotTest(
            backgroundColor = Color.Black,
            creationDisplayInfo = creationDisplayInfo,
        ) {
            Center(RemoteModifier.fillMaxSize()) {
                RemoteButton(
                    modifier = RemoteModifier.buttonSizeModifier(),
                    secondaryLabel = { RemoteText(RemoteString("secondaryLabel")) },
                    label = { RemoteText(RemoteString("label")) },
                )
            }
        }
    }

    @Test
    fun button_enabled_click_modifier_is_added() {
        val expectedContent =
            """
DATA_TEXT<42> = "button_enabled"
ROOT [-2:-1] = [0.0, 0.0, 0.0, 0.0] VISIBLE
  ROW [-3:-1] = [0.0, 0.0, 28.0, 12.0] VISIBLE
    MODIFIERS
      HEIGHT_IN = [52.0, 3.4028235E38]
      WIDTH_IN = [12.0, 3.4028235E38]
      DRAW_CONTENT
      CLICK_MODIFIER
      SEMANTICS = SEMANTICS BUTTON
      PADDING = [14.0, 6.0, 14.0, 6.0]
    TEXT_LAYOUT [-5:-1] = [0.0, 0.0, 0.0, 0.0] VISIBLE (42:"null")
      MODIFIERS"""
                .trimIndent()
        runBlocking {
            val document =
                remoteComposeTestRule.captureDocument(context = context) {
                    RemoteButton(
                        modifier = RemoteModifier.buttonSizeModifier(),
                        enabled = RemoteBoolean(true),
                    ) {
                        RemoteText(RemoteString("button_enabled"))
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
  ROW [-3:-1] = [0.0, 0.0, 28.0, 12.0] VISIBLE
    MODIFIERS
      HEIGHT_IN = [52.0, 3.4028235E38]
      WIDTH_IN = [12.0, 3.4028235E38]
      DRAW_CONTENT
      SEMANTICS = SEMANTICS BUTTON disabled
      PADDING = [14.0, 6.0, 14.0, 6.0]
    TEXT_LAYOUT [-5:-1] = [0.0, 0.0, 0.0, 0.0] VISIBLE (42:"null")
      MODIFIERS"""
                .trimIndent()

        runBlocking {
            val document =
                remoteComposeTestRule.captureDocument(context = context) {
                    RemoteButton(
                        modifier = RemoteModifier.buttonSizeModifier(),
                        enabled = RemoteBoolean(false),
                    ) {
                        RemoteText(RemoteString("button_disabled"))
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
