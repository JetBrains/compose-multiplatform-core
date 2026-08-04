/*
 * Copyright 2026 The Android Open Source Project
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

@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.desktop.logging.logger
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import org.jetbrains.desktop.win32.DataFormat as Win32DataFormat
import org.jetbrains.desktop.win32.DataObject as Win32DataObject
import org.jetbrains.desktop.win32.DataTransferException
import org.jetbrains.desktop.win32.DragDropEffect
import org.jetbrains.desktop.win32.DragDropModifier
import org.jetbrains.desktop.win32.DragDropModifiers
import org.jetbrains.desktop.win32.PhysicalPoint
import org.jetbrains.desktop.win32.Screen
import org.jetbrains.desktop.win32.Window as Win32Window

internal sealed interface WindowsDragAndDropData {
    class DataObject(val dataObject: Win32DataObject) : WindowsDragAndDropData
    class Formats(val availableFormats: Set<Win32DataFormat>) : WindowsDragAndDropData
}

internal class WindowsDragAndDropClipboardEntry(
    internal val data: WindowsDragAndDropData,
    internal val allowedEffect: DragDropEffect = DragDropEffect.None,
) : ClipboardEntry {
    private val acceptedFormats = linkedMapOf<ClipboardFormat<*>, List<DragAndDropTransferAction>>(
        ClipboardFormat.WindowLocalDrag to listOf(DragAndDropTransferAction.Move, DragAndDropTransferAction.Copy),
    )

    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> = getForFormatSync(format)

    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        if (data !is WindowsDragAndDropData.DataObject) return emptyList()
        val dataObject = data.dataObject
        return try {
            dataObject.readForFormat(format)
        } catch (e: DataTransferException) {
            logger.warn(e) { "Failed to read drag-and-drop data for format $format" }
            emptyList()
        }
    }

    internal fun containsFormat(format: ClipboardFormat<*>, actions: List<DragAndDropTransferAction>): Boolean {
        if (!actions.any { allowedEffect.hasFlag(it.toDragDropEffect()) }) {
            return false
        }
        val nativeFormat = format.toWin32DataFormat() ?: return false
        return when (data) {
            is WindowsDragAndDropData.Formats -> nativeFormat in data.availableFormats
            is WindowsDragAndDropData.DataObject -> try {
                data.dataObject.isFormatAvailable(nativeFormat)
            } catch (e: DataTransferException) {
                logger.warn(e) { "Failed to query drag-and-drop format availability" }
                false
            }
        }
    }

    internal fun acceptsFormat(format: ClipboardFormat<*>, actions: List<DragAndDropTransferAction>) {
        if (containsFormat(format, actions)) {
            acceptedFormats[format] = actions
        }
    }

    internal fun acceptedFormats(): List<Pair<ClipboardFormat<*>, List<DragAndDropTransferAction>>> =
        acceptedFormats.map { it.key to it.value }
}

/**
 * The OLE DROP-TARGET side of Windows drag and drop: translates `DropTarget` callbacks into the
 * scene's [ComposeSceneDragAndDropNode] and negotiates the resulting [DragDropEffect]. The drag
 * SOURCE lives in `WindowsWindow.startDragSession` over `DragDropManager.doDragDrop`; this class
 * is drop-only. Every node-touching callback runs through [withMainThreadPrepared], which the
 * window backs with `composeScene.withFrameTransaction` — DnD callbacks mutate Compose state and
 * must carry frame attribution.
 */
