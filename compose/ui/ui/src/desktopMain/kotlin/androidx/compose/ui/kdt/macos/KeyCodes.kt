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

package androidx.compose.ui.kdt.macos

import androidx.compose.ui.input.key.Key
import org.jetbrains.desktop.macos.KeyCode


// Keyboard event conversion utilities
internal fun KeyCode.toComposeKey(): Key {
    return when (this) {
        // Letters
        KeyCode.ANSI_A -> Key.A
        KeyCode.ANSI_B -> Key.B
        KeyCode.ANSI_C -> Key.C
        KeyCode.ANSI_D -> Key.D
        KeyCode.ANSI_E -> Key.E
        KeyCode.ANSI_F -> Key.F
        KeyCode.ANSI_G -> Key.G
        KeyCode.ANSI_H -> Key.H
        KeyCode.ANSI_I -> Key.I
        KeyCode.ANSI_J -> Key.J
        KeyCode.ANSI_K -> Key.K
        KeyCode.ANSI_L -> Key.L
        KeyCode.ANSI_M -> Key.M
        KeyCode.ANSI_N -> Key.N
        KeyCode.ANSI_O -> Key.O
        KeyCode.ANSI_P -> Key.P
        KeyCode.ANSI_Q -> Key.Q
        KeyCode.ANSI_R -> Key.R
        KeyCode.ANSI_S -> Key.S
        KeyCode.ANSI_T -> Key.T
        KeyCode.ANSI_U -> Key.U
        KeyCode.ANSI_V -> Key.V
        KeyCode.ANSI_W -> Key.W
        KeyCode.ANSI_X -> Key.X
        KeyCode.ANSI_Y -> Key.Y
        KeyCode.ANSI_Z -> Key.Z

        // Numbers
        KeyCode.ANSI_0 -> Key.Zero
        KeyCode.ANSI_1 -> Key.One
        KeyCode.ANSI_2 -> Key.Two
        KeyCode.ANSI_3 -> Key.Three
        KeyCode.ANSI_4 -> Key.Four
        KeyCode.ANSI_5 -> Key.Five
        KeyCode.ANSI_6 -> Key.Six
        KeyCode.ANSI_7 -> Key.Seven
        KeyCode.ANSI_8 -> Key.Eight
        KeyCode.ANSI_9 -> Key.Nine

        // Special characters
        KeyCode.ANSI_Equal -> Key.Equals
        KeyCode.ANSI_Minus -> Key.Minus
        KeyCode.ANSI_RightBracket -> Key.RightBracket
        KeyCode.ANSI_LeftBracket -> Key.LeftBracket
        KeyCode.ANSI_Quote -> Key.Apostrophe
        KeyCode.ANSI_Semicolon -> Key.Semicolon
        KeyCode.ANSI_Backslash -> Key.Backslash
        KeyCode.ANSI_Comma -> Key.Comma
        KeyCode.ANSI_Slash -> Key.Slash
        KeyCode.ANSI_Period -> Key.Period
        KeyCode.ANSI_Grave -> Key.Grave

        // Keypad numbers
        KeyCode.ANSI_Keypad0 -> Key.NumPad0
        KeyCode.ANSI_Keypad1 -> Key.NumPad1
        KeyCode.ANSI_Keypad2 -> Key.NumPad2
        KeyCode.ANSI_Keypad3 -> Key.NumPad3
        KeyCode.ANSI_Keypad4 -> Key.NumPad4
        KeyCode.ANSI_Keypad5 -> Key.NumPad5
        KeyCode.ANSI_Keypad6 -> Key.NumPad6
        KeyCode.ANSI_Keypad7 -> Key.NumPad7
        KeyCode.ANSI_Keypad8 -> Key.NumPad8
        KeyCode.ANSI_Keypad9 -> Key.NumPad9

        // Keypad operators
        KeyCode.ANSI_KeypadDecimal -> Key.NumPadDot
        KeyCode.ANSI_KeypadMultiply -> Key.NumPadMultiply
        KeyCode.ANSI_KeypadPlus -> Key.NumPadAdd
        KeyCode.ANSI_KeypadDivide -> Key.NumPadDivide
        KeyCode.ANSI_KeypadEnter -> Key.NumPadEnter
        KeyCode.ANSI_KeypadMinus -> Key.NumPadSubtract
        KeyCode.ANSI_KeypadEquals -> Key.NumPadEquals

        // Control keys
        KeyCode.Return -> Key.Enter
        KeyCode.Tab -> Key.Tab
        KeyCode.Space -> Key.Spacebar
        KeyCode.Delete -> Key.Backspace
        KeyCode.Escape -> Key.Escape
        KeyCode.ForwardDelete -> Key.Delete

        // Modifier keys
        KeyCode.Command -> Key.MetaLeft
        KeyCode.RightCommand -> Key.MetaRight
        KeyCode.Shift -> Key.ShiftLeft
        KeyCode.RightShift -> Key.ShiftRight
        KeyCode.CapsLock -> Key.CapsLock
        KeyCode.Option -> Key.AltLeft
        KeyCode.RightOption -> Key.AltRight
        KeyCode.Control -> Key.CtrlLeft
        KeyCode.RightControl -> Key.CtrlRight

        // Function keys
        KeyCode.F1 -> Key.F1
        KeyCode.F2 -> Key.F2
        KeyCode.F3 -> Key.F3
        KeyCode.F4 -> Key.F4
        KeyCode.F5 -> Key.F5
        KeyCode.F6 -> Key.F6
        KeyCode.F7 -> Key.F7
        KeyCode.F8 -> Key.F8
        KeyCode.F9 -> Key.F9
        KeyCode.F10 -> Key.F10
        KeyCode.F11 -> Key.F11
        KeyCode.F12 -> Key.F12

        // Navigation keys
        KeyCode.Help -> Key.Help
        KeyCode.Home -> Key.Home
        KeyCode.PageUp -> Key.PageUp
        KeyCode.End -> Key.MoveEnd
        KeyCode.PageDown -> Key.PageDown
        KeyCode.LeftArrow -> Key.DirectionLeft
        KeyCode.RightArrow -> Key.DirectionRight
        KeyCode.DownArrow -> Key.DirectionDown
        KeyCode.UpArrow -> Key.DirectionUp

        // Default case for unknown keys
        else -> Key.Unknown
    }
}