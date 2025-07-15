package androidx.compose.ui.platform

import androidx.compose.ui.input.key.toComposeEvent
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextInCodePointsCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.CompositionEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent


internal class DomInputStrategy(
    imeOptions: ImeOptions,
    private val composeSender: ComposeCommandCommunicator,
) {
    val htmlInput = imeOptions.createDomElement()

    private var editState: EditState = EditState.Default

    private var lastMeaningfulUpdate = TextFieldValue("")

    private var repeatDetector: RepeatDetector

    init {
        repeatDetector = RepeatDetector(htmlInput)
        initEvents()
    }

    fun updateState(textFieldValue: TextFieldValue) {
        htmlInput as HTMLElementWithValue

        if (editState != EditState.WaitingComposeActivity) return

        if (lastMeaningfulUpdate.text != textFieldValue.text) {
            htmlInput.value = textFieldValue.text
        }
        if (lastMeaningfulUpdate.selection != textFieldValue.selection) {
            htmlInput.setSelectionRange(textFieldValue.selection.start, textFieldValue.selection.end)
        }

        lastMeaningfulUpdate = textFieldValue

        editState = EditState.Default
    }

    var lastKeyDownEvent: KeyboardEvent? = null

    private fun initEvents() {
        var lastKeyboardEventIsDown = false

        htmlInput.addEventListener("blur", {evt ->
            // both accent dialogue and composition dialogue are lost when we switch windows
            // but can be restored later on if we are back
            editState = EditState.Default
        })

        var typedEventInputBalance = 0
        var keyDownUpPair = 0
        var lastKeydown: KeyboardEvent? = null
        var isLastKeyDownTypedRepeat = false
        var noSkipNextDelete = false

        htmlInput.addEventListener("keydown", { evt ->
            keyDownUpPair = 1
            evt as KeyboardEvent

            val key = evt.key
            lastKeydown = evt
            val isTypedEvent = isTypedEvent(evt)
            lastKeyboardEventIsDown = key != "Dead" && key != "Unidentified"
            println("$editState, keydown - ${key}, isComposing = ${evt.isComposing} $key, $typedEventInputBalance, repeat = ${evt.repeat}, ist=$isTypedEvent")

            noSkipNextDelete = isLastKeyDownTypedRepeat && key == "Process"
            isLastKeyDownTypedRepeat = isTypedEvent && evt.repeat
            if (isTypedEvent) {
                if (typedEventInputBalance == 0) typedEventInputBalance++
                return@addEventListener
            } else if (!evt.isComposing && typedEventInputBalance == 0) {
                composeSender.sendKeyboardEvent(evt.toComposeEvent())
                return@addEventListener
            }
            typedEventInputBalance = 0
            return@addEventListener
        })

        htmlInput.addEventListener("keyup", { evt ->
            keyDownUpPair = 0
            lastKeyboardEventIsDown = false
            lastKeydown = null
            evt as KeyboardEvent
            println("keyup ${evt.key}")

            if (evt.isComposing) {
                editState = EditState.CompositeDialogue
            }
        })

        htmlInput.addEventListener("beforeinput", { evt ->
            evt as InputEvent

            if (editState is EditState.WaitingComposeActivity) return@addEventListener

            println(evt.inputType)

            when (evt.inputType) {
                "deleteContentBackward" -> {
                    if (lastKeydown != null && lastKeydown?.key != "Backspace") {
                        htmlInput as HTMLElementWithValue
                        val size = htmlInput.selectionEnd - htmlInput.selectionStart
                        composeSender.sendEditCommand(DeleteSurroundingTextCommand(size,0))
                    }
                }
                "insertCompositionText" -> {
                    editState = EditState.Default
                    composeSender.sendEditCommand(SetComposingTextCommand(evt.data!!, 1))
                }
                "insertReplacementText" -> {
                    // happens in Safari when we choose something from the Accent Dialogue
                    editState = EditState.WaitingComposeActivity
                    composeSender.sendEditCommand(listOf(
                        DeleteSurroundingTextInCodePointsCommand(1, 0),
                        CommitTextCommand(evt.data!!, 1)
                    ))
                }
                "insertText" -> {
                    println("insertText = ${evt.data}, $typedEventInputBalance, ${evt.isComposing}")
                    val editCommands = mutableListOf<EditCommand>()

                    println("lkdk = ${lastKeydown?.key} vs ${evt.data}")
                    val eq = lastKeydown != null && lastKeydown!!.key != evt.data
                    if ((keyDownUpPair == 0 || eq) && lastKeydown?.key != "Unidentified") {
                        editCommands.add(DeleteSurroundingTextInCodePointsCommand(1, 0))
                    }

                    editCommands.add(CommitTextCommand(evt.data!!, 1))

                    editState = EditState.WaitingComposeActivity
                    composeSender.sendEditCommand(editCommands)
                }
            }

            typedEventInputBalance = 0
        })

        htmlInput.addEventListener("compositionstart", {evt ->
            evt as CompositionEvent
            typedEventInputBalance = 0
            editState = EditState.CompositeDialogue

            println("compositionstart($lastKeyboardEventIsDown) - ${evt.data}, skipNextDelete = $noSkipNextDelete")
            if (lastKeydown?.key == "Process") {
                if (noSkipNextDelete) {
                    composeSender.sendEditCommand(DeleteSurroundingTextInCodePointsCommand(1, 0))
                }
            } else if (lastKeydown != null && !isTypedEvent(lastKeydown!!)) {
                composeSender.sendEditCommand(DeleteSurroundingTextInCodePointsCommand(1, 0))
            }
        })

        htmlInput.addEventListener("compositionend", {evt ->
            evt as CompositionEvent
            typedEventInputBalance = 0
            println("compositionend - ${evt.data}")

            // in Safari we can rely on "insertFromComposition" input event but unfortunately it's not present in other browsers
            editState = EditState.WaitingComposeActivity
            composeSender.sendEditCommand(CommitTextCommand(evt.data, 1))
        })
    }
}


