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
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.isContainer
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.ComposeWindow
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

internal class ComposeWebSemanticsListener(
    val platformContext: PlatformContext,
    val composeWindow: ComposeWindow,
    val coroutineScope: CoroutineScope,
    val webSemanticsRoot: HTMLElement,
) : PlatformContext.SemanticsOwnerListener {


    init {
        coroutineScope.launch {
            var lastSyncTimeMs = currentTimeMillis()

            var debounceJob: Job? = null

            @OptIn(FlowPreview::class)
            invalidationChannel.receiveAsFlow().collect {
                debounceJob?.cancel()
                val currentTime = currentTimeMillis()

                if (currentTime - lastSyncTimeMs >= 1000) {
                    // we've been debouncing for too long, but must sync periodically
                    lastSyncTimeMs = currentTime
                    syncSemanticsWithWebA11Y()
                } else {
                    // debounce until the Semantics changes settled for at least 100ms
                    debounceJob = launch {
                        delay(100)
                        lastSyncTimeMs = currentTimeMillis()
                        syncSemanticsWithWebA11Y()
                    }
                }
            }
        }
    }

    private val invalidationChannel = Channel<Unit>(1, onBufferOverflow = BufferOverflow.DROP_LATEST)

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



            if (nodes[currentId] != null) {
                nodes[currentId] = node
                syncNode(node, webNodes[currentId]!!, rootPosition)
            } else {
                nodes[currentId] = node
                val htmlNode = document.createElement("div") as HTMLElement
                htmlNode.apply {
                    style.setProperty("position", "fixed")
                }

                webNodes[currentId] = htmlNode

                val parentId = nodeToParent[currentId]
                val htmlParent = parentId?.let { webNodes[it] } ?: webSemanticsRoot
                htmlParent.appendChild(htmlNode)
                syncNode(node, htmlNode, rootPosition, true)
            }
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

        println("Config = $config")

        if (config.contains(SemanticsProperties.Text)) {
            val text = config[SemanticsProperties.Text]
            htmlNode.innerText = text.joinToString("\n") { it.text }
        }

        if (config.contains(SemanticsProperties.ContentDescription)) {
            val contentDescription = config[SemanticsProperties.ContentDescription]
            htmlNode.setAttribute("aria-label", contentDescription.joinToString(", "))
        }

        val role = config.getOrNull(SemanticsProperties.Role)
        if (role == Role.Button) {
            htmlNode.setAttribute("role", "button")
        }

        if (config.contains(SemanticsActions.OnClick) && justCreated) {
            val listener = config[SemanticsActions.OnClick].action!!

            htmlNode.addEventListener("click", {
                listener.invoke()
            })

            htmlNode.setAttribute("role", "button")
        }

        sn.layoutInfo.let {
            val newPosition = rootOffset + it.coordinates.positionInRoot().div(it.density.density)
            val rootCoordinates = it.coordinates.findRootCoordinates()

            val clippedRect = rootCoordinates
                .localBoundingBoxOf(it.coordinates, clipBounds = true)
                .round(it.density)


            setSizeAndPosition(htmlNode, newPosition.x, newPosition.y, clippedRect.width, clippedRect.height)
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

private fun setSizeAndPosition(element: HTMLElement, left: Float, top: Float, width: Int, height: Int) {
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