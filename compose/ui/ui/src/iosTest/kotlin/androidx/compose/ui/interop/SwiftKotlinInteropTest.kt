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

package androidx.compose.ui.interop

import androidx.compose.ui.uikit.utils.CMPUIKitSwiftInteropBox
import androidx.compose.ui.uikit.utils.CMPUIKitSwiftInteropProtocol
import kotlin.test.Test
import kotlin.test.assertEquals

class SwiftKotlinInteropTest {
    @Test
    fun swiftImplementationIsAvailableThroughObjcInterop() {
        val box = CMPUIKitSwiftInteropBox(seed = 7)
        val interop: CMPUIKitSwiftInteropProtocol = box

        assertEquals(7L, box.seed())
        assertEquals(7L, interop.seed())
        assertEquals("swift-7-kotlin", box.combinedValueWithSuffix("kotlin"))
        assertEquals("swift-7-interop", interop.combinedValueWithSuffix("interop"))
    }
}
