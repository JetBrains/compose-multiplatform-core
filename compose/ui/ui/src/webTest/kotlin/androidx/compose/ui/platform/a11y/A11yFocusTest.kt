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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

class A11yFocusTest : OnCanvasTests {

    @Test
    fun focusMovesToNewPageWhenFocusedA11yNodeIsRemoved() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            NavigationPages(page, onNavigate = { page = it })
        }
        awaitA11YChanges()

        val nextButton = element("next")
        focusForTest(nextButton)

        for ((previousTag, targetTag) in listOf("next" to "back", "back" to "next")) {
            val previousButton = element(previousTag)
            assertSame(previousButton, getShadowRoot().activeElement)
            previousButton.click()

            awaitCondition("Navigation replaces $previousTag with $targetTag") {
                getShadowRoot().getElementById(previousTag) == null &&
                    getShadowRoot().getElementById(targetTag) != null
            }
            assertFalse(previousButton.isConnected, "The $previousTag button must be removed")
            val targetButton = element(targetTag)
            awaitCondition("DOM focus moves to $targetTag on the new page") {
                getShadowRoot().activeElement === targetButton
            }
        }
    }

    @Test
    fun navigationDoesNotTakeFocusFromOutsideCompose() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            NavigationPages(page, onNavigate = { page = it })
        }
        awaitA11YChanges()

        val externalButton = document.createElement("button") as HTMLElement
        document.body!!.appendChild(externalButton)
        try {
            externalButton.focus()
            assertSame(externalButton, document.activeElement)

            page = 2
            awaitCondition("Page 2 is exposed") {
                getShadowRoot().getElementById("next") == null &&
                    getShadowRoot().getElementById("back") != null
            }
            assertSame(externalButton, document.activeElement, "External focus must be preserved")
        } finally {
            externalButton.remove()
        }
    }

    @Test
    fun navigationPreservesExplicitFocusMoveInsideCompose() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            Column {
                Button(onClick = {}, modifier = Modifier.testTag("destination")) {
                    Text("Keep focus here")
                }
                NavigationPages(page, onNavigate = {
                    page = it
                    element("destination").focus()
                })
            }
        }
        awaitA11YChanges()
        val destination = element("destination")
        focusForTest(destination)
        val nextButton = element("next")
        focusForTest(nextButton)

        nextButton.click()
        assertSame(destination, getShadowRoot().activeElement)
        awaitCondition("Page 2 is exposed") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("back") != null
        }
        assertSame(destination, getShadowRoot().activeElement, "The explicit focus move must win")
    }

    @Test
    fun focusRecoveryUsesNearestSurvivingContainer() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            Column {
                Button(onClick = {}, modifier = Modifier.testTag("unrelated")) {
                    Text("Outside the navigation container")
                }
                Column(Modifier.testTag("navigation").semantics { isTraversalGroup = true }) {
                    NavigationPages(page, onNavigate = { page = it })
                }
            }
        }
        awaitA11YChanges()
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("Page 2 is exposed") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("back") != null
        }
        val backButton = element("back")
        assertTrue(element("navigation").contains(backButton))
        awaitCondition("Recovery prefers Back inside the surviving navigation container") {
            getShadowRoot().activeElement === backButton
        }
    }

    @Test
    fun semanticsUpdatePreservesFocusOnSurvivingNode() = runApplicationTest {
        var label by mutableStateOf("Before update")
        createComposeWindow {
            Button(onClick = {}, modifier = Modifier.testTag("survivor")) {
                Text(label)
            }
        }
        awaitA11YChanges()
        val button = element("survivor")
        focusForTest(button)

        label = "After update"
        awaitCondition("The focused button's text is updated") {
            element("survivor").textContent == "After update"
        }
        assertSame(button, element("survivor"), "The DOM node must be reused")
        assertSame(button, getShadowRoot().activeElement, "The surviving node must retain focus")
    }

    @Test
    fun focusRecoverySkipsFullyClippedInertNode() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            if (page == 1) {
                Button(onClick = { page = 2 }, modifier = Modifier.testTag("next")) {
                    Text("Next")
                }
            } else {
                Column {
                    Box(Modifier.size(100.dp).clipToBounds()) {
                        Button(
                            onClick = {},
                            modifier = Modifier.offset(200.dp, 0.dp).size(80.dp, 40.dp).testTag("clipped"),
                        ) {
                            Text("Clipped")
                        }
                    }
                    Button(onClick = { page = 1 }, modifier = Modifier.testTag("back")) {
                        Text("Back")
                    }
                }
            }
        }
        awaitA11YChanges()
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("Page 2 exposes a clipped node before Back") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("clipped") != null &&
                getShadowRoot().getElementById("back") != null
        }
        assertFalse(nextButton.isConnected)
        assertTrue(element("clipped").hasAttribute("inert"), "The clipped candidate must be inert")
        val backButton = element("back")
        awaitCondition("Focus recovery skips the inert node and focuses Back") {
            getShadowRoot().activeElement === backButton
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun navigationInsideDialogKeepsFocusInDialogOwner() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            Button(onClick = {}, modifier = Modifier.testTag("background")) {
                Text("Background")
            }
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(animateTransition = false),
            ) {
                NavigationPages(page, onNavigate = { page = it })
            }
        }
        awaitCondition("The dialog's Next button is exposed above the inert main owner") {
            getA11YContainer()!!.childElementCount == 2 &&
                ownerRoot(0).hasAttribute("inert") &&
                getShadowRoot().getElementById("next") != null
        }
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("Navigation replaces the dialog's Next button with Back") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("back") != null
        }
        assertFalse(nextButton.isConnected)
        val backButton = element("back")
        assertTrue(ownerRoot(1).contains(backButton))
        assertTrue(ownerRoot(0).hasAttribute("inert"))
        awaitCondition("Focus recovery stays inside the dialog owner") {
            getShadowRoot().activeElement === backButton
        }
    }

    @Test
    fun focusRecoverySkipsDisabledAndHiddenNodes() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            if (page == 1) {
                Button(onClick = { page = 2 }, modifier = Modifier.testTag("next")) {
                    Text("Next")
                }
            } else {
                Column {
                    Button(onClick = {}, enabled = false, modifier = Modifier.testTag("disabled")) {
                        Text("Disabled")
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier.semantics { hideFromAccessibility() }.testTag("hidden"),
                    ) {
                        Text("Hidden")
                    }
                    Button(onClick = { page = 1 }, modifier = Modifier.testTag("back")) {
                        Text("Back")
                    }
                }
            }
        }
        awaitA11YChanges()
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("Page 2 is exposed") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("back") != null
        }
        val backButton = element("back")
        awaitCondition("Focus recovery skips disabled and hidden nodes") {
            getShadowRoot().activeElement === backButton
        }
    }

    @Test
    fun focusRecoveryLeavesFocusAloneWhenNoSuitableTargetExists() = runApplicationTest {
        var showButton by mutableStateOf(true)
        createComposeWindow {
            if (showButton) {
                Button(onClick = { showButton = false }, modifier = Modifier.testTag("next")) {
                    Text("Next")
                }
            } else {
                Box(Modifier.size(40.dp).testTag("empty"))
            }
        }
        awaitA11YChanges()
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("The focused button is replaced with an accessibility-empty node") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("empty") != null
        }
        assertNull(getShadowRoot().activeElement, "Neither the empty node nor the canvas should gain focus")
    }

    @Test
    fun focusRecoveryPrefersContentLeafOverLabelledPageContainer() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            if (page == 1) {
                Button(onClick = { page = 2 }, modifier = Modifier.testTag("next")) {
                    Text("Next")
                }
            } else {
                Column(Modifier.semantics { contentDescription = "New page" }.testTag("page")) {
                    Text("First label", Modifier.testTag("firstLabel"))
                    Text("Second label", Modifier.testTag("secondLabel"))
                }
            }
        }
        awaitA11YChanges()
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("The labelled page and its content are exposed") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("secondLabel") != null
        }
        val firstLabel = element("firstLabel")
        assertTrue(element("page").contains(firstLabel), "The labelled container must remain in the DOM")
        awaitCondition("Recovery focuses the first content leaf rather than the whole page") {
            getShadowRoot().activeElement === firstLabel
        }
    }

    @Test
    fun focusRecoveryKeepsButtonWithTextChildrenAsWholeTarget() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            if (page == 1) {
                Button(onClick = { page = 2 }, modifier = Modifier.testTag("next")) {
                    Text("Next")
                }
            } else {
                Button(
                    onClick = { page = 1 },
                    modifier = Modifier.semantics { contentDescription = "Back" }.testTag("back"),
                ) {
                    // A nested merging boundary keeps the label as a separate DOM element.
                    Column(Modifier.semantics(mergeDescendants = true) {}.testTag("buttonLabel")) {
                        Text("Back")
                    }
                }
            }
        }
        awaitA11YChanges()
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("Page 2 is exposed") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("back") != null
        }
        val backButton = element("back")
        assertTrue(backButton.childElementCount > 0, "The button must have child elements")
        awaitCondition("Recovery focuses the button rather than its text child") {
            getShadowRoot().activeElement === backButton
        }
    }

    @Test
    fun focusRecoveryKeepsMergedSemanticsAsWholeTarget() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            if (page == 1) {
                Button(onClick = { page = 2 }, modifier = Modifier.testTag("next")) {
                    Text("Next")
                }
            } else {
                Column(
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Merged summary"
                    }.testTag("summary")
                ) {
                    Column(Modifier.semantics(mergeDescendants = true) {}.testTag("firstLabel")) {
                        Text("First label")
                    }
                    Column(Modifier.semantics(mergeDescendants = true) {}.testTag("secondLabel")) {
                        Text("Second label")
                    }
                }
            }
        }
        awaitA11YChanges()
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("The merged summary and its children are exposed") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("secondLabel") != null
        }
        val summary = element("summary")
        assertTrue(summary.contains(element("firstLabel")))
        awaitCondition("Recovery focuses the merged summary as one accessibility target") {
            getShadowRoot().activeElement === summary
        }
    }

    @Test
    fun focusRecoveryFallsBackToFartherAncestorWhenNearestContainerHasNoTarget() = runApplicationTest {
        var showButton by mutableStateOf(true)
        createComposeWindow {
            Column {
                Column(Modifier.testTag("navigation").semantics { isTraversalGroup = true }) {
                    if (showButton) {
                        Button(onClick = { showButton = false }, modifier = Modifier.testTag("next")) {
                            Text("Next")
                        }
                    } else {
                        Box(Modifier.size(40.dp).testTag("empty"))
                    }
                }
                Button(onClick = {}, modifier = Modifier.testTag("fallback")) {
                    Text("Outside the navigation container")
                }
            }
        }
        awaitA11YChanges()
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("The focused button is replaced with an accessibility-empty node") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("empty") != null
        }
        assertTrue(element("navigation").contains(element("empty")))
        val fallbackButton = element("fallback")
        assertFalse(element("navigation").contains(fallbackButton))
        awaitCondition("Recovery leaves the empty navigation container and focuses the next target") {
            getShadowRoot().activeElement === fallbackButton
        }
    }

    @Test
    fun focusRecoverySkipsSurvivingContainerInsideHiddenContainer() = runApplicationTest {
        var page by mutableStateOf(1)
        createComposeWindow {
            Column {
                Column(Modifier.testTag("hidden").semantics { hideFromAccessibility() }) {
                    // The nearest surviving container isn't hidden itself, but its ancestor is.
                    Column(Modifier.testTag("navigation").semantics { isTraversalGroup = true }) {
                        NavigationPages(page, onNavigate = { page = it })
                    }
                }
                Button(onClick = {}, modifier = Modifier.testTag("fallback")) {
                    Text("Outside the navigation container")
                }
            }
        }
        awaitA11YChanges()
        val nextButton = element("next")
        focusForTest(nextButton)
        nextButton.click()

        awaitCondition("Page 2 is exposed") {
            getShadowRoot().getElementById("next") == null &&
                getShadowRoot().getElementById("back") != null
        }
        assertTrue(element("navigation").contains(element("back")))
        assertTrue(element("hidden").contains(element("navigation")))
        val fallbackButton = element("fallback")
        awaitCondition("Recovery skips Back inside the hidden container and focuses the next target") {
            getShadowRoot().activeElement === fallbackButton
        }
    }

    private fun ownerRoot(index: Int): HTMLElement =
        assertNotNull(getA11YContainer()!!.children.item(index) as? HTMLElement)

    @Composable
    private fun NavigationPages(page: Int, onNavigate: (Int) -> Unit) {
        if (page == 1) {
            Button(onClick = { onNavigate(2) }, modifier = Modifier.testTag("next")) {
                Text("Next")
            }
        } else {
            Button(onClick = { onNavigate(1) }, modifier = Modifier.testTag("back")) {
                Text("Back")
            }
        }
    }

    private fun element(tag: String): HTMLElement =
        assertNotNull(getShadowRoot().getElementById(tag) as? HTMLElement, "No A11Y element for '$tag'")

    private fun focusForTest(element: HTMLElement) {
        // Buttons are mirrored as divs without tabindex; allow DOM focus for the test setup.
        element.setAttribute("tabindex", "-1")
        element.focus()
        assertSame(element, getShadowRoot().activeElement, "The initial node must have DOM focus")
    }
}
