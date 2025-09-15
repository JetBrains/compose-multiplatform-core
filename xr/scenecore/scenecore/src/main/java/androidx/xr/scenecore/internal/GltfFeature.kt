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
import java.util.concurrent.Executor

/** Provide the rendering implementation for [GltfEntity] */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface GltfFeature : RenderingFeature {

    /** Returns the current animation state of the glTF entity. */
    public val animationState: Int

    /**
     * Starts the animation with the given name.
     *
     * @param animationName The name of the animation to start. If null is supplied, will play the
     *   first animation found in the glTF.
     * @param loop Whether the animation should loop.
     * @param executor The Entity's executor to use for the animation.
     */
    public fun startAnimation(loop: Boolean, animationName: String?, executor: Executor)

    /** Stops the animation of the glTF entity. */
    public fun stopAnimation()

    /**
     * Sets a material override for a mesh in the glTF model.
     *
     * @param material The material to use for the mesh.
     * @param meshName The name of the mesh to use the material for.
     */
    public fun setMaterialOverride(material: MaterialResource, meshName: String)

    /**
     * Sets whether the collider is enabled.
     *
     * @param enableCollider Whether the collider is enabled.
     */
    public fun setColliderEnabled(enableCollider: Boolean)
}
