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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusDirection.Companion.Exit
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.FocusTargetNode
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.performRequestFocus
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.requireLayoutNode
import androidx.compose.ui.node.requireOwner
import androidx.compose.ui.node.visitLocalDescendants
import androidx.compose.ui.node.Nodes
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.window.LocalComposeWindow
import kotlin.js.js
import kotlinx.browser.document
import kotlinx.browser.window
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

private object FocusTargetInteropElement : ModifierNodeElement<FocusTargetInteropNode>() {
    override fun create() = FocusTargetInteropNode()

    override fun update(node: FocusTargetInteropNode) {}

    override fun hashCode() = "focusTargetInterop".hashCode()

    override fun equals(other: Any?) = other === this
}

/**
 * The node that becomes focused on the Compose side when the associated HTML element gains focus.
 */
private class FocusTargetInteropNode :
    DelegatingNode(), ObserverModifierNode, CompositionLocalConsumerModifierNode {

    private val focusTargetNode =
        delegate(FocusTargetNode(isInteropViewHost = true, onFocusChange = ::onFocusStateChange))

    private fun onFocusStateChange(previousState: FocusState, currentState: FocusState) {
        if (!isAttached) return
        val isFocused = currentState.isFocused
        val wasFocused = previousState.isFocused
        // Ignore cases where we are initialized as unfocused, or moving between different
        // unfocused states.
        if (isFocused == wasFocused) return
        // Pinning could be added here in the future if needed.
    }

    override fun onObservedReadsChanged() {}
}

private class FocusTargetPropertiesNode : Modifier.Node(), FocusPropertiesModifierNode {
    override fun applyFocusProperties(focusProperties: FocusProperties) {
        val htmlElement = getEmbeddedHtmlElement()
        focusProperties.canFocus = node.isAttached && true // htmlElement.isFocusable()
//        htmlElement.getBoundingClientRect()?.let { rect ->
//            focusProperties.focusRect = Rect(rect.left.toFloat(), rect.top.toFloat(),
//                rect.right.toFloat(), rect.bottom.toFloat())
//        }
    }
}

/**
 * Checks if an HTML element can receive focus.
 */
//private fun HTMLElement.isFocusable(): Boolean = js("this.tabIndex >= 0 || this.tagName === 'BUTTON' || this.tagName === 'INPUT' || " +
//        "this.tagName === 'SELECT' || this.tagName === 'TEXTAREA' || this.tagName === 'A'")

/**
 * Gets the bounding client rect of an HTML element.
 */
//private fun HTMLElement.getBoundingClientRect(): Rect? {
//    // language=javascript
//    return js("var rect = this.getBoundingClientRect(); " +
//        "if (rect.width === 0 && rect.height === 0) return null; " +
//        "return new androidx.compose.ui.geometry.Rect(rect.left, rect.top, rect.right, rect.bottom);")
//}

private class FocusGroupPropertiesNode :
    Modifier.Node(), FocusPropertiesModifierNode, CompositionLocalConsumerModifierNode {

    var focusedChild: HTMLElement? = null

    val onEnter: FocusEnterExitScope.() -> Unit = {
        val htmlElement = getEmbeddedHtmlElement()
        if (!htmlElement.isFocused()) {
            htmlElement.focus()
            // Try to focus the HTML element or its first focusable child.
//            val target = (htmlElement.querySelector(":focusable") ?: htmlElement) as? HTMLElement
//            target?.focus()
        }
    }

    val onExit: FocusEnterExitScope.() -> Unit = {
        val htmlElement = getEmbeddedHtmlElement()
        if (htmlElement.isFocused()) {
            htmlElement.blur()
        }
    }

    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.canFocus = false
        focusProperties.onEnter = onEnter
        focusProperties.onExit = onExit
    }

    private var lastTabKeyDown: KeyboardEvent? = null

    override fun onAttach() {
        println("FocusGroupNode.onAttach")
        super.onAttach()
        val htmlElement = getEmbeddedHtmlElement()
        println("htmlElement: $htmlElement")
        val tabKeyDownListener = { event: Event ->
            println("Tab keydown!!! - $event")

            lastTabKeyDown = (event as? KeyboardEvent)?.takeIf { it.key == "Tab" }


            if (lastTabKeyDown != null) {
                // This will ensure focus indication:
                currentValueOf(LocalInputModeManager).requestInputMode(InputMode.Keyboard)
                window.requestAnimationFrame {
                    lastTabKeyDown = null
                }
            }

            Unit
        }
        htmlElement.addEventListener("focus") {
            println("focus::: HTML element gained focus")
            htmlElement.addEventListener("keydown", tabKeyDownListener)
            // HTML element (or a child) gained focus. Sync Compose focus to the interop wrapper.
            val focusTargetNode = getFocusTargetOfEmbeddedViewWrapper()
            if (!focusTargetNode.focusState.hasFocus) {
                focusTargetNode.performRequestFocus()
            }
        }
        htmlElement.addEventListener("blur") {
            println("blur::: HTML element lost focus")
            htmlElement.removeEventListener("keydown", tabKeyDownListener)
            if (lastTabKeyDown != null) {
                println("blur::: Tab - $lastTabKeyDown")
                val localComposeWindow = currentValueOf(LocalComposeWindow)
                localComposeWindow?.focusCanvas()
                val focusManager = currentValueOf(LocalFocusManager)
                val direction = if (lastTabKeyDown?.shiftKey == true) FocusDirection.Previous else FocusDirection.Next
                focusManager.moveFocus(direction)
                println("blur::: Tab - direction: $direction")
                lastTabKeyDown = null
            }
        }
    }

    override fun onDetach() {
        println("FocusGroupNode.onDetach")
        val htmlElement = getEmbeddedHtmlElement()
//        htmlElement.removeEventListener("focusin", ::onFocusIn)
//        htmlElement.removeEventListener("focusout", ::onFocusOut)
        focusedChild = null
        super.onDetach()
    }

    private val onFocusIn: (Event) -> Unit = { event ->
    }

    private val onFocusOut: (Event) -> Unit = { event ->
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

private fun HTMLElement.isFocused(): Boolean {
    val root = getRootNode()
    return false
}

private external interface DocumentOrShadowRootLike {
    val activeElement: HTMLElement?
}

private fun getRootNode(): DocumentOrShadowRootLike = js("this.getRootNode()")