/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.window

import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.uikit.utils.CMPMetalLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.trace
import androidx.compose.ui.viewinterop.UIKitInteropAction
import androidx.compose.ui.viewinterop.UIKitInteropTransaction
import kotlin.math.roundToInt
import kotlinx.cinterop.*
import org.jetbrains.skia.*
import org.jetbrains.skia.Rect
import platform.Foundation.NSLock
import platform.Foundation.NSRunLoop
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSThread
import platform.QuartzCore.*
import platform.darwin.*
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSTimeInterval
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLDeviceProtocol
import platform.posix.QOS_CLASS_USER_INTERACTIVE

internal class DisplayLinkConditions(
    val setPausedCallback: (Boolean) -> Unit
) {
    /**
     * see [MetalRedrawer.ongoingInteractionEventsCount]
     */
    var needsToBeProactive: Boolean = false
        set(value) {
            field = value

            update()
        }

    /**
     * Indicates that application is running foreground now
     */
    var isActive: Boolean = true
        set(value) {
            field = value

            update()
        }

    /**
     * Number of subsequent vsync that will issue a draw
     */
    private var scheduledRedrawsCount = 0
        set(value) {
            field = value

            update()
        }

    /**
     * Handle display link callback by updating internal state and dispatching the draw, if needed.
     */
    inline fun onDisplayLinkTick(draw: () -> Unit) {
        if (scheduledRedrawsCount > 0) {
            scheduledRedrawsCount -= 1
            draw()
        }
    }

    /**
     * Mark next [FRAMES_COUNT_TO_SCHEDULE_ON_NEED_REDRAW] frames to issue a draw dispatch and unpause displayLink if needed.
     */
    fun setNeedsRedraw() {
        scheduledRedrawsCount = FRAMES_COUNT_TO_SCHEDULE_ON_NEED_REDRAW
    }

    private fun update() {
        val isUnpaused = isActive && (needsToBeProactive || scheduledRedrawsCount > 0)
        setPausedCallback(!isUnpaused)
    }

    companion object {
        /**
         * Right now `needRedraw` doesn't reentry from within `draw` callback during animation which leads to a situation where CADisplayLink is first paused
         * and then asynchronously unpaused. This effectively makes Pro Motion display lose a frame before running on highest possible frequency again.
         * To avoid this, we need to render at least two frames (instead of just one) after each `needRedraw` assuming that invalidation comes inbetween them and
         * displayLink is not paused by the end of RuntimeLoop tick.
         */
        const val FRAMES_COUNT_TO_SCHEDULE_ON_NEED_REDRAW = 2
    }
}

