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

@file:Suppress("DuplicatedCode")
@file:OptIn(kotlin.time.ExperimentalTime::class)

package androidx.compose.ui.desktop.windows

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.copy
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isBack
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isForward
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimary
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiary
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import org.jetbrains.desktop.win32.LogicalPoint
import org.jetbrains.desktop.win32.PointerButton as Win32PointerButton
import org.jetbrains.desktop.win32.VirtualKey
import org.jetbrains.desktop.win32.Event as Win32Event
import org.jetbrains.desktop.win32.EventHandlerResult as Win32EventHandlerResult

/**
 * Callback signature mirroring [androidx.compose.ui.scene.ComposeScene.sendPointerEvent].
 */
@InternalComposeUiApi
internal fun interface SendPointerEvent {
    fun invoke(
        eventType: PointerEventType,
        position: Offset,
        scrollDelta: Offset,
        timeMillis: Long,
        type: PointerType,
        buttons: PointerButtons?,
        keyboardModifiers: PointerKeyboardModifiers?,
        nativeEvent: Any?,
        button: PointerButton?,
    ): PointerEventResult
}

/**
 * Translates win32 (`org.jetbrains.desktop.win32`) pointer/key/scroll/activation events into Compose
 * pointer and key events. Structural twin of the macOS/linux trackers, but win32-specific in three
 * ways carried over from Noria: it reads live modifier state via [currentKeyboardModifiers] (win32
 * has no `ModifiersChanged` event), it forces Touch input mode on every pointer event, and it
 * synthesizes a "moved outside window" nudge on [Win32Event.PointerExited] (Windows stops delivering
 * moves once the cursor leaves the window, so hover would otherwise stick — no macOS/linux analogue).
 *
 * The tracker does NOT open frame transactions: the window wraps every native→Compose ingress in
 * `composeScene.withFrameTransaction { }` (fork pattern, identical to the macOS window's division of
 * responsibility).
 */
