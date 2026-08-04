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

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.input.key.Key
import kotlin.test.assertEquals
import org.jetbrains.desktop.win32.VirtualKey
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * `VirtualKey.toKey()` is a pure mapping and the win32 `VirtualKey` companion constants are
 * public, so the mapped cases are directly testable. Only the unmapped case needs the value-class
 * `box-impl` reflection trick (private constructor, public static `box-impl(int)`) to build a raw
 * value no constant covers. `currentKeyboardModifiers()` reads native keyboard state and is not
 * unit-testable here.
 */
@Category(HeadlessTest::class)
class WindowsKeyMappingTest {

    @Test
    fun mapsDigitsLettersAndControls() {
        assertEquals(Key.Zero, VirtualKey.Number0.toKey())
        assertEquals(Key.Nine, VirtualKey.Number9.toKey())
        assertEquals(Key.A, VirtualKey.A.toKey())
        assertEquals(Key.Z, VirtualKey.Z.toKey())
        assertEquals(Key.Enter, VirtualKey.Enter.toKey())
        assertEquals(Key.Escape, VirtualKey.Escape.toKey())
        assertEquals(Key.PrintScreen, VirtualKey.Snapshot.toKey())
        assertEquals(Key.DirectionLeft, VirtualKey.Left.toKey())
        assertEquals(Key.F12, VirtualKey.F12.toKey())
    }

    @Test
    fun mapsOemKeysIncludingTheSemicolonAliases() {
        assertEquals(Key.Equals, VirtualKey.OemPlus.toKey())
        assertEquals(Key.Semicolon, VirtualKey.Oem1.toKey())
        assertEquals(Key.Semicolon, VirtualKey.OemSemicolon.toKey())
        assertEquals(Key.Backslash, VirtualKey.Oem5.toKey())
        assertEquals(Key.Grave, VirtualKey.Oem3.toKey())
    }

    @Test
    fun mapsNumpadAndLockKeys() {
        assertEquals(Key.NumPad0, VirtualKey.NumberPad0.toKey())
        assertEquals(Key.NumPadDot, VirtualKey.Decimal.toKey())
        assertEquals(Key.NumPadComma, VirtualKey.Separator.toKey())
        assertEquals(Key.NumLock, VirtualKey.NumberKeyLock.toKey())
    }

    @Test
    fun collapsesModifierVariantsToTheLeftKeys() {
        assertEquals(Key.AltLeft, VirtualKey.Menu.toKey())
        assertEquals(Key.AltLeft, VirtualKey.RightMenu.toKey())
        assertEquals(Key.MetaLeft, VirtualKey.LeftWindows.toKey())
        assertEquals(Key.MetaLeft, VirtualKey.RightWindows.toKey())
        assertEquals(Key.CtrlLeft, VirtualKey.RightControl.toKey())
        assertEquals(Key.ShiftLeft, VirtualKey.RightShift.toKey())
    }

    @Test
    fun unmappedVirtualKeyIsUnknown() {
        // 0x07 is undefined in the VK_* table and mapped by no VirtualKey constant.
        val boxImpl = VirtualKey::class.java.getMethod("box-impl", Int::class.javaPrimitiveType)
        val unmapped = boxImpl.invoke(null, 0x07) as VirtualKey
        assertEquals(Key.Unknown, unmapped.toKey())
    }
}
