package androidx.compose.ui.kdt.headless

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.input.pointer.PointerInputEventData
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.copy
import androidx.compose.ui.input.pointer.isBack
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForward
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isPrimary
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiary
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import noria.ui.input.pointer.ProcessResult

@ExperimentalComposeUiApi
@InternalCoreApi
internal class InputStateTracker(
    private val inputModeManager: InputModeManager,
    private val sendPointerInputEvent: (PointerInputEvent) -> ProcessResult,
    private val sendKeyEvent: (KeyEvent) -> Boolean,
) {
    /**
     * With potentially overlapping windows, this can't be determined by a simple bounds check,
     * and we need to keep track of Enter/Exit events instead.
     */
    private var pointerInWindow: Boolean = false
    private var pointerPosition: Offset? = null
    private var lastNativeEventUptimeMillis: Long? = null
    private var pointerButtons = PointerButtons()
    internal var keyboardModifiers by mutableStateOf(PointerKeyboardModifiers())

    fun updateStateAndSendEvents(windowEvent: WindowEvent, density: Density) {
        when (windowEvent) {
            is Event.MouseDown -> {
                val uptime = windowEvent.timestamp.seconds.inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        windowEvent,
                    )
                }
                pointerButtons += windowEvent.button
                val processResult = sendPointerInputEvent(
                    windowEvent.toPointerInputEvent(
                        pointerButtons,
                        keyboardModifiers,
                        density,
                    ),
                )
                if (!processResult.anyPressOrReleaseConsumed) {
                    sendKeyEvent(windowEvent.toKeyEvent(keyboardModifiers))
                }
            }
            is Event.MouseUp -> {
                val uptime = windowEvent.timestamp.seconds.inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        windowEvent,
                    )
                }
                pointerButtons -= windowEvent.button
                val processResult = sendPointerInputEvent(
                    windowEvent.toPointerInputEvent(
                        pointerButtons,
                        keyboardModifiers,
                        density,
                    ),
                )
                if (!processResult.anyPressOrReleaseConsumed) {
                    sendKeyEvent(windowEvent.toKeyEvent(keyboardModifiers))
                }
            }
            is Event.MouseMoved -> {
                val uptime = windowEvent.timestamp.seconds.inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerButtons = PointerButtons()
                density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        windowEvent,
                    )
                }
            }
            is Event.MouseEntered -> {
                val uptime = windowEvent.timestamp.seconds.inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = true
                density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Enter,
                        uptime,
                        windowEvent,
                    )
                }
            }
            is Event.MouseExited -> {
                val uptime = windowEvent.timestamp.seconds.inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = false
                density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Exit,
                        uptime,
                        windowEvent,
                    )
                }
            }
            is Event.KeyDown -> {
                updateModifierState(windowEvent)
                sendKeyEvent(windowEvent.toKeyEvent(keyboardModifiers))
            }
            is Event.KeyUp -> {
                updateModifierState(windowEvent)
                sendKeyEvent(windowEvent.toKeyEvent(keyboardModifiers))
            }
        }
    }

    private fun Density.updatePointerPosition(
        locationInWindow: DpOffset,
        pointerEventType: PointerEventType,
        uptime: Long,
        nativeEvent: WindowEvent,
    ): ProcessResult {
        val previousPointerPosition = pointerPosition
        pointerPosition = locationInWindow.toOffset()
        return if (previousPointerPosition != pointerPosition) {
            sendPointerInputEvent(
                mousePointerInputEvent(
                    type = pointerEventType,
                    uptime = uptime,
                    position = pointerPosition!!,
                    buttons = pointerButtons,
                    scrollDelta = Offset.Zero,
                    keyboardModifiers = keyboardModifiers,
                    button = null,
                    nativeEvent = nativeEvent,
                ),
            )
        } else {
            ProcessResult(0)
        }
    }

    private fun updateModifierState(windowEvent: WindowEvent) {
        when (windowEvent) {
            is Event.KeyDown -> {
                keyboardModifiers = keyboardModifiers.update(windowEvent.key, KeyEventType.KeyDown)
            }
            is Event.KeyUp -> {
                keyboardModifiers = keyboardModifiers.update(windowEvent.key, KeyEventType.KeyUp)
            }
            else -> {} // do nothing
        }
    }

    fun sendPointerInputEventWithCurrentStateIfNecessary(
        type: PointerEventType,
        uptime: Long = lastNativeEventUptimeMillis ?: Clock.System.now().toEpochMilliseconds(),
        scrollDelta: Offset = Offset.Zero,
        nativeEvent: Any? = null,
        button: PointerButton? = null,
    ): ProcessResult {
        return if (!pointerInWindow || pointerPosition == null) {
            ProcessResult(0)
        } else {
            sendPointerInputEvent(
                pointerInputEventWithCurrentState(
                    type,
                    uptime,
                    scrollDelta,
                    nativeEvent,
                    button,
                ),
            )
        }
    }

    private fun pointerInputEventWithCurrentState(
        type: PointerEventType,
        uptime: Long,
        scrollDelta: Offset,
        nativeEvent: Any?,
        button: PointerButton?,
    ): PointerInputEvent {
        return mousePointerInputEvent(
            type = type,
            uptime = uptime,
            position = pointerPosition!!,
            scrollDelta = if (type == PointerEventType.Scroll) scrollDelta else Offset.Zero,
            buttons = pointerButtons,
            keyboardModifiers = keyboardModifiers,
            nativeEvent = nativeEvent,
            button = button,
        )
    }
}

