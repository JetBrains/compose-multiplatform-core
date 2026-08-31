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

package androidx.compose.ui.platform.a11y

import androidx.compose.ui.platform.accessibility.splitTextAndLinks
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun url() = LinkAnnotation.Url("https://www.example.com")

private fun textWithLinks(builder: AnnotatedString.Builder.() -> Unit) =
    listOf(buildAnnotatedString(builder))

class SplitTextAroundLinksTest {

    @Test
    fun textWithoutLinks() {
        val split = splitTextAndLinks(listOf(AnnotatedString("A text without links")))

        assertEquals(expected = listOf("A text without links"), actual = split.textParts)
        assertEquals(expected = emptyList<String>(), actual = split.linkTexts)
        assertTrue(split.matchesLinksCount(0))
    }

    @Test
    fun linkInTheMiddle() {
        val texts = textWithLinks {
            append("Text before the ")
            withLink(url()) { append("link") }
            append(" and text after it")
        }

        assertEquals(
            expected = listOf("Text before the ", " and text after it"),
            actual = splitTextAndLinks(texts).textParts
        )
    }

    @Test
    fun linkAtTheStartAndAtTheEnd() {
        val texts = textWithLinks {
            withLink(url()) { append("first") }
            append(" middle ")
            withLink(url()) { append("second") }
        }

        assertEquals(
            expected = listOf("", " middle ", ""),
            actual = splitTextAndLinks(texts).textParts
        )
    }

    @Test
    fun adjacentLinks() {
        val texts = textWithLinks {
            append("start")
            withLink(url()) { append("first") }
            withLink(url()) { append("second") }
            append("end")
        }

        assertEquals(
            expected = listOf("start", "", "end"),
            actual = splitTextAndLinks(texts).textParts
        )
    }

    @Test
    fun theWholeTextIsALink() {
        val texts = textWithLinks {
            withLink(url()) { append("link") }
        }

        assertEquals(
            expected = listOf("", ""),
            actual = splitTextAndLinks(texts).textParts
        )
    }

    @Test
    fun severalTextsAreJoinedWithLineBreak() {
        val texts = listOf(
            buildAnnotatedString {
                append("first text with a ")
                withLink(url()) { append("link") }
            },
            AnnotatedString("second text"),
            buildAnnotatedString {
                withLink(url()) { append("another link") }
                append(" in the third text")
            },
        )

        assertEquals(
            expected = listOf("first text with a ", "\nsecond text\n", " in the third text"),
            actual = splitTextAndLinks(texts).textParts
        )
    }

    @Test
    fun emptyLinkRangesAreSkipped() {
        val texts = textWithLinks {
            append("start ")
            // An empty link range doesn't produce a link node, so it's not counted:
            withLink(url()) { }
            append("middle ")
            withLink(url()) { append("link") }
            append(" end")
        }

        assertEquals(
            expected = listOf("start middle ", " end"),
            actual = splitTextAndLinks(texts).textParts
        )
    }

    @Test
    fun linkTextsAreExtracted() {
        val texts = listOf(
            buildAnnotatedString {
                append("start ")
                withLink(url()) { append("first") }
            },
            buildAnnotatedString {
                withLink(url()) { append("second") }
                append(" end")
            },
        )

        assertEquals(
            expected = listOf("first", "second"),
            actual = splitTextAndLinks(texts).linkTexts
        )
    }

    @Test
    fun unexpectedLinksCountDoesNotMatch() {
        val texts = textWithLinks {
            append("Text before the ")
            withLink(url()) { append("link") }
            append(" and text after it")
        }

        val split = splitTextAndLinks(texts)

        assertTrue(split.matchesLinksCount(1))
        // The number of the link children doesn't match the number of the link ranges,
        // so the text node must expose the whole text instead of interleaving the parts:
        assertFalse(split.matchesLinksCount(2))
    }
}
