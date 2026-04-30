@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package androidx.compose.ui.desktop.macos

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import org.jetbrains.desktop.macos.DragInfo
import org.jetbrains.desktop.macos.DragOperation

internal class MacOsDragAndDropManager(
    private val rootDragAndDropNode: () -> ComposeSceneDragAndDropNode,
    private val density: () -> Density,
) {
    private var previousAction: DragAndDropTransferAction? = null

    fun onDragEntered(info: DragInfo): DragOperation {
        val dndEvent = info.toDragAndDropEvent()
        val node = rootDragAndDropNode()
        val acceptedTransfer = node.acceptDragAndDropTransfer(dndEvent)
        if (acceptedTransfer) {
            previousAction = dndEvent.action
            node.onStarted(dndEvent)
            node.onEntered(dndEvent)
        }

        return if (acceptedTransfer && dndEvent.action != null) {
            dndEvent.action.toDragOperation()
        } else {
            DragOperation.NONE
        }
    }

    fun onDragUpdated(info: DragInfo): DragOperation {
        val dndEvent = info.toDragAndDropEvent()
        val node = rootDragAndDropNode()

        if (dndEvent.action != previousAction) {
            node.onChanged(dndEvent)
            previousAction = dndEvent.action
        }
        node.onMoved(dndEvent)

        return if (node.hasEligibleDropTarget && dndEvent.action != null) {
            dndEvent.action.toDragOperation()
        } else {
            DragOperation.NONE
        }
    }

    fun onDragExited() {
        val dndEvent = DragAndDropEvent(
            action = null, nativeEvent = null, positionInRootImpl = Offset.Zero,
        )
        val node = rootDragAndDropNode()

        node.onExited(dndEvent)
        node.onEnded(dndEvent)
        previousAction = null
    }

    fun onDragPerformed(info: DragInfo): Boolean {
        val dndEvent = info.toDragAndDropEvent()
        val node = rootDragAndDropNode()

        val consumed = node.onDrop(dndEvent)
        node.onEnded(dndEvent)
        previousAction = null
        return consumed
    }

    private fun DragInfo.toDragAndDropEvent(): DragAndDropEvent {
        val selectedAction = when {
            DragOperation.MOVE in allowedOperations -> DragAndDropTransferAction.Move
            DragOperation.COPY in allowedOperations -> DragAndDropTransferAction.Copy
            DragOperation.LINK in allowedOperations -> DragAndDropTransferAction.Link
            else -> null
        }

        return DragAndDropEvent(
            action = selectedAction,
            nativeEvent = this,
            positionInRootImpl = locationInWindow.toDpOffset().toOffset(),
        )
    }

    private fun DpOffset.toOffset() = with(density()) {
        Offset(x.toPx(), y.toPx())
    }
}
