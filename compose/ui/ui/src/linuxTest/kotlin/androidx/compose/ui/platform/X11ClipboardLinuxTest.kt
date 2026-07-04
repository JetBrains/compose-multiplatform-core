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

package androidx.compose.ui.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class X11ClipboardLinuxTest {

    @Test
    fun latin1RoundTripsAsciiText() {
        val text = "Hello, X11 clipboard! 123"
        assertEquals(text, text.encodeLatin1().decodeLatin1())
    }

    @Test
    fun latin1RoundTripsHighBytes() {
        val text = "café ü Ø"
        assertEquals(text, text.encodeLatin1().decodeLatin1())
    }

    @Test
    fun decodeLatin1TreatsBytesAsUnsigned() {
        assertEquals("é", byteArrayOf(0xE9.toByte()).decodeLatin1())
    }

    @Test
    fun encodeLatin1ReplacesUnrepresentableCodePointsWithQuestionMark() {
        assertEquals("?? ok", "€世 ok".encodeLatin1().decodeLatin1())
    }

    @Test
    fun encodeLatin1OfEmptyStringIsEmpty() {
        assertContentEquals(ByteArray(0), "".encodeLatin1())
    }
}
