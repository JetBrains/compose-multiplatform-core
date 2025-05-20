/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.xr.compose.subspace

import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.xr.compose.subspace.layout.SubspaceLayout
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.runtime.math.Pose
import androidx.xr.scenecore.ContentlessEntity
import androidx.xr.scenecore.Entity

/**
 * A composable that represents a 3D volume of space within which an application can fill content.
 *
 * This composable provides a [Entity] through the [onVolumeEntity] lambda, allowing the caller to
 * attach child Jetpack XR Entities to it.
 *
 * @param modifier SubspaceModifiers to apply to the Volume.
 * @param onVolumeEntity A lambda function that will be invoked when the [Entity] becomes available.
 */
@Composable
@SubspaceComposable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun Volume(modifier: SubspaceModifier = SubspaceModifier, onVolumeEntity: (Entity) -> Unit) {
    SubspaceLayout(
        modifier = modifier,
        coreEntity =
            rememberCoreContentlessEntity {
                ContentlessEntity.create(this, name = entityName("Volume"), pose = Pose.Identity)
                    .apply(onVolumeEntity)
            },
    ) { _, constraints ->
        val initialWidth = constraints.minWidth.coerceAtLeast(0)
        val initialHeight = constraints.minHeight.coerceAtLeast(0)
        val initialDepth = constraints.minDepth.coerceAtLeast(0)
        layout(initialWidth, initialHeight, initialDepth) {}
    }
}
