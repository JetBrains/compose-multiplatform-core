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

package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.jetbrains.desktop.gtk.DragAndDropAction
import org.jetbrains.desktop.gtk.LogicalPoint
import org.jetbrains.desktop.gtk.LogicalSize

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

internal fun Size.toLogicalSize(density: Density): LogicalSize {
    return with(density) {
        LogicalSize(width.toDp().value.roundToInt(), height.toDp().value.roundToInt())
    }
}

internal operator fun LogicalPoint.plus(other: LogicalPoint): LogicalPoint =
    LogicalPoint(x + other.x, y + other.y)

internal operator fun LogicalPoint.minus(other: LogicalPoint): LogicalPoint =
    LogicalPoint(x - other.x, y - other.y)

@OptIn(ExperimentalComposeUiApi::class)
internal fun DragAndDropTransferAction.toGtkAction(): DragAndDropAction? {
    return when (this) {
        DragAndDropTransferAction.Copy -> DragAndDropAction.Copy
        DragAndDropTransferAction.Move -> DragAndDropAction.Move
        else -> null
    }
}
