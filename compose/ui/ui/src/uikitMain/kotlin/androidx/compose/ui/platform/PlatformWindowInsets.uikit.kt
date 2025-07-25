/*
 * Copyright 2025 The Android Open Source Project
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

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.uikit.InterfaceOrientation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

internal class UIKitWindowInsets: PlatformWindowInsets {
    var layoutMargins = mutableStateOf(PlatformInsets.Zero)
        private set
    var safeAreaInsets = mutableStateOf(PlatformInsets.Zero)
        private set
    var keyboardOverlapHeight = mutableStateOf(0)
        private set

    private var sceneSize = mutableStateOf(IntSize.Zero)
    private var interfaceOrientation = mutableStateOf(InterfaceOrientation.Portrait)

    private val _displayCutouts = derivedStateOf {
        val orientation = interfaceOrientation.value
        val safeAreaInsets = safeAreaInsets.value
        val sceneSize = sceneSize.value

        val hasCutout = when(orientation) {
            InterfaceOrientation.Portrait -> safeAreaInsets.top > 0
            InterfaceOrientation.PortraitUpsideDown -> safeAreaInsets.bottom > 0
            InterfaceOrientation.LandscapeLeft -> safeAreaInsets.left > 0
            InterfaceOrientation.LandscapeRight -> safeAreaInsets.right > 0
        }

        if (!hasCutout || sceneSize.width <= 0 || sceneSize.height <= 0) {
            emptyList()
        } else {
            when (orientation) {
                InterfaceOrientation.Portrait -> listOf(
                    Rect(0f, 0f, sceneSize.width.toFloat(), safeAreaInsets.top.toFloat())
                )
                InterfaceOrientation.PortraitUpsideDown -> listOf(
                    Rect(0f, sceneSize.height - safeAreaInsets.bottom.toFloat(), sceneSize.width.toFloat(), sceneSize.height.toFloat())
                )
                InterfaceOrientation.LandscapeLeft -> listOf(
                    Rect(0f, 0f, safeAreaInsets.left.toFloat(), sceneSize.height.toFloat())
                )
                InterfaceOrientation.LandscapeRight -> listOf(
                    Rect(sceneSize.width - safeAreaInsets.right.toFloat(), 0f, sceneSize.width.toFloat(), sceneSize.height.toFloat())
                )
            }
        }
    }

    private val _displayCutout = derivedStateOf {
        val orientation = interfaceOrientation.value
        val safeAreaInsets = safeAreaInsets.value

        when(orientation) {
            InterfaceOrientation.Portrait -> PlatformInsets(top = safeAreaInsets.top)
            InterfaceOrientation.PortraitUpsideDown -> PlatformInsets(bottom = safeAreaInsets.bottom)
            InterfaceOrientation.LandscapeLeft -> PlatformInsets(left = safeAreaInsets.left)
            InterfaceOrientation.LandscapeRight -> PlatformInsets(right = safeAreaInsets.right)
        }
    }
    private val _ime = derivedStateOf {
        PlatformInsets(bottom = keyboardOverlapHeight.value)
    }
    private val _mandatorySystemGesture = derivedStateOf {
        val safeAreaInsets = safeAreaInsets.value
        PlatformInsets(top = safeAreaInsets.top, bottom = safeAreaInsets.bottom)
    }
    private val _navigationBars = derivedStateOf {
        PlatformInsets(bottom = safeAreaInsets.value.bottom)
    }
    private val _statusBars = derivedStateOf {
        PlatformInsets(top = safeAreaInsets.value.top)
    }
    private val _tappableElement = derivedStateOf {
        PlatformInsets(top = safeAreaInsets.value.top)
    }

    override val displayCutouts: List<Rect> get() = _displayCutouts.value
    override val captionBar: PlatformInsets get() = PlatformInsets.Zero
    override val displayCutout: PlatformInsets get() = _displayCutout.value
    override val ime: PlatformInsets get() = _ime.value
    override val mandatorySystemGestures: PlatformInsets get() = _mandatorySystemGesture.value
    override val navigationBars: PlatformInsets get() = _navigationBars.value
    override val statusBars: PlatformInsets get() = _statusBars.value
    override val systemBars: PlatformInsets get() = safeAreaInsets.value
    override val systemGestures: PlatformInsets get() = layoutMargins.value
    override val tappableElement: PlatformInsets get() = _tappableElement.value
    override val waterfall: PlatformInsets get() = PlatformInsets.Zero

    fun updateInterfaceOrientation(orientation: InterfaceOrientation) {
        interfaceOrientation.value = orientation
    }

    fun updateKeyboardOverlapHeight(height: Dp, density: Density) {
        keyboardOverlapHeight.value = with(density) { height.roundToPx() }
    }

    fun updateLayoutMargins(layoutMargins: PlatformInsets) {
        this.layoutMargins.value = layoutMargins
    }

    fun updateSafeAreaInsets(safeAreaInsets: PlatformInsets) {
        this.safeAreaInsets.value = safeAreaInsets
    }

    fun updateSceneSize(size: IntSize) {
        sceneSize.value = size
    }
}