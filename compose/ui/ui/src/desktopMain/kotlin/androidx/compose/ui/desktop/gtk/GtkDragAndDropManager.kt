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
import androidx.compose.ui.desktop.LinuxDragAndDropClipboardEntry
import androidx.compose.ui.desktop.WindowLocalDragMimeType
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.unit.Density
import noria.CallbackInterceptor
import org.jetbrains.desktop.gtk.DragAndDropAction
import org.jetbrains.desktop.gtk.DragAndDropQueryData
import org.jetbrains.desktop.gtk.DragAndDropQueryResponse
import org.jetbrains.desktop.gtk.Event
import org.jetbrains.desktop.gtk.SupportedActionsForMime

internal class GtkDragAndDropManager(
    private val rootDragAndDropNode: () -> ComposeSceneDragAndDropNode,
    private val density: () -> Density,
    private val callbackInterceptor: CallbackInterceptor,
) {
    private var previousAction: DragAndDropTransferAction? = null
    private var lastPositionInRoot: Offset = Offset.Zero
    private var started = false

    fun onQuery(query: DragAndDropQueryData): DragAndDropQueryResponse {
        val node = rootDragAndDropNode()
        val nativeEvent = LinuxDragAndDropClipboardEntry(query.mimeTypes, null)
        val event = query.toDragAndDropEvent(nativeEvent)

        if (!started) {
            val transferAccepted = callbackInterceptor.execute {
                node.acceptDragAndDropTransfer(event)
            }
            if (transferAccepted) {
                callbackInterceptor.execute {
                    previousAction = event.action
                    node.onStarted(event)
                    // onMoved (not onEntered) sets lastChildDragAndDropModifierNode, which is
                    // required for hasEligibleDropTarget to reflect the actual cursor position.
                    node.onMoved(event)
                }
                started = true
            }
        } else {
            // Always call onMoved regardless of current hasEligibleDropTarget: the position
            // must be kept up to date so the check below reflects the real cursor location.
            callbackInterceptor.execute {
                if (event.action != previousAction) {
                    node.onChanged(event)
                    previousAction = event.action
                }
                node.onMoved(event)
            }
        }

        // Check hasEligibleDropTarget after onMoved has updated the position state.
        val hasEligibleTarget = started && callbackInterceptor.execute {
            node.hasEligibleDropTarget
        }

        if (!hasEligibleTarget) {
            return DragAndDropQueryResponse(emptyList())
        }

        val acceptedTypes = nativeEvent.acceptedMimeTypes().filter { it.first in query.mimeTypes }

        return DragAndDropQueryResponse(
            acceptedTypes.map { (mimeType, actions) ->
            val linuxActions = actions.mapNotNull(DragAndDropTransferAction::toGtkAction)
                SupportedActionsForMime(
                    supportedMimeType = mimeType,
                    supportedActions = linuxActions.toSet(),
                    preferredAction = linuxActions.firstOrNull(),
                )
            },
        )
    }

    fun onDrop(event: Event.DropPerformed): Boolean {
        val node = rootDragAndDropNode()
        val mimeTypes = event.content?.mimeType?.let { listOf(it) }.orEmpty()
        val dragEvent = DragAndDropEvent(
            action = event.action?.toComposeAction(),
            nativeEvent = LinuxDragAndDropClipboardEntry(mimeTypes, event.content?.data),
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
        val event = DragAndDropEvent(
            action = null,
            nativeEvent = LinuxDragAndDropClipboardEntry(emptyList(), null),
            positionInRootImpl = Offset.Zero
        )
        node.onExited(event)
        node.onEnded(event)
        previousAction = null
        started = false
    }

    private fun DragAndDropQueryData.toDragAndDropEvent(nativeEvent: LinuxDragAndDropClipboardEntry): DragAndDropEvent {
        val positionInRoot = locationInWindow.toDpOffset().toPxOffset(density())
        lastPositionInRoot = positionInRoot
        return DragAndDropEvent(
            action = null,
            nativeEvent = nativeEvent,
            positionInRootImpl = positionInRoot,
        )
    }
}

internal fun DragAndDropAction.toComposeAction(): DragAndDropTransferAction = when (this) {
    DragAndDropAction.Move -> DragAndDropTransferAction.Move
    DragAndDropAction.Copy -> DragAndDropTransferAction.Copy
}