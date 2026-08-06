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

internal fun UIImage.forEachPixel(step: Int = 1, onPixel: (x: Int, y: Int, color: Color) -> Unit) {
    forEachPixelWhile(step) { x, y, color ->
        onPixel(x, y, color)
        true
    }
}

/**
 * Counts sampled pixels matching [color], stopping once [maxCount] matching pixels are found.
 */
internal fun UIImage.countPixels(
    color: Color,
    step: Int = 1,
    maxCount: Int = Int.MAX_VALUE,
): Int {
    if (maxCount <= 0) return 0

    var count = 0
    forEachPixelWhile(step) { _, _, pixelColor ->
        if (pixelColor == color) {
            count++
        }
        count < maxCount
    }
    return count
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.forEachPixelWhile(
    step: Int,
    onPixel: (x: Int, y: Int, color: Color) -> Boolean,
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

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val offset = (y * bytesPerRow) + (x * bytesPerPixel)
                val r = pinned.get()[offset].toUByte().toInt()
                val g = pinned.get()[offset + 1].toUByte().toInt()
                val b = pinned.get()[offset + 2].toUByte().toInt()
                val a = pinned.get()[offset + 3].toUByte().toInt()

                if (!onPixel(x, y, Color(r, g, b, a))) {
                    return@usePinned
                }
            }
        }
    }
}
