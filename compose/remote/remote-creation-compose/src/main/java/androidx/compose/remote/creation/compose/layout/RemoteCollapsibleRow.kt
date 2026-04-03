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

package androidx.compose.remote.creation.compose.layout

import androidx.annotation.RestrictTo
import androidx.compose.remote.core.operations.layout.managers.CollapsiblePriority
import androidx.compose.remote.core.operations.layout.modifiers.DimensionModifierOperation.Type
import androidx.compose.remote.creation.compose.modifier.CollapsiblePriorityModifier
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.WidthModifier
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.v2.RemoteCollapsibleRowV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteCollapsibleRowScope {
    public fun RemoteModifier.weight(weight: RemoteFloat): RemoteModifier =
        then(WidthModifier(Type.WEIGHT, weight))

    public fun RemoteModifier.weight(weight: Float): RemoteModifier =
        then(WidthModifier(Type.WEIGHT, RemoteFloat(weight)))

    public fun RemoteModifier.priority(priority: Float): RemoteModifier =
        then(CollapsiblePriorityModifier(CollapsiblePriority.HORIZONTAL, RemoteFloat(priority)))
}

/**
 * RemoteRow implements a row layout, delegating to the foundation Row layout as needed. This allows
 * RemoteRow to both work as a normal Row when called within a normal Compose tree, and capture the
 * layout information when called within a capture pass for RemoteCompose.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@RemoteComposable
@Composable
public fun RemoteCollapsibleRow(
    modifier: RemoteModifier = RemoteModifier,
    horizontalArrangement: RemoteArrangement.Horizontal = RemoteArrangement.Start,
    verticalAlignment: RemoteAlignment.Vertical = RemoteAlignment.Top,
    content: @Composable RemoteCollapsibleRowScope.() -> Unit,
) {
    RemoteCollapsibleRowV2(
        modifier,
        horizontalArrangement,
        verticalAlignment,
        LocalLayoutDirection.current,
        content,
    )
}
