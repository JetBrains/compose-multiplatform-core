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

import androidx.compose.ui.HeadlessTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class VendoredKeyConstantsTest {
    // The full 101-name list from the Noria fork (extraction dossier §forked-key-constants).
    private val vendored: List<Key> by lazy {
        listOf(
            Key.AAcute, Key.ABreve, Key.ACircumflex, Key.Ae, Key.AGrave, Key.AOgonek, Key.ARing,
            Key.AUmlaut, Key.CAcute, Key.CCaron, Key.CCedilla, Key.DStroke, Key.EAcute, Key.ECaron,
            Key.ECircumflex, Key.EDiaeresis, Key.EDot, Key.EGrave, Key.Eng, Key.EOgonek, Key.Eth,
            Key.GBreve, Key.GDot, Key.HStroke, Key.IAcute, Key.ICircumflex, Key.IDotless, Key.IGrave,
            Key.IOgonek, Key.LCaron, Key.LStroke, Key.NCaron, Key.NTilde, Key.OAcute, Key.OCircumflex,
            Key.ODoubleAcute, Key.OGrave, Key.OHorn, Key.OUmlaut, Key.OStroke, Key.OTilde, Key.RCaron,
            Key.SCaron, Key.SCedilla, Key.Schwa, Key.SComma, Key.SharpS, Key.TCaron, Key.TComma,
            Key.Thorn, Key.TStroke, Key.UAcute, Key.UCircumflex, Key.UDoubleAcute, Key.UGrave,
            Key.UHorn, Key.UMacron, Key.UOgonek, Key.URing, Key.UUmlaut, Key.YAcute, Key.ZCaron,
            Key.ZDot, Key.AcuteAccent, Key.Ampersand, Key.Asterisk, Key.Breve, Key.Caron, Key.Cedilla,
            Key.CircumflexAccent, Key.Colon, Key.Diaeresis, Key.DollarSign, Key.DongSign, Key.DotAbove,
            Key.DotBelow, Key.DoubleAcuteAccent, Key.ExclamationMark, Key.GreaterSign, Key.HookAbove,
            Key.InvertedExclamationMark, Key.InvertedQuestionMark, Key.LeftBrace, Key.LeftParenthesis,
            Key.LessSign, Key.LowQuotationMark, Key.Macron, Key.MasculineOrdinalIndicator,
            Key.NumberSign, Key.Ogonek, Key.PoundSign, Key.QuotationMark, Key.Rafe, Key.RightBrace,
            Key.RightParenthesis, Key.RingAbove, Key.SectionSign, Key.SuperscriptTwo, Key.Tilde,
            Key.Underscore, Key.VerticalLine,
        )
    }

    @Test
    fun allVendoredConstantsAreDistinctSyntheticNonAwtCodes() {
        assertEquals(101, vendored.size)
        assertEquals(101, vendored.map { it.keyCode }.distinct().size, "keycodes must be unique")
        assertTrue(vendored.all { it.keyCode < 0 }, "vendored keys use the synthetic negative range")
        assertTrue(vendored.none { it == Key.Unknown })
    }
}
