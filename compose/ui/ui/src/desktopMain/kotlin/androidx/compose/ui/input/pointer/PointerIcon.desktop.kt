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

import androidx.compose.ui.desktop.LinuxWindowSystem
import androidx.compose.ui.desktop.currentLinuxWindowSystem
import androidx.compose.ui.desktop.gtk.GtkPointerIcon
import androidx.compose.ui.desktop.linux.LinuxPointerIcon
import androidx.compose.ui.desktop.macos.MacOsPointerIcon
import androidx.compose.ui.platform.DesktopPlatform
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import org.jetbrains.desktop.macos.Cursor
import org.jetbrains.desktop.win32.CursorIcon as WindowsCursorIcon
import org.jetbrains.desktop.gtk.PointerShape as GtkPointerShape
import org.jetbrains.desktop.linux.PointerShape
import org.jetbrains.desktop.linux.PointerShape as LinuxPointerShape

//internal class AwtCursor(val cursor: Cursor) : PointerIcon {
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//
//        other as AwtCursor
//
//        // AwtCursor doesn't implement equals
//        if (cursor.type != other.cursor.type) return false
//
//        // All custom cursors have the type CUSTOM_CURSOR, so we can only use the type if it's
//        // not CUSTOM_CURSOR
//        if (cursor.type == Cursor.CUSTOM_CURSOR) return cursor === other.cursor
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        // AwtCursor doesn't implement hashCode
//        // Aso, all custom cursors have the type CUSTOM_CURSOR, so we can only use the type if it's
//        // not CUSTOM_CURSOR
//        val type = cursor.type
//        return if (type == Cursor.CUSTOM_CURSOR) System.identityHashCode(cursor) else type.hashCode()
//    }
//
//    override fun toString(): String {
//        return "AwtCursor(cursor=$cursor)"
//    }
//}
//
///**
// * Creates [PointerIcon] from [Cursor]
// */
//fun PointerIcon(cursor: Cursor): PointerIcon = AwtCursor(cursor)
//
//internal actual val pointerIconDefault: PointerIcon = AwtCursor(Cursor(Cursor.DEFAULT_CURSOR))
//internal actual val pointerIconCrosshair: PointerIcon = AwtCursor(Cursor(Cursor.CROSSHAIR_CURSOR))
//internal actual val pointerIconText: PointerIcon = AwtCursor(Cursor(Cursor.TEXT_CURSOR))
//internal actual val pointerIconHand: PointerIcon = AwtCursor(Cursor(Cursor.HAND_CURSOR))
//
//internal actual val pointerIconMove: PointerIcon = AwtCursor(Cursor(Cursor.MOVE_CURSOR))
//internal actual val pointerIconWait: PointerIcon = AwtCursor(Cursor(Cursor.WAIT_CURSOR))
//
//// todo[unterhofer] These aren't actually correct, I think
//internal actual val pointerIconColResize: PointerIcon = AwtCursor(Cursor(Cursor.E_RESIZE_CURSOR))
//internal actual val pointerIconRowResize: PointerIcon = AwtCursor(Cursor(Cursor.S_RESIZE_CURSOR))
//internal actual val pointerIconNResize: PointerIcon = AwtCursor(Cursor(Cursor.N_RESIZE_CURSOR))
//internal actual val pointerIconEResize: PointerIcon = AwtCursor(Cursor(Cursor.E_RESIZE_CURSOR))
//internal actual val pointerIconSResize: PointerIcon = AwtCursor(Cursor(Cursor.S_RESIZE_CURSOR))
//internal actual val pointerIconWResize: PointerIcon = AwtCursor(Cursor(Cursor.W_RESIZE_CURSOR))
//internal actual val pointerIconNeResize: PointerIcon = AwtCursor(Cursor(Cursor.NE_RESIZE_CURSOR))
//internal actual val pointerIconNwResize: PointerIcon = AwtCursor(Cursor(Cursor.NW_RESIZE_CURSOR))
//internal actual val pointerIconSeResize: PointerIcon = AwtCursor(Cursor(Cursor.SE_RESIZE_CURSOR))
//internal actual val pointerIconSwResize: PointerIcon = AwtCursor(Cursor(Cursor.SW_RESIZE_CURSOR))
//// todo[unterhofer] These aren't actually correct, I think
//internal actual val pointerIconNSResize: PointerIcon = AwtCursor(Cursor(Cursor.N_RESIZE_CURSOR))
//internal actual val pointerIconEWResize: PointerIcon = AwtCursor(Cursor(Cursor.E_RESIZE_CURSOR))
//internal actual val pointerIconNeSwResize: PointerIcon = AwtCursor(Cursor(Cursor.NE_RESIZE_CURSOR))
//internal actual val pointerIconNwSeResize: PointerIcon = AwtCursor(Cursor(Cursor.SE_RESIZE_CURSOR))
//
//internal actual val pointerIconNone: PointerIcon = PointerIcon(
//    Toolkit.getDefaultToolkit().createCustomCursor(
//        BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB),
//        Point(0, 0),
//        "Empty Cursor",
//    ),
//)


actual val pointerIconDefault: PointerIcon = platformPointerIcon(
    macOs = MacOsPointerIcon(Cursor.Icon.ArrowCursor),
    windows = WindowsPointerIcon(WindowsCursorIcon.Arrow),
    linux = LinuxPointerIcon(LinuxPointerShape.Default),
    gtk = GtkPointerIcon(GtkPointerShape.Default),
)

actual val pointerIconCrosshair: PointerIcon = platformPointerIcon(
    macOs = MacOsPointerIcon(Cursor.Icon.CrosshairCursor),
    windows = WindowsPointerIcon(WindowsCursorIcon.Crosshair),
    linux = LinuxPointerIcon(LinuxPointerShape.Crosshair),
    gtk = GtkPointerIcon(GtkPointerShape.Crosshair),
)

