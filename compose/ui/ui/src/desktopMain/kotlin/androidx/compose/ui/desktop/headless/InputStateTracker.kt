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

@file:OptIn(kotlin.time.ExperimentalTime::class)

package androidx.compose.ui.desktop.headless

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.toOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Callback signature mirroring [androidx.compose.ui.scene.ComposeScene.sendPointerEvent].
 *
 * A headless-package copy of the same `fun interface` each platform defines (see
 * `desktop/macos/InputStateTracker.kt`); its parameter list and [PointerEventResult] return type
 * match `ComposeScene.sendPointerEvent` exactly.
 */
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
 * Headless equivalent of the per-platform input trackers: it turns the injected headless [Event]s
 * into the pointer/key events a [androidx.compose.ui.scene.ComposeScene] expects, while keeping the
 * cursor position, pressed buttons and keyboard modifiers that must be replayed for hit-testing.
 *
 * Ported from the Fleet/Noria `headless/InputStateTracker.kt`, with the `PointerInputEvent` sink
 * replaced by the [SendPointerEvent] shape used by the other desktop platforms.
 */
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
    private var pendingSyntheticPointerEventType: PointerEventType? = null
    internal var keyboardModifiers by mutableStateOf(PointerKeyboardModifiers())

    fun updateStateAndSendEvents(event: Event, density: Density) {
        syntheticPointerEventAfterRelayoutGeneration++
        when (event) {
            is Event.MouseDown -> {
                val uptime = event.timestamp.seconds.inWholeMilliseconds
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
                pointerButtons += event.button
                val processResult = sendPointerEvent.invoke(
                    eventType = PointerEventType.Press,
                    position = event.locationInWindow.toOffset(density),
                    scrollDelta = Offset.Zero,
                    timeMillis = uptime,
                    type = PointerType.Mouse,
                    buttons = pointerButtons,
                    keyboardModifiers = keyboardModifiers,
                    nativeEvent = event,
                    button = event.button,
                )
                if (!processResult.anyChangeConsumed) {
                    sendKeyEvent(event.toKeyEvent(keyboardModifiers))
                }
            }
            is Event.MouseUp -> {
                val uptime = event.timestamp.seconds.inWholeMilliseconds
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
                pointerButtons -= event.button
                val processResult = sendPointerEvent.invoke(
                    eventType = PointerEventType.Release,
                    position = event.locationInWindow.toOffset(density),
                    scrollDelta = Offset.Zero,
                    timeMillis = uptime,
                    type = PointerType.Mouse,
                    buttons = pointerButtons,
                    keyboardModifiers = keyboardModifiers,
                    nativeEvent = event,
                    button = event.button,
                )
                if (!processResult.anyChangeConsumed) {
                    sendKeyEvent(event.toKeyEvent(keyboardModifiers))
                }
            }
            is Event.MouseMoved -> {
                val uptime = event.timestamp.seconds.inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerButtons = PointerButtons()
                density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Move,
                        uptime,
                        event,
                    )
                }
            }
            is Event.MouseEntered -> {
                val uptime = event.timestamp.seconds.inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = true
                density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Enter,
                        uptime,
                        event,
                    )
                }
            }
            is Event.MouseExited -> {
                val uptime = event.timestamp.seconds.inWholeMilliseconds
                lastNativeEventUptimeMillis = uptime
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
                pointerInWindow = false
                density.run {
                    updatePointerPosition(
                        event.locationInWindow,
                        PointerEventType.Exit,
                        uptime,
                        event,
                    )
                }
            }
            is Event.KeyDown -> {
                updateModifierState(event)
                sendKeyEvent(event.toKeyEvent(keyboardModifiers))
            }
            is Event.KeyUp -> {
                updateModifierState(event)
                sendKeyEvent(event.toKeyEvent(keyboardModifiers))
            }
        }
    }

    private fun Density.updatePointerPosition(
        locationInWindow: DpOffset,
        pointerEventType: PointerEventType,
        uptime: Long,
        nativeEvent: WindowEvent,
    ): PointerEventResult {
        val previousPointerPosition = pointerPosition
        pointerPosition = locationInWindow.toOffset(this)
        return if (previousPointerPosition != pointerPosition) {
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
            PointerEventResult(anyChangeConsumed = false)
        }
    }

    private fun updateModifierState(event: WindowEvent) {
        when (event) {
            is Event.KeyDown -> {
                keyboardModifiers = keyboardModifiers.update(event.key, KeyEventType.KeyDown)
            }
            is Event.KeyUp -> {
                keyboardModifiers = keyboardModifiers.update(event.key, KeyEventType.KeyUp)
            }
            else -> {} // do nothing
        }
    }

    /**
     * We intentionally keep this local-only so Wayland and similar environments don't have to
     * provide screen-space pointer coordinates. That still lets us refresh hit tests after content
     * relayouts inside the same window.
     *
     * Returns the generation token for the pending refresh, or `null` when no refresh is needed.
     * Every real event (and every [prepareSyntheticPointerEventAfterRelayoutIfNecessary] call)
     * bumps the generation, so a token becomes stale as soon as anything else happens.
     */
    fun prepareSyntheticPointerEventAfterRelayoutIfNecessary(): Long? {
        val type = when {
            pointerPosition == null -> return null
            pointerButtons.areAnyPressed -> PointerEventType.Move
            !pointerInWindow -> return null
            inputModeManager.inputMode == InputMode.Touch -> PointerEventType.Move
            else -> PointerEventType.Exit
        }
        val generation = syntheticPointerEventAfterRelayoutGeneration + 1
        syntheticPointerEventAfterRelayoutGeneration = generation
        pendingSyntheticPointerEventType = type
        return generation
    }

    fun sendSyntheticPointerEventAfterRelayoutIfCurrent(generation: Long) {
        if (generation != syntheticPointerEventAfterRelayoutGeneration) {
            return
        }
        val type = pendingSyntheticPointerEventType ?: return
        sendPointerInputEventWithCurrentStateIfNecessary(type)
    }

    private fun sendPointerInputEventWithCurrentStateIfNecessary(
        type: PointerEventType,
        uptime: Long = lastNativeEventUptimeMillis ?: Clock.System.now().toEpochMilliseconds(),
        scrollDelta: Offset = Offset.Zero,
        nativeEvent: Any? = null,
        button: PointerButton? = null,
    ): PointerEventResult {
        return if (!pointerInWindow || pointerPosition == null) {
            PointerEventResult(anyChangeConsumed = false)
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
