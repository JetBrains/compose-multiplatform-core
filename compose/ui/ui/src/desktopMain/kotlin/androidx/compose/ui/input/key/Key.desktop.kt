/*
 * Copyright 2020 The Android Open Source Project
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

import androidx.compose.ui.input.key.Key.Companion.Number
import androidx.compose.ui.util.packInts
import androidx.compose.ui.util.unpackInt1
import androidx.compose.ui.util.unpackInt2
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_LOCATION_LEFT
import java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD
import java.awt.event.KeyEvent.KEY_LOCATION_RIGHT
import java.awt.event.KeyEvent.KEY_LOCATION_STANDARD

// TODO(demin): implement most of key codes

/**
 * Actual implementation of [Key] for Desktop.
 *
 * @param keyCode an integer code representing the key pressed. Note: This keycode can be used to
 * uniquely identify a hardware key. It is different from the native keycode.
 */
@JvmInline
actual value class Key(val keyCode: Long) {
    actual companion object {
        /** Unknown key. */
        actual val Unknown: Key
            get() = Key(KeyEvent.VK_UNDEFINED)

        /**
         * System Home key.
         *
         * This key is handled by the framework and is never delivered to applications.
         */
        @Deprecated(
            "`Key.Home` was mapped to the keyboard \"Home\" key in error. It is meant to be the" +
                " \"system\" home key on Android, and should never be delivered to applications. " +
                "For the keyboard \"Home\" key use `Key.MoveHome`. For the Android system " +
                "\"Home\" key (unlikely to be needed), use `Key.SystemHome`",
            level = DeprecationLevel.ERROR,
        )
        actual val Home: Key
            get() = Key(KeyEvent.VK_HOME)

        /**
         * System Home key.
         *
         * This key is handled by the framework and is never delivered to applications.
         */
        actual val SystemHome: Key
            get() = Key(-1000000184)

        /** Help key. */
        actual val Help: Key
            get() = Key(KeyEvent.VK_HELP)

        /**
         * Up Arrow Key / Directional Pad Up key.
         *
         * May also be synthesized from trackball motions.
         */
        actual val DirectionUp: Key
            get() = Key(KeyEvent.VK_UP)

        /**
         * Down Arrow Key / Directional Pad Down key.
         *
         * May also be synthesized from trackball motions.
         */
        actual val DirectionDown: Key
            get() = Key(KeyEvent.VK_DOWN)

        /**
         * Left Arrow Key / Directional Pad Left key.
         *
         * May also be synthesized from trackball motions.
         */
        actual val DirectionLeft: Key
            get() = Key(KeyEvent.VK_LEFT)

        /**
         * Right Arrow Key / Directional Pad Right key.
         *
         * May also be synthesized from trackball motions.
         */
        actual val DirectionRight: Key
            get() = Key(KeyEvent.VK_RIGHT)

        /** '0' key. */
        actual val Zero: Key
            get() = Key(KeyEvent.VK_0)

        /** '1' key. */
        actual val One: Key
            get() = Key(KeyEvent.VK_1)

        /** '2' key. */
        actual val Two: Key
            get() = Key(KeyEvent.VK_2)

        /** '3' key. */
        actual val Three: Key
            get() = Key(KeyEvent.VK_3)

        /** '4' key. */
        actual val Four: Key
            get() = Key(KeyEvent.VK_4)

        /** '5' key. */
        actual val Five: Key
            get() = Key(KeyEvent.VK_5)

        /** '6' key. */
        actual val Six: Key
            get() = Key(KeyEvent.VK_6)

        /** '7' key. */
        actual val Seven: Key
            get() = Key(KeyEvent.VK_7)

        /** '8' key. */
        actual val Eight: Key
            get() = Key(KeyEvent.VK_8)

        /** '9' key. */
        actual val Nine: Key
            get() = Key(KeyEvent.VK_9)

        /** '+' key. */
        actual val Plus: Key
            get() = Key(KeyEvent.VK_PLUS)

        /** '-' key. */
        actual val Minus: Key
            get() = Key(KeyEvent.VK_MINUS)

        /** '*' key. */
        actual val Multiply: Key
            get() = Key(KeyEvent.VK_MULTIPLY)

        /** '=' key. */
        actual val Equals: Key
            get() = Key(KeyEvent.VK_EQUALS)

        /** '#' key. */
        actual val Pound: Key
            get() = Key(KeyEvent.VK_NUMBER_SIGN)

        /** 'A' key. */
        actual val A: Key
            get() = Key(KeyEvent.VK_A)

        /** 'B' key. */
        actual val B: Key
            get() = Key(KeyEvent.VK_B)

        /** 'C' key. */
        actual val C: Key
            get() = Key(KeyEvent.VK_C)

        /** 'D' key. */
        actual val D: Key
            get() = Key(KeyEvent.VK_D)

        /** 'E' key. */
        actual val E: Key
            get() = Key(KeyEvent.VK_E)

        /** 'F' key. */
        actual val F: Key
            get() = Key(KeyEvent.VK_F)

        /** 'G' key. */
        actual val G: Key
            get() = Key(KeyEvent.VK_G)

        /** 'H' key. */
        actual val H: Key
            get() = Key(KeyEvent.VK_H)

        /** 'I' key. */
        actual val I: Key
            get() = Key(KeyEvent.VK_I)

        /** 'J' key. */
        actual val J: Key
            get() = Key(KeyEvent.VK_J)

        /** 'K' key. */
        actual val K: Key
            get() = Key(KeyEvent.VK_K)

        /** 'L' key. */
        actual val L: Key
            get() = Key(KeyEvent.VK_L)

        /** 'M' key. */
        actual val M: Key
            get() = Key(KeyEvent.VK_M)

        /** 'N' key. */
        actual val N: Key
            get() = Key(KeyEvent.VK_N)

        /** 'O' key. */
        actual val O: Key
            get() = Key(KeyEvent.VK_O)

        /** 'P' key. */
        actual val P: Key
            get() = Key(KeyEvent.VK_P)

        /** 'Q' key. */
        actual val Q: Key
            get() = Key(KeyEvent.VK_Q)

        /** 'R' key. */
        actual val R: Key
            get() = Key(KeyEvent.VK_R)

        /** 'S' key. */
        actual val S: Key
            get() = Key(KeyEvent.VK_S)

        /** 'T' key. */
        actual val T: Key
            get() = Key(KeyEvent.VK_T)

        /** 'U' key. */
        actual val U: Key
            get() = Key(KeyEvent.VK_U)

        /** 'V' key. */
        actual val V: Key
            get() = Key(KeyEvent.VK_V)

        /** 'W' key. */
        actual val W: Key
            get() = Key(KeyEvent.VK_W)

        /** 'X' key. */
        actual val X: Key
            get() = Key(KeyEvent.VK_X)

        /** 'Y' key. */
        actual val Y: Key
            get() = Key(KeyEvent.VK_Y)

        /** 'Z' key. */
        actual val Z: Key
            get() = Key(KeyEvent.VK_Z)

        /** ',' key. */
        actual val Comma: Key
            get() = Key(KeyEvent.VK_COMMA)

        /** '.' key. */
        actual val Period: Key
            get() = Key(KeyEvent.VK_PERIOD)

        /** Left Alt modifier key. */
        actual val AltLeft: Key
            get() = Key(KeyEvent.VK_ALT, KEY_LOCATION_LEFT)

        /** Right Alt modifier key. */
        actual val AltRight: Key
            get() = Key(KeyEvent.VK_ALT, KEY_LOCATION_RIGHT)

        /** Left Shift modifier key. */
        actual val ShiftLeft: Key
            get() = Key(KeyEvent.VK_SHIFT, KEY_LOCATION_LEFT)

        /** Right Shift modifier key. */
        actual val ShiftRight: Key
            get() = Key(KeyEvent.VK_SHIFT, KEY_LOCATION_RIGHT)

        /** Tab key. */
        actual val Tab: Key
            get() = Key(KeyEvent.VK_TAB)

        /** Space key. */
        actual val Spacebar: Key
            get() = Key(KeyEvent.VK_SPACE)

        /** Enter key. */
        actual val Enter: Key
            get() = Key(KeyEvent.VK_ENTER)

        /**
         * Backspace key.
         *
         * Deletes characters before the insertion point, unlike [Delete].
         */
        actual val Backspace: Key
            get() = Key(KeyEvent.VK_BACK_SPACE)

        /**
         * Delete key.
         *
         * Deletes characters ahead of the insertion point, unlike [Backspace].
         */
        actual val Delete: Key
            get() = Key(KeyEvent.VK_DELETE)

        /** Escape key. */
        actual val Escape: Key
            get() = Key(KeyEvent.VK_ESCAPE)

        /** Left Control modifier key. */
        actual val CtrlLeft: Key
            get() = Key(KeyEvent.VK_CONTROL, KEY_LOCATION_LEFT)

        /** Right Control modifier key. */
        actual val CtrlRight: Key
            get() = Key(KeyEvent.VK_CONTROL, KEY_LOCATION_RIGHT)

        /** Caps Lock key. */
        actual val CapsLock: Key
            get() = Key(KeyEvent.VK_CAPS_LOCK)

        /** Scroll Lock key. */
        actual val ScrollLock: Key
            get() = Key(KeyEvent.VK_SCROLL_LOCK)

        /** Left Meta modifier key. */
        actual val MetaLeft: Key
            get() = Key(KeyEvent.VK_META, KEY_LOCATION_LEFT)

        /** Right Meta modifier key. */
        actual val MetaRight: Key
            get() = Key(KeyEvent.VK_META, KEY_LOCATION_RIGHT)

        /** System Request / Print Screen key. */
        actual val PrintScreen: Key
            get() = Key(KeyEvent.VK_PRINTSCREEN)

        /**
         * Home Movement key.
         *
         * Used for scrolling or moving the cursor around to the start of a line or to the top of a
         * list.
         */
        actual val MoveHome: Key
            get() = Key(KeyEvent.VK_HOME)

        /**
         * End Movement key.
         *
         * Used for scrolling or moving the cursor around to the end of a line or to the bottom of a
         * list.
         */
        actual val MoveEnd: Key
            get() = Key(KeyEvent.VK_END)

        /**
         * Insert key.
         *
         * Toggles insert / overwrite edit mode.
         */
        actual val Insert: Key
            get() = Key(KeyEvent.VK_INSERT)

        /** Cut key. */
        actual val Cut: Key
            get() = Key(KeyEvent.VK_CUT)

        /** Copy key. */
        actual val Copy: Key
            get() = Key(KeyEvent.VK_COPY)

        /** Paste key. */
        actual val Paste: Key
            get() = Key(KeyEvent.VK_PASTE)

        /** '`' (backtick) key. */
        actual val Grave: Key
            get() = Key(KeyEvent.VK_BACK_QUOTE)

        /** '[' key. */
        actual val LeftBracket: Key
            get() = Key(KeyEvent.VK_OPEN_BRACKET)

        /** ']' key. */
        actual val RightBracket: Key
            get() = Key(KeyEvent.VK_CLOSE_BRACKET)

        /** '/' key. */
        actual val Slash: Key
            get() = Key(KeyEvent.VK_SLASH)

        /** '\' key. */
        actual val Backslash: Key
            get() = Key(KeyEvent.VK_BACK_SLASH)

        /** ';' key. */
        actual val Semicolon: Key
            get() = Key(KeyEvent.VK_SEMICOLON)

        /** ''' (apostrophe) key. */
        actual val Apostrophe: Key
            get() = Key(KeyEvent.VK_QUOTE)

        /** '@' key. */
        actual val At: Key
            get() = Key(KeyEvent.VK_AT)

        /** Page Up key. */
        actual val PageUp: Key
            get() = Key(KeyEvent.VK_PAGE_UP)

        /** Page Down key. */
        actual val PageDown: Key
            get() = Key(KeyEvent.VK_PAGE_DOWN)

        /** F1 key. */
        actual val F1: Key
            get() = Key(KeyEvent.VK_F1)

        /** F2 key. */
        actual val F2: Key
            get() = Key(KeyEvent.VK_F2)

        /** F3 key. */
        actual val F3: Key
            get() = Key(KeyEvent.VK_F3)

        /** F4 key. */
        actual val F4: Key
            get() = Key(KeyEvent.VK_F4)

        /** F5 key. */
        actual val F5: Key
            get() = Key(KeyEvent.VK_F5)

        /** F6 key. */
        actual val F6: Key
            get() = Key(KeyEvent.VK_F6)

        /** F7 key. */
        actual val F7: Key
            get() = Key(KeyEvent.VK_F7)

        /** F8 key. */
        actual val F8: Key
            get() = Key(KeyEvent.VK_F8)

        /** F9 key. */
        actual val F9: Key
            get() = Key(KeyEvent.VK_F9)

        /** F10 key. */
        actual val F10: Key
            get() = Key(KeyEvent.VK_F10)

        /** F11 key. */
        actual val F11: Key
            get() = Key(KeyEvent.VK_F11)

        /** F12 key. */
        actual val F12: Key
            get() = Key(KeyEvent.VK_F12)

        /**
         * Num Lock key.
         *
         * This is the Num Lock key; it is different from [Number].
         * This key alters the behavior of other keys on the numeric keypad.
         */
        actual val NumLock: Key
            get() = Key(KeyEvent.VK_NUM_LOCK, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '0' key. */
        actual val NumPad0: Key
            get() = Key(KeyEvent.VK_NUMPAD0, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '1' key. */
        actual val NumPad1: Key
            get() = Key(KeyEvent.VK_NUMPAD1, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '2' key. */
        actual val NumPad2: Key
            get() = Key(KeyEvent.VK_NUMPAD2, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '3' key. */
        actual val NumPad3: Key
            get() = Key(KeyEvent.VK_NUMPAD3, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '4' key. */
        actual val NumPad4: Key
            get() = Key(KeyEvent.VK_NUMPAD4, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '5' key. */
        actual val NumPad5: Key
            get() = Key(KeyEvent.VK_NUMPAD5, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '6' key. */
        actual val NumPad6: Key
            get() = Key(KeyEvent.VK_NUMPAD6, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '7' key. */
        actual val NumPad7: Key
            get() = Key(KeyEvent.VK_NUMPAD7, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '8' key. */
        actual val NumPad8: Key
            get() = Key(KeyEvent.VK_NUMPAD8, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '9' key. */
        actual val NumPad9: Key
            get() = Key(KeyEvent.VK_NUMPAD9, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '/' key (for division). */
        actual val NumPadDivide: Key
            get() = Key(KeyEvent.VK_DIVIDE, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '*' key (for multiplication). */
        actual val NumPadMultiply: Key
            get() = Key(KeyEvent.VK_MULTIPLY, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '-' key (for subtraction). */
        actual val NumPadSubtract: Key
            get() = Key(KeyEvent.VK_SUBTRACT, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '+' key (for addition). */
        actual val NumPadAdd: Key
            get() = Key(KeyEvent.VK_ADD, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '.' key (for decimals or digit grouping). */
        actual val NumPadDot: Key
            get() = Key(KeyEvent.VK_DECIMAL, KEY_LOCATION_NUMPAD)

        /** Numeric keypad ',' key (for decimals or digit grouping). */
        actual val NumPadComma: Key
            get() = Key(KeyEvent.VK_COMMA, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Enter key. */
        actual val NumPadEnter: Key
            get() = Key(KeyEvent.VK_ENTER, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '=' key. */
        actual val NumPadEquals: Key
            get() = Key(KeyEvent.VK_EQUALS, KEY_LOCATION_NUMPAD)

        /** Numeric keypad '(' key. */
        actual val NumPadLeftParenthesis: Key
            get() = Key(KeyEvent.VK_LEFT_PARENTHESIS, KEY_LOCATION_NUMPAD)

        /** Numeric keypad ')' key. */
        actual val NumPadRightParenthesis: Key
            get() = Key(KeyEvent.VK_RIGHT_PARENTHESIS, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Up Arrow Key. */
        actual val NumPadDirectionUp: Key
            get() = Key(KeyEvent.VK_UP, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Down Arrow Key. */
        actual val NumPadDirectionDown: Key
            get() = Key(KeyEvent.VK_DOWN, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Left Arrow Key. */
        actual val NumPadDirectionLeft: Key
            get() = Key(KeyEvent.VK_LEFT, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Right Arrow Key. */
        actual val NumPadDirectionRight: Key
            get() = Key(KeyEvent.VK_RIGHT, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Home Key. */
        actual val NumPadMoveHome: Key
            get() = Key(KeyEvent.VK_HOME, KEY_LOCATION_NUMPAD)

        /** Numeric keypad End Key. */
        actual val NumPadMoveEnd: Key
            get() = Key(KeyEvent.VK_END, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Page Up Key. */
        actual val NumPadPageUp: Key
            get() = Key(KeyEvent.VK_PAGE_UP, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Page Down Key. */
        actual val NumPadPageDown: Key
            get() = Key(KeyEvent.VK_PAGE_DOWN, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Insert Key. */
        actual val NumPadInsert: Key
            get() = Key(KeyEvent.VK_INSERT, KEY_LOCATION_NUMPAD)

        /** Numeric keypad Delete Key. */
        actual val NumPadDelete: Key
            get() = Key(KeyEvent.VK_DELETE, KEY_LOCATION_NUMPAD)

        // Unsupported Keys. These keys will never be sent by the desktop. However we need unique
        // keycodes so that these constants can be used in a when statement without a warning.
        actual val SoftLeft: Key
            get() = Key(-1000000001)
        actual val SoftRight: Key
            get() = Key(-1000000002)
        actual val Back: Key
            get() = Key(-1000000003)
        actual val NavigatePrevious: Key
            get() = Key(-1000000004)
        actual val NavigateNext: Key
            get() = Key(-1000000005)
        actual val NavigateIn: Key
            get() = Key(-1000000006)
        actual val NavigateOut: Key
            get() = Key(-1000000007)
        actual val SystemNavigationUp: Key
            get() = Key(-1000000008)
        actual val SystemNavigationDown: Key
            get() = Key(-1000000009)
        actual val SystemNavigationLeft: Key
            get() = Key(-1000000010)
        actual val SystemNavigationRight: Key
            get() = Key(-1000000011)
        actual val Call: Key
            get() = Key(-1000000012)
        actual val EndCall: Key
            get() = Key(-1000000013)
        actual val DirectionCenter: Key
            get() = Key(-1000000014)
        actual val DirectionUpLeft: Key
            get() = Key(-1000000015)
        actual val DirectionDownLeft: Key
            get() = Key(-1000000016)
        actual val DirectionUpRight: Key
            get() = Key(-1000000017)
        actual val DirectionDownRight: Key
            get() = Key(-1000000018)
        actual val VolumeUp: Key
            get() = Key(-1000000019)
        actual val VolumeDown: Key
            get() = Key(-1000000020)
        actual val Power: Key
            get() = Key(-1000000021)
        actual val Camera: Key
            get() = Key(-1000000022)
        actual val Clear: Key
            get() = Key(-1000000023)
        actual val Symbol: Key
            get() = Key(-1000000024)
        actual val Browser: Key
            get() = Key(-1000000025)
        actual val Envelope: Key
            get() = Key(-1000000026)
        actual val Function: Key
            get() = Key(-1000000027)
        actual val Break: Key
            get() = Key(-1000000028)
        actual val Number: Key
            get() = Key(-1000000031)
        actual val HeadsetHook: Key
            get() = Key(-1000000032)
        actual val Focus: Key
            get() = Key(-1000000033)
        actual val Menu: Key
            get() = Key(-1000000034)
        actual val Notification: Key
            get() = Key(-1000000035)
        actual val Search: Key
            get() = Key(-1000000036)
        actual val PictureSymbols: Key
            get() = Key(-1000000037)
        actual val SwitchCharset: Key
            get() = Key(-1000000038)
        actual val ButtonA: Key
            get() = Key(-1000000039)
        actual val ButtonB: Key
            get() = Key(-1000000040)
        actual val ButtonC: Key
            get() = Key(-1000000041)
        actual val ButtonX: Key
            get() = Key(-1000000042)
        actual val ButtonY: Key
            get() = Key(-1000000043)
        actual val ButtonZ: Key
            get() = Key(-1000000044)
        actual val ButtonL1: Key
            get() = Key(-1000000045)
        actual val ButtonR1: Key
            get() = Key(-1000000046)
        actual val ButtonL2: Key
            get() = Key(-1000000047)
        actual val ButtonR2: Key
            get() = Key(-1000000048)
        actual val ButtonThumbLeft: Key
            get() = Key(-1000000049)
        actual val ButtonThumbRight: Key
            get() = Key(-1000000050)
        actual val ButtonStart: Key
            get() = Key(-1000000051)
        actual val ButtonSelect: Key
            get() = Key(-1000000052)
        actual val ButtonMode: Key
            get() = Key(-1000000053)
        actual val Button1: Key
            get() = Key(-1000000054)
        actual val Button2: Key
            get() = Key(-1000000055)
        actual val Button3: Key
            get() = Key(-1000000056)
        actual val Button4: Key
            get() = Key(-1000000057)
        actual val Button5: Key
            get() = Key(-1000000058)
        actual val Button6: Key
            get() = Key(-1000000059)
        actual val Button7: Key
            get() = Key(-1000000060)
        actual val Button8: Key
            get() = Key(-1000000061)
        actual val Button9: Key
            get() = Key(-1000000062)
        actual val Button10: Key
            get() = Key(-1000000063)
        actual val Button11: Key
            get() = Key(-1000000064)
        actual val Button12: Key
            get() = Key(-1000000065)
        actual val Button13: Key
            get() = Key(-1000000066)
        actual val Button14: Key
            get() = Key(-1000000067)
        actual val Button15: Key
            get() = Key(-1000000068)
        actual val Button16: Key
            get() = Key(-1000000069)
        actual val Forward: Key
            get() = Key(-1000000070)
        actual val MediaPlay: Key
            get() = Key(-1000000071)
        actual val MediaPause: Key
            get() = Key(-1000000072)
        actual val MediaPlayPause: Key
            get() = Key(-1000000073)
        actual val MediaStop: Key
            get() = Key(-1000000074)
        actual val MediaRecord: Key
            get() = Key(-1000000075)
        actual val MediaNext: Key
            get() = Key(-1000000076)
        actual val MediaPrevious: Key
            get() = Key(-1000000077)
        actual val MediaRewind: Key
            get() = Key(-1000000078)
        actual val MediaFastForward: Key
            get() = Key(-1000000079)
        actual val MediaClose: Key
            get() = Key(-1000000080)
        actual val MediaAudioTrack: Key
            get() = Key(-1000000081)
        actual val MediaEject: Key
            get() = Key(-1000000082)
        actual val MediaTopMenu: Key
            get() = Key(-1000000083)
        actual val MediaSkipForward: Key
            get() = Key(-1000000084)
        actual val MediaSkipBackward: Key
            get() = Key(-1000000085)
        actual val MediaStepForward: Key
            get() = Key(-1000000086)
        actual val MediaStepBackward: Key
            get() = Key(-1000000087)
        actual val MicrophoneMute: Key
            get() = Key(-1000000088)
        actual val VolumeMute: Key
            get() = Key(-1000000089)
        actual val Info: Key
            get() = Key(-1000000090)
        actual val ChannelUp: Key
            get() = Key(-1000000091)
        actual val ChannelDown: Key
            get() = Key(-1000000092)
        actual val ZoomIn: Key
            get() = Key(-1000000093)
        actual val ZoomOut: Key
            get() = Key(-1000000094)
        actual val Tv: Key
            get() = Key(-1000000095)
        actual val Window: Key
            get() = Key(-1000000096)
        actual val Guide: Key
            get() = Key(-1000000097)
        actual val Dvr: Key
            get() = Key(-1000000098)
        actual val Bookmark: Key
            get() = Key(-1000000099)
        actual val Captions: Key
            get() = Key(-1000000100)
        actual val Settings: Key
            get() = Key(-1000000101)
        actual val TvPower: Key
            get() = Key(-1000000102)
        actual val TvInput: Key
            get() = Key(-1000000103)
        actual val SetTopBoxPower: Key
            get() = Key(-1000000104)
        actual val SetTopBoxInput: Key
            get() = Key(-1000000105)
        actual val AvReceiverPower: Key
            get() = Key(-1000000106)
        actual val AvReceiverInput: Key
            get() = Key(-1000000107)
        actual val ProgramRed: Key
            get() = Key(-1000000108)
        actual val ProgramGreen: Key
            get() = Key(-1000000109)
        actual val ProgramYellow: Key
            get() = Key(-1000000110)
        actual val ProgramBlue: Key
            get() = Key(-1000000111)
        actual val AppSwitch: Key
            get() = Key(-1000000112)
        actual val LanguageSwitch: Key
            get() = Key(-1000000113)
        actual val MannerMode: Key
            get() = Key(-1000000114)
        actual val Toggle2D3D: Key
            get() = Key(-1000000125)
        actual val Contacts: Key
            get() = Key(-1000000126)
        actual val Calendar: Key
            get() = Key(-1000000127)
        actual val Music: Key
            get() = Key(-1000000128)
        actual val Calculator: Key
            get() = Key(-1000000129)
        actual val ZenkakuHankaru: Key
            get() = Key(-1000000130)
        actual val Eisu: Key
            get() = Key(-1000000131)
        actual val Muhenkan: Key
            get() = Key(-1000000132)
        actual val Henkan: Key
            get() = Key(-1000000133)
        actual val KatakanaHiragana: Key
            get() = Key(-1000000134)
        actual val Yen: Key
            get() = Key(-1000000135)
        actual val Ro: Key
            get() = Key(-1000000136)
        actual val Kana: Key
            get() = Key(-1000000137)
        actual val Assist: Key
            get() = Key(-1000000138)
        actual val BrightnessDown: Key
            get() = Key(-1000000139)
        actual val BrightnessUp: Key
            get() = Key(-1000000140)
        actual val Sleep: Key
            get() = Key(-1000000141)
        actual val WakeUp: Key
            get() = Key(-1000000142)
        actual val SoftSleep: Key
            get() = Key(-1000000143)
        actual val Pairing: Key
            get() = Key(-1000000144)
        actual val LastChannel: Key
            get() = Key(-1000000145)
        actual val TvDataService: Key
            get() = Key(-1000000146)
        actual val VoiceAssist: Key
            get() = Key(-1000000147)
        actual val TvRadioService: Key
            get() = Key(-1000000148)
        actual val TvTeletext: Key
            get() = Key(-1000000149)
        actual val TvNumberEntry: Key
            get() = Key(-1000000150)
        actual val TvTerrestrialAnalog: Key
            get() = Key(-1000000151)
        actual val TvTerrestrialDigital: Key
            get() = Key(-1000000152)
        actual val TvSatellite: Key
            get() = Key(-1000000153)
        actual val TvSatelliteBs: Key
            get() = Key(-1000000154)
        actual val TvSatelliteCs: Key
            get() = Key(-1000000155)
        actual val TvSatelliteService: Key
            get() = Key(-1000000156)
        actual val TvNetwork: Key
            get() = Key(-1000000157)
        actual val TvAntennaCable: Key
            get() = Key(-1000000158)
        actual val TvInputHdmi1: Key
            get() = Key(-1000000159)
        actual val TvInputHdmi2: Key
            get() = Key(-1000000160)
        actual val TvInputHdmi3: Key
            get() = Key(-1000000161)
        actual val TvInputHdmi4: Key
            get() = Key(-1000000162)
        actual val TvInputComposite1: Key
            get() = Key(-1000000163)
        actual val TvInputComposite2: Key
            get() = Key(-1000000164)
        actual val TvInputComponent1: Key
            get() = Key(-1000000165)
        actual val TvInputComponent2: Key
            get() = Key(-1000000166)
        actual val TvInputVga1: Key
            get() = Key(-1000000167)
        actual val TvAudioDescription: Key
            get() = Key(-1000000168)
        actual val TvAudioDescriptionMixingVolumeUp: Key
            get() = Key(-1000000169)
        actual val TvAudioDescriptionMixingVolumeDown: Key
            get() = Key(-1000000170)
        actual val TvZoomMode: Key
            get() = Key(-1000000171)
        actual val TvContentsMenu: Key
            get() = Key(-1000000172)
        actual val TvMediaContextMenu: Key
            get() = Key(-1000000173)
        actual val TvTimerProgramming: Key
            get() = Key(-1000000174)
        actual val StemPrimary: Key
            get() = Key(-1000000175)
        actual val Stem1: Key
            get() = Key(-1000000176)
        actual val Stem2: Key
            get() = Key(-1000000177)
        actual val Stem3: Key
            get() = Key(-1000000178)
        actual val AllApps: Key
            get() = Key(-1000000179)
        actual val Refresh: Key
            get() = Key(-1000000180)
        actual val ThumbsUp: Key
            get() = Key(-1000000181)
        actual val ThumbsDown: Key
            get() = Key(-1000000182)
        actual val ProfileSwitch: Key
            get() = Key(-1000000183)
}

    actual override fun toString(): String {
        return "Key: ${KeyEvent.getKeyText(nativeKeyCode)}"
    }
}

/**
 * Creates instance of [Key].
 *
 * @param nativeKeyCode represents this key as defined in [java.awt.event.KeyEvent]
 * @param nativeKeyLocation represents the location of key as defined in [java.awt.event.KeyEvent]
 */
fun Key(nativeKeyCode: Int, nativeKeyLocation: Int = KEY_LOCATION_STANDARD): Key {
    // Only 3 bits are required for nativeKeyLocation.
    return Key(packInts(nativeKeyLocation, nativeKeyCode))
}

/**
 * The native keycode corresponding to this [Key].
 */
val Key.nativeKeyCode: Int
    get() = unpackInt2(keyCode)

/**
 * The native location corresponding to this [Key].
 */
val Key.nativeKeyLocation: Int
    get() = unpackInt1(keyCode)
