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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.uikit.InterfaceOrientation
import androidx.compose.ui.uikit.density
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import org.jetbrains.skiko.SystemTheme
import androidx.compose.ui.uikit.utils.CMPKeyValueObserver
import androidx.compose.ui.uikit.utils.CMPUIWindowSceneUtils
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCAction
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceWasConnectedNotification
import platform.AVFoundation.AVCaptureDeviceWasDisconnectedNotification
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.Foundation.addObserver
import platform.Foundation.removeObserver
import platform.darwin.NSObject
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

internal class MediaEnvironment(val windowInfo: WindowInfo) : PlatformMediaEnvironment {

    private var window: UIWindow? = null
    /*
     * Initial value is arbitrarily chosen to avoid propagating invalid value logic
     * It's never the case in the real usage scenario to reflect that in type system
     */
    internal val interfaceOrientationState: MutableState<InterfaceOrientation> = mutableStateOf(
        InterfaceOrientation.Portrait
    )
    private val systemThemeState: MutableState<SystemTheme> = mutableStateOf(SystemTheme.UNKNOWN)

    private val systemDensityState: MutableState<Density> = mutableStateOf(window?.density ?: Density(1f))

    private val isImeShowing = mutableStateOf(false)
    private val pointerPrecisionState: MutableState<UiMediaScope.PointerPrecision> = mutableStateOf(
        UiMediaScope.PointerPrecision.Coarse
    )

    private val hasMicrophoneState: MutableState<Boolean> = mutableStateOf(detectHasMicrophone())
    private val hasCameraState: MutableState<Boolean> = mutableStateOf(detectHasCamera())

    private fun detectHasMicrophone(): Boolean =
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio) != null

    private fun detectHasCamera(): Boolean =
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) != null


    private fun updateCaptureDeviceAvailabilityState() {
        hasMicrophoneState.value = detectHasMicrophone()
        hasCameraState.value = detectHasCamera()
    }

    private val captureDeviceAvailabilityObserver = CaptureDeviceAvailabilityObserver(::updateCaptureDeviceAvailabilityState)

    fun updateInterfaceOrientationState() {
        currentInterfaceOrientation?.let {
            interfaceOrientationState.value = it
        }
    }
    fun updateUserInterfaceStyle(style: UIUserInterfaceStyle) {
        systemThemeState.value = style.asComposeSystemTheme()
    }
    fun updatePointerPrecision(precision: UiMediaScope.PointerPrecision) {
        pointerPrecisionState.value = precision
    }
    private val zeroDP = 0.dp
    fun onKeyboardOverlapHeightChanged(height: Dp) {
        isImeShowing.value = height > zeroDP
    }

    private val currentInterfaceOrientation: InterfaceOrientation?
        get() {
            return InterfaceOrientation.getByRawValue(
                CMPUIWindowSceneUtils.interfaceOrientationForWindowScene(window?.windowScene)
            )
        }

    private val interfaceOrientationObserver = SceneGeometryObserver {
        updateInterfaceOrientationState()
    }
    fun onDidMoveToWindow(window: UIWindow?) {
        this.window = window
        interfaceOrientationObserver.windowScene = window?.windowScene
        window ?: return

        systemDensityState.value = window.density
        updateInterfaceOrientationState()

    }

    override val systemTheme: SystemTheme
        get() = systemThemeState.value
    override val systemDensity: Density
        get() = systemDensityState.value

    fun startObserving() {
        interfaceOrientationObserver.isObservingEnabled = true
        captureDeviceAvailabilityObserver.isObservingEnabled = true
    }

    fun stopObserving() {
        interfaceOrientationObserver.isObservingEnabled = false
        captureDeviceAvailabilityObserver.isObservingEnabled = false
    }

    override val windowPosture: UiMediaScope.Posture
        get() = UiMediaScope.Posture.Flat //iOS doesn't have foldables yet!
    override val windowWidth: Dp
        get() = windowInfo.containerDpSize.width
    override val windowHeight: Dp
        get() = windowInfo.containerDpSize.height
    override val pointerPrecision: UiMediaScope.PointerPrecision
        get() = pointerPrecisionState.value
    override val keyboardKind: UiMediaScope.KeyboardKind
        get() = when {
            isImeShowing.value -> UiMediaScope.KeyboardKind.Virtual
            else -> UiMediaScope.KeyboardKind.None
        }
    override val hasMicrophone: Boolean
        get() = hasMicrophoneState.value
    override val hasCamera: Boolean
        get() = hasCameraState.value
    override val viewingDistance: UiMediaScope.ViewingDistance
        get() = UiMediaScope.ViewingDistance.Near
}


private class SceneGeometryObserver(
    val onGeometryChanged: () -> Unit
) : CMPKeyValueObserver() {
    private val observingKey = "effectiveGeometry"

    var windowScene: UIWindowScene? = null
        set(value) {
            if (field == value) return
            removeObserverIfNeeded()
            field = value
            addObserverIfNeeded()
        }

    var isObservingEnabled = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                addObserverIfNeeded()
            } else {
                removeObserverIfNeeded()
            }
        }

    private var isObservingAdded = false

    private fun addObserverIfNeeded() {
        if (isObservingEnabled && !isObservingAdded) {
            isObservingAdded = true
            windowScene?.addObserver(this, observingKey, NSKeyValueObservingOptionNew, null)
        }
    }

    private fun removeObserverIfNeeded() {
        windowScene?.removeObserver(this, observingKey)
        isObservingAdded = false
    }

    override fun observeValueForKeyPath(
        keyPath: String?,
        ofObject: Any?,
        change: Map<Any?, *>?,
        context: CPointer<out CPointed>?
    ) {
        onGeometryChanged()
    }
}

private class CaptureDeviceAvailabilityObserver(
    val onDeviceAvailabilityChanged: () -> Unit,
    private val notificationCenter: NSNotificationCenter = NSNotificationCenter.defaultCenter
) : NSObject() {

    var isObservingEnabled = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                addObservers()
            } else {
                removeObservers()
            }
        }

    private fun addObservers() {
        notificationCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString(::deviceAvailabilityDidChange.name + ":"),
            name = AVCaptureDeviceWasConnectedNotification,
            `object` = null
        )
        notificationCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString(::deviceAvailabilityDidChange.name + ":"),
            name = AVCaptureDeviceWasDisconnectedNotification,
            `object` = null
        )
    }

    private fun removeObservers() {
        notificationCenter.removeObserver(this)
    }

    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun deviceAvailabilityDidChange(arg: NSNotification) {
        onDeviceAvailabilityChanged()
    }
}

private fun UIUserInterfaceStyle.asComposeSystemTheme(): SystemTheme {
    return when (this) {
        UIUserInterfaceStyle.UIUserInterfaceStyleLight -> SystemTheme.LIGHT
        UIUserInterfaceStyle.UIUserInterfaceStyleDark -> SystemTheme.DARK
        else -> SystemTheme.UNKNOWN
    }
}