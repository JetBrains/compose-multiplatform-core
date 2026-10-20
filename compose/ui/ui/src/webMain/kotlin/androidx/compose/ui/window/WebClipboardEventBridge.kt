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

package androidx.compose.ui.window

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.internal.focusExt
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.skiko.hostOs
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

/**
 * Some browsers do not send clipboard events to a focused canvas.
 * See https://youtrack.jetbrains.com/issue/CMP-9508
 *
 * We briefly focus a hidden textarea so it can receive those events instead.
 * The active backing input is used as the event target when one exists.
 * Clipboard data is handled separately by rememberClipboardEventsHandler.
 */
internal class WebClipboardEventBridge(
    canvas: HTMLCanvasElement,
    private val layerRoot: HTMLElement,
    private val backingInputProvider: () -> HTMLElement?
) {
    private val clipTarget = clipTargetElement(canvas)

    fun getEventTarget(): HTMLElement = backingInputProvider() ?: clipTarget

    private val isMacOS = hostOs.isMacOS

    fun processKeyDown(keyEvent: KeyEvent) {
        val mod = if (isMacOS) keyEvent.isMetaPressed else keyEvent.isCtrlPressed
        if (!mod) return
        if (keyEvent.key == Key.C || keyEvent.key == Key.V || keyEvent.key == Key.X) {
            // A browser is about to dispatch a Clipboard Event.
            // Some browsers do not dispatch Clipboard events to <canvas> despite it having focus,
            // so let it dispatch the event to clipTarget (text area).
            // By focusing on it, we let a browser dispatch the event to it.
            layerRoot.appendChild(clipTarget)
            focusExt(clipTarget, true)
        }
    }
}

/**
 * The purpose of the clipTarget element is to briefly steal the focus to let the browser dispatch
 * ClipboardEvent to it. Then it returns the focus to the canvas.
 */
private fun clipTargetElement(canvas: HTMLCanvasElement): HTMLTextAreaElement {
    val clipTarget = (document.createElement("textarea") as HTMLTextAreaElement).apply {
        tabIndex = -1
        setAttribute("aria-hidden", "true")
        style.position = "fixed"
        style.left = "-1000px"
        style.top = "0"
        style.opacity = "0"
        style.width = "1px"
        style.height = "1px"
    }

    val clipEventListener: (Event) -> Unit = { _ ->
        window.requestAnimationFrame {
            focusExt(canvas, true)
            clipTarget.remove()
        }
    }

    // Here just return the focus to canvas.
    // For the actual event handling see rememberClipboardEventsHandler implementations.
    clipTarget.addEventListener("copy", clipEventListener)
    clipTarget.addEventListener("cut", clipEventListener)
    clipTarget.addEventListener("paste", clipEventListener)

    return clipTarget
}

