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

package androidx.compose.ui.window

import androidx.collection.MutableScatterMap
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.Text
import org.w3c.dom.get

/** The stable DOM id of the accessibility activation button, targeted by its CSS rules. */
internal const val A11Y_ACTIVATION_BUTTON_ID = "cmp_a11y_activation_button"

/**
 * CSS rules for the accessibility activation button. The button is visually hidden until focused
 * so it doesn't clutter the page, but remains discoverable via keyboard navigation and screen readers.
 */
internal val a11yActivationButtonCss = """
    #$A11Y_ACTIVATION_BUTTON_ID {
        position: absolute;
        inset-block-start: 0.5rem;
        inset-inline-start: 0.5rem;
        z-index: 1;
        max-inline-size: calc(100% - 1rem);
    }

    #$A11Y_ACTIVATION_BUTTON_ID:not(:focus) {
        inline-size: 1px;
        block-size: 1px;
        overflow: hidden;
        clip-path: inset(50%);
        pointer-events: none;
    }
""".trimIndent()

/**
 * Creates the accessibility activation button for [WebAccessibilityActivation.OnUserRequest]
 * activation, or returns `null` for other activation modes.
 *
 * The returned button must be attached to the DOM by the caller.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun createA11yActivationButton(activation: WebAccessibilityActivation): HTMLButtonElement? =
    if (activation is WebAccessibilityActivation.OnUserRequest) {
        (document.createElement("button") as HTMLButtonElement).apply {
            id = A11Y_ACTIVATION_BUTTON_ID
            type = "button"
            textContent = activation.activationLabel.ifBlank { DEFAULT_A11Y_ACTIVATION_LABEL }
        }
    } else {
        null
    }

/**
 * Finds the element the user should start interacting with after activating accessibility.
 *
 * The entry point is the Compose-focused node, if any; otherwise the first exposed element
 * (with a role, an aria-label or a text content). Editable and inert elements are skipped:
 * focusing an editable node invokes its click handler and can open the keyboard.
 *
 * Returns `null` when the tree contains no eligible element.
 *
 * @param webSemanticsRoot The root of the activated a11y DOM tree.
 * @param a11yNodeToSemanticsNode The listener's mapping from the a11y DOM elements to the
 * Compose semantics nodes they mirror, used to detect the focused node.
 */
internal fun findFirstEligibleA11YElementToFocus(
    webSemanticsRoot: HTMLElement,
    a11yNodeToSemanticsNode: MutableScatterMap<HTMLElement, SemanticsNode>,
): HTMLElement? {
    // A one-time scan on activation: [Element.closest] walks the ancestors for every element,
    // which is fine here — don't reuse this pattern in per-frame paths.
    var firstExposed: HTMLElement? = null
    var focused: HTMLElement? = null
    val elements = webSemanticsRoot.querySelectorAll("*")
    for (index in 0 until elements.length) {
        val element = elements[index] as? HTMLElement ?: continue
        val node = a11yNodeToSemanticsNode[element] ?: continue
        // Focusing editable nodes invokes their click handler and can open the keyboard.
        if (element.hasAttribute("contenteditable") ||
            element.closest("[inert], [aria-hidden=true]") != null
        ) continue

        if (node.config.getOrNull(SemanticsProperties.Focused) == true) {
            focused = element
            break
        }
        if (firstExposed == null && (
                element.hasAttribute("role") ||
                    element.hasAttribute("aria-label") ||
                    element.firstChild is Text
            )
        ) {
            firstExposed = element
        }
    }

    return focused ?: firstExposed
}
