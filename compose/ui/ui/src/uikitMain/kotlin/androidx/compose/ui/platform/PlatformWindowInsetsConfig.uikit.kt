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
import androidx.compose.ui.uikit.InterfaceOrientation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

internal class UIKitWindowInsetsConfig: PlatformWindowInsetsConfig {
    var layoutMargins = mutableStateOf(ValueInsets.ZERO)
        private set
    var safeAreaInsets = mutableStateOf(ValueInsets.ZERO)
        private set
    var keyboardOverlapHeight = mutableStateOf(0)
        private set

    private var interfaceOrientation = mutableStateOf(InterfaceOrientation.Portrait)

    private val _displayCutout = derivedStateOf {
        val orientation = interfaceOrientation.value
        val safeAreaInsets = safeAreaInsets.value

        when(orientation) {
            InterfaceOrientation.Portrait -> ValueInsets(top = safeAreaInsets.top)
            InterfaceOrientation.PortraitUpsideDown -> ValueInsets(bottom = safeAreaInsets.bottom)
            InterfaceOrientation.LandscapeLeft -> ValueInsets(left = safeAreaInsets.left)
            InterfaceOrientation.LandscapeRight -> ValueInsets(right = safeAreaInsets.right)
        }
    }
    private val _ime = derivedStateOf {
        ValueInsets(bottom = keyboardOverlapHeight.value)
    }
    private val _mandatorySystemGesture = derivedStateOf {
        val safeAreaInsets = safeAreaInsets.value
        ValueInsets(top = safeAreaInsets.top, bottom = safeAreaInsets.bottom)
    }
    private val _navigationBars = derivedStateOf {
        ValueInsets(bottom = safeAreaInsets.value.bottom)
    }
    private val _statusBars = derivedStateOf {
        ValueInsets(top = safeAreaInsets.value.top)
    }
    private val _tappableElement = derivedStateOf {
        ValueInsets(top = safeAreaInsets.value.top)
    }

    override val captionBar: PlatformInsets get() = ValueInsets.ZERO
    override val displayCutout: PlatformInsets get() = _displayCutout.value
    override val ime: PlatformInsets get() = _ime.value
    override val mandatorySystemGestures: PlatformInsets get() = _mandatorySystemGesture.value
    override val navigationBars: PlatformInsets get() = _navigationBars.value
    override val statusBars: PlatformInsets get() = _statusBars.value
    override val systemBars: PlatformInsets get() = safeAreaInsets.value
    override val systemGestures: PlatformInsets get() = layoutMargins.value
    override val tappableElement: PlatformInsets get() = _tappableElement.value
    override val waterfall: PlatformInsets get() = ValueInsets.ZERO

    fun updateInterfaceOrientation(orientation: InterfaceOrientation) {
        interfaceOrientation.value = orientation
    }

    fun updateKeyboardOverlapHeight(height: Dp, density: Density) {
        keyboardOverlapHeight.value = with(density) { height.roundToPx() }
    }

    fun updateLayoutMargins(layoutMargins: ValueInsets) {
        this.layoutMargins.value = layoutMargins
    }

    fun updateSafeAreaInsets(safeAreaInsets: ValueInsets) {
        this.safeAreaInsets.value = safeAreaInsets
    }
}