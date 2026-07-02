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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsName
import kotlin.js.JsNumber
import kotlin.js.definedExternally
import kotlin.js.get
import kotlin.js.js
import kotlin.js.toInt
import kotlin.js.unsafeCast
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.EventInit
import org.w3c.dom.Node
import org.w3c.dom.events.CompositionEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.UIEvent
import org.w3c.dom.events.InputEvent
import org.w3c.dom.events.KeyboardEvent


internal class DomInputStrategy(
    imeOptions: ImeOptions,
    private val composeSender: ComposeCommandCommunicator,
) {
    val htmlInput = imeOptions.createDomElement()

    private var lastMeaningfulUpdate = TextFieldValue("")
    private var latestSelection = TextSelection(0, 0)
    private var isInCompositionMode = false

    // To avoid the re-triggering of the selection change
    private var pauseSelectionChangeListener = false
    private var selectionChangeListener: ((Event) -> Unit)? = null

    init {
        initEvents()
    }

    private val nativeInputEventsProcessor = object : NativeInputEventsProcessor(composeSender) {
        override fun scheduleCheckpoint() {
            window.requestAnimationFrame {
                runCheckpoint(currentTextFieldValue = lastMeaningfulUpdate)
            }
        }
    }

    fun updateState(textFieldValue: TextFieldValue) {
        val needsTextUpdate = (lastMeaningfulUpdate.text != textFieldValue.text) && !isInCompositionMode
        val needsSelectionUpdate = (lastMeaningfulUpdate.selection != textFieldValue.selection) && !isInCompositionMode
        lastMeaningfulUpdate = textFieldValue

        if (needsTextUpdate) {
            htmlInput.textContent = textFieldValue.text

            htmlInput.focus()
        }

        if (needsTextUpdate || needsSelectionUpdate) {
            pauseSelectionChangeListener = true
            setSelectionRange(htmlInput, textFieldValue.selection.min, textFieldValue.selection.max)

            // Resetting `pauseSelectionChangeListener` synchronously right after is not enough
            // TODO: this is the cheapest way to make sure that DOM <=> Compose sync won't self-trigger but we need to consider better possible options
            window.requestAnimationFrame {
                pauseSelectionChangeListener = false
            }
        }
    }

    private val tabKeyCode = Key.Tab.keyCode.toInt()

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun initEvents() {
        // Whenever new type of event is processed, don't forget to sync the NativeInputEventsProcessor::runCheckpoint isIME check
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
                val inputExt = evt.asInputEventExt()

                inputExt.firstRange = inputExt.getTargetRanges()[0]

                nativeInputEventsProcessor.registerEvent(evt)
            }
        })

        htmlInput.addEventListener("compositionstart", {evt ->
            isInCompositionMode = true
        })

        htmlInput.addEventListener("compositionend", { evt ->
            isInCompositionMode = false
            nativeInputEventsProcessor.registerEvent(evt as CompositionEvent)
        })

        selectionChangeListener = listener@{ _ ->
            if (pauseSelectionChangeListener || !isInputActive()) return@listener

            val currentSelection = getSelectionRange(htmlInput)
            val start = currentSelection?.get(0)?.toInt() ?: 0
            val end = currentSelection?.get(1)?.toInt() ?: 0
            latestSelection = TextSelection(start, end)

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

    var firstRange: StaticRange?

    constructor(type: String, eventInitDict: EventInit = definedExternally)

    /**
     * Returns an array of static ranges that will be affected by a change to the DOM
     * if the input event is not canceled.
     *
     * See https://developer.mozilla.org/en-US/docs/Web/API/InputEvent/getTargetRanges
     */
    fun getTargetRanges(): JsArray<StaticRange>
}

