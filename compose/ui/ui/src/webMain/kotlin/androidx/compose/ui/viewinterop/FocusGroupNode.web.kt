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

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.focus.FocusTargetNode
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.performRequestFocus
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.Nodes
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutNode
import androidx.compose.ui.node.visitLocalDescendants
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.window.LocalComposeWindow
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * Modifier that bridges HTML interop views into Compose's focus system.
 *
 * Applied automatically to [HtmlElementView] so that interop elements participate in
 * Compose focus navigation (Tab / Shift+Tab).
 *
 * Mirrors [FocusGroupNode.android.kt] for Android.
 */
internal fun Modifier.focusInteropModifier(): Modifier = this
    // Focus group to intercept focus enter/exit. Manages focus enter/exit events from the
    // HTML element and observes the focus state inside the interop element.
    .then(FocusGroupPropertiesElement)
    .focusTarget()
    // Focus target to make the embedded view focusable. This focusTarget becomes focused when
    // the associated HTML element gains focus. It represents the focusability of the interop view.
    .then(FocusTargetPropertiesElement)
    .then(FocusTargetInteropElement)

private object FocusTargetInteropElement : ModifierNodeElement<FocusTargetNode>() {
    override fun create() = FocusTargetNode(isInteropViewHost = true, onFocusChange = { _, _, -> })
    override fun update(node: FocusTargetNode) {}
    override fun hashCode() = "focusTargetInterop".hashCode()
    override fun equals(other: Any?) = other === this
}

private class FocusTargetPropertiesNode : Modifier.Node(), FocusPropertiesModifierNode {
    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.canFocus = node.isAttached
    }
}

private class FocusGroupPropertiesNode :
    Modifier.Node(), FocusPropertiesModifierNode, CompositionLocalConsumerModifierNode {

    private val onEnter: FocusEnterExitScope.() -> Unit = {
        getEmbeddedHtmlElement().focus()
    }

    private val onExit: FocusEnterExitScope.() -> Unit = {
        getEmbeddedHtmlElement().blur()
    }

    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.canFocus = false
        focusProperties.onEnter = onEnter
        focusProperties.onExit = onExit
    }

    private var lastTabKeyDown: KeyboardEvent? = null
    private var htmlElement: HTMLElement? = null

    private val tabKeyDownListener = { event: Event ->
        lastTabKeyDown = (event as? KeyboardEvent)?.takeIf {
            it.keyCode == Key.Tab.keyCode.toInt()
        }

        if (lastTabKeyDown != null) {
            // This will ensure focus indication:
            currentValueOf(LocalInputModeManager).requestInputMode(InputMode.Keyboard)

            // Reset in case no downstream events occur.
            window.requestAnimationFrame {
                lastTabKeyDown = null
            }
        }
    }

    private val onFocusEvent = { _: Event ->
        // Listen to Tab / Tab+Shift key down events to track where the focus moves.
        htmlElement?.addEventListener("keydown", tabKeyDownListener)

        // HTML element (or a child) gained focus. Update Compose focus too.
        val focusTargetNode = getFocusTargetOfEmbeddedViewWrapper()
        if (!focusTargetNode.focusState.hasFocus) {
            focusTargetNode.performRequestFocus()
        }
    }

    private val onBlurEvent = { _: Event ->
        htmlElement?.removeEventListener("keydown", tabKeyDownListener)

        val composeWindow = currentValueOf(LocalComposeWindow)!!
        val isFocusInComposeContainer = composeWindow.isFocusInComposeContainer()

        // If the browser moved focus to a different element within the Compose-managed html-subtree,
        // then focus canvas again so it can handle key events.
        if (isFocusInComposeContainer) {
            composeWindow.focusCanvas()
        }

        // Now let Compose move its own focus according to the earlier Tab keydown.
        if (isFocusInComposeContainer && lastTabKeyDown != null) {
            val direction = if (lastTabKeyDown?.shiftKey == true) {
                FocusDirection.Previous
            } else {
                FocusDirection.Next
            }
            currentValueOf(LocalFocusManager).moveFocus(direction)
        }

        lastTabKeyDown = null
    }

    override fun onAttach() {
        super.onAttach()
        htmlElement = getEmbeddedHtmlElement()
        htmlElement?.addEventListener("focus", onFocusEvent)
        htmlElement?.addEventListener("blur", onBlurEvent)
    }

    override fun onDetach() {
        htmlElement?.removeEventListener("focus", onFocusEvent)
        htmlElement?.removeEventListener("blur", onBlurEvent)
        super.onDetach()
    }

    private fun getFocusTargetOfEmbeddedViewWrapper(): FocusTargetNode {
        var foundFocusTargetOfFocusGroup = false
        visitLocalDescendants(Nodes.FocusTarget) {
            if (foundFocusTargetOfFocusGroup) return it
            foundFocusTargetOfFocusGroup = true
        }
        error("Could not find focus target of embedded view wrapper")
    }
}

private object FocusGroupPropertiesElement : ModifierNodeElement<FocusGroupPropertiesNode>() {
    override fun create(): FocusGroupPropertiesNode = FocusGroupPropertiesNode()
    override fun update(node: FocusGroupPropertiesNode) {}
    override fun hashCode() = "FocusGroupProperties".hashCode()
    override fun equals(other: Any?) = other === this
}

private object FocusTargetPropertiesElement : ModifierNodeElement<FocusTargetPropertiesNode>() {
    override fun create(): FocusTargetPropertiesNode = FocusTargetPropertiesNode()
    override fun update(node: FocusTargetPropertiesNode) {}
    override fun hashCode() = "FocusTargetProperties".hashCode()
    override fun equals(other: Any?) = other === this
}

private fun Modifier.Node.getEmbeddedHtmlElement(): HTMLElement {
    return checkNotNull(node.requireLayoutNode().getInteropView()) {
        "Could not fetch interop view"
    } as HTMLElement
}
