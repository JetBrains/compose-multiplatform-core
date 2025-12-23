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

package androidx.compose.foundation.lazy.layout

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.util.trace
import androidx.compose.ui.util.traceValue
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.staticCFunction
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.QuartzCore.CADisplayLink
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.UIKit.UIScreen
import kotlin.math.max

@Suppress("DEPRECATION") // b/420551535
@ExperimentalFoundationApi
@Composable
internal actual fun rememberDefaultPrefetchScheduler(): PrefetchScheduler {
    // Check if UIKit prefetch is enabled
    if (!ComposeFoundationFlags.isUikitLazyListPrefetchEnabled) {
        // Return a no-op scheduler
        return object : PrefetchScheduler {
            override fun schedulePrefetch(prefetchRequest: PrefetchRequest) {
                // No-op: prefetch is disabled
            }
        }
    }

    val viewController = LocalUIViewController.current
    val scheduler = remember(viewController) {
        SkikoPrefetchScheduler(viewController)
    }

    DisposableEffect(scheduler) {
        scheduler.onAttach()
        onDispose {
            scheduler.onDetach()
        }
    }

    return scheduler
}

/**
 * Get current time in nanoseconds using CFAbsoluteTimeGetCurrent.
 * CFAbsoluteTimeGetCurrent returns time in seconds as a Double.
 */
private fun currentTimeNanos(): Long {
    return (CFAbsoluteTimeGetCurrent() * 1_000_000_000).toLong()
}

/**
 * Skiko (iOS/Desktop) specific prefetch implementation. This implementation maintains algorithm
 * parity with the Android version, using CADisplayLink instead of Choreographer for frame
 * synchronization.
 *
 * The implementation is platform-agnostic in its logic, with only the frame callback mechanism
 * being platform-specific.
 */
