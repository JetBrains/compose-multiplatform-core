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

package androidx.pdf.view.fastscroll

import android.view.MotionEvent
import android.view.ViewParent

/**
 * Handles touch events related to the fast scroll functionality.
 *
 * This class is responsible for detecting and processing touch events that interact with the fast
 * scroll scrubber. It determines if a touch event is within the bounds of the scrubber and notifies
 * a [FastScrollGestureHandler] when a fast scroll gesture (dragging the scrubber) is detected.
 *
 * @param fastScoller The [FastScroller] instance associated with this handler.
 * @param gestureHandler The [FastScrollGestureHandler] that will be notified of fast scroll events.
 */
internal class FastScrollGestureDetector(
    private val fastScoller: FastScroller,
    private val gestureHandler: FastScrollGestureHandler
) {
    internal var trackingFastScrollGesture: Boolean = false

    /**
     * Handles touch events and detects fast scroll gestures.
     *
     * This method processes the provided [MotionEvent] and determines if it represents a fast
     * scroll interaction. If a fast scroll gesture is detected (e.g., the user starts dragging the
     * fast scroll scrubber), the [gestureHandler] is notified.
     *
     * @param event The [MotionEvent] to handle.
     * @param viewWidth Width of the view in pixels.
     * @return True if the event was handled as a fast scroll gesture, false otherwise.
     */
    fun handleEvent(event: MotionEvent, parent: ViewParent?, viewWidth: Int): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (isPointWithinVisibleBounds(event, viewWidth)) {
                trackingFastScrollGesture = true
                return true
            }
        }

        if (trackingFastScrollGesture) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                gestureHandler.onFastScrollDetected(event.y)
            } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                parent?.requestDisallowInterceptTouchEvent(false)
                trackingFastScrollGesture = false
            }
            return true
        }

        return false
    }

    /**
     * Checks if a touch event is within the visible bounds of the fast scroll scrubber.
     *
     * @param event The [MotionEvent] to check.
     * @param viewWidth Width of the view in pixels
     * @return True if the touch event is within the bounds of the scrubber, false otherwise.
     */
    private fun isPointWithinVisibleBounds(event: MotionEvent, viewWidth: Int): Boolean {
        return event.x > (viewWidth - fastScoller.fastScrollDrawer.thumbWidthPx)
        // Deliberately ignore (x < getWidth() - scrollbarMarginRight) to make it easier
        // to grab it.
        &&
            event.y >= fastScoller.fastScrollY &&
            event.y <= fastScoller.fastScrollY + fastScoller.fastScrollDrawer.thumbHeightPx
    }

    /** An interface for receiving notifications about fast scroll gestures. */
    interface FastScrollGestureHandler {
        /**
         * Called when a fast scroll gesture is detected.
         *
         * @param eventY The vertical scroll position in pixels indicated by the fast scroll
         *   gesture.
         */
        fun onFastScrollDetected(eventY: Float)
    }
}
