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

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.testutils.assertIsEqualTo
import androidx.compose.testutils.assertShape
import androidx.compose.ui.ExperimentalIndirectTouchTypeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.indirect.IndirectTouchEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performIndirectTouchEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.core.view.InputDeviceCompat.SOURCE_TOUCH_NAVIGATION
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
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
class CardTest {
    @get:Rule val rule = createComposeRule()

    // Enter non-touch mode for tests, so that clickables can be focused.
    // TODO(b/267253920): Add a compose test API to set/reset InputMode.
    @Before
    fun enterNonTouchMode() = InstrumentationRegistry.getInstrumentation().setInTouchMode(false)

    // TODO(b/267253920): Add a compose test API to set/reset InputMode.
    @After fun resetTouchMode() = InstrumentationRegistry.getInstrumentation().resetInTouchMode()

    @Test
    fun semantics() {
        rule.setGlimmerThemeContent {
            Box { Card(modifier = Modifier.testTag("card")) { Text("This is a card") } }
        }

        rule
            .onNodeWithTag("card")
            .assert(isFocusable())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    @Test
    fun semantics_clickable() {
        rule.setGlimmerThemeContent {
            Box {
                Card(modifier = Modifier.testTag("card"), onClick = {}) { Text("This is a card") }
            }
        }

        rule
            .onNodeWithTag("card")
            .assert(isFocusable())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assertHasClickAction()
            .assertIsEnabled()
    }

    @Test
    fun shapeAndColorFromThemeIsUsed() {
        lateinit var expectedShape: Shape
        val surfaceColor = Color.Blue
        rule.setGlimmerThemeContent {
            GlimmerTheme(Colors(surface = surfaceColor)) {
                expectedShape = GlimmerTheme.shapes.medium
                Card(modifier = Modifier.testTag("card"), border = null) {
                    Box(Modifier.size(100.dp, 100.dp))
                }
            }
        }

        rule
            .onNodeWithTag("card")
            .captureToImage()
            .assertShape(
                density = rule.density,
                shape = expectedShape,
                shapeColor = surfaceColor,
                backgroundColor = Color.Black,
                antiAliasingGap = with(rule.density) { 1.dp.toPx() },
            )
    }

    @Test
    fun setsLocalTextStyle() {
        lateinit var actualTitleTextStyle: TextStyle
        lateinit var actualSubtitleTextStyle: TextStyle
        lateinit var actualContentTextStyle: TextStyle
        lateinit var expectedTitleTextStyle: TextStyle
        lateinit var expectedSubtitleTextStyle: TextStyle
        lateinit var expectedContentTextStyle: TextStyle
        rule.setGlimmerThemeContent {
            expectedTitleTextStyle = GlimmerTheme.typography.bodyMedium
            expectedSubtitleTextStyle = GlimmerTheme.typography.bodySmall
            expectedContentTextStyle = GlimmerTheme.typography.bodySmall
            Card(
                title = { actualTitleTextStyle = LocalTextStyle.current },
                subtitle = { actualSubtitleTextStyle = LocalTextStyle.current },
            ) {
                actualContentTextStyle = LocalTextStyle.current
            }
        }

        rule.runOnIdle {
            assertThat(actualTitleTextStyle).isEqualTo(expectedTitleTextStyle)
            assertThat(actualSubtitleTextStyle).isEqualTo(expectedSubtitleTextStyle)
            assertThat(actualContentTextStyle).isEqualTo(expectedContentTextStyle)
        }
    }

    @Test
    fun setsContentColor() {
        var primary = Color.Unspecified
        var titleContentColor = Color.Unspecified
        var subtitleContentColor = Color.Unspecified
        var leadingIconContentColor = Color.Unspecified
        var trailingIconContentColor = Color.Unspecified
        var contentContentColor = Color.Unspecified
        rule.setGlimmerThemeContent {
            primary = GlimmerTheme.colors.primary
            Card(
                title = {
                    Box(
                        DelegatableNodeProviderElement {
                            titleContentColor = it?.currentContentColor() ?: Color.Unspecified
                        }
                    )
                },
                subtitle = {
                    Box(
                        DelegatableNodeProviderElement {
                            subtitleContentColor = it?.currentContentColor() ?: Color.Unspecified
                        }
                    )
                },
                leadingIcon = {
                    Box(
                        DelegatableNodeProviderElement {
                            leadingIconContentColor = it?.currentContentColor() ?: Color.Unspecified
                        }
                    )
                },
                trailingIcon = {
                    Box(
                        DelegatableNodeProviderElement {
                            trailingIconContentColor =
                                it?.currentContentColor() ?: Color.Unspecified
                        }
                    )
                },
            ) {
                Box(
                    DelegatableNodeProviderElement {
                        contentContentColor = it?.currentContentColor() ?: Color.Unspecified
                    }
                )
            }
        }

        rule.runOnIdle {
            assertThat(titleContentColor).isEqualTo(Color.White)
            assertThat(subtitleContentColor).isEqualTo(Color.White)
            assertThat(leadingIconContentColor).isEqualTo(primary)
            assertThat(trailingIconContentColor).isEqualTo(primary)
            assertThat(contentContentColor).isEqualTo(Color.White)
        }
    }

    @Test
    fun setsLocalIconSize() {
        var actualLeadingIconSize: Dp? = null
        var actualTrailingIconSize: Dp? = null
        var expectedIconSize: Dp? = null
        rule.setGlimmerThemeContent {
            expectedIconSize = GlimmerTheme.iconSizes.large
            Card(
                leadingIcon = { actualLeadingIconSize = LocalIconSize.current },
                trailingIcon = { actualTrailingIconSize = LocalIconSize.current },
            ) {}
        }

        rule.runOnIdle {
            assertThat(actualLeadingIconSize!!).isEqualTo(expectedIconSize!!)
            assertThat(actualTrailingIconSize!!).isEqualTo(expectedIconSize)
        }
    }

    @Test
    fun emitsFocusInteractions() {
        val interactionSource = MutableInteractionSource()
        val (focusRequester, otherFocusRequester) = FocusRequester.createRefs()

        lateinit var scope: CoroutineScope

        rule.setGlimmerThemeContent {
            scope = rememberCoroutineScope()
            Box {
                Card(
                    modifier = Modifier.testTag("card").focusRequester(focusRequester),
                    interactionSource = interactionSource,
                ) {
                    Text("This is a card")
                }
                Box(Modifier.size(100.dp).focusRequester(otherFocusRequester).focusTarget())
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

    @OptIn(ExperimentalIndirectTouchTypeApi::class)
    @Test
    fun emitsPressInteractions_clickable() {
        val interactionSource = MutableInteractionSource()
        val focusRequester = FocusRequester()

        lateinit var scope: CoroutineScope

        rule.setGlimmerThemeContent {
            scope = rememberCoroutineScope()
            Box {
                Card(
                    modifier = Modifier.testTag("card").focusRequester(focusRequester),
                    interactionSource = interactionSource,
                    onClick = {},
                ) {
                    Text("This is a card")
                }
            }
        }

        val interactions = mutableListOf<Interaction>()

        scope.launch { interactionSource.interactions.collect { interactions.add(it) } }

        rule.runOnIdle { focusRequester.requestFocus() }

        rule.runOnIdle { interactions.clear() }

        val currentTime = SystemClock.uptimeMillis()

        val down =
            MotionEvent.obtain(
                currentTime, // downTime,
                currentTime, // eventTime,
                MotionEvent.ACTION_DOWN,
                0f,
                0f,
                0,
            )
        down.source = SOURCE_TOUCH_NAVIGATION
        rule.onNodeWithTag("card").performIndirectTouchEvent(IndirectTouchEvent(down))

        rule.runOnIdle {
            assertThat(interactions).hasSize(1)
            assertThat(interactions.first()).isInstanceOf(PressInteraction.Press::class.java)
        }

        val up =
            MotionEvent.obtain(
                currentTime + 200L, // downTime,
                currentTime + 200L, // eventTime,
                MotionEvent.ACTION_UP,
                0f,
                0f,
                0,
            )
        up.source = SOURCE_TOUCH_NAVIGATION
        rule.onNodeWithTag("card").performIndirectTouchEvent(IndirectTouchEvent(up))

        rule.runOnIdle {
            assertThat(interactions).hasSize(2)
            assertThat(interactions.first()).isInstanceOf(PressInteraction.Press::class.java)
            assertThat(interactions[1]).isInstanceOf(PressInteraction.Release::class.java)
            assertThat((interactions[1] as PressInteraction.Release).press)
                .isEqualTo(interactions[0])
        }
    }

    @Test
    fun positioning() {
        rule.setGlimmerThemeContent {
            Column {
                Spacer(Modifier.height(10.dp).fillMaxWidth().testTag("spacer"))
                Card(modifier = Modifier.testTag("card")) {
                    Text("This is a card", modifier = Modifier.testTag("content"))
                }
            }
        }

        val spacerBounds =
            rule.onNodeWithTag("spacer", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val contentBounds =
            rule.onNodeWithTag("content", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardBounds =
            rule.onNodeWithTag("card", useUnmergedTree = true).getUnclippedBoundsInRoot()

        // Content should typically be center aligned for cards without a title / subtitle, since
        // the minimum height of the item should be larger than the height of the text
        (((cardBounds.height - contentBounds.height) / 2f) + cardBounds.top).assertIsEqualTo(
            contentBounds.top,
            "Padding between top of card and top of content.",
        )

        (contentBounds.left - cardBounds.left).assertIsEqualTo(
            24.dp,
            "Padding between the start of the card and the start of the content.",
        )

        // The width should fill the max width, like with the spacer
        cardBounds.width.assertIsEqualTo(spacerBounds.width, "width of card.")
        cardBounds.height.assertIsEqualTo(80.dp, "height of card.")
    }

    @Test
    fun positioning_titleAndSubtitle() {
        rule.setGlimmerThemeContent {
            Column {
                Spacer(Modifier.height(10.dp).fillMaxWidth().testTag("spacer"))
                Card(
                    modifier = Modifier.testTag("card"),
                    title = { Text("Title", modifier = Modifier.testTag("title")) },
                    subtitle = { Text("Subtitle", modifier = Modifier.testTag("subtitle")) },
                ) {
                    Text("This is a card", modifier = Modifier.testTag("content"))
                }
            }
        }

        val spacerBounds =
            rule.onNodeWithTag("spacer", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val contentBounds =
            rule.onNodeWithTag("content", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titleBounds =
            rule.onNodeWithTag("title", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val subtitleBounds =
            rule.onNodeWithTag("subtitle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardBounds =
            rule.onNodeWithTag("card", useUnmergedTree = true).getUnclippedBoundsInRoot()

        // Title should be top aligned when the height of the content, title, and subtitle is
        // greater than minimum card height
        (titleBounds.top - cardBounds.top).assertIsEqualTo(
            24.dp,
            "Padding between top of card and top of title.",
        )

        (titleBounds.left - cardBounds.left).assertIsEqualTo(
            24.dp,
            "Padding between the start of the card and the start of the title.",
        )

        (subtitleBounds.left - cardBounds.left).assertIsEqualTo(
            24.dp,
            "Padding between the start of the card and the start of the subtitle.",
        )

        (contentBounds.left - cardBounds.left).assertIsEqualTo(
            24.dp,
            "Padding between the start of the card and the start of the content.",
        )

        titleBounds.bottom.assertIsEqualTo(
            subtitleBounds.top - 3.dp,
            "Padding between the bottom of the title and the top of the subtitle.",
        )

        subtitleBounds.bottom.assertIsEqualTo(
            contentBounds.top - 3.dp,
            "Padding between the bottom of the subtitle and the top of the content.",
        )

        (cardBounds.bottom - contentBounds.bottom).assertIsEqualTo(
            24.dp,
            "Padding between bottom of card and bottom of content.",
        )

        // The width should fill the max width, like with the spacer
        cardBounds.width.assertIsEqualTo(spacerBounds.width, "width of card.")
        // Title and subtitle will likely make the item taller than the minimum height, so just
        // assert we are at least the minimum height
        assertThat(cardBounds.height.value).isAtLeast(80)
    }

    @Test
    fun positioning_withIcons() {
        rule.setGlimmerThemeContent {
            Column {
                Spacer(Modifier.height(10.dp).fillMaxWidth().testTag("spacer"))
                Card(
                    modifier = Modifier.testTag("card"),
                    leadingIcon = {
                        Icon(
                            FavoriteIcon,
                            contentDescription = "Localized description",
                            modifier = Modifier.testTag("leadingIcon"),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            FavoriteIcon,
                            contentDescription = "Localized description",
                            modifier = Modifier.testTag("trailingIcon"),
                        )
                    },
                ) {
                    Text("This is a card", modifier = Modifier.testTag("content"))
                }
            }
        }

        val spacerBounds =
            rule.onNodeWithTag("spacer", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val contentBounds =
            rule.onNodeWithTag("content", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val leadingIconBounds =
            rule.onNodeWithTag("leadingIcon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val trailingIconBounds =
            rule.onNodeWithTag("trailingIcon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardBounds =
            rule.onNodeWithTag("card", useUnmergedTree = true).getUnclippedBoundsInRoot()

        (leadingIconBounds.top - cardBounds.top).assertIsEqualTo(
            24.dp,
            "Padding between top of card and top of leading icon.",
        )

        (leadingIconBounds.left - cardBounds.left).assertIsEqualTo(
            24.dp,
            "Padding between start of card and start of leading icon.",
        )

        // Content should typically be center aligned for cards without title / subtitle, since
        // the minimum height of the item should be larger than the height of the content
        (((cardBounds.height - contentBounds.height) / 2f) + cardBounds.top).assertIsEqualTo(
            contentBounds.top,
            "Padding between top of card and top of content.",
        )

        (contentBounds.left - leadingIconBounds.right).assertIsEqualTo(
            12.dp,
            "Padding between end of leading icon and start of content.",
        )

        (trailingIconBounds.top - cardBounds.top).assertIsEqualTo(
            24.dp,
            "Padding between top of card and top of trailing icon.",
        )

        (cardBounds.right - trailingIconBounds.right).assertIsEqualTo(
            24.dp,
            "Padding between end of trailing icon and end of card.",
        )

        // The width should fill the max width, like with the spacer
        cardBounds.width.assertIsEqualTo(spacerBounds.width, "width of card.")
        cardBounds.height.assertIsEqualTo(
            /* vertical padding * 2 + icon height*/ (24 + 24 + 56).dp,
            "height of card.",
        )
    }

    @Test
    fun positioning_titleAndSubtitle_withIcons() {
        rule.setGlimmerThemeContent {
            Column {
                Spacer(Modifier.height(10.dp).fillMaxWidth().testTag("spacer"))
                Card(
                    modifier = Modifier.testTag("card"),
                    title = { Text("Title", modifier = Modifier.testTag("title")) },
                    subtitle = { Text("Subtitle", modifier = Modifier.testTag("subtitle")) },
                    leadingIcon = {
                        Icon(
                            FavoriteIcon,
                            contentDescription = "Localized description",
                            modifier = Modifier.testTag("leadingIcon"),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            FavoriteIcon,
                            contentDescription = "Localized description",
                            modifier = Modifier.testTag("trailingIcon"),
                        )
                    },
                ) {
                    Text("This is a card", modifier = Modifier.testTag("content"))
                }
            }
        }

        val spacerBounds =
            rule.onNodeWithTag("spacer", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val contentBounds =
            rule.onNodeWithTag("content", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titleBounds =
            rule.onNodeWithTag("title", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val subtitleBounds =
            rule.onNodeWithTag("subtitle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val leadingIconBounds =
            rule.onNodeWithTag("leadingIcon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val trailingIconBounds =
            rule.onNodeWithTag("trailingIcon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardBounds =
            rule.onNodeWithTag("card", useUnmergedTree = true).getUnclippedBoundsInRoot()

        (leadingIconBounds.top - cardBounds.top).assertIsEqualTo(
            24.dp,
            "Padding between top of card and top of leading icon.",
        )

        (leadingIconBounds.left - cardBounds.left).assertIsEqualTo(
            24.dp,
            "Padding between start of card and start of leading icon.",
        )

        // Title should be top aligned when the height of the content, title, and subtitle is
        // greater than minimum card height
        (titleBounds.top - cardBounds.top).assertIsEqualTo(
            24.dp,
            "Padding between top of card and top of title.",
        )

        (titleBounds.left - leadingIconBounds.right).assertIsEqualTo(
            12.dp,
            "Padding between end of leading icon and start of title.",
        )

        (subtitleBounds.left - leadingIconBounds.right).assertIsEqualTo(
            12.dp,
            "Padding between end of leading icon and start of subtitle.",
        )

        (contentBounds.left - leadingIconBounds.right).assertIsEqualTo(
            12.dp,
            "Padding between end of leading icon and start of content.",
        )

        titleBounds.bottom.assertIsEqualTo(
            subtitleBounds.top - 3.dp,
            "Padding between the bottom of the title and the top of the subtitle.",
        )

        subtitleBounds.bottom.assertIsEqualTo(
            contentBounds.top - 3.dp,
            "Padding between the bottom of the subtitle and the top of the content.",
        )

        (cardBounds.bottom - contentBounds.bottom).assertIsEqualTo(
            24.dp,
            "Padding between bottom of card and bottom of content.",
        )

        (trailingIconBounds.top - cardBounds.top).assertIsEqualTo(
            24.dp,
            "Padding between top of card and top of trailing icon.",
        )

        (cardBounds.right - trailingIconBounds.right).assertIsEqualTo(
            24.dp,
            "Padding between end of trailing icon and end of card.",
        )

        // The width should fill the max width, like with the spacer
        cardBounds.width.assertIsEqualTo(spacerBounds.width, "width of card.")
        // Title and subtitle will likely make the item taller than the minimum height, so just
        // assert we are at least the minimum height
        assertThat(cardBounds.height.value).isAtLeast(80)
    }

    @Test
    fun positioning_titleAndSubtitle_withIcons_longText() {
        rule.setGlimmerThemeContent {
            Column {
                Spacer(Modifier.height(10.dp).fillMaxWidth().testTag("spacer"))
                Card(
                    modifier = Modifier.testTag("card"),
                    title = { Text("Title", modifier = Modifier.testTag("title")) },
                    subtitle = { Text("Subtitle", modifier = Modifier.testTag("subtitle")) },
                    leadingIcon = {
                        Icon(
                            FavoriteIcon,
                            contentDescription = "Localized description",
                            modifier = Modifier.testTag("leadingIcon"),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            FavoriteIcon,
                            contentDescription = "Localized description",
                            modifier = Modifier.testTag("trailingIcon"),
                        )
                    },
                ) {
                    Text("This is a card", modifier = Modifier.testTag("content"))
                }
            }
        }

        val spacerBounds =
            rule.onNodeWithTag("spacer", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val contentBounds =
            rule.onNodeWithTag("content", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titleBounds =
            rule.onNodeWithTag("title", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val subtitleBounds =
            rule.onNodeWithTag("subtitle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val leadingIconBounds =
            rule.onNodeWithTag("leadingIcon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val trailingIconBounds =
            rule.onNodeWithTag("trailingIcon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardBounds =
            rule.onNodeWithTag("card", useUnmergedTree = true).getUnclippedBoundsInRoot()

        (leadingIconBounds.top - cardBounds.top).assertIsEqualTo(
            24.dp,
            "Padding between top of card and top of leading icon.",
        )

        (leadingIconBounds.left - cardBounds.left).assertIsEqualTo(
            24.dp,
            "Padding between start of card and start of leading icon.",
        )

        // Title should be top aligned when the height of the content, title, and subtitle is
        // greater than minimum card height
        (titleBounds.top - cardBounds.top).assertIsEqualTo(
            24.dp,
            "Padding between top of card and top of title.",
        )

        (titleBounds.left - leadingIconBounds.right).assertIsEqualTo(
            12.dp,
            "Padding between end of leading icon and start of title.",
        )

        (subtitleBounds.left - leadingIconBounds.right).assertIsEqualTo(
            12.dp,
            "Padding between end of leading icon and start of subtitle.",
        )

        (contentBounds.left - leadingIconBounds.right).assertIsEqualTo(
            12.dp,
            "Padding between end of leading icon and start of content.",
        )

        titleBounds.bottom.assertIsEqualTo(
            subtitleBounds.top - 3.dp,
            "Padding between the bottom of the title and the top of the subtitle.",
        )

        subtitleBounds.bottom.assertIsEqualTo(
            contentBounds.top - 3.dp,
            "Padding between the bottom of the subtitle and the top of the content.",
        )

        (cardBounds.bottom - contentBounds.bottom).assertIsEqualTo(
            24.dp,
            "Padding between bottom of card and bottom of content.",
        )

        (trailingIconBounds.top - cardBounds.top).assertIsEqualTo(
            24.dp,
            "Padding between top of card and top of trailing icon.",
        )

        (cardBounds.right - trailingIconBounds.right).assertIsEqualTo(
            24.dp,
            "Padding between end of trailing icon and end of card.",
        )

        // The width should fill the max width, like with the spacer
        cardBounds.width.assertIsEqualTo(spacerBounds.width, "width of card.")
        assertThat(cardBounds.height.value).isAtLeast(80)
    }

    @Test
    fun minHeightCanBeOverridden() {
        rule.setGlimmerThemeContent {
            Card(
                contentPadding = PaddingValues(),
                modifier = Modifier.requiredHeightIn(30.dp).testTag("card"),
            ) {
                Spacer(Modifier.requiredSize(10.dp))
            }
        }

        rule.onNodeWithTag("card").apply {
            with(getBoundsInRoot()) { height.assertIsEqualTo(30.dp, "height") }
        }
    }
}
