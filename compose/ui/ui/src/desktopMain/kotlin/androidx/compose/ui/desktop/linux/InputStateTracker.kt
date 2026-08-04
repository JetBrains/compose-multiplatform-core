@file:Suppress("DuplicatedCode")
@file:OptIn(InternalComposeUiApi::class)

package androidx.compose.ui.desktop.linux

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
import org.jetbrains.desktop.linux.Event
import org.jetbrains.desktop.linux.MouseButton

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
    private var hasKeyboardFocus: Boolean = false
    private var syntheticPointerEventAfterRelayoutGeneration: Long = 0
    internal var keyboardModifiers by mutableStateOf(PointerKeyboardModifiers())

    /** Test seam: KDT linux Event constructors are internal, so tests drive state directly. */
    internal fun overridePointerStateForTest(pointerInWindow: Boolean, pointerPosition: Offset? = this.pointerPosition) {
        this.pointerInWindow = pointerInWindow
        this.pointerPosition = pointerPosition
    }

    fun updateStateAndSendEvents(
        event: Event,
        density: Density,
    ): org.jetbrains.desktop.linux.EventHandlerResult {
        syntheticPointerEventAfterRelayoutGeneration++
        return when (event) {
            is Event.MouseDown -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                pointerInWindow = true
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                density.updatePointerPosition(
                    event.locationInWindow.toDpOffset(),
                    PointerEventType.Move,
                    uptime,
                    event,
                )
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
                    result.anyChangeConsumed -> org.jetbrains.desktop.linux.EventHandlerResult.Stop
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
                    ) -> org.jetbrains.desktop.linux.EventHandlerResult.Stop
                    else -> org.jetbrains.desktop.linux.EventHandlerResult.Continue
                }
            }

            is Event.MouseUp -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                pointerInWindow = true
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                density.updatePointerPosition(
                    event.locationInWindow.toDpOffset(),
                    PointerEventType.Move,
                    uptime,
                    event,
                )
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
                    result.anyChangeConsumed -> org.jetbrains.desktop.linux.EventHandlerResult.Stop
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
                    ) -> org.jetbrains.desktop.linux.EventHandlerResult.Stop
                    else -> org.jetbrains.desktop.linux.EventHandlerResult.Continue
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
                    density.updatePointerPosition(
                        event.locationInWindow.toDpOffset(),
                        PointerEventType.Move,
                        uptime,
                        event,
                    )
                if (result.anyChangeConsumed) {
                    org.jetbrains.desktop.linux.EventHandlerResult.Stop
                } else {
                    org.jetbrains.desktop.linux.EventHandlerResult.Continue
                }
            }

            is Event.MouseEntered -> {
                val uptime = now()
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = true
                val result =
                    density.updatePointerPosition(
                        event.locationInWindow.toDpOffset(),
                        PointerEventType.Enter,
                        uptime,
                        event,
                    )
                if (result.anyChangeConsumed) {
                    org.jetbrains.desktop.linux.EventHandlerResult.Stop
                } else {
                    org.jetbrains.desktop.linux.EventHandlerResult.Continue
                }
            }

            is Event.MouseExited -> {
                val uptime = now()
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = false
                // Interactive resize/move (e.g. on KDE) grabs the pointer and eats the matching
                // MouseUp; the compositor sends Exit when the grab starts, so clearing here keeps
                // buttons from wedging pressed (AIR-5571). Modifiers survive while the window still
                // has keyboard focus.
                pointerButtons = PointerButtons()
                if (!hasKeyboardFocus) {
                    keyboardModifiers = PointerKeyboardModifiers()
                }
                val result =
                    density.updatePointerPosition(
                        event.locationInWindow.toDpOffset(),
                        PointerEventType.Exit,
                        uptime,
                        event,
                    )
                if (result.anyChangeConsumed) {
                    org.jetbrains.desktop.linux.EventHandlerResult.Stop
                } else {
                    org.jetbrains.desktop.linux.EventHandlerResult.Continue
                }
            }

            is Event.ScrollWheel -> {
                val uptime = event.timestamp.toDuration().inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                val position = event.locationInWindow.toDpOffset().toPxOffset(density)
                val moveResult =
                    density.updatePointerPosition(
                        event.locationInWindow.toDpOffset(),
                        PointerEventType.Move,
                        uptime,
                        event,
                    )
                val scrollResult = sendPointerInputEvent(
                    mousePointerInputEvent(
                        type = PointerEventType.Scroll,
                        uptime = uptime,
                        position = position,
                        buttons = pointerButtons,
                        scrollDelta = computeLinuxScrollDelta(
                            horizontalDelta = event.horizontalScroll.delta,
                            horizontalWheelValue120 = event.horizontalScroll.wheelValue120,
                            verticalDelta = event.verticalScroll.delta,
                            verticalWheelValue120 = event.verticalScroll.wheelValue120,
                            shiftPressed = keyboardModifiers.isShiftPressed,
                        ).toPxOffset(density),
                        keyboardModifiers = keyboardModifiers,
                        button = null,
                        nativeEvent = event,
                    ),
                )
                if (scrollResult.anyChangeConsumed || moveResult.anyChangeConsumed) {
                    sendPointerInputEventWithCurrentStateIfNecessary(PointerEventType.Move)
                    org.jetbrains.desktop.linux.EventHandlerResult.Stop
                } else {
                    org.jetbrains.desktop.linux.EventHandlerResult.Continue
                }
            }

            is Event.KeyDown -> {
                lastNativeEventUptimeMillis = now()
                val key = event.key.toKey()
                val modifiers = keyboardModifiers
                val handled = sendKeyEvent(
                    KeyEvent(
                        key = key,
                        type = KeyEventType.KeyDown,
                        codePoint = run {
                            val raw = event.characters?.firstCodePointOrNull() ?: 0
                            // Normalize Ctrl+letter to ISO control chars for cross-platform consistency.
                            // Some backends (e.g. GTK) give the unmodified char ('f' for Ctrl+F) rather
                            // than the ISO control char (0x06). AltGr (Ctrl+Alt) is exempt.
                            if (modifiers.isCtrlPressed && !modifiers.isAltPressed && raw in 0x40..0x7E) {
                                raw and 0x1F
                            } else {
                                raw
                            }
                        },
                        isCtrlPressed = modifiers.isCtrlPressed,
                        isMetaPressed = modifiers.isMetaPressed,
                        isAltPressed = modifiers.isAltPressed,
                        isShiftPressed = modifiers.isShiftPressed,
                        nativeEvent = event,
                    ),
                )
                if (handled) org.jetbrains.desktop.linux.EventHandlerResult.Stop
                else org.jetbrains.desktop.linux.EventHandlerResult.Continue
            }

            is Event.KeyUp -> {
                lastNativeEventUptimeMillis = now()
                val key = event.key.toKey()
                val modifiers = keyboardModifiers
                val handled = sendKeyEvent(
                    KeyEvent(
                        key = key,
                        type = KeyEventType.KeyUp,
                        isCtrlPressed = modifiers.isCtrlPressed,
                        isMetaPressed = modifiers.isMetaPressed,
                        isAltPressed = modifiers.isAltPressed,
                        isShiftPressed = modifiers.isShiftPressed,
                        nativeEvent = event,
                    ),
                )
                if (handled) org.jetbrains.desktop.linux.EventHandlerResult.Stop
                else org.jetbrains.desktop.linux.EventHandlerResult.Continue
            }

            is Event.ModifiersChanged -> updateStateForModifiersChanged(event)

            is Event.WindowKeyboardEnter -> {
                updateStateForKeyboardEnter()
                org.jetbrains.desktop.linux.EventHandlerResult.Continue
            }

            is Event.WindowKeyboardLeave -> updateStateForKeyboardLeave()

            else -> org.jetbrains.desktop.linux.EventHandlerResult.Continue
        }
    }

    fun updateStateForModifiersChanged(
        event: Event.ModifiersChanged,
    ): org.jetbrains.desktop.linux.EventHandlerResult {
        keyboardModifiers = event.modifiers.toPointerKeyboardModifiers()
        return org.jetbrains.desktop.linux.EventHandlerResult.Continue
    }

    fun updateStateForKeyboardEnter() {
        hasKeyboardFocus = true
    }

    fun updateStateForKeyboardLeave(): org.jetbrains.desktop.linux.EventHandlerResult {
        hasKeyboardFocus = false
        if (!pointerInWindow) {
            keyboardModifiers = PointerKeyboardModifiers()
        }
        return org.jetbrains.desktop.linux.EventHandlerResult.Continue
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

internal data class PendingSyntheticPointerEventAfterRelayout(
    val generation: Long,
    val type: PointerEventType,
)

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
    MouseButton.BACK,
    MouseButton.SIDE,
        -> PointerButton.Back
    MouseButton.FORWARD,
    MouseButton.EXTRA,
        -> PointerButton.Forward
    else -> PointerButton(value)
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

/**
 * Scroll delta per Noria (AIR-5233 + AIR-5621): discrete wheel detents scroll 100.dp per notch
 * (`wheelValue120` reports 120 per notch), smooth/touchpad deltas scale by 15.dp, and Shift swaps
 * the axes so a vertical wheel scrolls horizontally.
 */
internal fun computeLinuxScrollDelta(
    horizontalDelta: Double,
    horizontalWheelValue120: Int,
    verticalDelta: Double,
    verticalWheelValue120: Int,
    shiftPressed: Boolean,
): DpOffset {
    val rawDelta = if (verticalWheelValue120 != 0 || horizontalWheelValue120 != 0) {
        DpOffset(
            ((horizontalWheelValue120.toFloat() / 120f) * 100).dp,
            ((verticalWheelValue120.toFloat() / 120f) * 100).dp,
        )
    } else {
        DpOffset(
            (horizontalDelta * 15).dp,
            (verticalDelta * 15).dp,
        )
    }
    return if (shiftPressed) {
        DpOffset(rawDelta.y, rawDelta.x)
    } else {
        rawDelta
    }
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
