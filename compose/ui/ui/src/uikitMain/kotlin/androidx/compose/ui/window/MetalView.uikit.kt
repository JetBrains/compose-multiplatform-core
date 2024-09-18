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

import androidx.compose.ui.viewinterop.UIKitInteropTransaction
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Canvas
import platform.CoreGraphics.CGRectIsEmpty
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSTimeInterval
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.QuartzCore.CAMetalLayer
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewMeta

internal class MetalView(
    retrieveInteropTransaction: () -> UIKitInteropTransaction,
    render: (Canvas, nanoTime: Long) -> Unit,
) : UIView(frame = CGRectZero.readValue()) {
    companion object : UIViewMeta() {
        @BetaInteropApi
        override fun layerClass() = CAMetalLayer
    }

    private val device: MTLDeviceProtocol =
        MTLCreateSystemDefaultDevice()
            ?: throw IllegalStateException("Metal is not supported on this system")

    private val metalLayer: CAMetalLayer get() = layer as CAMetalLayer

    val redrawer = MetalRedrawer(
        metalLayer,
        retrieveInteropTransaction,
    ) { canvas, targetTimestamp ->
        render(canvas, targetTimestamp.toNanoSeconds())
    }

    /**
     * @see [MetalRedrawer.canBeOpaque]
     */
    var canBeOpaque by redrawer::canBeOpaque

    /**
     * @see [MetalRedrawer.needsProactiveDisplayLink]
     */
    var needsProactiveDisplayLink by redrawer::needsProactiveDisplayLink

    /**
     * Indicates that the view needs to be drawn synchronously with the next layout pass to avoid
     * flickering.
     */
    private var needsSynchronousDraw = true

    /**
     * Raise the flag to indicate that the view needs to be drawn synchronously with the next layout.
     */
    fun setNeedsSynchronousDrawOnNextLayout() {
        needsSynchronousDraw = true
    }

    init {
        userInteractionEnabled = false

        metalLayer.also {
            // Workaround for cinterop issue
            // Type mismatch: inferred type is platform.Metal.MTLDeviceProtocol but objcnames.protocols.MTLDeviceProtocol? was expected
            it.device = device as objcnames.protocols.MTLDeviceProtocol?

            it.pixelFormat = MTLPixelFormatBGRA8Unorm
            it.backgroundColor = UIColor.clearColor.CGColor
            it.framebufferOnly = false
        }
    }

    fun dispose() {
        redrawer.dispose()
    }

    override fun didMoveToWindow() {
        super.didMoveToWindow()

        val window = window ?: return

        val screen = window.screen
        contentScaleFactor = screen.scale
        redrawer.maximumFramesPerSecond = screen.maximumFramesPerSecond

        updateMetalLayerSize()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()

        updateMetalLayerSize()
    }

    private fun updateMetalLayerSize() {
        if (window == null || CGRectIsEmpty(bounds)) {
            return
        }

        metalLayer.drawableSize = bounds.useContents {
            CGSizeMake(
                width = size.width * contentScaleFactor,
                height = size.height * contentScaleFactor
            )
        }

        if (needsSynchronousDraw) {
            redrawer.drawSynchronously()

            needsSynchronousDraw = false
        }
    }

    override fun canBecomeFirstResponder() = false
}

private fun NSTimeInterval.toNanoSeconds(): Long {
    // The calculation is split in two instead of
    // `(targetTimestamp * 1e9).toLong()`
    // to avoid losing precision for fractional part
    val integral = floor(this)
    val fractional = this - integral
    val secondsToNanos = 1_000_000_000L
    val nanos = integral.roundToLong() * secondsToNanos + (fractional * 1e9).roundToLong()
    return nanos
}
