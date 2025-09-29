/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.input.pointer

import platform.AppKit.NSCursor
import platform.AppKit.NSCursorFrameResizeDirectionsAll
import platform.AppKit.NSCursorFrameResizeDirectionsOutward
import platform.AppKit.NSCursorFrameResizePositionBottomLeft
import platform.AppKit.NSCursorFrameResizePositionBottomRight
import platform.AppKit.NSCursorFrameResizePositionTopLeft
import platform.AppKit.NSCursorFrameResizePositionTopRight

internal data class MacosCursor(val cursor: NSCursor) : PointerIcon

internal actual val pointerIconDefault: PointerIcon = MacosCursor(NSCursor.arrowCursor)
internal actual val pointerIconCrosshair: PointerIcon = MacosCursor(NSCursor.crosshairCursor)
internal actual val pointerIconText: PointerIcon = MacosCursor(NSCursor.IBeamCursor)
internal actual val pointerIconHand: PointerIcon = MacosCursor(NSCursor.pointingHandCursor)

// todo Create move and wait cursors from custom images; Chrome and Safari even animate the wait
//  cursor by manually updating the image on a timer!
// internal actual val pointerIconMove: PointerIcon = MacosCursor(NSCursor.???)
// internal actual val pointerIconWait: PointerIcon = MacosCursor(NSCursor.???)
internal actual val pointerIconColResize: PointerIcon = MacosCursor(NSCursor.columnResizeCursor)
internal actual val pointerIconRowResize: PointerIcon = MacosCursor(NSCursor.rowResizeCursor)
internal actual val pointerIconNResize: PointerIcon = MacosCursor(NSCursor.resizeUpCursor)
internal actual val pointerIconEResize: PointerIcon = MacosCursor(NSCursor.resizeRightCursor)
internal actual val pointerIconSResize: PointerIcon = MacosCursor(NSCursor.resizeDownCursor)
internal actual val pointerIconWResize: PointerIcon = MacosCursor(NSCursor.resizeLeftCursor)
internal actual val pointerIconNeResize: PointerIcon = MacosCursor(
    NSCursor.frameResizeCursorFromPosition(
        NSCursorFrameResizePositionTopRight,
        NSCursorFrameResizeDirectionsOutward,
    )
)
internal actual val pointerIconNwResize: PointerIcon = MacosCursor(
    NSCursor.frameResizeCursorFromPosition(
        NSCursorFrameResizePositionTopLeft,
        NSCursorFrameResizeDirectionsOutward,
    )
)
internal actual val pointerIconSeResize: PointerIcon = MacosCursor(
    NSCursor.frameResizeCursorFromPosition(
        NSCursorFrameResizePositionBottomRight,
        NSCursorFrameResizeDirectionsOutward,
    )
)
internal actual val pointerIconSwResize: PointerIcon = MacosCursor(
    NSCursor.frameResizeCursorFromPosition(
        NSCursorFrameResizePositionBottomLeft,
        NSCursorFrameResizeDirectionsOutward,
    )
)
internal actual val pointerIconNSResize: PointerIcon = MacosCursor(NSCursor.resizeUpDownCursor)
internal actual val pointerIconEWResize: PointerIcon = MacosCursor(NSCursor.resizeLeftRightCursor)
internal actual val pointerIconNeSwResize: PointerIcon = MacosCursor(
    NSCursor.frameResizeCursorFromPosition(
        NSCursorFrameResizePositionTopRight,
        NSCursorFrameResizeDirectionsAll,
    )
)
internal actual val pointerIconNwSeResize: PointerIcon = MacosCursor(
    NSCursor.frameResizeCursorFromPosition(
        NSCursorFrameResizePositionBottomRight,
        NSCursorFrameResizeDirectionsAll,
    )
)