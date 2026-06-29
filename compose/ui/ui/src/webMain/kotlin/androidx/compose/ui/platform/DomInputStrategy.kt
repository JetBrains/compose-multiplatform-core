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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsName
import kotlin.js.definedExternally
import kotlin.js.get
import kotlin.js.js
import kotlin.js.length
import kotlin.js.unsafeCast
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.EventInit
import org.w3c.dom.Node
import org.w3c.dom.Range
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
            htmlInput.setSelectionRange(textFieldValue.selection.min, textFieldValue.selection.max)
            pauseSelectionChangeListener = false
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

                inputExt.textRangeStart = latestSelection.start
                inputExt.textRangeEnd = latestSelection.end

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

            val currentSelection = htmlInput.getSelectionRange()
            latestSelection = currentSelection

            val selection = lastMeaningfulUpdate.selection
            val start = currentSelection.start
            val end = currentSelection.end

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

internal val InputEventExt.textRangeCollapsed: Boolean
    get() = this.asInputEventExt().let { it.textRangeEnd == it.textRangeStart }


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
    val rangeCount: Int
    fun getRangeAt(index: Int): Range
    fun removeAllRanges()
    fun addRange(range: Range)
    val anchorOffset: Int
    val focusOffset: Int
    // https://developer.mozilla.org/en-US/docs/Web/API/Selection/setBaseAndExtent
    fun setBaseAndExtent(anchorNode: Node, anchorOffset: Int, focusNode: Node, focusOffset: Int)

    /**
     * See https://developer.mozilla.org/en-US/docs/Web/API/Selection/getComposedRanges
     */
    fun getComposedRanges(options: GetComposedRangesOptions = definedExternally): JsArray<StaticRange>

    fun getComposedRanges(fallbackRoot: DocumentOrShadowRootLike): JsArray<StaticRange>
}

private fun HTMLElement.getSelectionRange(): TextSelection {
    val selection = window.unsafeCast<HasDomSelection>().getSelection() ?: return TextSelection(0, 0)
    val root = this.unsafeCast<NodeWithRootNode>().getRootNode() ?: return TextSelection(0, 0)

    val composedRanges = try {
        // Try the modern standard approach
        selection.getComposedRanges(GetComposedRangesOptions(root))
    } catch (e: Throwable) {
        // Fallback for older Safari 17 point-releases
        selection.getComposedRanges(root)
    }

    if (composedRanges.length > 0) {
        val firstRange = composedRanges[0]!!
        return TextSelection(firstRange.startOffset, firstRange.endOffset)
    }

    return TextSelection(0, 0)
}

/**
 * Options for [Selection.getComposedRanges].
 * See https://developer.mozilla.org/en-US/docs/Web/API/Selection/getComposedRanges#parameters
 */
@OptIn(ExperimentalWasmJsInterop::class)
private external interface GetComposedRangesOptions : JsAny {
    var shadowRoots: JsArray<DocumentOrShadowRootLike>
}
private fun GetComposedRangesOptions(root: DocumentOrShadowRootLike): GetComposedRangesOptions =  js("({ shadowRoots: [root] })")

internal fun HTMLElement.setSelectionRange(startOffset: Int, endOffset: Int) {
    val selection = window.unsafeCast<HasDomSelection>().getSelection()

    val textNode = firstChild
    if (textNode != null) {
        selection?.setBaseAndExtent(textNode, startOffset, textNode, endOffset)
    } else {
        selection?.setBaseAndExtent(this, 0, this, 0)
    }
}

internal fun isTypedEvent(evt: KeyboardEvent): Boolean =
    js("!evt.metaKey && !evt.ctrlKey && evt.key.charAt(0) === evt.key")


private data class TextSelection(val start: Int, val end: Int)