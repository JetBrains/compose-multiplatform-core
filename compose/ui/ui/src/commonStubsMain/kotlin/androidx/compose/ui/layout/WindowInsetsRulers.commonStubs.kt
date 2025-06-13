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

package androidx.compose.ui.layout

import androidx.annotation.IntRange

actual sealed interface WindowInsetsAnimationProperties {
    actual val source: RectRulers

    actual val target: RectRulers

    actual val isVisible: Boolean

    actual val isAnimating: Boolean

    actual val fraction: Float

    @get:IntRange(from = 0) actual val durationMillis: Long
}

internal actual fun findDisplayCutouts(placementScope: Placeable.PlacementScope): List<RectRulers> =
    emptyList()

internal actual fun findInsetsAnimationProperties(
    placementScope: Placeable.PlacementScope,
    windowInsetsRulers: WindowInsetsRulers,
): WindowInsetsAnimationProperties = NoAnimationProperties
