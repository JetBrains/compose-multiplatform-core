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
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlin.math.min
import kotlinx.cinterop.useContents

@Composable
internal actual fun rememberPreferredDialogLayoutWidth(): Density.(Constraints) -> Int {
    val view = LocalUIView.current
    return remember(view) {
        { constraints ->
            // During the size transition animation (e.g. device rotation), the layer constraints
            // are interpolated between the initial and the target sizes, so their smallest
            // dimension might be bigger than at the start and at the end of the transition.
            // Limit it by the window bounds, which are set to the target size immediately.
            // Note: reading bounds isn't observable, but the window size changes always
            // change the layer constraints as well, which triggers a new measure pass.
            val smallestConstraintsWidth = min(constraints.maxWidth, constraints.maxHeight).toDp()
            val smallestWindowWidth = view.window?.bounds?.useContents {
                min(size.width, size.height).dp
            }?.let { min(it, smallestConstraintsWidth) } ?: smallestConstraintsWidth

            preferredDialogWidth(smallestWindowWidth)
        }
    }
}
