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

package androidx.compose.ui.window

import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.unit.IntSize
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.js
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.events.EventTarget

internal interface ComposeWindowState {
    fun init() {}
    fun currentSize(): IntSize
    fun observeSizeAndScaleChanges(listener: () -> Unit): () -> Unit

    val globalEvents: EventTargetListener

    fun dispose() {
        globalEvents.dispose()
    }
}

internal class DefaultWindowState(private val viewportContainer: Element) : ComposeWindowState {
    private var sizeChangeListener: (() -> Unit)? = null

    override val globalEvents = EventTargetListener(window)

    private var mediaQueryListener: MediaQueryListener? = null

    private var viewportTargetListener: EventTargetListener? = null
    private var viewportContainerResizeObserver: ResizeObserver? = null

    override fun init() {
        val resizeListener: () -> Unit = {
            sizeChangeListener?.invoke()
        }

        // Observe the actual container size:
        // iOS Safari can change it after a viewport resize without firing another resize event,
        // leaving the canvas buffer out of sync with its CSS size.
        viewportContainerResizeObserver = ResizeObserver(resizeListener)
            .apply { observe(viewportContainer) }

        viewportTargetListener = getVisualViewport()?.let { EventTargetListener(it) }
        // Unlike resize on window, this one is also trigerred when visualViewport.scale is changed,
        // so on pinch-to-zoom too:
        viewportTargetListener?.addDisposableEvent("resize") { resizeListener() }

        recreateMediaQueryListener()
    }

    override fun currentSize(): IntSize {
        return IntSize(viewportContainer.clientWidth, viewportContainer.clientHeight)
    }

    override fun observeSizeAndScaleChanges(listener: () -> Unit): () -> Unit {
        check(sizeChangeListener == null) { "A size listener is already registered" }
        sizeChangeListener = listener
        return {
            if (sizeChangeListener === listener) {
                sizeChangeListener = null
            }
        }
    }

    private fun recreateMediaQueryListener() {
        mediaQueryListener?.dispose()
        val contentScale = actualDensity
        mediaQueryListener = object : MediaQueryListener("(resolution: ${contentScale}dppx)") {
            override fun onChange(matches: Boolean) {
                if (!matches) {
                    sizeChangeListener?.invoke()
                }
                recreateMediaQueryListener()
            }
        }
    }

    override fun dispose() {
        sizeChangeListener = null
        viewportContainerResizeObserver?.disconnect()
        viewportTargetListener?.dispose()
        mediaQueryListener?.dispose()
        super.dispose()
    }
}

private external class ResizeObserver(callback: () -> Unit) : JsAny {
    fun observe(target: Element)
    fun disconnect()
}

internal val actualDensity: Double
    get() = window.devicePixelRatio

//language=js
internal fun getVisualViewportScale(): Float =
    js("window.visualViewport.scale")

// https://developer.mozilla.org/en-US/docs/Web/API/Window/visualViewport
// Widely available
private fun getVisualViewport(): EventTarget =
    js("window.visualViewport")
