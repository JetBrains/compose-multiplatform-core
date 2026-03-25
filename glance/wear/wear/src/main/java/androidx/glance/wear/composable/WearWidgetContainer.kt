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

package androidx.glance.wear.composable

import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.drawWithContent
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.state.RemoteDp
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.runtime.Composable
import androidx.glance.wear.WearWidgetBrush

/**
 * A container for a remote compose widget, applying standard styling.
 *
 * This container applies horizontal and vertical padding, as well as corners, to its content.
 */
@RemoteComposable
@Composable
internal fun WearWidgetContainer(
    horizontalPadding: RemoteDp,
    verticalPadding: RemoteDp,
    cornerRadius: RemoteDp,
    background: WearWidgetBrush,
    content: @RemoteComposable @Composable () -> Unit,
) {
    RemoteBox(
        modifier =
            RemoteModifier.fillMaxSize().drawWithContent {
                val cornerRadiusOffset = RemoteOffset(cornerRadius.toPx(), cornerRadius.toPx())
                val paint = RemotePaint()
                background.foldIn(Unit) { _, element ->
                    with(element.brush) { applyTo(paint, size) }
                    drawRoundRect(paint = paint, cornerRadius = cornerRadiusOffset)
                }
                drawContent()
            }
    ) {
        RemoteBox(
            modifier =
                RemoteModifier.fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            content = content,
        )
    }
}
