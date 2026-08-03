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

package androidx.compose.ui.desktop.wasm

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.SessionMutex
import androidx.compose.ui.desktop.TextInputSessionOwner
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.InternalKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.withFrameTransaction
import androidx.compose.ui.unit.Density
import kotlin.js.js
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * The browser text-input request contract. Mirrors Noria's API of the same FQN so that product
 * IME integrations compile against either runtime.
 */
interface PlatformTextInputMethodRequestWasmJs : PlatformTextInputMethodRequest {
    /** Rect of the current caret in the scene's root coordinates, or null if not available. */
    fun caretRectInRoot(): Rect?

    /** Insert [text] at the caret, replacing the current composition (if any) or selection. */
    fun commitText(text: String)

    /** Replace previous composition (or current selection) with [text] and mark it as composing. */
    fun setComposingText(text: String)

    /** Finalize the current composition, leaving the inserted text in place. */
    fun finishComposingText()

    /**
     * Delete the content immediately before the caret, in response to a browser `beforeinput`
     * event with `inputType="deleteContentBackward"`. Per the W3C Input Events spec the deleted
     * unit is browser-defined; the typical approximation is one code point.
     */
    fun deleteContentBackward()
}

class PlatformTextInputSessionWasmJs internal constructor(
    coroutineScope: CoroutineScope,
    private val scene: ComposeScene,
    private val density: () -> Density,
    private val container: HTMLElement,
    private val focus: WasmJsDomFocus,
) : PlatformTextInputSessionScope<PlatformTextInputMethodRequestWasmJs>,
    CoroutineScope by coroutineScope {

    internal var currentTextInputSession: WasmJsTextInputSession? = null
        private set

    override suspend fun startInputMethod(request: PlatformTextInputMethodRequestWasmJs): Nothing {
        val hadDomFocus = focus.hasDomFocus()
        currentTextInputSession?.dispose()
        val session = WasmJsTextInputSession(
            request = request,
            scene = scene,
            density = density,
            container = container,
        )
        currentTextInputSession = session
        focus.setTextArea(session.textArea)
        if (hadDomFocus) {
            focus.requestFocus()
        }
        focus.updateFocusState()
        try {
            awaitCancellation()
        } finally {
            val hadDomFocusOnEnd = focus.hasDomFocus()
            session.dispose()
            currentTextInputSession = null
            focus.setTextArea(null)
            if (hadDomFocusOnEnd) {
                focus.requestFocus()
            }
            focus.scheduleFocusUpdate()
        }
    }
}

@OptIn(InternalComposeUiApi::class)
internal class WasmJsTextInputSessionOwner(
    private val scene: () -> ComposeScene,
    private val density: () -> Density,
    private val container: HTMLElement,
    private val focus: WasmJsDomFocus,
) : TextInputSessionOwner {
    private val textInputSessionMutex = SessionMutex<PlatformTextInputSessionWasmJs>()

    internal val currentTextInputSession: WasmJsTextInputSession?
        get() = textInputSessionMutex.currentSession?.currentTextInputSession

    override suspend fun textInputSession(
        session: suspend PlatformTextInputSessionScope<*>.() -> Nothing
    ): Nothing {
        textInputSessionMutex.withSessionCancellingPrevious(
            sessionInitializer = {
                PlatformTextInputSessionWasmJs(
                    coroutineScope = it,
                    scene = scene(),
                    density = density,
                    container = container,
                    focus = focus,
                )
            },
            session,
        )
    }

    override fun isTextInputSessionActive(): Boolean {
        return currentTextInputSession != null
    }

    override fun handleEventWithInputSession(keyEvent: KeyEvent): Boolean {
        val activeTextInputSession = currentTextInputSession
        return if (activeTextInputSession != null && keyEvent.willProduceInputEvent()) {
            activeTextInputSession.currentKeyEventShouldBeHandled = true
            true
        } else {
            false
        }
    }

    internal fun dispose() {
        currentTextInputSession?.dispose()
    }
}

/**
 * Hosts the hidden text area the browser IME talks to and translates its composition and
 * `beforeinput` events into [PlatformTextInputMethodRequestWasmJs] calls. Every request call runs
 * inside the scene's frame transaction so that data-source reads are bound like on desktop.
 */
