package androidx.compose.ui.kdt.macos

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
import androidx.compose.ui.input.pointer.KeyboardModifierMasks
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import noria.ui.input.pointer.ProcessResult
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.EventHandlerResult
import org.jetbrains.desktop.macos.KeyModifiersSet
import org.jetbrains.desktop.macos.LogicalPoint
import org.jetbrains.desktop.macos.MouseButton
import org.jetbrains.desktop.macos.WindowEvent

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

    fun updateStateAndSendEvents(windowEvent: WindowEvent, density: Density): EventHandlerResult {
        return when (windowEvent) {
            is Event.MouseDown -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
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
                pointerButtons += windowEvent.button.toPointerButton()
                val processResult = sendPointerInputEvent(
                    windowEvent.toPointerInputEvent(
                        pointerButtons,
                        keyboardModifiers,
                        density,
                    ),
                )
                when {
                    processResult.anyPressOrReleaseConsumed -> EventHandlerResult.Stop
                    sendKeyEvent(windowEvent.toKeyEvent(keyboardModifiers)) -> EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }
            }
            is Event.MouseUp -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
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
                pointerButtons -= windowEvent.button.toPointerButton()
                val processResult = sendPointerInputEvent(
                    windowEvent.toPointerInputEvent(
                        pointerButtons,
                        keyboardModifiers,
                        density,
                    ),
                )
                when {
                    processResult.anyPressOrReleaseConsumed -> EventHandlerResult.Stop
                    sendKeyEvent(windowEvent.toKeyEvent(keyboardModifiers)) -> EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }
            }
            is Event.MouseMoved -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerButtons = PointerButtons()
                val processResult = density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        windowEvent,
                    )
                }
                when {
                    processResult.anyChangeConsumed -> EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }
            }
            is Event.MouseDragged -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerButtons += windowEvent.button.toPointerButton()
                val processResult = density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        windowEvent,
                    )
                }
                when {
                    processResult.anyChangeConsumed -> EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }
            }
            is Event.MouseEntered -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = true
                val processResult = density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Enter,
                        uptime,
                        windowEvent,
                    )
                }
                when {
                    processResult.anyChangeConsumed -> EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }
            }
            is Event.MouseExited -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = false
                val processResult = density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Exit,
                        uptime,
                        windowEvent,
                    )
                }
                when {
                    processResult.anyChangeConsumed -> EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }
            }
            is Event.ScrollWheel -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                var processResult = density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        windowEvent,
                    )
                }
                processResult += sendPointerInputEvent(
                    windowEvent.toPointerInputEvent(
                        buttons = pointerButtons,
                        keyboardModifiers = keyboardModifiers,
                        density = density,
                        windowEvent = windowEvent,
                    ),
                )
                when {
                    processResult.anyScrollingConsumed -> {
                        sendPointerInputEventWithCurrentStateIfNecessary(PointerEventType.Move)
                        EventHandlerResult.Stop
                    }
                    inputModeManager.inputMode == InputMode.Keyboard -> {
                        // todo[unterhofer] This won't work with the hide stack in macOS
                        inputModeManager.requestInputMode(InputMode.Touch)
                        sendPointerInputEventWithCurrentStateIfNecessary(PointerEventType.Move)
                        EventHandlerResult.Continue
                    }
                    else -> {
                        EventHandlerResult.Continue
                    }
                }
            }
            is Event.KeyDown -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                updateModifierState(windowEvent.modifiers, windowEvent)
                val keyEvent = windowEvent.toKeyEvent()
                val handled = sendKeyEvent(keyEvent)
                if (handled) {
                    EventHandlerResult.Stop
                } else {
                    EventHandlerResult.Continue
                }
            }
            is Event.KeyUp -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                updateModifierState(windowEvent.modifiers, windowEvent)
                val keyEvent = windowEvent.toKeyEvent()
                val handled = sendKeyEvent(keyEvent)
                if (handled) {
                    EventHandlerResult.Stop
                } else {
                    EventHandlerResult.Continue
                }
            }
            is Event.ModifiersChanged -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                updateModifierState(windowEvent.modifiers, windowEvent)
                if (inputModeManager.inputMode == InputMode.Touch) {
                    sendPointerInputEventWithCurrentStateIfNecessary(PointerEventType.Move)
                }
                EventHandlerResult.Continue
            }
            is Event.WindowFocusChange -> {
                if (windowEvent.isKeyWindow) {
                    updateModifierState(Event.pressedModifiers(), windowEvent)
                } else {
                    /**
                     * We need to release all pressed modifiers when a window loses focus, otherwise it will stick forever.
                     * if you want to change this logic, please check the following scenario:
                     * - Focus window
                     * - Press and hold Cmd
                     * - Press Tab
                     * - Focus window with mouse or with a short press of Cmd + Tab
                     */
                    updateModifierState(KeyModifiersSet.create(), windowEvent)
                }
                EventHandlerResult.Continue
            }
            else -> EventHandlerResult.Continue
        }
    }

    private fun Density.updatePointerPosition(
        locationInWindow: LogicalPoint,
        pointerEventType: PointerEventType,
        uptime: Long,
        nativeEvent: WindowEvent,
    ): ProcessResult {
        val previousPointerPosition = pointerPosition
        pointerPosition = locationInWindow.toDpOffset().toOffset()
        return if (
            previousPointerPosition != pointerPosition ||
            pointerEventType == PointerEventType.Enter ||
            pointerEventType == PointerEventType.Exit
        ) {
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

    private fun updateModifierState(modifiers: KeyModifiersSet, windowEvent: WindowEvent) {
        val previousKeyboardModifiers = keyboardModifiers
        keyboardModifiers = modifiers.toPointerKeyboardModifiers()
        if (previousKeyboardModifiers != keyboardModifiers) {
            previousKeyboardModifiers.forEachPressOrReleaseTo(keyboardModifiers) { keyEventType, key ->
                sendKeyEvent(
                    KeyEvent(
                        key = key,
                        type = keyEventType,
                        isCtrlPressed = previousKeyboardModifiers.isCtrlPressed,
                        isMetaPressed = previousKeyboardModifiers.isMetaPressed,
                        isAltPressed = previousKeyboardModifiers.isAltPressed,
                        isShiftPressed = previousKeyboardModifiers.isShiftPressed,
                        nativeEvent = windowEvent,
                    ),
                )
            }
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
        uptime = timestamp.toDuration().inWholeMilliseconds,
        position = density.run { locationInWindow.toDpOffset().toOffset() },
        buttons = buttons,
        scrollDelta = Offset.Zero,
        keyboardModifiers = keyboardModifiers,
        button = button.toPointerButton(),
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
        uptime = timestamp.toDuration().inWholeMilliseconds,
        position = density.run { locationInWindow.toDpOffset().toOffset() },
        buttons = buttons,
        scrollDelta = Offset.Zero,
        keyboardModifiers = keyboardModifiers,
        button = button.toPointerButton(),
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

private fun MouseButton.toPointerButton(): PointerButton = when (this) {
    MouseButton.LEFT -> PointerButton.Primary
    MouseButton.RIGHT -> PointerButton.Secondary
    MouseButton.MIDDLE -> PointerButton.Tertiary
    else -> PointerButton(value)
}

private fun PointerKeyboardModifiers.forEachPressOrReleaseTo(
    result: PointerKeyboardModifiers,
    block: (KeyEventType, Key) -> Unit,
) {
    for (modifierMask in KeyboardModifierMasks.all) {
        when {
            (packedValue and modifierMask) == 0 && (result.packedValue and modifierMask) != 0 -> {
                block(KeyEventType.KeyDown, modifierMask.toKey())
            }
            (packedValue and modifierMask) != 0 && (result.packedValue and modifierMask) == 0 -> {
                block(KeyEventType.KeyUp, modifierMask.toKey())
            }
        }
    }
}

private fun Int.toKey(): Key = when (this) {
    KeyboardModifierMasks.CtrlPressed -> Key.CtrlLeft
    KeyboardModifierMasks.MetaPressed -> Key.MetaLeft
    KeyboardModifierMasks.AltPressed -> Key.AltLeft
    KeyboardModifierMasks.AltGraphPressed -> Key.AltRight
    KeyboardModifierMasks.ShiftPressed -> Key.ShiftLeft
    KeyboardModifierMasks.FunctionPressed -> Key.Function
    KeyboardModifierMasks.CapsLockOn -> Key.CapsLock
    KeyboardModifierMasks.ScrollLockOn -> Key.ScrollLock
    KeyboardModifierMasks.NumLockOn -> Key.NumLock
    else -> throw IllegalArgumentException("Unknown modifier mask: $this")
}

private fun KeyModifiersSet.toPointerKeyboardModifiers(): PointerKeyboardModifiers {
    return PointerKeyboardModifiers(
        isCtrlPressed = control,
        isMetaPressed = command,
        isAltPressed = option,
        isShiftPressed = shift,
        isAltGraphPressed = false,
        isSymPressed = false,
        isScrollLockOn = false,
        // These below are deprecated and behave unexpectedly in many cases. Might not be a good
        // idea to support them in Compose.
        isFunctionPressed = function,
        isCapsLockOn = capsLock,
        isNumLockOn = numericPad,
    )
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

@OptIn(InternalCoreApi::class)
private fun Event.ScrollWheel.toPointerInputEvent(
    buttons: PointerButtons,
    keyboardModifiers: PointerKeyboardModifiers,
    density: Density,
    windowEvent: WindowEvent,
): PointerInputEvent {
    return mousePointerInputEvent(
        type = PointerEventType.Scroll,
        uptime = timestamp.toDuration().inWholeMilliseconds,
        position = density.run { locationInWindow.toDpOffset().toOffset() },
        buttons = buttons,
        scrollDelta = density.run { delta().toOffset() },
        keyboardModifiers = keyboardModifiers,
        button = null,
        nativeEvent = windowEvent,
    )
}

private fun Event.ScrollWheel.delta(): DpOffset {
    val directionMultiplier = -1f // scroll is inverted on macos
    val yScaleMultiplier = if (hasPreciseScrillingDeltas) {
        1f
    } else {
        // According to the macOS documentation, it should be the height of editor line
        // but for now we have a constant hardcoded here...
        10f
    }
    val xScaleMultiplier = if (hasPreciseScrillingDeltas) {
        1f
    } else {
        // According to the macOS documentation, it should be some multiplier, based on character width, I guess
        10f
    }
    return DpOffset(
        (directionMultiplier * xScaleMultiplier * scrollingDeltaX).dp,
        (directionMultiplier * yScaleMultiplier * scrollingDeltaY).dp,
    )
}
