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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for https://youtrack.jetbrains.com/issue/CMP-9009: text after an inline
 * content item whose alternate text is longer than one character was hit-tested with the wrong
 * offsets on Skiko, so a link placed after the item could not be clicked.
 */
@OptIn(ExperimentalTestApi::class)
class BasicTextInlineContentOffsetTest {
    private val linkText = "Button"
    private val text = buildAnnotatedString {
        append("ab")
        appendInlineContent("icon", "[icon]")
        withLink(LinkAnnotation.Clickable("tag") { linkClicks++ }) { append(linkText) }
    }
    private val linkStart = text.indexOf(linkText)
    private var linkClicks = 0

    private val inlineContent =
        mapOf(
            "icon" to
                InlineTextContent(
                    placeholder = Placeholder(30.sp, 10.sp, PlaceholderVerticalAlign.TextCenter)
                ) {
                    Box(Modifier.fillMaxSize())
                }
        )

    @Test
    fun clickOnLinkAfterMultiCharInlineContent_invokesLinkHandler() = runSkikoComposeUiTest {
        var layoutResult: TextLayoutResult? = null
        setContent {
            BasicText(
                text = text,
                modifier = Modifier.testTag("text"),
                style = TextStyle(fontSize = 10.sp),
                inlineContent = inlineContent,
                onTextLayout = { layoutResult = it },
            )
        }

        val layout = assertNotNull(layoutResult)
        // The link's first glyph is laid out right after the inline content. The position is
        // derived from the placeholder rect (which was never affected by the bug) rather than from
        // getBoundingBox(linkStart), so that a broken offset mapping cannot make the test
        // self-consistent.
        val placeholderRect = assertNotNull(layout.placeholderRects.single())
        val positionInLink = Offset(placeholderRect.right + 3f, placeholderRect.center.y)
        assertEquals(linkStart, layout.getOffsetForPosition(positionInLink))

        onNodeWithTag("text").performTouchInput { click(positionInLink) }

        waitForIdle()
        assertEquals(1, linkClicks)
    }

    @Test
    fun getOffsetForPosition_afterMultiCharInlineContent_returnsComposeOffset() =
        runSkikoComposeUiTest {
            var layoutResult: TextLayoutResult? = null
            setContent {
                BasicText(
                    text = text,
                    style = TextStyle(fontSize = 10.sp),
                    inlineContent = inlineContent,
                    onTextLayout = { layoutResult = it },
                )
            }

            val layout = assertNotNull(layoutResult)
            val placeholderRect = assertNotNull(layout.placeholderRects.single())
            val linkBox = layout.getBoundingBox(linkStart)

            // The link starts right after the inline content.
            assertTrue(
                abs(placeholderRect.right - linkBox.left) < 0.01f,
                "link left ${linkBox.left} != placeholder right ${placeholderRect.right}",
            )
            // A hit inside the link resolves to an offset inside the link, not inside "[icon]".
            assertEquals(linkStart, layout.getOffsetForPosition(linkBox.center))
            assertEquals(
                linkStart + 2,
                layout.getOffsetForPosition(layout.getBoundingBox(linkStart + 2).center),
            )
            // The cursor after the whole text is at the end of the link.
            assertEquals(
                layout.getBoundingBox(text.length - 1).right,
                layout.getCursorRect(text.length).left,
            )
            // A click inside the inline content resolves to its start or its end.
            val y = placeholderRect.center.y
            val insideStart = layout.getOffsetForPosition(Offset(placeholderRect.left + 1f, y))
            val insideEnd = layout.getOffsetForPosition(Offset(placeholderRect.right - 1f, y))
            assertEquals(2, insideStart)
            assertEquals(linkStart, insideEnd)
        }
}
