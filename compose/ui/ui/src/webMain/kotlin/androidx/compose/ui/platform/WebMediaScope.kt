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

@file:OptIn(ExperimentalMediaQueryApi::class, ExperimentalWasmJsInterop::class)

package androidx.compose.ui.platform

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.UiMediaScope.FoldOrientation
import androidx.compose.ui.UiMediaScope.FoldState
import androidx.compose.ui.UiMediaScope.KeyboardKind
import androidx.compose.ui.UiMediaScope.PointerPrecision
import androidx.compose.ui.UiMediaScope.ViewingDistance
import androidx.compose.ui.UiMediaScope.WindowFold
import androidx.compose.ui.UiMediaScope.WindowPosture
import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.MediaQueryListener
import androidx.compose.ui.window.MediaQueryStatus
import androidx.compose.ui.window.documentIsVisible
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlinx.browser.document
import org.w3c.dom.events.EventTarget

/** Browser device capabilities, observed for the lifetime of a Compose window. */
internal class WebMediaScope(private val windowInfo: WindowInfo) : UiMediaScope {
    private val finePointer = MediaFeature("(any-pointer: fine)")
    private val coarsePointer = MediaFeature("(any-pointer: coarse)")
    private val horizontalSegments = MediaFeature("(horizontal-viewport-segments: 2)")
    private val verticalSegments = MediaFeature("(vertical-viewport-segments: 2)")
    private val folded = MediaFeature("(device-posture: folded)")

    override val windowWidth: Dp
        get() = windowInfo.containerDpSize.width

    override val windowHeight: Dp
        get() = windowInfo.containerDpSize.height

    override val windowPosture: WindowPosture by derivedStateOf {
        // Segments describe the layout, not the hinge angle: a segmented screen can also be flat.
        val state = if (folded.value) FoldState.HalfOpened else FoldState.Flat
        WindowPosture(
            buildList {
                if (horizontalSegments.value) {
                    add(WindowFold(state, FoldOrientation.Vertical))
                }
                if (verticalSegments.value) {
                    add(WindowFold(state, FoldOrientation.Horizontal))
                }
            }
        )
    }

    override val pointerPrecision: PointerPrecision
        get() =
            when {
                finePointer.value -> PointerPrecision.Fine
                coarsePointer.value -> PointerPrecision.Coarse
                else -> PointerPrecision.None
            }

    private val desktopKeyboard = hasDesktopUserAgent()
    private var virtualKeyboardVisible by mutableStateOf(isVirtualKeyboardVisible())

    override val keyboardKind: KeyboardKind
        get() =
            when {
                virtualKeyboardVisible -> KeyboardKind.Virtual
                desktopKeyboard -> KeyboardKind.Physical
                else -> KeyboardKind.None
            }

    override var hasMicrophone: Boolean by mutableStateOf(false)
        private set

    override var hasCamera: Boolean by mutableStateOf(false)
        private set

    override val viewingDistance: ViewingDistance
        get() = ViewingDistance.Near

    private var disposed = false
    private var enumerationGeneration = 0
    private val mediaDevices = getMediaDevices()

    private val keyboardEvents =
        getVirtualKeyboard()?.let { keyboard ->
            EventTargetListener(keyboard).apply {
                addDisposableEvent("geometrychange") {
                    virtualKeyboardVisible = isVirtualKeyboardVisible()
                }
            }
        }

    private val deviceEvents =
        mediaDevices?.let { devices ->
            EventTargetListener(devices).apply {
                addDisposableEvent("devicechange") { updateMediaDevices() }
            }
        }

    private val documentEvents =
        mediaDevices?.let {
            EventTargetListener(document).apply {
                addDisposableEvent("visibilitychange") {
                    if (documentIsVisible()) updateMediaDevices()
                }
            }
        }

    init {
        updateMediaDevices()
    }

    private fun updateMediaDevices() {
        val devices = mediaDevices ?: return
        val generation = ++enumerationGeneration
        enumerateMediaDevices(devices) { microphone, camera ->
            if (!disposed && generation == enumerationGeneration) {
                Snapshot.withMutableSnapshot {
                    hasMicrophone = microphone
                    hasCamera = camera
                }
            }
        }
    }

    fun dispose() {
        disposed = true
        finePointer.dispose()
        coarsePointer.dispose()
        horizontalSegments.dispose()
        verticalSegments.dispose()
        folded.dispose()
        keyboardEvents?.dispose()
        deviceEvents?.dispose()
        documentEvents?.dispose()
    }
}

private class MediaFeature(query: String) : MediaQueryListener(query) {
    var value: Boolean by mutableStateOf(matches() == MediaQueryStatus.MATCH)
        private set

    override fun onChange(matches: Boolean) {
        value = matches
    }
}

// language=js
private fun hasDesktopUserAgent(): Boolean =
    js(
        """(function() {
    const ua = navigator.userAgent;
    const isIPad = navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1;
    return !isIPad && !/Android|iPhone|iPad|iPod|Mobile/i.test(ua) &&
        /Windows|Macintosh|Mac OS X|X11|Linux|CrOS/i.test(ua);
})()"""
    )

// language=js
private fun getVirtualKeyboard(): EventTarget? = js("(navigator.virtualKeyboard || null)")

// language=js
private fun isVirtualKeyboardVisible(): Boolean =
    js(
        """Boolean(
    navigator.virtualKeyboard && navigator.virtualKeyboard.boundingRect.height > 0
)"""
    )

// language=js
private fun getMediaDevices(): EventTarget? =
    js(
        """(
    navigator.mediaDevices && typeof navigator.mediaDevices.enumerateDevices === 'function'
        ? navigator.mediaDevices : null
)"""
    )

// enumerateDevices does not request capture permission. Before permission is granted, browsers
// may expose only one input of each kind, with empty labels and identifiers; only kind is needed.
// language=js
private fun enumerateMediaDevices(
    devices: EventTarget,
    onResult: (Boolean, Boolean) -> Unit,
): Unit =
    js(
        """(function() {
    try {
        devices.enumerateDevices().then(function(inputs) {
            onResult(
                inputs.some(function(input) { return input.kind === 'audioinput'; }),
                inputs.some(function(input) { return input.kind === 'videoinput'; })
            );
        }, function() { onResult(false, false); });
    } catch (error) {
        onResult(false, false);
    }
})()"""
    )
