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

package androidx.compose.ui.desktop.macos

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.input.key.Key
import org.jetbrains.desktop.macos.MouseButton
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class MacOsKeyMappingTest {
    @Test
    fun mouseButtonsMapZeroBased() {
        // KDT MouseButton values are 0-based (LEFT=0 … FORWARD=4); the AIR-6023 bug mapped them 1-based.
        assertEquals(Key.Button1, MouseButton.LEFT.toKey())
        assertEquals(Key.Button2, MouseButton.RIGHT.toKey())
        assertEquals(Key.Button3, MouseButton.MIDDLE.toKey())
        assertEquals(Key.Button4, MouseButton.BACK.toKey())
        assertEquals(Key.Button5, MouseButton.FORWARD.toKey())
    }

    @Test
    fun internationalSymbolsResolveToVendoredKeys() {
        assertEquals(Key.SectionSign, keyFromInternationalSymbols("§"))
        assertEquals(Key.SharpS, keyFromInternationalSymbols("ß"))
        assertEquals(Key.AUmlaut, keyFromInternationalSymbols("ä"))
        assertEquals(Key.QuotationMark, keyFromInternationalSymbols("\""))
        assertEquals(Key.VerticalLine, keyFromInternationalSymbols("|"))
        assertEquals(Key.At, keyFromInternationalSymbols("@")) // was already live
        assertEquals(null, keyFromInternationalSymbols("x"))
    }
}
