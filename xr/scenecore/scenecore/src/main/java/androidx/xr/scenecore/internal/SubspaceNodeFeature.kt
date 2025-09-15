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

package androidx.xr.scenecore.internal

import androidx.annotation.RestrictTo
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3

/** Represents an entity that manages a subspace node. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface SubspaceNodeFeature : RenderingFeature {
    /** The size of the [SubspaceNodeFeature] in meters, in unscaled local space. */
    public var size: Dimensions

    /**
     * Set after Entity's setPose.
     *
     * @param pose the pose
     */
    public fun setPose(pose: Pose)

    /**
     * Set after Entity's setScale.
     *
     * @param scaleActivity the scale from activity space
     */
    public fun setScale(scaleActivity: Vector3)

    /**
     * Set after Entity's setAlpha
     *
     * @param alpha the alpha
     */
    public fun setAlpha(alpha: Float)

    /**
     * Set after Entity's setHidden.
     *
     * @param hidden whether to hide this Entity
     */
    public fun setHidden(hidden: Boolean)
}
