/*
 * Copyright 2025 The Android Open Source Project
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
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastJoinToString
import kotlin.js.js
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

internal class ComposeWebSemanticsListener(
    val webSemanticsRoot: HTMLElement,
) : PlatformContext.SemanticsOwnerListener {

    private val invalidationChannel =
        Channel<Unit>(1, onBufferOverflow = BufferOverflow.DROP_LATEST)
    private val syncTriggerChannel =
        Channel<Long>(1, onBufferOverflow = BufferOverflow.DROP_LATEST)

    private companion object {
        const val MAX_TIME_IN_DEBOUNCE_MS = 1000L
        const val DEBOUNCE_MS = 100L
    }

    private var hasStarted = false
    private var hasStopped = false
    private val startJob = Job()

    /**
     * @param coroutineScope The [CoroutineScope] used to run this listener,
     * typically the composition scope so the listener follows the composition lifecycle.
     */
    internal fun start(coroutineScope: CoroutineScope) {
        check(!hasStopped) { "ComposeWebSemanticsListener can't be started after it was stopped" }
        if (hasStarted) return
        hasStarted = true

        // Here we do the following:
        // - Every invalidation doesn't trigger an a11y tree sync immediately, but only after the changes have settled (debounce 100ms).
        // - We track the time spent in "debounce", so eventually it must sync the a11y tree despite no pause in invalidation events (the changes couldn't settle).
        // So the a11y tree sync will happen either when the changes have settled or when the timeSpentInDebounce exceeds 1000 ms.

        /*
              1) --x-x-x-x-------------------------------------------------
                         |--- 100ms ---| -> sync after changes settle

              2) ---x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x--
                    |-------- 1000ms -------| spent 1 second debouncing
                                            |-> forced sync

              3) ----------------------------x-x-x-x-x-x-x-x---------------
                 |---------- 1200ms ---------|             |--- 100 ms ---| -> sync after changes settle
                                             | No forced sync here, because the debouncing has just started
         */
        coroutineScope.launch(
            context = startJob,
            start = CoroutineStart.UNDISPATCHED
        ) {
            var timeSpentDebouncing = 0L
            var lastDebouncedTime = 0L
            var lastSyncTime = currentTimeMillis()

            launch(start = CoroutineStart.UNDISPATCHED) {
                invalidationChannel.receiveAsFlow().collect {
                    val currentTime = currentTimeMillis()

                    if (lastDebouncedTime == 0L) {
                        lastDebouncedTime = currentTime
                        timeSpentDebouncing = 0L
                    } else {
                        val delta = currentTime - lastDebouncedTime
                        timeSpentDebouncing += delta
                        lastDebouncedTime = currentTime
                    }

                    if (timeSpentDebouncing >= MAX_TIME_IN_DEBOUNCE_MS) {
                        // we've been debouncing for too long, but must sync periodically, so force a sync
                        lastDebouncedTime = 0L
                        lastSyncTime = currentTime
                        syncSemanticsWithWebA11Y()
                    } else {
                        syncTriggerChannel.trySend(currentTime)
                    }
                }
            }

            @OptIn(FlowPreview::class)
            launch(start = CoroutineStart.UNDISPATCHED) {
                // debounce until the Semantics changes settled for at least 100ms
                syncTriggerChannel.receiveAsFlow().debounce(DEBOUNCE_MS.milliseconds).collect {
                    val currentTime = currentTimeMillis()

                    // syncSemanticsWithWebA11Y could've been triggered from a "force sync" above,
                    // so we check the lastSyncTime here
                    if (currentTime - lastSyncTime >= DEBOUNCE_MS) {
                        lastDebouncedTime = 0L
                        lastSyncTime = currentTime
                        syncSemanticsWithWebA11Y()
                    }
                }
            }
        }

        // Event delegation: all nodes delegate to one global click listener
        webSemanticsRoot.addEventListener("click", onClick)
    }

    private val semanticsOwners = mutableListOf<SemanticsOwner>()

    override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
        if (semanticsOwners.contains(semanticsOwner)) return
        semanticsOwners.add(semanticsOwner)
        invalidationChannel.trySend(Unit)
    }

    override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
        if (semanticsOwners.remove(semanticsOwner)) {
            invalidationChannel.trySend(Unit)
        }
    }

    override fun onSemanticsChange(semanticsOwner: SemanticsOwner) {
        invalidationChannel.trySend(Unit)
    }

    override fun onLayoutChange(
        semanticsOwner: SemanticsOwner, semanticsNodeId: Int
    ) {
        invalidationChannel.trySend(Unit)
    }

    private val dfsDeque = ArrayDeque<SemanticsNode>()

    private val nodes = MutableScatterMap<Int, SemanticsNode>()
    private val nodeToParent = MutableScatterMap<Int, Int>()
    private val webNodes = MutableScatterMap<Int, HTMLElement>()
    private val elementToSemanticsNode = MutableScatterMap<HTMLElement, SemanticsNode>()

    /**
     * The scroll offsets (in CSS px) we last derived from the Compose scroll state and applied to
     * the scrollable elements ourselves. A "scroll" event whose offset differs from this value is
     * a scroll initiated by the browser or an assistive technology and must be forwarded to
     * Compose via [SemanticsActions.ScrollBy]; a matching offset is just the echo of our own
     * programmatic assignment and must be ignored.
     */
    private val expectedScrollOffsets = MutableScatterMap<Int, Offset>()

    /**
     * The scroll offsets computed from the Compose scroll state during the current tree sync.
     * They are moved into [expectedScrollOffsets] (and applied to the elements) by
     * [applyScrollOffsets] after the whole tree is synced — unless the element's offset was
     * changed by an AT in the meantime, see [applyScrollOffsets].
     */
    private val pendingScrollOffsets = MutableScatterMap<Int, Offset>()

    /**
     * Invisible elements stretching the scrollable containers to their full content extent
     * (viewport + maxValue of the scroll range), so the browser exposes them to assistive
     * technologies as scrollable and gives them the correct scroll range.
     */
    private val scrollSizers = MutableScatterMap<Int, HTMLElement>()

    // Ids of the nodes that have the `onScroll` listener attached to their html element.
    private val scrollListenersAttached = MutableIntSet()

    // A reusable set of node ids for sync purposes
    private val allNodesIds = MutableIntSet()

    /**
     * Event delegation: Single shared click listener for all a11y nodes with SemanticsActions.OnClick.
     * It's expected to be triggered by A11Y tools and tests (element.click()), not by pointer input.
     */
    private val onClick: (Event) -> Unit = onClick@ { event ->
        val semanticsNode = (event.target as? HTMLElement)
            ?.let { elementToSemanticsNode[it] }
            ?: return@onClick

        val config = semanticsNode.config

        if (!config.contains(SemanticsProperties.Disabled) &&
            config.contains(SemanticsActions.OnClick)
        ) {
            config[SemanticsActions.OnClick].action?.invoke()
        }
    }

    /**
     * Shared "scroll" listener for all scroll container nodes.
     *
     * Assistive technologies never send an explicit "scroll" command to a web page. Instead they
     * ask the *browser* to scroll: either by requesting an element to be brought into view when
     * the screen reader cursor moves to it, or via dedicated scroll gestures (e.g. the VoiceOver
     * three-finger swipe) targeting an element the browser exposed as scrollable. In both cases
     * the only thing the page observes is a change of `scrollLeft`/`scrollTop` and a "scroll"
     * event. Here we translate that offset change back into a [SemanticsActions.ScrollBy] action,
     * so Compose performs the actual scroll of the content.
     *
     * Note: "scroll" events don't bubble, so this listener is attached per scrollable element
     * rather than delegated to [webSemanticsRoot].
     */
    private val onScroll: (Event) -> Unit = onScroll@ { event ->
        val element = event.target as? HTMLElement ?: return@onScroll
        val semanticsNode = elementToSemanticsNode[element] ?: return@onScroll

        val expected = expectedScrollOffsets[semanticsNode.id] ?: return@onScroll
        val actual = Offset(element.scrollLeft.toFloat(), element.scrollTop.toFloat())
        val deltaCssPx = actual - expected
        // The epsilon absorbs Float->Double conversion noise and fractional offsets stored by
        // the browser, so our own programmatic writes are never misread as an AT scroll.
        if (abs(deltaCssPx.x) < SCROLL_EPSILON_CSS_PX && abs(deltaCssPx.y) < SCROLL_EPSILON_CSS_PX) {
            return@onScroll
        }

        // Remember the offset the AT scrolled to, so the deltas of the follow-up scroll events
        // arriving before the next a11y tree sync are computed against the up-to-date offset.
        expectedScrollOffsets[semanticsNode.id] = actual

        val config = semanticsNode.config
        if (config.contains(SemanticsProperties.Disabled)) return@onScroll

        val density = semanticsNode.layoutNode.density.density
        // The DOM scroll offset grows in the "forward" direction of the a11y element, while
        // ScrollBy expects deltas in the Compose scroll direction, so flip the sign for
        // reversed scrolling (see how expectedScrollOffsets values are computed in syncNode).
        val hReverse =
            config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.reverseScrolling == true
        val vReverse =
            config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.reverseScrolling == true

        val dx = deltaCssPx.x * density * (if (hReverse) -1f else 1f)
        val dy = deltaCssPx.y * density * (if (vReverse) -1f else 1f)

        config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(dx, dy)
    }


    private fun syncSemanticsWithWebA11Y() {
        allNodesIds.clear()

        semanticsOwners.fastForEach {
            syncSemanticsWithWebA11Y(it, allNodesIds)
        }

        val removedIds = mutableSetOf<Int>()

        webNodes.forEachKey {
            if (it !in allNodesIds) {
                webNodes[it]?.remove()
                removedIds.add(it)
            }
        }

        removedIds.forEach {
            val htmlNode = webNodes.remove(it)
            if (htmlNode != null) {
                elementToSemanticsNode.remove(htmlNode)
            }
            nodes.remove(it)
            nodeToParent.remove(it)
            expectedScrollOffsets.remove(it)
            pendingScrollOffsets.remove(it)
            scrollSizers.remove(it)
            scrollListenersAttached.remove(it)
        }

        applyScrollOffsets()
        updateInertRoots()
    }

    // The last (top) root is never inert.
    // Other owners might become inert when the top root contains a dialog.
    // See LayersA11YTest.
    private fun updateInertRoots() {
        val lastOwnerRoot = webSemanticsRoot.lastElementChild
        lastOwnerRoot?.setInert(false)

        // Assuming the dialog semantics are set on the first node of the owner:
        val isModalOnTop = lastOwnerRoot?.firstElementChild?.hasAttribute("aria-modal") == true

        val children = webSemanticsRoot.children
        repeat(children.length - 1) {
            val ownerRoot = children.item(it)
            ownerRoot?.setInert(isModalOnTop)
        }
    }

    /**
     * Sync the tree corresponding to the [semanticsOwner] and populate [allNodesIds] - a set of node ids.
     */
    private fun syncSemanticsWithWebA11Y(
        semanticsOwner: SemanticsOwner,
        allNodesIds: MutableIntSet
    ) {
        fun SemanticsNode.isValid() = layoutNode.let { it.isPlaced && it.isAttached }

        val root = semanticsOwner.rootSemanticsNode
        if (!root.isValid()) return

        dfsDeque.clear()
        dfsDeque.addLast(root)

        val rootPosition = webSemanticsRoot.getBoundingClientRect().let {
            Offset(it.left.toFloat(), it.top.toFloat())
        }

        while (!dfsDeque.isEmpty()) {
            val node = dfsDeque.removeLast()
            val currentId = node.id
            allNodesIds.add(currentId)

            // `config` recreates the merged subtree on every call, so read it once
            val config = node.config

            val children = node.replacedChildren.asReversed()
            dfsDeque.addAll(children)
            children.fastForEach { it -> nodeToParent[it.id] = currentId }

            val htmlNode = if (nodes[currentId] != null) {
                nodes[currentId] = node
                val htmlNode = webNodes[currentId] ?: error("Node $currentId not found")

                if (children.isNotEmpty()) {
                    // To ensure the correct order of nested nodes, we remove all of them.
                    // I assume it's more efficient to remove and re-add them than to insert the nodes at specific positions.
                    // Also, the code is more simple with this approach.
                    // They are added below.
                    removeAllChildrenOf(htmlNode)
                }

                syncNode(node, config, htmlNode, rootPosition)
                htmlNode
            } else {
                nodes[currentId] = node
                val htmlNode = document.createElement("div") as HTMLElement
                htmlNode.style.apply {
                    position = "fixed"
                    whiteSpace = "pre"
                }

                webNodes[currentId] = htmlNode
                syncNode(node, config, htmlNode, rootPosition, true)
                htmlNode
            }

            // find the parent node and attach to it
            val parentId = nodeToParent[currentId]
            val htmlParent = parentId?.let { webNodes[it] } ?: webSemanticsRoot
            htmlParent.appendChild(htmlNode)
            elementToSemanticsNode[htmlNode] = node
        }
    }

    private fun syncNode(
        sn: SemanticsNode,
        config: SemanticsConfiguration,
        htmlNode: HTMLElement,
        rootOffset: Offset,
        justCreated: Boolean = false,
    ) {
        if (config.contains(SemanticsProperties.Text)) {
            val text = config[SemanticsProperties.Text]
            htmlNode.innerText = text.fastJoinToString("\n") { it.text }
        }

        if (config.contains(SemanticsProperties.ContentDescription)) {
            val contentDescription = config[SemanticsProperties.ContentDescription]
            htmlNode.setAttribute("aria-label", contentDescription.fastJoinToString(", "))
        }

        if (config.contains(SemanticsProperties.TestTag)) {
            val testTag = config[SemanticsProperties.TestTag]
            htmlNode.id = testTag
        }

        if (config.contains(SemanticsProperties.EditableText)) {
            val text = config[SemanticsProperties.EditableText].text
            htmlNode.innerText = text

            if (justCreated) {
                htmlNode.setAttribute("contenteditable", "true")
                htmlNode.addEventListener("focus", {
                    htmlNode.click()
                })
            }
        }

        if (config.contains(SemanticsProperties.Disabled)) {
            htmlNode.setAttribute("aria-disabled", "true")
        } else {
            htmlNode.removeAttribute("aria-disabled")
        }

        setA11YAriaRole(element = htmlNode, config.getRoleId())

        if (config.contains(SemanticsProperties.IsDialog)) {
            htmlNode.setAttribute("aria-modal", "true")
        } else {
            htmlNode.removeAttribute("aria-modal")
        }

        syncScrollability(sn, config, htmlNode)

        val density = sn.layoutNode.density.density

        // Note: the unclipped geometry (positionInRoot + size) is used instead of boundsInRoot.
        // Within a scroll container the children must be laid out at their full, unclipped
        // positions in the container's content coordinate space: this is the geometry the browser
        // uses to compute the scroll offset when an assistive technology asks to bring a
        // (partially) offscreen element into view.
        val positionInRoot = sn.positionInRoot
        val width = sn.size.width / density
        val height = sn.size.height / density

        val parentNode = nodeToParent[sn.id]?.let { nodes[it] }
        if (parentNode == null) {
            // An owner root: positioned in viewport coordinates, as before.
            htmlNode.style.position = "fixed"
            val left = rootOffset.x + positionInRoot.x / density
            val top = rootOffset.y + positionInRoot.y / density
            setSizeAndPosition(htmlNode, left, top, width, height)
        } else {
            // Positioned relative to the parent node's element (the nearest positioned ancestor).
            // If the parent is a scroll container, the position is in its *content* coordinate
            // space: the visible offset within the parent plus the parent's scroll offset. The
            // browser then subtracts the DOM scroll offset back when rendering, so the element
            // still overlays the same content on the canvas, but scrolling machinery
            // (scroll-into-view, AT scroll gestures) now sees the correct geometry.
            htmlNode.style.position = "absolute"
            // The parent is synced before its children in the DFS, so a scrollable parent has
            // its fresh offset in pendingScrollOffsets by now.
            val parentScrollCssPx = pendingScrollOffsets[parentNode.id]
                ?: expectedScrollOffsets[parentNode.id]
                ?: Offset.Zero
            val left =
                (positionInRoot.x - parentNode.positionInRoot.x) / density + parentScrollCssPx.x
            val top =
                (positionInRoot.y - parentNode.positionInRoot.y) / density + parentScrollCssPx.y
            setSizeAndPosition(htmlNode, left, top, width, height)
        }
    }

    /**
     * Makes the [htmlNode] of a semantics node with scroll semantics a real CSS scroll container:
     * an element with a non-`visible` `overflow` and an inner "sizer" element stretching its
     * scrollable extent to the full content size.
     *
     * This is what browsers derive "scrollability" from — there is no ARIA attribute for it.
     * Only a genuine CSS scroll container is exposed to assistive technologies as scrollable
     * (with its scroll range taken from the layout), can be targeted by AT scroll gestures, and
     * gets scrolled by the browser when an AT asks to bring one of its descendants into view.
     *
     * The DOM scroll offset is kept in sync with the Compose scroll state:
     * - Compose -> DOM: [expectedScrollOffsets] is computed here from the ScrollAxisRanges and
     *   applied to the elements in [applyScrollOffsets] after the tree sync.
     * - DOM -> Compose: [onScroll] translates offsets diverging from [expectedScrollOffsets]
     *   into [SemanticsActions.ScrollBy].
     */
    private fun syncScrollability(
        sn: SemanticsNode,
        config: SemanticsConfiguration,
        htmlNode: HTMLElement,
    ) {
        val nodeId = sn.id
        val vRange = config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        val hRange = config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        val canScroll = (vRange != null || hRange != null) &&
            config.getOrNull(SemanticsActions.ScrollBy)?.action != null

        if (!canScroll) {
            if (scrollListenersAttached.remove(nodeId)) {
                // The node is no longer a scroll container
                htmlNode.removeEventListener("scroll", onScroll)
                resetScrollContainerStyle(htmlNode)
                htmlNode.removeAttribute("aria-orientation")
                scrollSizers.remove(nodeId)?.remove()
                expectedScrollOffsets.remove(nodeId)
                pendingScrollOffsets.remove(nodeId)
            }
            return
        }

        setScrollContainerStyle(htmlNode, hRange != null, vRange != null)

        if ((vRange != null) xor (hRange != null)) {
            htmlNode.setAttribute(
                "aria-orientation",
                if (vRange != null) "vertical" else "horizontal"
            )
        } else {
            htmlNode.removeAttribute("aria-orientation")
        }

        val density = sn.layoutNode.density.density
        val maxH = hRange?.maxValue?.invoke()?.coerceAtLeast(0f) ?: 0f
        val maxV = vRange?.maxValue?.invoke()?.coerceAtLeast(0f) ?: 0f

        // Content extent = viewport + the remaining scroll range reported by Compose.
        val viewportWidth = sn.size.width / density
        val viewportHeight = sn.size.height / density
        val contentWidth =
            (viewportWidth + maxH / density).coerceAtMost(MAX_SCROLL_EXTENT_CSS_PX)
        val contentHeight =
            (viewportHeight + maxV / density).coerceAtMost(MAX_SCROLL_EXTENT_CSS_PX)

        val sizer = scrollSizers.getOrPut(nodeId) { createScrollSizer() }
        setSizeAndPosition(sizer, 0f, 0f, contentWidth, contentHeight)
        // (Re-)append: the sync of a node with children clears all its element's children,
        // and setting innerText (Text/EditableText semantics) drops them as well.
        htmlNode.appendChild(sizer)

        // The DOM scroll offset always grows in the element's "forward" direction, so a reversed
        // Compose scroll value is mirrored within its range. The offsets are clamped to the
        // (possibly capped) sizer extent — a value the browser can actually store — so a huge
        // Compose scroll offset is never misread back as an AT scroll delta.
        val scrollLeftCssPx = hRange?.let {
            val value = it.value().coerceIn(0f, maxH)
            ((if (it.reverseScrolling) maxH - value else value) / density)
                .coerceIn(0f, (contentWidth - viewportWidth).coerceAtLeast(0f))
        } ?: 0f
        val scrollTopCssPx = vRange?.let {
            val value = it.value().coerceIn(0f, maxV)
            ((if (it.reverseScrolling) maxV - value else value) / density)
                .coerceIn(0f, (contentHeight - viewportHeight).coerceAtLeast(0f))
        } ?: 0f
        pendingScrollOffsets[nodeId] = Offset(scrollLeftCssPx, scrollTopCssPx)

        if (scrollListenersAttached.add(nodeId)) {
            htmlNode.addEventListener("scroll", onScroll)
        }
    }

    /**
     * Applies [pendingScrollOffsets] to the scrollable elements and promotes them to
     * [expectedScrollOffsets]. Must run after the whole tree sync: `scrollTop`/`scrollLeft`
     * assignments are clamped by the browser to the current scrollable extent, so the sizers and
     * children must already be attached.
     *
     * The assignment fires a "scroll" event, which [onScroll] recognizes as our own (the offset
     * matches the expected one) and ignores.
     *
     * Race guard: "scroll" events are dispatched asynchronously (in the rendering steps), while
     * this sync may run from a timer task in between. If an AT changed the element's offset and
     * its "scroll" event hasn't been processed yet (the current offset diverges from the last
     * expected one), the pending offset is NOT applied: overwriting the offset would silently
     * swallow the AT scroll, and updating [expectedScrollOffsets] would corrupt the delta
     * [onScroll] is about to compute. The next sync (triggered by the very ScrollBy the event
     * will invoke) reconciles the DOM offset with the Compose state.
     */
    private fun applyScrollOffsets() {
        pendingScrollOffsets.forEach { id, offset ->
            val element = webNodes[id] ?: return@forEach

            val actual = Offset(element.scrollLeft.toFloat(), element.scrollTop.toFloat())
            val lastExpected = expectedScrollOffsets[id]
            if (lastExpected != null &&
                (abs(actual.x - lastExpected.x) >= SCROLL_EPSILON_CSS_PX ||
                    abs(actual.y - lastExpected.y) >= SCROLL_EPSILON_CSS_PX)
            ) {
                // Unprocessed AT scroll — leave both the element and expectedScrollOffsets as is.
                return@forEach
            }

            expectedScrollOffsets[id] = offset
            if (abs(actual.x - offset.x) >= SCROLL_EPSILON_CSS_PX) {
                element.scrollLeft = offset.x.toDouble()
            }
            if (abs(actual.y - offset.y) >= SCROLL_EPSILON_CSS_PX) {
                element.scrollTop = offset.y.toDouble()
            }
        }
        pendingScrollOffsets.clear()
    }


    internal fun stop() {
        if (!hasStarted || hasStopped) return

        webSemanticsRoot.removeEventListener("click", onClick)

        dfsDeque.clear()
        nodes.clear()
        nodeToParent.clear()
        webNodes.clear()
        elementToSemanticsNode.clear()
        allNodesIds.clear()
        expectedScrollOffsets.clear()
        pendingScrollOffsets.clear()
        scrollSizers.clear()
        scrollListenersAttached.clear()

        invalidationChannel.close()
        syncTriggerChannel.close()
        startJob.cancel()

        removeAllChildrenOf(webSemanticsRoot)
        hasStopped = true
    }
}

