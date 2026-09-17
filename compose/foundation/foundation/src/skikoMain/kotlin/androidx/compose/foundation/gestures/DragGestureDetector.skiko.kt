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

package androidx.compose.foundation.gestures

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed

internal actual suspend fun AwaitPointerEventScope.awaitDragOrCancellationImpl(
    pointerId: PointerId
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the gesture is canceled
    }
    // The default/Android implementation just checks whether the pointer moved.
    // But if the target element has moved instead while the pointer is stationary, we still need to
    // deliver the (synthetic) move event.
    val change = awaitDragOrUp(pointerId) { event, change ->
        change.positionChangedIgnoreConsumed() || (event.type == PointerEventType.Move)
    }
    return if (change?.isConsumed == false) change else null
}