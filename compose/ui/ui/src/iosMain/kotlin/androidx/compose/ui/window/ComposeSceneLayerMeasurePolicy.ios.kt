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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.uikit.LocalUIView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlin.math.min

internal val LocalSceneDestinationSize = staticCompositionLocalOf<State<DpSize>?> { null }

@Composable
internal actual fun rememberComposeSceneLayerConstraintsModifier(
    usePlatformDefaultWidth: Boolean,
    platformInsets: PlatformInsets
): Density.(Constraints) -> Constraints {
    if (!usePlatformDefaultWidth) {
        return defaultPlatformConstraintsModifier(usePlatformDefaultWidth, platformInsets)
    }
    val sceneSize = LocalSceneDestinationSize.current
    return remember(sceneSize, platformInsets) {
        { constraints ->
            // During the size transition animation (e.g. device rotation), the layer constraints
            // are interpolated between the initial and the target sizes, so their smallest
            // dimension might be bigger than at the start and at the end of the transition.
            // Limit it by the window bounds, which are set to the target size immediately.
            // Note: reading bounds isn't observable, but the window size changes always
            // change the layer constraints as well, which triggers a new measure pass.
            val smallestConstraintsWidth = min(constraints.maxWidth, constraints.maxHeight).toDp()
            val smallestWidth = sceneSize?.value
                ?.let { min(it.width, it.height) }
                ?.let { min(it, smallestConstraintsWidth) } ?: smallestConstraintsWidth

            constraints
                .withoutPlatformInsets(platformInsets)
                .constrain(preferredDialogWidthConstraints(constraints, smallestWidth))
        }
    }
}