@ExperimentalComposeUiApi
@InternalCoreApi
@InternalComposeUiApi
internal class InputStateTracker(
    private val inputModeManager: InputModeManager,
    private val sendPointerEvent: SendPointerEvent,
    private val sendKeyEvent: (KeyEvent) -> Boolean,
) {
    private var pointerInWindow: Boolean = false
    private var pointerPosition: Offset? = null
    private var lastNativeEventUptimeMillis: Long? = null
    private var pointerButtons = PointerButtons()
    private var syntheticPointerEventAfterRelayoutGeneration: Long = 0
    internal var keyboardModifiers by mutableStateOf(PointerKeyboardModifiers())

    /** Test seam: KDT win32 Event constructors are internal, so tests drive state directly. */
    internal fun overridePointerStateForTest(
        pointerInWindow: Boolean,
        pointerPosition: Offset? = this.pointerPosition,
    ) {
        this.pointerInWindow = pointerInWindow
        this.pointerPosition = pointerPosition
    }

    fun updateStateAndSendEvents(event: Win32Event, density: Density): Win32EventHandlerResult {
        syntheticPointerEventAfterRelayoutGeneration++
        return when (event) {
            is Win32Event.PointerDown -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        event,
                    )
                }
                pointerButtons += event.button.toPointerButton()
                val modifiers = currentKeyboardModifiers()
                val processResult = density.run {
                    sendPointerEvent.invoke(
                        eventType = PointerEventType.Press,
                        position = event.locationInWindow.toDpOffset().toPxOffset(this),
                        scrollDelta = Offset.Zero,
                        timeMillis = uptime,
                        type = PointerType.Mouse,
                        buttons = pointerButtons,
                        keyboardModifiers = modifiers,
                        nativeEvent = event,
                        button = event.button.toPointerButton(),
                    )
                }
                when {
                    processResult.anyChangeConsumed -> Win32EventHandlerResult.Stop
                    sendKeyEvent(
                        KeyEvent(
                            key = event.button.toKey(),
                            type = KeyEventType.KeyDown,
                            isCtrlPressed = modifiers.isCtrlPressed,
                            isMetaPressed = modifiers.isMetaPressed,
                            isAltPressed = modifiers.isAltPressed,
                            isShiftPressed = modifiers.isShiftPressed,
                            nativeEvent = event,
                        ),
                    ) -> Win32EventHandlerResult.Stop
                    else -> Win32EventHandlerResult.Continue
                }
            }
            is Win32Event.PointerUp -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        event,
                    )
                }
                pointerButtons -= event.button.toPointerButton()
                val modifiers = currentKeyboardModifiers()
                val processResult = density.run {
                    sendPointerEvent.invoke(
                        eventType = PointerEventType.Release,
                        position = event.locationInWindow.toDpOffset().toPxOffset(this),
                        scrollDelta = Offset.Zero,
                        timeMillis = uptime,
                        type = PointerType.Mouse,
                        buttons = pointerButtons,
                        keyboardModifiers = modifiers,
                        nativeEvent = event,
                        button = event.button.toPointerButton(),
                    )
                }
                when {
                    processResult.anyChangeConsumed -> Win32EventHandlerResult.Stop
                    sendKeyEvent(
                        KeyEvent(
                            key = event.button.toKey(),
                            type = KeyEventType.KeyUp,
                            isCtrlPressed = modifiers.isCtrlPressed,
                            isMetaPressed = modifiers.isMetaPressed,
                            isAltPressed = modifiers.isAltPressed,
                            isShiftPressed = modifiers.isShiftPressed,
                            nativeEvent = event,
                        ),
                    ) -> Win32EventHandlerResult.Stop
                    else -> Win32EventHandlerResult.Continue
                }
            }
            is Win32Event.PointerUpdated -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                val processResult = density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        event,
                    )
                }
                when {
                    processResult.anyChangeConsumed -> Win32EventHandlerResult.Stop
                    else -> Win32EventHandlerResult.Continue
                }
            }
            is Win32Event.PointerEntered -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = true
                val processResult = density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Enter,
                        uptime,
                        event,
                    )
                }
                when {
                    processResult.anyChangeConsumed -> Win32EventHandlerResult.Stop
                    else -> Win32EventHandlerResult.Continue
                }
            }
            is Win32Event.PointerExited -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = false
                val lastPositionInWindow = pointerPosition
                val processResult = density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Exit,
                        uptime,
                        event,
                    )
                }
                // Windows stops delivering pointer movements once the cursor leaves the window, yet
                // hit-test based hover (HitPathTracker) only emits a per-node Exit when a *later*
                // event's hit path no longer contains the node -- the Exit event type alone changes
                // nothing, hover is purely position-driven. On a fast exit the last reported position
                // can still sit on a hovered control (e.g. a caption button), leaving it stuck hovered.
                // Emulate a slight continued movement just outside the window so the hit path empties
                // and the previously hovered nodes reliably receive their Exit. Skip while a button is
                // held, where the pointer is captured and we must keep delivering real positions.
                if (!pointerButtons.areAnyPressed) {
                    density.run {
                        sendSyntheticPointerMovedOutsideWindow(lastPositionInWindow, uptime, event)
                    }
                }
                when {
                    processResult.anyChangeConsumed -> Win32EventHandlerResult.Stop
                    else -> Win32EventHandlerResult.Continue
                }
            }
            is Win32Event.ScrollWheelX -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                val moveResult = density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        event,
                    )
                }
                val scrollResult = density.run {
                    sendPointerEvent.invoke(
                        eventType = PointerEventType.Scroll,
                        position = event.locationInWindow.toDpOffset().toPxOffset(this),
                        scrollDelta = computeWindowsHorizontalScrollDelta(event.scrollingDelta).toPxOffset(this),
                        timeMillis = uptime,
                        type = PointerType.Mouse,
                        buttons = pointerButtons,
                        keyboardModifiers = currentKeyboardModifiers(),
                        nativeEvent = event,
                        button = null,
                    )
                }
                resolveScrollResult(scrollResult, moveResult)
            }
            is Win32Event.ScrollWheelY -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                val moveResult = density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        event,
                    )
                }
                val modifiers = currentKeyboardModifiers()
                val scrollResult = density.run {
                    sendPointerEvent.invoke(
                        eventType = PointerEventType.Scroll,
                        position = event.locationInWindow.toDpOffset().toPxOffset(this),
                        scrollDelta = computeWindowsVerticalScrollDelta(
                            event.scrollingDelta,
                            modifiers.isShiftPressed,
                        ).toPxOffset(this),
                        timeMillis = uptime,
                        type = PointerType.Mouse,
                        buttons = pointerButtons,
                        keyboardModifiers = modifiers,
                        nativeEvent = event,
                        button = null,
                    )
                }
                resolveScrollResult(scrollResult, moveResult)
            }
            is Win32Event.KeyDown -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                val modifiers = currentKeyboardModifiers()
                val keyEvent = KeyEvent(
                    key = event.virtualKey.toKey(),
                    type = KeyEventType.KeyDown,
                    codePoint = event.toUnicode().firstOrNull()?.code ?: 0,
                    isCtrlPressed = modifiers.isCtrlPressed,
                    isMetaPressed = modifiers.isMetaPressed,
                    isAltPressed = modifiers.isAltPressed,
                    isShiftPressed = modifiers.isShiftPressed,
                    nativeEvent = event,
                )
                val handled = sendKeyEvent(keyEvent)
                if (event.virtualKey.isModifier()) {
                    keyboardModifiers = modifiers
                }
                if (handled) Win32EventHandlerResult.Stop else Win32EventHandlerResult.Continue
            }
            is Win32Event.KeyUp -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                val modifiers = currentKeyboardModifiers()
                val keyEvent = KeyEvent(
                    key = event.virtualKey.toKey(),
                    type = KeyEventType.KeyUp,
                    isCtrlPressed = modifiers.isCtrlPressed,
                    isMetaPressed = modifiers.isMetaPressed,
                    isAltPressed = modifiers.isAltPressed,
                    isShiftPressed = modifiers.isShiftPressed,
                    nativeEvent = event,
                )
                val handled = sendKeyEvent(keyEvent)
                if (event.virtualKey.isModifier()) {
                    keyboardModifiers = modifiers
                }
                if (handled) Win32EventHandlerResult.Stop else Win32EventHandlerResult.Continue
            }
            is Win32Event.WindowActivated -> updateStateForWindowActivated(event.active)
            else -> Win32EventHandlerResult.Continue
        }
    }

    /**
     * win32 has no `ModifiersChanged` event, so activation is where a stuck modifier is released:
     * gaining activation re-snapshots the live keyboard, losing it clears everything (otherwise a
     * modifier held while the window is switched away stays pressed forever). Extracted from the
     * event handler so the deactivation branch is testable without a native `Keyboard.getState()`.
     */
    fun updateStateForWindowActivated(active: Boolean): Win32EventHandlerResult {
        keyboardModifiers = if (active) currentKeyboardModifiers() else PointerKeyboardModifiers()
        return Win32EventHandlerResult.Continue
    }

    private fun resolveScrollResult(
        scrollResult: PointerEventResult,
        moveResult: PointerEventResult,
    ): Win32EventHandlerResult = when {
        scrollResult.anyChangeConsumed || moveResult.anyChangeConsumed -> {
            sendPointerInputEventWithCurrentStateIfNecessary(PointerEventType.Move)
            Win32EventHandlerResult.Stop
        }
        inputModeManager.inputMode == InputMode.Keyboard -> {
            inputModeManager.requestInputMode(InputMode.Touch)
            sendPointerInputEventWithCurrentStateIfNecessary(PointerEventType.Move)
            Win32EventHandlerResult.Continue
        }
        else -> Win32EventHandlerResult.Continue
    }

    private fun Density.updatePointerPosition(
        locationInWindow: LogicalPoint,
        pointerEventType: PointerEventType,
        uptime: Long,
        nativeEvent: Win32Event,
    ): PointerEventResult {
        val previousPointerPosition = pointerPosition
        pointerPosition = locationInWindow.toDpOffset().toPxOffset(this)
        return if (
            previousPointerPosition != pointerPosition ||
            pointerEventType == PointerEventType.Enter ||
            pointerEventType == PointerEventType.Exit
        ) {
            sendPointerEvent.invoke(
                eventType = pointerEventType,
                position = pointerPosition!!,
                scrollDelta = Offset.Zero,
                timeMillis = uptime,
                type = PointerType.Mouse,
                buttons = pointerButtons,
                keyboardModifiers = keyboardModifiers,
                nativeEvent = nativeEvent,
                button = null,
            )
        } else {
            PointerEventResult()
        }
    }

    /**
     * Emulates a slight pointer movement just outside the window after the cursor has left it.
     *
     * Windows does not report mouse movements once the cursor is outside the window, but hit-test
     * based hover relies on a movement whose hit path no longer contains a node to emit that node's
     * Exit. We continue the cursor's last in-window motion past the edge (so the position lands
     * outside the root layout) and dispatch it as a [PointerEventType.Move], which empties the hit
     * path and lets the previously hovered nodes receive their Exit.
     */
    private fun Density.sendSyntheticPointerMovedOutsideWindow(
        lastPositionInWindow: Offset?,
        uptime: Long,
        nativeEvent: Win32Event,
    ): PointerEventResult {
        val exitPosition = pointerPosition ?: return PointerEventResult()
        val outsidePosition = computeSyntheticExitPosition(
            exitPosition = exitPosition,
            lastPositionInWindow = lastPositionInWindow,
            minNudgePx = SYNTHETIC_EXIT_NUDGE.toPx(),
        )
        pointerPosition = outsidePosition
        return sendPointerEvent.invoke(
            eventType = PointerEventType.Move,
            position = outsidePosition,
            scrollDelta = Offset.Zero,
            timeMillis = uptime,
            type = PointerType.Mouse,
            buttons = pointerButtons,
            keyboardModifiers = keyboardModifiers,
            nativeEvent = nativeEvent,
            button = null,
        )
    }

    /**
     * We intentionally keep this local-only so Wayland and similar environments don't have to
     * provide screen-space pointer coordinates. That still lets us refresh hit tests after content
     * relayouts inside the same window.
     */
    fun prepareSyntheticPointerEventAfterRelayoutIfNecessary(): PendingSyntheticPointerEventAfterRelayout? {
        val type = when {
            pointerPosition == null -> return null
            pointerButtons.areAnyPressed -> PointerEventType.Move
            !pointerInWindow -> return null
            inputModeManager.inputMode == InputMode.Touch -> PointerEventType.Move
            else -> PointerEventType.Exit
        }
        val generation = syntheticPointerEventAfterRelayoutGeneration + 1
        syntheticPointerEventAfterRelayoutGeneration = generation
        return PendingSyntheticPointerEventAfterRelayout(
            generation = generation,
            type = type,
        )
    }

    fun sendSyntheticPointerEventAfterRelayoutIfCurrent(
        request: PendingSyntheticPointerEventAfterRelayout,
    ): PointerEventResult {
        if (request.generation != syntheticPointerEventAfterRelayoutGeneration) {
            return PointerEventResult()
        }
        return sendPointerInputEventWithCurrentStateIfNecessary(request.type)
    }

    fun sendPointerInputEventWithCurrentStateIfNecessary(
        type: PointerEventType,
        uptime: Long = lastNativeEventUptimeMillis ?: Clock.System.now().toEpochMilliseconds(),
        scrollDelta: Offset = Offset.Zero,
        nativeEvent: Any? = null,
        button: PointerButton? = null,
    ): PointerEventResult {
        val position = pointerPosition
        return if (!pointerInWindow || position == null) {
            PointerEventResult()
        } else {
            sendPointerEvent.invoke(
                eventType = type,
                position = position,
                scrollDelta = if (type == PointerEventType.Scroll) scrollDelta else Offset.Zero,
                timeMillis = uptime,
                type = PointerType.Mouse,
                buttons = pointerButtons,
                keyboardModifiers = keyboardModifiers,
                nativeEvent = nativeEvent,
                button = button,
            )
        }
    }

    private companion object {
        /**
         * How far past the window edge the synthetic "cursor left the window" movement is placed.
         * Acts as a lower bound on the continuation distance; overshooting only lands further
         * outside, never back in.
         */
        val SYNTHETIC_EXIT_NUDGE = 32.dp
    }
}