internal class WasmJsTextInputSession(
    private val request: PlatformTextInputMethodRequestWasmJs,
    private val scene: ComposeScene,
    private val density: () -> Density,
    private val container: HTMLElement,
) {
    var currentKeyEventShouldBeHandled = false

    private val compositionHandler: (Event) -> Unit = { event ->
        handleCompositionEvent(event)
        updateCaretPosition()
    }
    private val beforeInputHandler: (Event) -> Unit = { event ->
        handleInputEvent(event)
        updateCaretPosition()
    }

    internal val textArea: HTMLTextAreaElement =
        (document.createElement("textarea") as HTMLTextAreaElement).apply {
            configureTextArea(this)
            addEventListener("compositionstart", compositionHandler)
            addEventListener("compositionend", compositionHandler)
            addEventListener("beforeinput", beforeInputHandler)
            value = ""
        }

    init {
        container.appendChild(textArea)
        updateCaretPosition()
    }

    fun dispose() {
        textArea.removeEventListener("compositionstart", compositionHandler)
        textArea.removeEventListener("compositionend", compositionHandler)
        textArea.removeEventListener("beforeinput", beforeInputHandler)
        textArea.parentNode?.removeChild(textArea)
    }

    fun updateCaretPosition() {
        scene.withFrameTransaction {
            val caretRect = runCatching { request.caretRectInRoot() }.getOrNull()
                ?: return@withFrameTransaction
            if (caretRect == Rect.Zero) return@withFrameTransaction
            val scale = density().density.toDouble()
            val containerRect = container.getBoundingClientRect()
            val left = containerRect.left + caretRect.left.toDouble() / scale
            val top = containerRect.top + caretRect.top.toDouble() / scale
            textArea.style.left = "${left}px"
            textArea.style.top = "${top}px"
        }
    }

    private fun handleCompositionEvent(event: Event) {
        val data = eventDataOrEmpty(event)
        scene.withFrameTransaction {
            when (event.type) {
                "compositionstart" -> {
                    request.setComposingText(data)
                }
                "compositionend" -> {
                    if (data.isNotEmpty()) {
                        request.setComposingText(data)
                    }
                    request.finishComposingText()
                    textArea.value = ""
                }
            }
        }
    }

    private fun handleInputEvent(event: Event) {
        val inputType = eventInputType(event)
        val data = eventDataOrEmpty(event)
        scene.withFrameTransaction {
            when (inputType) {
                "deleteContentBackward" -> {
                    request.deleteContentBackward()
                    event.preventDefault()
                }
                "insertText" -> {
                    request.commitText(data)
                    event.preventDefault()
                }
                "insertCompositionText" -> {
                    request.setComposingText(data)
                    // No preventDefault here because it can't be cancelled anyway;
                    // See https://w3c.github.io/input-events/#interface-InputEvent-Attributes
                }
                "insertReplacementText" -> {
                    request.commitText(data)
                    event.preventDefault()
                }
                else -> {
                    // Cancel all unsupported inputs to keep the textarea empty (apart from
                    // composition)
                    event.preventDefault()
                }
            }
        }
    }

    private fun configureTextArea(textArea: HTMLTextAreaElement) {
        with(textArea) {
            // autocorrect="on" enables iOS keyboard suggestions (CMP-8807)
            setAttribute("autocorrect", "off")
            setAttribute("autocomplete", "off")
            setAttribute("autocapitalize", "off")
            spellcheck = false

            setAttribute("inputmode", "text")
            setAttribute("enterkeyhint", "enter")

            // Focusable but not in tab order
            tabIndex = -1

            style.apply {
                position = "fixed"
                setProperty("user-select", "none")
                setProperty("forced-color-adjust", "none")
                whiteSpace = "pre"
                setProperty("align-content", "center")
                left = "0px"
                top = "0px"
                width = "1px"
                height = "1em"
                padding = "0"
                color = "transparent"
                background = "transparent"
                setProperty("caret-color", "transparent")
                outline = "none"
                border = "none"
                setProperty("resize", "none")
                setProperty("text-shadow", "none")
                zIndex = "-1"
                // Don't use opacity:0 - Safari iOS keyboard overlaps text input (CMP-8611)
                // To prevent auto-zoom in some mobile browsers, we set a larger font-size
                fontSize = "20px"
            }
        }
    }
}

private fun KeyEvent.willProduceInputEvent(): Boolean {
    val nativeEvent = (nativeKeyEvent as? InternalKeyEvent)?.nativeEvent as? KeyboardEvent
        ?: return false
    if (nativeEvent.ctrlKey || nativeEvent.metaKey || nativeEvent.altKey) return false
    val key = nativeEvent.key
    // A single-character key produces text; "Process"/"Unidentified"/"Dead" are IME-owned.
    return key.length == 1 || key == "Process" || key == "Unidentified" || key == "Dead"
}

private fun eventInputType(event: Event): String = js("event.inputType || ''")

private fun eventDataOrEmpty(event: Event): String = js("event.data == null ? '' : event.data")
