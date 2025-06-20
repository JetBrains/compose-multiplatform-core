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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor
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
    val coroutineScope: CoroutineScope,
    val webSemanticsRoot: HTMLElement,
) : PlatformContext.SemanticsOwnerListener {

    private val invalidationChannel =
        Channel<Unit>(1, onBufferOverflow = BufferOverflow.DROP_LATEST)
    private val syncTriggerChannel = Channel<Unit>(1, onBufferOverflow = BufferOverflow.DROP_LATEST)

    init {
        coroutineScope.launch {
            var lastSyncTimeMs = currentTimeMillis()

            launch {
                invalidationChannel.receiveAsFlow().collect {
                    val currentTime = currentTimeMillis()

                    if (currentTime - lastSyncTimeMs >= 1000) {
                        // we've been debouncing for too long, but must sync periodically
                        lastSyncTimeMs = currentTime
                        syncSemanticsWithWebA11Y()
                    } else {
                        syncTriggerChannel.trySend(Unit)
                    }
                }
            }

            launch {
                @OptIn(FlowPreview::class)
                // debounce until the Semantics changes settled for at least 100ms
                syncTriggerChannel.receiveAsFlow().debounce(100.milliseconds).collect {
                    lastSyncTimeMs = currentTimeMillis()
                    syncSemanticsWithWebA11Y()
                }
            }
        }
    }

    private var semanticsOwner: SemanticsOwner? = null

    override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
        this.semanticsOwner = semanticsOwner
    }

    override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
        if (semanticsOwner == this.semanticsOwner) {
            this.semanticsOwner = null
        }
    }

    override fun onSemanticsChange(semanticsOwner: SemanticsOwner) {
        invalidationChannel.trySend(Unit)
    }

    override fun onLayoutChange(
        semanticsOwner: SemanticsOwner,
        semanticsNodeId: Int
    ) {
        invalidationChannel.trySend(Unit)
    }

    private val dfsDeque = ArrayDeque<SemanticsNode>()

    private val nodes = MutableScatterMap<Int, SemanticsNode>()
    private val nodeToParent = MutableScatterMap<Int, Int>()
    private val webNodes = MutableScatterMap<Int, HTMLElement>()


    private fun syncSemanticsWithWebA11Y() {
        fun SemanticsNode.isValid() = layoutNode.let { it.isPlaced && it.isAttached }

        val root = semanticsOwner?.rootSemanticsNode ?: return

        if (root.isValid()) {
            dfsDeque.addLast(root)
        }


        val allIds = mutableSetOf<Int>()

        val rootPosition = webSemanticsRoot.getBoundingClientRect().let {
            Offset(it.left.toFloat(), it.top.toFloat())
        }

        while (!dfsDeque.isEmpty()) {
            val node = dfsDeque.removeLast()
            val currentId = node.id
            allIds.add(currentId)

            val children = node.replacedChildren.asReversed()
            dfsDeque.addAll(children)
            children.forEach { it -> nodeToParent[it.id] = currentId }

            val htmlNode = if (nodes[currentId] != null) {
                nodes[currentId] = node
                val htmlNode = webNodes[currentId]!!

                if (children.isNotEmpty()) {
                    // To ensure the correct order of nested nodes, we remove all of them.
                    // I assume it's more efficient to remove and re-add them than to insert the nodes at specific positions.
                    // Also, the code is more simple with this approach.
                    // They are added below.
                    removeAllChildrenOf(htmlNode)
                }

                syncNode(node, htmlNode, rootPosition)
                htmlNode
            } else {
                nodes[currentId] = node
                val htmlNode = document.createElement("div") as HTMLElement
                htmlNode.style.setProperty("position", "fixed")

                webNodes[currentId] = htmlNode
                syncNode(node, htmlNode, rootPosition, true)
                htmlNode
            }

            // find the parent node and attach to it
            val parentId = nodeToParent[currentId]
            val htmlParent = parentId?.let { webNodes[it] } ?: webSemanticsRoot
            htmlParent.appendChild(htmlNode)
        }

        val removedIds = mutableSetOf<Int>()

        webNodes.forEachKey {
            if (it !in allIds) {
                webNodes[it]?.remove()
                removedIds.add(it)
            }
        }

        removedIds.forEach { webNodes.remove(it) }
    }

    private fun syncNode(
        sn: SemanticsNode,
        htmlNode: HTMLElement,
        rootOffset: Offset,
        justCreated: Boolean = false,
    ) {
        val config = sn.config

        if (config.contains(SemanticsProperties.Text)) {
            val text = config[SemanticsProperties.Text]
            htmlNode.innerText = text.joinToString("\n") { it.text }
        }

        if (config.contains(SemanticsProperties.ContentDescription)) {
            val contentDescription = config[SemanticsProperties.ContentDescription]
            htmlNode.setAttribute("aria-label", contentDescription.joinToString(", "))
        }

        val role = config.getOrNull(SemanticsProperties.Role)
        if (role != null) {
            setA11YRole(htmlNode, roleId = role.toIntId())
        }

        if (config.contains(SemanticsActions.OnClick) && justCreated) {
            val listener = config[SemanticsActions.OnClick].action!!

            htmlNode.addEventListener("click", {
                listener.invoke()
            })

            // Setting role=Button, so the A11Y tool would hint that this element can be clicked
            setA11YRole(htmlNode, roleId = Role.Button.toIntId())
        }

        sn.layoutInfo.let {
            val newPosition = rootOffset + it.coordinates.positionInRoot().div(it.density.density)
            val rootCoordinates = it.coordinates.findRootCoordinates()

            val clippedRect = rootCoordinates
                .localBoundingBoxOf(it.coordinates, clipBounds = true)
                .round(it.density)

            setSizeAndPosition(
                htmlNode,
                newPosition.x,
                newPosition.y,
                clippedRect.width,
                clippedRect.height
            )
        }
    }

    private fun Rect.round(density: Density): IntRect {
        val left = floor(left / density.density).toInt()
        val top = floor(top / density.density).toInt()
        val right = ceil(right / density.density).toInt()
        val bottom = ceil(bottom / density.density).toInt()

        return IntRect(left, top, right, bottom)
    }
}

private fun setSizeAndPosition(
    element: HTMLElement,
    left: Float,
    top: Float,
    width: Int,
    height: Int
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

// To avoid passing a Kotlin string to JS, we pass an int instead and map it to String on the JS side.
// See https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
private fun setA11YRole(element: HTMLElement, roleId: Int) {
    // language=javascript
    js(
        """
        var roleValue = "";
        switch (roleId) {
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
            default:
                break;
        }
        element.setAttribute("role", roleValue);
    """
    )
}

private fun removeAllChildrenOf(element: HTMLElement) {
    // language=javascript
    js("element.replaceChildren()")
}

// https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
// Unfortunately, Role value is private, so we map it here:
private fun Role.toIntId(): Int = when (this) {
    Role.Button -> 0
    Role.Checkbox -> 1
    Role.Switch -> 2
    Role.RadioButton -> 3
    Role.Tab -> 4
    Role.Image -> 5
    Role.DropdownList -> 6
    Role.ValuePicker -> -1 // TODO: Any web alternative?
    Role.Carousel -> -1 // TODO: Any web alternative?
    else -> -1
}