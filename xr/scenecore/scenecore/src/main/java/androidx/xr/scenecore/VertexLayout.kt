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
 * Enum defining the attribute of a vertex.
 *
 * This enum is used to define the semantic meaning of a vertex attribute in a
 * [VertexAttributeDescriptor].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public enum class VertexAttribute(public val id: Int) {
    /** The position of the vertex. Must be a FLOAT3. */
    POSITION(0),

    /**
     * The normal of the vertex. Must be a FLOAT3 normal, or a FLOAT4 quaternion representing the
     * entire tangent frame.
     */
    NORMAL(1),

    /** The color of the vertex. Must be a UBYTE4_NORM. */
    COLOR(2),

    /** The first set of texture coordinates. Must be a FLOAT2. */
    UV0(3),

    /** The second set of texture coordinates. Must be a FLOAT2. */
    UV1(4),

    /** The indices of the bones that affect this vertex. Must be a UBYTE4. */
    BONE_INDICES(5),

    /** The weights of the bones that affect this vertex. Must be a UBYTE4_NORM. */
    BONE_WEIGHTS(6),
}

/**
 * Enum defining the type of data for a vertex attribute.
 *
 * This specifies the data type and component count for an attribute in the vertex buffer.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public enum class VertexAttributeType(public val id: Int) {
    /** A single 32-bit floating point value. */
    FLOAT(0),

    /** Two 32-bit floating point values. */
    FLOAT2(1),

    /** Three 32-bit floating point values. */
    FLOAT3(2),

    /** Four 32-bit floating point values. */
    FLOAT4(3),

    /** Four unsigned 8-bit integers, normalized to [0, 1]. */
    UBYTE4_NORM(4),

    /** A single unsigned 8-bit integer. */
    UBYTE(5),
}

/**
 * Descriptor for a single vertex attribute.
 *
 * Defines how a specific vertex attribute is structured within a vertex buffer.
 *
 * @param attribute The [VertexAttribute] semantic being described.
 * @param type The [VertexAttributeType] data type of the attribute.
 * @param bufferIndex The index of the vertex buffer where this attribute is stored.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public data class VertexAttributeDescriptor(
    public val attribute: VertexAttribute,
    public val type: VertexAttributeType,
    public val bufferIndex: Int = 0,
)

/**
 * Layout of a vertex, composed of multiple attribute descriptors.
 *
 * A `VertexLayout` describes the complete structure of vertices in a [MeshBuffer], which may span
 * across multiple vertex buffers.
 *
 * @param attributes List of [VertexAttributeDescriptor]s defining the vertex layout.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public data class VertexLayout(public val attributes: List<VertexAttributeDescriptor>)
