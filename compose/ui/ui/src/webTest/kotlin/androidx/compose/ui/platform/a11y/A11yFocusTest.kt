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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

class A11yFocusTest : OnCanvasTests {

    private fun a11yElement(tag: String): HTMLElement =
        assertNotNull(getShadowRoot().getElementById(tag) as? HTMLElement)

    @Test
    fun activatedNodeRemovalFocusesFirstElementAfterTransition() = runApplicationTest {
        var showSource by mutableStateOf(true)
        var showDestination by mutableStateOf(false)

        createComposeWindow {
            Box {
                if (showSource) {
                    Column {
                        Text("Home")
                        Text(
                            "Components",
                            Modifier.testTag("navigate").clickable { showDestination = true },
                        )
                    }
                }
                if (showDestination) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Components") },
                                navigationIcon = {
                                    IconButton(onClick = {}, modifier = Modifier.testTag("back")) {
                                        Text("Back")
                                    }
                                },
                            )
                        },
                    ) { padding ->
                        Column(Modifier.padding(padding)) {
                            Text("Popups")
                            Text("Dialogs")
                        }
                    }
                }
            }
        }

        awaitA11YChanges()
        val navigate = a11yElement("navigate")
        val initialFocus = getShadowRoot().activeElement

        // VoiceOver can activate a node using its virtual cursor without giving it DOM focus.
        navigate.click()
        awaitA11YChanges()

        // Animated destinations coexist until the outgoing transition finishes.
        assertTrue(navigate.isConnected)
        assertSame(initialFocus, getShadowRoot().activeElement)

        showSource = false
        awaitA11YChanges()

        assertFalse(navigate.isConnected)
        assertSame(a11yElement("back"), getShadowRoot().activeElement)
    }

    @Test
    fun focusedNodeRemovalFocusesFirstReadableElement() = runApplicationTest {
        var showButton by mutableStateOf(true)

        createComposeWindow {
            Column(Modifier.testTag("container")) {
                Text(
                    "Hidden",
                    Modifier.testTag("hidden").semantics { hideFromAccessibility() },
                )
                Text("Heading", Modifier.testTag("heading"))
                if (showButton) {
                    Button(onClick = {}, modifier = Modifier.testTag("button")) {
                        Text("Button")
                    }
                }
            }
        }

        awaitA11YChanges()
        val button = a11yElement("button")
        button.focus()
        assertSame(button, getShadowRoot().activeElement)

        showButton = false
        awaitA11YChanges()

        assertFalse(button.isConnected)
        assertSame(a11yElement("heading"), getShadowRoot().activeElement)
    }

    @Test
    fun survivingFocusedNodeKeepsFocusDuringUpdate() = runApplicationTest {
        var count by mutableStateOf(0)

        createComposeWindow {
            Column {
                Text("Updates: $count")
                Slider(value = 0.5f, onValueChange = {}, modifier = Modifier.testTag("slider"))
            }
        }

        awaitA11YChanges()
        val slider = a11yElement("slider")
        slider.focus()
        assertSame(slider, getShadowRoot().activeElement)

        count++
        awaitA11YChanges()

        assertSame(slider, a11yElement("slider"))
        assertSame(slider, getShadowRoot().activeElement)
    }

    @Test
    fun leavingAccessibilityTreeDoesNotStealFocus() = runApplicationTest {
        var showButton by mutableStateOf(true)

        createComposeWindow {
            Column {
                Text("Heading")
                if (showButton) {
                    Button(onClick = {}, modifier = Modifier.testTag("button")) {
                        Text("Button")
                    }
                }
            }
        }

        awaitA11YChanges()
        val button = a11yElement("button")
        button.focus()
        assertSame(button, getShadowRoot().activeElement)

        getCanvas().focus()
        assertSame(getCanvas(), getShadowRoot().activeElement)
        showButton = false
        awaitA11YChanges()

        assertFalse(button.isConnected)
        assertSame(getCanvas(), getShadowRoot().activeElement)
    }

    @Test
    fun virtualCursorActivationDoesNotStealFocusFromOutsideTheViewport() = runApplicationTest {
        var showButton by mutableStateOf(true)

        createComposeWindow {
            Column {
                Text("Heading")
                if (showButton) {
                    Button(onClick = {}, modifier = Modifier.testTag("button")) {
                        Text("Button")
                    }
                }
            }
        }

        awaitA11YChanges()
        val button = a11yElement("button")
        button.click()

        val outsideButton = document.createElement("button") as HTMLElement
        document.body!!.appendChild(outsideButton)
        try {
            outsideButton.focus()
            assertSame(outsideButton, document.activeElement)

            showButton = false
            awaitA11YChanges()

            assertFalse(button.isConnected)
            assertSame(outsideButton, document.activeElement)
        } finally {
            outsideButton.remove()
        }
    }

    @Test
    fun nodeRemovalWithoutAccessibilityInteractionDoesNotMoveFocus() = runApplicationTest {
        var showButton by mutableStateOf(true)

        createComposeWindow {
            Column {
                Text("Heading")
                if (showButton) {
                    Button(onClick = {}, modifier = Modifier.testTag("button")) {
                        Text("Button")
                    }
                }
            }
        }

        awaitA11YChanges()
        val button = a11yElement("button")
        val initialFocus = getShadowRoot().activeElement

        showButton = false
        awaitA11YChanges()

        assertFalse(button.isConnected)
        assertSame(initialFocus, getShadowRoot().activeElement)
    }
}
