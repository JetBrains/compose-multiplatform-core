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

package androidx.compose.ui.desktop.linux

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.desktop.gtk.toKey as gtkToKey
import androidx.compose.ui.input.key.Key
import kotlin.test.assertEquals
import org.jetbrains.desktop.gtk.KeySym as GtkKeySym
import org.jetbrains.desktop.linux.KeySym as LinuxKeySym
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Covers the punctuation mappings shared (modulo KDT package) by both
 * `androidx.compose.ui.desktop.linux.Key.kt` (Wayland) and `androidx.compose.ui.desktop.gtk.Key.kt`
 * (X11) via each package's own `KeySym.toKey()`.
 *
 * KDT's `KeySym.<name>` companion constants (e.g. `KeySym.quotedbl`) are plain `UInt` values, not
 * `KeySym` instances, and `KeySym`'s constructor is internal to the KDT module, so there is no
 * public way to build a `KeySym` from a raw code. We box the raw value through the compiler
 * generated `box-impl` static (the standard JVM ABI for a `value class`) via reflection instead of
 * touching KDT's own visibility.
 */
@Category(HeadlessTest::class)
class LinuxKeyMappingTest {
    private fun linuxKeySym(rawValue: UInt): LinuxKeySym {
        val boxImpl = LinuxKeySym::class.java.getMethod("box-impl", Int::class.javaPrimitiveType)
        return boxImpl.invoke(null, rawValue.toInt()) as LinuxKeySym
    }

    private fun gtkKeySym(rawValue: UInt): GtkKeySym {
        val boxImpl = GtkKeySym::class.java.getMethod("box-impl", Int::class.javaPrimitiveType)
        return boxImpl.invoke(null, rawValue.toInt()) as GtkKeySym
    }

    @Test
    fun linuxPunctuationSymbolsMapToVendoredKeys() {
        assertEquals(Key.QuotationMark, linuxKeySym(LinuxKeySym.quotedbl).toKey())
        assertEquals(Key.Colon, linuxKeySym(LinuxKeySym.colon).toKey())
        assertEquals(Key.LeftBrace, linuxKeySym(LinuxKeySym.braceleft).toKey())
        assertEquals(Key.DollarSign, linuxKeySym(LinuxKeySym.dollar).toKey())
        assertEquals(Key.LeftParenthesis, linuxKeySym(LinuxKeySym.parenleft).toKey())
        assertEquals(Key.VerticalLine, linuxKeySym(LinuxKeySym.bar).toKey())

        // Controls: F13 has no vendored constant in this fork and must stay Unknown;
        // `at` was already live before this fix and must not regress.
        assertEquals(Key.Unknown, linuxKeySym(LinuxKeySym.F13).toKey())
        assertEquals(Key.At, linuxKeySym(LinuxKeySym.at).toKey())
    }

    @Test
    fun gtkPunctuationSymbolsMapToVendoredKeys() {
        assertEquals(Key.QuotationMark, gtkKeySym(GtkKeySym.quotedbl).gtkToKey())
        assertEquals(Key.Colon, gtkKeySym(GtkKeySym.colon).gtkToKey())
        assertEquals(Key.LeftBrace, gtkKeySym(GtkKeySym.braceleft).gtkToKey())
        assertEquals(Key.DollarSign, gtkKeySym(GtkKeySym.dollar).gtkToKey())
        assertEquals(Key.LeftParenthesis, gtkKeySym(GtkKeySym.parenleft).gtkToKey())
        assertEquals(Key.VerticalLine, gtkKeySym(GtkKeySym.bar).gtkToKey())

        // Controls: F13 has no vendored constant in this fork and must stay Unknown;
        // `at` was already live before this fix and must not regress.
        assertEquals(Key.Unknown, gtkKeySym(GtkKeySym.F13).gtkToKey())
        assertEquals(Key.At, gtkKeySym(GtkKeySym.at).gtkToKey())
    }
}
