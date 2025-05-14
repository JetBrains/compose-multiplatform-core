/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.events

import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit

internal external interface KeyboardEventInitExtended : KeyboardEventInit {
    var keyCode: Int?
}

private fun KeyboardEventInit.keyEvent(type: String) = KeyboardEvent(type, this)

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
internal fun keyEvent(
    key: String,
    code: String = "Key${key.uppercase()}",
    keyCode: Int = key.uppercase().first().code,
    type: String = "keydown",
    ctrlKey: Boolean = false,
    metaKey: Boolean = false,
    altKey: Boolean = false,
    shiftKey: Boolean = false,
    cancelable: Boolean = true,
    repeat: Boolean = false,
    isComposing: Boolean = false
): KeyboardEvent {
    val keyboardEventInit = KeyboardEventInit(
        key = key,
        code = code,
        ctrlKey = ctrlKey,
        metaKey = metaKey,
        altKey = altKey,
        shiftKey = shiftKey,
        cancelable = cancelable,
        repeat = repeat,
        isComposing = isComposing,
    ) as KeyboardEventInitExtended

    keyboardEventInit.keyCode = keyCode

    return keyboardEventInit
        .keyEvent(type)
}

private fun DummyTouchEventInit(): TouchEventInit = js("({ changedTouches: [new Touch({identifier: 0, target: document})] })")

internal fun createTouchEvent(type: String): TouchEvent {
    return TouchEvent(type, DummyTouchEventInit())
}

internal fun createPointerEvent(type: String, pointerInitEvent: PointerEventInit, pointerType: String = "mouse"): PointerEvent {
    pointerInitEvent.pointerType = pointerType
    return PointerEvent(type, pointerInitEvent)
}

internal fun createPointerEvent(type: String, pointerType: String = "mouse"): PointerEvent {
    return PointerEvent(type, PointerEventInit(pointerType = pointerType))
}