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

import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImage
import kotlin.math.ceil
import kotlin.math.sqrt

internal fun UIImage.forEachPixel(step: Int = 1, onPixel: (x: Int, y: Int, color: Color) -> Unit) {
    require(step > 0) { "step must be positive" }

    withPixelReader { width, height, colorAt ->
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                onPixel(x, y, colorAt(x, y))
            }
        }
    }
}

/**
 * Visits at most [maxSamples] points in an aspect-ratio-aware grid spanning the entire image.
 */
internal fun UIImage.forEachSampledPixel(
    maxSamples: Int,
    onPixel: (x: Int, y: Int, color: Color) -> Unit,
) {
    require(maxSamples > 0) { "maxSamples must be positive" }

    withPixelReader { width, height, colorAt ->
        val samples = minOf(maxSamples.toLong(), width.toLong() * height).toInt()
        val columns = minOf(
            width,
            samples,
            ceil(sqrt(samples.toDouble() * width / height)).toInt(),
        )
        val rows = minOf(height, samples / columns)

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val x = ((column + 0.5) * width / columns).toInt()
                val y = ((row + 0.5) * height / rows).toInt()
                onPixel(x, y, colorAt(x, y))
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.withPixelReader(
    block: (width: Int, height: Int, colorAt: (x: Int, y: Int) -> Color) -> Unit,
) {
    val cgImage = this.CGImage
    val width = CGImageGetWidth(cgImage).toInt()
    val height = CGImageGetHeight(cgImage).toInt()
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bytesPerPixel = 4
    val bytesPerRow = bytesPerPixel * width
    val rawData = ByteArray(height * bytesPerRow)

    rawData.usePinned { pinned ->
        val context = CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8UL,
            bytesPerRow = bytesPerRow.toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
        )

        CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)

        block(width, height) { x, y ->
            val offset = (y * bytesPerRow) + (x * bytesPerPixel)
            val r = pinned.get()[offset].toUByte().toInt()
            val g = pinned.get()[offset + 1].toUByte().toInt()
            val b = pinned.get()[offset + 2].toUByte().toInt()
            val a = pinned.get()[offset + 3].toUByte().toInt()
            Color(r, g, b, a)
        }
    }
}
