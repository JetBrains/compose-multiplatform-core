@file:Suppress("RedundantVisibilityModifier")

package androidx.compose.ui.desktop.headless

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.unit.DpOffset

internal typealias Timestamp = Double

internal sealed interface WindowEvent {
    public val windowId: LightweightWindowId
}

internal sealed class Event {
    internal data class KeyDown(
        override val windowId: LightweightWindowId,
        val key: Key,
        val codePoint: Int?,
    ) : Event(),
        WindowEvent

    internal data class KeyUp(
        override val windowId: LightweightWindowId,
        val key: Key,
        val codePoint: Int?,
    ) : Event(),
        WindowEvent

    internal data class MouseMoved(
        override val windowId: LightweightWindowId,
        val locationInWindow: DpOffset,
        val timestamp: Timestamp,
    ) : Event(),
        WindowEvent

    internal data class MouseEntered(
        override val windowId: LightweightWindowId,
        val locationInWindow: DpOffset,
        val timestamp: Timestamp,
    ) : Event(),
        WindowEvent

    internal data class MouseExited(
        override val windowId: LightweightWindowId,
        val locationInWindow: DpOffset,
        val timestamp: Timestamp,
    ) : Event(),
        WindowEvent

    internal data class MouseUp(
        override val windowId: LightweightWindowId,
        val button: PointerButton,
        val locationInWindow: DpOffset,
        val timestamp: Timestamp,
    ) : Event(),
        WindowEvent

    internal data class MouseDown(
        override val windowId: LightweightWindowId,
        val button: PointerButton,
        val locationInWindow: DpOffset,
        val timestamp: Timestamp,
    ) : Event(),
        WindowEvent
}
