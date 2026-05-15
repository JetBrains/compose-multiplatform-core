/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.node

import androidx.collection.ArraySet
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropManager
import androidx.compose.ui.draganddrop.DragAndDropNode
import androidx.compose.ui.draganddrop.DragAndDropStartTransferScope
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformDragAndDropManager
import androidx.compose.ui.platform.PlatformDragAndDropSource

/**
 * The actual [DragAndDropManager] implementation tied to a specific
 * [androidx.compose.ui.node.RootNodeOwner].
 */
internal class DragAndDropOwner(
    private val platformDragAndDropManager: PlatformDragAndDropManager
) : DragAndDropManager {
    val rootNode = DragAndDropNode()

    /**
     * A collection [DragAndDropTarget] instances that registered interested in a drag and drop
     * session by returning true in [DragAndDropTarget.onStarted].
     */
    private val interestedTargets = ArraySet<DragAndDropTarget>()
    private var lastDragEvent: DragAndDropEvent? = null
    override val modifier: Modifier = RootDragAndDropElement(rootNode)

    override val isRequestDragAndDropTransferRequired: Boolean
        get() = platformDragAndDropManager.isRequestDragAndDropTransferRequired

    override fun requestDragAndDropTransfer(node: DragAndDropNode, offset: Offset) {
        platformDragAndDropManager.requestDragAndDropTransfer(node.asPlatformDragAndDropSource(), offset)
    }

    override fun registerTargetInterest(target: DragAndDropTarget) {
        interestedTargets.add(target)
    }

    override fun isInterestedTarget(target: DragAndDropTarget): Boolean {
        return interestedTargets.contains(target)
    }

    override fun onNodeAttached(node: DragAndDropNode) {
        // If there's an ongoing drag session, redispatch the last event
        // to allow the newly attached node to participate
        val event = lastDragEvent
        if (event != null) {
            val accepted = node.acceptDragAndDropTransfer(event)
            if (accepted) {
                node.onStarted(event)
                // Redispatch onMoved from root so that nodes can track their potentially new children
                this.rootNode.onMoved(event)
            }
        }
    }

    override fun onNodeDetached(node: DragAndDropNode) {
        // If there's an ongoing drag session, remove the node from interested targets
        // and redispatch the last event to update the tree
        val event = lastDragEvent
        if (event != null) {
            interestedTargets.remove(node)
            // Redispatch onMoved from root so that nodes can update their children
            this.rootNode.onMoved(event)
        }
    }

    fun onDrop(event: DragAndDropEvent): Boolean = rootNode.onDrop(event)

    fun onStarted(event: DragAndDropEvent) {
        interestedTargets.forEach { it.onStarted(event) }
    }

    fun onEntered(event: DragAndDropEvent) = rootNode.onEntered(event)

    fun onMoved(event: DragAndDropEvent) {
        lastDragEvent = event
        rootNode.onMoved(event)
    }

    fun onExited(event: DragAndDropEvent) = rootNode.onExited(event)

    fun onChanged(event: DragAndDropEvent) = rootNode.onChanged(event)

    fun onEnded(event: DragAndDropEvent) {
        lastDragEvent = null
        rootNode.onEnded(event)
        interestedTargets.clear()
    }
}

private fun DragAndDropNode.asPlatformDragAndDropSource(): PlatformDragAndDropSource =
    object : PlatformDragAndDropSource {
        override fun PlatformDragAndDropSource.StartTransferScope.startDragAndDropTransfer(
            offset: Offset,
            isTransferStarted: () -> Boolean
        ) {
            asDragAndDropStartTransferScope().startDragAndDropTransfer(offset, isTransferStarted)
        }
    }

private fun PlatformDragAndDropSource.StartTransferScope.asDragAndDropStartTransferScope(): DragAndDropStartTransferScope =
    object : DragAndDropStartTransferScope {
        override fun startDragAndDropTransfer(
            transferData: DragAndDropTransferData,
            decorationSize: Size,
            drawDragDecoration: DrawScope.() -> Unit
        ): Boolean =
            this@asDragAndDropStartTransferScope.startDragAndDropTransfer(
                transferData = transferData,
                decorationSize = decorationSize,
                drawDragDecoration = drawDragDecoration
            )
    }

private class RootDragAndDropElement(
    private val dragAndDropNode: DragAndDropNode
) : ModifierNodeElement<DragAndDropNode>() {
    override fun create() = dragAndDropNode
    override fun update(node: DragAndDropNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "RootDragAndDropNode"
    }

    override fun equals(other: Any?) = other === this
    override fun hashCode(): Int = dragAndDropNode.hashCode()
}

