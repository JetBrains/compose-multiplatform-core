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

package androidx.compose.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import javax.swing.SwingUtilities

internal enum class TouchState {
    Down,
    Move,
    Up
}

/**
 * Receives raw touch and pen input (`WM_POINTER`) for a window on Windows.
 *
 * AWT doesn't handle pointer messages, so they reach `DefWindowProc`, which synthesizes
 * mouse events for them, and touch screens are handled as a mouse. This class subclasses
 * the window procedure to handle `WM_POINTER` messages before the mouse event synthesis
 * happens. Hovering pointers (such as a pen in range of the digitizer, but not touching it)
 * are not handled and remain reported as mouse moves.
 *
 * [dispose] restores the original window procedure.
 *
 * @param windowHandle the HWND of the top-level window to receive pointer input for
 * @param onTouchEvent the touch event callback; called on the event dispatch thread with
 * the pointer position in the screen coordinate space, in physical pixels. [PointerType] is
 * [PointerType.Stylus] for input coming from a pen ([PointerType.Eraser] if the pen is
 * inverted or its eraser button is pressed), and [PointerType.Touch] otherwise
 * @param onTouchCancel called on the event dispatch thread when the ongoing touch input is
 * canceled, for example when the pointer is captured by the system for an edge gesture
 *
 * @throws IllegalStateException if [windowHandle] is not a valid window handle
 */
internal class WindowsTouchBridge(
    windowHandle: Long,
    private val onTouchEvent: (
        id: Long,
        position: Offset,
        state: TouchState,
        type: PointerType,
        pressure: Float
    ) -> Unit,
    private val onTouchCancel: () -> Unit,
) {
    private val user32 = PointerInputUser32.INSTANCE

    /**
     * AWT dispatches input events to the first heavyweight child of the top-level window
     * rather than to the window itself, so its window procedure is the one to subclass.
     */
    private val hwnd: WinDef.HWND

    // Keep a strong reference to the callback to prevent its garbage collection while
    // the native code holds a pointer to it.
    private val windowProc: WindowProc

    private val originalWindowProc: BaseTSD.LONG_PTR

    private var isDisposed = false

    init {
        require(windowHandle != 0L) { "Invalid window handle: 0" }

        val window = WinDef.HWND(Pointer.createConstant(windowHandle))
        check(user32.IsWindow(window)) {
            "Invalid window handle: 0x${windowHandle.toString(16)}"
        }
        hwnd = user32.GetWindow(window, GW_CHILD) ?: window

        windowProc = WindowProc()
        originalWindowProc = user32.GetWindowLongPtrA(hwnd, GWLP_WNDPROC)
        val callbackPointer = CallbackReference.getFunctionPointer(windowProc)
        user32.SetWindowLongPtrA(
            hwnd,
            GWLP_WNDPROC,
            BaseTSD.LONG_PTR(Pointer.nativeValue(callbackPointer))
        )
    }

    /**
     * Restores the original window procedure.
     */
    fun dispose() {
        if (isDisposed) {
            return
        }
        isDisposed = true
        user32.SetWindowLongPtrA(hwnd, GWLP_WNDPROC, originalWindowProc)
    }

    private inner class WindowProc : WinUser.WindowProc {
        override fun callback(
            hwnd: WinDef.HWND,
            uMsg: Int,
            wParam: WinDef.WPARAM,
            lParam: WinDef.LPARAM
        ): WinDef.LRESULT = when (uMsg) {
            WM_POINTERDOWN, WM_POINTERUPDATE, WM_POINTERUP ->
                if (onPointerMessage(uMsg, wParam)) {
                    // Mark the message as handled to prevent mouse event synthesis
                    WinDef.LRESULT(0)
                } else {
                    user32.CallWindowProcA(originalWindowProc, hwnd, uMsg, wParam, lParam)
                }
            WM_POINTERCAPTURECHANGED -> {
                SwingUtilities.invokeLater(onTouchCancel)
                WinDef.LRESULT(0)
            }
            else -> user32.CallWindowProcA(originalWindowProc, hwnd, uMsg, wParam, lParam)
        }
    }

    /**
     * Handles a `WM_POINTER` message, and returns whether it was handled.
     *
     * Messages of hovering pointers, and of pointer types other than touch and pen, are
     * not handled and should be passed to the original window procedure.
     */
    private fun onPointerMessage(uMsg: Int, wParam: WinDef.WPARAM): Boolean {
        // The low-order word of wParam contains the pointer identifier
        val pointerId = (wParam.toLong() and 0xFFFF).toInt()
        val info = POINTER_INFO()
        if (!user32.GetPointerInfo(pointerId, info)) {
            return false
        }
        if (info.pointerType != PT_TOUCH && info.pointerType != PT_PEN) {
            return false
        }
        val state = when (uMsg) {
            WM_POINTERDOWN -> TouchState.Down
            WM_POINTERUP -> TouchState.Up
            else -> TouchState.Move
        }
        // Don't handle hovering pointers to keep reporting them as mouse moves
        if (state == TouchState.Move && info.pointerFlags and POINTER_FLAG_INCONTACT == 0) {
            return false
        }
        if (info.pointerFlags and POINTER_FLAG_CANCELED != 0) {
            SwingUtilities.invokeLater(onTouchCancel)
            return true
        }

        var type = PointerType.Touch
        var pressure = 1f
        if (info.pointerType == PT_PEN) {
            type = PointerType.Stylus
            val penInfo = POINTER_PEN_INFO()
            if (user32.GetPointerPenInfo(pointerId, penInfo)) {
                if (penInfo.penFlags and (PEN_FLAG_INVERTED or PEN_FLAG_ERASER) != 0) {
                    type = PointerType.Eraser
                }
                if (penInfo.penMask and PEN_MASK_PRESSURE != 0) {
                    pressure = penInfo.pressure / 1024f
                }
            }
        }
        val position = Offset(
            info.ptPixelLocation.x.toFloat(),
            info.ptPixelLocation.y.toFloat()
        )
        SwingUtilities.invokeLater {
            onTouchEvent(pointerId.toLong(), position, state, type, pressure)
        }
        return true
    }
}

