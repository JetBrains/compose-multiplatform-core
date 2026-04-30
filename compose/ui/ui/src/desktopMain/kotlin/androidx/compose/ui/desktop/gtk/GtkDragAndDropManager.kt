@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.FileUriListMimeType
import androidx.compose.ui.desktop.WindowLocalDragMimeType
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.unit.Density
import org.jetbrains.desktop.gtk.DragAndDropAction
import org.jetbrains.desktop.gtk.DragAndDropQueryData
import org.jetbrains.desktop.gtk.DragAndDropQueryResponse
import org.jetbrains.desktop.gtk.Event
import org.jetbrains.desktop.gtk.SupportedActionsForMime

internal class GtkDragAndDropManager(
    private val rootDragAndDropNode: () -> ComposeSceneDragAndDropNode,
    private val density: () -> Density,
    private val currentDragClipboardEntry: () -> ClipboardEntry,
    private val currentMimeTypes: () -> List<String>,
) {
    private var previousAction: DragAndDropTransferAction? = null
    private var lastPositionInRoot: Offset = Offset.Zero
    private var started = false

    fun onQuery(query: DragAndDropQueryData): DragAndDropQueryResponse {
        val event = query.toDragAndDropEvent()
        val node = rootDragAndDropNode()

        if (!started) {
            val transferAccepted = node.acceptDragAndDropTransfer(event)
            if (transferAccepted) {
                previousAction = event.action
                node.onStarted(event)
                // onMoved (not onEntered) sets lastChildDragAndDropModifierNode, which is
                // required for hasEligibleDropTarget to reflect the actual cursor position.
                node.onMoved(event)
                started = true
            }
        } else {
            // Always call onMoved regardless of current hasEligibleDropTarget: the position
            // must be kept up-to-date so the check below reflects the real cursor location.
            if (event.action != previousAction) {
                node.onChanged(event)
                previousAction = event.action
            }
            node.onMoved(event)
        }

        // Check hasEligibleDropTarget after onMoved has updated the position state.
        val hasEligibleTarget = started && node.hasEligibleDropTarget
        val selectedAction = if (hasEligibleTarget) event.action ?: DragAndDropTransferAction.Copy else null
        val supportedActions = selectedAction?.toGtkSupportedActions().orEmpty()
        if (supportedActions.isEmpty()) {
            return DragAndDropQueryResponse(emptyList())
        }
        return DragAndDropQueryResponse(
            currentMimeTypes().map { mimeType ->
                SupportedActionsForMime(
                    supportedMimeType = mimeType,
                    supportedActions = supportedActions,
                    preferredAction = supportedActions.first(),
                )
            },
        )
    }

    fun onDrop(event: Event.DropPerformed): Boolean {
        val node = rootDragAndDropNode()
        val dragEvent = DragAndDropEvent(
            action = event.action?.toComposeAction(),
            nativeEvent = currentDragClipboardEntry(),
            positionInRootImpl = lastPositionInRoot,
        )
        val consumed = node.onDrop(dragEvent)
        node.onEnded(dragEvent)
        previousAction = null
        started = false
        return consumed
    }

    fun onLeave() {
        if (!started) return

        val node = rootDragAndDropNode()
        val event = DragAndDropEvent(null, currentDragClipboardEntry(), Offset.Zero)
        node.onExited(event)
        node.onEnded(event)
        previousAction = null
        started = false
    }

    private fun DragAndDropQueryData.toDragAndDropEvent(): DragAndDropEvent {
        val positionInRoot = locationInWindow.toDpOffset().toPxOffset(density())
        lastPositionInRoot = positionInRoot
        val selectedAction = when {
            WindowLocalDragMimeType in currentMimeTypes() -> DragAndDropTransferAction.Move
            FileUriListMimeType in currentMimeTypes() -> DragAndDropTransferAction.Copy
            else -> DragAndDropTransferAction.Copy
        }
        return DragAndDropEvent(
            action = selectedAction,
            nativeEvent = currentDragClipboardEntry(),
            positionInRootImpl = positionInRoot,
        )
    }
}

private fun DragAndDropTransferAction.toGtkSupportedActions(): Set<DragAndDropAction> =
    if (this == DragAndDropTransferAction.Move) {
        setOf(DragAndDropAction.Move)
    } else {
        setOf(DragAndDropAction.Copy)
    }

internal fun DragAndDropAction.toComposeAction(): DragAndDropTransferAction = when (this) {
    DragAndDropAction.Move -> DragAndDropTransferAction.Move
    DragAndDropAction.Copy -> DragAndDropTransferAction.Copy
}
