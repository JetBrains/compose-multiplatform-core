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

import androidx.compose.foundation.text.input.internal.TransformedTextFieldState
import androidx.compose.foundation.text.selection.DefaultTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionManager
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.TextInputContainer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.objc.OBJC_ASSOCIATION_RETAIN
import platform.objc.objc_getAssociatedObject
import platform.objc.objc_setAssociatedObject

@OptIn(InternalComposeUiApi::class)
internal var LegacyTextFieldState.holder: TextInputContainer.Holder?
    get() = textInputHolder
    set(value) {
        textInputHolder = value
    }

@OptIn(InternalComposeUiApi::class)
internal var TransformedTextFieldState.holder: TextInputContainer.Holder?
    get() = textInputHolder
    set(value) {
        textInputHolder = value
    }

@OptIn(InternalComposeUiApi::class)
internal var SelectionManager.holder: TextInputContainer.Holder?
    get() = textInputHolder
    set(value) {
        textInputHolder = value
    }

@OptIn(ExperimentalForeignApi::class)
private val TextInputHolderAssociationKey: COpaquePointer = nativeHeap.alloc<IntVar>().ptr

/** Associated object storage, since the common text field states can't declare an iOS-only field. */
@OptIn(ExperimentalForeignApi::class, InternalComposeUiApi::class)
private var Any.textInputHolder: TextInputContainer.Holder?
    get() =
        objc_getAssociatedObject(this, TextInputHolderAssociationKey)
            as? TextInputContainer.Holder
    set(value) {
        objc_setAssociatedObject(
            this,
            TextInputHolderAssociationKey,
            value,
            OBJC_ASSOCIATION_RETAIN
        )
    }

@OptIn(InternalComposeUiApi::class)
internal val TextInputContainer.Holder?.isNativeTextInput: Boolean
    get() = this?.usingNativeTextInput() == true

internal val TextSelectionColors.nativeTintColor: Color?
    get() = handleColor.takeIf { it != DefaultTextSelectionColors.handleColor }
