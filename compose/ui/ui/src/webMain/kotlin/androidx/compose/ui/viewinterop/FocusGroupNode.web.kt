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
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.js
import kotlinx.browser.window
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.FocusEvent
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
    @OptIn(ExperimentalWasmJsInterop::class)
    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.canFocus = node.isAttached
            // find at least one focusable element inside the interop container
            && getFocusableHtmlElement(getEmbeddedHtmlContainer()) != null
    }
}

private class FocusGroupPropertiesNode :
    Modifier.Node(), FocusPropertiesModifierNode, CompositionLocalConsumerModifierNode {

    private var lastTabKeyDown: KeyboardEvent? = null
    private var htmlElement: HTMLElement? = null

    @OptIn(ExperimentalWasmJsInterop::class)
    private val onEnter: FocusEnterExitScope.() -> Unit = {
        val focusTarget = when (requestedFocusDirection) {
            FocusDirection.Previous,
            FocusDirection.Up,
            FocusDirection.Left -> getFocusableHtmlElement(getEmbeddedHtmlContainer(), last = true)
            else -> getFocusableHtmlElement(getEmbeddedHtmlContainer())
        }
        focusTarget?.focus()
    }

    private val onExit: FocusEnterExitScope.() -> Unit = {
        htmlElement?.blur()
    }

    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.canFocus = false
        focusProperties.onEnter = onEnter
        focusProperties.onExit = onExit
    }

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

    private var focusedInHtml = false

    private val onFocusEvent = onFocusEvent@{ _: Event ->
        if (focusedInHtml) return@onFocusEvent
        focusedInHtml = true

        // Listen to Tab / Tab+Shift key down events to track where the focus moves.
        htmlElement?.addEventListener("keydown", tabKeyDownListener)

        // HTML element (or a child) gained focus. Update Compose focus too.
        val focusTargetNode = getFocusTargetOfEmbeddedViewWrapper()
        if (!focusTargetNode.focusState.hasFocus) {
            focusTargetNode.performRequestFocus()
        }
    }

    private val onBlurEvent = { event: Event ->
        val blurEvent = event as FocusEvent
        val isLeavingInteropContainer = blurEvent.relatedTarget == null
            || htmlElement?.contains(blurEvent.relatedTarget as HTMLElement) != true

      if (isLeavingInteropContainer) {
          htmlElement?.removeEventListener("keydown", tabKeyDownListener)
          focusedInHtml = false
      }

        val composeWindow = currentValueOf(LocalComposeWindow)!!
        val isFocusInComposeContainer = composeWindow.isFocusInComposeContainer()

        // If the browser moved focus to a different element within the Compose-managed html-subtree,
        // then focus canvas again so it can handle key events.
        if (isLeavingInteropContainer && isFocusInComposeContainer) {
            composeWindow.focusCanvas()

            // Now let Compose move its own focus according to the earlier Tab keydown.
            if (lastTabKeyDown != null) {
                val direction = if (lastTabKeyDown?.shiftKey == true) {
                    FocusDirection.Previous
                } else {
                    FocusDirection.Next
                }
                currentValueOf(LocalFocusManager).moveFocus(direction)
            }
        }

        lastTabKeyDown = null
    }

    override fun onAttach() {
        super.onAttach()
        htmlElement = getEmbeddedHtmlContainer()
        // capture=true to listen to focus/blur events on the children of the interop container
        htmlElement?.addEventListener("focus", onFocusEvent, AddEventListenerOptions(capture = true))
        htmlElement?.addEventListener("blur", onBlurEvent, AddEventListenerOptions(capture = true))
    }

    override fun onDetach() {
        htmlElement?.removeEventListener("focus", onFocusEvent, AddEventListenerOptions(capture = true))
        htmlElement?.removeEventListener("blur", onBlurEvent, AddEventListenerOptions(capture = true))
        super.onDetach()
        htmlElement = null
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

private fun Modifier.Node.getEmbeddedHtmlContainer(): HTMLElement {
    val interopView = node.requireLayoutNode().getInteropView() as? HTMLElement
    checkNotNull(interopView) { "Could not fetch interop view" }
    val interopContainer = interopView.parentElement as? HTMLElement
    checkNotNull(interopContainer) { "Could not fetch interop container" }
    return interopContainer
}

/**
 * Gets the first or last focusable HTML element inside [container].
 *
 * When [last] is false (default), performs a forward TreeWalker traversal with early exit.
 * When [last] is true, jumps to the deepest last leaf and walks backward for instant early exit.
 */
@OptIn(ExperimentalWasmJsInterop::class)
//language=js
internal fun getFocusableHtmlElement(container: HTMLElement, last: Boolean = false): HTMLElement? = js("""
(() => {
    if (!container) return null;

    // A flag to avoid redundant closest(inert) checks during upwards traversal when no inert nested elements are present
    const skipClosestInertCheck = last && !container.querySelector('[inert]');

    const selector = ':is(button, select, textarea, input:not([type="hidden"])):not([disabled]), [href], [tabindex]:not([tabindex="-1"]), [contenteditable]:not([contenteditable="false"]), summary, iframe, :is(audio, video)[controls]';
    const filter = {
        acceptNode(node) {
            if (node.inert) return NodeFilter.FILTER_REJECT; // completely skip an inert subtree
            if (node.matches(selector) && (skipClosestInertCheck || !node.closest('[inert]'))) return NodeFilter.FILTER_ACCEPT;
            return NodeFilter.FILTER_SKIP;
        }
    };

    if (!last) {
        const walker = document.createTreeWalker(container, NodeFilter.SHOW_ELEMENT, filter);
        return walker.nextNode();
    }

    // Reverse traversal — jump to the deepest last leaf, then walk backward
    let lastLeaf = container;
    while (lastLeaf.lastElementChild) {
        lastLeaf = lastLeaf.lastElementChild;
    }
    if (lastLeaf === container) return null;

    // If the last leaf itself is focusable, return it immediately
    if (filter.acceptNode(lastLeaf) === NodeFilter.FILTER_ACCEPT) {
        return lastLeaf;
    }

    // Position TreeWalker at the leaf and walk backward natively
    const walker = document.createTreeWalker(container, NodeFilter.SHOW_ELEMENT, filter);
    walker.currentNode = lastLeaf;
    return walker.previousNode();
})()""")
