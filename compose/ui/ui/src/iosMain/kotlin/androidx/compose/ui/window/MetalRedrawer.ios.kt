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

import androidx.collection.IntIntPair
import androidx.compose.ui.uikit.utils.CMPMetalDrawablesHandler
import androidx.compose.ui.util.trace
import androidx.compose.ui.viewinterop.InteropSyncAction
import androidx.compose.ui.viewinterop.InteropSyncTransaction
import kotlin.math.roundToInt
import kotlinx.cinterop.*
import org.jetbrains.skia.*
import platform.Foundation.NSThread
import platform.QuartzCore.*
import platform.darwin.*
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLDeviceProtocol

internal sealed interface MetalRedrawer {
    fun render(waitUntilCompletion: Boolean)
    var isForcedToPresentWithTransactionEveryFrame: Boolean
    fun awaitRenderingCompletion()
    fun dispose()
}

// https://youtrack.jetbrains.com/issue/CMP-9722
// Copy of the class SurfaceMetalRedrawer with a different layer.
// All changes made here must also be implemented in the `SurfaceMetalRedrawer`.
internal class LegacyMetalRedrawer(
    private val metalLayer: CAMetalLayer,
    private var retrieveInteropTransaction: () -> InteropSyncTransaction,
    private var draw: (Canvas) -> Unit,
): MetalRedrawer {
    /**
     * A wrapper around CAMetalLayer that allows to perform operations on its drawables without
     * exposing the objects to Kotlin/Native runtime and thus allowing explicit lifetime control of them.
     *
     * See ObjC implementation of [CMPMetalDrawablesHandler] for more details.
     */
    private val metalDrawablesHandler = CMPMetalDrawablesHandler(metalLayer)
    // Workaround for KN compiler bug
    // Type mismatch: inferred type is objcnames.protocols.MTLDeviceProtocol but platform.Metal.MTLDeviceProtocol was expected
    @Suppress("USELESS_CAST")
    private val device = metalLayer.device as MTLDeviceProtocol?
        ?: throw IllegalStateException("CAMetalLayer.device can not be null")
    private val queue = getCachedCommandQueue(device)
    private val context = DirectContext.makeMetal(device.objcPtr(), queue.objcPtr())
    private val pictureRecorder = PictureRecorder()
    private val inflightCommandBuffersGroup = dispatch_group_create()
    // A guard flag to have proper assertion when draw() method is called recursively.
    private var isDrawRecursiveCall = false

    override var isForcedToPresentWithTransactionEveryFrame = false

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
                // Rendering into opaque CAMetalLayer allows direct-to-screen optimization.
                updateLayerOpacity()
                metalLayer.drawsAsynchronously = !value
            }
        }

    private fun updateLayerOpacity() {
        metalLayer.setOpaque(!isInteropActive && canBeOpaque)
    }

    init {
        updateLayerOpacity()
    }

    private var isDisposed = false

    override fun awaitRenderingCompletion() {
        // If an application enters the background, synchronously wait for inflightCommandBuffersGroup, as per
        // https://developer.apple.com/documentation/metal/gpu_devices_and_work_submission/preparing_your_metal_app_to_run_in_the_background?language=objc
        // Set the expiration time to 1 second to ensure that the main thread does not get stuck when the app is suspended.
        dispatch_group_wait(
            inflightCommandBuffersGroup,
            dispatch_time(DISPATCH_TIME_NOW, 1L * NSEC_PER_SEC.toLong())
        )
    }

    override fun dispose() {
        check(!isDisposed) { "MetalRedrawer.dispose() was called more than once" }
        isDisposed = true

        retrieveInteropTransaction = {
            object : InteropSyncTransaction {
                override val isInteropActive: Boolean = false
                override val actions = emptyList<InteropSyncAction>()
            }
        }

        draw = { _ -> }

        releaseCachedCommandQueue(queue)

        pictureRecorder.close()
        context.close()
    }

    /**
     * Encodes the frame and presents it on the screen.
     *
     * @param waitUntilCompletion if `true`, the method will block the thread until the frame is
     * presented on the screen. If false, the method will just dispatch GPU workload and return.
     */
    @OptIn(BetaInteropApi::class)
    override fun render(waitUntilCompletion: Boolean) = trace("MetalRedrawer:draw") {
        check(NSThread.isMainThread)
        check(!isDrawRecursiveCall) {
            "Attempt to call MetalRedrawer.draw() recursively which may lead to the PictureRecorder corruption."
        }
        isDrawRecursiveCall = true

        try {
            autoreleasepool {
                val (width, height) = metalLayer.drawableSize.useContents {
                    IntIntPair(width.roundToInt(), height.roundToInt())
                }

                if (width <= 0 || height <= 0) {
                    return@autoreleasepool
                }

                // Perform timestep and record all draw commands into [Picture]
                val picture = trace("MetalRedrawer:draw:pictureRecording") {
                    pictureRecorder.beginRecording(
                        left = 0f,
                        top = 0f,
                        width.toFloat(),
                        height.toFloat()
                    ).also { canvas ->
                        draw(canvas)
                    }

                    pictureRecorder.finishRecordingAsPicture()
                }

                val metalDrawable = trace("MetalRedrawer:draw:nextDrawable") {
                    metalDrawablesHandler.nextDrawable()
                }

                if (metalDrawable == null) {
                    // TODO: anomaly, log
                    // Logger.warn { "'metalLayer.nextDrawable()' returned null. 'metalLayer.allowsNextDrawableTimeout' should be set to false. Skipping the frame." }
                    picture.close()
                    return@autoreleasepool
                }

                val renderTarget = BackendRenderTarget.makeMetal(
                    width,
                    height,
                    texturePtr = metalDrawablesHandler.drawableTexture(metalDrawable).rawValue
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
                    // TODO: anomaly, log
                    // Logger.warn { "'Surface.makeFromBackendRenderTarget' returned null. Skipping the frame." }
                    picture.close()
                    renderTarget.close()
                    metalDrawablesHandler.releaseDrawable(metalDrawable)
                    return@autoreleasepool
                }

                val interopTransaction = retrieveInteropTransaction()

                val presentsWithTransaction =
                    isForcedToPresentWithTransactionEveryFrame
                        || interopTransaction.actions.isNotEmpty()
                        || isInteropActive != interopTransaction.isInteropActive
                metalLayer.presentsWithTransaction = presentsWithTransaction

                if (interopTransaction.isInteropActive) {
                    isInteropActive = true
                }

                trace("MetalRedrawer:draw:encodeAndPresent") {
                    surface.canvas.drawPicture(picture)
                    picture.close()
                    surface.flushAndSubmit()

                    surface.close()
                    renderTarget.close()

                    val commandBuffer = queue.commandBuffer()!!
                    commandBuffer.label = "Present"

                    if (!presentsWithTransaction) {
                        // scheduleDrawablePresentation consumes metalDrawable
                        // don't use metalDrawable after this call
                        metalDrawablesHandler.scheduleDrawablePresentation(
                            metalDrawable,
                            commandBuffer
                        )
                    }

                    dispatch_group_enter(inflightCommandBuffersGroup)
                    commandBuffer.addScheduledHandler {
                        dispatch_group_leave(inflightCommandBuffersGroup)
                    }
                    commandBuffer.commit()

                    if (presentsWithTransaction) {
                        // If there are pending changes in UIKit interop, [waitUntilScheduled](https://developer.apple.com/documentation/metal/mtlcommandbuffer/1443036-waituntilscheduled) is called
                        // to ensure that transaction is available
                        trace("MetalRedrawer:draw:waitTransaction") {
                            commandBuffer.waitUntilScheduled()
                        }

                        // presentDrawable consumes metalDrawable
                        // don't use metalDrawable after this call
                        metalDrawablesHandler.presentDrawable(metalDrawable)

                        interopTransaction.performTransaction()

                        if (interopTransaction.isInteropActive.not()) {
                            isInteropActive = false
                        }
                    }

                    if (waitUntilCompletion) {
                        trace("MetalRedrawer:draw:waitUntilCompleted") {
                            commandBuffer.waitUntilCompleted()
                        }
                    }
                }
            }
        } finally {
            isDrawRecursiveCall = false
        }
    }

    companion object {
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
