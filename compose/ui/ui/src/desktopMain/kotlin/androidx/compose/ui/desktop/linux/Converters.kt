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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.roundToInt
import org.jetbrains.desktop.linux.DragAndDropAction
import org.jetbrains.desktop.linux.LogicalPoint
import org.jetbrains.desktop.linux.LogicalRect
import org.jetbrains.desktop.linux.LogicalSize

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

internal fun Offset.toLogicalPoint(density: Density): LogicalPoint {
    return with(density) {
        LogicalPoint(x.toDp().value.toDouble(), y.toDp().value.toDouble())
    }
}

internal fun Size.toLogicalSize(density: Density): LogicalSize {
    return with(density) {
        LogicalSize(width.toDp().value.roundToInt(), height.toDp().value.roundToInt())
    }
}

internal operator fun LogicalPoint.plus(other: LogicalPoint): LogicalPoint =
    LogicalPoint(x + other.x, y + other.y)

internal operator fun LogicalPoint.minus(other: LogicalPoint): LogicalPoint =
    LogicalPoint(x - other.x, y - other.y)

internal fun Rect.toLogicalRect(density: Density): LogicalRect {
    return with(density) {
        LogicalRect(
            x = left.toDp().value.roundToInt(),
            y = top.toDp().value.roundToInt(),
            width = width.toDp().value.roundToInt(),
            height = height.toDp().value.roundToInt(),
        )
    }
}

internal fun DpRect.toLogicalRect(): LogicalRect {
    return LogicalRect(
        x = left.value.roundToInt(),
        y = top.value.roundToInt(),
        width = width.value.roundToInt(),
        height = height.value.roundToInt(),
    )
}

internal fun DpOffset.toPxOffset(density: Density): Offset = with(density) {
    Offset(x.toPx(), y.toPx())
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun DragAndDropTransferAction.toLinuxAction(): DragAndDropAction? {
    return when (this) {
        DragAndDropTransferAction.Copy -> DragAndDropAction.Copy
        DragAndDropTransferAction.Move -> DragAndDropAction.Move
        else -> null
    }
}
