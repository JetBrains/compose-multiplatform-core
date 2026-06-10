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

package androidx.compose.ui.desktop.linux

import androidx.compose.runtime.NoriaOnly
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.SessionMutex
import androidx.compose.ui.desktop.LinuxTextInputContext
import androidx.compose.ui.desktop.LinuxTextInputSurroundingText
import androidx.compose.ui.desktop.PlatformTextInputSessionLinux
import androidx.compose.ui.desktop.TextInputSessionOwner
import androidx.compose.ui.desktop.utf8OffsetToUtf16Offset
import androidx.compose.ui.input.key.InternalKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.text.TextRange
import org.jetbrains.desktop.linux.Event
import org.jetbrains.desktop.linux.KeyCode
import org.jetbrains.desktop.linux.TextInputCommitStringData
import org.jetbrains.desktop.linux.TextInputDeleteSurroundingTextData
import org.jetbrains.desktop.linux.TextInputPreeditStringData

/**
 * Linux (Wayland) text input session owner. IME is driven by the native text-input protocol: the
 * editor's [PlatformTextInputMethodRequestLinux] supplies the [LinuxTextInputContext] and
 * surrounding text, and incoming [Event.TextInput] (preedit/commit/delete-surrounding) is forwarded
 * back to it. The [startInputMethod]/[stopInputMethod]/[onDataChanged] callbacks bridge to the
 * native seat (see [LinuxApplication]).
 */
@OptIn(InternalComposeUiApi::class)
class LinuxTextInputSessionOwner(
    private val startInputMethod: (LinuxTextInputContext, LinuxTextInputSurroundingText) -> Unit,
    private val stopInputMethod: () -> Unit,
    private val onDataChanged: (LinuxTextInputContext, LinuxTextInputSurroundingText) -> Unit,
) : TextInputSessionOwner {
    private val textInputSessionMutex = SessionMutex<PlatformTextInputSessionLinux>()

    private var currentLinuxTextInputSurroundingText: LinuxTextInputSurroundingText? = null

    internal var hasPreeditString: Boolean = false
        private set

    override suspend fun textInputSession(
        session: suspend PlatformTextInputSessionScope<*>.() -> Nothing,
    ): Nothing {
        textInputSessionMutex.withSessionCancellingPrevious(
            sessionInitializer = { coroutineScope ->
                PlatformTextInputSessionLinux(
                    coroutineScope = coroutineScope,
                    startInputMethod = { context, surroundingText ->
                        hasPreeditString = false
                        currentLinuxTextInputSurroundingText = surroundingText
                        startInputMethod(context, surroundingText)
                    },
                    stopInputMethod = {
                        hasPreeditString = false
                        currentLinuxTextInputSurroundingText = null
                        stopInputMethod()
                    },
                    onDataChanged = { context, surroundingText ->
                        currentLinuxTextInputSurroundingText = surroundingText
                        onDataChanged(context, surroundingText)
                    },
                )
            },
            session,
        )
    }

    @NoriaOnly
    override fun isTextInputSessionActive(): Boolean {
        return textInputSessionMutex.currentSession?.currentRequest != null
    }

    @NoriaOnly
    override fun handleEventWithInputSession(keyEvent: KeyEvent): Boolean {
        val nativeEvent =
            (keyEvent.nativeKeyEvent as? InternalKeyEvent)?.nativeEvent as? Event.KeyDown
                ?: return false
        val characters = nativeEvent.characters?.takeIf { it.isNotEmpty() }
        val session = textInputSessionMutex.currentSession ?: return false
        return if (
            keyEvent.type == KeyEventType.KeyDown &&
            // Some keys have characters that we don't want to type
            // e.g. Esc contains a string with 0x1B codepoint
            nativeEvent.keyCode.value != KeyCode.Escape &&
            characters != null &&
            !keyEvent.isMetaPressed &&
            (!keyEvent.isCtrlPressed && !keyEvent.isAltPressed ||
                keyEvent.isAltPressed && keyEvent.isCtrlPressed)
        ) {
            session.commitText(characters)
            true
        } else {
            false
        }
    }

    internal fun handleTextInputEvent(
        preeditStringData: TextInputPreeditStringData?,
        commitStringData: TextInputCommitStringData?,
        deleteSurroundingTextData: TextInputDeleteSurroundingTextData?,
    ) {
        textInputSessionMutex.currentSession?.currentRequest?.let { currentRequest ->
            val preeditString = preeditStringData?.text
            hasPreeditString = preeditString != null
            val cursorRangeInComposedText = preeditString?.let { preeditString ->
                if (preeditStringData.cursorBeginBytePos == -1 || preeditStringData.cursorEndBytePos == -1) {
                    null
                } else {
                    val start = utf8OffsetToUtf16Offset(preeditString, preeditStringData.cursorBeginBytePos.toUInt())
                    val end = preeditStringData.cursorEndBytePos.let {
                        if (it == preeditStringData.cursorBeginBytePos) start
                        else utf8OffsetToUtf16Offset(preeditString, it.toUInt())
                    }
                    TextRange(start = start.toInt(), end = end.toInt())
                }
            }
            val deleteSurroundingText = deleteSurroundingTextData?.let {
                currentLinuxTextInputSurroundingText?.convertByteOffsetsFromCursor(
                    it.beforeLengthInBytes,
                    it.afterLengthInBytes,
                )
            }
            currentRequest.handleTextChangedEvent(
                committedText = commitStringData?.text,
                composedText = preeditStringData?.text,
                caretRangeInComposedText = cursorRangeInComposedText,
                deleteSurroundingText = deleteSurroundingText,
            )
        }
    }
}
