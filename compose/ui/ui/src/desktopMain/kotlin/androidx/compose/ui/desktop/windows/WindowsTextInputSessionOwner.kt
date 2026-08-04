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

@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.SessionMutex
import androidx.compose.ui.desktop.NativePlatformTextInputMethodRequest
import androidx.compose.ui.desktop.TextInputSessionOwner
import androidx.compose.ui.input.key.InternalKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.withFrameTransaction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.desktop.win32.Event as Win32Event
import org.jetbrains.desktop.win32.LogicalPoint as Win32LogicalPoint
import org.jetbrains.desktop.win32.LogicalRect as Win32LogicalRect
import org.jetbrains.desktop.win32.LogicalSize as Win32LogicalSize
import org.jetbrains.desktop.win32.TextCompositionSegment as Win32TextCompositionSegment
import org.jetbrains.desktop.win32.TextInputClient as Win32TextInputClient
import org.jetbrains.desktop.win32.TextRange as Win32TextRange
import org.jetbrains.desktop.win32.Window as Win32Window

/**
 * The win32 (IMM32) text-input request surface. Mirrors the win32 `TextInputClient` protocol the way
 * macOS's [androidx.compose.ui.desktop.macos.PlatformTextInputMethodRequestMacOs] mirrors
 * NSTextInputClient. It extends [NativePlatformTextInputMethodRequest] so the generic Compose
 * text-field accessors default to the "unsupported" throwing stubs; only these platform-specific
 * members are implemented (by [toWindowsRequest] for Compose's own `BasicTextField`, or by an
 * external editor supplying its own richer implementation).
 *
 * [setMarkedText]'s `selectedRange` is nullable: the IME reports the caret position within the
 * composition only when it has one, and passes `null` otherwise.
 */
interface PlatformTextInputMethodRequestWindows : NativePlatformTextInputMethodRequest {
    fun selectedRange(): TextRange
    fun insertText(text: String, replacementRange: TextRange?)
    fun setMarkedText(text: String, selectedRange: TextRange?, replacementRange: TextRange?)
    fun unmarkText()
    fun discardMarkedText()

    fun boundingRectForCharacterRange(range: TextRange): DpRect
}

class PlatformTextInputSessionWindows(
    coroutineScope: CoroutineScope,
    private val nativeWindow: Win32Window,
    private val composeScene: ComposeScene,
    internal val density: () -> Density,
) : PlatformTextInputSessionScope<PlatformTextInputMethodRequestWindows>,
    CoroutineScope by coroutineScope {

    @Volatile
    internal var currentTextInputClient: Win32TextInputClient? = null

    override suspend fun startInputMethod(request: PlatformTextInputMethodRequestWindows): Nothing {
        withContext(ComposeUIDispatcher.immediate) {
            currentTextInputClient =
                request.toTextInputClient(composeScene, nativeWindow).also { textInputClient ->
                    nativeWindow.setTextInputClient(textInputClient)
                }
        }
        try {
            awaitCancellation()
        } finally {
            withContext(ComposeUIDispatcher.immediate + NonCancellable) {
                currentTextInputClient?.unmarkText()
                nativeWindow.clearTextInputClient()
                currentTextInputClient = null
            }
        }
    }
}

