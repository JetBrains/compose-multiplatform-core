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

package androidx.compose.ui.test.utils

import androidx.compose.test.utils.endHoverInWindow
import androidx.compose.test.utils.endInWindow
import androidx.compose.test.utils.endPinchInWindow
import androidx.compose.test.utils.hoverEventAtPoint
import androidx.compose.test.utils.hoverMoveToPoint
import androidx.compose.test.utils.pinchByScale
import androidx.compose.test.utils.pinchEventAtPoint
import androidx.compose.test.utils.scrollByDelta
import androidx.compose.test.utils.scrollEventAtPoint
import androidx.compose.ui.unit.DpOffset
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIEvent
import platform.UIKit.UIWindow

/**
 * Opens a synthetic trackpad scroll session anchored at [location] in this window. The
 * returned [UIEvent] is a UIScrollEvent already dispatched with `UIScrollPhaseBegan` and
 * the initial [delta]; use [UIEvent.scrollBy] to emit follow-up `Changed` events and
 * [UIEvent.endScroll] to close the session.
 *
 * Returns `null` if the private `UIScrollEvent` class is not available on the current
 * runtime (pre-iOS 13.4) — synthesized scroll is then unavailable.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIWindow.scrollEventAt(
    location: DpOffset,
    delta: DpOffset = DpOffset.Zero,
): UIEvent {
    return UIEvent.scrollEventAtPoint(
        point = location.toCGPoint(),
        delta = delta.toCGPoint(),
        inWindow = this,
    ) ?: error("UIScrollEvent unavailable on this runtime")
}

/**
 * Emits one `UIScrollPhaseChanged` event on this scroll session with the given [delta]
 * (in dp, converted to points). Must only be called on a [UIEvent] returned by
 * [UIWindow.scrollEventAt].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIEvent.scrollBy(delta: DpOffset, window: UIWindow) {
    scrollByDelta(delta.toCGPoint(), inWindow = window)
}

/**
 * Emits the closing `UIScrollPhaseEnded` event on this scroll session. Must only be
 * called on a [UIEvent] returned by [UIWindow.scrollEventAt].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIEvent.endScroll(window: UIWindow) {
    endInWindow(window)
}

/**
 * Begins a synthetic trackpad pinch session anchored at [location] in this window with the
 * initial absolute [scale] (typically `1.0`, phase Began). Use [UIEvent.pinchBy] to emit
 * follow-up `Changed` events with new absolute scales and [UIEvent.endPinch] to close the
 * session.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIWindow.pinchEventAt(location: DpOffset, scale: Double = 1.0): UIEvent {
    return UIEvent.pinchEventAtPoint(
        point = location.toCGPoint(),
        scale = scale,
        inWindow = this,
    ) ?: error("UITransformEvent unavailable on this runtime")
}

/**
 * Emits one `phase-Changed` pinch event on this pinch session with the new absolute
 * [scale]. Must only be called on a [UIEvent] returned by [UIWindow.pinchEventAt].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIEvent.pinchBy(scale: Double, window: UIWindow) {
    pinchByScale(scale, inWindow = window)
}

/**
 * Emits the closing `phase-Ended` pinch event on this pinch session. Must only be called
 * on a [UIEvent] returned by [UIWindow.pinchEventAt].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIEvent.endPinch(window: UIWindow) {
    endPinchInWindow(window)
}

/**
 * Opens a synthetic hover session anchored at [location] in this window (phase Began).
 * Use [UIEvent.hoverTo] to emit follow-up `Changed` events at new locations and
 * [UIEvent.endHover] to close the session.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIWindow.hoverEventAt(
    location: DpOffset,
): UIEvent {
    return UIEvent.hoverEventAtPoint(
        point = location.toCGPoint(),
        inWindow = this,
    ) ?: error("UIHoverEvent unavailable on this runtime")
}

/**
 * Emits one `Changed` hover event at [location] on this hover session. Must only be
 * called on a [UIEvent] returned by [UIWindow.hoverEventAt].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIEvent.hoverTo(location: DpOffset, window: UIWindow) {
    hoverMoveToPoint(location.toCGPoint(), inWindow = window)
}

/**
 * Emits the closing `Ended` hover event on this hover session. Must only be called on
 * a [UIEvent] returned by [UIWindow.hoverEventAt].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIEvent.endHover(window: UIWindow) {
    endHoverInWindow(window)
}
