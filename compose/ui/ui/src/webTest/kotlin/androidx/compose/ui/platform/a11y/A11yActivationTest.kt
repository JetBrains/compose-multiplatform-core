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

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.WebAccessibilityActivation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement

@OptIn(ExperimentalComposeUiApi::class)
class A11yActivationTest : OnCanvasTests {

    @Test
    fun defaultA11YTreeIsBuiltOnActivationClick() = runApplicationTest {
        var contentIsPlaced = false
        val pageTitle = mutableStateOf("Page title")
        createComposeWindow {
            Column(Modifier.onGloballyPositioned { contentIsPlaced = true }) {
                Text(pageTitle.value, modifier = Modifier.testTag("pageTitle"))
                Button(onClick = {}, modifier = Modifier.testTag("continueButton")) {
                    Text("Continue")
                }
            }
        }
        awaitCondition("Compose content to be placed") { contentIsPlaced }

        val shadowRoot = getShadowRoot()
        assertNull(shadowRoot.querySelector("#cmp_a11y_root > *"), "No A11Y tree before activation")
        val activationButton = assertNotNull(
            shadowRoot.getElementById("cmp_a11y_activation_button") as? HTMLButtonElement,
            "The default viewport must offer an accessibility activation button",
        )
        assertEquals("Enable accessibility", activationButton.textContent)

        activationButton.focus()
        activationButton.click()

        val a11yRoot = assertNotNull(shadowRoot.getElementById("cmp_a11y_root"))
        assertEquals("auto", window.getComputedStyle(a11yRoot).getPropertyValue("pointer-events"))
        assertEquals("Page title", a11yRoot.querySelector("#pageTitle")?.textContent)
        val continueButton = assertNotNull(a11yRoot.querySelector("#continueButton"))
        assertEquals("button", continueButton.getAttribute("role"))
        assertEquals("Continue", continueButton.textContent)
        assertNull(shadowRoot.getElementById("cmp_a11y_activation_button"))
        assertTrue(a11yRoot.contains(shadowRoot.activeElement), "Focus should move into the tree")

        pageTitle.value = "Next page"
        awaitCondition("the active accessibility tree to reflect new content") {
            a11yRoot.querySelector("#pageTitle")?.textContent == "Next page"
        }
    }

    @Test
    fun activationButtonUsesConfiguredLabel() = runApplicationTest {
        val label = "Toegankelijkheid inschakelen"
        createComposeWindow(
            configure = { accessibilityActivation = WebAccessibilityActivation.OnUserRequest(label) }
        ) {
            Text("Page title")
        }

        val activationButton = assertNotNull(
            getShadowRoot().getElementById("cmp_a11y_activation_button"),
        )
        assertEquals(label, activationButton.textContent)
        assertEquals(label, activationButton.textContent)
    }

    @Test
    fun eagerA11YTreeIsBuiltWithoutActivationClick() = runApplicationTest {
        createComposeWindow(configure = { accessibilityActivation = WebAccessibilityActivation.Eager }) {
            Button(onClick = {}, modifier = Modifier.testTag("continueButton")) {
                Text("Continue")
            }
        }

        val shadowRoot = getShadowRoot()
        assertNull(shadowRoot.getElementById("cmp_a11y_activation_button"))

        val buttonSelector = "#cmp_a11y_root #continueButton"
        awaitCondition("eager accessibility tree to contain the Compose button") {
            shadowRoot.querySelector(buttonSelector) != null
        }

        val continueButton = assertNotNull(shadowRoot.querySelector(buttonSelector))
        val a11yRoot = assertNotNull(shadowRoot.getElementById("cmp_a11y_root"))
        assertEquals("none", window.getComputedStyle(a11yRoot).getPropertyValue("pointer-events"))
        assertEquals("button", continueButton.getAttribute("role"))
        assertEquals("Continue", continueButton.textContent)
        assertNull(shadowRoot.getElementById("cmp_a11y_activation_button"))
    }

    @Test
    fun disabledA11YOmitsTreeAndActivationButton() = runApplicationTest {
        var contentIsPlaced = false
        createComposeWindow(
            configure = { accessibilityActivation = WebAccessibilityActivation.Disabled }
        ) {
            Text("Page title", modifier = Modifier.onGloballyPositioned { contentIsPlaced = true })
        }
        awaitCondition("Compose content to be placed") { contentIsPlaced }

        val shadowRoot = getShadowRoot()
        assertNull(shadowRoot.getElementById("cmp_a11y_root"))
        assertNull(shadowRoot.getElementById("cmp_a11y_activation_button"))
    }
}
