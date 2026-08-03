package androidx.compose.ui.desktop.macos

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import org.jetbrains.desktop.macos.Characters
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.KeyCode
import org.jetbrains.desktop.macos.KeyModifiersSet
import org.jetbrains.desktop.macos.MouseButton
import org.jetbrains.desktop.macos.SpecialCharacter
import org.jetbrains.desktop.macos.SpecialKey
import org.jetbrains.desktop.macos.TextInputSource

internal fun Event.KeyDown.toKeyEvent(): KeyEvent {
    val keyData = KeyData(this)
    MacOsApplication.logger.debug { "KeyDown ${this} -> $key" }
    return KeyEvent(
        key = keyData.toKey(),
        type = KeyEventType.KeyDown,
        codePoint = keyData.toUtf16CodePoint(),
        isCtrlPressed = modifiers.control,
        isMetaPressed = modifiers.command,
        isAltPressed = modifiers.option,
        isShiftPressed = modifiers.shift,
        nativeEvent = this,
    )
}

internal fun Event.KeyUp.toKeyEvent(): KeyEvent {
    val keyData = KeyData(this)
    MacOsApplication.logger.debug { "KeyUp ${this} -> $key" }
    return KeyEvent(
        key = keyData.toKey(),
        type = KeyEventType.KeyUp,
        codePoint = keyData.toUtf16CodePoint(),
        isCtrlPressed = modifiers.control,
        isMetaPressed = modifiers.command,
        isAltPressed = modifiers.option,
        isShiftPressed = modifiers.shift,
        nativeEvent = this,
    )
}

private data class KeyData(
    val keyCode: KeyCode,
    val characters: Characters,
    val charactersIgnoringModifiers: Characters,
    val modifiers: KeyModifiersSet,
) {
    constructor(keyDown: Event.KeyDown) : this(
        keyCode = keyDown.keyCode,
        characters = keyDown.characters,
        charactersIgnoringModifiers = keyDown.charactersIgnoringModifiers,
        modifiers = keyDown.modifiers,
    )

    constructor(keyUp: Event.KeyUp) : this(
        keyCode = keyUp.keyCode,
        characters = keyUp.characters,
        charactersIgnoringModifiers = keyUp.charactersIgnoringModifiers,
        modifiers = keyUp.modifiers,
    )
}

/**
 * Different considerations
 *
 * - [KeyData.charactersIgnoringModifiers] isn't ignoring Shift modifier, for example,
 * for Shift+Tab, charactersIgnoringModifiers = BackTabCharacter
 *
 * - [KeyData.characters] and [KeyData.charactersIgnoringModifiers] produce empty strings for dead keys
 *
 * - MacOS supports custom keyboard layouts, in theory it should be possible to remap any key including functional keys in any way.
 * (I haven't checked it). UI for choosing layouts gives a clue that only ANSI_* part of codepoints might be affected
 *
 * - For non-latin layouts (Russian, Serbian) we should use latin layer for shortcuts,
 * see: [always_use_command_layout] in gpui or [TextInputSource.isAsciiCapable] in KDT
 *
 * - Dvorak+QWERTY, people expect that QWERTY layer is activated by pressing Command, so to press Alt-R they will press Alt-Cmd-R
 * on MacOS it works because most of the shortcuts contain Command. We might add implicit Command
 */

/**
 * Be aware this function is using the current event and current TextInputSource, thus can be called only from the key event handler
 */
private fun KeyData.toKey(): Key {
    charactersIgnoringModifiers.specialKey?.let { specialKey ->
        keyFromSpecialKey(specialKey)?.let {
            return it
        }
    }
    // It's different from [charactersIgnoringModifiers] see [KeyDown] event doc
    val keyWithNoModifiers =
        Event.charactersByApplyingModifiersForCurrentEvent(KeyModifiersSet.create())
    keyWithNoModifiers.specialCharacter?.let { specialCharacter ->
        keyFromSpecialCharacter(specialCharacter)?.let {
            return it
        }
    }
    val isAsciiInputSource = TextInputSource.current()?.let { currentSource ->
        TextInputSource.isAsciiCapable(currentSource)
    } ?: true

    val text = if (isAsciiInputSource) {
        keyWithNoModifiers.text
    } else {
        // Non Ascii layouts usually have Ascii layer under Command
        Event.charactersByApplyingModifiersForCurrentEvent(KeyModifiersSet.create(command = true)).text
    }
    return keyFromAsciiSymbols(text, modifiers) ?: keyFromInternationalSymbols(text) ?: Key.Unknown
}

