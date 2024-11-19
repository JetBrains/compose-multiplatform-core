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

package androidx.pdf.view

import android.graphics.PointF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.util.LinkedList
import java.util.Queue
import kotlin.math.abs

/** Adjusts the position of [PdfView] in response to gestures detected by [GestureTracker] */
internal class ZoomScrollGestureHandler(private val pdfView: PdfView) :
    GestureTracker.GestureHandler() {
    internal var scrollInProgress = false
    internal var scaleInProgress = false

    /**
     * The multiplier to convert from a scale gesture's delta span, in pixels, to scale factor.
     *
     * [ScaleGestureDetector] returns scale factors proportional to the ratio of `currentSpan /
     * prevSpan`. This is problematic because it results in scale factors that are very large for
     * small pixel spans, which is particularly problematic for quickScale gestures, where the span
     * pixel values can be small, but the ratio can yield very large scale factors.
     *
     * Instead, we use this to ensure that pinching or quick scale dragging a certain number of
     * pixels always corresponds to a certain change in zoom. The equation that we've found to work
     * well is a delta span of the larger screen dimension should result in a zoom change of 2x.
     */
    private val linearScaleSpanMultiplier: Float =
        2f /
            maxOf(
                pdfView.resources.displayMetrics.heightPixels,
                pdfView.resources.displayMetrics.widthPixels
            )
    /** The maximum scroll distance used to determine if the direction is vertical. */
    private val maxScrollWindow =
        (pdfView.resources.displayMetrics.density * MAX_SCROLL_WINDOW_DP).toInt()

    /** The smallest scroll distance that can switch mode to "free scrolling". */
    private val minScrollToSwitch =
        (pdfView.resources.displayMetrics.density * MIN_SCROLL_TO_SWITCH_DP).toInt()

    /** Remember recent scroll events so we can examine the general direction. */
    private val scrollQueue: Queue<PointF> = LinkedList()

    /** Are we correcting vertical scroll for the current gesture? */
    private var straightenCurrentVerticalScroll = true

    private var totalX = 0f
    private var totalY = 0f

    private val totalScrollLength
        // No need for accuracy of correct hypotenuse calculation
        get() = abs(totalX) + abs(totalY)

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        scrollInProgress = true
        var dx = Math.round(distanceX)
        val dy = Math.round(distanceY)

        if (straightenCurrentVerticalScroll) {
            // Remember a window of recent scroll events.
            scrollQueue.offer(PointF(distanceX, distanceY))
            totalX += distanceX
            totalY += distanceY

            // Only consider scroll direction for a certain window of scroll events.
            while (totalScrollLength > maxScrollWindow && scrollQueue.size > 1) {
                // Remove the oldest scroll event - it is too far away to determine scroll
                // direction.
                val oldest = scrollQueue.poll()
                oldest?.let {
                    totalY -= oldest.y
                    totalX -= oldest.x
                }
            }

            if (
                totalScrollLength > minScrollToSwitch &&
                    abs((totalY / totalX).toDouble()) < SCROLL_CORRECTION_RATIO
            ) {
                straightenCurrentVerticalScroll = false
            } else {
                // Ignore the horizontal component of the scroll.
                dx = 0
            }
        }

        pdfView.scrollBy(dx, dy)
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return super.onFling(e1, e2, velocityX, velocityY)
        // TODO(b/376136621) Animate scroll position during a fling
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        return super.onDoubleTap(e)
        // TODO(b/376136331) Toggle between fit-to-page and zoomed-in on double tap gestures
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        scaleInProgress = true
        val rawScaleFactor = detector.scaleFactor
        val deltaSpan = abs(detector.currentSpan - detector.previousSpan)
        val scaleDelta = deltaSpan * linearScaleSpanMultiplier
        val linearScaleFactor = if (rawScaleFactor >= 1f) 1f + scaleDelta else 1f - scaleDelta
        val newZoom = (pdfView.zoom * linearScaleFactor).coerceIn(pdfView.minZoom, pdfView.maxZoom)

        pdfView.zoomTo(newZoom, detector.focusX, detector.focusY)
        return true
    }

    override fun onGestureEnd(gesture: GestureTracker.Gesture?) {
        when (gesture) {
            GestureTracker.Gesture.ZOOM -> {
                scaleInProgress = false
                pdfView.onZoomChanged()
            }
            GestureTracker.Gesture.DRAG,
            GestureTracker.Gesture.DRAG_Y,
            GestureTracker.Gesture.DRAG_X -> {
                scrollInProgress = false
                pdfView.onZoomChanged()
            }
            else -> {
                /* no-op */
            }
        }
        totalX = 0f
        totalY = 0f
        straightenCurrentVerticalScroll = true
        scrollQueue.clear()
    }

    companion object {
        /** The ratio of vertical to horizontal scroll that is assumed to be vertical only */
        private const val SCROLL_CORRECTION_RATIO = 1.5f
        /** The maximum scroll distance used to determine if the direction is vertical */
        private const val MAX_SCROLL_WINDOW_DP = 70
        /** The smallest scroll distance that can switch mode to "free scrolling" */
        private const val MIN_SCROLL_TO_SWITCH_DP = 30
    }
}
