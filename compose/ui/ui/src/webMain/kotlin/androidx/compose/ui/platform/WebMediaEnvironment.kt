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
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.MediaQueryListener
import androidx.compose.ui.window.MediaQueryStatus
import kotlin.js.Promise
import kotlinx.browser.window
import org.jetbrains.skiko.SystemTheme
import org.w3c.dom.events.Event
import org.w3c.dom.mediacapture.AUDIOINPUT
import org.w3c.dom.mediacapture.MediaDeviceInfo
import org.w3c.dom.mediacapture.MediaDeviceKind
import org.w3c.dom.mediacapture.VIDEOINPUT

internal class WebMediaEnvironment(
    val windowInfo: WindowInfo,
    val onDensityChanged: (Density) -> Unit
) : UiMediaScope {

    private var isDisposed = false

    //Microphone And Camera detection Api supported
    private val isMediaDevicesEnumerateSupported: Boolean =
        isSecureContext && isMediaDevicesEnumerateSupported()

    //<editor-fold desc="System Theme Media Query">

    private val themeMediaQueryListener: MediaQueryListener = object : MediaQueryListener("(prefers-color-scheme: dark)") {
        override fun onChange(matches: Boolean) {
            _currentSystemTheme = if (matches) SystemTheme.DARK else SystemTheme.LIGHT
        }
    }

    private var _currentSystemTheme by mutableStateOf(
        when(themeMediaQueryListener.matches()) {
            MediaQueryStatus.MATCH -> SystemTheme.DARK
            MediaQueryStatus.NO_MATCH -> SystemTheme.LIGHT
            MediaQueryStatus.UNSUPPORTED -> SystemTheme.UNKNOWN
        }
    )

    //</editor-fold>
    //<editor-fold desc="Resolution Media Query">
    private var resolutionMediaQueryListener: MediaQueryListener? = null
    private fun initializeResolutionMediaQuery() {
        if (isDisposed) return
        val contentScale = window.devicePixelRatio
        resolutionMediaQueryListener?.dispose()
        resolutionMediaQueryListener = object : MediaQueryListener("(resolution: ${contentScale}dppx)") {
            override fun onChange(matches: Boolean) {
                if (!isDisposed) {
                    if (!matches) {
                        val density = Density(window.devicePixelRatio.toFloat())
                        onDensityChanged(density)
                        _systemDensity = density
                    }
                    initializeResolutionMediaQuery()
                }
            }
        }
    }

    //</editor-fold>
    //<editor-fold desc="Orientation Media Query">
    private val orientationMediaQueryListener: MediaQueryListener = object : MediaQueryListener("(orientation: portrait)") {
        override fun onChange(matches: Boolean) {
            isOrientationPortrait = matches
        }
    }

    private var isOrientationPortrait by mutableStateOf(orientationMediaQueryListener.matches() == MediaQueryStatus.MATCH)

    //</editor-fold>
    //<editor-fold desc="Device Posture Media Query">
    private var _windowPosture by mutableStateOf(UiMediaScope.Posture.Flat)

    private val devicePostureEventCallback: (Event) -> Unit = {
        _windowPosture = getDevicePosture()
    }

    private fun getDevicePosture(): UiMediaScope.Posture {
        val postureType = getDevicePostureType()
        val isPortrait = isOrientationPortrait
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

    val systemTheme: SystemTheme
        get() = _currentSystemTheme
    val systemDensity: Density
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
        initializeResolutionMediaQuery()
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
        val currentHasPhysicalKeyboard = hasPhysicalKeyboard
        val currentPointerPrecision = _pointerPrecision
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
        themeMediaQueryListener.dispose()
        resolutionMediaQueryListener?.dispose()
        orientationMediaQueryListener.dispose()

        if (isMediaDevicesEnumerateSupported && isMediaDevicesChangeEventSupported()) {
            window.navigator.mediaDevices.ondevicechange = null
        }

        if (isDevicePostureSupported) {
            removeDevicePostureEventListener(devicePostureEventCallback)
        }
    }
}


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