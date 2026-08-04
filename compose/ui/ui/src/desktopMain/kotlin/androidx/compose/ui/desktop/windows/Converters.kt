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

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.jetbrains.desktop.win32.LogicalPoint
import org.jetbrains.desktop.win32.LogicalSize
import org.jetbrains.desktop.win32.PhysicalPoint

// win32 Logical* geometry is Float-valued (unlike macOS KDT's Double) — no widening conversions.

internal fun LogicalSize.roundToIntSize(density: Density): IntSize {
    return with(density) {
        IntSize(width.dp.roundToPx(), height.dp.roundToPx())
    }
}

internal fun LogicalSize.toDpSize(): DpSize {
    return DpSize(width.dp, height.dp)
}

internal fun LogicalPoint.toDpOffset(): DpOffset {
    return DpOffset(x.dp, y.dp)
}

internal fun DpOffset.toPxOffset(density: Density): Offset = with(density) {
    Offset(x.toPx(), y.toPx())
}

internal fun Offset.toLogicalPoint(density: Density): LogicalPoint {
    return with(density) {
        LogicalPoint(x.toDp().value, y.toDp().value)
    }
}

internal fun Offset.toPhysicalPoint(): PhysicalPoint {
    return PhysicalPoint(x.roundToInt(), y.roundToInt())
}

internal fun DpOffset.toLogicalPoint(): LogicalPoint =
    LogicalPoint(x.value, y.value)

internal fun Size.toLogicalSize(density: Density): LogicalSize {
    return with(density) {
        LogicalSize(
            width.toDp().value,
            height.toDp().value,
        )
    }
}

internal operator fun LogicalPoint.plus(other: LogicalPoint): LogicalPoint =
    LogicalPoint(x + other.x, y + other.y)

internal operator fun LogicalPoint.minus(other: LogicalPoint): LogicalPoint =
    LogicalPoint(x - other.x, y - other.y)
