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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.events.EventTargetListener
import kotlin.js.JsAny
import kotlin.js.js
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.MediaQueryList
import org.w3c.dom.Node

/** Preferences belong to the window, independently of whether its semantics mirror is enabled. */
internal class WebPreferencesObserver(
    rootNode: Node,
    private val fontSizeProbe: WebFontSizeProbe = DomFontSizeProbe(rootNode),
    reducedMotionQuery: MediaQueryList? = matchMediaOrNull("(prefers-reduced-motion: reduce)")
) {
    private var isDisposed = false
    private var isStarted = false
    private var onFontScaleChanged: ((Float) -> Unit)? = null

    val motionDurationScale = WebMotionDurationScale()

    var fontScale: Float = 1f
        private set

    private val reducedMotionListener = object : MediaQueryListener(
        "(prefers-reduced-motion: reduce)", reducedMotionQuery
    ) {
        override fun onChange(matches: Boolean) {
            if (!isDisposed) motionDurationScale.update(matches)
        }
    }

    init {
        // An unsupported feature does not match `reduce`, even when matchMedia itself exists.
        // Both unsupported and no-preference intentionally retain normal motion.
        motionDurationScale.update(reducedMotionListener.matches() == MediaQueryStatus.MATCH)
        readFontScale()
    }

    /** Called only after the scene has been constructed; observation may call back synchronously. */
    fun start(onFontScaleChanged: (Float) -> Unit) {
        if (isDisposed) return
        check(!isStarted) { "Web preferences are already being observed" }
        isStarted = true
        this.onFontScaleChanged = onFontScaleChanged
        fontSizeProbe.observe(::readFontScale)
        // Close the gap between the initial measurement and starting observation.
        readFontScale()
    }

    private fun readFontScale() {
        if (isDisposed) return
        val fontSize = fontSizeProbe.readFontSize() ?: return
        if (!fontSize.isFinite() || fontSize <= 0f) return
        // 16 CSS px is the compatibility baseline, NOT the first observed size: preferences
        // already active at startup must be honored. This ratio is independent of DPR and zoom.
        val newScale = fontSize / 16f
        if (!newScale.isFinite() || newScale <= 0f || newScale == fontScale) return
        fontScale = newScale
        onFontScaleChanged?.invoke(newScale)
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        onFontScaleChanged = null
        reducedMotionListener.dispose()
        fontSizeProbe.dispose()
    }
}

/** Snapshot-backed so InfiniteTransition's snapshotFlow can resume after a zero scale. */
internal class WebMotionDurationScale : MotionDurationScale {
    override var scaleFactor: Float by mutableFloatStateOf(1f)
        private set

    fun update(reduceMotion: Boolean) {
        scaleFactor = if (reduceMotion) 0f else 1f
    }
}

/** A small per-window seam for deterministic tests, without patching browser globals. */
internal interface WebFontSizeProbe {
    fun readFontSize(): Float?
    fun observe(onChange: () -> Unit)
    fun dispose()
}

/** Measures the browser's default font, deliberately not the host's rem/inherited font size. */
internal class DomFontSizeProbe(
    rootNode: Node,
    private val observeResize: (Element, () -> Unit) -> (() -> Unit)? = ::observeFontSize,
    private val isDocumentVisible: () -> Boolean = ::documentIsVisible
) : WebFontSizeProbe {
    private var isDisposed = false
    private var stopObservingResize: (() -> Unit)? = null
    private val windowEvents = EventTargetListener(window)
    private val documentEvents = EventTargetListener(document)

    // Clipping a zero-sized wrapper prevents even a very large font in a tiny viewport from
    // enlarging the host's scroll area. No size containment is applied to the measured element.
    private val clip = (document.createElement("div") as HTMLElement).apply {
        setAttribute("aria-hidden", "true")
        style.apply {
            position = "absolute"
            left = "0"
            top = "0"
            width = "0"
            height = "0"
            setProperty("overflow", "hidden")
            visibility = "hidden"
            setProperty("pointer-events", "none")
        }
    }
    private val probe = (document.createElement("div") as HTMLElement).apply {
        setAttribute("aria-hidden", "true")
        setAttribute("data-compose-font-size-probe", "")
        style.apply {
            position = "absolute"
            left = "0"
            top = "0"
            font = "medium sans-serif"
            width = "1em"
            height = "0"
            visibility = "hidden"
            setProperty("pointer-events", "none")
        }
    }

    init {
        clip.appendChild(probe)
        rootNode.appendChild(clip)
    }

    override fun readFontSize(): Float? {
        if (isDisposed) return null
        return window.getComputedStyle(probe).fontSize.removeSuffix("px").toFloatOrNull()
    }

    override fun observe(onChange: () -> Unit) {
        if (isDisposed) return
        val refresh = { if (!isDisposed) onChange() }
        stopObservingResize = observeResize(probe, refresh)
        // Some engines may only apply changed browser preferences on returning to the page.
        windowEvents.addDisposableEvent("focus") { refresh() }
        documentEvents.addDisposableEvent("visibilitychange") {
            if (isDocumentVisible()) refresh()
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        stopObservingResize?.invoke()
        stopObservingResize = null
        windowEvents.dispose()
        documentEvents.dispose()
        clip.parentNode?.removeChild(clip)
    }
}

private fun observeFontSize(element: Element, onChange: () -> Unit): (() -> Unit)? {
    val observer = createFontResizeObserver(onChange) ?: return null
    observer.observe(element)
    return { observer.disconnect() }
}

private external interface FontResizeObserver : JsAny {
    fun observe(element: Element)
    fun disconnect()
}

// Missing ResizeObserver retains the initial measurement and focus/visibility refreshes.
private fun createFontResizeObserver(onChange: () -> Unit): FontResizeObserver? =
    js("typeof ResizeObserver === 'function' ? new ResizeObserver(onChange) : null")
