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
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.LocalComposeSceneContext
import androidx.compose.ui.scene.createOverlayLayer
import androidx.compose.ui.scene.requireCurrent

/**
 * Displays content above all regular [Popup] and [Dialog] layers, including those created later.
 * Overlay popups are stacked in creation order, with the newest on top. Positioning and behavior
 * otherwise follow [Popup] and [PopupProperties]. Only [CanvasLayersComposeScene] is supported.
 */
@InternalComposeUiApi
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OverlayPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val scene = LocalComposeSceneContext.requireCurrent()
    check(scene is ComposeScene) { "OverlayPopup requires CanvasLayersComposeScene" }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val compositionLocalContext = currentCompositionLocalContext
    val layer = remember(scene) {
        scene.createOverlayLayer(density, layoutDirection)
    }
    layer.density = density
    layer.layoutDirection = layoutDirection
    layer.compositionLocalContext = compositionLocalContext
    layer.focusable = properties.focusable
    layer.consumePointerInputOutside = properties.consumePointerInputOutside

    PopupWithLayer(
        layer = layer,
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = properties,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        content = content,
    )
}
