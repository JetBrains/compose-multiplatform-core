@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package androidx.compose.ui.kdt.macos

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import noria.CallbackInterceptor
import org.jetbrains.desktop.macos.DragInfo
import org.jetbrains.desktop.macos.DragOperation

internal class MacOsDragAndDropManager(
    private val rootDragAndDropNode: ComposeSceneDragAndDropNode,
    private var density: Density,
    private val callbackInterceptor: CallbackInterceptor,
) {
    private var previousAction: DragAndDropTransferAction? = null

    fun onDragEntered(info: DragInfo): DragOperation {
        val dndEvent = info.toDragAndDropEvent()

        val acceptedTransfer = callbackInterceptor.execute {
            rootDragAndDropNode.acceptDragAndDropTransfer(dndEvent)
        }
        if (acceptedTransfer) {
            callbackInterceptor.execute {
                previousAction = dndEvent.action
                rootDragAndDropNode.onStarted(dndEvent)
                rootDragAndDropNode.onEntered(dndEvent)
            }
        }

        return if (acceptedTransfer && dndEvent.action != null) {
            dndEvent.action.toDragOperation()
        } else {
            DragOperation.NONE
        }
    }

    fun onDragUpdated(info: DragInfo): DragOperation {
        val dndEvent = info.toDragAndDropEvent()

        callbackInterceptor.execute {
            if (dndEvent.action != previousAction) {
                rootDragAndDropNode.onChanged(dndEvent)
                previousAction = dndEvent.action
            }
            rootDragAndDropNode.onMoved(dndEvent)
        }

        return if (rootDragAndDropNode.hasEligibleDropTarget && dndEvent.action != null) {
            dndEvent.action.toDragOperation()
        } else {
            DragOperation.NONE
        }
    }

    fun onDragExited() {
        val dndEvent = DragAndDropEvent(
            action = null, nativeEvent = null, positionInRootImpl = Offset.Zero,
        )

        callbackInterceptor.execute {
            rootDragAndDropNode.onExited(dndEvent)
            rootDragAndDropNode.onEnded(dndEvent)
            previousAction = null
        }
    }

    fun onDragPerformed(info: DragInfo): Boolean {
        val dndEvent = info.toDragAndDropEvent()

        val consumed = callbackInterceptor.execute {
            val wasDropConsumed = rootDragAndDropNode.onDrop(dndEvent)
            rootDragAndDropNode.onEnded(dndEvent)
            previousAction = null
            wasDropConsumed
        }
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

    private fun DpOffset.toOffset() = with(density) {
        Offset(x.toPx(), y.toPx())
    }
}
