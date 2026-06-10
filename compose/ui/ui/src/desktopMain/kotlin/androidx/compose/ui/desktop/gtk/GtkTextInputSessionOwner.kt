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

package androidx.compose.ui.desktop.gtk

import androidx.compose.runtime.NoriaOnly
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.SessionMutex
import androidx.compose.ui.desktop.LinuxTextInputContext
import androidx.compose.ui.desktop.LinuxTextInputSurroundingText
import androidx.compose.ui.desktop.PlatformTextInputSessionLinux
import androidx.compose.ui.desktop.TextInputSessionOwner
import androidx.compose.ui.desktop.codepointFromOffset
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.desktop.gtk.Event
import org.jetbrains.desktop.gtk.KeyCode
import org.jetbrains.desktop.gtk.TextInputCommitStringData
import org.jetbrains.desktop.gtk.TextInputContentPurpose
import org.jetbrains.desktop.gtk.TextInputContext
import org.jetbrains.desktop.gtk.TextInputContextHint
import org.jetbrains.desktop.gtk.TextInputDeleteSurroundingTextData
import org.jetbrains.desktop.gtk.TextInputPreeditStringData
import org.jetbrains.desktop.gtk.TextInputSurroundingText

/**
 * GTK text input session owner. Mirrors [androidx.compose.ui.desktop.linux.LinuxTextInputSessionOwner]
 * but adapts the editor-supplied [LinuxTextInputContext]/surrounding text to GTK's native
 * [TextInputContext]/[TextInputSurroundingText], and drives IME at the window level (see [GtkWindow]).
 */
@OptIn(InternalComposeUiApi::class)
class GtkTextInputSessionOwner(
    private val startInputMethod: (TextInputContext) -> Unit,
    private val stopInputMethod: () -> Unit,
    private val onDataChanged: (TextInputContext) -> Unit,
) : TextInputSessionOwner {
    private val textInputSessionMutex = SessionMutex<PlatformTextInputSessionLinux>()

    internal var currentContext: TextInputContext? = null
        private set

    private var currentLinuxTextInputSurroundingText: LinuxTextInputSurroundingText? = null

    internal var currentTextInputSurroundingText: TextInputSurroundingText? = null
        private set

    internal var hasPreeditString: Boolean = false
        private set

    override suspend fun textInputSession(
        session: suspend PlatformTextInputSessionScope<*>.() -> Nothing,
    ): Nothing {
        textInputSessionMutex.withSessionCancellingPrevious(
            sessionInitializer = {
                PlatformTextInputSessionLinux(
                    coroutineScope = it,
                    startInputMethod = { context, surroundingText ->
                        val gtkContext = createGtkTextInputContext(context)
                        hasPreeditString = false
                        currentContext = gtkContext
                        currentLinuxTextInputSurroundingText = surroundingText
                        currentTextInputSurroundingText = createGtkTextInputSurroundingText(surroundingText)
                        startInputMethod(gtkContext)
                    },
                    stopInputMethod = {
                        hasPreeditString = false
                        currentContext = null
                        currentLinuxTextInputSurroundingText = null
                        currentTextInputSurroundingText = null
                        stopInputMethod()
                    },
                    onDataChanged = { context, surroundingText ->
                        val gtkContext = createGtkTextInputContext(context)
                        currentContext = gtkContext
                        currentLinuxTextInputSurroundingText = surroundingText
                        currentTextInputSurroundingText = createGtkTextInputSurroundingText(surroundingText)
                        onDataChanged(gtkContext)
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

    fun handleTextInputEvent(
        preeditStringData: TextInputPreeditStringData?,
        commitStringData: TextInputCommitStringData?,
        deleteSurroundingTextData: TextInputDeleteSurroundingTextData?,
    ) {
        textInputSessionMutex.currentSession?.currentRequest?.let { currentRequest ->
            val preeditString = preeditStringData?.text
            hasPreeditString = preeditString != null
            val caretOffsetInComposedText = preeditStringData?.text?.let { preeditString ->
                if (preeditStringData.cursorBytePos == -1) {
                    null
                } else {
                    utf8OffsetToUtf16Offset(preeditString, preeditStringData.cursorBytePos.toUInt())
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
                caretRangeInComposedText = caretOffsetInComposedText?.let { TextRange(it.toInt(), it.toInt()) },
                deleteSurroundingText = deleteSurroundingText,
            )
        }
    }
}

private fun createGtkTextInputContext(context: LinuxTextInputContext): TextInputContext {
    val imeOptions = context.imeOptions
    return TextInputContext(
        hints = buildSet {
            if (imeOptions.autoCorrect) add(TextInputContextHint.Spellcheck)
            when (imeOptions.capitalization) {
                KeyboardCapitalization.None -> add(TextInputContextHint.Lowercase)
                KeyboardCapitalization.Characters -> add(TextInputContextHint.UppercaseChars)
                KeyboardCapitalization.Words -> add(TextInputContextHint.UppercaseWords)
                KeyboardCapitalization.Sentences -> add(TextInputContextHint.UppercaseSentences)
                else -> {}
            }
        },
        contentPurpose = imeOptions.keyboardType.toTextInputContentPurpose(),
        cursorRectangle = context.cursorRectangle.toLogicalRect(),
    )
}

private fun createGtkTextInputSurroundingText(
    surroundingText: LinuxTextInputSurroundingText,
): TextInputSurroundingText {
    val text = surroundingText.text
    return TextInputSurroundingText(
        surroundingText = text,
        cursorCodepointOffset = codepointFromOffset(text, surroundingText.cursorOffset),
        selectionStartCodepointOffset = codepointFromOffset(text, surroundingText.selectionStartOffset),
    )
}

@Suppress("DuplicatedCode")
private fun KeyboardType.toTextInputContentPurpose(): TextInputContentPurpose {
    return when (this) {
        KeyboardType.Unspecified,
        KeyboardType.Text,
        KeyboardType.Ascii,
            -> TextInputContentPurpose.Normal
        KeyboardType.Uri -> TextInputContentPurpose.Url
        KeyboardType.Email -> TextInputContentPurpose.Email
        KeyboardType.Number -> TextInputContentPurpose.Digits
        KeyboardType.Decimal -> TextInputContentPurpose.Number
        KeyboardType.Phone -> TextInputContentPurpose.Phone
        KeyboardType.Password -> TextInputContentPurpose.Password
        KeyboardType.NumberPassword -> TextInputContentPurpose.Pin
        else -> TextInputContentPurpose.Normal
    }
}
