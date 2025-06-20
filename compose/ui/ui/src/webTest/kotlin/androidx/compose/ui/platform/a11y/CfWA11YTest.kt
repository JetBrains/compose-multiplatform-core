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

package androidx.compose.ui.platform.a11y

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

class CfWA11YTest : OnCanvasTests {

    @Test
    fun a11yButtonClick() = runApplicationTest {
        var clickCounter = 0

        createComposeWindow {
            Button(onClick = {
                clickCounter++
            }) {
                Text("Button1")
            }
        }

        awaitIdle()

        val a11yContainer = getA11YContainer()
        assertNotNull(a11yContainer)

        awaitA11YChanges()

        val button1 = a11yContainer.children[0]?.children[0] as? HTMLElement
        assertNotNull(button1)

        assertEquals("button", button1.getAttribute("role"))
        assertEquals("Button1", button1.innerText)

        repeat(3) {
            button1.click()
            assertEquals(it + 1, clickCounter)
        }
    }

    @Test
    fun changesAreApplied() = runApplicationTest {
        var clickCounter1 = 0
        var clickCounter2 = 0

        var showButton2 by mutableStateOf(false)

        createComposeWindow {
            Button(onClick = {
                clickCounter1++
            }) {
                Text("Button1")
            }

            if (showButton2) {
                Button(onClick = {
                    clickCounter2++
                }) {
                    Text("Button2")
                }
            }
        }

        awaitIdle()

        val a11yContainer = getA11YContainer()
        assertNotNull(a11yContainer)

        awaitA11YChanges()

        val buttonsContainer = a11yContainer.children[0] as HTMLDivElement
        assertEquals(1, buttonsContainer.children.length)

        val button1 = buttonsContainer.children[0] as HTMLElement
        assertEquals("button", button1.getAttribute("role"))
        assertEquals("Button1", button1.innerText)

        showButton2 = true
        awaitIdle()
        awaitA11YChanges()

        assertEquals(2, buttonsContainer.children.length)
        assertTrue(button1.isConnected)

        val button2 = buttonsContainer.children[1] as HTMLElement
        assertEquals("button", button2.getAttribute("role"))
        assertEquals("Button2", button2.innerText)
        assertTrue(button2.isConnected)


        repeat(3) {
            button1.click()
            button2.click()
            assertEquals(it + 1, clickCounter1)
            assertEquals(it + 1, clickCounter2)
        }

        showButton2 = false
        awaitIdle()
        awaitA11YChanges()

        assertEquals(1, buttonsContainer.children.length)
        assertFalse(button2.isConnected)
    }

    @Test
    fun orderOfElements() = runApplicationTest {
        var show1 by mutableStateOf(true)
        var show2 by mutableStateOf(false)
        var show3 by mutableStateOf(false)

        createComposeWindow {
            if (show1) {
                Button(onClick = {}) {
                    Text("Button1")
                }
            }
            if (show2) {
                Button(onClick = {}) {
                    Text("Button2")
                }
            }
            if (show3) {
                Button(onClick = {}) {
                    Text("Button3")
                }
            }
        }

        awaitIdle()

        val a11yContainer = getA11YContainer()
        assertNotNull(a11yContainer)

        awaitA11YChanges()

        val buttonsContainer = a11yContainer.children[0] as HTMLDivElement
        assertEquals(1, buttonsContainer.children.length)

        show2 = true
        show3 = true

        awaitIdle()
        awaitA11YChanges()

        assertEquals(3, buttonsContainer.children.length)

        assertEquals("Button1", buttonsContainer.children[0]!!.innerHTML)
        assertEquals("Button2", buttonsContainer.children[1]!!.innerHTML)
        assertEquals("Button3", buttonsContainer.children[2]!!.innerHTML)

        show1 = false
        awaitIdle()
        awaitA11YChanges()

        assertEquals(2, buttonsContainer.children.length)
        assertEquals("Button2", buttonsContainer.children[0]!!.innerHTML)
        assertEquals("Button3", buttonsContainer.children[1]!!.innerHTML)

        show1 = true
        awaitIdle()
        awaitA11YChanges()

        assertEquals(3, buttonsContainer.children.length)
        assertEquals("Button1", buttonsContainer.children[0]!!.innerHTML)
        assertEquals("Button2", buttonsContainer.children[1]!!.innerHTML)
        assertEquals("Button3", buttonsContainer.children[2]!!.innerHTML)
    }

    @Test
    fun changesMustBeBatched() = runApplicationTest {
        var show1 by mutableStateOf(true)

        createComposeWindow {
            if (show1) {
                Button(onClick = {}) {
                    Text("Text in Button")
                }
            }
        }

        awaitIdle()
        val a11yContainer = getA11YContainer()!!

        assertEquals("",a11yContainer.innerHTML)
        assertEquals(0,a11yContainer.childElementCount)

        suspend fun realDelay(timeMs: Long) {
            withContext(Dispatchers.Default) {
                delay(timeMs)
            }
        }

        repeat(20) {
            show1 = !show1
            realDelay(10)

            // No changes expected yet due to debounce
            assertEquals("",a11yContainer.innerHTML)
            assertEquals(0,a11yContainer.childElementCount)
        }

        val startTime = currentTimeMillis()
        awaitA11YChanges()
        val waitedForChangesMs = currentTimeMillis() - startTime

        val buttonsContainer = a11yContainer.children[0] as HTMLDivElement
        assertEquals(1, buttonsContainer.children.length)

        (buttonsContainer.children[0] as HTMLElement).let { button ->
            assertEquals("button", button.getAttribute("role"))
            assertEquals("Text in Button", button.innerHTML)
        }

        assertTrue(waitedForChangesMs in 90..110, "Changes must be batched, waited for $waitedForChangesMs ms. Allowed tolerance 10ms was exceeded")
    }
}