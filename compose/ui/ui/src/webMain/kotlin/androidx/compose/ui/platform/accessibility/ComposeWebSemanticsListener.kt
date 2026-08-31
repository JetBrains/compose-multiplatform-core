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

import androidx.collection.MutableScatterMap
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastJoinToString
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

    // Two array deques for dfs traversal / tree sync:
    private val dfsSemanticsNodes = ArrayDeque<SemanticsNode>()
    private val dfsA11YParents = ArrayDeque<HTMLElement>()

    // Lookup maps between semantics nodes and corresponding A11Y DOM elements:
    private val idToA11YNode = MutableScatterMap<Int, HTMLElement>()
    private val a11yNodeToSemanticsNode = MutableScatterMap<HTMLElement, SemanticsNode>()

    // An intermediate tree representation which is applied to the actual DOM after every sync:
    private val targetParentToChildren = MutableScatterMap<HTMLElement, MutableList<HTMLElement>>()
    private val targetChildToParent = MutableScatterMap<HTMLElement, HTMLElement>()

    /**
     * Event delegation: Single shared click listener for all a11y nodes with SemanticsActions.OnClick.
     * It's expected to be triggered by A11Y tools and tests (element.click()), not by pointer input.
     */
    private val onClick: (Event) -> Unit = onClick@ { event ->
        val semanticsNode = (event.target as? HTMLElement)
            ?.let { a11yNodeToSemanticsNode[it] }
            ?: return@onClick

        val config = semanticsNode.config

        if (!config.contains(SemanticsProperties.Disabled) &&
            config.contains(SemanticsActions.OnClick)
        ) {
            config[SemanticsActions.OnClick].action?.invoke()
        }
    }


    /**
     * Updates the A11Y DOM to mirror all current semantics owners using only necessary structural
     * changes. The tree is updated incrementally instead of being rebuilt from scratch on every sync.
     * Elements whose semantics node and parent remain unchanged must not be detached, because
     * assistive technologies may lose their current accessibility node when it is reattached.
     * HTML elements are reused by semantics node ID and moved only when their parent or sibling
     * position changes.
     */
    private fun syncSemanticsWithWebA11Y() {
        targetParentToChildren.clear()
        targetChildToParent.clear()

        semanticsOwners.fastForEach {
            syncSemanticsWithWebA11Y(it)
        }

        // Move surviving nodes to their target parents before removing obsolete elements.
        // Otherwise, removing an obsolete parent would also temporarily detach surviving descendants.
        targetParentToChildren.forEach { parent, targetChildren ->
            placeA11YChildrenInOrder(parent, targetChildren)
        }

        val removedIds = mutableSetOf<Int>()

        idToA11YNode.forEach { id, htmlNode ->
            if (!targetChildToParent.containsKey(htmlNode)) {
                htmlNode.remove()
                removedIds.add(id)
            }
        }

        removedIds.forEach {
            val htmlNode = idToA11YNode.remove(it)
            if (htmlNode != null) {
                a11yNodeToSemanticsNode.remove(htmlNode)
            }
        }

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
     * Syncs the tree corresponding to [semanticsOwner] and records its target DOM structure.
     */
    private fun syncSemanticsWithWebA11Y(semanticsOwner: SemanticsOwner) {
        fun SemanticsNode.isValid() = layoutNode.let { it.isPlaced && it.isAttached }

        val root = semanticsOwner.rootSemanticsNode
        if (!root.isValid()) return

        dfsSemanticsNodes.clear()
        dfsA11YParents.clear()
        dfsSemanticsNodes.addLast(root)
        dfsA11YParents.addLast(webSemanticsRoot)

        val rootPosition = webSemanticsRoot.getBoundingClientRect().let {
            Offset(it.left.toFloat(), it.top.toFloat())
        }

        while (!dfsSemanticsNodes.isEmpty()) {
            val node = dfsSemanticsNodes.removeLast()
            val htmlParent = dfsA11YParents.removeLast()

            // `config` recreates the merged subtree on every call, so read it once
            val config = node.config
            val children = node.replacedChildren

            val htmlNode = if (config.contains(SemanticsProperties.Text)) {
                // Usually, the order of SemanticsNode children matches the mirroring a11y HTML.
                // But for text with links we have to interleave text parts with links.
                // We split text into parts: plain text fragments and links.
                // That's why a text node doesn't push its children to the traversal queue.
                // It handles its link children itself:
                syncTextNode(node, config, children, rootPosition)
            } else {
                syncNode(node, config, rootPosition)
                    .also { pushNodesForTraversal(children, it) }
            }
            check(htmlNode !== htmlParent) { "A11Y node ${node.id} cannot be its own parent" }
            targetParentToChildren.getOrPut(htmlParent) { mutableListOf() }.add(htmlNode)
            targetChildToParent[htmlNode] = htmlParent
        }
    }

    /**
     * @param children - the nodes to be added for traversal during the sync
     * @param htmlParent - the a11y (dom) parent element to contain the children a11y elements
     */
    private fun pushNodesForTraversal(children: List<SemanticsNode>, htmlParent: HTMLElement) {
        val reversedChildren = children.asReversed()
        dfsSemanticsNodes.addAll(reversedChildren)
        repeat(reversedChildren.size) {
            dfsA11YParents.addLast(htmlParent)
        }
    }

    /**
     * Creates (or reuses) the HTML node corresponding to [semanticsNode] and syncs its state.
     * Placement is handled separately after all expected parent-child relationships are known.
     */
    private fun syncNode(
        semanticsNode: SemanticsNode,
        config: SemanticsConfiguration,
        rootPosition: Offset,
        text: String? = null,
    ): HTMLElement {
        val currentId = semanticsNode.id

        var htmlNode = idToA11YNode[currentId]
        if (htmlNode != null) {
            syncNodeProperties(semanticsNode, config, htmlNode, rootPosition, text)
        } else {
            htmlNode = document.createElement("div") as HTMLElement
            htmlNode.style.apply {
                position = "fixed"
                whiteSpace = "pre"
            }

            idToA11YNode[currentId] = htmlNode
            syncNodeProperties(semanticsNode, config, htmlNode, rootPosition, text, justCreated = true)
        }

        a11yNodeToSemanticsNode[htmlNode] = semanticsNode
        return htmlNode
    }

    private fun placeA11YChildrenInOrder(
        parent: HTMLElement,
        children: List<HTMLElement>,
    ) {
        var current = parent.firstElementChild?.nextExpectedSibling(parent)

        children.fastForEach { child ->
            if (child.parentElement !== parent || child !== current) {
                parent.insertBefore(child, current)
            }
            current = child.nextElementSibling?.nextExpectedSibling(parent)
        }
    }

    /** Skips removed elements and elements that are moving to another parent. */
    private fun Element.nextExpectedSibling(parent: HTMLElement): HTMLElement? {
        var element: Element? = this
        while (element != null) {
            val htmlElement = element as HTMLElement
            if (targetChildToParent[htmlElement] === parent) {
                return htmlElement
            }
            element = element.nextElementSibling
        }
        return null
    }

    /**
     * Writes the state of [semanticsNode] onto [htmlNode]:
     * the text, the ARIA attributes and role, the size and the position.
     */
    private fun syncNodeProperties(
        semanticsNode: SemanticsNode,
        config: SemanticsConfiguration,
        htmlNode: HTMLElement,
        rootOffset: Offset,
        text: String?,
        justCreated: Boolean = false,
    ) {
        if (text != null && htmlNode.innerText != text) {
            htmlNode.innerText = text
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
            val editableText = config[SemanticsProperties.EditableText].text
            if (htmlNode.innerText != editableText) {
                htmlNode.innerText = editableText
            }

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

        val density = semanticsNode.layoutNode.density
        semanticsNode.boundsInRoot.let { rect ->
            val newPosition = rootOffset + rect.topLeft.div(density.density)
            val width = rect.width.div(density.density)
            val height = rect.height.div(density.density)

            setSizeAndPosition(htmlNode, newPosition.x, newPosition.y, width, height)
        }
    }


    /**
     * Syncs a node with [SemanticsProperties.Text], attaching its link children right away instead
     * of scheduling them for the regular traversal.
     *
     * A link doesn't have its own text in the semantics tree: the whole text (including the links)
     * belongs to the text node, while every link range is a separate child node marked with
     * [SemanticsProperties.LinkTestMarker]. Exposing it as is would read the link text twice,
     * so the text is split and interleaved with the link nodes:
     * ```
     * Semantics nodes:                     HTML nodes:
     *
     * Text("Read the docs, please")        <div>
     *  ├─ LinkTestMarker  // "Read"          <div role="link">Read</div>
     *  └─ LinkTestMarker  // "the docs"      " "
     *                                        <div role="link">the docs</div>
     *                                        ", please"
     *                                      </div>
     * ```
     * Note that the empty text parts (here: the one before the first link) are skipped.
     *
     * If the text parts don't surround the link children exactly (for example, a link clipped by
     * `maxLines` doesn't produce a child node), this node exposes the whole text and the links keep
     * their own text, so nothing is lost.
     *
     * [children] other than the links (a text node might be a merged node with arbitrary merging
     * children) are pushed to the regular traversal and end up after the links.
     */
    private fun syncTextNode(
        node: SemanticsNode,
        config: SemanticsConfiguration,
        children: List<SemanticsNode>,
        rootPosition: Offset,
    ): HTMLElement {
        val texts = config[SemanticsProperties.Text]
        val linksCount = children.count { it.config.contains(SemanticsProperties.LinkTestMarker) }

        val split = if (linksCount == 0) null else splitTextAndLinks(texts)
        // Null if the parts don't surround the link children exactly: the whole text is exposed then.
        val textParts = split?.textParts?.takeIf { split.matchesLinksCount(linksCount) }

        val text = texts.fastJoinToString("\n") { it.text }
        val htmlNode = syncNode(
            semanticsNode = node,
            config = config,
            rootPosition = rootPosition,
        )
        val targetTextAndLinks = mutableListOf<Any>()
        if (textParts == null) {
            targetTextAndLinks.add(text)
        }

        var linkIndex = 0
        // Non-link children are pushed together (after the loop) to keep their relative order.
        var deferredChildren: MutableList<SemanticsNode>? = null

        children.fastForEach { child ->
            val childConfig = child.config
            if (!childConfig.contains(SemanticsProperties.LinkTestMarker)) {
                val deferred = deferredChildren ?: mutableListOf<SemanticsNode>().also {
                    deferredChildren = it
                }
                deferred.add(child)
                return@fastForEach
            }

            if (textParts != null) {
                targetTextAndLinks.add(textParts[linkIndex])
            }

            val linkChildren = child.replacedChildren
            val linkHtmlNode = syncNode(
                semanticsNode = child,
                config = childConfig,
                rootPosition = rootPosition,
                text = split?.linkTexts?.getOrNull(linkIndex),
            )
            targetTextAndLinks.add(linkHtmlNode)
            targetParentToChildren.getOrPut(htmlNode) { mutableListOf() }.add(linkHtmlNode)
            targetChildToParent[linkHtmlNode] = htmlNode
            // A link node is expected to be a leaf, but don't rely on it.
            pushNodesForTraversal(linkChildren, linkHtmlNode)

            linkIndex++
        }

        if (textParts != null) {
            targetTextAndLinks.add(textParts.last())
        }

        updateTextAndLinks(htmlNode, targetTextAndLinks)
        deferredChildren?.let { pushNodesForTraversal(it, htmlNode) }
        return htmlNode
    }

    /** Updates the text fragments and semantic link elements at the start of a text node. */
    private fun updateTextAndLinks(parent: HTMLElement, targetContent: List<Any>) {
        var current = parent.firstChild

        targetContent.fastForEach { item ->
            when (item) {
                is String -> {
                    if (item.isEmpty()) return@fastForEach

                    if (current != null && current !is HTMLElement) {
                        if (current?.textContent != item) {
                            current?.textContent = item
                        }
                        current = current?.nextSibling
                    } else {
                        parent.insertBefore(document.createTextNode(item), current)
                    }
                }
                is HTMLElement -> {
                    if (item !== current) {
                        parent.insertBefore(item, current)
                    }
                    current = item.nextSibling
                }
                else -> error("Unsupported text-and-link content type: ${item::class}")
            }
        }

        // Regular semantics children follow the text/link prefix and are placed later.
        while (current != null) {
            val currentElement = current as? HTMLElement
            val isRegularSemanticsChild = currentElement
                ?.let { a11yNodeToSemanticsNode[it] }
                ?.config
                ?.contains(SemanticsProperties.LinkTestMarker) == false
            if (isRegularSemanticsChild) break

            val next = current?.nextSibling
            parent.removeChild(current!!)
            current = next
        }
    }

    internal fun stop() {
        if (!hasStarted || hasStopped) return

        webSemanticsRoot.removeEventListener("click", onClick)

        dfsSemanticsNodes.clear()
        dfsA11YParents.clear()
        idToA11YNode.clear()
        a11yNodeToSemanticsNode.clear()
        targetParentToChildren.clear()
        targetChildToParent.clear()

        invalidationChannel.close()
        syncTriggerChannel.close()
        startJob.cancel()

        removeAllChildrenOf(webSemanticsRoot)
        hasStopped = true
    }
}
