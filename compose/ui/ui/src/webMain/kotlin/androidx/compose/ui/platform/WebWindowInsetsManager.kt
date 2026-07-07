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

@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.ui.platform

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlinx.browser.window
import org.w3c.dom.events.EventTarget

/**
 * Reads system window insets (safe area and IME) from the browser and exposes them as Compose
 * state.
 *
 * Safe area insets are read from CSS `env(safe-area-inset-*)` environment variables via CSS custom
 * properties, and re-read on each window resize event.
 *
 * IME (virtual keyboard) insets are tracked using:
 * - **VirtualKeyboard API** when available — the most precise source.
 * - **VisualViewport API** as a fallback for Safari and Firefox — derived from the difference
 *   between `window.innerHeight` and `visualViewport.height`.
 *
 */
@OptIn(InternalComposeUiApi::class)
internal class WebWindowInsetsManager(
    private val density: Density
) {

    val safeAreaInsets = mutableStateOf(PlatformInsets.Zero)
    val imeInsets = mutableStateOf(PlatformInsets.Zero)

    private val hasVirtualKeyboardApi: Boolean = hasVirtualKeyboard()

    private val safeAreaListener: EventTargetListener
    private val imeEventsListener: EventTargetListener?

    init {
        installSafeAreaCssProperties()
        safeAreaListener = initSafeAreaTracking()
        imeEventsListener = initImeTracking()
    }

    fun dispose() {
        safeAreaListener.dispose()
        imeEventsListener?.dispose()
    }


    private fun initSafeAreaTracking(): EventTargetListener {
        readAndUpdateSafeArea()
        return EventTargetListener(window).apply {
            addDisposableEvent("resize") { readAndUpdateSafeArea() }
        }
    }

    private fun initImeTracking(): EventTargetListener? {
        readAndUpdateIme()
        return if (hasVirtualKeyboardApi) {
            enableVirtualKeyboardOverlay()
            val vk = getVirtualKeyboard() ?: return null
            EventTargetListener(vk).apply {
                addDisposableEvent("geometrychange") { readAndUpdateIme() }
            }
        } else {
            val vv = getVisualViewport() ?: return null
            EventTargetListener(vv).apply {
                addDisposableEvent("resize") { readAndUpdateIme() }
                addDisposableEvent("scroll") { readAndUpdateIme() }
            }
        }
    }

    private fun readAndUpdateSafeArea() {
        safeAreaInsets.value = with(density) {
            PlatformInsets(
                left = readCssVarLeft().dp.roundToPx(),
                top = readCssVarTop().dp.roundToPx(),
                right = readCssVarRight().dp.roundToPx(),
                bottom = readCssVarBottom().dp.roundToPx()
            )
        }
    }

    private fun readAndUpdateIme() {
        val bottomCssPx = if (hasVirtualKeyboardApi) {
            readVirtualKeyboardHeight()
        } else {
            readVisualViewportImeHeight()
        }
        imeInsets.value = with(density) {
            PlatformInsets(bottom = bottomCssPx.dp.roundToPx())
        }
    }
}

/**
 * Installs CSS custom properties on `document.documentElement` that mirror `env(safe-area-inset-*)`.
 *
 * Setting them on the root element (rather than inside a canvas shadow root) works around a WebKit
 * bug where `env()` values return 0 in canvas-based shadow roots on some iOS versions.
 */
// language=js
private fun installSafeAreaCssProperties(): Unit = js(
    """(function() {
        let s = document.documentElement.style;
        s.setProperty('--cmp-safe-top',    'env(safe-area-inset-top,    0px)');
        s.setProperty('--cmp-safe-right',  'env(safe-area-inset-right,  0px)');
        s.setProperty('--cmp-safe-bottom', 'env(safe-area-inset-bottom, 0px)');
        s.setProperty('--cmp-safe-left',   'env(safe-area-inset-left,   0px)');
    })()"""
)

// language=js
private fun readCssVarTop(): Float =
    js("(parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--cmp-safe-top')) || 0)")

// language=js
private fun readCssVarRight(): Float =
    js("(parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--cmp-safe-right')) || 0)")

// language=js
private fun readCssVarBottom(): Float =
    js("(parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--cmp-safe-bottom')) || 0)")

// language=js
private fun readCssVarLeft(): Float =
    js("(parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--cmp-safe-left')) || 0)")

// language=js
private fun hasVirtualKeyboard(): Boolean = js("('virtualKeyboard' in navigator)")

// language=js
private fun enableVirtualKeyboardOverlay(): Unit =
    js("(navigator.virtualKeyboard.overlaysContent = true)")

// language=js
private fun getVirtualKeyboard(): EventTarget? = js("navigator.virtualKeyboard")

/** Returns the current keyboard height in CSS pixels (0 when keyboard is hidden). */
// language=js
private fun readVirtualKeyboardHeight(): Float =
    js("(navigator.virtualKeyboard.boundingRect.height || 0)")

// --- IME: VisualViewport API fallback (Safari, Firefox) ---

// language=js
private fun getVisualViewport(): EventTarget? = js("(window.visualViewport || null)")

/**
 * Estimates the IME height in CSS pixels from the visual viewport geometry.
 * Returns 0 when the keyboard is not visible.
 */
// language=js
private fun readVisualViewportImeHeight(): Float = js("""(function() {
    let vv = window.visualViewport;
    if (!vv) return 0;
    return Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
})()""")
