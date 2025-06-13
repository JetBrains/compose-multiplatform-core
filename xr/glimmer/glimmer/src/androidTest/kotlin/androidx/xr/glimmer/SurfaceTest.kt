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
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.testutils.assertPixels
import androidx.compose.testutils.toList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isNotFocusable
import androidx.compose.ui.test.isNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class SurfaceTest {

    @get:Rule val rule = createComposeRule()

    @Before
    fun before() {
        isDebugInspectorInfoEnabled = true
    }

    @After
    fun after() {
        isDebugInspectorInfoEnabled = false
    }

    @Test
    fun equality() {
        lateinit var surface: Modifier
        lateinit var surfaceWithSameParameters: Modifier
        lateinit var surfaceWithDifferentParameters: Modifier
        rule.setGlimmerThemeContent {
            surface =
                Modifier.surface(
                    shape = RectangleShape,
                    color = Color.Blue,
                    contentColor = Color.Magenta,
                    border = BorderStroke(1.dp, Color.Red),
                )
            surfaceWithSameParameters =
                Modifier.surface(
                    shape = RectangleShape,
                    color = Color.Blue,
                    contentColor = Color.Magenta,
                    border = BorderStroke(1.dp, Color.Red),
                )
            surfaceWithDifferentParameters =
                Modifier.surface(
                    shape = CircleShape,
                    color = Color.Blue,
                    contentColor = Color.Magenta,
                    border = BorderStroke(1.dp, Color.Red),
                )
        }

        rule.runOnIdle {
            assertThat(surface).isEqualTo(surfaceWithSameParameters)
            assertThat(surface).isNotEqualTo(surfaceWithDifferentParameters)
        }
    }

    @Test
    fun semantics_focusable() {
        val focusRequester = FocusRequester()
        rule.setGlimmerThemeContent {
            Box(Modifier.size(100.dp).focusRequester(focusRequester).surface().testTag("surface"))
        }
        rule
            .onNodeWithTag("surface")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
            .assert(isFocusable())
            .assert(isNotFocused())

        rule.runOnIdle { focusRequester.requestFocus() }

        rule
            .onNodeWithTag("surface")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
            .assert(isFocusable())
            .assert(isFocused())
    }

    @Test
    fun semantics_not_focusable() {
        val focusRequester = FocusRequester()
        rule.setGlimmerThemeContent {
            Box(
                Modifier.size(100.dp)
                    .focusRequester(focusRequester)
                    .surface(focusable = false)
                    .testTag("surface")
            )
        }
        rule
            .onNodeWithTag("surface")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
            .assert(isNotFocusable())

        rule.runOnIdle { focusRequester.requestFocus() }

        rule
            .onNodeWithTag("surface")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
            .assert(isNotFocusable())
    }

    @Test
    fun inspectorValue() {
        rule.setContent {
            val modifiers = Modifier.surface().toList()
            assertThat((modifiers[0] as InspectableValue).nameFallback).isEqualTo("graphicsLayer")
            assertThat((modifiers[1] as InspectableValue).nameFallback).isEqualTo("border")
            assertThat((modifiers[2] as InspectableValue).nameFallback).isEqualTo("background")
            val surfaceModifier = modifiers[3] as InspectableValue
            assertThat(surfaceModifier.nameFallback).isEqualTo("surface")
            assertThat(surfaceModifier.valueOverride).isNull()
            assertThat(surfaceModifier.inspectableElements.map { it.name }.asIterable())
                .containsExactly("contentColor")
            assertThat((modifiers[4] as InspectableValue).nameFallback).isEqualTo("focusable")
        }
    }

    @Test
    fun clipsContent() {
        rule.setGlimmerThemeContent {
            with(LocalDensity.current) {
                val outerSize = 100.toDp()
                val innerSize = 50.toDp()
                Box(Modifier.size(outerSize).testTag("outerBox").background(Color.Red)) {
                    Box(
                        Modifier.size(innerSize)
                            .surface(shape = RectangleShape, color = Color.Blue, border = null)
                            .drawWithContent {
                                // Try and draw a rect that would fill the outerSize, if there was
                                // no clipping
                                drawRect(color = Color.Green, size = Size(100f, 100f))
                            }
                    )
                }
            }
        }
        rule.onNodeWithTag("outerBox").captureToImage().assertPixels(
            expectedSize = IntSize(100, 100)
        ) {
            if (it.x < 50 && it.y < 50) {
                // The inner surface should all be green
                Color.Green
            } else {
                // The outer box should be red, as the inner surface should clip the green
                Color.Red
            }
        }
    }

    @Test
    fun cachesBorder() {
        lateinit var defaultBorder: BorderStroke
        lateinit var anotherDefaultBorder: BorderStroke
        lateinit var customBorder: BorderStroke
        rule.setGlimmerThemeContent {
            defaultBorder = SurfaceDefaults.border()
            anotherDefaultBorder = SurfaceDefaults.border()
            customBorder = SurfaceDefaults.border(color = Color.Red)
        }

        rule.runOnIdle {
            assertThat(defaultBorder).isSameInstanceAs(anotherDefaultBorder)
            assertThat(defaultBorder).isNotEqualTo(customBorder)
        }
    }

    @Test
    fun borderValues() {
        lateinit var defaultBorder: BorderStroke
        lateinit var customBorder: BorderStroke
        var outline: Color = Color.Unspecified
        rule.setGlimmerThemeContent {
            outline = GlimmerTheme.colors.outline
            defaultBorder = SurfaceDefaults.border()
            customBorder = SurfaceDefaults.border(color = Color.Red)
        }

        rule.runOnIdle {
            assertThat((defaultBorder.brush as SolidColor).value).isEqualTo(outline)
            assertThat(defaultBorder.width).isEqualTo(2.dp)
            assertThat((customBorder.brush as SolidColor).value).isEqualTo(Color.Red)
            assertThat(customBorder.width).isEqualTo(2.dp)
        }
    }

    @Test
    fun providesContentColor_default() {
        var color: Color = Color.Unspecified
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface()
                    .then(
                        DelegatableNodeProviderElement {
                            color = it?.currentContentColor() ?: Color.Unspecified
                        }
                    )
            )
        }

        rule.runOnIdle { assertThat(color).isEqualTo(Color.White) }
    }

    @Test
    fun providesContentColor_calculatedFromBackground() {
        var node: DelegatableNode? = null
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(color = Color.White)
                    .then(DelegatableNodeProviderElement { node = it })
            )
        }

        // Surface color is white, so the content color should be black
        rule.runOnIdle { assertThat(node!!.currentContentColor()).isEqualTo(Color.Black) }
    }

    @Test
    fun providesContentColor_updates_backgroundColor() {
        var backgroundColor by mutableStateOf(Color.White)
        var node: DelegatableNode? = null
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(color = backgroundColor)
                    .then(DelegatableNodeProviderElement { node = it })
            )
        }

        rule.runOnIdle {
            // Surface color is white, so the content color should be black
            assertThat(node!!.currentContentColor()).isEqualTo(Color.Black)
            backgroundColor = Color.Black
        }

        rule.runOnIdle {
            // Surface color is now black, so the content color should be white
            assertThat(node!!.currentContentColor()).isEqualTo(Color.White)
        }
    }

    @Test
    fun providesContentColor_updates_contentColor() {
        var expectedColor by mutableStateOf(Color.White)
        var node: DelegatableNode? = null
        rule.setGlimmerThemeContent {
            Box(
                Modifier.surface(contentColor = expectedColor)
                    .then(DelegatableNodeProviderElement { node = it })
            )
        }

        rule.runOnIdle {
            assertThat(node!!.currentContentColor()).isEqualTo(Color.White)
            expectedColor = Color.Blue
        }

        rule.runOnIdle { assertThat(node!!.currentContentColor()).isEqualTo(Color.Blue) }
    }

    @Test
    fun focusable_emitsFocusInteractions() {
        val interactionSource = MutableInteractionSource()
        val (focusRequester, otherFocusRequester) = FocusRequester.createRefs()

        lateinit var scope: CoroutineScope

        rule.setGlimmerThemeContent {
            scope = rememberCoroutineScope()
            Box {
                Box(
                    Modifier.size(100.dp)
                        .focusRequester(focusRequester)
                        .surface(interactionSource = interactionSource)
                        .testTag("surface")
                )
                Box(Modifier.size(100.dp).focusRequester(otherFocusRequester).surface())
            }
        }

        val interactions = mutableListOf<Interaction>()

        scope.launch { interactionSource.interactions.collect { interactions.add(it) } }

        rule.runOnIdle { assertThat(interactions).isEmpty() }

        rule.runOnIdle { focusRequester.requestFocus() }

        rule.runOnIdle {
            assertThat(interactions).hasSize(1)
            assertThat(interactions.first()).isInstanceOf(FocusInteraction.Focus::class.java)
        }

        rule.runOnIdle { otherFocusRequester.requestFocus() }

        rule.runOnIdle {
            assertThat(interactions).hasSize(2)
            assertThat(interactions.first()).isInstanceOf(FocusInteraction.Focus::class.java)
            assertThat(interactions[1]).isInstanceOf(FocusInteraction.Unfocus::class.java)
            assertThat((interactions[1] as FocusInteraction.Unfocus).focus)
                .isEqualTo(interactions[0])
        }
    }

    @Test
    fun focusable_resetsFocusInteractions_whenNoLongerFocusable() {
        val interactionSource = MutableInteractionSource()
        val focusRequester = FocusRequester()
        var focusable by mutableStateOf(true)

        lateinit var scope: CoroutineScope

        rule.setGlimmerThemeContent {
            scope = rememberCoroutineScope()
            Box(
                Modifier.size(100.dp)
                    .focusRequester(focusRequester)
                    .surface(focusable = focusable, interactionSource = interactionSource)
                    .testTag("surface")
            )
        }

        val interactions = mutableListOf<Interaction>()

        scope.launch { interactionSource.interactions.collect { interactions.add(it) } }

        rule.runOnIdle { assertThat(interactions).isEmpty() }

        rule.runOnIdle { focusRequester.requestFocus() }

        rule.runOnIdle {
            assertThat(interactions).hasSize(1)
            assertThat(interactions.first()).isInstanceOf(FocusInteraction.Focus::class.java)
        }

        // Make surface no longer focusable, Interaction should be gone
        rule.runOnIdle { focusable = false }

        rule.runOnIdle {
            assertThat(interactions).hasSize(2)
            assertThat(interactions.first()).isInstanceOf(FocusInteraction.Focus::class.java)
            assertThat(interactions[1]).isInstanceOf(FocusInteraction.Unfocus::class.java)
            assertThat((interactions[1] as FocusInteraction.Unfocus).focus)
                .isEqualTo(interactions[0])
        }
    }
}
