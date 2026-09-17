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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

/**
 * Verifies how the a11y mirror DOM handles multiple [androidx.compose.ui.semantics.SemanticsOwner]s
 */
class LayersA11YTest : OnCanvasTests {

    private fun HTMLElement.isInert() = hasAttribute("inert")

    private fun a11yContainer() = getA11YContainer() ?: error("a11y container is missing")

    /** Root element of the layer at [index] in z-order (0 == the scene's main content). */
    private fun ownerRoot(index: Int): HTMLElement =
        a11yContainer().children[index] as? HTMLElement
            ?: error("no a11y root element for layer $index")

    private fun ownerCount() = a11yContainer().childElementCount

    /**
     * A single [awaitA11YChanges] only waits for the next mutation, which is not necessarily the one
     * that settles the whole layer state - a layer's root element appears before its content has
     * been laid out. So poll instead of assuming one sync is enough.
     */
    private suspend fun awaitUntil(timeout: Duration = 5.seconds, condition: () -> Boolean) {
        if (condition()) return
        val deadline = currentTimeMillis() + timeout.inWholeMilliseconds
        while (true) {
            awaitA11YChanges(timeout)
            if (condition()) return
            assertTrue(
                currentTimeMillis() < deadline,
                "condition still not met after $timeout.\ninnerHTML = ${a11yContainer().innerHTML}"
            )
        }
    }

