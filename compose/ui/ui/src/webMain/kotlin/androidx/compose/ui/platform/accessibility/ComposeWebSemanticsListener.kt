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
import androidx.compose.ui.util.fastForEachIndexed
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
import org.w3c.dom.HTMLElement

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


    /**
     * @param coroutineScope The [CoroutineScope] used to run this listener,
     * typically the composition scope so the listener follows the composition lifecycle.
     */
    internal fun start(coroutineScope: CoroutineScope) {
        // The scene appends its main owner from its own constructor, long before this point, so an
        // invalidation may already be buffered. Drop it to keep the "start after the initial
        // composition" contract that the caller relies on.
        invalidationChannel.tryReceive()

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
    }

    /**
     * All tracked [SemanticsOwner]s, in z-order: the scene's main owner first, then one owner per
     * [androidx.compose.ui.scene.ComposeSceneLayer] (Popup/Dialog) in the order they appeared.
     * [PlatformContext.SemanticsOwnerListener.onSemanticsOwnerAppended] guarantees that a new owner
     * is always created above the existing ones, so append order is stacking order.
     */
    private val semanticsOwners = mutableListOf<SemanticsOwner>()

    /**
     * Owners that have been observed to be modal, i.e. that contain a node marked with
     * `Modifier.semantics { dialog() }`.
     *
     * Modality is remembered for the owner's whole lifetime rather than recomputed from the current
     * semantics on every sync, because a Dialog drops that marker while it animates away: with
     * [androidx.compose.ui.window.DialogProperties.animateTransition] enabled (the default),
     * `hideDialogWithAnimation` replaces the layer content with a bare `Layout`. Recomputing would
     * un-inert the content below for the duration of the fade-out, while the scrim is still up.
     */
    private val modalOwners = mutableSetOf<SemanticsOwner>()

    override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
        if (semanticsOwners.contains(semanticsOwner)) return
        semanticsOwners.add(semanticsOwner)
        invalidationChannel.trySend(Unit)
    }

    override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
        modalOwners.remove(semanticsOwner)
        // A sync is required here, otherwise the removed owner's HTML nodes would leak into the
        // mirror DOM forever - a screen reader would keep reading a dismissed popup.
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


    private fun syncSemanticsWithWebA11Y() {
        fun SemanticsNode.isValid() = layoutNode.let { it.isPlaced && it.isAttached }

        val allIds = mutableSetOf<Int>()

        val rootPosition = webSemanticsRoot.getBoundingClientRect().let {
            Offset(it.left.toFloat(), it.top.toFloat())
        }

        // The root HTML element of every synced owner, in z-order, and the index of the topmost
        // modal one. Everything below that index becomes inert once the walk is done.
        val ownerRootElements = ArrayList<HTMLElement>(semanticsOwners.size)
        var topmostModalIndex = -1

        // Snapshot: the callbacks above mutate `semanticsOwners`, and syncNode invokes app code.
        semanticsOwners.toList().fastForEach { owner ->
            val root = owner.rootSemanticsNode
            if (!root.isValid()) return@fastForEach

            // A layer containing `Modifier.semantics { dialog() }` is modal: it takes over the a11y
            // tree and the layers below it become inert.
            // A layer marked `popup()` is treated as non-modal. That is correct for the default
            // `PopupProperties(focusable = false)` but under-approximates a focusable Popup, whose
            // modality is not observable from this source set - `ComposeSceneLayer.focusable` is
            // not carried by the semantics tree.
            var isModal = owner in modalOwners

            // Defensive: a previous walk that threw would leave foreign nodes behind, which would
            // then be attributed to this owner.
            dfsDeque.clear()
            dfsDeque.addLast(root)

            while (!dfsDeque.isEmpty()) {
                val node = dfsDeque.removeLast()
                val currentId = node.id
                allIds.add(currentId)

                // `config` recreates the merged subtree on every call, so read it exactly once per
                // node and pass it down to syncNode.
                val config = node.config
                if (config.contains(SemanticsProperties.IsDialog)) {
                    isModal = true
                }

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

                // find the parent node and attach to it.
                // An owner's root node has no parent, so it is appended directly to
                // [webSemanticsRoot]. Because owners are walked in z-order and `appendChild` moves
                // an existing element to the end, DOM order ends up matching z-order - which is
                // also the reading order for a screen reader's virtual cursor.
                val parentId = nodeToParent[currentId]
                val htmlParent = parentId?.let { webNodes[it] } ?: webSemanticsRoot
                htmlParent.appendChild(htmlNode)
            }

            if (isModal) {
                modalOwners.add(owner)
            }

            webNodes[root.id]?.let { rootElement ->
                if (isModal) {
                    topmostModalIndex = ownerRootElements.size
                }
                ownerRootElements.add(rootElement)
            }
        }

        val removedIds = mutableSetOf<Int>()

        webNodes.forEachKey {
            if (it !in allIds) {
                webNodes[it]?.remove()
                removedIds.add(it)
            }
        }

        removedIds.forEach {
            webNodes.remove(it)
            nodes.remove(it)
            nodeToParent.remove(it)
        }

        // Marks every layer below the topmost modal one as `inert`, so that assistive technologies
        // stop exposing content covered by a modal Dialog, while non-modal Popups (tooltips,
        // dropdowns that don't take focus) leave the content underneath readable.
        //
        // `inert` is preferred over detaching the subtree: the elements stay cached in [webNodes],
        // so opening and closing a dialog doesn't churn the mirror DOM, and nesting composes
        // naturally. Applied after the removal pass so it only ever touches surviving elements.
        ownerRootElements.fastForEachIndexed { index, element ->
            element.setInert(index < topmostModalIndex)
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

        if (config.contains(SemanticsActions.OnClick) && justCreated) {
            val listener = config[SemanticsActions.OnClick].action!!

            // TODO: need to remove the click listener when the new config version doesn't have OnClick action
            htmlNode.addEventListener("click", {
                listener.invoke()
            })
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

        setA11YAriaRole(element = htmlNode, config.getRoleId())

        // Marking the layers below as `inert` is what actually enforces modality; `aria-modal` is
        // only advisory, but it is what makes a screen reader announce the dialog and scope itself
        // to it.
        if (config.contains(SemanticsProperties.IsDialog)) {
            htmlNode.setAttribute("aria-modal", "true")
        } else {
            // HTML elements are cached and reused across syncs, so the negative case must be
            // handled too - otherwise a node that stops being a dialog keeps the attribute.
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
 * Adds or removes the `inert` attribute, which removes the whole subtree from the accessibility
 * tree. The write is skipped when the state already matches, because `setAttribute` queues a
 * mutation record even when the value is unchanged, and this runs on every a11y sync.
 */
private fun HTMLElement.setInert(inert: Boolean) {
    if (inert == hasAttribute("inert")) return

    if (inert) {
        setAttribute("inert", "")
    } else {
        removeAttribute("inert")
    }
}