/**
 * A safety cap for the scroll sizer extent (browsers cap element sizes at ~33 million px;
 * Compose may report huge maxValues, e.g. for lazy lists with estimated sizes).
 */
private const val MAX_SCROLL_EXTENT_CSS_PX = 4_000_000f

/**
 * Tolerance for comparing DOM scroll offsets: browsers may store fractional offsets, and the
 * Float<->Double conversions add noise. Differences below this threshold are never treated as
 * an AT-initiated scroll.
 */
private const val SCROLL_EPSILON_CSS_PX = 0.5f

private fun createScrollSizer(): HTMLElement {
    val sizer = document.createElement("div") as HTMLElement
    // The sizer is a layout-only element: hide it from assistive technologies.
    sizer.setAttribute("aria-hidden", "true")
    sizer.style.position = "absolute"
    return sizer
}

private fun setScrollContainerStyle(
    element: HTMLElement, horizontal: Boolean, vertical: Boolean
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

private fun resetScrollContainerStyle(element: HTMLElement) {
    // language=javascript
    js(
        """
       element.style.overflowX = "";
       element.style.overflowY = "";
       element.style.scrollbarWidth = "";
    """
    )
}

private fun setSizeAndPosition(
    element: HTMLElement, left: Float, top: Float, width: Float, height: Float
) {
    // language=javascript
    js(
        """
       element.style.left = "" + left + "px";
       element.style.top = "" + top + "px";
       element.style.width = "" + width + "px";
       element.style.height = "" + height + "px";
    """
    )
}

internal object AriaRoleId {
    const val Unknown = -1

    // Mapped from [androidx.compose.ui.semantics.Role] values:
    const val Button = 0
    const val Checkbox = 1
    const val Switch = 2
    const val RadioButton = 3
    const val Tab = 4
    const val Image = 5
    const val DropdownList = 6
    const val ValuePicker = Unknown // TODO: Any web alternative?
    const val Carousel = Unknown // TODO: Any web alternative?

    // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
    // Other ARIA roles not specified explicitly by [androidx.compose.ui.semantics.Role]:
    const val Heading = 7
    const val TextBox = 8
    const val List = 9
    const val Grid = 10
    const val Dialog = 11
}

internal fun SemanticsConfiguration.getRoleId(): Int {
    // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
    // Unfortunately, Role value is private, so we map it here:
    fun Role.toIntId(): Int = when (this) {
        Role.Button -> AriaRoleId.Button
        Role.Checkbox -> AriaRoleId.Checkbox
        Role.Switch -> AriaRoleId.Switch
        Role.RadioButton -> AriaRoleId.RadioButton
        Role.Tab -> AriaRoleId.Tab
        Role.Image -> AriaRoleId.Image
        Role.DropdownList -> AriaRoleId.DropdownList
        Role.ValuePicker -> AriaRoleId.Unknown // TODO: Any web alternative?
        Role.Carousel -> AriaRoleId.Unknown // TODO: Any web alternative?
        else -> AriaRoleId.Unknown
    }

    var roleId = -1

    if (this.contains(SemanticsProperties.Role)) {
        roleId = this[SemanticsProperties.Role].toIntId()
    }

    if (this.contains(SemanticsActions.OnClick)) {
        // TODO: Not everything with OnClick is a button!!!
        roleId = Role.Button.toIntId()
    }

    if (this.contains(SemanticsProperties.Heading)) {
        roleId = AriaRoleId.Heading
    }

    if (this.contains(SemanticsProperties.EditableText)) {
        roleId = AriaRoleId.TextBox
    }

    if (this.contains(SemanticsProperties.CollectionInfo)) {
        val info = this.get(SemanticsProperties.CollectionInfo)
        roleId = if (info.columnCount > 1 && info.rowCount > 1) {
            AriaRoleId.Grid
        } else {
            AriaRoleId.List
        }
    }

    // Checked last: a layer's structural role outranks whatever its content looks like.
    if (this.contains(SemanticsProperties.IsDialog)) {
        roleId = AriaRoleId.Dialog
    }

    return roleId
}

// To avoid passing a Kotlin string to JS, we pass an int instead and map it to String on the JS side.
// See https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
internal fun setA11YAriaRole(element: HTMLElement, ariaRoleId: Int) {
    // language=javascript
    js(
        """
        var roleValue = "";
        switch (ariaRoleId) {
            case 0: // Role.Button
                roleValue = "button";
                break;
            case 1: // Role.Checkbox
                roleValue = "checkbox";
                break;
            case 2: // Role.Switch
                roleValue = "switch";
                break;
            case 3: // Role.RadioButton
                roleValue = "radio";
                break;
            case 4: // Role.Tab
                roleValue = "tab";
                break;
            case 5: // Role.Image
                roleValue = "img";
                break;
            case 6: // Role.DropdownList
                roleValue = "menu";
                break;
            case 7: // heading https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/heading_role
                roleValue = "heading";
                break;
            case 8: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/textbox_role
                roleValue = "textbox";
                break;
            case 9: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/list_role
                roleValue = "list";
                break;
            case 10: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/grid_role
                roleValue = "grid";
                break;
            case 11: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/dialog_role
                roleValue = "dialog";
                break;
            default:
                break;
        }
        if (roleValue.length > 0) { 
            element.setAttribute("role", roleValue);
        } else {
            element.removeAttribute("role");
        }
    """
    )
}

private fun removeAllChildrenOf(element: HTMLElement) {
    // language=javascript
    js("element.replaceChildren()")
}

/**
 * https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Global_attributes/inert
 *  When an element is inert, it along with all of the element's descendants,
 *  including normally interactive elements such as links, buttons, and form controls are disabled
 *  because they cannot receive focus or be clicked.
 */
private fun Element.setInert(inert: Boolean) {
    val element = this.unsafeCast<CanToggleAttribute>()
    element.toggleAttribute("inert", inert)
}

private external interface CanToggleAttribute : JsAny {
    // https://developer.mozilla.org/en-US/docs/Web/API/Element/toggleAttribute
    fun toggleAttribute(attributeName: String, force: Boolean): Boolean
}