    /**
     * Dialogs animate by default ([androidx.compose.ui.ComposeUiFlags.isDialogAnimationEnabled]),
     * which keeps invalidating layout and makes the a11y tree settle at an unpredictable time.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun noAnimation() = DialogProperties(animateTransition = false)

    @Test
    fun dialogAddsAdditionalOwnerRootInHtml() = runApplicationTest {
        createComposeWindow {
            Text("Root")
            Dialog(onDismissRequest = {}, properties = noAnimation()) {
                Text("Dialog")
            }
        }

        awaitUntil { ownerCount() == 2 }

        assertTrue(
            ownerRoot(0).innerText.contains("Root"),
            "main content root must be present"
        )
        assertTrue(
            ownerRoot(1).innerText.contains("Dialog"),
            "dialog root must be present"
        )
    }

    @Test
    fun popupAddsAdditionalOwnerRootInHtml() = runApplicationTest {
        createComposeWindow {
            Text("Root")
            Popup {
                Text("Popup")
            }
        }

        awaitUntil { ownerCount() == 2 }

        assertTrue(
            ownerRoot(0).innerText.contains("Root"),
            "main content root must be present"
        )
        assertTrue(
            ownerRoot(1).innerText.contains("Popup"),
            "popup root must be present"
        )
    }

    @Test
    fun nonFocusablePopupKeepsContentBelowReadable() = runApplicationTest {
        createComposeWindow {
            Text("Root")
            Popup(properties = PopupProperties(focusable = false)) {
                Text("Popup")
            }
        }

        awaitUntil { ownerCount() == 2 }

        // A non-focusable popup does not take over: everything stays readable.
        assertFalse(ownerRoot(0).isInert(), "main content must stay readable under a popup")
        assertFalse(ownerRoot(1).isInert(), "popup content must be readable")

        // Popups are not modal, so they must not be announced as dialogs.
        assertFalse(
            ownerRoot(1).outerHTML.contains("aria-modal"),
            "a popup must not be marked aria-modal"
        )
    }

    @Test
    fun dialogMakesContentBelowInert() = runApplicationTest {
        createComposeWindow {
            Text("Root")
            Dialog(onDismissRequest = {}, properties = noAnimation()) {
                Text("Dialog")
            }
        }

        awaitUntil { ownerCount() == 2 && ownerRoot(0).isInert() }

        assertFalse(ownerRoot(1).isInert(), "dialog content must stay readable")

        // The `dialog()` marker sits on a node inside the layer, not on the layer root, so search
        // the whole subtree rather than asserting on a fixed depth.
        assertNotNull(
            ownerRoot(1).querySelector("[role=\"dialog\"][aria-modal=\"true\"]"),
            "dialog must expose role=dialog and aria-modal=true"
        )
    }

    @Test
    fun dismissingDialogRestoresContentBelow() = runApplicationTest {
        var showDialog by mutableStateOf(false)

        createComposeWindow {
            Text("Root")
            if (showDialog) {
                Dialog(onDismissRequest = {}, properties = noAnimation()) {
                    Text("Dialog")
                }
            }
        }

        awaitUntil { ownerCount() == 1 }
        assertFalse(ownerRoot(0).isInert())

        showDialog = true
        awaitUntil { ownerCount() == 2 && ownerRoot(0).isInert() }

        showDialog = false
        awaitUntil { ownerCount() == 1 && !ownerRoot(0).isInert() }
        assertTrue(
            ownerRoot(0).innerText.contains("Root"),
            "main content must be readable again once the dialog is gone"
        )
    }

    @Test
    fun mainTreeSurvivesPopupDismissal() = runApplicationTest {
        // Regression test: the listener used to keep a single SemanticsOwner, so opening a popup
        // dropped the main tree, and closing it left the popup's stale nodes behind forever.
        var showPopup by mutableStateOf(false)

        createComposeWindow {
            Text("Root")
            if (showPopup) {
                Popup(properties = PopupProperties()) {
                    Text("Popup")
                }
            }
        }

        awaitUntil { ownerCount() == 1 }
        assertTrue(ownerRoot(0).innerText.contains("Root"))

        showPopup = true
        awaitUntil { ownerCount() == 2 }
        assertTrue(
            ownerRoot(0).innerText.contains("Root"),
            "main tree must survive a popup being shown"
        )

        showPopup = false
        awaitUntil { ownerCount() == 1 }
        assertTrue(
            ownerRoot(0).innerText.contains("Root"),
            "main tree must survive a popup being dismissed"
        )
        assertFalse(
            a11yContainer().innerText.contains("Popup"),
            "a dismissed popup must not stay readable"
        )
    }

    @Test
    fun dialogOverPopupMakesBothLayersBelowInert() = runApplicationTest {
        createComposeWindow {
            Text("Root")
            Popup(properties = PopupProperties()) {
                Text("Popup")
            }
            Dialog(onDismissRequest = {}, properties = noAnimation()) {
                Text("Dialog")
            }
        }

        awaitUntil { ownerCount() == 3 && ownerRoot(0).isInert() }

        assertTrue(ownerRoot(1).isInert(), "a popup below a dialog must be inert too")
        assertFalse(ownerRoot(2).isInert(), "the dialog itself must stay readable")
    }

    @Test
    fun onlyLayersBelowTheTopmostDialogAreInert() = runApplicationTest {
        // Two stacked dialogs: the intermediate one is modal but must still be inert, because a
        // modal layer above it wins.
        createComposeWindow {
            Text("Root")
            Dialog(onDismissRequest = {}, properties = noAnimation()) {
                Text("Dialog1")
                Dialog(onDismissRequest = {}, properties = noAnimation()) {
                    Text("Dialog2")
                }
            }
        }

        awaitUntil { ownerCount() == 3 && ownerRoot(1).isInert() }

        assertTrue(ownerRoot(0).isInert(), "main content must be inert under two dialogs")
        assertFalse(ownerRoot(2).isInert(), "the topmost dialog must stay readable")
    }

    @Test
    fun focusablePopupDoesNotYetMakeContentBelowInert() = runApplicationTest {
        // Pins a known limitation: `ComposeSceneLayer.focusable` is not carried by the semantics
        // tree, so a focusable Popup is indistinguishable from a non-focusable one here and is
        // treated as non-modal. Update this test when modality is plumbed through properly.
        createComposeWindow {
            Text("Root")
            Popup(properties = PopupProperties(focusable = true)) {
                Text("Popup")
            }
        }

        awaitUntil { ownerCount() == 2 }

        assertFalse(ownerRoot(0).isInert(), "known limitation: focusable popups are not modal yet")
        assertEquals(2, ownerCount())
    }
}