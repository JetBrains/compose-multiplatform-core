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

package androidx.compose.ui.test.utils

import androidx.compose.test.utils.endPress
import androidx.compose.test.utils.keyboardPressEventForCharacter
import androidx.compose.test.utils.pressesEventOfType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIKeyModifierFlags
import platform.UIKit.UIPressType
import platform.UIKit.UIPressesEvent
import platform.UIKit.UIWindow

@OptIn(ExperimentalForeignApi::class)
internal fun UIWindow.beginPress(pressType: UIPressType): UIPressesEvent {
    return UIPressesEvent.pressesEventOfType(pressType, inWindow = this)
        ?: error("UIPressesEvent unavailable on this runtime")
}

@OptIn(ExperimentalForeignApi::class)
internal fun UIWindow.beginKeyPress(
    char: Char,
    modifierFlags: UIKeyModifierFlags = 0,
): UIPressesEvent {
    return UIPressesEvent.keyboardPressEventForCharacter(
        char.toString(),
        modifierFlags = modifierFlags,
        inWindow = this,
    ) ?: error("Cannot synthesise a key press for '$char' — unsupported character.")
}

internal fun UIPressesEvent.release() = this.endPress()