// See https://learn.microsoft.com/en-us/windows/win32/inputmsg/messages
private const val WM_POINTERUPDATE = 0x0245
private const val WM_POINTERDOWN = 0x0246
private const val WM_POINTERUP = 0x0247
private const val WM_POINTERCAPTURECHANGED = 0x024C
private const val PT_TOUCH = 2
private const val PT_PEN = 3
private const val POINTER_FLAG_INCONTACT = 0x00000004
private const val POINTER_FLAG_CANCELED = 0x00008000
private const val PEN_FLAG_INVERTED = 0x00000002
private const val PEN_FLAG_ERASER = 0x00000004
private const val PEN_MASK_PRESSURE = 0x00000001
private const val GWLP_WNDPROC = -4
private const val GW_CHILD = 5

/**
 * [User32] extended with the pointer input functions that are missing from JNA.
 */
internal interface PointerInputUser32 : User32 {
    /** Retrieves a handle to a window that has the specified relationship to [hWnd]. */
    fun GetWindow(hWnd: WinDef.HWND, uCmd: Int): WinDef.HWND?

    /** Retrieves the information of the pointer with the given identifier. */
    fun GetPointerInfo(pointerId: Int, pointerInfo: POINTER_INFO): Boolean

    /** Retrieves the pen-specific information of the pointer with the given identifier. */
    fun GetPointerPenInfo(pointerId: Int, penInfo: POINTER_PEN_INFO): Boolean

    /** Retrieves information about the specified window, such as its window procedure. */
    fun GetWindowLongPtrA(hWnd: WinDef.HWND, nIndex: Int): BaseTSD.LONG_PTR

    /** Changes an attribute of the specified window, such as its window procedure. */
    fun SetWindowLongPtrA(hWnd: WinDef.HWND, nIndex: Int, dwNewLong: BaseTSD.LONG_PTR): BaseTSD.LONG_PTR

    /** Passes message information to the specified window procedure. */
    fun CallWindowProcA(
        lpPrevWndFunc: BaseTSD.LONG_PTR,
        hWnd: WinDef.HWND,
        uMsg: Int,
        wParam: WinDef.WPARAM,
        lParam: WinDef.LPARAM
    ): WinDef.LRESULT

    companion object {
        val INSTANCE: PointerInputUser32 = Native.load("user32", PointerInputUser32::class.java)
    }
}

/**
 * The `POINTER_INFO` structure with the information common to all pointer types.
 *
 * See https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-pointer_info
 */
@Structure.FieldOrder(
    "pointerType",
    "pointerId",
    "frameId",
    "pointerFlags",
    "sourceDevice",
    "hwndTarget",
    "ptPixelLocation",
    "ptHimetricLocation",
    "ptPixelLocationRaw",
    "ptHimetricLocationRaw",
    "dwTime",
    "historyCount",
    "InputData",
    "dwKeyStates",
    "PerformanceCount",
    "ButtonChangeType"
)
internal class POINTER_INFO : Structure() {
    @JvmField var pointerType: Int = 0
    @JvmField var pointerId: Int = 0
    @JvmField var frameId: Int = 0
    @JvmField var pointerFlags: Int = 0
    @JvmField var sourceDevice: WinNT.HANDLE? = null
    @JvmField var hwndTarget: WinDef.HWND? = null
    @JvmField var ptPixelLocation: WinDef.POINT = WinDef.POINT()
    @JvmField var ptHimetricLocation: WinDef.POINT = WinDef.POINT()
    @JvmField var ptPixelLocationRaw: WinDef.POINT = WinDef.POINT()
    @JvmField var ptHimetricLocationRaw: WinDef.POINT = WinDef.POINT()
    @JvmField var dwTime: Int = 0
    @JvmField var historyCount: Int = 0
    @JvmField var InputData: Int = 0
    @JvmField var dwKeyStates: Int = 0
    @JvmField var PerformanceCount: Long = 0
    @JvmField var ButtonChangeType: Int = 0
}

/**
 * The `POINTER_PEN_INFO` structure with the pen-specific pointer information.
 *
 * See https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-pointer_pen_info
 */
@Structure.FieldOrder(
    "pointerInfo",
    "penFlags",
    "penMask",
    "pressure",
    "rotation",
    "tiltX",
    "tiltY"
)
internal class POINTER_PEN_INFO : Structure() {
    @JvmField var pointerInfo: POINTER_INFO = POINTER_INFO()
    @JvmField var penFlags: Int = 0
    @JvmField var penMask: Int = 0
    @JvmField var pressure: Int = 0
    @JvmField var rotation: Int = 0
    @JvmField var tiltX: Int = 0
    @JvmField var tiltY: Int = 0
}
