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

import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCapsLockOn
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isNumLockOn
import androidx.compose.ui.input.pointer.isShiftPressed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val X_SHIFT_MASK = 1L shl 0
private const val X_LOCK_MASK = 1L shl 1
private const val X_CONTROL_MASK = 1L shl 2
private const val X_MOD1_MASK = 1L shl 3
private const val X_MOD2_MASK = 1L shl 4
private const val X_MOD4_MASK = 1L shl 6

class KeyEventLinuxTest {

    @Test
    fun lettersOfBothCasesMapToTheSameKey() {
        assertEquals(Key.A, keysymToKey[0x61L])
        assertEquals(Key.A, keysymToKey[0x41L])
        assertEquals(Key.Z, keysymToKey[0x7aL])
        assertEquals(Key.Z, keysymToKey[0x5aL])
    }

    @Test
    fun digitsPunctuationAndNavigationAreMapped() {
        assertEquals(Key.Zero, keysymToKey['0'.code.toLong()])
        assertEquals(Key.Nine, keysymToKey['9'.code.toLong()])
        assertEquals(Key.Semicolon, keysymToKey[0x3bL])
        assertEquals(Key.Grave, keysymToKey[0x60L])
        assertEquals(Key.MoveHome, keysymToKey[0xff50L])
        assertEquals(Key.MoveEnd, keysymToKey[0xff57L])
        assertEquals(Key.PageDown, keysymToKey[0xff56L])
        assertEquals(Key.Tab, keysymToKey[0xff09L])
    }

    @Test
    fun functionNumpadAndModifierKeysAreMapped() {
        assertEquals(Key.F1, keysymToKey[0xffbeL])
        assertEquals(Key.F12, keysymToKey[0xffc9L])
        assertEquals(Key.NumPad0, keysymToKey[0xffb0L])
        assertEquals(Key.NumPad9, keysymToKey[0xffb9L])
        assertEquals(Key.NumPadEnter, keysymToKey[0xff8dL])
        assertEquals(Key.ShiftLeft, keysymToKey[0xffe1L])
        assertEquals(Key.CtrlRight, keysymToKey[0xffe4L])
        assertEquals(Key.AltLeft, keysymToKey[0xffe9L])
        // Super keys are Compose's Meta
        assertEquals(Key.MetaLeft, keysymToKey[0xffebL])
        assertEquals(Key.MetaRight, keysymToKey[0xffecL])
    }

    @Test
    fun stateMaskTranslatesToModifiers() {
        val modifiers = xKeyboardModifiers(
            state = X_SHIFT_MASK or X_CONTROL_MASK or X_LOCK_MASK or X_MOD2_MASK,
        )
        assertTrue(modifiers.isShiftPressed)
        assertTrue(modifiers.isCtrlPressed)
        assertTrue(modifiers.isCapsLockOn)
        assertTrue(modifiers.isNumLockOn)
        assertFalse(modifiers.isAltPressed)
        assertFalse(modifiers.isMetaPressed)
    }

    @Test
    fun altAndMetaComeFromMod1AndMod4() {
        val modifiers = xKeyboardModifiers(state = X_MOD1_MASK or X_MOD4_MASK)
        assertTrue(modifiers.isAltPressed)
        assertTrue(modifiers.isMetaPressed)
        assertFalse(modifiers.isShiftPressed)
    }

    @Test
    fun modifierKeyPressOverridesStaleState() {
        // X11 reports the state before the event: pressing Shift carries no ShiftMask yet.
        val pressed = xKeyboardModifiers(state = 0L, key = Key.ShiftLeft, isKeyDown = true)
        assertTrue(pressed.isShiftPressed)
    }

    @Test
    fun modifierKeyReleaseOverridesStaleState() {
        // Releasing Shift still carries ShiftMask in the pre-event state.
        val released = xKeyboardModifiers(
            state = X_SHIFT_MASK or X_CONTROL_MASK,
            key = Key.ShiftRight,
            isKeyDown = false,
        )
        assertFalse(released.isShiftPressed)
        assertTrue(released.isCtrlPressed)
    }
}
