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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.xr.glimmer.samples.SurfaceSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class SurfaceScreenshotTest() {

    @get:Rule val rule = createComposeRule()

    @get:Rule val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_DIRECTORY)

    @Test
    fun surface() {
        rule.setGlimmerThemeContent { SurfaceSample() }
        rule.assertRootAgainstGolden("surface", screenshotRule)
    }

    @Test
    fun surface_focused_defaultRoundRectBorder() {
        rule.mainClock.autoAdvance = false
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(interactionSource = alwaysFocusedInteractionSource)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text("This is a surface")
            }
        }
        rule.assertRootAgainstGolden("surface_focused_defaultBorder", screenshotRule)
    }

    @Test
    fun surface_focused_defaultRoundRectBorder_animation() {
        rule.mainClock.autoAdvance = false
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(interactionSource = alwaysFocusedInteractionSource)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text("This is a surface")
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.assertRootAgainstGolden("surface_focused_defaultBorder_animation", screenshotRule)
    }

    @Test
    fun surface_focused_rectBorder() {
        rule.mainClock.autoAdvance = false
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(
                        shape = RectangleShape,
                        interactionSource = alwaysFocusedInteractionSource,
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text("This is a surface")
            }
        }
        rule.assertRootAgainstGolden("surface_focused_rectBorder", screenshotRule)
    }

    @Test
    fun surface_focused_rectBorder_animation() {
        rule.mainClock.autoAdvance = false
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(
                        shape = RectangleShape,
                        interactionSource = alwaysFocusedInteractionSource,
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text("This is a surface")
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.assertRootAgainstGolden("surface_focused_rectBorder_animation", screenshotRule)
    }

    @Test
    fun surface_focused_genericBorder() {
        rule.mainClock.autoAdvance = false
        rule.setGlimmerThemeContent {
            Box(
                Modifier.size(100.dp)
                    .surface(
                        shape = DoubleTriangleShape,
                        interactionSource = alwaysFocusedInteractionSource,
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {}
        }
        rule.assertRootAgainstGolden("surface_focused_genericBorder", screenshotRule)
    }

    @Test
    fun surface_focused_genericBorder_animation() {
        rule.mainClock.autoAdvance = false
        rule.setGlimmerThemeContent {
            Box(
                Modifier.size(100.dp)
                    .surface(
                        shape = DoubleTriangleShape,
                        interactionSource = alwaysFocusedInteractionSource,
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {}
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.assertRootAgainstGolden("surface_focused_genericBorder_animation", screenshotRule)
    }

    /**
     * Practically a surface cannot be pressed without also being focused, but we test them in
     * isolation as well to make it easier to identify changes. See [surface_focused_and_pressed]
     * for the combined state.
     */
    @Test
    fun surface_pressed() {
        rule.mainClock.autoAdvance = false
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(interactionSource = alwaysPressedInteractionSource, onClick = {})
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text("This is a surface")
            }
        }
        // Skip until after the animation has finished
        rule.mainClock.advanceTimeBy(5000)
        rule.assertRootAgainstGolden("surface_pressed", screenshotRule)
    }

    /**
     * Practically a surface cannot be pressed without also being focused, but we test them in
     * isolation as well to make it easier to identify changes. See [surface_focused_and_pressed]
     * for the combined state.
     */
    @Test
    fun surface_pressed_animation() {
        rule.mainClock.autoAdvance = false
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(interactionSource = alwaysPressedInteractionSource, onClick = {})
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text("This is a surface")
            }
        }
        // Advance to partway through the animation
        rule.mainClock.advanceTimeBy(200)
        rule.assertRootAgainstGolden("surface_pressed_animation", screenshotRule)
    }

    @Test
    fun surface_focused_and_pressed() {
        rule.mainClock.autoAdvance = false
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(
                        interactionSource = alwaysFocusedAndPressedInteractionSource,
                        onClick = {},
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text("This is a surface")
            }
        }
        // Skip until after the animation has finished
        rule.mainClock.advanceTimeBy(5000)
        rule.assertRootAgainstGolden("surface_focused_and_pressed", screenshotRule)
    }

    @Test
    fun custom_surface() {
        rule.setGlimmerThemeContent {
            Box(
                Modifier.size(100.dp)
                    .surface(
                        shape = DoubleTriangleShape,
                        color = Color.Red,
                        border =
                            BorderStroke(
                                5.dp,
                                Brush.linearGradient(0f to Color.Blue, 1f to Color.Green),
                            ),
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("!")
            }
        }
        rule.assertRootAgainstGolden("surface_custom", screenshotRule)
    }

    private object DoubleTriangleShape : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
            Outline.Generic(
                Path().apply {
                    lineTo(size.width / 2f, size.height / 2f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
            )
    }

    private val alwaysFocusedInteractionSource =
        StaticMutableInteractionSource(FocusInteraction.Focus())

    private val alwaysPressedInteractionSource =
        StaticMutableInteractionSource(PressInteraction.Press(Offset.Zero))

    private val alwaysFocusedAndPressedInteractionSource =
        StaticMutableInteractionSource(
            FocusInteraction.Focus(),
            PressInteraction.Press(Offset.Zero),
        )

    private class StaticMutableInteractionSource(vararg interactionsToShow: Interaction) :
        MutableInteractionSource {
        override val interactions: Flow<Interaction> = interactionsToShow.asFlow()

        override suspend fun emit(interaction: Interaction) {}

        override fun tryEmit(interaction: Interaction): Boolean = true
    }
}
