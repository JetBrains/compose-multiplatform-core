/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.kdt

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

@OptIn(ExperimentalComposeUiApi::class)
internal class ComposeTextInputSession(
    private val request: PlatformTextInputMethodRequest,
    private val scene: Scene<*>,
) {
    val value: TextFieldValue
        get() = request.value()

    val focusedRectInRoot: Rect?
        get() = request.focusedRectInRoot()

    val isSingleLine: Boolean
        get() = request.imeOptions.singleLine

    val keyboardType: KeyboardType
        get() = request.imeOptions.keyboardType

    fun commitText(text: String) {
        scene.withPreparedMainThread {
            request.editText {
                commitText(text, 1)
            }
        }
    }

    fun setComposingText(
        text: String,
        selection: TextRange? = null,
    ) {
        scene.withPreparedMainThread {
            request.editText {
                val replacementRange = request.state.composition ?: request.state.selection
                setComposition(replacementRange)
                setComposingText(text, 1)
                if (selection != null) {
                    setSelection(selection)
                }
            }
        }
    }

    fun finishComposingText() {
        scene.withPreparedMainThread {
            request.editText {
                finishComposingText()
            }
        }
    }
}
