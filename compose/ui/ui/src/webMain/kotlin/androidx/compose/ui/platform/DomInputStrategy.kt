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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.definedExternally
import kotlin.js.js
import kotlin.js.unsafeCast
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.EventInit
import org.w3c.dom.events.CompositionEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.UIEvent
import org.w3c.dom.events.InputEvent
import org.w3c.dom.events.KeyboardEvent

internal abstract class DomInputStrategy(
    private val htmlInput: HTMLElement,
    private val composeSender: ComposeCommandCommunicator,
): TextLayoutProvider {
    private var lastMeaningfulUpdate = TextFieldValue("")

    // To avoid the re-triggering of the selection change
    private var pauseSelectionChangeListener = false
    private var selectionChangeListener: ((Event) -> Unit)? = null

    init {
        initEvents()
    }

    fun updateState(textFieldValue: TextFieldValue) {
        htmlInput as HTMLElementWithValue

        val needsTextUpdate = lastMeaningfulUpdate.text != textFieldValue.text
        val needsSelectionUpdate = lastMeaningfulUpdate.selection != textFieldValue.selection
        lastMeaningfulUpdate = textFieldValue

        if (needsTextUpdate) {
            htmlInput.value = textFieldValue.text
        }
        if (needsSelectionUpdate) {
            pauseSelectionChangeListener = true
            htmlInput.setSelectionRange(textFieldValue.selection.min, textFieldValue.selection.max)
            pauseSelectionChangeListener = false
        }
    }

    private val tabKeyCode = Key.Tab.keyCode.toInt()

    private fun initEvents() {
        val nativeInputEventsProcessor = object : NativeInputEventsProcessor() {
            override fun scheduleCheckpoint() {
                window.requestAnimationFrame {
                    runCheckpoint(currentTextFieldValue = lastMeaningfulUpdate)
                }
            }

            override fun withCommandSenderContext(block: ComposeCommandCommunicator.() -> Unit) {
                block.invoke(composeSender)
            }

            override fun currentTextLayoutResult(): TextLayoutResult? = this@DomInputStrategy.currentTextLayoutResult()
        }

        htmlInput.addEventListener("keydown", { evt ->
            nativeInputEventsProcessor.registerEvent(evt as KeyboardEvent)

            if (evt.keyCode == tabKeyCode) {
                // Compose logic will handle the focus movement or insert Tabs if necessary
                evt.preventDefault()
            }

            // Let Compose decide the selection right after a new key input
            pauseSelectionChangeListener = true
        })

        htmlInput.addEventListener("keyup", { evt ->
            nativeInputEventsProcessor.registerEvent(evt as KeyboardEvent)
        })

        htmlInput.addEventListener("beforeinput", { evt ->
            if (evt is InputEvent) {
                htmlInput as HTMLElementWithValue

                val inputExt = evt.asInputEventExt()
                inputExt.textRangeStart = htmlInput.selectionStart
                inputExt.textRangeEnd = htmlInput.selectionEnd

                nativeInputEventsProcessor.registerEvent(evt)
            }
        })

        htmlInput.addEventListener("compositionstart", { evt ->
            nativeInputEventsProcessor.registerEvent(evt as CompositionEvent)
        })

        htmlInput.addEventListener("compositionend", { evt ->
            nativeInputEventsProcessor.registerEvent(evt as CompositionEvent)
        })

        selectionChangeListener = listener@{ _ ->
            if (pauseSelectionChangeListener || !isInputActive()) return@listener
            htmlInput as HTMLElementWithValue
            val start = htmlInput.selectionStart
            val end = htmlInput.selectionEnd
            val selection = lastMeaningfulUpdate.selection

            if (start != selection.min || end != selection.max) {
                val normalizedStart = minOf(start, end)
                val normalizedEnd = maxOf(start, end)
                composeSender.sendEditCommand(SetSelectionCommand(normalizedStart, normalizedEnd))
            }
        }
        document.addEventListener("selectionchange", selectionChangeListener)
    }

    fun dispose() {
        document.removeEventListener("selectionchange", selectionChangeListener)
        selectionChangeListener = null
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun isInputActive(): Boolean {
        val root = htmlInput.unsafeCast<NodeWithRootNode>().getRootNode()
        val rootActive = root?.activeElement
        return rootActive == htmlInput
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private external interface NodeWithRootNode : JsAny {
    fun getRootNode(): DocumentOrShadowRootLike?
}

private external interface DocumentOrShadowRootLike : JsAny {
    val activeElement: HTMLElement?
}

@JsName("InputEvent")
internal external class InputEventExt : UIEvent {
    val data: String?
    val inputType: String
    var textRangeStart: Int
    var textRangeEnd: Int

    constructor(type: String, eventInitDict: EventInit = definedExternally)
}

internal inline fun UIEvent.asInputEventExt(): InputEventExt =  unsafeCast<InputEventExt>()

internal val InputEventExt.textRangeSize: Int
    get() = this.asInputEventExt().let { it.textRangeEnd - it.textRangeStart }

private external interface HTMLElementWithValue {
    var value: String
    val selectionStart: Int
    val selectionEnd: Int
    val selectionDirection: String?
    fun setSelectionRange(start: Int, end: Int, direction: String = definedExternally)
}

internal fun isTypedEvent(evt: KeyboardEvent): Boolean =
    js("!evt.metaKey && !evt.ctrlKey && evt.key.charAt(0) === evt.key")
