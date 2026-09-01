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

import androidx.collection.MutableIntSet
import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlin.math.abs
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

internal class A11YScrollState(
    private val idToA11YNode: ScatterMap<Int, HTMLElement>,
    private val a11yNodeToSemanticsNode: ScatterMap<HTMLElement, SemanticsNode>,
) {
    // Scroll offsets are in CSS pixels. Pending values come from the current Compose state;
    // applied values distinguish our own DOM writes from browser- or AT-initiated scrolling.
    private val appliedScrollOffsets = MutableScatterMap<Int, Offset>()
    private val pendingScrollOffsets = MutableScatterMap<Int, Offset>()
    private val scrollSizers = MutableScatterMap<Int, HTMLElement>()
    private val scrollListenersAttached = MutableIntSet()

    /**
     * Translates browser scroll offset changes into Compose semantics scroll actions. Scroll events
     * do not bubble, so this listener is attached to every A11Y DOM scroll container.
     */
    private val onScroll: (Event) -> Unit = onScroll@ { event ->
        val element = event.target as? HTMLElement ?: return@onScroll
        val semanticsNode = a11yNodeToSemanticsNode[element] ?: return@onScroll
        val applied = appliedScrollOffsets[semanticsNode.id] ?: return@onScroll
        val actual = Offset(element.scrollLeft.toFloat(), element.scrollTop.toFloat())
        val deltaCssPx = actual - applied
        if (deltaCssPx.isCloseTo(Offset.Zero)) return@onScroll

        // Subsequent events before the next semantics sync must use the latest DOM offset.
        appliedScrollOffsets[semanticsNode.id] = actual

        val config = semanticsNode.config
        if (config.contains(SemanticsProperties.Disabled)) return@onScroll

        val density = semanticsNode.layoutNode.density.density
        val horizontalDirection =
            if (config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                    ?.reverseScrolling == true
            ) -1f else 1f
        val verticalDirection =
            if (config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                    ?.reverseScrolling == true
            ) -1f else 1f

        config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(
            deltaCssPx.x * density * horizontalDirection,
            deltaCssPx.y * density * verticalDirection,
        )
    }

    fun getScrollOffset(semanticsNode: SemanticsNode): Offset {
        return pendingScrollOffsets[semanticsNode.id]
            ?: appliedScrollOffsets[semanticsNode.id]
            ?: Offset.Zero
    }

    fun getScrollSizer(semanticsNode: SemanticsNode): HTMLElement? {
        return scrollSizers[semanticsNode.id]
    }

    /** Makes scroll semantics visible to the browser as a real CSS scroll container. */
    fun syncNodeScrollability(
        semanticsNode: SemanticsNode,
        config: SemanticsConfiguration,
        htmlNode: HTMLElement,
    ) {
        val nodeId = semanticsNode.id
        val verticalRange = config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        val horizontalRange = config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        val canScroll = (verticalRange != null || horizontalRange != null) &&
            config.getOrNull(SemanticsActions.ScrollBy)?.action != null

        if (!canScroll) {
            if (scrollListenersAttached.remove(nodeId)) {
                htmlNode.removeEventListener("scroll", onScroll)
                resetScrollContainerStyle(htmlNode)
                htmlNode.removeAttribute("aria-orientation")
                scrollSizers.remove(nodeId)?.remove()
                appliedScrollOffsets.remove(nodeId)
                pendingScrollOffsets.remove(nodeId)
            }
            return
        }

        setScrollContainerStyle(
            htmlNode,
            horizontal = horizontalRange != null,
            vertical = verticalRange != null,
        )

        when {
            verticalRange != null && horizontalRange == null ->
                htmlNode.setAttribute("aria-orientation", "vertical")
            horizontalRange != null && verticalRange == null ->
                htmlNode.setAttribute("aria-orientation", "horizontal")
            else ->
                // Bidirectional scroll, or no scroll range at all
                htmlNode.removeAttribute("aria-orientation")
        }

        val density = semanticsNode.layoutNode.density.density
        val maxHorizontal = horizontalRange?.maxValue?.invoke()?.coerceAtLeast(0f) ?: 0f
        val maxVertical = verticalRange?.maxValue?.invoke()?.coerceAtLeast(0f) ?: 0f
        val viewportWidth = semanticsNode.size.width / density
        val viewportHeight = semanticsNode.size.height / density
        val contentWidth =
            (viewportWidth + maxHorizontal / density).coerceAtMost(MAX_SCROLL_EXTENT_CSS_PX)
        val contentHeight =
            (viewportHeight + maxVertical / density).coerceAtMost(MAX_SCROLL_EXTENT_CSS_PX)

        val sizer = scrollSizers.getOrPut(nodeId) { createScrollSizer() }
        setSizeAndPosition(sizer, 0f, 0f, contentWidth, contentHeight)

        // Lazy layouts expose estimated accessibility offsets rather than physical pixels.
        // Preserve their current DOM offset instead of interpreting the estimate as scrollTop/Left.
        val isLazyLayout = config.contains(SemanticsActions.ScrollToIndex)
        val scrollLeft = if (isLazyLayout) {
            htmlNode.scrollLeft.toFloat()
        } else {
            horizontalRange?.let { range ->
                val value = range.value().coerceIn(0f, maxHorizontal)
                ((if (range.reverseScrolling) maxHorizontal - value else value) / density)
                    .coerceIn(0f, (contentWidth - viewportWidth).coerceAtLeast(0f))
            } ?: 0f
        }
        val scrollTop = if (isLazyLayout) {
            htmlNode.scrollTop.toFloat()
        } else {
            verticalRange?.let { range ->
                val value = range.value().coerceIn(0f, maxVertical)
                ((if (range.reverseScrolling) maxVertical - value else value) / density)
                    .coerceIn(0f, (contentHeight - viewportHeight).coerceAtLeast(0f))
            } ?: 0f
        }
        pendingScrollOffsets[nodeId] = Offset(scrollLeft, scrollTop)

        if (scrollListenersAttached.add(nodeId)) {
            htmlNode.addEventListener("scroll", onScroll)
        }
    }

    /** Applies Compose scroll offsets after DOM structure and scroll extents have been updated. */
    fun applyScrollOffsets() {
        pendingScrollOffsets.forEach { id, offset ->
            val element = idToA11YNode[id] ?: return@forEach
            val sizer = scrollSizers[id] ?: return@forEach
            if (sizer.parentElement !== element || element.firstElementChild !== sizer) {
                element.insertBefore(sizer, element.firstChild)
            }

            val actual = Offset(element.scrollLeft.toFloat(), element.scrollTop.toFloat())
            val lastApplied = appliedScrollOffsets[id]
            if (lastApplied != null && offset.isCloseTo(lastApplied) && !actual.isCloseTo(lastApplied)) {
                // Preserve a browser/AT offset until its asynchronous scroll event is handled.
                return@forEach
            }

            appliedScrollOffsets[id] = offset
            if (abs(actual.x - offset.x) >= SCROLL_EPSILON_CSS_PX) {
                element.scrollLeft = offset.x.toDouble()
            }
            if (abs(actual.y - offset.y) >= SCROLL_EPSILON_CSS_PX) {
                element.scrollTop = offset.y.toDouble()
            }
        }
        pendingScrollOffsets.clear()
    }

    fun clear() {
        scrollListenersAttached.forEach { id ->
            idToA11YNode[id]?.removeEventListener("scroll", onScroll)
        }
        appliedScrollOffsets.clear()
        pendingScrollOffsets.clear()
        scrollSizers.clear()
        scrollListenersAttached.clear()
    }

    fun onNodeRemoved(id: Int, htmlNode: HTMLElement?) {
        htmlNode?.removeEventListener("scroll", onScroll)

        appliedScrollOffsets.remove(id)
        pendingScrollOffsets.remove(id)
        scrollSizers.remove(id)
        scrollListenersAttached.remove(id)
    }
}

internal const val MAX_SCROLL_EXTENT_CSS_PX = 4_000_000f
internal const val SCROLL_EPSILON_CSS_PX = 0.5f

internal fun Offset.isCloseTo(other: Offset): Boolean =
    abs(x - other.x) < SCROLL_EPSILON_CSS_PX && abs(y - other.y) < SCROLL_EPSILON_CSS_PX

internal fun createScrollSizer(): HTMLElement {
    val sizer = document.createElement("div") as HTMLElement
    sizer.setAttribute("aria-hidden", "true")
    sizer.style.position = "absolute"
    return sizer
}

internal fun setScrollContainerStyle(
    element: HTMLElement,
    horizontal: Boolean,
    vertical: Boolean,
) {
    // language=javascript
    js(
        """
        element.style.overflowX = horizontal ? "scroll" : "hidden";
        element.style.overflowY = vertical ? "scroll" : "hidden";
        element.style.scrollbarWidth = "none";
        """
    )
}

internal fun resetScrollContainerStyle(element: HTMLElement) {
    // language=javascript
    js(
        """
        element.style.overflowX = "";
        element.style.overflowY = "";
        element.style.scrollbarWidth = "";
        """
    )
}
