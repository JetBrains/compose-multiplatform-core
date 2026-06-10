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

package androidx.compose.ui.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.desktop.macos.PlatformTextInputSessionMacOs
import androidx.compose.ui.desktop.macos.toMacOsRequest
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation

/**
 * Starts a desktop platform text input method for Compose's own `BasicTextField`, which produces the
 * generic [PlatformTextInputMethodRequest]. The request is adapted to the platform-specific request
 * that the active session ([PlatformTextInputSessionMacOs] / [PlatformTextInputSessionLinux])
 * expects. Linux and GTK share [PlatformTextInputSessionLinux].
 *
 * Called from `foundation`'s desktop `platformSpecificTextInputSession`. External editors (e.g.
 * fleet) bypass this and call `startInputMethod` with their own platform request directly.
 */
@InternalComposeUiApi
@OptIn(ExperimentalComposeUiApi::class)
suspend fun PlatformTextInputSession<*>.startComposeTextInputMethod(
    request: PlatformTextInputMethodRequest,
): Nothing {
    when (this) {
        is PlatformTextInputSessionMacOs -> startInputMethod(request.toMacOsRequest(density))
        is PlatformTextInputSessionLinux -> startInputMethod(request.toLinuxRequest())
        else -> error("Unexpected desktop text input session: $this")
    }
}

/**
 * Minimal adapter from Compose's generic [PlatformTextInputMethodRequest] to the Linux/GTK
 * [PlatformTextInputMethodRequestLinux] used by [PlatformTextInputSessionLinux]. It applies IME
 * commit/compose directly to the text field via [PlatformTextInputMethodRequest.editText].
 *
 * It does not push live editor-state updates back to the input method (surrounding-text
 * synchronization / composed-caret placement); full fidelity for Compose's own `BasicTextField` on
 * Linux/GTK is a follow-up. External editors (fleet) supply their own richer implementation and do
 * not go through this adapter.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun PlatformTextInputMethodRequest.toLinuxRequest(): PlatformTextInputMethodRequestLinux {
    val request = this
    return object : PlatformTextInputMethodRequestLinux {
        override fun commitText(text: String) {
            request.editText { commitText(text, 1) }
        }

        override fun handleTextChangedEvent(
            committedText: String?,
            composedText: String?,
            caretRangeInComposedText: TextRange?,
            deleteSurroundingText: Pair<UInt, UInt>?,
        ) {
            request.editText {
                deleteSurroundingText?.let { (before, after) ->
                    deleteSurroundingTextInCodePoints(before.toInt(), after.toInt())
                }
                if (committedText != null) {
                    commitText(committedText, 1)
                }
                if (composedText != null) {
                    setComposingText(composedText, 1)
                } else if (committedText == null) {
                    finishComposingText()
                }
            }
        }

        override fun initialData(): Pair<LinuxTextInputContext, LinuxTextInputSurroundingText> {
            val state = request.state
            val text = state.toString()
            val context = LinuxTextInputContext(
                imeOptions = request.imeOptions,
                cursorRectangle = DpRect(0.dp, 0.dp, 0.dp, 0.dp),
            )
            val surroundingText = LinuxTextInputSurroundingText(
                text = text,
                cursorOffset = state.selection.end.toUInt(),
                selectionStartOffset = state.selection.start.toUInt(),
            )
            return context to surroundingText
        }

        override suspend fun waitForEditorStateChange():
            Pair<LinuxTextInputContext, LinuxTextInputSurroundingText> = awaitCancellation()
    }
}