internal data class PendingSyntheticPointerEventAfterRelayout(
    val generation: Long,
    val type: PointerEventType,
)

/**
 * Continues the cursor's last in-window travel past the window edge so the synthetic exit movement
 * lands outside the root layout. Extended to at least [minNudgePx] so a tiny final travel still
 * clears the edge; with no travel vector to extrapolate the point is pushed above the top edge
 * (y &lt; 0 is always outside the root layout, which starts at y = 0). See
 * [InputStateTracker.sendSyntheticPointerMovedOutsideWindow].
 */
internal fun computeSyntheticExitPosition(
    exitPosition: Offset,
    lastPositionInWindow: Offset?,
    minNudgePx: Float,
): Offset {
    val travel = if (lastPositionInWindow != null) exitPosition - lastPositionInWindow else Offset.Zero
    val distance = travel.getDistance()
    return if (distance > 0f) {
        exitPosition + (travel / distance) * maxOf(distance, minNudgePx)
    } else {
        Offset(exitPosition.x, -minNudgePx)
    }
}

/**
 * Horizontal wheel delta (win32 `ScrollWheelX`): dp-for-detent, no inversion, no axis swap.
 */
internal fun computeWindowsHorizontalScrollDelta(scrollingDelta: Int): DpOffset =
    DpOffset(scrollingDelta.toFloat().dp, 0.dp)

