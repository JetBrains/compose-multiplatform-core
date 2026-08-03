@file:OptIn(kotlin.time.ExperimentalTime::class)

package androidx.compose.ui.desktop.macos

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
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.EventHandlerResult
import org.jetbrains.desktop.macos.KeyModifiersSet
import org.jetbrains.desktop.macos.LogicalPoint
import org.jetbrains.desktop.macos.MouseButton
import org.jetbrains.desktop.macos.WindowEvent

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

@ExperimentalComposeUiApi
@InternalCoreApi
@InternalComposeUiApi
internal class InputStateTracker(
    private val inputModeManager: InputModeManager,
    private val sendPointerEvent: SendPointerEvent,
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
    private var syntheticPointerEventAfterRelayoutGeneration: Long = 0
    internal var keyboardModifiers by mutableStateOf(PointerKeyboardModifiers())

    fun updateStateAndSendEvents(windowEvent: WindowEvent, density: Density): EventHandlerResult {
        syntheticPointerEventAfterRelayoutGeneration++
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
                val processResult = density.run {
                    sendPointerEvent.invoke(
                        eventType = PointerEventType.Press,
                        position = windowEvent.locationInWindow.toDpOffset().toPxOffset(this),
                        scrollDelta = Offset.Zero,
                        timeMillis = uptime,
                        type = PointerType.Mouse,
                        buttons = pointerButtons,
                        keyboardModifiers = keyboardModifiers,
                        nativeEvent = windowEvent,
                        button = windowEvent.button.toPointerButton(),
                    )
                }
                when {
                    processResult.anyChangeConsumed -> EventHandlerResult.Stop
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
                val processResult = density.run {
                    sendPointerEvent.invoke(
                        eventType = PointerEventType.Release,
                        position = windowEvent.locationInWindow.toDpOffset().toPxOffset(this),
                        scrollDelta = Offset.Zero,
                        timeMillis = uptime,
                        type = PointerType.Mouse,
                        buttons = pointerButtons,
                        keyboardModifiers = keyboardModifiers,
                        nativeEvent = windowEvent,
                        button = windowEvent.button.toPointerButton(),
                    )
                }
                when {
                    processResult.anyChangeConsumed -> EventHandlerResult.Stop
                    sendKeyEvent(windowEvent.toKeyEvent(keyboardModifiers)) -> EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }
            }
            is Event.MouseMoved -> {
                // AppKit keeps delivering mouseMoved to the key window even when another window
                // (or the Dock) genuinely occludes the cursor. A Move only refines a position
                // already established by a real Enter - without that, there's nothing to trust
                // it against, so it's dropped rather than used to synthesize presence.
                if (!pointerInWindow) {
                    EventHandlerResult.Continue
                } else {
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
                val moveResult = density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        windowEvent,
                    )
                }
                val scrollResult = density.run {
                    sendPointerEvent.invoke(
                        eventType = PointerEventType.Scroll,
                        position = windowEvent.locationInWindow.toDpOffset().toPxOffset(this),
                        scrollDelta = windowEvent.delta().toPxOffset(this),
                        timeMillis = uptime,
                        type = PointerType.Mouse,
                        buttons = pointerButtons,
                        keyboardModifiers = keyboardModifiers,
                        nativeEvent = windowEvent,
                        button = null,
                    )
                }
                when {
                    scrollResult.anyChangeConsumed || moveResult.anyChangeConsumed -> {
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
                if (sendKeyEvent(keyEvent)) EventHandlerResult.Stop else EventHandlerResult.Continue
            }
            is Event.KeyUp -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                updateModifierState(windowEvent.modifiers, windowEvent)
                val keyEvent = windowEvent.toKeyEvent()
                if (sendKeyEvent(keyEvent)) EventHandlerResult.Stop else EventHandlerResult.Continue
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
            is Event.Swipe -> {
                val uptime = windowEvent.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                density.run {
                    updatePointerPosition(
                        windowEvent.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        windowEvent,
                    )
                }

                val button = windowEvent.toBackForwardButton()
                    ?: return EventHandlerResult.Continue
                val pointerButton = button.toPointerButton()
                val position = density.run {
                    windowEvent.locationInWindow.toDpOffset().toPxOffset(this)
                }

                val downProcessResult = sendPointerEvent.invoke(
                    eventType = PointerEventType.Press,
                    position = position,
                    scrollDelta = Offset.Zero,
                    timeMillis = uptime,
                    type = PointerType.Mouse,
                    buttons = pointerButtons + pointerButton,
                    keyboardModifiers = keyboardModifiers,
                    nativeEvent = windowEvent,
                    button = pointerButton,
                )
                val downResult = when {
                    downProcessResult.anyChangeConsumed -> EventHandlerResult.Stop
                    sendKeyEvent(
                        windowEvent.toKeyEvent(button, KeyEventType.KeyDown, keyboardModifiers),
                    ) -> EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }

                val upProcessResult = sendPointerEvent.invoke(
                    eventType = PointerEventType.Release,
                    position = position,
                    scrollDelta = Offset.Zero,
                    timeMillis = uptime,
                    type = PointerType.Mouse,
                    buttons = pointerButtons,
                    keyboardModifiers = keyboardModifiers,
                    nativeEvent = windowEvent,
                    button = pointerButton,
                )
                val upResult = when {
                    upProcessResult.anyChangeConsumed -> EventHandlerResult.Stop
                    sendKeyEvent(
                        windowEvent.toKeyEvent(button, KeyEventType.KeyUp, keyboardModifiers),
                    ) -> EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }

                when {
                    downResult == EventHandlerResult.Stop || upResult == EventHandlerResult.Stop ->
                        EventHandlerResult.Stop
                    else -> EventHandlerResult.Continue
                }
            }
            else -> EventHandlerResult.Continue
        }
    }

    private fun Density.updatePointerPosition(
        locationInWindow: LogicalPoint,
        pointerEventType: PointerEventType,
        uptime: Long,
        nativeEvent: WindowEvent,
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

    /**
     * We intentionally keep this local-only so Wayland and similar environments don't have to
     * provide screen-space pointer coordinates. That still lets us refresh hit tests after content
     * relayouts inside the same window.
     */
    fun prepareSyntheticPointerEventAfterRelayoutIfNecessary(): PendingSyntheticPointerEventAfterRelayout? {
        if (pointerPosition == null) {
            return null
        }
        val type = when {
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
    ) {
        if (request.generation != syntheticPointerEventAfterRelayoutGeneration) {
            return
        }
        sendPointerInputEventWithCurrentStateIfNecessary(request.type)
    }

    fun sendPointerInputEventWithCurrentStateIfNecessary(
        type: PointerEventType,
        uptime: Long = lastNativeEventUptimeMillis ?: Clock.System.now().toEpochMilliseconds(),
        scrollDelta: Offset = Offset.Zero,
        nativeEvent: Any? = null,
        button: PointerButton? = null,
    ): PointerEventResult {
        return if (!pointerInWindow || pointerPosition == null) {
            PointerEventResult()
        } else {
            sendPointerEvent.invoke(
                eventType = type,
                position = pointerPosition!!,
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
}


internal data class PendingSyntheticPointerEventAfterRelayout(
    val generation: Long,
    val type: PointerEventType,
)

private fun Event.Swipe.toBackForwardButton(): MouseButton? = when {
    deltaX > 0.0 -> MouseButton.BACK
    deltaX < 0.0 -> MouseButton.FORWARD
    else -> null
}

private fun MouseButton.toPointerButton(): PointerButton = when (this) {
    MouseButton.LEFT -> PointerButton.Primary
    MouseButton.RIGHT -> PointerButton.Secondary
    MouseButton.MIDDLE -> PointerButton.Tertiary
    else -> PointerButton(value)
}

/**
 * Local copy of the bit-mask layout used by [PointerKeyboardModifiers.packedValue] in
 * `PointerEvent.skiko.kt` (that [ModifierMasks] object is file-private there). Kept in sync with
 * that file.
 */
private object ModifierMasks {
    const val CtrlPressed = 1 shl 0
    const val MetaPressed = 1 shl 1
    const val AltPressed = 1 shl 2
    const val AltGraphPressed = 1 shl 3
    const val SymPressed = 1 shl 4
    const val ShiftPressed = 1 shl 5
    const val FunctionPressed = 1 shl 6
    const val CapsLockOn = 1 shl 7
    const val ScrollLockOn = 1 shl 8
    const val NumLockOn = 1 shl 9

    val all = intArrayOf(
        CtrlPressed, MetaPressed, AltPressed, AltGraphPressed, ShiftPressed,
        FunctionPressed, CapsLockOn, ScrollLockOn, NumLockOn,
    )
}

private fun PointerKeyboardModifiers.forEachPressOrReleaseTo(
    result: PointerKeyboardModifiers,
    block: (KeyEventType, Key) -> Unit,
) {
    for (modifierMask in ModifierMasks.all) {
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
    ModifierMasks.CtrlPressed -> Key.CtrlLeft
    ModifierMasks.MetaPressed -> Key.MetaLeft
    ModifierMasks.AltPressed -> Key.AltLeft
    ModifierMasks.AltGraphPressed -> Key.AltRight
    ModifierMasks.ShiftPressed -> Key.ShiftLeft
    ModifierMasks.FunctionPressed -> Key.Function
    ModifierMasks.CapsLockOn -> Key.CapsLock
    ModifierMasks.ScrollLockOn -> Key.ScrollLock
    ModifierMasks.NumLockOn -> Key.NumLock
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

private fun Event.ScrollWheel.delta(): DpOffset {
    val directionMultiplier = -1f // scroll is inverted on macos
    val yScaleMultiplier = if (hasPreciseScrillingDeltas) {
        0.1f
    } else {
        // According to the macOS documentation, it should be the height of editor line
        // but for now we have a constant hardcoded here...
        1f
    }
    val xScaleMultiplier = if (hasPreciseScrillingDeltas) {
        0.1f
    } else {
        // According to the macOS documentation, it should be some multiplier, based on character width, I guess
        1f
    }
    return DpOffset(
        (directionMultiplier * xScaleMultiplier * scrollingDeltaX).dp,
        (directionMultiplier * yScaleMultiplier * scrollingDeltaY).dp,
    )
}
