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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyboardModifiers
import org.jetbrains.desktop.win32.Keyboard
import org.jetbrains.desktop.win32.VirtualKey

internal fun VirtualKey.toKey(): Key = when (this) {
    // digits
    VirtualKey.Number0 -> Key.Zero
    VirtualKey.Number1 -> Key.One
    VirtualKey.Number2 -> Key.Two
    VirtualKey.Number3 -> Key.Three
    VirtualKey.Number4 -> Key.Four
    VirtualKey.Number5 -> Key.Five
    VirtualKey.Number6 -> Key.Six
    VirtualKey.Number7 -> Key.Seven
    VirtualKey.Number8 -> Key.Eight
    VirtualKey.Number9 -> Key.Nine

    // letters
    VirtualKey.A -> Key.A
    VirtualKey.B -> Key.B
    VirtualKey.C -> Key.C
    VirtualKey.D -> Key.D
    VirtualKey.E -> Key.E
    VirtualKey.F -> Key.F
    VirtualKey.G -> Key.G
    VirtualKey.H -> Key.H
    VirtualKey.I -> Key.I
    VirtualKey.J -> Key.J
    VirtualKey.K -> Key.K
    VirtualKey.L -> Key.L
    VirtualKey.M -> Key.M
    VirtualKey.N -> Key.N
    VirtualKey.O -> Key.O
    VirtualKey.P -> Key.P
    VirtualKey.Q -> Key.Q
    VirtualKey.R -> Key.R
    VirtualKey.S -> Key.S
    VirtualKey.T -> Key.T
    VirtualKey.U -> Key.U
    VirtualKey.V -> Key.V
    VirtualKey.W -> Key.W
    VirtualKey.X -> Key.X
    VirtualKey.Y -> Key.Y
    VirtualKey.Z -> Key.Z

    // special characters (OEM keys)
    VirtualKey.OemPlus -> Key.Equals
    VirtualKey.OemMinus -> Key.Minus
    VirtualKey.Oem6 -> Key.RightBracket
    VirtualKey.Oem4 -> Key.LeftBracket
    VirtualKey.Oem7 -> Key.Apostrophe
    VirtualKey.Oem1, VirtualKey.OemSemicolon -> Key.Semicolon
    VirtualKey.Oem5 -> Key.Backslash
    VirtualKey.OemComma -> Key.Comma
    VirtualKey.Oem2 -> Key.Slash
    VirtualKey.OemPeriod -> Key.Period
    VirtualKey.Oem3 -> Key.Grave

    // numpad
    VirtualKey.Decimal -> Key.NumPadDot
    VirtualKey.Multiply -> Key.NumPadMultiply
    VirtualKey.Add -> Key.NumPadAdd
    VirtualKey.Clear -> Key.NumLock
    VirtualKey.Divide -> Key.NumPadDivide
    VirtualKey.Separator -> Key.NumPadComma
    VirtualKey.Subtract -> Key.NumPadSubtract
    VirtualKey.NumberPad0 -> Key.NumPad0
    VirtualKey.NumberPad1 -> Key.NumPad1
    VirtualKey.NumberPad2 -> Key.NumPad2
    VirtualKey.NumberPad3 -> Key.NumPad3
    VirtualKey.NumberPad4 -> Key.NumPad4
    VirtualKey.NumberPad5 -> Key.NumPad5
    VirtualKey.NumberPad6 -> Key.NumPad6
    VirtualKey.NumberPad7 -> Key.NumPad7
    VirtualKey.NumberPad8 -> Key.NumPad8
    VirtualKey.NumberPad9 -> Key.NumPad9

    // modifiers
    VirtualKey.Menu, VirtualKey.LeftMenu, VirtualKey.RightMenu -> Key.AltLeft
    VirtualKey.LeftWindows, VirtualKey.RightWindows -> Key.MetaLeft
    VirtualKey.Control, VirtualKey.LeftControl, VirtualKey.RightControl -> Key.CtrlLeft
    VirtualKey.Shift, VirtualKey.LeftShift, VirtualKey.RightShift -> Key.ShiftLeft

    // control keys
    VirtualKey.Enter -> Key.Enter
    VirtualKey.Tab -> Key.Tab
    VirtualKey.Space -> Key.Spacebar
    VirtualKey.Back -> Key.Backspace
    VirtualKey.Escape -> Key.Escape
    VirtualKey.CapitalLock -> Key.CapsLock
    VirtualKey.Snapshot -> Key.PrintScreen
    VirtualKey.Scroll -> Key.ScrollLock
    VirtualKey.Pause -> Key.Break
    VirtualKey.Insert -> Key.Insert
    VirtualKey.Delete -> Key.Delete
    VirtualKey.Home -> Key.MoveHome
    VirtualKey.End -> Key.MoveEnd
    VirtualKey.PageUp -> Key.PageUp
    VirtualKey.PageDown -> Key.PageDown

    // arrow keys
    VirtualKey.Left -> Key.DirectionLeft
    VirtualKey.Right -> Key.DirectionRight
    VirtualKey.Down -> Key.DirectionDown
    VirtualKey.Up -> Key.DirectionUp

    // function keys
    VirtualKey.F1 -> Key.F1
    VirtualKey.F2 -> Key.F2
    VirtualKey.F3 -> Key.F3
    VirtualKey.F4 -> Key.F4
    VirtualKey.F5 -> Key.F5
    VirtualKey.F6 -> Key.F6
    VirtualKey.F7 -> Key.F7
    VirtualKey.F8 -> Key.F8
    VirtualKey.F9 -> Key.F9
    VirtualKey.F10 -> Key.F10
    VirtualKey.F11 -> Key.F11
    VirtualKey.F12 -> Key.F12

    // media keys
    VirtualKey.VolumeUp -> Key.VolumeUp
    VirtualKey.VolumeDown -> Key.VolumeDown
    VirtualKey.VolumeMute -> Key.VolumeMute
    VirtualKey.MediaNextTrack -> Key.MediaNext
    VirtualKey.MediaPreviousTrack -> Key.MediaPrevious
    VirtualKey.MediaStop -> Key.MediaStop
    VirtualKey.MediaPlayPause -> Key.MediaPlayPause

    // browser keys
    VirtualKey.GoBack -> Key.NavigateOut
    VirtualKey.GoForward -> Key.NavigateIn

    // application keys
    VirtualKey.Application -> Key.Menu
    VirtualKey.NumberKeyLock -> Key.NumLock

    else -> Key.Unknown
}

internal fun currentKeyboardModifiers(): KeyboardModifiers {
    val keyboardState = Keyboard.getState()
    return KeyboardModifiers(
        isCtrlPressed = keyboardState.isKeyDown(VirtualKey.Control),
        isMetaPressed = keyboardState.isKeyDown(VirtualKey.LeftWindows) ||
                        keyboardState.isKeyDown(VirtualKey.RightWindows),
        isAltPressed = keyboardState.isKeyDown(VirtualKey.Menu),
        isShiftPressed = keyboardState.isKeyDown(VirtualKey.Shift),
        isAltGraphPressed = false,
        isSymPressed = false,
        isScrollLockOn = keyboardState.isKeyToggled(VirtualKey.Scroll),
        isFunctionPressed = false,
        isCapsLockOn = keyboardState.isKeyToggled(VirtualKey.CapitalLock),
        isNumLockOn = keyboardState.isKeyToggled(VirtualKey.NumberKeyLock),
    )
}
