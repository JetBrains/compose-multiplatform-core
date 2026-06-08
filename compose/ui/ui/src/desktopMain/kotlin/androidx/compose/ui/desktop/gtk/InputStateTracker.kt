@file:Suppress("DuplicatedCode")
@file:OptIn(InternalComposeUiApi::class)

package androidx.compose.ui.desktop.gtk

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
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.input.pointer.PointerInputEventData
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.copy
import androidx.compose.ui.input.pointer.isBack
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isAltPressed
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
import org.jetbrains.desktop.gtk.Event
import org.jetbrains.desktop.gtk.MouseButton

@ExperimentalComposeUiApi
@InternalCoreApi
internal class InputStateTracker(
    private val inputModeManager: InputModeManager,
    private val sendPointerInputEvent: (PointerInputEvent) -> PointerEventResult,
    private val sendKeyEvent: (KeyEvent) -> Boolean,
) {
    private var pointerInWindow: Boolean = false
    private var pointerPosition: Offset? = null
    private var lastNativeEventUptimeMillis: Long? = null
    private var pointerButtons = PointerButtons()
    internal var keyboardModifiers by mutableStateOf(PointerKeyboardModifiers())

    fun updateStateAndSendEvents(
        event: Event,
        density: Density,
    ): org.jetbrains.desktop.gtk.EventHandlerResult {
        return when (event) {
            is Event.MouseDown -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                pointerInWindow = true
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                density.updatePointerPosition(event.locationInWindow.toDpOffset(), PointerEventType.Move, uptime, event)
                pointerButtons += event.button.toPointerButton()
                val modifiers = keyboardModifiers
                val result = sendPointerInputEvent(
                    mousePointerInputEvent(
                        type = PointerEventType.Press,
                        uptime = uptime,
                        position = event.locationInWindow.toDpOffset().toPxOffset(density),
                        buttons = pointerButtons,
                        scrollDelta = Offset.Zero,
                        keyboardModifiers = modifiers,
                        button = event.button.toPointerButton(),
                        nativeEvent = event,
                    ),
                )
                when {
                    result.anyChangeConsumed -> org.jetbrains.desktop.gtk.EventHandlerResult.Stop
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
                    ) -> org.jetbrains.desktop.gtk.EventHandlerResult.Stop
                    else -> org.jetbrains.desktop.gtk.EventHandlerResult.Continue
                }
            }

            is Event.MouseUp -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                pointerInWindow = true
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                density.updatePointerPosition(event.locationInWindow.toDpOffset(), PointerEventType.Move, uptime, event)
                pointerButtons -= event.button.toPointerButton()
                val modifiers = keyboardModifiers
                val result = sendPointerInputEvent(
                    mousePointerInputEvent(
                        type = PointerEventType.Release,
                        uptime = uptime,
                        position = event.locationInWindow.toDpOffset().toPxOffset(density),
                        buttons = pointerButtons,
                        scrollDelta = Offset.Zero,
                        keyboardModifiers = modifiers,
                        button = event.button.toPointerButton(),
                        nativeEvent = event,
                    ),
                )
                when {
                    result.anyChangeConsumed -> org.jetbrains.desktop.gtk.EventHandlerResult.Stop
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
                    ) -> org.jetbrains.desktop.gtk.EventHandlerResult.Stop
                    else -> org.jetbrains.desktop.gtk.EventHandlerResult.Continue
                }
            }

            is Event.MouseMoved -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                pointerInWindow = true
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                val result =
                    density.updatePointerPosition(event.locationInWindow.toDpOffset(), PointerEventType.Move, uptime, event)
                if (result.anyChangeConsumed) {
                    org.jetbrains.desktop.gtk.EventHandlerResult.Stop
                } else {
                    org.jetbrains.desktop.gtk.EventHandlerResult.Continue
                }
            }

            is Event.MouseEntered -> {
                val uptime = now()
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = true
                val result =
                    density.updatePointerPosition(event.locationInWindow.toDpOffset(), PointerEventType.Enter, uptime, event)
                if (result.anyChangeConsumed) {
                    org.jetbrains.desktop.gtk.EventHandlerResult.Stop
                } else {
                    org.jetbrains.desktop.gtk.EventHandlerResult.Continue
                }
            }

            is Event.MouseExited -> {
                val uptime = now()
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = true
                val result = sendPointerInputEventWithCurrentStateIfNecessary(
                    PointerEventType.Exit,
                    uptime = uptime,
                    nativeEvent = event,
                )
                pointerInWindow = false
                if (result.anyChangeConsumed) {
                    org.jetbrains.desktop.gtk.EventHandlerResult.Stop
                } else {
                    org.jetbrains.desktop.gtk.EventHandlerResult.Continue
                }
            }

            is Event.ScrollWheel -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                density.updatePointerPosition(pointerPosition?.toDpOffset(density) ?: DpOffset.Zero, PointerEventType.Move, uptime, event)
                val result = sendPointerInputEventWithCurrentStateIfNecessary(
                    PointerEventType.Scroll,
                    uptime = uptime,
                    scrollDelta = DpOffset(
                        (event.scrollingDeltaX * 20).dp,
                        (event.scrollingDeltaY * 20).dp,
                    ).toPxOffset(density),
                    nativeEvent = event,
                )
                if (result.anyChangeConsumed) {
                    sendPointerInputEventWithCurrentStateIfNecessary(PointerEventType.Move)
                    org.jetbrains.desktop.gtk.EventHandlerResult.Stop
                } else {
                    org.jetbrains.desktop.gtk.EventHandlerResult.Continue
                }
            }

            is Event.KeyDown -> {
                lastNativeEventUptimeMillis = now()
                keyboardModifiers = event.modifiers.toPointerKeyboardModifiers()
                val handled = sendKeyEvent(
                    KeyEvent(
                        key = event.key.toKey(),
                        type = KeyEventType.KeyDown,
                        codePoint = run {
                            val raw = event.characters?.firstCodePointOrNull() ?: 0
                            // GTK/X11 produces the unmodified char for Ctrl+letter (e.g. 'f' = 0x66 for
                            // Ctrl+F) instead of the ISO control char (e.g. 0x06) that other platforms
                            // produce. Normalize here so Compose code is cross-platform consistent.
                            // AltGr (Ctrl+Alt) is intentionally exempt — it produces real typeable chars.
                            if (keyboardModifiers.isCtrlPressed && !keyboardModifiers.isAltPressed && raw in 0x40..0x7E) {
                                raw and 0x1F
                            } else {
                                raw
                            }
                        },
                        isCtrlPressed = keyboardModifiers.isCtrlPressed,
                        isMetaPressed = keyboardModifiers.isMetaPressed,
                        isAltPressed = keyboardModifiers.isAltPressed,
                        isShiftPressed = keyboardModifiers.isShiftPressed,
                        nativeEvent = event,
                    ),
                )
                if (handled) org.jetbrains.desktop.gtk.EventHandlerResult.Stop
                else org.jetbrains.desktop.gtk.EventHandlerResult.Continue
            }

            is Event.KeyUp -> {
                lastNativeEventUptimeMillis = now()
                val handled = sendKeyEvent(
                    KeyEvent(
                        key = event.key.toKey(),
                        type = KeyEventType.KeyUp,
                        isCtrlPressed = keyboardModifiers.isCtrlPressed,
                        isMetaPressed = keyboardModifiers.isMetaPressed,
                        isAltPressed = keyboardModifiers.isAltPressed,
                        isShiftPressed = keyboardModifiers.isShiftPressed,
                        nativeEvent = event,
                    ),
                )
                if (handled) org.jetbrains.desktop.gtk.EventHandlerResult.Stop
                else org.jetbrains.desktop.gtk.EventHandlerResult.Continue
            }

            is Event.ModifiersChanged -> {
                keyboardModifiers = event.modifiers.toPointerKeyboardModifiers()
                org.jetbrains.desktop.gtk.EventHandlerResult.Continue
            }

            is Event.WindowKeyboardEnter -> {
                keyboardModifiers = PointerKeyboardModifiers()
                pointerButtons = PointerButtons()
                org.jetbrains.desktop.gtk.EventHandlerResult.Continue
            }

            is Event.WindowKeyboardLeave -> {
                keyboardModifiers = PointerKeyboardModifiers()
                pointerButtons = PointerButtons()
                org.jetbrains.desktop.gtk.EventHandlerResult.Continue
            }

            else -> org.jetbrains.desktop.gtk.EventHandlerResult.Continue
        }
    }

    fun clearPointerButtons() {
        pointerButtons = PointerButtons()
    }

    private fun Density.updatePointerPosition(
        positionInWindow: DpOffset,
        pointerEventType: PointerEventType,
        uptime: Long,
        nativeEvent: Event,
    ): PointerEventResult {
        lastNativeEventUptimeMillis = uptime
        val previous = pointerPosition
        pointerPosition = positionInWindow.toPxOffset(this)
        return if (
            previous != pointerPosition ||
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
            PointerEventResult()
        }
    }

    fun sendPointerInputEventWithCurrentStateIfNecessary(
        type: PointerEventType,
        uptime: Long = lastNativeEventUptimeMillis ?: now(),
        scrollDelta: Offset = Offset.Zero,
        nativeEvent: Any? = null,
        button: PointerButton? = null,
    ): PointerEventResult {
        val position = pointerPosition
        return if (!pointerInWindow || position == null) {
            PointerEventResult()
        } else {
            sendPointerInputEvent(
                mousePointerInputEvent(
                    type = type,
                    uptime = uptime,
                    position = position,
                    scrollDelta = if (type == PointerEventType.Scroll) scrollDelta else Offset.Zero,
                    buttons = pointerButtons,
                    keyboardModifiers = keyboardModifiers,
                    nativeEvent = nativeEvent,
                    button = button,
                ),
            )
        }
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun now(): Long = Clock.System.now().toEpochMilliseconds().also {
        lastNativeEventUptimeMillis = it
    }
}

