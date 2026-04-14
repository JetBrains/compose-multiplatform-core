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

package androidx.xr.scenecore

import androidx.annotation.RestrictTo

/**
 * Enum defining the topology of the mesh subset.
 *
 * This specifies how the indices in the index buffer are interpreted to form geometric primitives.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public enum class MeshSubsetTopology(public val id: Int) {
    /** Every three indices form a separate triangle. */
    TRIANGLES(0),

    /** Every index after the first two forms a triangle with the previous two indices. */
    TRIANGLE_STRIP(1),
}

/**
 * Defines a subset of the mesh to draw.
 *
 * A subset defines a contiguous range of indices within the [MeshBuffer]'s index buffer that should
 * be rendered together using a specific topology and a single [Material].
 *
 * @param topology The [MeshSubsetTopology] of the primitives to draw.
 * @param indexOffset The offset (in number of indices, not bytes) to the first index in the index
 *   buffer.
 * @param indexCount The number of indices to draw.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public data class MeshSubset(
    public val topology: MeshSubsetTopology,
    public val indexOffset: Int,
    public val indexCount: Int,
)
