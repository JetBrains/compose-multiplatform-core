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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.roundToInt
import org.jetbrains.desktop.gtk.DragAndDropAction
import org.jetbrains.desktop.gtk.LogicalPixels
import org.jetbrains.desktop.gtk.LogicalPixelsInt
import org.jetbrains.desktop.gtk.LogicalPoint
import org.jetbrains.desktop.gtk.LogicalRect
import org.jetbrains.desktop.gtk.LogicalSize

internal fun LogicalPixels.toDp(): Dp {
    return rawLogical.dp
}

internal fun LogicalPixelsInt.toDp(): Dp {
    return rawLogical.toFloat().dp
}

internal fun LogicalSize.toDpSize(): DpSize {
    return DpSize(width.toDp(), height.toDp())
}

internal fun LogicalPoint.toDpOffset(): DpOffset {
    return DpOffset(x.toDp(), y.toDp())
}

internal fun DpOffset.toPxOffset(density: Density): Offset = with(density) {
    Offset(x.toPx(), y.toPx())
}

internal fun Dp.roundToLogicalPixelsInt(): LogicalPixelsInt {
    return LogicalPixelsInt(value.roundToInt())
}

internal fun Size.roundToLogicalSize(density: Density): LogicalSize {
    return with(density) {
        LogicalSize(width.toDp().roundToLogicalPixelsInt(), height.toDp().roundToLogicalPixelsInt())
    }
}

internal fun DpRect.roundToLogicalRect(): LogicalRect {
    return LogicalRect(
        x = left.roundToLogicalPixelsInt(),
        y = top.roundToLogicalPixelsInt(),
        width = width.roundToLogicalPixelsInt(),
        height = height.roundToLogicalPixelsInt(),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun DragAndDropTransferAction.toGtkAction(): DragAndDropAction? {
    return when (this) {
        DragAndDropTransferAction.Copy -> DragAndDropAction.Copy
        DragAndDropTransferAction.Move -> DragAndDropAction.Move
        else -> null
    }
}
