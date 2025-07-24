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

import androidx.compose.ui.InternalComposeUiApi

@InternalComposeUiApi
interface PlatformWindowInsetsConfig {
    val captionBar: PlatformInsets get() = PlatformInsets.Unspecified
    val displayCutout: PlatformInsets get() = PlatformInsets.Unspecified
    val ime: PlatformInsets get() = PlatformInsets.Unspecified
    val mandatorySystemGestures: PlatformInsets get() = PlatformInsets.Unspecified
    val navigationBars: PlatformInsets get() = PlatformInsets.Unspecified
    val statusBars: PlatformInsets get() = PlatformInsets.Unspecified
    val systemBars: PlatformInsets get() = PlatformInsets.Unspecified
    val systemGestures: PlatformInsets get() = PlatformInsets.Unspecified
    val tappableElement: PlatformInsets get() = PlatformInsets.Unspecified
    val waterfall: PlatformInsets get() = PlatformInsets.Unspecified
}

val PlatformWindowInsetsConfig.safeDrawing: PlatformInsets get() = InnermostPlatformInsets(
    arrayOf(statusBars, navigationBars, captionBar, displayCutout, ime, systemBars, tappableElement)
)

val PlatformWindowInsetsConfig.safeGestures: PlatformInsets get() = InnermostPlatformInsets(
    arrayOf(mandatorySystemGestures, systemGestures, tappableElement, waterfall)
)

val PlatformWindowInsetsConfig.safeContent: PlatformInsets get() = InnermostPlatformInsets(
    arrayOf(statusBars, navigationBars, captionBar, ime, systemGestures, mandatorySystemGestures, tappableElement, displayCutout, waterfall)
)

private class InnermostPlatformInsets(
    val insets: Array<out PlatformInsets>
): PlatformInsets {
    override val left: Int get() = if (insets.isEmpty()) 0 else insets.maxOf { it.left }
    override val top: Int get() = if (insets.isEmpty()) 0 else insets.maxOf { it.top }
    override val right: Int get() = if (insets.isEmpty()) 0 else insets.maxOf { it.right }
    override val bottom: Int get() = if (insets.isEmpty()) 0 else insets.maxOf { it.bottom }
}