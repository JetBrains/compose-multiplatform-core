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

import androidx.compose.ui.internal.focusExt
import kotlin.js.js
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.Node
import org.w3c.dom.events.Event

//https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilityState
internal fun documentIsVisible(): Boolean = js("document.visibilityState === 'visible'")

internal fun Element.isFocused(): Boolean {
    val activeElement = when {
        document.activeElement?.shadowRoot != null -> (document.activeElement?.shadowRoot as? ShadowRootExt)?.activeElement
        else -> document.activeElement
    }

    if (activeElement == null) {
        return false
    }

    return activeElement == this
}

internal fun Node.hasActiveElement(): Boolean =
    (this as ShadowRootExt).activeElement != null

// language=js
internal fun checkViewportFitCover(): Unit = js(
    """(function() {
        let meta = document.querySelector('meta[name=viewport]');
        let content = meta ? (meta.getAttribute('content') || '') : '';
        if (!content.includes('viewport-fit=cover')) {
            console.warn(
                "[ComposeWeb] enableBrowserWindowInsets is set to true, but " +
                "'viewport-fit=cover' is not found in the viewport meta tag. " +
                "Safe area insets will be zero. Add viewport-fit=cover to your viewport meta tag: " +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
            );
        }
    })()"""
)

/**
 * The purpose of the clipTarget element is to briefly steal the focus to let the browser dispatch
 * ClipboardEvent to it. Then it returns the focus to the canvas.
 */
internal fun clipTargetElement(canvas: HTMLCanvasElement): HTMLTextAreaElement {
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

private external interface ShadowRootExt {
    val activeElement: Element?
}
