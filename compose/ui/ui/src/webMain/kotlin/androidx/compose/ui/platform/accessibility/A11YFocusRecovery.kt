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

package androidx.compose.ui.platform.accessibility

import androidx.collection.ScatterMap
import androidx.compose.ui.internal.focusExt
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.isHidden
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * When a user navigates within an application, the previously focused node could get removed during
 * the A11Y tree sync. In this case the browser will move the focus elsewhere
 * (usually to the document body). This breaks the UX for the A11Y users.
 * We have to recover the focus. Choose the "best" focus target:
 * - Reuses the previously focused element if eligible;
 * - otherwise searches surviving ancestors' subtrees from nearest to farthest, choosing the first
 *   eligible element in DOM reading order.
 * - Prefer content leaves over ordinary containers; skip structural, synthetic, disabled, hidden, and inert nodes.
 * - If the owner is removed or inert, or no eligible target exists, focus is not restored.
 */
internal class A11YFocusRecovery(
    private val webSemanticsRoot: HTMLElement,
    private val a11yNodeToSemanticsNode: ScatterMap<HTMLElement, SemanticsNode>,
) {
    class FocusSnapshot(
        val element: HTMLElement,
        val ownerRoot: HTMLElement,
        val ancestors: List<HTMLElement>,
    )

    fun captureFocus(): FocusSnapshot? {
        val focused = focusedElement() ?: return null
        if (!webSemanticsRoot.contains(focused) || !a11yNodeToSemanticsNode.containsKey(focused)) {
            return null
        }

        val ancestors = ArrayList<HTMLElement>()
        var parent = focused.parentElement
        while (parent != null && parent !== webSemanticsRoot) {
            ancestors.add(parent as HTMLElement)
            parent = parent.parentElement
        }
        return FocusSnapshot(focused, ancestors.lastOrNull() ?: focused, ancestors)
    }

    fun restoreFocus(snapshot: FocusSnapshot) {
        val currentFocus = focusedElement()
        // Explicit focus moves, including moves outside Compose, take precedence over recovery.
        if (currentFocus != null && currentFocus !== document.body &&
            currentFocus !== document.documentElement
        ) return
        // Recovery stays within the previous owner. Layer transitions need a separate focus policy.
        if (!webSemanticsRoot.contains(snapshot.ownerRoot) || snapshot.ownerRoot.hasAttribute("inert")) {
            return
        }

        val target = if (snapshot.ownerRoot.contains(snapshot.element) && snapshot.element.isRecoveryTarget()) {
            snapshot.element
        } else {
            var replacement: HTMLElement? = null
            for (ancestor in snapshot.ancestors) {
                if (!snapshot.ownerRoot.contains(ancestor)) continue
                val descendants = ancestor.querySelectorAll("*")
                for (index in 0 until descendants.length) {
                    val candidate = descendants.item(index) as? HTMLElement ?: continue
                    if (candidate.isRecoveryTarget()) {
                        replacement = candidate
                        break
                    }
                }
                if (replacement != null) break
            }
            replacement
        } ?: return

        if (!target.hasAttribute("tabindex")) target.setAttribute("tabindex", "-1")
        focusExt(target, _preventScroll = true)
    }

    private fun HTMLElement.isRecoveryTarget(): Boolean {
        if (!isConnected || !webSemanticsRoot.contains(this)) return false
        val node = a11yNodeToSemanticsNode[this] ?: return false
        val config = node.config
        val role = config.getRoleId()
        if (node.isFake || node.isRoot || !config.containsImportantForAccessibility() ||
            config.isScrollContainer() || role == AriaRoleId.Dialog ||
            role == AriaRoleId.List || role == AriaRoleId.Grid
        ) return false

        // Text may contain links, while controls and merged semantics represent one logical unit.
        val isWholeTarget = config.hasNonEditableText() || role != AriaRoleId.Unknown ||
            config.isMergingSemanticsOfDescendants
        if (childElementCount > 0 && !isWholeTarget) return false

        var ancestor: Element? = this
        while (ancestor != null && ancestor !== webSemanticsRoot) {
            if (ancestor.hasAttribute("inert") || ancestor.hasAttribute("hidden") ||
                ancestor.getAttribute("aria-hidden") == "true"
            ) return false
            val semantics = (ancestor as? HTMLElement)?.let { a11yNodeToSemanticsNode[it] }
            if (semantics != null && (semantics.isHidden || SemanticsProperties.Disabled in semantics.config)) {
                return false
            }
            ancestor = ancestor.parentElement
        }
        return true
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun focusedElement(): HTMLElement? = js("""{
    let element = document.activeElement;
    while (element && element.shadowRoot && element.shadowRoot.activeElement) {
        element = element.shadowRoot.activeElement;
    }
    return element;
}""")
