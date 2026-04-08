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

package androidx.compose.remote.creation.compose.layout

import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.v2.RemoteCanvasV2
import androidx.compose.runtime.Composable

/**
 * A Composable that provides a [RemoteDrawScope] for drawing operations in RemoteCompose.
 *
 * @param modifier The [RemoteModifier] to apply to this layout.
 * @param content The drawing commands to be executed on the remote canvas via [RemoteDrawScope].
 */
@RemoteComposable
@Composable
public fun RemoteCanvas(
    modifier: RemoteModifier = RemoteModifier,
    content: RemoteDrawScope.() -> Unit,
) {
    RemoteCanvasV2(modifier, content)
}