actual val pointerIconText: PointerIcon = platformPointerIcon(
    macOs = MacOsPointerIcon(Cursor.Icon.IBeamCursor),
    windows = WindowsPointerIcon(WindowsCursorIcon.IBeam),
    linux = LinuxPointerIcon(LinuxPointerShape.Text),
    gtk = GtkPointerIcon(GtkPointerShape.Text),
)

actual val pointerIconHand: PointerIcon = platformPointerIcon(
    macOs = MacOsPointerIcon(Cursor.Icon.PointingHandCursor),
    windows = WindowsPointerIcon(WindowsCursorIcon.Hand),
    linux = LinuxPointerIcon(LinuxPointerShape.Pointer),
    gtk = GtkPointerIcon(GtkPointerShape.Pointer),
)

actual val pointerIconColResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.ColumnResizeLeftRightCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeWE),
        linux = LinuxPointerIcon(LinuxPointerShape.ColResize),
        gtk = GtkPointerIcon(GtkPointerShape.ColResize),
    )

actual val pointerIconRowResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.RowResizeUpDownCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeNS),
        linux = LinuxPointerIcon(LinuxPointerShape.RowResize),
        gtk = GtkPointerIcon(GtkPointerShape.RowResize),
    )

actual val pointerIconEResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.ColumnResizeRightCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeWE),
        linux = LinuxPointerIcon(LinuxPointerShape.EResize),
        gtk = GtkPointerIcon(GtkPointerShape.EResize),
    )

actual val pointerIconNResize: PointerIcon = platformPointerIcon(
    macOs = MacOsPointerIcon(Cursor.Icon.RowResizeUpCursor),
    windows = WindowsPointerIcon(WindowsCursorIcon.SizeNS),
    linux = LinuxPointerIcon(LinuxPointerShape.NResize),
    gtk = GtkPointerIcon(GtkPointerShape.NResize),
)

actual val pointerIconNeResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.FrameResizeUpRightDownLeftCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeNESW),
        linux = LinuxPointerIcon(LinuxPointerShape.NeResize),
        gtk = GtkPointerIcon(GtkPointerShape.NeResize),
    )

actual val pointerIconNwResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.FrameResizeUpLeftDownRightCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeNWSE),
        linux = LinuxPointerIcon(LinuxPointerShape.NwResize),
        gtk = GtkPointerIcon(GtkPointerShape.NwResize),
    )

actual val pointerIconSResize: PointerIcon = platformPointerIcon(
    macOs = MacOsPointerIcon(Cursor.Icon.RowResizeDownCursor),
    windows = WindowsPointerIcon(WindowsCursorIcon.SizeNS),
    linux = LinuxPointerIcon(LinuxPointerShape.SResize),
    gtk = GtkPointerIcon(GtkPointerShape.SResize),
)

actual val pointerIconSeResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.FrameResizeUpLeftDownRightCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeNWSE),
        linux = LinuxPointerIcon(LinuxPointerShape.SeResize),
        gtk = GtkPointerIcon(GtkPointerShape.SeResize),
    )

actual val pointerIconSwResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.FrameResizeUpRightDownLeftCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeNESW),
        linux = LinuxPointerIcon(LinuxPointerShape.SwResize),
        gtk = GtkPointerIcon(GtkPointerShape.SwResize),
    )

actual val pointerIconWResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.ColumnResizeLeftCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeWE),
        linux = LinuxPointerIcon(LinuxPointerShape.WResize),
        gtk = GtkPointerIcon(GtkPointerShape.WResize),
    )

actual val pointerIconNSResize: PointerIcon = platformPointerIcon(
    macOs = MacOsPointerIcon(Cursor.Icon.RowResizeDownCursor),
    windows = WindowsPointerIcon(WindowsCursorIcon.SizeNS),
    linux = LinuxPointerIcon(LinuxPointerShape.NsResize),
    gtk = GtkPointerIcon(GtkPointerShape.NsResize),
)

actual val pointerIconEWResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.ColumnResizeLeftRightCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeWE),
        linux = LinuxPointerIcon(LinuxPointerShape.EwResize),
        gtk = GtkPointerIcon(GtkPointerShape.EwResize),
    )

actual val pointerIconNeSwResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.FrameResizeUpRightDownLeftCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeNESW),
        linux = LinuxPointerIcon(LinuxPointerShape.NeswResize),
        gtk = GtkPointerIcon(GtkPointerShape.NeswResize),
    )

actual val pointerIconNwSeResize: PointerIcon =
    platformPointerIcon(
        macOs = MacOsPointerIcon(Cursor.Icon.FrameResizeUpLeftDownRightCursor),
        windows = WindowsPointerIcon(WindowsCursorIcon.SizeNWSE),
        linux = LinuxPointerIcon(LinuxPointerShape.NwseResize),
        gtk = GtkPointerIcon(GtkPointerShape.NwseResize),
    )

// TODO use WindowsPointerIcon coming from androidx.compose.ui.desktop.windows.WindowsPointerIcon
internal data class WindowsPointerIcon(val icon: WindowsCursorIcon) : PointerIcon

private fun platformPointerIcon(
    macOs: PointerIcon,
    windows: PointerIcon,
    linux: PointerIcon,
    gtk: PointerIcon,
): PointerIcon {
    return when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> macOs
        DesktopPlatform.Linux -> when (currentLinuxWindowSystem()) {
            LinuxWindowSystem.Wayland -> linux
            LinuxWindowSystem.Gtk -> gtk
        }
        DesktopPlatform.Windows -> windows
        DesktopPlatform.Unknown -> error("Unsupported desktop platform: ${DesktopPlatform.Current}")
    }
}

