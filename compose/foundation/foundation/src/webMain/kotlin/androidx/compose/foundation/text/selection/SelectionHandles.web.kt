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

package androidx.compose.foundation.text.selection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.util.fastForEach

@Composable
internal actual fun SelectionHandle(
    offsetProvider: OffsetProvider,
    isStartHandle: Boolean,
    direction: ResolvedTextDirection,
    handlesCrossed: Boolean,
    minTouchTargetSize: DpSize,
    lineHeight: Float,
    modifier: Modifier
) = SkikoSelectionHandle(
    offsetProvider = offsetProvider,
    isStartHandle = isStartHandle,
    direction = direction,
    handlesCrossed = handlesCrossed,
    minTouchTargetSize = minTouchTargetSize,
    lineHeight = lineHeight,
    modifier = modifier.consumeTouchPressAndRelease()
)

/**
 * Consumes touch press/release on a handle to "preventDefault" the corresponding touch events
 * (see platform-wiring code in ComposeWindowInternal.web.kt)
 *
 * Handles live in a Popup that captures the gesture but consumes nothing until a drag starts.
 * On web, unconsumed taps bypass 'preventDefault', causing default browser behavior to
 * move focus from the backing text input and hide the virtual keyboard.
 * See https://youtrack.jetbrains.com/issue/CMP-10621/Web-Mobile.-The-virtual-keyboard-hides-after-long-tap-while-the-word-is-selected
 *
 * Consume only in the final pass - [PointerEventPass.Final]:
 * consuming earlier cancels the handle's own drag detection.
 * Only press/release, only touch: drags consume their own moves, and mouse input isn't affected.
 */
private fun Modifier.consumeTouchPressAndRelease(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.fastForEach {
                if (it.type == PointerType.Touch && it.pressed != it.previousPressed) {
                    it.consume()
                }
            }
        }
    }
}