internal class WindowsDragAndDropManager(
    private val nativeWindow: Win32Window,
    private val rootDragAndDropNode: () -> ComposeSceneDragAndDropNode,
    private val withMainThreadPrepared: (block: () -> Unit) -> Unit,
) {
    private var previousEffect: DragDropEffect? = null
    private var activeAvailableFormats: Set<Win32DataFormat>? = null

    fun onDragEnter(
        dataObject: Win32DataObject,
        point: PhysicalPoint,
        allowedEffect: DragDropEffect,
        modifiers: DragDropModifiers,
    ): DragDropEffect {
        val clipboardEntry = WindowsDragAndDropClipboardEntry(
            WindowsDragAndDropData.DataObject(dataObject),
            allowedEffect,
        )
        val dndEvent = clipboardEntry.toDragAndDropEvent(point = point)

        var acceptedTransfer = false
        withMainThreadPrepared {
            acceptedTransfer = rootDragAndDropNode().acceptDragAndDropTransfer(dndEvent)
        }
        if (!acceptedTransfer) {
            return DragDropEffect.None
        }

        var effect = DragDropEffect.None
        withMainThreadPrepared {
            val node = rootDragAndDropNode()
            node.onStarted(dndEvent)
            node.onEntered(dndEvent)
            // onMoved sets lastChildDragAndDropModifierNode (required for hasEligibleDropTarget)
            // and lets targets register accepted formats needed for `negotiateEffect`
            node.onMoved(dndEvent)
            effect = negotiateEffect(clipboardEntry, allowedEffect, modifiers)
        }
        activeAvailableFormats = try {
            dataObject.listItemFormats().toSet()
        } catch (e: DataTransferException) {
            logger.warn(e) { "Failed to list drag-and-drop formats" }
            emptySet()
        }
        previousEffect = effect
        return effect
    }

    fun onDragOver(
        point: PhysicalPoint,
        allowedEffect: DragDropEffect,
        modifiers: DragDropModifiers,
    ): DragDropEffect {
        val available = activeAvailableFormats ?: return DragDropEffect.None
        val clipboardEntry = WindowsDragAndDropClipboardEntry(WindowsDragAndDropData.Formats(available), allowedEffect)

        var effect = DragDropEffect.None
        withMainThreadPrepared {
            rootDragAndDropNode().onMoved(clipboardEntry.toDragAndDropEvent(point))
            effect = negotiateEffect(clipboardEntry, allowedEffect, modifiers)
        }

        if (effect != previousEffect) {
            withMainThreadPrepared {
                rootDragAndDropNode().onChanged(clipboardEntry.toDragAndDropEvent(point, effect))
            }
            previousEffect = effect
        }

        return effect
    }

    fun onDragLeave() {
        if (activeAvailableFormats == null) return
        val clipboardEntry = WindowsDragAndDropClipboardEntry(WindowsDragAndDropData.Formats(emptySet()))
        val dndEvent = DragAndDropEvent(
            action = null,
            nativeEvent = clipboardEntry,
            positionInRootImpl = Offset.Zero,
        )

        withMainThreadPrepared {
            val node = rootDragAndDropNode()
            node.onExited(dndEvent)
            node.onEnded(dndEvent)
            previousEffect = null
            activeAvailableFormats = null
        }
    }

    fun onDrop(
        dataObject: Win32DataObject,
        point: PhysicalPoint,
        allowedEffect: DragDropEffect,
        modifiers: DragDropModifiers,
    ): DragDropEffect {
        val clipboardEntry = WindowsDragAndDropClipboardEntry(
            data = WindowsDragAndDropData.DataObject(dataObject),
            allowedEffect = allowedEffect,
        )

        var result = DragDropEffect.None
        withMainThreadPrepared {
            val node = rootDragAndDropNode()
            // onMoved sets lastChildDragAndDropModifierNode (required for hasEligibleDropTarget)
            // and lets targets register accepted formats needed for `negotiateEffect`
            node.onMoved(clipboardEntry.toDragAndDropEvent(point))
            val effect = negotiateEffect(clipboardEntry, allowedEffect, modifiers)

            val dropEvent = clipboardEntry.toDragAndDropEvent(point, effect)
            val consumed = node.onDrop(dropEvent)
            node.onEnded(dropEvent)
            previousEffect = null
            activeAvailableFormats = null
            result = if (consumed) effect else DragDropEffect.None
        }
        return result
    }

    private fun negotiateEffect(
        clipboardEntry: WindowsDragAndDropClipboardEntry,
        allowedEffect: DragDropEffect,
        modifiers: DragDropModifiers,
    ): DragDropEffect {
        if (!rootDragAndDropNode().hasEligibleDropTarget) {
            return DragDropEffect.None
        }
        val availableEffects = clipboardEntry.acceptedFormats().firstNotNullOfOrNull { (format, actions) ->
            if (clipboardEntry.containsFormat(format, actions)) {
                actions.filter { allowedEffect.hasFlag(it.toDragDropEffect()) }.ifEmpty { null }
            } else {
                null
            }
        }?.map { it.toDragDropEffect() } ?: return DragDropEffect.None

        val preferred = modifiers.preferredEffect()
        return preferred?.takeIf { effect -> availableEffects.any { it == effect } }
            ?: availableEffects.firstOrNull()
            ?: DragDropEffect.None
    }

    private fun WindowsDragAndDropClipboardEntry.toDragAndDropEvent(
        point: PhysicalPoint,
        effect: DragDropEffect? = previousEffect,
    ): DragAndDropEvent {
        val clientPoint = Screen.mapToClient(nativeWindow, point)
        return DragAndDropEvent(
            action = effect?.toDragAndDropTransferAction(),
            nativeEvent = this,
            positionInRootImpl = Offset(clientPoint.x.toFloat(), clientPoint.y.toFloat()),
        )
    }
}

private val logger by lazy { logger<WindowsDragAndDropManager>() }

internal fun DragAndDropTransferAction.toDragDropEffect(): DragDropEffect = when (this) {
    DragAndDropTransferAction.Copy -> DragDropEffect.Copy
    DragAndDropTransferAction.Move -> DragDropEffect.Move
    DragAndDropTransferAction.Link -> DragDropEffect.Link
    else -> DragDropEffect.None
}

private fun DragDropModifiers.preferredEffect(): DragDropEffect? = when {
    hasFlag(DragDropModifier.Control) && hasFlag(DragDropModifier.Shift) -> DragDropEffect.Link
    hasFlag(DragDropModifier.Alt) -> DragDropEffect.Link
    hasFlag(DragDropModifier.Control) -> DragDropEffect.Copy
    hasFlag(DragDropModifier.Shift) -> DragDropEffect.Move
    else -> null
}

internal fun DragDropEffect.toDragAndDropTransferAction(): DragAndDropTransferAction? = when {
    hasFlag(DragDropEffect.Move) -> DragAndDropTransferAction.Move
    hasFlag(DragDropEffect.Copy) -> DragAndDropTransferAction.Copy
    hasFlag(DragDropEffect.Link) -> DragAndDropTransferAction.Link
    else -> null
}
