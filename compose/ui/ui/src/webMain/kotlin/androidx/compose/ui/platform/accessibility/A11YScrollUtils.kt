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
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlin.math.abs
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * Responsibilities:
 * 1. For Compose nodes with scrollable semantics,
 * it configures and maintains the scrollable html node / container. See [syncNodeScrollability]
 * 2. It ensures the two-way synchronization of scroll offsets:
 * - from Compose to A11Y tree. See [applyScrollOffsets]
 * - from A11Y tree back to Compose. See [onScroll]
 *
 * The scrollability of the html node is achieved by placing a "sizer" element exceeding
 * the container's sizes (according to Compose-specified scroll ranges), allowing AT and browser
 * manipulate the scroll offset.
 */
internal class A11YScrollController(
    private val idToA11YNode: ScatterMap<Int, HTMLElement>,
    private val a11yNodeToSemanticsNode: ScatterMap<HTMLElement, SemanticsNode>,
) {

    // When a browser (or AT) updates the scroll offset of the node in A11Y tree, we apply the
    // new offset to SemanticsNode and save the applied offset value here.
    private val appliedScrollOffsets = MutableScatterMap<Int, Offset>()

    // When we process the Semantics updates, we record the new scroll offsets here.
    // After the new offsets get applied to A11Y tree, the map is cleared.
    private val pendingScrollOffsets = MutableScatterMap<Int, Offset>()

    // The non-semantic elements inserted into A11Y scrollable nodes.
    // To make the scrollable a11y node aware of the possible scroll ranges, they include this "sizer"
    // element, which width/height equal to the scrollable viewport size + max scroll distance.
    private val domScrollSizers = MutableScatterMap<Int, HTMLElement>()

    // Tracking the nodes with a scroll listener:
    private val scrollListenersAttached = MutableIntSet()

    // Applies the browser scroll offset changes to the corresponding SemanticsNode - SemanticsActions.ScrollBy
    private val onScroll: (Event) -> Unit = onScroll@ { event ->
        val element = event.target as? HTMLElement ?: return@onScroll
        val semanticsNode = a11yNodeToSemanticsNode[element] ?: return@onScroll
        val applied = appliedScrollOffsets[semanticsNode.id] ?: return@onScroll
        val actual = Offset(element.scrollLeft.toFloat(), element.scrollTop.toFloat())

        val deltaCssPx = actual - applied
        if (deltaCssPx.isCloseTo(Offset.Zero)) return@onScroll
        appliedScrollOffsets[semanticsNode.id] = actual

        val config = semanticsNode.config
        if (config.contains(SemanticsProperties.Disabled)) return@onScroll

        val density = semanticsNode.layoutNode.density.density
        val horizontalDirection = config.getSupportedScrollDirection(horizontal = true)
        val verticalDirection = config.getSupportedScrollDirection(horizontal = false)

        config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(
            deltaCssPx.x * density * horizontalDirection,
            deltaCssPx.y * density * verticalDirection,
        )
    }

    private fun SemanticsConfiguration.getSupportedScrollDirection(
        horizontal: Boolean
    ): Float {
        val key = if (horizontal) {
            SemanticsProperties.HorizontalScrollAxisRange
        } else {
            SemanticsProperties.VerticalScrollAxisRange
        }
        val isReverse = this.getOrNull(key)?.reverseScrolling == true
        return if (isReverse) {
            -1f
        } else {
            1f
        }
    }

    fun getScrollOffset(semanticsNode: SemanticsNode): Offset {
        return pendingScrollOffsets[semanticsNode.id]
            ?: appliedScrollOffsets[semanticsNode.id]
            ?: Offset.Zero
    }

    fun getScrollSizer(semanticsNode: SemanticsNode): HTMLElement? {
        return domScrollSizers[semanticsNode.id]
    }

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
                removeScrollProperties(htmlNode, nodeId)
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

        val sizerElementWidth = (viewportWidth + maxHorizontal / density)
            .coerceAtMost(MAX_SUPPORTED_SCROLL_CSS_PX)
        val sizerElementHeight = (viewportHeight + maxVertical / density)
            .coerceAtMost(MAX_SUPPORTED_SCROLL_CSS_PX)

        val sizerElement = domScrollSizers.getOrPut(nodeId) { createDomScrollSizer() }
        setSizeAndPosition(sizerElement, 0f, 0f, sizerElementWidth, sizerElementHeight)

        val scrollLeft = horizontalRange?.toCssScrollOffset(
            maxValue = maxHorizontal,
            viewportSize = viewportWidth,
            contentSize = sizerElementWidth,
            density = density,
        ) ?: 0f

        val scrollTop = verticalRange?.toCssScrollOffset(
            maxValue = maxVertical,
            viewportSize = viewportHeight,
            contentSize = sizerElementHeight,
            density = density,
        ) ?: 0f

        pendingScrollOffsets[nodeId] = Offset(scrollLeft, scrollTop)

        if (scrollListenersAttached.add(nodeId)) {
            htmlNode.addEventListener("scroll", onScroll)
        }
    }

    private fun removeScrollProperties(htmlNode: HTMLElement, nodeId: Int) {
        resetScrollContainerStyle(htmlNode)
        htmlNode.removeEventListener("scroll", onScroll)
        htmlNode.removeAttribute("aria-orientation")
        domScrollSizers.remove(nodeId)?.remove()
        appliedScrollOffsets.remove(nodeId)
        pendingScrollOffsets.remove(nodeId)
    }

    // Applies Compose scroll offsets to DOM
    fun applyScrollOffsets() {
        pendingScrollOffsets.forEach { id, offset ->
            val element = idToA11YNode[id] ?: return@forEach
            val sizer = domScrollSizers[id] ?: return@forEach
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
        domScrollSizers.clear()
        scrollListenersAttached.clear()
    }

    fun onNodeRemoved(id: Int, htmlNode: HTMLElement?) {
        htmlNode?.removeEventListener("scroll", onScroll)

        appliedScrollOffsets.remove(id)
        pendingScrollOffsets.remove(id)
        domScrollSizers.remove(id)
        scrollListenersAttached.remove(id)
    }
}

// We don't expect such huge layouts in Compose (it's likely too expensive), so
// keep synthetic scroll range well below known browser layout limits (>10kk).
internal const val MAX_SUPPORTED_SCROLL_CSS_PX = 4_000_000f
internal const val SCROLL_EPSILON_CSS_PX = 0.5f

@Suppress("NOTHING_TO_INLINE")
internal inline fun Offset.isCloseTo(other: Offset): Boolean =
    abs(x - other.x) < SCROLL_EPSILON_CSS_PX && abs(y - other.y) < SCROLL_EPSILON_CSS_PX

internal fun createDomScrollSizer(): HTMLElement {
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


private fun ScrollAxisRange.toCssScrollOffset(
    maxValue: Float,
    viewportSize: Float,
    contentSize: Float,
    density: Float,
): Float {
    val value = value().coerceIn(0f, maxValue)
    val offset = if (reverseScrolling) {
        maxValue - value
    } else {
        value
    }

    return (offset / density)
        .coerceIn(0f, (contentSize - viewportSize).coerceAtLeast(0f))
}