private fun keyFromSpecialCharacter(specialCharacter: SpecialCharacter): Key? {
    return when (specialCharacter) {
        SpecialCharacter.EnterCharacter -> Key.Enter
        SpecialCharacter.BackspaceCharacter -> Key.Backspace
        SpecialCharacter.TabCharacter -> Key.Tab
        SpecialCharacter.BackTabCharacter -> Key.Tab
        SpecialCharacter.DeleteCharacter -> Key.Delete
        SpecialCharacter.EscapeCharacter -> Key.Escape
        SpecialCharacter.SpaceCharacter -> Key.Spacebar
        SpecialCharacter.CarriageReturnCharacter -> Key.Enter
        SpecialCharacter.NewlineCharacter -> Key.Enter
        else -> null
    }
}

private fun keyFromSpecialKey(specialKey: SpecialKey): Key? {
    return when (specialKey) {
        SpecialKey.UpArrowFunctionKey -> Key.DirectionUp
        SpecialKey.DownArrowFunctionKey -> Key.DirectionDown
        SpecialKey.LeftArrowFunctionKey -> Key.DirectionLeft
        SpecialKey.RightArrowFunctionKey -> Key.DirectionRight

        SpecialKey.F1FunctionKey -> Key.F1
        SpecialKey.F2FunctionKey -> Key.F2
        SpecialKey.F3FunctionKey -> Key.F3
        SpecialKey.F4FunctionKey -> Key.F4
        SpecialKey.F5FunctionKey -> Key.F5
        SpecialKey.F6FunctionKey -> Key.F6
        SpecialKey.F7FunctionKey -> Key.F7
        SpecialKey.F8FunctionKey -> Key.F8
        SpecialKey.F9FunctionKey -> Key.F9
        SpecialKey.F10FunctionKey -> Key.F10
        SpecialKey.F11FunctionKey -> Key.F11
        SpecialKey.F12FunctionKey -> Key.F12
//        SpecialKey.F13FunctionKey -> Key.F13
//        SpecialKey.F14FunctionKey -> Key.F14
//        SpecialKey.F15FunctionKey -> Key.F15
//        SpecialKey.F16FunctionKey -> Key.F16
//        SpecialKey.F17FunctionKey -> Key.F17
//        SpecialKey.F18FunctionKey -> Key.F18
//        SpecialKey.F19FunctionKey -> Key.F19
//        SpecialKey.F20FunctionKey -> Key.F20
//        SpecialKey.F21FunctionKey -> Key.F21
//        SpecialKey.F22FunctionKey -> Key.F22
//        SpecialKey.F23FunctionKey -> Key.F23
//        SpecialKey.F24FunctionKey -> Key.F24

        SpecialKey.InsertFunctionKey -> Key.Insert
        SpecialKey.DeleteFunctionKey -> Key.Delete
        SpecialKey.HomeFunctionKey -> Key.MoveHome
        SpecialKey.EndFunctionKey -> Key.MoveEnd
        SpecialKey.PageUpFunctionKey -> Key.PageUp
        SpecialKey.PageDownFunctionKey -> Key.PageDown

        SpecialKey.PrintScreenFunctionKey -> Key.PrintScreen
        SpecialKey.ScrollLockFunctionKey -> Key.ScrollLock
        SpecialKey.PauseFunctionKey -> Key.Break
        SpecialKey.MenuFunctionKey -> Key.Menu
        SpecialKey.ClearLineFunctionKey -> Key.NumLock

        SpecialKey.NextFunctionKey -> Key.Forward
        SpecialKey.PrevFunctionKey -> Key.Back
        SpecialKey.HelpFunctionKey -> Key.Insert

        else -> null
    }
}

/**
 * Those symbols are on the base layer for the US keyboard layout.
 */