@OptIn(InternalCoreApi::class)
private fun Event.MouseDown.toPointerInputEvent(
    buttons: PointerButtons,
    keyboardModifiers: PointerKeyboardModifiers,
    density: Density,
): PointerInputEvent {
    return mousePointerInputEvent(
        type = PointerEventType.Press,
        uptime = timestamp.seconds.inWholeMilliseconds,
        position = density.run { locationInWindow.toOffset() },
        buttons = buttons,
        scrollDelta = Offset.Zero,
        keyboardModifiers = keyboardModifiers,
        button = button,
        nativeEvent = this,
    )
}

@OptIn(InternalCoreApi::class)
private fun Event.MouseUp.toPointerInputEvent(
    buttons: PointerButtons,
    keyboardModifiers: PointerKeyboardModifiers,
    density: Density,
): PointerInputEvent {
    return mousePointerInputEvent(
        type = PointerEventType.Release,
        uptime = timestamp.seconds.inWholeMilliseconds,
        position = density.run { locationInWindow.toOffset() },
        buttons = buttons,
        scrollDelta = Offset.Zero,
        keyboardModifiers = keyboardModifiers,
        button = button,
        nativeEvent = this,
    )
}

@OptIn(InternalCoreApi::class, ExperimentalComposeUiApi::class)
private fun mousePointerInputEvent(
    type: PointerEventType,
    uptime: Long,
    position: Offset,
    buttons: PointerButtons,
    scrollDelta: Offset,
    keyboardModifiers: PointerKeyboardModifiers,
    button: PointerButton?,
    nativeEvent: Any?,
): PointerInputEvent {
    return PointerInputEvent(
        eventType = type,
        uptime = uptime,
        pointers = listOf(
            PointerInputEventData(
                id = DefaultPointerId,
                uptime = uptime,
                positionOnScreen = Offset.Unspecified,
                position = position,
                down = buttons.areAnyPressed,
                pressure = 1f,
                type = PointerType.Mouse,
                scrollDelta = scrollDelta,
            ),
        ),
        buttons = buttons,
        keyboardModifiers = keyboardModifiers,
        nativeEvent = nativeEvent,
        button = button,
    )
}

private val DefaultPointerId = PointerId(0)

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

private fun PointerKeyboardModifiers.update(
    key: Key,
    eventType: KeyEventType,
): PointerKeyboardModifiers {
    val pressed = when (eventType) {
        KeyEventType.KeyDown -> true
        KeyEventType.KeyUp -> false
        else -> return this
    }
    return when (key) {
        Key.CtrlLeft, Key.CtrlRight -> copy(isCtrlPressed = pressed)
        Key.MetaLeft, Key.MetaRight -> copy(isMetaPressed = pressed)
        Key.AltLeft, Key.AltRight -> copy(isAltPressed = pressed)
        Key.ShiftLeft, Key.ShiftRight -> copy(isShiftPressed = pressed)
        // There is no binding in common for AltGraph
        Key.Symbol -> copy(isSymPressed = pressed)
        Key.Function -> copy(isFunctionPressed = pressed)
        Key.CapsLock -> copy(isCapsLockOn = pressed)
        Key.ScrollLock -> copy(isScrollLockOn = pressed)
        Key.NumLock -> copy(isNumLockOn = pressed)
        else -> this
    }
}
