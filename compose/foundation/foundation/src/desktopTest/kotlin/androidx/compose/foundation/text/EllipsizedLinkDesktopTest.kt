/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.foundation.text

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class EllipsizedLinkDesktopTest {
    @Suppress("DEPRECATION") @get:Rule val rule = createComposeRule()

    @Test
    fun hiddenLink_hasNoClickableArea() {
        var clicks = 0
        rule.setContent {
            BasicText(
                text =
                    buildAnnotatedString {
                        append("123".repeat(20))
                        withLink(
                            LinkAnnotation.Clickable(
                                "hidden",
                                linkInteractionListener = { clicks++ },
                            )
                        ) {
                            append("hidden link")
                        }
                    },
                modifier = Modifier.width(100.dp).testTag("text"),
                style = TextStyle(fontSize = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val link =
            rule
                .onNode(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.LinkTestMarker),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNode()
        assertThat(link.boundsInRoot.width * link.boundsInRoot.height).isEqualTo(0f)
        rule.onNodeWithTag("text").performTouchInput { click(center) }
        rule.runOnIdle { assertThat(clicks).isEqualTo(0) }
    }

    @Test
    fun partiallyVisibleLink_keepsVisibleCharactersClickable() {
        var clicks = 0
        lateinit var layout: TextLayoutResult
        rule.setContent {
            BasicText(
                text =
                    buildAnnotatedString {
                        append("a ")
                        withLink(
                            LinkAnnotation.Clickable(
                                "partial",
                                linkInteractionListener = { clicks++ },
                            )
                        ) {
                            append("123".repeat(20))
                        }
                    },
                modifier = Modifier.width(100.dp).testTag("text"),
                style = TextStyle(fontSize = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout = it },
            )
        }
        rule.onNodeWithTag("text").performTouchInput { click(layout.getBoundingBox(2).center) }
        rule.runOnIdle { assertThat(clicks).isEqualTo(1) }
        rule.onNodeWithTag("text").performTouchInput { click(layout.getBoundingBox(0).center) }
        rule.runOnIdle { assertThat(clicks).isEqualTo(1) }
    }
}