private fun keyFromAsciiSymbols(text: String, modifiers: KeyModifiersSet): Key? {
    return when (text) {
        "a" -> Key.A
        "b" -> Key.B
        "c" -> Key.C
        "d" -> Key.D
        "e" -> Key.E
        "f" -> Key.F
        "g" -> Key.G
        "h" -> Key.H
        "i" -> Key.I
        "j" -> Key.J
        "k" -> Key.K
        "l" -> Key.L
        "m" -> Key.M
        "n" -> Key.N
        "o" -> Key.O
        "p" -> Key.P
        "q" -> Key.Q
        "r" -> Key.R
        "s" -> Key.S
        "t" -> Key.T
        "u" -> Key.U
        "v" -> Key.V
        "w" -> Key.W
        "x" -> Key.X
        "y" -> Key.Y
        "z" -> Key.Z
        "=" if !modifiers.numericPad -> Key.Equals
        "-" if !modifiers.numericPad -> Key.Minus
        "+" if !modifiers.numericPad -> Key.Plus
        "*" if !modifiers.numericPad -> Key.Multiply
        "]" -> Key.RightBracket
        "[" -> Key.LeftBracket
        "'" -> Key.Apostrophe
        ";" -> Key.Semicolon
        "\\" -> Key.Backslash
        "," if !modifiers.numericPad -> Key.Comma
        "/" if !modifiers.numericPad -> Key.Slash
        "." if !modifiers.numericPad -> Key.Period
        "`" -> Key.Grave
        "@" -> Key.At

        "0" if !modifiers.numericPad -> Key.Zero
        "1" if !modifiers.numericPad -> Key.One
        "2" if !modifiers.numericPad -> Key.Two
        "3" if !modifiers.numericPad -> Key.Three
        "4" if !modifiers.numericPad -> Key.Four
        "5" if !modifiers.numericPad -> Key.Five
        "6" if !modifiers.numericPad -> Key.Six
        "7" if !modifiers.numericPad -> Key.Seven
        "8" if !modifiers.numericPad -> Key.Eight
        "9" if !modifiers.numericPad -> Key.Nine

        "," if modifiers.numericPad -> Key.NumPadComma
        "." if modifiers.numericPad -> Key.NumPadDot
        "*" if modifiers.numericPad -> Key.NumPadMultiply
        "+" if modifiers.numericPad -> Key.NumPadAdd
        "/" if modifiers.numericPad -> Key.NumPadDivide
        "\n" if modifiers.numericPad -> Key.NumPadEnter
        "\r" if modifiers.numericPad -> Key.NumPadEnter
        "\r\n" if modifiers.numericPad -> Key.NumPadEnter
        "-" if modifiers.numericPad -> Key.NumPadSubtract
        "=" if modifiers.numericPad -> Key.NumPadEquals
        "0" if modifiers.numericPad -> Key.NumPad0
        "1" if modifiers.numericPad -> Key.NumPad1
        "2" if modifiers.numericPad -> Key.NumPad2
        "3" if modifiers.numericPad -> Key.NumPad3
        "4" if modifiers.numericPad -> Key.NumPad4
        "5" if modifiers.numericPad -> Key.NumPad5
        "6" if modifiers.numericPad -> Key.NumPad6
        "7" if modifiers.numericPad -> Key.NumPad7
        "8" if modifiers.numericPad -> Key.NumPad8
        "9" if modifiers.numericPad -> Key.NumPad9
        "(" if modifiers.numericPad -> Key.NumPadLeftParenthesis
        ")" if modifiers.numericPad -> Key.NumPadRightParenthesis

        "\n" if !modifiers.numericPad -> Key.Enter
        "\r" if !modifiers.numericPad -> Key.Enter
        "\r\n" if !modifiers.numericPad -> Key.Enter
        "\t" -> Key.Tab
        " " -> Key.Spacebar
        "\u0008" -> Key.Backspace
        "\u001B" -> Key.Escape
        "\u21EA" -> Key.CapsLock

        else -> null
    }
}

/**
 * Those symbols might be on the base layer for non-US keyboard layout.
 */