@OptIn(ExperimentalForeignApi::class)
@Suppress("DEPRECATION") // b/420551535
@ExperimentalFoundationApi
internal class SkikoPrefetchScheduler(
    private val viewController: platform.UIKit.UIViewController
) :
    PrefetchScheduler,
    PriorityPrefetchScheduler {

    /**
     * The list of currently not processed prefetch requests. The requests will be processed one by
     * during subsequent [run]s.
     *
     * Since Kotlin/Native doesn't have java.util.PriorityQueue, we use a sorted MutableList.
     * Items are kept sorted by priority (highest first).
     */
    private val prefetchRequests = mutableListOf<PriorityTask>()
    private var prefetchScheduled = false
    private val scope = PrefetchRequestScopeImpl()

    /** Is true when LazyList was composed and not yet disposed. */
    private var isActive = false

    private var frameStartTimeNanos = 0L
    private var lastDrawingTimeNanos = 0L

    /** CADisplayLink for frame synchronization (iOS/macOS equivalent of Choreographer) */
    private var displayLink: CADisplayLink? = null

    /**
     * Frame interval in nanoseconds, calculated from the screen's refresh rate.
     * Similar to Android's static cache, we calculate this once per scheduler instance.
     */
    private var frameIntervalNs: Long = 0L

    /**
     * NSObject wrapper for CADisplayLink target.
     * This is necessary because CADisplayLink requires an NSObject as target.
     */
    private inner class DisplayLinkTarget : NSObject() {
        @ObjCAction
        fun onDisplayLink(displayLink: CADisplayLink) {
            this@SkikoPrefetchScheduler.handleDisplayLink(displayLink)
        }
    }

    private val displayLinkTarget = DisplayLinkTarget()

    init {
        setupDisplayLink()
    }

    /**
     * Callback to be executed when the prefetching is needed. [prefetchRequests] will be used as an
     * input.
     */
    private fun run() {
        if (
            prefetchRequests.isEmpty() ||
                !prefetchScheduled ||
                !isActive
        ) {
            // incorrect input. ignore
            prefetchScheduled = false
            return
        }

        // Use both the last drawing time or the frameStartTime given by the display link.
        // In most cases the last drawing time should be enough and equal to the frame start
        // time given by the display link. These are the cases where they should differ:
        // 1) When this handler is executed in the same frame as it was scheduled. In these cases,
        // using last drawing time will be correct because scheduling is usually followed by a
        // drawing operation as it happens during scroll.
        // 2) When there wasn't enough time to complete a request in the current frame. If there
        // isn't enough time, the handler will be executed in the next frame where there might
        // not have been a drawing operation. Using the display link frame start time will be
        // safe in these cases.
        val viewDrawTimeNanos = lastDrawingTimeNanos

        // enter idle mode if the last time we draw was 2 frames ago.
        scope.isFrameIdle = (currentTimeNanos() > viewDrawTimeNanos + 2 * frameIntervalNs)
        scope.nextFrameTimeNs = maxOf(frameStartTimeNanos, viewDrawTimeNanos) + frameIntervalNs
        var scheduleForNextFrame = false
        while (prefetchRequests.isNotEmpty() && !scheduleForNextFrame) {
            scheduleForNextFrame =
                if (scope.isFrameIdle) {
                    trace("compose:lazy:prefetch:idle_frame") { runRequest() }
                } else {
                    runRequest()
                }
        }

        if (scheduleForNextFrame) {
            // there is not enough time left in this frame. we schedule a next frame callback
            // in which we are going to post the message in the handler again.
            // The display link will automatically call us on the next frame.
        } else {
            prefetchScheduled = false
        }
        traceValue("compose:lazy:prefetch:available_time_nanos", 0L) // reset counter
    }

    private fun runRequest(): Boolean {
        var scheduleForNextFrame = false
        val availableTimeNanos = scope.availableTimeNanos()
        traceValue("compose:lazy:prefetch:available_time_nanos", availableTimeNanos)
        if (availableTimeNanos > 0) {
            // at this point we know that prefetchRequests is not empty.
            val request = prefetchRequests.first().request
            val hasMoreWorkToDo = with(request) { scope.execute() }
            if (hasMoreWorkToDo) {
                scheduleForNextFrame = true
            } else {
                prefetchRequests.removeFirst()
            }
            scope.isFrameIdle = false // reset idle state for subsequent requests.
        } else {
            scheduleForNextFrame = true
        }
        return scheduleForNextFrame
    }

    /**
     * CADisplayLink callback. It will be called on every frame. We will post execution to the
     * main queue to process prefetch requests.
     */
    private fun handleDisplayLink(displayLink: CADisplayLink) {
        if (isActive) {
            frameStartTimeNanos = (displayLink.timestamp * 1_000_000_000).toLong()

            // Update last drawing time
            lastDrawingTimeNanos = currentTimeNanos()

            // Post to main thread to execute prefetch work
            postToMainThread()
        }
    }

    private fun postToMainThread() {
        dispatch_async(dispatch_get_main_queue()) {
            run()
        }
    }

    private fun startExecution() {
        if (!prefetchScheduled) {
            prefetchScheduled = true
            // schedule the prefetching by posting to main thread
            postToMainThread()
        }
    }

    override fun scheduleLowPriorityPrefetch(prefetchRequest: PrefetchRequest) {
        addPrefetchRequest(PriorityTask(PriorityTask.Low, prefetchRequest))
        startExecution()
    }

    override fun scheduleHighPriorityPrefetch(prefetchRequest: PrefetchRequest) {
        addPrefetchRequest(PriorityTask(PriorityTask.High, prefetchRequest))
        startExecution()
    }

    /**
     * Adds a prefetch request while maintaining priority order (highest priority first).
     * This mimics the behavior of PriorityQueue with a reverse comparator.
     */
    private fun addPrefetchRequest(task: PriorityTask) {
        // Find the insertion point to maintain descending priority order
        val index = prefetchRequests.indexOfFirst { it.priority < task.priority }
        if (index == -1) {
            // All existing items have higher or equal priority, add at the end
            prefetchRequests.add(task)
        } else {
            // Insert before the first item with lower priority
            prefetchRequests.add(index, task)
        }
    }

    fun onAttach() {
        isActive = true
    }

    fun onDetach() {
        isActive = false
        displayLink?.invalidate()
        displayLink = null
    }

    private fun setupDisplayLink() {
        displayLink = CADisplayLink.displayLinkWithTarget(
            target = displayLinkTarget,
            selector = platform.objc.sel_registerName("onDisplayLink:")
        )

        // Calculate frame interval from screen's maximum frames per second
        calculateFrameIntervalIfNeeded()

        displayLink?.addToRunLoop(
            NSRunLoop.mainRunLoop,
            NSRunLoopCommonModes
        )
    }

    private fun calculateFrameIntervalIfNeeded() {
        // We only do this query once, similar to Android's approach
        if (frameIntervalNs == 0L) {
            val screen = UIScreen.mainScreen
            val refreshRate = screen?.maximumFramesPerSecond?.toFloat() ?: 60f
            // Ensure we have a valid refresh rate (at least 30fps)
            val validRefreshRate = if (refreshRate >= 30f) refreshRate else 60f
            frameIntervalNs = (1_000_000_000 / validRefreshRate).toLong()
        }
    }

    class PrefetchRequestScopeImpl : PrefetchRequestScope {

        /**
         * If the [PrefetchRequest] execution can do "overtime". Overtime here means more time than
         * what is available in this frame. If this is true, [availableTimeNanos] will return
         * [Long.MAX_VALUE] indicating that any time constraints taken into consideration to execute
         * this request will be ignored.
         */
        var isFrameIdle: Boolean = false

        var nextFrameTimeNs: Long = 0L

        override fun availableTimeNanos() =
            if (isFrameIdle) {
                Long.MAX_VALUE
            } else {
                max(0, nextFrameTimeNs - currentTimeNanos())
            }
    }
}

@Suppress("DEPRECATION") // b/420551535
@ExperimentalFoundationApi
internal class PriorityTask(val priority: Int, val request: PrefetchRequest) {
    companion object {
        val Low = 0
        val High = 1
    }
}
