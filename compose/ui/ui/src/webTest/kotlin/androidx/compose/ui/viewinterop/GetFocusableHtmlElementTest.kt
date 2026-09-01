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

package androidx.compose.ui.viewinterop

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

class GetFocusableHtmlElementTest {

    @BeforeTest
    fun setup() {
        document.body!!.innerHTML = ""
    }

    private fun createContainer(html: String): HTMLElement {
        val div = document.createElement("div") as HTMLElement
        div.innerHTML = html
        document.body!!.appendChild(div)
        return div
    }

    @Test
    fun noFocusableElements() {
        val container = createContainer("<div><span>text</span><div>more</div></div>")
        assertNull(getFocusableHtmlElement(container), "expected no first focusable")
        assertNull(getFocusableHtmlElement(container, last = true), "expected no last focusable")
    }

    @Test
    fun singleFocusableElement() {
        val container = createContainer("<div><button id='btn'>Click</button></div>")
        val first = getFocusableHtmlElement(container)
        val last = getFocusableHtmlElement(container, last = true)

        assertEquals(first, last, "first and last should be the same element")
        assertEquals("btn", first?.id)
    }

    @Test
    fun flatListOfFocusableElements() {
        val container = createContainer("""
            <div>
                <button id='a'>A</button>
                <button id='b'>B</button>
                <button id='c'>C</button>
            </div>
        """.trimIndent())
        assertEquals("a", getFocusableHtmlElement(container)?.id, "button 'a' is the first focusable")
        assertEquals("c", getFocusableHtmlElement(container, last = true)?.id, "button 'c' is the last focusable")
    }

    @Test
    fun lastFocusableDiffersFromLastLeaf() {
        val container = createContainer("""
            <div>
                <button id='first'>First</button>
                <button id='middle'>Middle</button>
                <div><div><span>non-focusable leaf</span></div></div>
            </div>
        """.trimIndent())
        assertEquals("first", getFocusableHtmlElement(container)?.id)
        // the last leaf is the <span>, but the last focusable is <button id='middle'>
        assertEquals("middle", getFocusableHtmlElement(container, last = true)?.id)
    }

    @Test
    fun selectorRespectsFocusabilityRules() {
        val container = createContainer("""
            <div>
                <button id='disabled' disabled>Disabled</button>
                <input type="hidden" id="hidden" />
                <span tabindex="-1" id="negative">Negative tabindex</span>
                <div inert><button id='inert'>Inert</button></div>
                <a href="#" id="link">Link</a>
                <button id="btn">Button</button>
                <input type="hidden" id="inputHiddenLast" />
            </div>
        """.trimIndent())
        // The first focusable is the link
        assertEquals("link", getFocusableHtmlElement(container)?.id, "first focusable")
        // The last focusable is the button
        assertEquals("btn", getFocusableHtmlElement(container, last = true)?.id, "last focusable")
    }

    @Test
    fun rejectsInertContainers() {
        val container = createContainer("""
            <div>
                <div inert><button id='a'>A</button></div>
                <button id='b'>B</button>
                <button id='c'>C</button>
                <div inert><button id='d'>D</button></div>
            </div>
        """.trimIndent())
        // button 'a' is inside an inert container and should be rejected
        assertEquals("b", getFocusableHtmlElement(container)?.id, "button 'b' is the first focusable")
        // button 'd' is inside an inert container and should be rejected
        assertEquals("c", getFocusableHtmlElement(container, last = true)?.id, "button 'c' is the last focusable")
    }
}
