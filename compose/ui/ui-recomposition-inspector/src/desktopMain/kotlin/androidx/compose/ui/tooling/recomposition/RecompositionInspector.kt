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

package androidx.compose.ui.tooling.recomposition

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.observe
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.desktop.Application
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowInspection
import androidx.compose.ui.desktop.WindowOverlayRenderer
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.tooling.recomposition.RecompositionInspector.Companion.of
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Records and visualizes the recompositions of one desktop [Window]: which scopes ran in each
 * frame, why, and what loops. Obtain one with [of] and show it with `openInspectorWindow`.
 * Names, locations and bounds need the window content wrapped in [RecompositionInspectable].
 */
@OptIn(InternalComposeUiApi::class, ExperimentalComposeRuntimeApi::class)
class RecompositionInspector private constructor(
    val window: Window,
    private val windowInspection: WindowInspection,
    /** The application owning [window]. */
    val application: Application,
) : AutoCloseable {

    val settings = InspectorSettings()

    private val _snapshot = MutableStateFlow(InspectorSnapshot.Empty)

    /** Latest recorded state; updated once per host frame at most. */
    val snapshot: StateFlow<InspectorSnapshot>
        get() = _snapshot

    private val recorder = RecompositionRecorder(settings) { _snapshot.value = it }

    /**
     * Runs once per rendered frame, after the scene drew: closes the frame that just composed,
     * opens the next and draws the overlay from the fresh snapshot.
     */
    private val frameTick = WindowOverlayRenderer { canvas: Canvas, density: Density ->
        recorder.onFrameEnd()
        recorder.onFrameStart()
        if (overlayVisibleState.value) overlay.onRenderOverlay(canvas, density)
    }

    private var registration: CompositionObserverHandle? = null
    private var frameTickRegistration: AutoCloseable? = null

    /** Whether the recorder is attached to the window's recomposer. */
    var isStarted: Boolean by mutableStateOf(false)
        private set

    private val overlayVisibleState = mutableStateOf(false)

    /** Flashes recomposed nodes over the window content. Drawn only while [isStarted]. */
    var isOverlayVisible: Boolean
        get() = overlayVisibleState.value
        set(value) {
            if (overlayVisibleState.value == value) return
            overlayVisibleState.value = value
            windowInspection.requestRedraw()
        }

    /** The overlay renderer; its fade length and depth filter are adjustable. */
    val overlay = RecompositionOverlay(this) { windowInspection.requestRedraw() }

    fun start() {
        if (isStarted) return
        isStarted = true
        recorder.onFrameStart()
        registration = windowInspection.frameRecomposer.recomposer.observe(recorder)
        frameTickRegistration = windowInspection.addOverlayRenderer(frameTick)
    }

    private fun stop() {
        if (!isStarted) return
        isStarted = false
        registration?.dispose()
        registration = null
        frameTickRegistration?.close()
        frameTickRegistration = null
        recorder.dispose()
        _snapshot.value = recorder.snapshotNow()
    }

    /** Pauses recording without detaching; counters keep their values. */
    fun pause() {
        recorder.pause()
        _snapshot.value = recorder.snapshotNow()
    }

    fun resume() {
        recorder.resume()
        _snapshot.value = recorder.snapshotNow()
    }

    /** Resets counters and findings, keeps the scope registry. */
    fun clear() {
        recorder.clear()
        _snapshot.value = recorder.snapshotNow()
    }

    /** Excludes [scope] and everything composed under it from recording. */
    fun excludeScope(scope: RecomposeScope) = recorder.excludeScope(scope)

    override fun close() {
        isOverlayVisible = false
        stop()
        synchronized(inspectors) { inspectors.remove(window) }
    }

    companion object {
        private val inspectors = HashMap<Window, RecompositionInspector>()

        /** The host's recomposer. */
        internal val FrameRecomposer.recomposer: Recomposer
            get() = compositionContext as Recomposer

        /**
         * The inspector of [window], created on first use, or `null` when the window's
         * implementation does not expose its recomposer.
         */
        fun of(window: Window, application: Application = Application.current): RecompositionInspector? {
            // An inspector window is never inspected itself; it resolves to the inspector that owns it.
            ownerOfInspectorWindow(window)?.let { return it }
            synchronized(inspectors) {
                inspectors[window]?.let { return it }
                val inspection = window.inspection ?: return null
                return RecompositionInspector(window, inspection, application).also { inspectors[window] = it }
            }
        }

        /** The inspector of [window] if one was created with [of]. */
        fun existing(window: Window): RecompositionInspector? =
            ownerOfInspectorWindow(window) ?: synchronized(inspectors) { inspectors[window] }
    }
}
