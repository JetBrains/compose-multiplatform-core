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
package androidx.compose.ui.platform

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.jvm.JvmInline

@InternalComposeUiApi
interface PlatformWindowInsets {
    /**
     * Returns a list of [Rect] objects representing the bounds of display cutouts (such as notches,
     * camera holes, or other areas that intrude into the screen).
     *
     * This property provides the actual geometric bounds of the cutouts themselves, while
     * [displayCutout] provides the safe insets that content should observe to avoid these cutouts.
     *
     * Different platforms may represent cutouts differently:
     * - On platforms with a notch (like iOS), this typically includes a rectangle at the top of the screen
     * - On platforms with camera holes, this includes circles or rounded rectangles where cameras or sensors are located
     * - On platforms with curved edges or "waterfall" displays, this may include areas along the edges
     *
     * The coordinates of these rectangles are relative to the containing window or scene.
     * An empty list is returned when there are no display cutouts.
     */
    val displayCutouts: List<Rect> get() = emptyList()
    val captionBar: PlatformInsets get() = PlatformInsets.Zero
    /**
     * Represents the safe inset areas that content should observe to avoid all display cutouts.
     *
     * Unlike [displayCutouts] which provides the actual geometric bounds of cutouts, this property
     * provides aggregated inset values for all sides of the screen to avoid any cutouts.
     */
    val displayCutout: PlatformInsets get() = PlatformInsets.Zero
    val ime: PlatformInsets get() = PlatformInsets.Zero
    val mandatorySystemGestures: PlatformInsets get() = PlatformInsets.Zero
    val navigationBars: PlatformInsets get() = PlatformInsets.Zero
    val statusBars: PlatformInsets get() = PlatformInsets.Zero
    val systemBars: PlatformInsets get() = PlatformInsets.Zero
    val systemGestures: PlatformInsets get() = PlatformInsets.Zero
    val tappableElement: PlatformInsets get() = PlatformInsets.Zero
    val waterfall: PlatformInsets get() = PlatformInsets.Zero
}

@InternalComposeUiApi
val PlatformWindowInsets.safeDrawing: PlatformInsets get() = PlatformInsets(
    left = maxOf(statusBars.left, navigationBars.left, captionBar.left, displayCutout.left, ime.left, systemBars.left, tappableElement.left),
    top = maxOf(statusBars.top, navigationBars.top, captionBar.top, displayCutout.top, ime.top, systemBars.top, tappableElement.top),
    right = maxOf(statusBars.right, navigationBars.right, captionBar.right, displayCutout.right, ime.right, systemBars.right, tappableElement.right),
    bottom = maxOf(statusBars.bottom, navigationBars.bottom, captionBar.bottom, displayCutout.bottom, ime.bottom, systemBars.bottom, tappableElement.bottom)
)

@InternalComposeUiApi
val PlatformWindowInsets.safeGestures: PlatformInsets get() = PlatformInsets(
    left = maxOf(mandatorySystemGestures.left, systemGestures.left, tappableElement.left, waterfall.left),
    top = maxOf(mandatorySystemGestures.top, systemGestures.top, tappableElement.top, waterfall.top),
    right = maxOf(mandatorySystemGestures.right, systemGestures.right, tappableElement.right, waterfall.right),
    bottom = maxOf(mandatorySystemGestures.bottom, systemGestures.bottom, tappableElement.bottom, waterfall.bottom)
)

@InternalComposeUiApi
val PlatformWindowInsets.safeContent: PlatformInsets get() = PlatformInsets(
    left = maxOf(statusBars.left, navigationBars.left, captionBar.left, ime.left, systemGestures.left, mandatorySystemGestures.left, tappableElement.left, displayCutout.left, waterfall.left),
    top = maxOf(statusBars.top, navigationBars.top, captionBar.top, ime.top, systemGestures.top, mandatorySystemGestures.top, tappableElement.top, displayCutout.top, waterfall.top),
    right = maxOf(statusBars.right, navigationBars.right, captionBar.right, ime.right, systemGestures.right, mandatorySystemGestures.right, tappableElement.right, displayCutout.right, waterfall.right),
    bottom = maxOf(statusBars.bottom, navigationBars.bottom, captionBar.bottom, ime.bottom, systemGestures.bottom, mandatorySystemGestures.bottom, tappableElement.bottom, displayCutout.bottom, waterfall.bottom)
)

/**
 * This class represents platform insets.
 */
@InternalComposeUiApi
interface PlatformInsets {
    /**
     * The left inset in pixels.
     */
    val left: Int

    /**
     * The top inset in pixels.
     */
    val top: Int

    /**
     * The right inset in pixels.
     */
    val right: Int

    /**
     * The bottom inset in pixels.
     */
    val bottom: Int

    companion object {
        val Zero: PlatformInsets = ValuePlatformInsets(0L)
    }
}

@InternalComposeUiApi
fun Density.PlatformInsets(
    left: Dp = 0.dp,
    top: Dp = 0.dp,
    right: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PlatformInsets = ValuePlatformInsets(
    left.roundToPx(),
    top.roundToPx(),
    right.roundToPx(),
    bottom.roundToPx()
)

@InternalComposeUiApi
fun PlatformInsets(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0
): PlatformInsets = ValuePlatformInsets(left, top, right, bottom)

@JvmInline
private value class ValuePlatformInsets(
    val packedValue: Long
): PlatformInsets {

    constructor(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ): this(checkBoundsAndPackInsets(left, top, right, bottom))

    override val left: Int
        inline get() = ((packedValue ushr 48) and 0xFFFF).toInt()

    override val top: Int
        inline get() = ((packedValue ushr 32) and 0xFFFF).toInt()

    override val right: Int
        inline get() = ((packedValue ushr 16) and 0xFFFF).toInt()

    override val bottom: Int
        inline get() = (packedValue and 0xFFFF).toInt()

    override fun toString(): String {
        return "ValueInsets($left, $top, $right, $bottom)"
    }

    companion object {
        private fun checkBoundsAndPackInsets(left: Int, top: Int, right: Int, bottom: Int): Long {
            checkBounds(left, "left")
            checkBounds(top, "top")
            checkBounds(right, "right")
            checkBounds(bottom, "bottom")
            return (left.toLong() shl 48) or
                (top.toLong() shl 32) or
                (right.toLong() shl 16) or
                bottom.toLong()
        }

        private fun checkBounds(value: Int, name: String) {
            check(value in 0..0xFFFF) {
                "$name should be in 0..0xFFFF range, but was $value"
            }
        }
    }
}