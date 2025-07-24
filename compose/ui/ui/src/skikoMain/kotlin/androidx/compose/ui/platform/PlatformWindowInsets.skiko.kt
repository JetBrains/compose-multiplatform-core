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
import kotlin.jvm.JvmInline

@InternalComposeUiApi
interface PlatformWindowInsets {
    val captionBar: PlatformInsets get() = PlatformInsets.Zero
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
val PlatformWindowInsets.safeDrawing: PlatformInsets get() = InnermostPlatformInsets(
    arrayOf(statusBars, navigationBars, captionBar, displayCutout, ime, systemBars, tappableElement)
)

@InternalComposeUiApi
val PlatformWindowInsets.safeGestures: PlatformInsets get() = InnermostPlatformInsets(
    arrayOf(mandatorySystemGestures, systemGestures, tappableElement, waterfall)
)

@InternalComposeUiApi
val PlatformWindowInsets.safeContent: PlatformInsets get() = InnermostPlatformInsets(
    arrayOf(statusBars, navigationBars, captionBar, ime, systemGestures, mandatorySystemGestures, tappableElement, displayCutout, waterfall)
)

private class InnermostPlatformInsets(
    val insets: Array<out PlatformInsets>
): PlatformInsets {
    override val left: Int get() = if (insets.isEmpty()) 0 else insets.maxOf { it.left }
    override val top: Int get() = if (insets.isEmpty()) 0 else insets.maxOf { it.top }
    override val right: Int get() = if (insets.isEmpty()) 0 else insets.maxOf { it.right }
    override val bottom: Int get() = if (insets.isEmpty()) 0 else insets.maxOf { it.bottom }
}

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
        val Unspecified: PlatformInsets = ValueInsets(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        val Zero: PlatformInsets = ValueInsets(0,0,0,0)
    }
}

@InternalComposeUiApi
@JvmInline
internal value class ValueInsets internal constructor(
    val packedValue: Long
): PlatformInsets {
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
        val ZERO = ValueInsets(0L)
    }
}

@InternalComposeUiApi
internal inline fun ValueInsets(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0
): ValueInsets = ValueInsets(
    (left.toLong() shl 48) or
        (top.toLong() shl 32) or
        (right.toLong() shl 16) or
        bottom.toLong()
)