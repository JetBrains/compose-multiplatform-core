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

package androidx.compose.ui.platform

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/** Supplies safe-area CSS values without replacing browser APIs. */
internal class WebInsetsTestEnvironment {
    private val style = (document.documentElement as HTMLElement).style
    private val properties = listOf(
        "--cmp-safe-left", "--cmp-safe-top", "--cmp-safe-right", "--cmp-safe-bottom"
    )
    private val originalValues = properties.map {
        style.getPropertyValue(it) to style.getPropertyPriority(it)
    }

    // The manager installs env() expressions in its constructor. Set test values afterwards.
    fun setSafeArea(
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 0f,
        bottom: Float = 0f
    ) {
        properties.zip(listOf(left, top, right, bottom)).forEach { (property, value) ->
            style.setProperty(property, "${value}px")
        }
    }

    fun restore() {
        properties.zip(originalValues).forEach { (property, original) ->
            if (original.first.isEmpty()) {
                style.removeProperty(property)
            } else {
                style.setProperty(property, original.first, original.second)
            }
        }
    }

    /** Only this detached element has synthetic geometry; the browser viewport is unchanged. */
    @OptIn(ExperimentalWasmJsInterop::class)
    fun canvas(
        left: Float = 0f,
        top: Float = 0f,
        right: Float = window.innerWidth.toFloat(),
        bottom: Float = window.innerHeight.toFloat()
    ): Element = createCanvas(left, top, right, bottom)
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun createCanvas(left: Float, top: Float, right: Float, bottom: Float): Element = js(
    """(function() {
        const canvas = document.createElement('canvas');
        canvas.getBoundingClientRect = function() {
            return new DOMRect(left, top, right - left, bottom - top);
        };
        return canvas;
    })()"""
)
