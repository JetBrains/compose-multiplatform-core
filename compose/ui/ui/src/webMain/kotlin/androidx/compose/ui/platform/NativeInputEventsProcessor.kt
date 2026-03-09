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

package androidx.compose.ui.platform

import androidx.compose.runtime.TestOnly
import androidx.compose.ui.input.key.toComposeEvent
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.util.fastForEach
import org.w3c.dom.events.CompositionEvent
import org.w3c.dom.events.InputEvent
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.UIEvent
import kotlin.js.js
import kotlin.js.toDouble
import kotlin.js.toInt

/**
 * Processes native input events and handles their translation to commands
 * for Compose-based input handling and text editing. This class aggregates
 * events such as keyboard, composition, and input events, managing their
 * scheduling and execution in defined checkpoints.
 *
 * @param composeSender The communicator responsible for transmitting edit
 * commands and keyboard events to the Compose system.
 */
internal abstract class NativeInputEventsProcessor(
    private val composeSender: ComposeCommandCommunicator
) {

    private val collectedEvents = mutableListOf<UIEvent>()
    private var isCheckpointScheduled = false
    private var lastCompositionEndTimestamp = 0.0 // Double because of k/wasm where Number.toLong() leads to a compilation error
    var isInIMEComposition = false
    private var lastKeydownStatus: ComposeKeyDownStatus? = null

    /**
     * Schedules a checkpoint for processing input events.
     *
     * Realistically, it would schedule the checkpoint to the next Animation Frame:
     * window.requestAnimationFrame { runCheckpoint(...) }.
     *
     * But we keep it abstract to simplify the testing (unit tests).
     */
    abstract fun scheduleCheckpoint()

    private fun internalScheduleCheckpoint() {
        if (!isCheckpointScheduled) {
            scheduleCheckpoint()
            isCheckpointScheduled = true
        }
    }

    private fun UIEvent.isInIMEComposition(): Boolean {
        return (this is CompositionEvent)
            || type == "keydown" && (this as KeyboardEvent).isComposing
            || type == "beforeinput" && (this as InputEvent).isComposing
    }

    fun runCheckpoint(currentTextFieldValue: TextFieldValue) {
        isCheckpointScheduled = false

        collectedEvents.sortBy { it.timeStamp.toInt() }

        collectedEvents.fastForEach { evt ->
            val timestamp = evt.timeStamp.toDouble()

            when (evt.type) {
                "keydown" -> {
                    if (isInIMEComposition) return@fastForEach

                    evt as KeyboardEvent
                    if (isTypedEvent(evt)) {
                        // we need to reset this each time we consider something to be typed
                        // see  https://youtrack.jetbrains.com/issue/CMP-8773
                        lastKeydownStatus = null
                        return@fastForEach
                    }

                    val isFromLastComposition = timestamp < lastCompositionEndTimestamp

                    // see https://youtrack.jetbrains.com/issue/CMP-8745/Web-Mobile.-iOS.-Composite-input.-Characters-arent-deleted
                    // on mobile iOS we cannot rely on the timestamp of the "keydown" event, it's always zero
                    // it might seem strange that we are ignoring the isFromLastComposition safeguard
                    // which was historically introduced exactly to resolve issues in Safari -
                    // but isFromLastComposition is needed so that we won't type a number digit if it was pressed during composition mode
                    // this is something that is not supposed to happen on mobile devices
                    val shouldBeProcessed = timestamp == 0.0 || !isFromLastComposition

                    if (shouldBeProcessed) {
                        lastKeydownStatus = ComposeKeyDownStatus(
                            evt,
                            composeSender.sendKeyboardEvent(evt.toComposeEvent())
                        )

                    }
                }

                "compositionend" -> {
                    lastCompositionEndTimestamp = timestamp
                    composeSender.sendEditCommand(CommitTextCommand((evt as CompositionEvent).data, 1))
                }

                "beforeinput" -> {
                    evt.asInputEventExt().process(currentTextFieldValue = currentTextFieldValue)
                }
            }
        }

        isInIMEComposition = false
        collectedEvents.clear()
    }

    private fun InputEventExt.process(currentTextFieldValue: TextFieldValue) {
        val textRangeSize = textRangeEnd - textRangeStart

        val editCommands = buildList {
            when (inputType) {
                "deleteContentBackward" -> {
                    if (lastKeydownStatus?.getProcessedEvent()?.isBackspace() == true) return@buildList

                    if (!currentTextFieldValue.selection.collapsed) {
                        // Likely it's on mobile, where the Backspace has Unidentified key value.
                        // When Compose TextField shows text selection,
                        // a good UX for deleteContentBackward would be to emulate Backspace
                        add(BackspaceCommand())
                    } else {
                        // This happens when an autocorrection is applied on mobile:
                        // The system first tells us to delete the old text,
                        // and then it would send the "insertText" event.
                        if (textRangeSize > 0) {
                            // deleteContentBackward can happen under very non-trivial circumstances,
                            // for instance; when an input suggestion on Android Chrome is accepted,
                            // the browser then deletes space after the word just to add space again
                            add(SetSelectionCommand(textRangeStart, textRangeEnd))
                            add(BackspaceCommand())
                        } else if (textRangeSize == 0) {
                            // under specific circumstance previous symbol can be deleted while inputting new one
                            // see https://youtrack.jetbrains.com/issue/CMP-8773
                            add(BackspaceCommand())
                        }
                    }
                }

                "insertReplacementText" -> {
                    if (data == null) return@buildList
                    if (textRangeSize > 0) {
                        add(SetSelectionCommand(textRangeStart, textRangeEnd))
                    }

                    add(CommitTextCommand(data, 1))
                }

                "insertText" -> {
                    if (data == null) return@buildList
                    if (textRangeSize > 0 && currentTextFieldValue.selection.collapsed) {
                        add(SetSelectionCommand(textRangeStart, textRangeEnd))
                    }

                    add(CommitTextCommand(data, 1))
                }

                "insertCompositionText" -> {
                    if (data == null) return@buildList
                    if (textRangeSize > 0) {
                        add(SetSelectionCommand(textRangeStart, textRangeEnd))
                    }
                    add(SetComposingTextCommand(data, 1))
                }

                // "insertFromComposition", "deleteCompositionText" are triggered in Safari just before the 'compositionEnd' event.
                // They're ignored because Safari also sends 'insertCompositionText' which we handle (alongside 'compositionEnd')
            }
        }

        if (editCommands.isNotEmpty()) {
            composeSender.sendEditCommand(editCommands)
        }
    }

    internal fun updateImeStatus(event: UIEvent) {
        isInIMEComposition = isInIMEComposition || event.isInIMEComposition()
    }

    internal fun registerEvent(event: UIEvent) {
        updateImeStatus(event)
        collectedEvents.add(event)
        internalScheduleCheckpoint()
    }

    @TestOnly
    internal fun getCollectedEvents() = collectedEvents
}

private fun isTypedEvent(evt: KeyboardEvent): Boolean =
    js("!evt.metaKey && !evt.ctrlKey && evt.key.charAt(0) === evt.key")


private class ComposeKeyDownStatus(
    val event: KeyboardEvent,
    val processed: Boolean
)

private fun ComposeKeyDownStatus.getProcessedEvent(): KeyboardEvent? {
    return if (processed) event else null
}

private fun KeyboardEvent.isBackspace(): Boolean = key == "Backspace"
