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

import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.OPEN
import org.w3c.dom.ShadowRoot
import org.w3c.dom.ShadowRootInit
import org.w3c.dom.ShadowRootMode

/**
 * A `<div>` wrapping a shadow root that hosts a `<canvas>` (plus its a11y container and backing
 * text-input field). The shadow boundary isolates Compose's own canvas rendering from the
 * embedding page's global CSS (a page-level `canvas { ... }` rule, a CSS reset, a `user-select`
 * override, etc. can't reach in) — every Compose-owned canvas needs this, not just the main
 * window's, so [WebComposeSceneLayer] uses this too instead of appending its canvas directly into
 * the light DOM.
 *
 * @property host append this into the surrounding (light DOM) container.
 * @property shadowRoot the shadow root itself, needed as the root [org.w3c.dom.Node] for
 * anything that must operate within this specific shadow tree (e.g. drag-and-drop wiring).
 * @property appContainer canvas / a11y container / backing input field all belong inside this —
 * the shadow-root-internal counterpart of [host].
 */
internal data class ShadowedAppContainer(
    val host: HTMLDivElement,
    val shadowRoot: ShadowRoot,
    val appContainer: HTMLElement,
)

internal fun createShadowedAppContainer(): ShadowedAppContainer {
    val host = document.createElement("div") as HTMLDivElement
    host.style.position = "relative"

    val shadowRoot = host.attachShadow(ShadowRootInit(ShadowRootMode.OPEN))
    val shadowRootStyle = document.createElement("style")

    // don't style backing .compose-backing-field with opacity, see https://youtrack.jetbrains.com/projects/CMP/issues/CMP-8611
    shadowRootStyle.textContent = """
        :host {
            -webkit-touch-callout: none;
            -webkit-user-select: none;
            user-select: none;

            position: relative;
            padding: 0;
        }

        canvas {
               display: block;
               width: 100%;
               height: 100%;
        }

       .compose-backing-field {
            position: absolute;
            height: calc(var(--compose-internal-web-backing-input-height) * 1px);
            width: calc(var(--compose-internal-web-backing-input-width) * 1px);
            left: min(var(--compose-internal-web-backing-input-left) * 1px, 100vw - var(--compose-internal-web-backing-input-width) * 1px);
            top: min(var(--compose-internal-web-backing-input-top) * 1px, 100vh - var(--compose-internal-web-backing-input-height) * 1px);

            align-content: center;
            background: transparent;
            border: none;
            caret-color: transparent;
            color: transparent;
            font-size: 20px;
            forced-color-adjust: none;
            outline: none;
            padding: 0;
            resize: none;
            text-shadow: none;
            user-select: none;
            white-space: pre;
            z-index: -1;
       }
    """.trimIndent()
    shadowRoot.appendChild(shadowRootStyle)

    val appContainer = document.createElement("div") as HTMLElement
    appContainer.style.position = "relative"
    shadowRoot.appendChild(appContainer)

    return ShadowedAppContainer(host, shadowRoot, appContainer)
}
