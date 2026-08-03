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

package androidx.compose.ui.desktop.headless

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class HeadlessKeyMappingTest {
    @Test
    fun mouseButtonsMatchMacOsConvention() {
        // The macOS convention (see MacOsKeyMappingTest.mouseButtonsMapZeroBased) maps 0-based
        // mouse buttons so BACK -> Button4 and FORWARD -> Button5. PointerButton.Back/Forward
        // (index 3/4) must follow the same convention through the headless conversion path.
        assertEquals(Key.Button1, PointerButton.Primary.toKey())
        assertEquals(Key.Button2, PointerButton.Secondary.toKey())
        assertEquals(Key.Button3, PointerButton.Tertiary.toKey())
        assertEquals(Key.Button4, PointerButton.Back.toKey())
        assertEquals(Key.Button5, PointerButton.Forward.toKey())
    }
}