// https://youtrack.jetbrains.com/issue/CMP-9722
// Copy of the class LegacyMetalRedrawer with a different layer.
// All the changes here must be implemented in the `LegacyMetalRedrawer` as well.
internal class SurfaceMetalRedrawer(
    private val metalLayer: CMPMetalLayer,
    private var retrieveInteropTransaction: () -> UIKitInteropTransaction,
    private var render: (Canvas, targetTimestamp: NSTimeInterval) -> Unit,
): MetalRedrawer {
    private val device = metalLayer.device as? MTLDeviceProtocol
        ?: throw IllegalStateException("MetalRedrawer requires MTLDevice")
    private val queue = getCachedCommandQueue(device)
    private val context = DirectContext.makeMetal(device.objcPtr(), queue.objcPtr())
    private var lastRenderTimestamp: NSTimeInterval = CACurrentMediaTime()
    private val pictureRecorder = PictureRecorder()

    private val inflightCommandBuffersGroup = dispatch_group_create()

    var maximumFramesPerSecond: NSInteger = 0

    // https://youtrack.jetbrains.com/issue/CMP-9722
    // Left here for compatibility reasons. Does not make any effect and must be removed.
    override var isForcedToPresentWithTransactionEveryFrame: Boolean = false

    override var preferredFramesPerSecond: NSInteger
        get() = caDisplayLink?.preferredFramesPerSecond ?: 0
        set(value) {
            if (caDisplayLink?.preferredFramesPerSecond == value) return
            caDisplayLink?.preferredFramesPerSecond = value
        }

    override val currentTargetFrameDuration: NSTimeInterval?
        get() {
            val currentTargetTimestamp = currentTargetTimestamp ?: return null
            val currentTimestamp = caDisplayLink?.timestamp ?: return null
            return currentTargetTimestamp - currentTimestamp
        }

    private val displayLinkConditions = DisplayLinkConditions { paused ->
        caDisplayLink?.paused = paused
    }

    /**
     * Runs invalidation-independent displayLink for forcing UITouch events to come at the fastest
     * possible cadence. Otherwise, touch events can come at rate lower than actual display refresh
     * rate.
     */
    override var ongoingInteractionEventsCount: Int = 0
        set(value) {
            field = value
            displayLinkConditions.needsToBeProactive = value > 0
        }

    /**
     * True if Metal layer can be opaque. In this case if no interop views are present, Metal
     * rendering will be optimized for direct-to-screen rendering.
     *
     * In some scenarios like using this layer as a canvas for dialog and popup layers, it's never the
     * case.
     */
    var canBeOpaque: Boolean = true
        set(value) {
            field = value

            updateLayerOpacity()
        }

    /**
     * `true` if Metal rendering is synchronized with changes of UIKit interop views, `false` otherwise
     */
    private var isInteropActive = false
        set(value) {
            if (field != value) {
                field = value
                // If active, make metalLayer transparent, opaque otherwise.
                // Rendering into an opaque CMPMetalLayer allows direct-to-screen optimization.
                updateLayerOpacity()
            }
        }

    private fun updateLayerOpacity() {
        metalLayer.setOpaque(!isInteropActive && canBeOpaque)
    }

    /**
     * Display link for driving the rendering loop.
     * null after [dispose] call
     */
    private var caDisplayLink: CADisplayLink? = CADisplayLink.displayLinkWithTarget(
        target = SurfaceDisplayLinkProxy {
            val targetTimestamp = currentTargetTimestamp ?: return@SurfaceDisplayLinkProxy

            displayLinkConditions.onDisplayLinkTick {
                draw(waitUntilCompletion = false, targetTimestamp)
            }
        },
        selector = NSSelectorFromString(SurfaceDisplayLinkProxy::handleDisplayLinkTick.name)
    )

    private val currentTargetTimestamp: NSTimeInterval?
        get() = caDisplayLink?.targetTimestamp

    init {
        val caDisplayLink = caDisplayLink
            ?: throw IllegalStateException("caDisplayLink is null during redrawer init")

        caDisplayLink.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)

        updateLayerOpacity()
    }

    override var isActive: Boolean
        get() = displayLinkConditions.isActive
        set(isActive) {
            if (displayLinkConditions.isActive != isActive) {
                displayLinkConditions.isActive = isActive

                if (isActive) {
                    setNeedsRedraw()
                } else {
                    dispatch_sync(renderingDispatchQueue) {}
                    // If an application enters the background, synchronously wait for inflightCommandBuffersGroup, as per
                    // https://developer.apple.com/documentation/metal/gpu_devices_and_work_submission/preparing_your_metal_app_to_run_in_the_background?language=objc
                    // Set the expiration time to 1 second to ensure that the main thread does not get stuck when the app is suspended.
                    dispatch_group_wait(inflightCommandBuffersGroup, dispatch_time(DISPATCH_TIME_NOW, 1L * NSEC_PER_SEC.toLong()))
                }
            }
        }

    override fun dispose() {
        check(caDisplayLink != null) { "MetalRedrawer.dispose() was called more than once" }

        retrieveInteropTransaction = {
            object : UIKitInteropTransaction {
                override val isInteropActive: Boolean = false
                override val actions = emptyList<UIKitInteropAction>()
            }
        }

        render = { _, _ -> }

        releaseCachedCommandQueue(queue)

        caDisplayLink?.invalidate()
        caDisplayLink = null

        // Wait until all scheduled rendering tasks are completed to eliminate race conditions
        // when clearing the resources
        trace("MetalRedrawer:dispose:waitForAsyncRenderingTasks") {
            dispatch_sync(renderingDispatchQueue) {}
        }

        pictureRecorder.close()
        context.close()
    }

    /**
     * Marks current state as dirty and unpauses display link if needed and enables draw dispatch operation on
     * next vsync
     */
    override fun setNeedsRedraw() {
        displayLinkConditions.setNeedsRedraw()
    }

    /**
     * Immediately dispatch draw and block the thread until it's finished and presented on the screen.
     */
    override fun draw(waitUntilCompletion: Boolean) {
        if (caDisplayLink == null) {
            return
        }
        draw(waitUntilCompletion, CACurrentMediaTime())
    }

    private var currentFrameRate: Float = Float.NaN

    override fun voteFrameRate(frameRate: Float, frameRateCategory: Float) {
        val frameRateCategoryValue = when (frameRateCategory) {
            FrameRateCategory.Default.value -> CAFrameRateRangeDefault.preferred
            FrameRateCategory.Normal.value -> 60f
            FrameRateCategory.High.value -> maximumFramesPerSecond.toFloat()
            else -> Float.NaN
        }

        val resolvedFrameRate = when {
            !frameRate.isNaN() && !frameRateCategoryValue.isNaN() -> maxOf(frameRate, frameRateCategoryValue)
            !frameRate.isNaN() -> frameRate
            !frameRateCategoryValue.isNaN() -> frameRateCategoryValue
            else -> return
        }

        if (currentFrameRate.isNaN() || resolvedFrameRate > currentFrameRate) {
            currentFrameRate = resolvedFrameRate
        }
    }

    /**
     * Encodes the frame and presents it on the screen.
     *
     * @param waitUntilCompletion if `true`, the method will block the thread until the frame is
     * presented on the screen. If false, the method will just dispatch GPU workload and return.
     * @param targetTimestamp the target timestamp for the frame to drive vsync-dependant time clock.
     */
    @OptIn(BetaInteropApi::class)
    private fun draw(waitUntilCompletion: Boolean, targetTimestamp: NSTimeInterval) =
        trace("MetalRedrawer:draw") {
            check(NSThread.isMainThread) { "MetalRedrawer.draw() must be called on main thread" }
            check(caDisplayLink != null) { "MetalRedrawer.draw() was called after dispose()" }

            lastRenderTimestamp = maxOf(targetTimestamp, lastRenderTimestamp)

            val (width, height) = metalLayer.drawableSize.useContents {
                width.roundToInt() to height.roundToInt()
            }

            if (width <= 0 || height <= 0) {
                return@trace
            }

            // Perform timestep and record all draw commands into [Picture]
            val picture = trace("MetalRedrawer:draw:pictureRecording") {
                pictureRecorder.beginRecording(
                    Rect(
                        left = 0f,
                        top = 0f,
                        width.toFloat(),
                        height.toFloat()
                    )
                ).also { canvas ->
                    render(canvas, lastRenderTimestamp)
                }

                pictureRecorder.finishRecordingAsPicture()
            }

            if (!currentFrameRate.isNaN()) {
                preferredFramesPerSecond = currentFrameRate.toLong()
                currentFrameRate = Float.NaN
            }

            val transactions = retrieveInteropTransaction()
            isInteropActive = transactions.isInteropActive

            val frame = Frame(
                picture = picture,
                size = IntSize(width, height),
                waitUntilCompletion = waitUntilCompletion,
                interopTransaction = transactions
            )

            if (waitUntilCompletion) {
                submitNextFrameForRenderLoop(null)
                // Ensure render queue is idle before synchronous presentation
                dispatch_sync(renderingDispatchQueue) {}
                renderAndPresentFrame(frame)
            } else {
                if (submitNextFrameForRenderLoop(frame)) {
                    dispatch_async(renderingDispatchQueue) {
                        startRenderLoop()
                    }
                }
            }
        }

    private class Frame(
        val picture: Picture,
        val size: IntSize,
        val waitUntilCompletion: Boolean,
        val interopTransaction: UIKitInteropTransaction,
    ) {
        fun dispose() {
            picture.close()
        }
    }

    private var nextFrameForRenderLoop: Frame? = null
    private var isRenderLoopActive: Boolean = false
    private val nextFrameLock = NSLock()

    private fun getNextFrameForRenderLoop(): Frame? {
        dispatch_assert_queue(renderingDispatchQueue)

        var frame: Frame? = null

        nextFrameLock.doLocked {
            frame = nextFrameForRenderLoop
            if (nextFrameForRenderLoop != null) {
                nextFrameForRenderLoop = null
                isRenderLoopActive = true
            } else {
                isRenderLoopActive = false
            }
        }

        return frame
    }

    private fun submitNextFrameForRenderLoop(frame: Frame?): Boolean {
        check(NSThread.isMainThread)

        var isDrawing = false

        nextFrameLock.doLocked {
            nextFrameForRenderLoop?.dispose()
            nextFrameForRenderLoop = frame
            isDrawing = isRenderLoopActive
        }

        return !isDrawing
    }

    @OptIn(BetaInteropApi::class)
    private fun startRenderLoop() {
        dispatch_assert_queue(renderingDispatchQueue)

        var frame = getNextFrameForRenderLoop()
        while (frame != null) {
            autoreleasepool {
                renderAndPresentFrame(frame)
            }

            frame = getNextFrameForRenderLoop()
        }
    }

    private fun renderAndPresentFrame(frame: Frame) {
        val drawable = trace("MetalRedrawer:draw:nextDrawable") {
            metalLayer.nextDrawable()
        }

        val presentAsynchronously = !NSThread.isMainThread && !frame.waitUntilCompletion

        if (drawable == null) {
            // Logger.warn { "'metalLayer.nextDrawable()' returned null. Skipping the frame." }
            frame.dispose()
            return
        }

        val renderTarget = BackendRenderTarget.makeMetal(
            frame.size.width,
            frame.size.height,
            texturePtr = drawable.texture.objcPtr()
        )

        val surface = Surface.makeFromBackendRenderTarget(
            context,
            renderTarget,
            SurfaceOrigin.TOP_LEFT,
            SurfaceColorFormat.BGRA_8888,
            ColorSpace.sRGB,
            SurfaceProps(pixelGeometry = PixelGeometry.UNKNOWN)
        )

        if (surface == null) {
            frame.dispose()
            renderTarget.close()
            metalLayer.releaseDrawable(drawable)
            return
        }

        surface.canvas.drawPicture(frame.picture)
        frame.dispose()
        surface.flushAndSubmit()

        surface.close()
        renderTarget.close()

        val commandBuffer = queue.commandBuffer()!!
        commandBuffer.label = "Present"

        metalLayer.prepareDrawableForPresent(drawable, commandBuffer)

        dispatch_group_enter(inflightCommandBuffersGroup)
        commandBuffer.addCompletedHandler {
            dispatch_group_leave(inflightCommandBuffersGroup)
        }
        if (presentAsynchronously) {
            commandBuffer.addScheduledHandler {
                metalLayer.presentDrawable(drawable) {
                    frame.interopTransaction.performTransaction()
                }
            }
        }
        commandBuffer.commit()

        // Present texture
        if (!presentAsynchronously) {
            commandBuffer.waitUntilScheduled()
            metalLayer.presentDrawable(drawable) {
                frame.interopTransaction.performTransaction()
            }
        }
        if (frame.waitUntilCompletion) {
            commandBuffer.waitUntilCompleted()
        }
    }

    companion object {
        private val renderingDispatchQueue =
            dispatch_queue_create(
                label = "RenderingDispatchQueue",
                attr = dispatch_queue_attr_make_with_qos_class(null, QOS_CLASS_USER_INTERACTIVE, 0)
            )

        private class CachedCommandQueue(
            val queue: MTLCommandQueueProtocol,
            var refCount: Int = 1
        )

        /**
         * Cached command queue record. Assumed to be associated with default MTLDevice.
         */
        private var cachedCommandQueue: CachedCommandQueue? = null

        /**
         * Get an existing command queue associated with the device or create a new one and cache it.
         * Assumed to be run on the main thread.
         */
        private fun getCachedCommandQueue(device: MTLDeviceProtocol): MTLCommandQueueProtocol {
            val cached = cachedCommandQueue
            if (cached != null) {
                cached.refCount++
                return cached.queue
            } else {
                val queue = device.newCommandQueue() ?: throw IllegalStateException("MTLDevice.newCommandQueue() returned null")
                cachedCommandQueue = CachedCommandQueue(queue)
                return queue
            }
        }

        /**
         * Release the cached command queue. Release the cache if refCount reaches 0.
         * Assumed to be run on the main thread.
         */
        private fun releaseCachedCommandQueue(queue: MTLCommandQueueProtocol) {
            val cached = cachedCommandQueue ?: return
            if (cached.queue == queue) {
                cached.refCount--
                if (cached.refCount == 0) {
                    cachedCommandQueue = null
                }
            }
        }
    }
}

private class SurfaceDisplayLinkProxy(
    private val callback: () -> Unit
) : NSObject() {
    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun handleDisplayLinkTick() {
        callback()
    }
}

private inline fun <T> NSLock.doLocked(block: () -> T): T {
    lock()

    try {
        return block()
    } finally {
        unlock()
    }
}
