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

package androidx.compose.material3.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIView
import androidx.compose.ui.uikit.utils.CMPWindowHingeInfo
import androidx.compose.ui.uikit.utils.CMPWindowPostureObserver

@Composable
internal actual fun calculatePosture(): Posture {
    val view = LocalUIView.current
    val density = LocalDensity.current
    var platformHinges by remember(view) { mutableStateOf(emptyList<CMPWindowHingeInfo>()) }

    DisposableEffect(view) {
        val observer = CMPWindowPostureObserver(
            view = view,
            onUpdate = { hinges ->
                platformHinges = hinges?.filterIsInstance<CMPWindowHingeInfo>().orEmpty()
            },
        )
        observer.start()

        onDispose { observer.stop() }
    }

    return remember(platformHinges, density) { platformHinges.toPosture(density.density) }
}

private fun List<CMPWindowHingeInfo>.toPosture(scale: Float): Posture {
    val hinges = map { hinge ->
        HingeInfo(
            bounds = Rect(
                left = (hinge.x * scale).toFloat(),
                top = (hinge.y * scale).toFloat(),
                right = ((hinge.x + hinge.width) * scale).toFloat(),
                bottom = ((hinge.y + hinge.height) * scale).toFloat(),
            ),
            isFlat = hinge.isFlat,
            isVertical = hinge.isVertical,
            isSeparating = hinge.isSeparating,
            isOccluding = hinge.isOccluding,
        )
    }

    return Posture(
        isTabletop = hinges.any { !it.isVertical && !it.isFlat },
        hingeList = hinges,
    )
}
