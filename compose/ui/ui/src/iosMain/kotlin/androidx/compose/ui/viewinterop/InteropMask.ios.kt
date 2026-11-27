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

package androidx.compose.ui.viewinterop

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toCGRect
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGPathAddLineToPoint
import platform.CoreGraphics.CGPathCloseSubpath
import platform.CoreGraphics.CGPathCreateMutable
import platform.CoreGraphics.CGPathMoveToPoint
import platform.QuartzCore.CAShapeLayer
import platform.UIKit.UIColor

/**
 * Maintains and applies the UIKit mask used by overlay interop to clip only at Compose clip
 * boundaries.
 *
 * Geometry and scene-occluder changes are coalesced into one final-frame update. This keeps mask
 * changes synchronized with the frame while avoiding multiple mask recalculations when both the
 * interop position and an occluding scene move during the same frame.
 */
internal class InteropMask(
    private val interopWrappingView: InteropWrappingView,
    private val isPlacedAsOverlay: () -> Boolean,
    private val density: () -> Density,
    private val finalFrameUpdateScheduler: FinalFrameUpdateScheduler,
) {
    private var rootSize: IntSize? = null
    private var unclippedRect: IntRect? = null
    private var clippedRect: IntRect? = null
    private var occluderRects: List<IntRect> = emptyList()
    private var currentMaskKey: MaskKey? = null
    private var isMaskInitialized = false
    private var interopMaskLayer: CAShapeLayer? = null
    private var currentFrame: IntRect? = null
    private var currentVisibleRects: List<IntRect>? = null

    val hasInstalledMask: Boolean
        get() = interopMaskLayer != null

    // Distinguishes final-frame callbacks queued before a clear from those queued after it.
    private var updateGeneration = 0

    fun update(
        rootSize: IntSize,
        unclippedRect: IntRect,
        clippedRect: IntRect,
        transactionScope: InteropTransactionScope,
    ) {
        this.rootSize = rootSize
        this.unclippedRect = unclippedRect
        this.clippedRect = clippedRect

        // Background interop is clipped by the wrapping view and never needs a mask. Keep the
        // geometry so a later switch to overlay can resolve the mask without another layout pass.
        if (!isPlacedAsOverlay()) return

        scheduleUpdate(transactionScope)
    }

    fun updateSceneLayerMask(occluderRects: List<IntRect>) {
        if (this.occluderRects == occluderRects) return

        this.occluderRects = occluderRects
        // A pending geometry update already has a final-frame callback. Let that callback apply the
        // combined geometry and occluder state instead of updating the mask twice in one frame.
        if (finalFrameUpdateScheduler.isScheduled) return

        applyCurrentLayout()
    }

    fun scheduleUpdate(transactionScope: InteropTransactionScope? = null) {
        val scheduledGeneration = updateGeneration
        val action: () -> Unit = {
            if (scheduledGeneration == updateGeneration) {
                applyCurrentLayout()
            }
        }

        if (transactionScope == null) {
            finalFrameUpdateScheduler.schedule(action)
        } else {
            finalFrameUpdateScheduler.schedule(transactionScope, action)
        }
    }

    /** Removes the installed mask and clears all cached mask state. */
    fun clear() {
        updateGeneration++
        finalFrameUpdateScheduler.cancel()
        rootSize = null
        unclippedRect = null
        clippedRect = null
        occluderRects = emptyList()
        currentMaskKey = null
        isMaskInitialized = false
        currentFrame = null
        currentVisibleRects = null
        interopWrappingView.setInteropMaskLayer(null)
        interopMaskLayer = null
        interopWrappingView.setWaitingForInteropMask(isPlacedAsOverlay())
    }

    private fun hasLayout() = rootSize != null && unclippedRect != null && clippedRect != null

    private fun applyCurrentLayout() {
        val maskKey = calculateMask()
        if (isMaskInitialized && maskKey == currentMaskKey) return

        isMaskInitialized = true
        currentMaskKey = maskKey
        updateMask(frame = maskKey?.frame, visibleRects = maskKey?.visibleRects.orEmpty())
    }

    /**
     * Updates the UIKit mask that clips overlay interop at Compose clipping boundaries.
     *
     * A null [frame] removes the mask, allowing UIKit effects such as shadows to overflow.
     * [visibleRects] are root-space rectangles in the mask layer's local coordinate system.
     */
    private fun updateMask(frame: IntRect?, visibleRects: List<IntRect>) {
        if (!isPlacedAsOverlay() || frame == null) {
            interopWrappingView.setInteropMaskLayer(null)
            interopMaskLayer = null
            currentFrame = null
            currentVisibleRects = null
            if (hasLayout()) {
                interopWrappingView.setWaitingForInteropMask(false)
            }
            return
        }

        val maskLayer =
            interopMaskLayer
                ?: CAShapeLayer().also {
                    it.fillColor = UIColor.whiteColor.CGColor
                    interopMaskLayer = it
                }

        if (currentFrame != frame) {
            currentFrame = frame
            maskLayer.frame = frame.toCGRect(density())
        }
        if (currentVisibleRects != visibleRects) {
            currentVisibleRects = visibleRects
            val path = createMaskPath(visibleRects)
            maskLayer.path = path
            // CGPathCreateMutable follows the Core Foundation Create Rule. CAShapeLayer retains the
            // path, so release this temporary creator reference after assigning it.
            CFRelease(path)
        }
        interopWrappingView.setInteropMaskLayer(maskLayer)
        interopWrappingView.setWaitingForInteropMask(false)
    }

    private fun createMaskPath(visibleRects: List<IntRect>) =
        CGPathCreateMutable().also { path ->
            val scale = density().density
            visibleRects.forEach { rect ->
                val left = (rect.left / scale).toDouble()
                val top = (rect.top / scale).toDouble()
                val right = (rect.right / scale).toDouble()
                val bottom = (rect.bottom / scale).toDouble()

                CGPathMoveToPoint(path, null, left, top)
                CGPathAddLineToPoint(path, null, right, top)
                CGPathAddLineToPoint(path, null, right, bottom)
                CGPathAddLineToPoint(path, null, left, bottom)
                CGPathCloseSubpath(path)
            }
        }

    /**
     * Creates a root-sized UIKit mask. Its path contains rectangles for the Compose clip region
     * not covered by higher-z scene layers.
     */
    private fun calculateMask(): MaskKey? {
        val rootSize = rootSize ?: return null
        val unclippedRect = unclippedRect ?: return null
        val clippedRect = clippedRect ?: return null
        if (!isPlacedAsOverlay() || rootSize.width <= 0 || rootSize.height <= 0) {
            return null
        }

        val rootBounds = IntRect(0, 0, rootSize.width, rootSize.height)

        val clipLeft = if (unclippedRect.left < clippedRect.left) clippedRect.left else 0
        val clipTop = if (unclippedRect.top < clippedRect.top) clippedRect.top else 0
        val clipRight =
            if (unclippedRect.right > clippedRect.right) clippedRect.right else rootSize.width
        val clipBottom =
            if (unclippedRect.bottom > clippedRect.bottom) clippedRect.bottom else rootSize.height
        // Only sides crossed by the interop bounds constrain the mask; all other sides extend to
        // the root so native effects are not clipped unnecessarily.
        val visibleRect = IntRect(clipLeft, clipTop, clipRight, clipBottom).intersect(rootBounds)

        // The mask's local coordinate system is root space. Its origin is shifted into the
        // wrapping view's local space because the wrapper itself is positioned at clippedRect.
        val frame =
            IntRect(
                left = -clippedRect.left,
                top = -clippedRect.top,
                right = rootSize.width - clippedRect.left,
                bottom = rootSize.height - clippedRect.top,
            )
        if (visibleRect.isEmpty) {
            return MaskKey(frame = frame, visibleRects = emptyList())
        }

        if (
            unclippedRect == clippedRect &&
                visibleRect == rootBounds &&
                occluderRects.none { it.overlaps(visibleRect) }
        )
            return null

        return MaskKey(frame = frame, visibleRects = visibleRect.subtractOccluders(occluderRects))
    }

    private fun IntRect.subtractOccluders(occluderRects: List<IntRect>): List<IntRect> =
        // Fold, rather than subtract overlapping occluders from one path, to keep overlapping
        // scene layers well defined.
        occluderRects.fold(listOf(this)) { visibleRects, occluder ->
            visibleRects.flatMap { it.subtractOccluder(occluder) }
        }

    private fun IntRect.subtractOccluder(occluder: IntRect): List<IntRect> {
        if (!overlaps(occluder)) return listOf(this)

        val overlap = intersect(occluder)
        if (overlap == this) return emptyList()

        return buildList {
            addIfNotEmpty(left, top, right, overlap.top)
            addIfNotEmpty(left, overlap.bottom, right, bottom)
            addIfNotEmpty(left, overlap.top, overlap.left, overlap.bottom)
            addIfNotEmpty(overlap.right, overlap.top, right, overlap.bottom)
        }
    }

    private fun MutableList<IntRect>.addIfNotEmpty(left: Int, top: Int, right: Int, bottom: Int) {
        if (left < right && top < bottom) {
            add(IntRect(left, top, right, bottom))
        }
    }

    private data class MaskKey(val frame: IntRect, val visibleRects: List<IntRect>)
}
