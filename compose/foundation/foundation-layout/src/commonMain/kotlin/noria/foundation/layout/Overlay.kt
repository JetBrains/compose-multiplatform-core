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

package noria.foundation.layout

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect

fun Modifier.overlay(
    key: OverlayHostKey = MainOverlayHostKey,
    zIndex: Int = 0,
    overlay: @Composable OverlayScope.() -> Unit,
): Modifier {
    return composed {
        val overlayHostState = key.current
        val compositionContext = rememberCompositionContext()
        val overlay = remember { OverlayState(compositionContext, overlay) }.apply {
            this.compositionContext = compositionContext
            this.content = overlay
        }

        DisposableEffect(overlay) {
            overlayHostState.overlays += overlay
            onDispose {
                overlayHostState.overlays -= overlay
            }
        }

        Modifier.onGloballyPositioned { anchorCoordinates ->
            overlayHostState.coordinates?.let {
                overlay.anchorBounds = it.localBoundingBoxOf(
                    anchorCoordinates, clipBounds =
                        false
                ).roundToIntRect()
            }
        }
    }
}

@LayoutScopeMarker
@Stable
interface OverlayScope : BoxScope {
    val anchorBounds: IntRect

    fun Modifier.alignInAnchor(alignment: Alignment): Modifier

    fun Modifier.alignByAnchor(alignment: Alignment): Modifier

    fun Modifier.alignByAnchor(anchor: Alignment.Horizontal, side: Alignment.Vertical): Modifier

    fun Modifier.alignByAnchor(anchor: Alignment.Vertical, side: Alignment.Horizontal): Modifier
}