class WindowsTextInputSessionOwner(
    private val nativeWindow: Win32Window,
    private val composeScene: ComposeScene,
    private val density: () -> Density,
) : TextInputSessionOwner {
    private val textInputSessionMutex = SessionMutex<PlatformTextInputSessionWindows>()

    override suspend fun textInputSession(
        session: suspend PlatformTextInputSessionScope<*>.() -> Nothing,
    ): Nothing {
        textInputSessionMutex.withSessionCancellingPrevious(
            sessionInitializer = {
                PlatformTextInputSessionWindows(
                    coroutineScope = it,
                    nativeWindow = nativeWindow,
                    composeScene = composeScene,
                    density = density,
                )
            },
            session,
        )
    }

    override fun isTextInputSessionActive(): Boolean {
        return textInputSessionMutex.currentSession?.currentTextInputClient != null
    }

    override fun handleEventWithInputSession(keyEvent: KeyEvent): Boolean {
        // The win32 IME half of AIR-5776: when a text-input session is active, a text-producing
        // KeyDown is handed to Win32 TranslateMessage (translate()) instead of being processed as a
        // Compose key event. Translation posts a WM_CHAR that arrives as Event.CharacterReceived and
        // is committed through tryHandleTextInputEvent below (or a WM_IME_* message for composing
        // input). Keys that do not produce text (navigation, editing, shortcuts) are filtered out
        // here and fall through to the normal Compose dispatch.
        if (keyEvent.type != KeyEventType.KeyDown) return false
        if (!isTextInputSessionActive()) return false
        val nativeEvent = (keyEvent.nativeKeyEvent as? InternalKeyEvent)
            ?.nativeEvent as? Win32Event.KeyDown ?: return false
        val chars = nativeEvent.toUnicode()
        // Control characters (enter/tab/backspace/delete and the like) are handled via dedicated
        // Compose actions, not the IME; a lone Ctrl or Alt (but not AltGr = Ctrl+Alt) is a shortcut
        // modifier, and Meta is never IME input.
        val shouldBeTranslated = chars.firstOrNull()?.let { !it.isISOControl() } == true &&
            !keyEvent.isMetaPressed &&
            (!keyEvent.isCtrlPressed && !keyEvent.isAltPressed ||
                keyEvent.isAltPressed && keyEvent.isCtrlPressed)
        return shouldBeTranslated && nativeEvent.translate()
    }

    /**
     * First-refusal for native win32 text events, offered at the top of the window's event handler.
     * A committed character (WM_CHAR) while a session is active is inserted directly; a dead-key
     * character opens a composition via marked text. Returns whether the event was consumed.
     */
    fun tryHandleTextInputEvent(event: Win32Event): Boolean {
        return when (event) {
            is Win32Event.CharacterReceived -> {
                val textInputClient = textInputSessionMutex.currentSession?.currentTextInputClient
                if (textInputClient != null && !event.character.isISOControl() && !event.isSystemKey) {
                    if (event.isDeadChar) {
                        textInputClient.setMarkedText(event.character.toString(), null, emptyList())
                    } else {
                        textInputClient.insertText(event.character.toString())
                    }
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }
}

private fun PlatformTextInputMethodRequestWindows.toTextInputClient(
    composeScene: ComposeScene,
    nativeWindow: Win32Window,
): Win32TextInputClient = WindowsTextInputClient(nativeWindow, this, composeScene)

/**
 * Adapts a [PlatformTextInputMethodRequestWindows] to the KDT win32 [Win32TextInputClient] protocol.
 * Every native→Compose ingress is wrapped in `composeScene.withFrameTransaction { }` (the fork's
 * analogue of Noria's `scene.withPreparedMainThread`): the whole IME callback is one frame slice,
 * so any state it writes publishes atomically. No suspension point is allowed inside the slice.
 *
 * Client coordinates are scene coordinates on win32 (the custom title bar lives inside the client
 * area), so [caretRect] returns the character rect straight in logical client space with no
 * window-origin offset — unlike macOS, which offsets by the window content origin.
 */
internal class WindowsTextInputClient(
    private val nativeWindow: Win32Window,
    private val request: PlatformTextInputMethodRequestWindows,
    private val composeScene: ComposeScene,
) : Win32TextInputClient {
    override fun selectedRange(): Win32TextRange = composeScene.withFrameTransaction {
        request.selectedRange().toWin32TextRange()
    }

    override fun caretRect(range: Win32TextRange): Win32LogicalRect =
        composeScene.withFrameTransaction {
            request.boundingRectForCharacterRange(range.toComposeTextRange()).toWin32LogicalRect()
        }

    override fun insertText(text: String): Unit = composeScene.withFrameTransaction {
        request.insertText(text, null)
    }

    override fun setMarkedText(
        text: String,
        selectedRange: Win32TextRange?,
        // TODO: composition clause styling is dropped (Noria parity) — multi-clause CJK
        //  conversion shows no per-segment highlighting until these are mapped to Compose.
        segments: List<Win32TextCompositionSegment>,
    ): Unit = composeScene.withFrameTransaction {
        request.setMarkedText(
            text,
            selectedRange?.toComposeTextRange(),
            null,
        )
    }

    override fun unmarkText() {
        composeScene.withFrameTransaction {
            request.unmarkText()
        }
        changed()
    }

    override fun discardMarkedText() {
        composeScene.withFrameTransaction {
            request.discardMarkedText()
        }
        changed()
    }

    private fun changed() {
        nativeWindow.notifySelectionChanged()
        nativeWindow.requestRedraw()
    }
}

private fun TextRange.toWin32TextRange(): Win32TextRange =
    Win32TextRange(start.toLong(), length.toLong())

private fun Win32TextRange.toComposeTextRange(): TextRange =
    TextRange(location.toInt(), (location + length).toInt())

private fun DpRect.toWin32LogicalRect(): Win32LogicalRect =
    Win32LogicalRect(
        origin = Win32LogicalPoint(left.value, top.value),
        size = Win32LogicalSize(right.value - left.value, bottom.value - top.value),
    )
