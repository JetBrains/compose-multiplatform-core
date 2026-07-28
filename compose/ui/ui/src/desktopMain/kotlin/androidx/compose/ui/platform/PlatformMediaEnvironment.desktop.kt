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

import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.unit.Dp

internal class DesktopMediaEnvironment(val windowInfo: WindowInfo) : UiMediaScope {

    override val windowPosture: UiMediaScope.Posture
        get() = UiMediaScope.Posture.Flat

    override val windowWidth: Dp
        get() = windowInfo.containerDpSize.width

    override val windowHeight: Dp
        get() = windowInfo.containerDpSize.height

    override val pointerPrecision: UiMediaScope.PointerPrecision
        get() = UiMediaScope.PointerPrecision.Fine

    override val keyboardKind: UiMediaScope.KeyboardKind
        get() = UiMediaScope.KeyboardKind.Physical

    override val hasMicrophone: Boolean
        get() = true //inferred as always having at least 1 microphone

    override val hasCamera: Boolean
        get() = false //no reliable way to get it

    override val viewingDistance: UiMediaScope.ViewingDistance
        get() = UiMediaScope.ViewingDistance.Near

    fun dispose() {
        // No-op for now, but can be used to clean up resources if needed in the future.
    }
}