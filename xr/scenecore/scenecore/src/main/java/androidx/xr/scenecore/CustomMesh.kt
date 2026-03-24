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

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.BoundingBox
import androidx.xr.runtime.math.FloatSize3d
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.runtime.CustomMeshResource as RtCustomMeshResource

/**
 * An immutable resource that defines the structure of a renderable mesh.
 *
 * A `CustomMesh` is composed of a [MeshBuffer] and a list of [MeshSubset]s. Each `MeshSubset`
 * defines a part of the mesh that can be rendered with a single [Material].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class CustomMesh
private constructor(
    private val resource: RtCustomMeshResource,
    public val meshBuffer: MeshBuffer,
    public val subsets: List<MeshSubset>,
    private val session: Session,
) : AutoCloseable {

    /** User-supplied bounding box for culling. */
    public var bounds: BoundingBox =
        BoundingBox.fromCenterAndHalfExtents(Vector3.Zero, FloatSize3d())
        set(value) {
            field = value
            session.renderingRuntime.setCustomMeshBoundingBox(
                resource,
                value.center.x,
                value.center.y,
                value.center.z,
                value.halfExtents.width,
                value.halfExtents.height,
                value.halfExtents.depth,
            )
        }

    @MainThread
    override fun close() {
        session.renderingRuntime.destroyCustomMesh(resource)
    }

    internal fun getResource(): RtCustomMeshResource {
        return resource
    }

    public companion object {
        /**
         * Creates a new [CustomMesh].
         *
         * @param session The session to use for creating the CustomMesh.
         * @param meshBuffer The [MeshBuffer] containing the vertex and index data.
         * @param subsets The list of [MeshSubset]s defining the parts of the mesh.
         * @return A new [CustomMesh].
         */
        @MainThread
        public fun create(
            session: Session,
            meshBuffer: MeshBuffer,
            subsets: List<MeshSubset>,
        ): CustomMesh {
            val runtime = session.renderingRuntime

            val subsetOffsets = IntArray(subsets.size)
            val subsetCounts = IntArray(subsets.size)

            for (i in subsets.indices) {
                subsetOffsets[i] = subsets[i].indexOffset
                subsetCounts[i] = subsets[i].indexCount
            }

            val resource =
                runtime.createCustomMesh(meshBuffer.getResource(), subsetOffsets, subsetCounts)

            return CustomMesh(resource, meshBuffer, subsets, session)
        }
    }
}