private val DefaultPointerId = PointerId(0)

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
                scaleGestureFactor = 1f,
                panGestureOffset = Offset.Zero,
            ),
        ),
        buttons = buttons,
        keyboardModifiers = keyboardModifiers,
        nativeEvent = nativeEvent,
        button = button,
    )
}

private fun MouseButton.toPointerButton(): PointerButton = when (this) {
    MouseButton.LEFT -> PointerButton.Primary
    MouseButton.RIGHT -> PointerButton.Secondary
    MouseButton.MIDDLE -> PointerButton.Tertiary
    else -> PointerButton(value - 1)
}

private fun MouseButton.toKey(): Key = when (this) {
    MouseButton.LEFT -> Key.Button1
    MouseButton.RIGHT -> Key.Button2
    MouseButton.MIDDLE -> Key.Button3
    else -> when (value) {
        1 -> Key.Button1
        2 -> Key.Button2
        3 -> Key.Button3
        4 -> Key.Button4
        5 -> Key.Button5
        6 -> Key.Button6
        7 -> Key.Button7
        8 -> Key.Button8
        9 -> Key.Button9
        10 -> Key.Button10
        11 -> Key.Button11
        12 -> Key.Button12
        13 -> Key.Button13
        14 -> Key.Button14
        15 -> Key.Button15
        16 -> Key.Button16
        else -> Key.Unknown
    }
}

private fun Offset.toDpOffset(density: Density): DpOffset = with(density) {
    DpOffset(x.toDp(), y.toDp())
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
