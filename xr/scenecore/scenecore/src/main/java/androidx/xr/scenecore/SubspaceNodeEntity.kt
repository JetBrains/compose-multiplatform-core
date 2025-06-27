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

package androidx.xr.scenecore

import androidx.annotation.RestrictTo
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.SubspaceNodeEntity as RtSubspaceNodeEntity
import com.google.androidxr.splitengine.SubspaceNode

/**
 * Represents an entity that manages a subspace node.
 *
 * <p>This class manages the pose and size of the subspace node enclosed by this entity, and allows
 * for Entity features (such as managing parents and children or attaching user input Components) to
 * be used with split-engine SubspaceNodes.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SubspaceNodeEntity
private constructor(rtEntity: RtSubspaceNodeEntity, entityManager: EntityManager) :
    BaseEntity<RtSubspaceNodeEntity>(rtEntity, entityManager) {

    /** The size of the [SubspaceNodeEntity] in meters, in unscaled local space. */
    public var size: Dimensions
        get() = rtEntity.size.toDimensions()
        set(value) {
            rtEntity.size = value.toRtDimensions()
        }

    public companion object {
        /**
         * Creates a [SubspaceNodeEntity] from a [SubspaceNode] with a given [Dimensions].
         *
         * @param session The [Session].
         * @param subspaceNode The [SubspaceNode] to create the [SubspaceNodeEntity] from.
         * @param size The initial [Dimensions] of the [SubspaceNodeEntity] in meters in unscaled
         *   local space.
         * @return The created [SubspaceNodeEntity].
         */
        @JvmStatic
        public fun create(
            session: Session,
            subspaceNode: SubspaceNode,
            size: Dimensions,
        ): SubspaceNodeEntity =
            SubspaceNodeEntity(
                session.platformAdapter.createSubspaceNodeEntity(
                    subspaceNode,
                    size.toRtDimensions()
                ),
                session.scene.entityManager,
            )
    }
}
