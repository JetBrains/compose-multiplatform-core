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
import androidx.compose.remote.creation.compose.v2.RemoteBoxV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * A layout composable that positions its children relative to its own edges.
 *
 * `RemoteBox` allows you to wrap multiple children and position them using [contentAlignment]. In
 * Remote Compose, this layout is recorded as a Box command.
 *
 * @param modifier The modifier to be applied to this box.
 * @param contentAlignment The default alignment inside the Box.
 * @param content The content of the box.
 */
@RemoteComposable
@Composable
public fun RemoteBox(
    modifier: RemoteModifier = RemoteModifier,
    contentAlignment: RemoteAlignment = RemoteAlignment.TopStart,
    content: @Composable () -> Unit,
) {
    RemoteBoxV2(modifier, contentAlignment, LocalLayoutDirection.current) { content() }
}

/**
 * A version of [RemoteBox] with no content, often used as a spacer or a background placeholder.
 *
 * @param modifier The modifier to be applied to this box.
 */
@RemoteComposable
@Composable
public fun RemoteBox(modifier: RemoteModifier = RemoteModifier) {
    RemoteBoxV2(modifier)
}
