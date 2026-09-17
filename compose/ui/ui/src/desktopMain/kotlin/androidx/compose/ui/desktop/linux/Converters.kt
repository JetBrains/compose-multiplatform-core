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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowFrame
import androidx.compose.ui.window.WindowFrameTiling
import kotlin.math.roundToInt
import org.jetbrains.desktop.linux.DragAndDropAction
import org.jetbrains.desktop.linux.LogicalPixels
import org.jetbrains.desktop.linux.LogicalPixelsInt
import org.jetbrains.desktop.linux.LogicalPoint
import org.jetbrains.desktop.linux.LogicalRect
import org.jetbrains.desktop.linux.LogicalSize
import org.jetbrains.desktop.linux.WindowDecorationMode as LinuxWindowDecorationMode
import org.jetbrains.desktop.linux.WindowFrame as LinuxWindowFrame

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

@OptIn(ExperimentalComposeUiApi::class)
internal fun WindowFrame.toLinuxWindowFrame(): LinuxWindowFrame {
    return LinuxWindowFrame(
        padding = LinuxWindowFrame.Padding(
            left = padding.left.roundToLogicalPixelsInt(),
            top = padding.top.roundToLogicalPixelsInt(),
            right = padding.right.roundToLogicalPixelsInt(),
            bottom = padding.bottom.roundToLogicalPixelsInt(),
        ),
        resizerThickness = LinuxWindowFrame.ResizerThickness(
            left = resizerThickness.left.roundToLogicalPixelsInt(),
            top = resizerThickness.top.roundToLogicalPixelsInt(),
            right = resizerThickness.right.roundToLogicalPixelsInt(),
            bottom = resizerThickness.bottom.roundToLogicalPixelsInt(),
        ),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun LinuxWindowDecorationMode.Client.toWindowDecoration(
    titleBarLayout: Pair<List<WindowDecoration.TitleBarElement>, List<WindowDecoration.TitleBarElement>>,
): WindowDecoration.Undecorated {
    return WindowDecoration.Undecorated(
        frame = WindowFrame(
            padding = WindowFrame.Padding(
                left = frame.padding.left.toDp(),
                top = frame.padding.top.toDp(),
                right = frame.padding.right.toDp(),
                bottom = frame.padding.bottom.toDp(),
            ),
            resizerThickness = WindowFrame.ResizerThickness(
                left = frame.resizerThickness.left.toDp(),
                top = frame.resizerThickness.top.toDp(),
                right = frame.resizerThickness.right.toDp(),
                bottom = frame.resizerThickness.bottom.toDp(),
            ),
        ),
        tiling = WindowFrameTiling(
            left = tiled.left,
            top = tiled.top,
            right = tiled.right,
            bottom = tiled.bottom,
        ),
        titleBarLayoutLeft = titleBarLayout.first,
        titleBarLayoutRight = titleBarLayout.second,
    )
}
