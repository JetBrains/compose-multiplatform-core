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
import androidx.xr.scenecore.runtime.MeshBufferResource as RtMeshBufferResource
import java.nio.ByteBuffer

/**
 * A container holding raw vertex and index data.
 *
 * A `MeshBuffer` contains one or more vertex buffers and an optional index buffer. The vertex
 * buffers contain the vertex data according to the provided [VertexLayout]. The index buffer
 * contains the indices of the vertices that form the primitives of the mesh.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class MeshBuffer
private constructor(
    private val resource: RtMeshBufferResource,
    public val vertexLayout: VertexLayout,
    public val maxVertices: Int,
    public val maxIndices: Int,
    private val session: Session,
) : AutoCloseable {

    @MainThread
    override fun close() {
        session.renderingRuntime.destroyMeshBuffer(resource)
    }

    internal fun getResource(): RtMeshBufferResource {
        return resource
    }

    public companion object {
        /**
         * Creates a new [MeshBuffer].
         *
         * @param session The session to use for creating the MeshBuffer.
         * @param vertexLayout The layout of the vertices in the vertex buffer(s).
         * @param maxVertices The maximum number of vertices the buffer can hold.
         * @param maxIndices The maximum number of indices the buffer can hold.
         * @param vertexData The vertex data arrays, one for each buffer index used in the layout.
         * @param vertexDataSizes The sizes of the vertex data arrays in bytes.
         * @param indexData The index data.
         * @param indexDataSize The size of the index data in bytes.
         * @return A new [MeshBuffer].
         */
        @MainThread
        public fun create(
            session: Session,
            vertexLayout: VertexLayout,
            maxVertices: Int,
            maxIndices: Int,
            vertexData: Array<ByteBuffer>? = null,
            vertexDataSizes: IntArray? = null,
            indexData: ByteBuffer? = null,
            indexDataSize: Int = 0,
        ): MeshBuffer {
            val runtime = session.renderingRuntime

            val attributeIds = IntArray(vertexLayout.attributes.size)
            val attributeTypes = IntArray(vertexLayout.attributes.size)
            val bufferIndices = ByteArray(vertexLayout.attributes.size)

            for (i in vertexLayout.attributes.indices) {
                val attr = vertexLayout.attributes[i]
                attributeIds[i] = attr.attribute.id
                attributeTypes[i] = attr.type.id
                bufferIndices[i] = attr.bufferIndex.toByte()
            }

            val resource =
                runtime.createMeshBuffer(
                    attributeIds,
                    attributeTypes,
                    bufferIndices,
                    maxVertices,
                    maxIndices,
                    vertexData,
                    vertexDataSizes,
                    indexData,
                    indexDataSize,
                )

            return MeshBuffer(resource, vertexLayout, maxVertices, maxIndices, session)
        }
    }
}
