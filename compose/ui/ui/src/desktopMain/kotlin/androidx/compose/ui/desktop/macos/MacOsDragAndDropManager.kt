@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package androidx.compose.ui.desktop.macos

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import noria.CallbackInterceptor
import org.jetbrains.desktop.macos.DragInfo
import org.jetbrains.desktop.macos.DragOperation
import org.jetbrains.desktop.macos.Pasteboard
import org.jetbrains.desktop.macos.PasteboardType

private fun PasteboardType.containsUniformTypeIdentifier(target: String): Boolean {
    val itemCount = Pasteboard.itemCount(this).toInt()
    return (0 until itemCount).any { target in Pasteboard.readItemTypes(it, this) }
}

@OptIn(ExperimentalComposeUiApi::class)
internal class MacOsDragAndDropClipboardEntry(
    private val dragInfo: DragInfo,
) : ClipboardEntry {
    private val clipboardEntry = MacOsClipboardEntry(dragInfo.pasteboard)
    private val acceptedTargets = linkedMapOf(
        UniformTypeIdentifiers.windowLocalDrag to listOf(DragOperation.MOVE, DragOperation.COPY)
    )

    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
        return clipboardEntry.getForFormat(format)
    }

    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        return clipboardEntry.getForFormatSync(format)
    }

    internal fun containsFormat(format: ClipboardFormat<*>, actions: List<DragAndDropTransferAction>): Boolean {
        val pasteboard = dragInfo.pasteboard
        if (!actions.any { dragInfo.allowedOperations.contains(it.toDragOperation()) }) {
            return false
        }
        val target = format.toUniformTypeIdentifier()
        return pasteboard.containsUniformTypeIdentifier(target)
    }

    internal fun acceptsFormat(format: ClipboardFormat<*>, actions: List<DragAndDropTransferAction>) {
        if (containsFormat(format, actions)) {
            val target = format.toUniformTypeIdentifier()
            acceptedTargets[target] = actions.map(DragAndDropTransferAction::toDragOperation)
        }
    }

    internal fun acceptedTargets(): Collection<Pair<String, List<DragOperation>>> = acceptedTargets.map { it.key to it.value }
}

internal class MacOsDragAndDropManager(
    private val rootDragAndDropNode: () -> ComposeSceneDragAndDropNode,
    private val density: () -> Density,
    private val callbackInterceptor: CallbackInterceptor,
) {
    private var previousAction: DragAndDropTransferAction? = null

    fun onDragEntered(info: DragInfo): DragOperation {
        val dndEvent = info.toDragAndDropEvent()
        val node = rootDragAndDropNode()

        val acceptedTransfer = callbackInterceptor.execute {
            node.acceptDragAndDropTransfer(dndEvent)
        }
        if (acceptedTransfer) {
            callbackInterceptor.execute {
                previousAction = dndEvent.action
                node.onStarted(dndEvent)
                node.onEntered(dndEvent)
            }
        }

        return if (acceptedTransfer && dndEvent.action != null) {
            dndEvent.action.toDragOperation()
        } else {
            DragOperation.NONE
        }
    }

    fun onDragUpdated(info: DragInfo): DragOperation {
        val node = rootDragAndDropNode()
        val nativeEvent = MacOsDragAndDropClipboardEntry(info)
        val dndEvent = info.toDragAndDropEvent(nativeEvent)

        callbackInterceptor.execute {
            if (dndEvent.action != previousAction) {
                node.onChanged(dndEvent)
                previousAction = dndEvent.action
            }
            node.onMoved(dndEvent)
        }

        val acceptedTargets = nativeEvent.acceptedTargets()
        return if (node.hasEligibleDropTarget) {
            val acceptedDragOperations = acceptedTargets.firstNotNullOfOrNull { (target, actions) ->
                if (info.pasteboard.containsUniformTypeIdentifier(target)) {
                    actions.filter { it in info.allowedOperations }.ifEmpty { null }
                } else {
                    null
                }
            }.orEmpty()

            when {
                DragOperation.MOVE in acceptedDragOperations -> DragOperation.MOVE
                DragOperation.COPY in acceptedDragOperations -> DragOperation.COPY
                DragOperation.LINK in acceptedDragOperations -> DragOperation.LINK
                else -> DragOperation.NONE
            }
        } else {
            DragOperation.NONE
        }
    }

    fun onDragExited() {
        val dndEvent = DragAndDropEvent(
            action = null, nativeEvent = null, positionInRootImpl = Offset.Zero,
        )
        val node = rootDragAndDropNode()

        callbackInterceptor.execute {
            node.onExited(dndEvent)
            node.onEnded(dndEvent)
            previousAction = null
        }
    }

    fun onDragPerformed(info: DragInfo): Boolean {
        val dndEvent = info.toDragAndDropEvent()
        val node = rootDragAndDropNode()

        val consumed = callbackInterceptor.execute {
            val wasDropConsumed = node.onDrop(dndEvent)
            node.onEnded(dndEvent)
            previousAction = null
            wasDropConsumed
        }
        return consumed
    }

    private fun DragInfo.toDragAndDropEvent(
        nativeEvent: MacOsDragAndDropClipboardEntry = MacOsDragAndDropClipboardEntry(this)
    ): DragAndDropEvent {
        val selectedAction = when {
            DragOperation.MOVE in allowedOperations -> DragAndDropTransferAction.Move
            DragOperation.COPY in allowedOperations -> DragAndDropTransferAction.Copy
            DragOperation.LINK in allowedOperations -> DragAndDropTransferAction.Link
            else -> null
        }

        return DragAndDropEvent(
            action = selectedAction,
            nativeEvent = nativeEvent,
            positionInRootImpl = locationInWindow.toDpOffset().toOffset(),
        )
    }

    private fun DpOffset.toOffset() = with(density()) {
        Offset(x.toPx(), y.toPx())
    }
}
