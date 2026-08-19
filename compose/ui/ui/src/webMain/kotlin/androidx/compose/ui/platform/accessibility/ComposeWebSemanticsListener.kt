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


    private fun syncSemanticsWithWebA11Y() {
        allNodesIds.clear()

        semanticsOwners.fastForEach {
            syncSemanticsWithWebA11Y(it)
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
     * Sync the tree corresponding to the [semanticsOwner] and populate [allNodesIds] - a set of node ids.
     */
    private fun syncSemanticsWithWebA11Y(semanticsOwner: SemanticsOwner) {
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

            // `config` recreates the merged subtree on every call, so read it once
            val config = node.config
            val children = node.replacedChildren

            // The parent is known here: it was recorded when this node was pushed to the deque.
            val htmlParent = nodeToParent[node.id]?.let { webNodes[it] } ?: webSemanticsRoot

            if (config.contains(SemanticsProperties.Text)) {
                // Usually, the order of SemanticsNode children matches the mirroring a11y HTML.
                // But for text with links we have to interleave text parts with links.
                // We split text into parts: plain text fragments and links.
                // That's why a text node doesn't push its children to the traversal queue.
                // It handles its link children itself:
                syncTextNode(node, config, children, htmlParent, rootPosition)
            } else {
                syncNode(node, config, htmlParent, rootPosition, children.isNotEmpty())
                pushChildren(children, node.id)
            }
        }
    }

    /**
     * Pushes the [children] of the node [parentId] to the traversal deque, keeping the order of the
     * semantics tree: the deque is LIFO, so the children are added in the reversed order.
     */
    private fun pushChildren(children: List<SemanticsNode>, parentId: Int) {
        val reversedChildren = children.asReversed()
        dfsDeque.addAll(reversedChildren)
        reversedChildren.fastForEach { nodeToParent[it.id] = parentId }
    }

    /**
     * Creates (or reuses) the HTML node corresponding to [semanticsNode], syncs its state and
     * attaches it to [htmlParent].
     *
     * @param hasChildren whether the node has semantics children
     */
    private fun syncNode(
        semanticsNode: SemanticsNode,
        config: SemanticsConfiguration,
        htmlParent: HTMLElement,
        rootPosition: Offset,
        hasChildren: Boolean,
        text: String? = null,
    ): HTMLElement {
        val currentId = semanticsNode.id
        allNodesIds.add(currentId)

        val htmlNode = if (nodes[currentId] != null) {
            nodes[currentId] = semanticsNode
            val htmlNode = webNodes[currentId] ?: error("Node $currentId not found")

            if (hasChildren) {
                // To ensure the correct order of nested nodes, we remove all of them.
                // I assume it's more efficient to remove and re-add them than to insert the nodes at specific positions.
                // Also, the code is more simple with this approach.
                // They are added back when they are synced.
                removeAllChildrenOf(htmlNode)
            }

            syncNodeProperties(semanticsNode, config, htmlNode, rootPosition, text)
            htmlNode
        } else {
            nodes[currentId] = semanticsNode
            val htmlNode = document.createElement("div") as HTMLElement
            htmlNode.style.apply {
                position = "fixed"
                whiteSpace = "pre"
            }

            webNodes[currentId] = htmlNode
            syncNodeProperties(semanticsNode, config, htmlNode, rootPosition, text, justCreated = true)
            htmlNode
        }

        htmlParent.appendChild(htmlNode)
        elementToSemanticsNode[htmlNode] = semanticsNode
        return htmlNode
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
        if (text != null) {
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
            htmlNode.innerText = config[SemanticsProperties.EditableText].text

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
        htmlParent: HTMLElement,
        rootPosition: Offset,
    ) {
        val texts = config[SemanticsProperties.Text]
        val linksCount = children.count { it.config.contains(SemanticsProperties.LinkTestMarker) }

        val split = if (linksCount == 0) null else splitTextAndLinks(texts)
        // Null if the parts don't surround the link children exactly: the whole text is exposed then.
        val textParts = split?.textParts?.takeIf { split.matchesLinksCount(linksCount) }

        val htmlNode = syncNode(
            semanticsNode = node,
            config = config,
            htmlParent = htmlParent,
            rootPosition = rootPosition,
            hasChildren = children.isNotEmpty(),
            // The text parts are appended in between the link children below.
            text = if (textParts != null) "" else texts.fastJoinToString("\n") { it.text },
        )

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
                htmlNode.appendText(textParts[linkIndex])
            }

            val linkChildren = child.replacedChildren
            syncNode(
                semanticsNode = child,
                config = childConfig,
                htmlParent = htmlNode,
                rootPosition = rootPosition,
                hasChildren = linkChildren.isNotEmpty(),
                text = split?.linkTexts?.getOrNull(linkIndex),
            )
            // A link node is expected to be a leaf, but don't rely on it.
            pushChildren(linkChildren, child.id)

            linkIndex++
        }

        if (textParts != null) {
            htmlNode.appendText(textParts.last())
        }

        deferredChildren?.let { pushChildren(it, node.id) }
    }

    private fun HTMLElement.appendText(text: String?) {
        if (text.isNullOrEmpty()) return
        appendChild(document.createTextNode(text))
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

        invalidationChannel.close()
        syncTriggerChannel.close()
        startJob.cancel()

        removeAllChildrenOf(webSemanticsRoot)
        hasStopped = true
    }
}
