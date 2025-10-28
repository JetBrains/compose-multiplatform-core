/*
 * Copyright 2021 The Android Open Source Project
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

import androidx.compose.foundation.InternalFoundationApi
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.awt.kdeEventOrNull
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

private fun Char.isPrintable(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return (!Character.isISOControl(this)) &&
        this != java.awt.event.KeyEvent.CHAR_UNDEFINED &&
        block != null &&
        block != Character.UnicodeBlock.SPECIALS
}

// This API was never supposed to be public, but currently there are some external usages of it,
// so it cannot be removed from the public right now.
// However, starting with 1.9 it's marked as NOT a public-stable API with compatibility guarantees.
@InternalFoundationApi
actual val KeyEvent.isTypedEvent: Boolean
    get() {
        return when {
            awtEventOrNull != null -> {
                awtEventOrNull?.id == java.awt.event.KeyEvent.KEY_TYPED &&
                    awtEventOrNull?.keyChar?.let { it.isPrintable() || it.isWhitespace() } == true
            }

            this.kdeEventOrNull != null -> {
                type == KeyEventType.KeyDown &&
                    !isISOControl(utf16CodePoint) &&
                    !isAppKitReserved(utf16CodePoint) &&
                    !isMetaPressed &&
                    !isCtrlPressed
            }

            else -> false
        }
    }


private fun isISOControl(codePoint: Int): Boolean =
    codePoint in 0x00..0x1F ||
        codePoint in 0x7F..0x9F

// https://www.unicode.org/Public/MAPPINGS/VENDORS/APPLE/CORPCHAR.TXT
private fun isAppKitReserved(codePoint: Int): Boolean =
    codePoint in 0xF700..0xF8FF
