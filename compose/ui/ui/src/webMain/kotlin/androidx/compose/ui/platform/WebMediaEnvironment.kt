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

@file:OptIn(ExperimentalMediaQueryApi::class)

package androidx.compose.ui.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastForEach
import kotlin.js.Promise
import kotlinx.browser.window
import org.jetbrains.skiko.SystemTheme
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.MediaQueryList
import org.w3c.dom.MediaQueryListEvent
import org.w3c.dom.events.Event
import org.w3c.dom.mediacapture.AUDIOINPUT
import org.w3c.dom.mediacapture.MediaDeviceInfo
import org.w3c.dom.mediacapture.MediaDeviceKind
import org.w3c.dom.mediacapture.VIDEOINPUT

internal class WebMediaEnvironment(
    val windowInfo: WindowInfo,
    val onDensityChanged: (Density) -> Unit
) : PlatformMediaEnvironment {

    private var isDisposed = false

    //Microphone And Camera detection Api supported
    private val isMediaDevicesEnumerateSupported: Boolean =
        isSecureContext && isMediaDevicesEnumerateSupported()

    private val isMediaQuerySupported: Boolean = isMatchMediaSupported()

    //<editor-fold desc="System Theme Media Query">
    private val themeMediaQuery: MediaQueryList by lazy(LazyThreadSafetyMode.NONE) {
        window.matchMedia("(prefers-color-scheme: dark)")
    }

    private val themeListenerCallback: (Event) -> Unit = { event ->
        _currentSystemTheme = if ((event as MediaQueryListEvent).matches)
            SystemTheme.DARK else SystemTheme.LIGHT
    }

    private var _currentSystemTheme by mutableStateOf(
        when {
            !isMediaQuerySupported -> SystemTheme.UNKNOWN
            themeMediaQuery.matches -> SystemTheme.DARK
            else -> SystemTheme.LIGHT
        }
    )

    //</editor-fold>
    //<editor-fold desc="Resolution Media Query">
    private fun initializeResolutionMediaQuery() {
        if (isDisposed) return
        val contentScale = window.devicePixelRatio
        currentResolutionMediaQuery = window.matchMedia("(resolution: ${contentScale}dppx)")
        try {
            currentResolutionMediaQuery?.addEventListener(
                "change",
                resolutionListenerCallback,
                resolutionListenerOptions
            )
        } catch (t: Throwable) {
            currentResolutionMediaQuery?.addListener(resolutionListenerCallback)
        }
    }

    private var currentResolutionMediaQuery: MediaQueryList? = null
    private val resolutionListenerOptions = AddEventListenerOptions(capture = true, once = true)
    private val resolutionListenerCallback: (Event) -> Unit = { evt ->
        if (!isDisposed) {
            evt as MediaQueryListEvent
            if (!evt.matches) {
                val density = Density(window.devicePixelRatio.toFloat())
                onDensityChanged(density)
                _systemDensity = density
            }
            initializeResolutionMediaQuery()
        }
    }

    //</editor-fold>
    //<editor-fold desc="Orientation Media Query">
    private val orientationMediaQuery: MediaQueryList by lazy(LazyThreadSafetyMode.NONE) {
        window.matchMedia("(orientation: portrait)")
    }

    private val orientationListenerCallback: (Event) -> Unit = { event ->
        isOrientationPortrait = (event as MediaQueryListEvent).matches
    }

    private var isOrientationPortrait by mutableStateOf(orientationMediaQuery.matches)

    //</editor-fold>
    //<editor-fold desc="Device Posture Media Query">
    private var _windowPosture by mutableStateOf(UiMediaScope.Posture.Flat)

    private val devicePostureEventCallback: (Event) -> Unit = {
        _windowPosture = getDevicePosture()
    }

    private fun getDevicePosture(): UiMediaScope.Posture {
        val postureType = getDevicePostureType()
        val isPortrait = Snapshot.withoutReadObservation { isOrientationPortrait }
        return when (postureType) {
            1 -> if (isPortrait) UiMediaScope.Posture.Book else UiMediaScope.Posture.Tabletop
            else -> UiMediaScope.Posture.Flat
        }

    }

    private val isDevicePostureSupported = isDevicePostureApiSupported()

    //</editor-fold>
    private var _systemDensity by mutableStateOf(Density(window.devicePixelRatio.toFloat()))

    //TODO Hookup to virtualKeyboard.isShowing after https://github.com/JetBrains/compose-multiplatform-core/pull/3202 is merged
    private var isImeVisible by mutableStateOf(false)
    private var hasPhysicalKeyboard by mutableStateOf(true)
    private var _pointerPrecision by mutableStateOf(UiMediaScope.PointerPrecision.None)

    //<editor-fold desc="Microphone And Camera Detection">
    private var _hasMicrophone by mutableStateOf(false)

    private var _hasCamera by mutableStateOf(false)

    private fun initializeMediaDevicesInfo() {
        enumerateMediaDevices().then { extDevices ->
            var hasMic = false
            var hasCam = false
            val devices = extDevices.toList()
            devices.fastForEach { device ->
                when (device.kind) {
                    MediaDeviceKind.AUDIOINPUT -> hasMic = true
                    MediaDeviceKind.VIDEOINPUT -> hasCam = true
                }
            }
            _hasMicrophone = hasMic
            _hasCamera = hasCam
            extDevices
        }.catch {
            println("Failed to enumerate media devices: ${it.asJsException().message}")
            null
        }
    }

    //</editor-fold>
    //<editor-fold desc="Viewing Distance">
    private val userAgent = window.navigator.userAgent

    private val tvAgentStrings = setOf(
        "TV", "Chromecast", "Nexus Player", "Roku", "Tizen", "WebOS",
        "Viera", "AFT", "BRAVIA", "TCL", "Hisense", "Xbox One", "PlayStation"
    )

    private val automotiveAgentStrings = setOf(
        "Android Auto", "Apple CarPlay", "Tesla", "QtCarBrowser", "BMW", "Mercedes", "Audi"
    )

    private var _viewingDistance by mutableStateOf(getViewingDistance())

    private fun getViewingDistance(): UiMediaScope.ViewingDistance {
        return when {
            automotiveAgentStrings.any(userAgent::contains) -> UiMediaScope.ViewingDistance.Medium
            tvAgentStrings.any(userAgent::contains) -> UiMediaScope.ViewingDistance.Far
            else -> UiMediaScope.ViewingDistance.Near
        }
    }
    //</editor-fold>

    override val systemTheme: SystemTheme
        get() = _currentSystemTheme
    override val systemDensity: Density
        get() = _systemDensity

    override val windowPosture: UiMediaScope.Posture
        get() = _windowPosture
    override val windowWidth: Dp
        get() = windowInfo.containerDpSize.width
    override val windowHeight: Dp
        get() = windowInfo.containerDpSize.height
    override val pointerPrecision: UiMediaScope.PointerPrecision
        get() = _pointerPrecision
    override val keyboardKind: UiMediaScope.KeyboardKind
        get() =
            when {
                hasPhysicalKeyboard -> UiMediaScope.KeyboardKind.Physical
                isImeVisible -> UiMediaScope.KeyboardKind.Virtual
                else -> UiMediaScope.KeyboardKind.None
            }
    override val hasMicrophone: Boolean
        get() = _hasMicrophone
    override val hasCamera: Boolean
        get() = _hasCamera
    override val viewingDistance: UiMediaScope.ViewingDistance
        get() = _viewingDistance


    init {
        if (isMediaQuerySupported) {
            try {
                themeMediaQuery.addEventListener("change", themeListenerCallback)
            } catch (t: Throwable) {
                themeMediaQuery.addListener(themeListenerCallback)
            }
            initializeResolutionMediaQuery()

            try {
                orientationMediaQuery.addEventListener("change", orientationListenerCallback)
            } catch (t: Throwable) {
                orientationMediaQuery.addListener(orientationListenerCallback)
            }
        }
        if (isMediaDevicesEnumerateSupported) {
            initializeMediaDevicesInfo()
            if (isMediaDevicesChangeEventSupported()) {
                window.navigator.mediaDevices.ondevicechange = {
                    initializeMediaDevicesInfo()
                }
            }
        }
        if (isDevicePostureSupported) {
            addDevicePostureEventListener(devicePostureEventCallback)
            _windowPosture = getDevicePosture()
        }
    }

    fun updateHardwareType(
        keyboardKind: UiMediaScope.KeyboardKind,
        pointerPrecision: UiMediaScope.PointerPrecision
    ) {
        val currentHasPhysicalKeyboard = Snapshot.withoutReadObservation { hasPhysicalKeyboard }
        val currentPointerPrecision = Snapshot.withoutReadObservation { _pointerPrecision }
        when (keyboardKind) {
            UiMediaScope.KeyboardKind.Physical if !currentHasPhysicalKeyboard -> {
                hasPhysicalKeyboard = true
            }

            UiMediaScope.KeyboardKind.Virtual if currentHasPhysicalKeyboard -> {
                hasPhysicalKeyboard = false
            }
        }
        if (pointerPrecision != currentPointerPrecision) {
            _pointerPrecision = pointerPrecision
        }
    }

    fun dispose() {
        if (isMediaQuerySupported) {
            try {
                themeMediaQuery.removeEventListener("change", themeListenerCallback)
            } catch (t: Throwable) {
                themeMediaQuery.removeListener(themeListenerCallback)
            }

            if (currentResolutionMediaQuery != null) {
                try {
                    currentResolutionMediaQuery?.removeEventListener(
                        "change",
                        resolutionListenerCallback,
                        resolutionListenerOptions
                    )
                } catch (t: Throwable) {
                    currentResolutionMediaQuery?.removeListener(resolutionListenerCallback)
                }
                currentResolutionMediaQuery = null
            }

            try {
                orientationMediaQuery.removeEventListener("change", orientationListenerCallback)
            } catch (t: Throwable) {
                orientationMediaQuery.removeListener(orientationListenerCallback)
            }
        }

        if (isMediaDevicesEnumerateSupported && isMediaDevicesChangeEventSupported()) {
            window.navigator.mediaDevices.ondevicechange = null
        }

        if (isDevicePostureSupported) {
            removeDevicePostureEventListener(devicePostureEventCallback)
        }
    }
}

