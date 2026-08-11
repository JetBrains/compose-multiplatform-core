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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastJoinToString
import kotlin.js.js
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
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
        const val DATA_SEMANTICS_ID_KEY = "data-semantics-id"
    }


    /**
     * @param coroutineScope The [CoroutineScope] used to run this listener,
     * typically the composition scope so the listener follows the composition lifecycle.
     */
    internal fun start(coroutineScope: CoroutineScope) {
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
        coroutineScope.launch {
            var timeSpentDebouncing = 0L
            var lastDebouncedTime = 0L
            var lastSyncTime = currentTimeMillis()

            launch {
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
            launch {
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

    // A reusable set of node ids for sync purposes
    private val allNodesIds = mutableSetOf<Int>()

    /**
     * Event delegation: Single shared click listener for all a11y nodes
     */
    private val onClick: (Event) -> Unit = onClick@ { event ->
        val element = (event.target as? Element)
            ?.closest("[$DATA_SEMANTICS_ID_KEY]")
            ?: return@onClick
        val semanticsId = element.getAttribute(DATA_SEMANTICS_ID_KEY)?.toIntOrNull() ?: return@onClick
        val config = nodes[semanticsId]?.config ?: return@onClick

        if (config.contains(SemanticsProperties.Disabled)) {
            return@onClick
        }

        if (config.contains(SemanticsActions.OnClick)) {
            config[SemanticsActions.OnClick].action?.invoke()
        }
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
            webNodes.remove(it)
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
    private fun syncSemanticsWithWebA11Y(
        semanticsOwner: SemanticsOwner,
        allNodesIds: MutableSet<Int>
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
        }
    }

    private fun syncNode(
        sn: SemanticsNode,
        config: SemanticsConfiguration,
        htmlNode: HTMLElement,
        rootOffset: Offset,
        justCreated: Boolean = false,
    ) {
        if (justCreated) {
            // Mark the element with the semantics node ID for click event delegation lookup.
            // https://developer.mozilla.org/en-US/docs/Web/HTML/How_to/Use_data_attributes
            htmlNode.setAttribute(DATA_SEMANTICS_ID_KEY, sn.id.toString())
        }

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

        val density = sn.layoutNode.density
        sn.boundsInRoot.let { rect ->
            val newPosition = rootOffset + rect.topLeft.div(density.density)
            val width = rect.width.div(density.density)
            val height = rect.height.div(density.density)

            setSizeAndPosition(htmlNode, newPosition.x, newPosition.y, width, height)
        }
    }
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
    if (inert == hasAttribute("inert")) return

    if (inert) {
        setAttribute("inert", "")
    } else {
        removeAttribute("inert")
    }
}
