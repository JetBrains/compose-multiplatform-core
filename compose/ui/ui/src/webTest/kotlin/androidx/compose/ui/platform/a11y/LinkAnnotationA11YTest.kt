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

import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

/**
 * Describes the a11y content of an element as a flat string, where the nested elements are
 * represented as `[role:text]`. Unlike `innerHTML`, it doesn't depend on the styles (position/size).
 */
private fun HTMLElement.describeA11YContent(): String = buildString {
    val nodes = childNodes
    repeat(nodes.length) { index ->
        val node = nodes.item(index) ?: return@repeat
        if (node is HTMLElement) {
            append("[${node.getAttribute("role")}:${node.describeA11YContent()}]")
        } else {
            append(node.textContent)
        }
    }
}

/**
 * Tests for the A11Y representation of [LinkAnnotation]s in a text:
 * every link range is exposed as a separate node (created by `TextLinkScope`),
 * while the surrounding plain text stays on the text node itself.
 */
class LinkAnnotationA11YTest : OnCanvasTests {

    @Test
    fun a11yLinkAnnotation() = runApplicationTest {
        createComposeWindow {
            Text(
                buildAnnotatedString {
                    append("Text before the ")
                    withLink(LinkAnnotation.Url("https://www.example.com")) {
                        append("link")
                    }
                    append(" and text after it")
                }
            )
        }

        awaitA11YChanges()

        val ownerRoot = getA11YContainer()!!.children[0] as HTMLElement
        val textNode = ownerRoot.children[0] as HTMLElement

        assertEquals(
            expected = "Text before the [link:link] and text after it",
            actual = textNode.describeA11YContent()
        )
    }

    @Test
    fun survivingLinkIsNotDetachedWhenTextChanges() = runApplicationTest {
        var prefix by mutableStateOf("Before ")
        var linkText by mutableStateOf("link")
        var suffix by mutableStateOf(" after")

        createComposeWindow {
            Text(
                buildAnnotatedString {
                    append(prefix)
                    withLink(LinkAnnotation.Url("https://www.example.com")) {
                        append(linkText)
                    }
                    append(suffix)
                }
            )
        }

        awaitA11YChanges()
        val a11yContainer = getA11YContainer()!!
        val textNode = a11yContainer.children[0]!!.children[0] as HTMLElement
        val linkBefore = textNode.children[0] as HTMLElement
        var linkWasRemoved = false
        val observer = createMutationObserver { removedNode ->
            if (removedNode === linkBefore) {
                linkWasRemoved = true
            }
        }
        observeChildListMutations(observer, a11yContainer)

        try {
            prefix = "Updated before "
            suffix = " updated after"
            awaitA11YChanges()
            awaitAnimationFrame()

            assertSame(linkBefore, textNode.children[0])
            assertEquals(
                "Updated before [link:link] updated after",
                textNode.describeA11YContent(),
            )

            linkText = "renamed link"
            awaitA11YChanges()
            awaitAnimationFrame()

            val linkAfter = textNode.children[0] as HTMLElement
            assertSame(linkBefore, linkAfter)
            assertTrue(linkAfter.isConnected)
            assertFalse(linkWasRemoved, "A surviving link must remain continuously connected")
            assertEquals(
                "Updated before [link:renamed link] updated after",
                textNode.describeA11YContent(),
            )
        } finally {
            disconnectMutationObserver(observer)
        }
    }

    @Test
    fun a11yLinkAnnotationClickable() = runApplicationTest {
        val clickedTags = mutableListOf<String>()

        createComposeWindow {
            Text(
                buildAnnotatedString {
                    append("Text before the ")
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "CLICKABLE_TAG",
                            linkInteractionListener = LinkInteractionListener { link ->
                                clickedTags.add((link as LinkAnnotation.Clickable).tag)
                            }
                        )
                    ) {
                        append("clickable")
                    }
                    append(" and text after it")
                }
            )
        }

        awaitA11YChanges()

        val textNode = getA11YContainer()!!.children[0]!!.children[0] as HTMLElement
        assertEquals(
            expected = "Text before the [link:clickable] and text after it",
            actual = textNode.describeA11YContent()
        )

        val linkNode = textNode.children[0] as HTMLElement
        assertEquals("link", linkNode.getAttribute("role"))

        linkNode.click()
        linkNode.click()
        assertEquals(listOf("CLICKABLE_TAG", "CLICKABLE_TAG"), clickedTags)
    }

    @Test
    fun a11yLinkAnnotationUrlWithCustomClickHandler() = runApplicationTest {
        val clickedUrls = mutableListOf<String>()

        createComposeWindow {
            Text(
                buildAnnotatedString {
                    append("Text before the ")
                    withLink(
                        LinkAnnotation.Url(
                            url = "https://www.example.com",
                            linkInteractionListener = LinkInteractionListener { link ->
                                clickedUrls.add((link as LinkAnnotation.Url).url)
                            }
                        )
                    ) {
                        append("link")
                    }
                    append(" and text after it")
                }
            )
        }

        awaitA11YChanges()

        val textNode = getA11YContainer()!!.children[0]!!.children[0] as HTMLElement
        assertEquals(
            expected = "Text before the [link:link] and text after it",
            actual = textNode.describeA11YContent()
        )

        val linkNode = textNode.children[0] as HTMLElement

        // The custom listener takes precedence over opening the url by the platform UriHandler:
        linkNode.click()
        assertEquals(listOf("https://www.example.com"), clickedUrls)
    }

    @Test
    fun a11yMultipleLinkAnnotations() = runApplicationTest {
        val clicks = mutableListOf<String>()

        createComposeWindow {
            Text(
                buildAnnotatedString {
                    append("Start ")
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "TAG1",
                            linkInteractionListener = LinkInteractionListener {
                                clicks.add("clickable1")
                            }
                        )
                    ) {
                        append("first")
                    }
                    append(" middle ")
                    withLink(
                        LinkAnnotation.Url(
                            url = "https://www.example.com",
                            linkInteractionListener = LinkInteractionListener {
                                clicks.add("url")
                            }
                        )
                    ) {
                        append("second")
                    }
                    append(" end")
                }
            )
        }

        awaitA11YChanges()

        val textNode = getA11YContainer()!!.children[0]!!.children[0] as HTMLElement
        assertEquals(
            expected = "Start [link:first] middle [link:second] end",
            actual = textNode.describeA11YContent()
        )

        (textNode.children[1] as HTMLElement).click()
        (textNode.children[0] as HTMLElement).click()
        assertEquals(listOf("url", "clickable1"), clicks)
    }
}
