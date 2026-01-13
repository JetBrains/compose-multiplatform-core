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
@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)

package androidx.compose.remote.creation.compose.modifier

import androidx.annotation.RestrictTo
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.painter.RemotePainter
import androidx.compose.remote.creation.compose.shaders.RemoteBrush
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.modifiers.RecordingModifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class BackgroundModifier(val color: RemoteColor) : RemoteModifier.Element {
    override fun toRemoteComposeElement(): RecordingModifier.Element {
        val r = color.red.id
        val g = color.green.id
        val b = color.blue.id
        val a = color.alpha.id
        return androidx.compose.remote.creation.modifiers.SolidBackgroundModifier(r, g, b, a)
    }
}

public fun RemoteModifier.background(color: Color): RemoteModifier =
    this.then(BackgroundModifier(color.rc))

public fun RemoteModifier.background(color: RemoteColor): RemoteModifier =
    this.then(BackgroundModifier(color))

@RemoteComposable
@Composable
public fun RemoteModifier.background(brush: RemoteBrush): RemoteModifier =
    this.drawWithContent {
        drawRect(brush)
        drawContent()
    }

@RemoteComposable
@Composable
public fun RemoteModifier.background(remotePainter: RemotePainter): RemoteModifier =
    this.drawWithContent {
        with(remotePainter) { onDraw() }
        drawContent()
    }
