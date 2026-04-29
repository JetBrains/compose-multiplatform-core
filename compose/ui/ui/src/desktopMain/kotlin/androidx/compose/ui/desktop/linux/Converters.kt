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

package androidx.compose.ui.desktop.linux

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.jetbrains.desktop.linux.LogicalPoint
import org.jetbrains.desktop.linux.LogicalRect
import org.jetbrains.desktop.linux.LogicalSize

internal fun LogicalSize.roundToIntSize(density: Density): IntSize {
    return with(density) {
        IntSize(width.toInt().dp.roundToPx(), height.toInt().dp.roundToPx())
    }
}

internal fun LogicalSize.toDpSize(): DpSize {
    return DpSize(width.toInt().dp, height.toInt().dp)
}

internal fun LogicalPoint.toDpOffset(): DpOffset {
    return DpOffset(x.dp, y.dp)
}

internal fun Offset.toLogicalPoint(density: Density): LogicalPoint {
    return with(density) {
        LogicalPoint(x.toDp().value.toDouble(), y.toDp().value.toDouble())
    }
}

internal fun Size.toLogicalSize(density: Density): LogicalSize {
    return with(density) {
        LogicalSize(width.toDp().value.roundToInt().toUInt(), height.toDp().value.roundToInt().toUInt())
    }
}

internal operator fun LogicalPoint.plus(other: LogicalPoint): LogicalPoint =
    LogicalPoint(x + other.x, y + other.y)

internal operator fun LogicalPoint.minus(other: LogicalPoint): LogicalPoint =
    LogicalPoint(x - other.x, y - other.y)

internal fun Rect.toLogicalRect(density: Density): LogicalRect {
    return with(density) {
        LogicalRect(
            x = left.toDp().value.roundToInt().toUInt(),
            y = top.toDp().value.roundToInt().toUInt(),
            width = width.toDp().value.roundToInt().toUInt(),
            height = height.toDp().value.roundToInt().toUInt(),
        )
    }
}
