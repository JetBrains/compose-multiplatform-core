package androidx.compose.ui.desktop.linux

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import org.jetbrains.desktop.linux.KeyModifiers
import org.jetbrains.desktop.linux.KeySym

internal fun Set<KeyModifiers>.toPointerKeyboardModifiers(): PointerKeyboardModifiers {
    return PointerKeyboardModifiers(
        isCtrlPressed = KeyModifiers.Control in this,
        isMetaPressed = KeyModifiers.Logo in this,
        isAltPressed = KeyModifiers.Alt in this,
        isShiftPressed = KeyModifiers.Shift in this,
        isAltGraphPressed = false,
        isSymPressed = false,
        isScrollLockOn = false,
        isFunctionPressed = false,
        isCapsLockOn = KeyModifiers.CapsLock in this,
        isNumLockOn = KeyModifiers.NumLock in this,
    )
}

internal fun String.firstCodePointOrNull(): Int? {
    return if (isNotEmpty()) codePointAt(0) else null
}

internal fun KeySym.toKey(): Key = when (value) {
    KeySym.`0` -> Key.Zero
    KeySym.`1` -> Key.One
    KeySym.`2` -> Key.Two
    KeySym.`3` -> Key.Three
    KeySym.`4` -> Key.Four
    KeySym.`5` -> Key.Five
    KeySym.`6` -> Key.Six
    KeySym.`7` -> Key.Seven
    KeySym.`8` -> Key.Eight
    KeySym.`9` -> Key.Nine

    KeySym.A,
    KeySym.a,
        -> Key.A
    KeySym.B,
    KeySym.b,
        -> Key.B
    KeySym.C,
    KeySym.c,
        -> Key.C
    KeySym.D,
    KeySym.d,
        -> Key.D
    KeySym.E,
    KeySym.e,
        -> Key.E
    KeySym.F,
    KeySym.f,
        -> Key.F
    KeySym.G,
    KeySym.g,
        -> Key.G
    KeySym.H,
    KeySym.h,
        -> Key.H
    KeySym.I,
    KeySym.i,
        -> Key.I
    KeySym.J,
    KeySym.j,
        -> Key.J
    KeySym.K,
    KeySym.k,
        -> Key.K
    KeySym.L,
    KeySym.l,
        -> Key.L
    KeySym.M,
    KeySym.m,
        -> Key.M
    KeySym.N,
    KeySym.n,
        -> Key.N
    KeySym.O,
    KeySym.o,
        -> Key.O
    KeySym.P,
    KeySym.p,
        -> Key.P
    KeySym.Q,
    KeySym.q,
        -> Key.Q
    KeySym.R,
    KeySym.r,
        -> Key.R
    KeySym.S,
    KeySym.s,
        -> Key.S
    KeySym.T,
    KeySym.t,
        -> Key.T
    KeySym.U,
    KeySym.u,
        -> Key.U
    KeySym.V,
    KeySym.v,
        -> Key.V
    KeySym.W,
    KeySym.w,
        -> Key.W
    KeySym.X,
    KeySym.x,
        -> Key.X
    KeySym.Y,
    KeySym.y,
        -> Key.Y
    KeySym.Z,
    KeySym.z,
        -> Key.Z

    KeySym.F1 -> Key.F1
    KeySym.F2 -> Key.F2
    KeySym.F3 -> Key.F3
    KeySym.F4 -> Key.F4
    KeySym.F5 -> Key.F5
    KeySym.F6 -> Key.F6
    KeySym.F7 -> Key.F7
    KeySym.F8 -> Key.F8
    KeySym.F9 -> Key.F9
    KeySym.F10 -> Key.F10
    KeySym.F11 -> Key.F11
    KeySym.F12 -> Key.F12
    KeySym.F13,
    KeySym.F14,
    KeySym.F15,
    KeySym.F16,
    KeySym.F17,
    KeySym.F18,
    KeySym.F19,
    KeySym.F20,
    KeySym.F21,
    KeySym.F22,
    KeySym.F23,
    KeySym.F24,
        -> Key.Unknown

    KeySym.apostrophe,
    KeySym.quoteright,
        -> Key.Apostrophe
    KeySym.quotedbl -> Key.QuotationMark
    KeySym.backslash -> Key.Backslash
    KeySym.bar -> Key.VerticalLine
    KeySym.comma -> Key.Comma
    KeySym.less -> Key.LessSign
    KeySym.equal -> Key.Equals
    KeySym.plus -> Key.Plus
    KeySym.grave,
    KeySym.quoteleft,
    0x7eU, // asciitilde - produced by Shift+BackQuote on US keyboard, not in KeySym companion
        -> Key.Grave
    KeySym.asciicircum -> Key.CircumflexAccent
    KeySym.bracketleft -> Key.LeftBracket
    KeySym.braceleft -> Key.LeftBrace
    KeySym.minus -> Key.Minus
    KeySym.underscore -> Key.Underscore
    KeySym.period -> Key.Period
    KeySym.greater -> Key.GreaterSign
    KeySym.bracketright -> Key.RightBracket
    KeySym.braceright -> Key.RightBrace
    KeySym.semicolon -> Key.Semicolon
    KeySym.colon -> Key.Colon
    KeySym.slash -> Key.Slash
    KeySym.at -> Key.At
    KeySym.ampersand -> Key.Ampersand
    KeySym.asterisk -> Key.Asterisk
    KeySym.numbersign -> Key.NumberSign
    KeySym.dollar -> Key.DollarSign
    KeySym.exclam -> Key.ExclamationMark
    KeySym.parenleft -> Key.LeftParenthesis
    KeySym.parenright -> Key.RightParenthesis

    KeySym.KP_0 -> Key.NumPad0
    KeySym.KP_1 -> Key.NumPad1
    KeySym.KP_2 -> Key.NumPad2
    KeySym.KP_3 -> Key.NumPad3
    KeySym.KP_4 -> Key.NumPad4
    KeySym.KP_5 -> Key.NumPad5
    KeySym.KP_6 -> Key.NumPad6
    KeySym.KP_7 -> Key.NumPad7
    KeySym.KP_8 -> Key.NumPad8
    KeySym.KP_9 -> Key.NumPad9
    KeySym.KP_Add -> Key.NumPadAdd
    KeySym.KP_Subtract -> Key.NumPadSubtract
    KeySym.KP_Multiply -> Key.NumPadMultiply
    KeySym.KP_Divide -> Key.NumPadDivide
    KeySym.KP_Decimal -> Key.NumPadDot
    KeySym.KP_Separator -> Key.NumPadComma
    KeySym.KP_Equal -> Key.NumPadEquals
    KeySym.KP_Enter -> Key.NumPadEnter
    KeySym.KP_F1 -> Key.F1
    KeySym.KP_F2 -> Key.F2
    KeySym.KP_F3 -> Key.F3
    KeySym.KP_F4 -> Key.F4
    KeySym.KP_Home -> Key.MoveHome
    KeySym.KP_Left -> Key.DirectionLeft
    KeySym.KP_Up -> Key.DirectionUp
    KeySym.KP_Right -> Key.DirectionRight
    KeySym.KP_Down -> Key.DirectionDown
    KeySym.KP_Prior,
    KeySym.KP_Page_Up,
        -> Key.PageUp
    KeySym.KP_Next,
    KeySym.KP_Page_Down,
        -> Key.PageDown
    KeySym.KP_End -> Key.MoveEnd
    KeySym.KP_Insert -> Key.Insert
    KeySym.KP_Delete -> Key.Delete
    KeySym.KP_Space -> Key.Spacebar
    KeySym.KP_Tab -> Key.Tab
    KeySym.Clear,
    KeySym.KP_Begin,
        -> Key.Clear
    KeySym.Num_Lock -> Key.NumLock

    KeySym.Alt_L -> Key.AltLeft
    KeySym.Alt_R,
    KeySym.ISO_Level3_Shift,
        -> Key.AltRight
    KeySym.Control_L -> Key.CtrlLeft
    KeySym.Control_R -> Key.CtrlRight
    KeySym.Shift_L -> Key.ShiftLeft
    KeySym.Shift_R -> Key.ShiftRight
    KeySym.Meta_L,
    KeySym.Super_L,
        -> Key.MetaLeft
    KeySym.Meta_R,
    KeySym.Super_R,
        -> Key.MetaRight

    KeySym.Return,
    KeySym.Linefeed,
    KeySym.ISO_Enter,
        -> Key.Enter
    KeySym.Tab -> Key.Tab
    KeySym.space -> Key.Spacebar
    KeySym.BackSpace -> Key.Backspace
    KeySym.Escape -> Key.Escape
    KeySym.Caps_Lock,
    KeySym.Shift_Lock,
        -> Key.CapsLock
    KeySym.Print,
    KeySym.Sys_Req,
        -> Key.PrintScreen
    KeySym.Scroll_Lock -> Key.ScrollLock
    KeySym.Pause,
    KeySym.Break,
        -> Key.Break
    KeySym.Insert -> Key.Insert
    KeySym.Delete -> Key.Delete
    KeySym.Home -> Key.MoveHome
    KeySym.End -> Key.MoveEnd
    KeySym.Prior,
    KeySym.Page_Up,
        -> Key.PageUp
    KeySym.Next,
    KeySym.Page_Down,
        -> Key.PageDown
    KeySym.Left -> Key.DirectionLeft
    KeySym.Right -> Key.DirectionRight
    KeySym.Up -> Key.DirectionUp
    KeySym.Down -> Key.DirectionDown
    KeySym.Menu -> Key.Menu
    KeySym.Help -> Key.Help

    KeySym.Kanji,
    KeySym.Mode_switch,
    KeySym.script_switch,
    KeySym.Romaji,
        -> Key.LanguageSwitch
    KeySym.Muhenkan -> Key.Muhenkan
    KeySym.Henkan_Mode,
    KeySym.Henkan,
        -> Key.Henkan
    KeySym.Hiragana,
    KeySym.Katakana,
    KeySym.Hiragana_Katakana,
        -> Key.KatakanaHiragana
    KeySym.Kana_Lock,
    KeySym.Kana_Shift,
        -> Key.Kana
    KeySym.Eisu_Shift,
    KeySym.Eisu_toggle,
        -> Key.Eisu
    KeySym.Zenkaku,
    KeySym.Hankaku,
    KeySym.Zenkaku_Hankaku,
        -> Key.ZenkakuHankaru

    else -> Key.Unknown
}
