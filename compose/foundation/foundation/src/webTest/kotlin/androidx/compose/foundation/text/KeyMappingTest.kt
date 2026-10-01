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

package androidx.compose.foundation.text

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skiko.OS

@OptIn(InternalComposeUiApi::class)
class KeyMappingTest {
    @Test
    fun ctrlEndMapsToEnd() {
        // Use the browser key code directly so a broken Key.MoveEnd accessor cannot mask the bug.
        val event = KeyEvent(key = Key(35), type = KeyEventType.KeyDown, isCtrlPressed = true)

        assertEquals(KeyCommand.END, createPlatformDefaultKeyMapping(OS.Linux).map(event))
    }
}