/**
 * Vertical wheel delta (win32 `ScrollWheelY`): the wheel is inverted (content follows the fingers),
 * and Shift redirects it to the horizontal axis (the standard Windows shift-scroll gesture).
 */
internal fun computeWindowsVerticalScrollDelta(scrollingDelta: Int, shiftPressed: Boolean): DpOffset {
    val delta = (-scrollingDelta.toFloat()).dp
    return if (shiftPressed) {
        DpOffset(delta, 0.dp)
    } else {
        DpOffset(0.dp, delta)
    }
}

private fun Win32PointerButton.toPointerButton(): PointerButton = when (this) {
    Win32PointerButton.Left -> PointerButton.Primary
    Win32PointerButton.Right -> PointerButton.Secondary
    Win32PointerButton.Middle -> PointerButton.Tertiary
    Win32PointerButton.XButton1 -> PointerButton.Back
    Win32PointerButton.XButton2 -> PointerButton.Forward
    else -> PointerButton(0)
}

private fun Win32PointerButton.toKey(): Key = when (this) {
    Win32PointerButton.Left -> Key.Button1
    Win32PointerButton.Right -> Key.Button2
    Win32PointerButton.Middle -> Key.Button3
    Win32PointerButton.XButton1 -> Key.Button4
    Win32PointerButton.XButton2 -> Key.Button5
    else -> Key.Unknown
}

