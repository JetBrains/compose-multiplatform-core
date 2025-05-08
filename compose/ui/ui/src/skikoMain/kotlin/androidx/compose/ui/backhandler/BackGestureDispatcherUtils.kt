/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.backhandler

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.width

internal fun BackGestureDispatcher.handleBackKeyEvent(
    event: KeyEvent,
    listener: BackGestureListener?
): Boolean {
    if (listener != null && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
        listener.onStarted()
        listener.onCompleted()
        return true
    } else {
        return false
    }
}

/*
 FIXME: Figure out why calling [BackEventCompat] constructor from uikitMain causes errors
  e: Cannot infer type for this parameter. Please specify it explicitly.
  e: Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
  fun <T, R> DeepRecursiveFunction<T, R>.invoke(value: T): R
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun backEventCompat(
    eventOffset: Offset,
    leftEdge: Boolean,
    touch: DpOffset,
    bounds: DpRect
): BackEventCompat {
    val progress = if (leftEdge) {
        touch.x / bounds.width
    } else {
        (bounds.width - touch.x) / bounds.width
    }
    return BackEventCompat(
        touchX = eventOffset.x,
        touchY = eventOffset.y,
        progress = progress,
        swipeEdge = if (leftEdge) {
            BackEventCompat.EDGE_LEFT
        } else {
            BackEventCompat.EDGE_RIGHT
        }
    )
}
