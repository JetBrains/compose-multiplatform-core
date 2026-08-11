@file:Suppress("RedundantVisibilityModifier")

package androidx.compose.ui.desktop.headless

import androidx.compose.ui.SystemTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.Screen
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement

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

    /**
     * [delta] is already in the direction and magnitude the scene should see.
     *
     * The native trackers scale and invert their platform's raw wheel values (macOS flips the sign
     * and applies a precise-delta factor, and the others have their own conventions). There is no
     * platform here whose convention could be modelled, so the caller states the outcome directly
     * rather than encoding one platform's arithmetic into a backend that has none.
     */
    internal data class ScrollWheel(
        override val windowId: LightweightWindowId,
        val locationInWindow: DpOffset,
        val delta: DpOffset,
        val timestamp: Timestamp,
    ) : Event(),
        WindowEvent

    // ----- Window and platform events -----
    //
    // These carry the values a real backend would read back off its native window after the
    // platform told it something changed. Headless has no native window, so the injected event is
    // the source of truth; [HeadlessWindow.handleEvent] applies each one inside a frame
    // transaction, exactly as the native backends apply theirs.

    internal data class WindowResize(
        override val windowId: LightweightWindowId,
        val size: DpSize,
        val contentSize: DpSize,
    ) : Event(),
        WindowEvent

    internal data class WindowMove(
        override val windowId: LightweightWindowId,
        val origin: DpOffset,
    ) : Event(),
        WindowEvent

    internal data class WindowFocusChange(
        override val windowId: LightweightWindowId,
        val isFocused: Boolean,
    ) : Event(),
        WindowEvent

    internal data class WindowPlacementChange(
        override val windowId: LightweightWindowId,
        val placement: WindowPlacement,
    ) : Event(),
        WindowEvent

    /**
     * Carries the whole screen because density belongs to it: a real display-scale change arrives
     * as a screen change and the backend re-reads density from it, so a headless density change is
     * a screen change with a different density rather than an independent event.
     */
    internal data class WindowScreenChange(
        override val windowId: LightweightWindowId,
        val screen: Screen,
    ) : Event(),
        WindowEvent

    internal data class WindowDecorationChange(
        override val windowId: LightweightWindowId,
        val decoration: WindowDecoration,
        val customTitleBarInsets: Pair<Dp, Dp>?,
    ) : Event(),
        WindowEvent

    internal data class WindowThemeChange(
        override val windowId: LightweightWindowId,
        val systemTheme: SystemTheme,
    ) : Event(),
        WindowEvent

    internal data class WindowCloseRequest(
        override val windowId: LightweightWindowId,
        val reason: WindowCloseRequestReason,
    ) : Event(),
        WindowEvent
}
