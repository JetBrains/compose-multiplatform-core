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

package androidx.compose.ui.input.key

import androidx.compose.ui.input.pointer.PointerKeyboardModifiers

// X11 core protocol modifier masks (X.h). Mod1 is Alt and Mod2 is NumLock on
// every stock XKB layout; Mod4 is Super, which Compose calls Meta.
private const val X_SHIFT_MASK = 1L shl 0
private const val X_LOCK_MASK = 1L shl 1
private const val X_CONTROL_MASK = 1L shl 2
private const val X_MOD1_MASK = 1L shl 3
private const val X_MOD2_MASK = 1L shl 4
private const val X_MOD4_MASK = 1L shl 6

/**
 * Translates the X11 `state` mask of a key, button, or motion event into Compose
 * keyboard modifiers.
 *
 * X11 reports the state *before* the event, so for the key event of a modifier key
 * itself the mask is off by one transition. Passing the event's own [key] and
 * [isKeyDown] corrects that: if [key] is that modifier, its pressed state is taken
 * from [isKeyDown] instead of the mask.
 */
internal fun xKeyboardModifiers(
    state: Long,
    key: Key = Key.Unknown,
    isKeyDown: Boolean = false,
): PointerKeyboardModifiers {
    fun pressed(mask: Long, left: Key, right: Key): Boolean =
        if (key == left || key == right) isKeyDown else state and mask != 0L
    return PointerKeyboardModifiers(
        isShiftPressed = pressed(X_SHIFT_MASK, Key.ShiftLeft, Key.ShiftRight),
        isCtrlPressed = pressed(X_CONTROL_MASK, Key.CtrlLeft, Key.CtrlRight),
        isAltPressed = pressed(X_MOD1_MASK, Key.AltLeft, Key.AltRight),
        isMetaPressed = pressed(X_MOD4_MASK, Key.MetaLeft, Key.MetaRight),
        isCapsLockOn = state and X_LOCK_MASK != 0L,
        isNumLockOn = state and X_MOD2_MASK != 0L,
    )
}

private val letterKeys = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
)

private val digitKeys = listOf(
    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four,
    Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
)

private val functionKeys = listOf(
    Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6,
    Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12,
)

private val numPadDigitKeys = listOf(
    Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
    Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
)

/**
 * X11 keysym → Compose [Key] (keysym values from `X11/keysymdef.h`).
 *
 * Latin-1 keysyms equal their character codes, so letter keysyms of both cases map to
 * the same key. Shifted punctuation keysyms are deliberately absent: callers resolve
 * the effective keysym first and fall back to the unshifted one, so e.g. `!` lands on
 * [Key.One] the way desktop key codes do. Keysyms absent from this table map to
 * [Key.Unknown] but still deliver their typed character via the event's code point.
 */
internal val keysymToKey: Map<Long, Key> =
    mapOf(
        // TTY function keys
        0xff08L to Key.Backspace,
        0xff09L to Key.Tab,
        0xff0dL to Key.Enter,
        0xff13L to Key.Break,
        0xff14L to Key.ScrollLock,
        0xff1bL to Key.Escape,
        0xffffL to Key.Delete,
        // Cursor motion
        0xff50L to Key.MoveHome,
        0xff51L to Key.DirectionLeft,
        0xff52L to Key.DirectionUp,
        0xff53L to Key.DirectionRight,
        0xff54L to Key.DirectionDown,
        0xff55L to Key.PageUp,
        0xff56L to Key.PageDown,
        0xff57L to Key.MoveEnd,
        // Misc functions
        0xff61L to Key.PrintScreen,
        0xff63L to Key.Insert,
        0xff67L to Key.Menu,
        0xff7fL to Key.NumLock,
        // Keypad
        0xff8dL to Key.NumPadEnter,
        0xff95L to Key.NumPadMoveHome,
        0xff96L to Key.NumPadDirectionLeft,
        0xff97L to Key.NumPadDirectionUp,
        0xff98L to Key.NumPadDirectionRight,
        0xff99L to Key.NumPadDirectionDown,
        0xff9aL to Key.NumPadPageUp,
        0xff9bL to Key.NumPadPageDown,
        0xff9cL to Key.NumPadMoveEnd,
        0xff9eL to Key.NumPadInsert,
        0xff9fL to Key.NumPadDelete,
        0xffaaL to Key.NumPadMultiply,
        0xffabL to Key.NumPadAdd,
        0xffacL to Key.NumPadComma,
        0xffadL to Key.NumPadSubtract,
        0xffaeL to Key.NumPadDot,
        0xffafL to Key.NumPadDivide,
        0xffbdL to Key.NumPadEquals,
        // Modifier keys (Super_L/Super_R are Compose's Meta)
        0xffe1L to Key.ShiftLeft,
        0xffe2L to Key.ShiftRight,
        0xffe3L to Key.CtrlLeft,
        0xffe4L to Key.CtrlRight,
        0xffe5L to Key.CapsLock,
        0xffe7L to Key.MetaLeft,
        0xffe8L to Key.MetaRight,
        0xffe9L to Key.AltLeft,
        0xffeaL to Key.AltRight,
        0xffebL to Key.MetaLeft,
        0xffecL to Key.MetaRight,
        // Latin-1 punctuation (unshifted positions on a US layout)
        0x0020L to Key.Spacebar,
        0x0027L to Key.Apostrophe,
        0x002cL to Key.Comma,
        0x002dL to Key.Minus,
        0x002eL to Key.Period,
        0x002fL to Key.Slash,
        0x003bL to Key.Semicolon,
        0x003dL to Key.Equals,
        0x005bL to Key.LeftBracket,
        0x005cL to Key.Backslash,
        0x005dL to Key.RightBracket,
        0x0060L to Key.Grave,
    ) +
        ('a'..'z').zip(letterKeys).flatMap { (char, key) ->
            listOf(char.code.toLong() to key, char.uppercaseChar().code.toLong() to key)
        } +
        ('0'..'9').zip(digitKeys).map { (char, key) -> char.code.toLong() to key } +
        numPadDigitKeys.mapIndexed { index, key -> 0xffb0L + index to key } +
        functionKeys.mapIndexed { index, key -> 0xffbeL + index to key }
