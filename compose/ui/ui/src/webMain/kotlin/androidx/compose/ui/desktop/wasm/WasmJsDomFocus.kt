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

import androidx.compose.runtime.mutableStateOf
import kotlin.js.js
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.FocusEvent

/**
 * Tracks DOM focus for a canvas-hosted window. The window is considered focused while the page is
 * visible, the document has focus, and the DOM focus is on either the canvas or the IME text area
 * ([setTextArea]); blur is settled one animation frame later so that a focus hop between the two
 * elements never reads as a window blur.
 */
internal class WasmJsDomFocus(
    private val canvas: HTMLCanvasElement,
) {
    var onFocusChanged: (Boolean) -> Unit = {}

    private val focusedState = mutableStateOf(false)
    val isFocused: Boolean get() = focusedState.value

    private var textArea: HTMLTextAreaElement? = null
    private var pendingFocusUpdate: Int? = null

    private val focusHandler: (Event) -> Unit = { event -> handleFocusEvent(event) }
    private val updateFocusStateHandler: (Event) -> Unit = { updateFocusState() }

    init {
        canvas.addEventListener("focus", focusHandler)
        canvas.addEventListener("blur", focusHandler)
        window.addEventListener("focus", updateFocusStateHandler)
        window.addEventListener("blur", updateFocusStateHandler)
        document.addEventListener("visibilitychange", updateFocusStateHandler)
    }

    fun requestFocus() {
        val activeElement = document.activeElement
        textArea?.let {
            if (it !== activeElement) {
                it.focus()
            }
        } ?: canvas.takeIf { it !== activeElement }?.focus()
    }

    fun setTextArea(value: HTMLTextAreaElement?) {
        if (textArea === value) return
        textArea?.let {
            it.removeEventListener("focus", focusHandler)
            it.removeEventListener("blur", focusHandler)
        }
        textArea = value
        value?.let {
            it.addEventListener("focus", focusHandler)
            it.addEventListener("blur", focusHandler)
        }
    }

    fun hasDomFocus(): Boolean {
        val activeElement = document.activeElement
        return activeElement === canvas || (textArea != null && activeElement === textArea)
    }

    fun isTextAreaFocused(): Boolean = textArea != null && document.activeElement === textArea

    fun updateFocusState() {
        val focused = documentIsVisible() && document.hasFocus() && hasDomFocus()
        if (focusedState.value != focused) {
            focusedState.value = focused
            onFocusChanged(focused)
        }
    }

    fun scheduleFocusUpdate() {
        cancelPendingFocusUpdate()
        pendingFocusUpdate = window.requestAnimationFrame {
            pendingFocusUpdate = null
            updateFocusState()
        }
    }

    fun dispose() {
        cancelPendingFocusUpdate()
        setTextArea(null)
        canvas.removeEventListener("focus", focusHandler)
        canvas.removeEventListener("blur", focusHandler)
        window.removeEventListener("focus", updateFocusStateHandler)
        window.removeEventListener("blur", updateFocusStateHandler)
        document.removeEventListener("visibilitychange", updateFocusStateHandler)
    }

    private fun handleFocusEvent(event: Event) {
        val isBlur = event.type == "blur"
        val isFocus = event.type == "focus"
        if (!isBlur && !isFocus) return

        val relatedTarget = (event as? FocusEvent)?.relatedTarget
        val isInternalFocusSwitch = relatedTarget != null &&
            (relatedTarget === canvas || (textArea != null && relatedTarget === textArea))
        when {
            isFocus -> {
                if (isInternalFocusSwitch) {
                    cancelPendingFocusUpdate()
                }
                updateFocusState()
            }
            isBlur -> if (!isInternalFocusSwitch) {
                scheduleFocusUpdate()
            }
        }
    }

    private fun cancelPendingFocusUpdate() {
        pendingFocusUpdate?.let {
            window.cancelAnimationFrame(it)
            pendingFocusUpdate = null
        }
    }
}

// https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilityState
private fun documentIsVisible(): Boolean = js("document.visibilityState === 'visible'")