// supported by all browsers since 2015
// https://developer.mozilla.org/en-US/docs/Web/API/Window/matchMedia
// Changed from `@JsFun` annotation because in 2.2.20 it's marked as not available on LV = 2.0
// TODO: Cannot add opt-in with LV = 2.0 due to https://youtrack.jetbrains.com/issue/KT-79716
//language=Js
private fun isMatchMediaSupported(): Boolean = js("window.matchMedia != undefined")

//language=Js
private fun isMediaDevicesEnumerateSupported(): Boolean =
    js("Boolean(navigator.mediaDevices && navigator.mediaDevices.enumerateDevices)")

//language=Js
private fun isMediaDevicesChangeEventSupported(): Boolean =
    js("Boolean(navigator.mediaDevices.ondevicechange)")

//language=Js
private fun enumerateMediaDevices(): Promise<JsArray<MediaDeviceInfo>> =
    js("navigator.mediaDevices.enumerateDevices()")

//language=Js
private fun isDevicePostureApiSupported(): Boolean = js("Boolean(navigator.devicePosture)")

// strings checks are faster on a JS side
// language=js
private fun getDevicePostureType(): Int = js(
    """{
        switch (navigator.devicePosture.type) {
          case 'continuous':
            return 0; // UiMediaScope.Posture.Flat
          case 'folded':
            return 1; // UiMediaScope.Posture.Tabletop or UiMediaScope.Posture.Book
          default:
            return 0; // UiMediaScope.Posture.Flat
        } 
    }"""
)

//language=Js
private fun addDevicePostureEventListener(callback: (Event) -> Unit): Unit =
    js("navigator.devicePosture.addEventListener('change', callback)")

//language=Js
private fun removeDevicePostureEventListener(callback: (Event) -> Unit): Unit =
    js("navigator.devicePosture.removeEventListener('change', callback)")