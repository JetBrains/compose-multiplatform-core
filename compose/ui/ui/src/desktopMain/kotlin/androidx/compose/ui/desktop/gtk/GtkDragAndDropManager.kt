@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEventJvm
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.FileUriListMimeType
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
    private val rootDragAndDropNode: ComposeSceneDragAndDropNode,
    private var density: Density,
    private val callbackInterceptor: CallbackInterceptor,
    private val currentDragClipboardEntry: () -> ClipboardEntry,
    private val currentMimeTypes: () -> List<String>,
) {
    private var previousAction: DragAndDropTransferAction? = null
    private var lastPositionInRoot: Offset = Offset.Zero
    private var started = false

    fun updateDensity(density: Density) {
        this.density = density
    }

    fun onQuery(query: DragAndDropQueryData): DragAndDropQueryResponse {
        val event = query.toDragAndDropEvent()

        if (!started) {
            val transferAccepted = callbackInterceptor.execute {
                rootDragAndDropNode.acceptDragAndDropTransfer(event)
            }
            if (transferAccepted) {
                callbackInterceptor.execute {
                    previousAction = event.action
                    rootDragAndDropNode.onStarted(event)
                    // onMoved (not onEntered) sets lastChildDragAndDropModifierNode, which is
                    // required for hasEligibleDropTarget to reflect the actual cursor position.
                    rootDragAndDropNode.onMoved(event)
                }
                started = true
            }
        } else {
            // Always call onMoved regardless of current hasEligibleDropTarget: the position
            // must be kept up-to-date so the check below reflects the real cursor location.
            callbackInterceptor.execute {
                if (event.action != previousAction) {
                    rootDragAndDropNode.onChanged(event)
                    previousAction = event.action
                }
                rootDragAndDropNode.onMoved(event)
            }
        }

        // Check hasEligibleDropTarget after onMoved has updated the position state.
        val hasEligibleTarget = started && callbackInterceptor.execute {
            rootDragAndDropNode.hasEligibleDropTarget
        }
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
        lastPositionInRoot = with(density) { event.locationInWindow.toDpOffset().toOffset() }
        val dragEvent = DragAndDropEventJvm(
            action = event.action?.toComposeAction(),
            nativeEvent = currentDragClipboardEntry(),
            positionInRootImpl = lastPositionInRoot,
        )
        val consumed = callbackInterceptor.execute {
            val result = rootDragAndDropNode.onDrop(dragEvent)
            rootDragAndDropNode.onEnded(dragEvent)
            previousAction = null
            started = false
            result
        }
        return consumed
    }

    fun onLeave() {
        if (!started) return
        val event = DragAndDropEventJvm(null, currentDragClipboardEntry(), Offset.Zero)
        callbackInterceptor.execute {
            rootDragAndDropNode.onExited(event)
            rootDragAndDropNode.onEnded(event)
            previousAction = null
            started = false
        }
    }

    private fun DragAndDropQueryData.toDragAndDropEvent(): DragAndDropEventJvm {
        val positionInRoot = with(density) { locationInWindow.toDpOffset().toOffset() }
        lastPositionInRoot = positionInRoot
        val selectedAction = when {
            WindowLocalDragMimeType in currentMimeTypes() -> DragAndDropTransferAction.Move
            FileUriListMimeType in currentMimeTypes() -> DragAndDropTransferAction.Copy
            else -> DragAndDropTransferAction.Copy
        }
        return DragAndDropEventJvm(
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
