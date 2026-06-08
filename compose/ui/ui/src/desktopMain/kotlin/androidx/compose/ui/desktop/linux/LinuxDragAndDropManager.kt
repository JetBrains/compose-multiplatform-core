@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.linux

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
import androidx.compose.ui.unit.toOffset
import noria.CallbackInterceptor
import org.jetbrains.desktop.linux.DragAndDropAction
import org.jetbrains.desktop.linux.DragAndDropQueryData
import org.jetbrains.desktop.linux.DragAndDropQueryResponse
import org.jetbrains.desktop.linux.Event
import org.jetbrains.desktop.linux.SupportedActionsForMime

internal class LinuxDragAndDropManager(
    private val rootDragAndDropNode: () -> ComposeSceneDragAndDropNode,
    private val density: () -> Density,
    private val callbackInterceptor: CallbackInterceptor,
) {
    private var previousAction: DragAndDropTransferAction? = null
    private var lastPositionInRoot: Offset = Offset.Zero
    private var started = false

    fun onQuery(query: DragAndDropQueryData): DragAndDropQueryResponse {
        val nativeEvent = LinuxDragAndDropClipboardEntry(query.mimeTypes, null)
        val event = query.toDragAndDropEvent(nativeEvent)
        val node = rootDragAndDropNode()

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
                val linuxActions = actions.mapNotNull(DragAndDropTransferAction::toLinuxAction)
                SupportedActionsForMime(
                    supportedMimeType = mimeType,
                    supportedActions = linuxActions.toSet(),
                    preferredAction = linuxActions.firstOrNull(),
                )
            },
        )
        }

    fun onDrop(event: Event.DropPerformed): Boolean {
        lastPositionInRoot = event.locationInWindow.toDpOffset().toOffset(density())
        val mimeTypes = event.content?.mimeType?.let { listOf(it) }.orEmpty()
        val node = rootDragAndDropNode()

        val dragEvent = DragAndDropEvent(
            action = event.action?.toComposeAction(),
            nativeEvent = LinuxDragAndDropClipboardEntry(mimeTypes, event.content?.data),
            positionInRootImpl = lastPositionInRoot,
        )
        val consumed = callbackInterceptor.execute {
            val result = node.onDrop(dragEvent)
            node.onEnded(dragEvent)
            previousAction = null
            started = false
            result
        }
        return consumed
    }

    fun onLeave() {
        if (!started) return
        val event = DragAndDropEvent(
            action = null,
            nativeEvent = LinuxDragAndDropClipboardEntry(emptyList(), null),
            positionInRootImpl = Offset.Zero
        )
        val node = rootDragAndDropNode()
        callbackInterceptor.execute {
            node.onExited(event)
            node.onEnded(event)
            previousAction = null
            started = false
        }
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
