package androidx.compose.ui.desktop.headless

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCapsLockOn
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed

internal fun Event.KeyDown.toKeyEvent(trackedModifiers: PointerKeyboardModifiers): KeyEvent {
    return KeyEvent(
        key = key,
        type = KeyEventType.KeyDown,
        codePoint = codePoint ?: key.codePoint(trackedModifiers),
        isCtrlPressed = trackedModifiers.isCtrlPressed,
        isMetaPressed = trackedModifiers.isMetaPressed,
        isAltPressed = trackedModifiers.isAltPressed,
        isShiftPressed = trackedModifiers.isShiftPressed,
        nativeEvent = this,
    )
}

internal fun Event.KeyUp.toKeyEvent(trackedModifiers: PointerKeyboardModifiers): KeyEvent {
    return KeyEvent(
        key = key,
        type = KeyEventType.KeyUp,
        codePoint = codePoint ?: key.codePoint(trackedModifiers),
        isCtrlPressed = trackedModifiers.isCtrlPressed,
        isMetaPressed = trackedModifiers.isMetaPressed,
        isAltPressed = trackedModifiers.isAltPressed,
        isShiftPressed = trackedModifiers.isShiftPressed,
        nativeEvent = this,
    )
}

internal fun Event.MouseDown.toKeyEvent(keyboardModifiers: PointerKeyboardModifiers): KeyEvent {
    return KeyEvent(
        key = button.toKey(),
        type = KeyEventType.KeyDown,
        codePoint = 0,
        isCtrlPressed = keyboardModifiers.isCtrlPressed,
        isMetaPressed = keyboardModifiers.isMetaPressed,
        isAltPressed = keyboardModifiers.isAltPressed,
        isShiftPressed = keyboardModifiers.isShiftPressed,
        nativeEvent = this,
    )
}

internal fun Event.MouseUp.toKeyEvent(keyboardModifiers: PointerKeyboardModifiers): KeyEvent {
    return KeyEvent(
        key = button.toKey(),
        type = KeyEventType.KeyUp,
        codePoint = 0,
        isCtrlPressed = keyboardModifiers.isCtrlPressed,
        isMetaPressed = keyboardModifiers.isMetaPressed,
        isAltPressed = keyboardModifiers.isAltPressed,
        isShiftPressed = keyboardModifiers.isShiftPressed,
        nativeEvent = this,
    )
}

internal fun PointerButton.toKey(): Key = when (this) {
    PointerButton.Primary -> Key.Button1
    PointerButton.Secondary -> Key.Button2
    PointerButton.Tertiary -> Key.Button3
    PointerButton.Back -> Key.Button4
    PointerButton.Forward -> Key.Button5
    else -> Key.Unknown
}


private fun Key.codePoint(trackedModifiers: PointerKeyboardModifiers): Int {
    val isUpperCase = trackedModifiers.isShiftPressed || trackedModifiers.isCapsLockOn
    return when (this) {
        Key.Zero -> '0'.code
        Key.One -> '1'.code
        Key.Two -> '2'.code
        Key.Three -> '3'.code
        Key.Four -> '4'.code
        Key.Five -> '5'.code
        Key.Six -> '6'.code
        Key.Seven -> '7'.code
        Key.Eight -> '8'.code
        Key.Nine -> '9'.code
        Key.Plus -> '+'.code
        Key.Minus -> '-'.code
        Key.Multiply -> '*'.code
        Key.Equals -> '='.code
        Key.Pound -> '#'.code
        Key.A -> if (isUpperCase) 'A'.code else 'a'.code
        Key.B -> if (isUpperCase) 'B'.code else 'b'.code
        Key.C -> if (isUpperCase) 'C'.code else 'c'.code
        Key.D -> if (isUpperCase) 'D'.code else 'd'.code
        Key.E -> if (isUpperCase) 'E'.code else 'e'.code
        Key.F -> if (isUpperCase) 'F'.code else 'f'.code
        Key.G -> if (isUpperCase) 'G'.code else 'g'.code
        Key.H -> if (isUpperCase) 'H'.code else 'h'.code
        Key.I -> if (isUpperCase) 'I'.code else 'i'.code
        Key.J -> if (isUpperCase) 'J'.code else 'j'.code
        Key.K -> if (isUpperCase) 'K'.code else 'k'.code
        Key.L -> if (isUpperCase) 'L'.code else 'l'.code
        Key.M -> if (isUpperCase) 'M'.code else 'm'.code
        Key.N -> if (isUpperCase) 'N'.code else 'n'.code
        Key.O -> if (isUpperCase) 'O'.code else 'o'.code
        Key.P -> if (isUpperCase) 'P'.code else 'p'.code
        Key.Q -> if (isUpperCase) 'Q'.code else 'q'.code
        Key.R -> if (isUpperCase) 'R'.code else 'r'.code
        Key.S -> if (isUpperCase) 'S'.code else 's'.code
        Key.T -> if (isUpperCase) 'T'.code else 't'.code
        Key.U -> if (isUpperCase) 'U'.code else 'u'.code
        Key.V -> if (isUpperCase) 'V'.code else 'v'.code
        Key.W -> if (isUpperCase) 'W'.code else 'w'.code
        Key.X -> if (isUpperCase) 'X'.code else 'x'.code
        Key.Y -> if (isUpperCase) 'Y'.code else 'y'.code
        Key.Z -> if (isUpperCase) 'Z'.code else 'z'.code
        Key.Comma -> ','.code
        Key.Period -> '.'.code
        else -> 0
    }
}