/**
 * Represents a [StaticRange] - a range of content in a document that is not updated
 * when the underlying DOM tree is modified.
 *
 * See https://developer.mozilla.org/en-US/docs/Web/API/StaticRange
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal external interface StaticRange : JsAny {
    val startContainer: JsAny
    val startOffset: Int
    val endContainer: JsAny
    val endOffset: Int
    val collapsed: Boolean
}


internal inline fun UIEvent.asInputEventExt(): InputEventExt =  unsafeCast<InputEventExt>()

private fun ImeOptions.createDomElement(): HTMLElement {
    val htmlElement = document.createElement(
        if (singleLine) "span" else "div"
    ) as HTMLElement

    // without autocorrect set "on" iOS virtual keyboard won't suggest
    // see https://youtrack.jetbrains.com/issue/CMP-8807
    htmlElement.setAttribute("autocorrect", "on")
    htmlElement.setAttribute("autocomplete", "off")
    htmlElement.setAttribute("autocapitalize", "off")
    htmlElement.setAttribute("spellcheck", "false")

    htmlElement.setAttribute("contenteditable", "true")

    val inputMode = when (keyboardType) {
        KeyboardType.Text -> "text"
        KeyboardType.Ascii -> "text"
        KeyboardType.Number -> "number"
        KeyboardType.Phone -> "tel"
        KeyboardType.Uri -> "url"
        KeyboardType.Email -> "email"
        KeyboardType.Password -> "password"
        KeyboardType.NumberPassword -> "number"
        KeyboardType.Decimal -> "decimal"
        else -> "text"
    }

    val enterKeyHint = when (imeAction) {
        ImeAction.Default -> "enter"
        ImeAction.None -> "enter"
        ImeAction.Done -> "done"
        ImeAction.Go -> "go"
        ImeAction.Next -> "next"
        ImeAction.Previous -> "previous"
        ImeAction.Search -> "search"
        ImeAction.Send -> "send"
        else -> "enter"
    }

    htmlElement.setAttribute("inputmode", inputMode)
    htmlElement.setAttribute("enterkeyhint", enterKeyHint)
    htmlElement.classList.add("compose-backing-field")

    return htmlElement
}

@OptIn(ExperimentalWasmJsInterop::class)
private external interface HasDomSelection : JsAny {
    fun getSelection(): Selection?
}

/**
 * Represents a [Selection] - the range of text selected by the user or the current position of the caret.
 *
 * Minimal definition sufficient for [setSelectionRange] and [getSelectionOffsets].
 *
 * See https://developer.mozilla.org/en-US/docs/Web/API/Selection
 */
@OptIn(ExperimentalWasmJsInterop::class)
private external interface Selection : JsAny {
    // https://developer.mozilla.org/en-US/docs/Web/API/Selection/setBaseAndExtent
    fun setBaseAndExtent(anchorNode: Node, anchorOffset: Int, focusNode: Node, focusOffset: Int)
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun getSelectionRange(element: HTMLElement): JsArray<JsNumber>? = js(
    """{
        var selection = window.getSelection();
        if (selection == null) return null;
        var root = element.getRootNode();
        if (root == null) return null;

        if (typeof selection.getComposedRanges === 'function') {
            try {
                // The modern standard approach
                var composedRanges = selection.getComposedRanges({ shadowRoots: [root] });
                if (composedRanges.length > 0) {
                    var firstRange = composedRanges[0];
                    return [firstRange.startOffset, firstRange.endOffset];
                }
                return null;
            } catch (e) {
                // Fallback for early Safari 17 point-releases
                var composedRanges = selection.getComposedRanges(root);
                if (composedRanges.length > 0) {
                    var firstRange = composedRanges[0];
                    return [firstRange.startOffset, firstRange.endOffset];
                }
                return null;
            }
        }

        if (typeof root.getSelection === 'function') {
            var rootSelection = root.getSelection();
            if (rootSelection == null) return [0, 0];
            if (rootSelection.rangeCount > 0) {
                var rootRange = rootSelection.getRangeAt(0);
                return [rootRange.startOffset, rootRange.endOffset];
            }
            return null;
        }

        if (selection.rangeCount > 0) {
            var selectionRange = selection.getRangeAt(0);
            return [selectionRange.startOffset, selectionRange.endOffset];
        }
        return null;
    }"""
)

internal fun setSelectionRange(element: HTMLElement, startOffset: Int, endOffset: Int) {
    val selection = window.unsafeCast<HasDomSelection>().getSelection()

    val textNode = element.firstChild
    if (textNode != null) {
        selection?.setBaseAndExtent(textNode, startOffset, textNode, endOffset)
    } else {
        selection?.setBaseAndExtent(element, 0, element, 0)
    }
}


internal fun isTypedEvent(evt: KeyboardEvent): Boolean =
    js("!evt.metaKey && !evt.ctrlKey && evt.key.charAt(0) === evt.key")


private data class TextSelection(val start: Int, val end: Int)