private fun VirtualKey.isModifier(): Boolean = when (this) {
    VirtualKey.Control, VirtualKey.LeftControl, VirtualKey.RightControl,
    VirtualKey.Menu, VirtualKey.LeftMenu, VirtualKey.RightMenu,
    VirtualKey.Shift, VirtualKey.LeftShift, VirtualKey.RightShift,
    VirtualKey.LeftWindows, VirtualKey.RightWindows,
        -> true
    else -> false
}

private operator fun PointerButtons.plus(other: PointerButton): PointerButtons = copy(
    isPrimaryPressed = isPrimaryPressed || other.isPrimary,
    isSecondaryPressed = isSecondaryPressed || other.isSecondary,
    isTertiaryPressed = isTertiaryPressed || other.isTertiary,
    isBackPressed = isBackPressed || other.isBack,
    isForwardPressed = isForwardPressed || other.isForward,
)

private operator fun PointerButtons.minus(other: PointerButton): PointerButtons = copy(
    isPrimaryPressed = isPrimaryPressed && !other.isPrimary,
    isSecondaryPressed = isSecondaryPressed && !other.isSecondary,
    isTertiaryPressed = isTertiaryPressed && !other.isTertiary,
    isBackPressed = isBackPressed && !other.isBack,
    isForwardPressed = isForwardPressed && !other.isForward,
)