private sealed interface EditState {
    data object Default : EditState
    data object WaitingComposeActivity : EditState
    data object CompositeDialogue: EditState
    data object AccentDialogue: EditState
}

private external class InputEvent : Event {
    val inputType: String
    val data: String?
    val isComposing: Boolean
}

private fun ImeOptions.createDomElement(): HTMLElement {
    val htmlElement = document.createElement(
        if (singleLine) "input" else "textarea"
    ) as HTMLElement

    htmlElement.setAttribute("autocorrect", "off")
    htmlElement.setAttribute("autocomplete", "off")
    htmlElement.setAttribute("autocapitalize", "off")
    htmlElement.setAttribute("spellcheck", "false")

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


    htmlElement.style.apply {
        setProperty("position", "absolute")
        setProperty("user-select", "none")
        setProperty("forced-color-adjust", "none")
        setProperty("white-space", "pre")
        setProperty("align-content", "center")
        setProperty("top", "calc(min(var(--compose-internal-web-backing-input-top) * 1px, 100vh - var(--compose-internal-web-backing-input-height) * 1px))")
        setProperty("left", "calc(min(var(--compose-internal-web-backing-input-left) * 1px, 100vw - var(--compose-internal-web-backing-input-width) * 1px))")
        setProperty("width", "calc(var(--compose-internal-web-backing-input-width) * 1px")
        setProperty("height", "calc(var(--compose-internal-web-backing-input-height) * 1px")
        setProperty("padding", "0")
        setProperty("opacity", "0")
        setProperty("color", "transparent")
        setProperty("background", "transparent")
        setProperty("caret-color", "transparent")
        setProperty("outline", "none")
        setProperty("border", "none")
        setProperty("resize", "none")
        setProperty("text-shadow", "none")
        setProperty("z-index", "-1")
        // TODO: do we need pointer-events: none
        //setProperty("pointer-events", "none")
    }

    return htmlElement
}

private external interface HTMLElementWithValue  {
    var value: String
    val selectionStart: Int
    val selectionEnd: Int
    fun setSelectionRange(start: Int, end: Int, direction: String = definedExternally)
}

/**
 * Represents the mode of key input repetition handling during text input.
 */
private sealed interface RepeatMode {
    /**
     * The repetition behavior of a key input event cannot be determined so far
     */
    data object Unknown: RepeatMode

    /**
     * Repetition triggers Accent Dialogue
     */
    data object Accent: RepeatMode

    /**
     * Repetition does not trigger Accent Dialogue
     */
    data object Default: RepeatMode
}

private class RepeatDetector(private val input: HTMLElement) {
    private var resolving = false
    var repeatMode: RepeatMode = RepeatMode.Accent
        private set

    init {
//        initEvents()
    }

    fun initEvents() {
        input.addEventListener("keydown", { evt ->
            evt as KeyboardEvent
            println("RepeatMode - ${evt.repeat}, ${evt.key}, ${this.repeatMode}")
            if (evt.repeat && this.repeatMode === RepeatMode.Unknown) {
                // we can not deduce anything if event is not typed
                if (!isTypedEvent(evt)) return@addEventListener
//                resolving = true
                if (resolving) {
                    println("Set RepeatMode.Accent")
                    repeatMode = RepeatMode.Accent;
                    resolving = false;
                } else {
                    resolving = true;
                }
            }
        });

        input.addEventListener("beforeinput", {
            if (resolving && repeatMode === RepeatMode.Unknown) {
                resolving = false;
                println("Set RepeatMode.Accent")
                repeatMode = RepeatMode.Default;
            }
        });
    }
}

private fun isTypedEvent(evt: KeyboardEvent): Boolean = js("!evt.metaKey && !evt.ctrlKey && evt.key.charAt(0) === evt.key")