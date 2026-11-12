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
import androidx.collection.mutableObjectListOf
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
 * - Start from the previously focused element, or from its nearest surviving ancestor if the
 *   element was removed, and widen the search one ancestor at a time up to the owner root.
 * - In every searched subtree choose the first suitable element in DOM reading order,
 *   see [FocusSuitability].
 * - If no suitable target exists (that includes a removed or an inert owner), focus is not
 *   restored. Recovery stays within the previous owner: layer transitions need a separate focus
 *   policy.
 *
 * Usage: call [onSyncStarted] before the A11Y tree sync modifies the DOM, and [onSyncFinished]
 * once the DOM is in its final state.
 */
internal class A11YFocusRecovery(
    private val webSemanticsRoot: HTMLElement,
    private val a11yNodeToSemanticsNode: ScatterMap<HTMLElement, SemanticsNode>,
) {
    /**
     * The elements whose subtrees are searched for a focus target, nearest first:
     * the focused A11Y element followed by its ancestors up to its owner root.
     * Empty when the focus is outside the A11Y tree.
     */
    private val focusPath = mutableObjectListOf<HTMLElement>()

    /**
     * Remembers where the focus is. It can't be found out after the sync: a removed element is
     * detached from its parent, so there would be no way to reach its surviving ancestors.
     */
    fun onSyncStarted() {
        val focused = focusedElement()
        if (focused != null && focused in a11yNodeToSemanticsNode) {
            setFocusPathFrom(focused)
        } else {
            focusPath.clear()
        }
    }

    /** Moves the focus to the best target if the sync has made the browser drop it. */
    fun onSyncFinished() {
        if (focusPath.isEmpty()) return

        // Explicit focus moves, including moves outside Compose, take precedence over recovery.
        if (isFocusLost()) {
            val target = findRecoveryTarget()
            if (target != null) {
                // Makes the element programmatically focusable. The sync keeps this attribute.
                if (!target.hasAttribute("tabindex")) target.setAttribute("tabindex", "-1")
                focusExt(target, _preventScroll = true)
            }
        }
        // Don't retain the removed elements until the next sync.
        focusPath.clear()
    }

    /**
     * Searches for a target near the place where the focus was, widening the search scope one
     * ancestor at a time:
     * ```
     * owner root                    <- 3rd scope, searched without "page"
     *  ├─ header
     *  └─ page                      <- 2nd scope, searched without "list"
     *      ├─ title
     *      └─ list                  <- 1st scope: the nearest surviving element
     *          └─ (removed item, it was focused)
     * ```
     */
    private fun findRecoveryTarget(): HTMLElement? {
        // The focused element could have been removed, along with some of its ancestors.
        val nearestSurvivor = focusPath.firstOrNull { it in a11yNodeToSemanticsNode } ?: return null

        // The scopes to search, innermost first: the survivor, its parent, ..., its owner root.
        // They are read from the DOM again, because the sync could have moved the elements.
        setFocusPathFrom(nearestSurvivor)
        val scopes = focusPath

        // A scope with no suitability (disabled, hidden or inert) has nothing suitable inside
        // it, including the scopes nested in it. So only the scopes outside the outermost such
        // scope are worth searching.
        // (All the scopes are searched when there is no such scope: the index is -1 then.)
        val outermostUnsuitable =
            scopes.indexOfLast { focusSuitabilityOf(it) == FocusSuitability.None }

        // Every scope contains the previous one, which has been searched already: skip it.
        var previousScope: HTMLElement? = null
        for (index in outermostUnsuitable + 1 until scopes.size) {
            val scope = scopes[index]
            val target = findTargetIn(scope, alreadySearched = previousScope)
            if (target != null) return target
            previousScope = scope
        }
        return null
    }

    /**
     * Returns the first suitable target in the subtree of [element] (including the [element]
     * itself) in DOM reading order. The subtree of [alreadySearched] is skipped: it is known to
     * contain no target.
     */
    private fun findTargetIn(element: HTMLElement, alreadySearched: HTMLElement?): HTMLElement? {
        if (element === alreadySearched) return null

        when (focusSuitabilityOf(element)) {
            FocusSuitability.Target -> return element
            FocusSuitability.None -> return null
            FocusSuitability.DescendantsOnly -> Unit // continue with the children
        }

        var child = element.firstElementChild
        while (child != null) {
            val target = findTargetIn(child as HTMLElement, alreadySearched)
            if (target != null) return target
            child = child.nextElementSibling
        }
        return null
    }

    private fun focusSuitabilityOf(element: HTMLElement): FocusSuitability {
        // The elements which don't mirror a semantics node (a scroll sizer) are leaves.
        val node = a11yNodeToSemanticsNode[element] ?: return FocusSuitability.None
        // `config` recreates the merged subtree on every call, so read it once
        val config = node.config

        val isDisabled = SemanticsProperties.Disabled in config
        if (isDisabled || node.isHidden || element.hasAttribute("inert")) {
            return FocusSuitability.None
        }

        val role = config.getRoleId()
        val isStructural = node.isRoot || node.isFake ||
            !config.containsImportantForAccessibility() || config.isScrollContainer() ||
            role == AriaRoleId.Dialog || role == AriaRoleId.List || role == AriaRoleId.Grid
        if (isStructural) return FocusSuitability.DescendantsOnly

        // Text may contain links, while controls and merged semantics represent one logical unit.
        val isWholeTarget = config.hasNonEditableText() || role != AriaRoleId.Unknown ||
            config.isMergingSemanticsOfDescendants
        // Prefer the content over an ordinary container.
        if (element.childElementCount > 0 && !isWholeTarget) return FocusSuitability.DescendantsOnly

        return FocusSuitability.Target
    }

    /** Sets [focusPath] to [start] followed by its ancestors up to its owner root. */
    private fun setFocusPathFrom(start: HTMLElement) {
        focusPath.clear()
        var element: Element? = start
        while (element != null && element !== webSemanticsRoot) {
            focusPath.add(element as HTMLElement)
            element = element.parentElement
        }
    }
}

private enum class FocusSuitability {
    /** The element can take the focus. */
    Target,

    /**
     * The element shouldn't take the focus, but its descendants can (if there are suitable ones):
     * a structural or synthetic node, or an ordinary container.
     */
    DescendantsOnly,

    /** Neither the element nor its descendants can take the focus: disabled, hidden or inert. */
    None,
}

/** The browser moves the focus to the document when the focused element can't keep it anymore. */
private fun isFocusLost(): Boolean {
    val focused = focusedElement()
    return focused == null || focused === document.body || focused === document.documentElement
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun focusedElement(): HTMLElement? = js("""{
    let element = document.activeElement;
    while (element && element.shadowRoot && element.shadowRoot.activeElement) {
        element = element.shadowRoot.activeElement;
    }
    return element;
}""")
