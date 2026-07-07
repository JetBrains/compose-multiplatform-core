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

/**
 * Reads system window insets (safe area) from the browser via CSS custom properties and
 * exposes them as Compose state.
 */
@OptIn(InternalComposeUiApi::class)
internal class WebWindowInsetsManager(
    private val density: Density,
    globalEvents: EventTargetListener
) {

    val safeAreaInsets = mutableStateOf(PlatformInsets.Zero)

    init {
        installSafeAreaCssProperties()
        readAndUpdate()
        globalEvents.addDisposableEvent("resize") {
            readAndUpdate()
        }
    }

    private fun readAndUpdate() {
        val top = readCssVarTop()
        val right = readCssVarRight()
        val bottom = readCssVarBottom()
        val left = readCssVarLeft()
        safeAreaInsets.value = with(density) {
            PlatformInsets(
                left = left.dp.roundToPx(),
                top = top.dp.roundToPx(),
                right = right.dp.roundToPx(),
                bottom = bottom.dp.roundToPx()
            )
        }
    }
}

/**
 * Installs CSS custom properties on `document.documentElement` that mirror the browser's
 * `env(safe-area-inset-*)` environment variables.
 *
 * Setting them on the root element (rather than on a canvas or shadow root) works around a WebKit
 * bug where `env()` values return 0 inside canvas-based shadow roots on some iOS versions.
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