internal fun keyFromInternationalSymbols(text: String): Key? {
    return when (text) {
        "á" -> Key.AAcute
        "ă" -> Key.ABreve
        "â" -> Key.ACircumflex
        "æ" -> Key.Ae
        "à" -> Key.AGrave
        "ą" -> Key.AOgonek
        "å" -> Key.ARing
        "ä" -> Key.AUmlaut
        "ć" -> Key.CAcute
        "č" -> Key.CCaron
        "ç" -> Key.CCedilla
        "đ" -> Key.DStroke
        "é" -> Key.EAcute
        "ě" -> Key.ECaron
        "ê" -> Key.ECircumflex
        "ë" -> Key.EDiaeresis
        "ė" -> Key.EDot
        "è" -> Key.EGrave
        "ŋ" -> Key.Eng
        "ę" -> Key.EOgonek
        "ð" -> Key.Eth
        "ğ" -> Key.GBreve
        "ġ" -> Key.GDot
        "ħ" -> Key.HStroke
        "í" -> Key.IAcute
        "î" -> Key.ICircumflex
        "ı" -> Key.IDotless
        "ì" -> Key.IGrave
        "į" -> Key.IOgonek
        "ľ" -> Key.LCaron
        "ł" -> Key.LStroke
        "ň" -> Key.NCaron
        "ñ" -> Key.NTilde
        "ó" -> Key.OAcute
        "ô" -> Key.OCircumflex
        "ő" -> Key.ODoubleAcute
        "ò" -> Key.OGrave
        "ơ" -> Key.OHorn
        "ö" -> Key.OUmlaut
        "ø" -> Key.OStroke
        "õ" -> Key.OTilde
        "ř" -> Key.RCaron
        "š" -> Key.SCaron
        "ş" -> Key.SCedilla
        "ə" -> Key.Schwa
        "ș" -> Key.SComma
        "ß" -> Key.SharpS
        "ť" -> Key.TCaron
        "ț" -> Key.TComma
        "þ" -> Key.Thorn
        "ŧ" -> Key.TStroke
        "ú" -> Key.UAcute
        "û" -> Key.UCircumflex
        "ű" -> Key.UDoubleAcute
        "ù" -> Key.UGrave
        "ư" -> Key.UHorn
        "ū" -> Key.UMacron
        "ų" -> Key.UOgonek
        "ů" -> Key.URing
        "ü" -> Key.UUmlaut
        "ý" -> Key.YAcute
        "ž" -> Key.ZCaron
        "ż" -> Key.ZDot

        "\u00B4" -> Key.AcuteAccent // ´
        "&" -> Key.Ampersand
        "*" -> Key.Asterisk
        "@" -> Key.At
        "\u02D8" -> Key.Breve // ˘
        "\u02C7" -> Key.Caron // ˇ
        "\u00B8" -> Key.Cedilla // ¸
        "^" -> Key.CircumflexAccent
        ":" -> Key.Colon
        "\u00A8" -> Key.Diaeresis // ¨
        "$" -> Key.DollarSign
        "\u20AB" -> Key.DongSign // ₫
        "\u02D9" -> Key.DotAbove // ˙
        // MacOS prepends some symbols with Space
        "\u0020\u0323" -> Key.DotBelow //  ̣
        "\u02DD" -> Key.DoubleAcuteAccent // ˝
        "!" -> Key.ExclamationMark
        ">" -> Key.GreaterSign
        "\u0020\u0309" -> Key.HookAbove // ̉
        "\u00A1" -> Key.InvertedExclamationMark // ¡
        "\u00BF" -> Key.InvertedQuestionMark // ¿
        "{" -> Key.LeftBrace
        "(" -> Key.LeftParenthesis
        "<" -> Key.LessSign
        "\u201E" -> Key.LowQuotationMark // „
        "\u00AF" -> Key.Macron // ¯
        "\u00BA" -> Key.MasculineOrdinalIndicator // º
        "#" -> Key.NumberSign
        "\u02DB" -> Key.Ogonek // ˛
        "+" -> Key.Plus
        "\u00A3" -> Key.PoundSign // £
        "\"" -> Key.QuotationMark
        "\u0020\u05BF" -> Key.Rafe // ֿ
        "}" -> Key.RightBrace
        ")" -> Key.RightParenthesis
        "\u02DA" -> Key.RingAbove // ˚
        "\u00A7" -> Key.SectionSign // §
        "\u00B2" -> Key.SuperscriptTwo // ²
        "~" -> Key.Tilde
        "_" -> Key.Underscore
        "|" -> Key.VerticalLine
        else -> null
    }
}

private fun KeyData.toUtf16CodePoint(): Int {
    return when {
        // We're not interested in these characters from the Unicode private use area (PUA)
        characters.specialKey != null -> 0
        characters.text.isEmpty() -> 0
        else -> characters.text.codePointAt(0)
    }
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

internal fun Event.Swipe.toKeyEvent(
    mouseButton: MouseButton,
    type: KeyEventType,
    keyboardModifiers: PointerKeyboardModifiers,
): KeyEvent {
    return KeyEvent(
        key = mouseButton.toKey(),
        type = type,
        codePoint = 0,
        isCtrlPressed = keyboardModifiers.isCtrlPressed,
        isMetaPressed = keyboardModifiers.isMetaPressed,
        isAltPressed = keyboardModifiers.isAltPressed,
        isShiftPressed = keyboardModifiers.isShiftPressed,
        nativeEvent = this,
    )
}

// AIR-6023: KDT MouseButton.value is 0-based, so 0 -> Button1
internal fun MouseButton.toKey(): Key = when (value) {
    0 -> Key.Button1
    1 -> Key.Button2
    2 -> Key.Button3
    3 -> Key.Button4
    4 -> Key.Button5
    5 -> Key.Button6
    6 -> Key.Button7
    7 -> Key.Button8
    8 -> Key.Button9
    9 -> Key.Button10
    10 -> Key.Button11
    11 -> Key.Button12
    12 -> Key.Button13
    13 -> Key.Button14
    14 -> Key.Button15
    15 -> Key.Button16
    else -> Key.Unknown
}

internal val Key.isModifier: Boolean
    get() = when (this) {
        Key.MetaLeft,
        Key.MetaRight,
        Key.AltLeft,
        Key.AltRight,
        Key.CtrlLeft,
        Key.CtrlRight,
        Key.ShiftLeft,
        Key.ShiftRight,
        Key.Function,
        Key.NumLock,
        Key.ScrollLock,
        Key.CapsLock,
        Key.Symbol,
            -> true
        else -> false
    }
