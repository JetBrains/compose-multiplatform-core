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

package androidx.compose.ui.platform

import androidx.compose.ui.events.beforeInputWithTargetRange
import androidx.compose.ui.events.compositionEnd
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.SetSelectionCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.w3c.dom.OPEN
import org.w3c.dom.ShadowRootInit
import org.w3c.dom.ShadowRootMode
import org.w3c.dom.events.Event

class DomInputStrategyTest {

    private class RecordingCommunicator : ComposeCommandCommunicator {
        val editCommands = mutableListOf<EditCommand>()

        override fun sendEditCommand(commands: List<EditCommand>) {
            editCommands.addAll(commands)
        }

        override fun sendKeyboardEvent(keyboardEvent: KeyEvent): Boolean = true
    }

    /**
     * A control test: it proves that the selection listener is alive in this very setup, so that
     * [caretMoveCausedByAutocorrectIsNotSentToCompose] can't be green just because the DOM =>
     * Compose selection synchronization doesn't work at all here.
     */
    @Test
    fun externalCaretMoveIsSentToCompose() = runTest {
        val communicator = RecordingCommunicator()
        val strategy = DomInputStrategy(ImeOptions.Default, communicator)
        val htmlInput = strategy.htmlInput

        val host = document.createElement("div")
        document.body!!.appendChild(host)
        val shadowRoot = host.attachShadow(ShadowRootInit(ShadowRootMode.OPEN))

        try {
            shadowRoot.appendChild(htmlInput)
            htmlInput.textContent = "hello wrld"
            htmlInput.focus()
            awaitAnimationFrame()

            setSelectionRange(htmlInput, 6, 6)
            document.dispatchEvent(Event("selectionchange"))
            awaitAnimationFrame()

            assertTrue(
                communicator.editCommands.contains(SetSelectionCommand(6, 6)),
                "the caret move must be propagated to Compose, got ${communicator.editCommands}"
            )
        } finally {
            strategy.dispose()
            host.remove()
        }
    }

    /**
     * Autocorrect/autosuggest insertions don't produce a "keydown" event, so the pause enabled in
     * the "beforeinput" listener is the only thing that prevents the native caret move (performed
     * by the browser while applying the edit to the contenteditable element) from being translated
     * into a [SetSelectionCommand].
     *
     * The native caret move is reported via "selectionchange" asynchronously, so it can easily
     * arrive after the collected events were processed - when neither `isCheckpointScheduled`
     * nor the pause enabled by [DomInputStrategy.updateState] protects the insertion point anymore.
     */
    @Test
    fun caretMoveCausedByAutocorrectIsNotSentToCompose() = runTest {
        val communicator = RecordingCommunicator()
        val strategy = DomInputStrategy(ImeOptions.Default, communicator)
        val htmlInput = strategy.htmlInput

        val host = document.createElement("div")
        document.body!!.appendChild(host)
        val shadowRoot = host.attachShadow(ShadowRootInit(ShadowRootMode.OPEN))

        try {
            shadowRoot.appendChild(htmlInput)
            htmlInput.textContent = "hello wrld"
            htmlInput.focus()
            awaitAnimationFrame()

            // the browser replaces "wrld" with "world" natively, without any "keydown" event
            htmlInput.dispatchEvent(
                beforeInputWithTargetRange(
                    inputType = "insertReplacementText",
                    data = "world",
                    startOffset = 6,
                    endOffset = 10
                )
            )

            // the collected events are processed here
            awaitAnimationFrame()

            // the browser has already moved the DOM caret to reflect the native edit
            // and reports it only now - when the events are already processed
            setSelectionRange(htmlInput, 6, 6)
            document.dispatchEvent(Event("selectionchange"))
            awaitAnimationFrame()

            assertEquals(
                listOf<EditCommand>(
                    SetSelectionCommand(6, 10),
                    CommitTextCommand("world", 1)
                ),
                communicator.editCommands
            )
        } finally {
            strategy.dispose()
            host.remove()
        }
    }

    /**
     * Covers the `isCheckpointScheduled` part of the selection listener guard: a "compositionend"
     * event schedules a checkpoint but, unlike "keydown"/"beforeinput", doesn't pause the
     * selection listener. While the collected events are not processed yet, the DOM selection is
     * in an intermediate state and must not be propagated to Compose.
     */
    @Test
    fun caretMoveWhileEventsAreNotProcessedYetIsNotSentToCompose() = runTest {
        val communicator = RecordingCommunicator()
        val strategy = DomInputStrategy(ImeOptions.Default, communicator)
        val htmlInput = strategy.htmlInput

        val host = document.createElement("div")
        document.body!!.appendChild(host)
        val shadowRoot = host.attachShadow(ShadowRootInit(ShadowRootMode.OPEN))

        try {
            shadowRoot.appendChild(htmlInput)
            htmlInput.textContent = "hello wrld"
            htmlInput.focus()
            awaitAnimationFrame()

            // the IME commits the composed text - the checkpoint is scheduled,
            // but the selection listener is not paused by this event
            htmlInput.dispatchEvent(compositionEnd("world"))

            // the DOM caret is moved before the collected events are processed
            setSelectionRange(htmlInput, 6, 6)
            document.dispatchEvent(Event("selectionchange"))

            // the collected events are processed here
            awaitAnimationFrame()
            awaitAnimationFrame()

            assertEquals(
                listOf<EditCommand>(CommitTextCommand("world", 1)),
                communicator.editCommands
            )
        } finally {
            strategy.dispose()
            host.remove()
        }
    }

    /**
     * A line break is already inserted by Compose from the "keydown" event. If the browser is
     * allowed to apply it natively, it splits the contenteditable content into several nodes
     * (Safari inserts a `<br>` and wraps the rest into a `<div>`), which breaks the offset math
     * relying on a single text node - every subsequent `beforeinput` reports a container-relative
     * offset, and the caret jumps to the beginning of the text.
     *
     * See https://youtrack.jetbrains.com/issue/CMP-10753
     */
    @Test
    fun lineBreakInputIsCancelledAndNotSentToCompose() = runTest {
        for (inputType in listOf("insertParagraph", "insertLineBreak")) {
            val communicator = RecordingCommunicator()
            val strategy = DomInputStrategy(ImeOptions.Default, communicator)
            val htmlInput = strategy.htmlInput

            val host = document.createElement("div")
            document.body!!.appendChild(host)
            val shadowRoot = host.attachShadow(ShadowRootInit(ShadowRootMode.OPEN))

            try {
                shadowRoot.appendChild(htmlInput)
                htmlInput.textContent = "line1\n"
                htmlInput.focus()
                awaitAnimationFrame()

                val event = beforeInputWithTargetRange(
                    inputType = inputType,
                    data = null,
                    startOffset = 6,
                    endOffset = 6,
                    cancelable = true
                )
                htmlInput.dispatchEvent(event)

                // the collected events are processed here
                awaitAnimationFrame()

                assertTrue(
                    event.defaultPrevented,
                    "'$inputType' must be cancelled so that the browser doesn't split the DOM"
                )
                assertEquals(
                    emptyList<EditCommand>(),
                    communicator.editCommands,
                    "'$inputType' must not produce edit commands - Compose already inserted the line break"
                )
            } finally {
                strategy.dispose()
                host.remove()
            }
        }
    }

    private suspend fun awaitAnimationFrame() {
        suspendCancellableCoroutine { continuation ->
            window.requestAnimationFrame { continuation.resumeWith(Result.success(Unit)) }
        }
    }
}
