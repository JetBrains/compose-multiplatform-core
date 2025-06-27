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

import androidx.annotation.IntDef
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.GltfEntity as RtGltfEntity
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.math.Pose

/**
 * GltfModelEntity is a concrete implementation of Entity that hosts a glTF model.
 *
 * Note: The size property of this Entity is always reported as {0, 0, 0}, regardless of the actual
 * size of the model.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class GltfModelEntity
private constructor(rtEntity: RtGltfEntity, entityManager: EntityManager) :
    BaseEntity<RtGltfEntity>(rtEntity, entityManager) {
    // TODO: b/362368652 - Add an OnAnimationEvent() Listener interface

    /** Specifies the current animation state of the GltfModelEntity. */
    @IntDef(AnimationState.PLAYING, AnimationState.STOPPED)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class AnimationStateValue

    public object AnimationState {
        public const val PLAYING: Int = 0
        public const val STOPPED: Int = 1
    }

    public companion object {
        /**
         * Factory method for GltfModelEntity.
         *
         * @param adapter Jetpack XR platform adapter.
         * @param model [GltfModel] which this entity will display.
         * @param pose Pose for this [GltfModelEntity], relative to its parent.
         */
        internal fun create(
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
            model: GltfModel,
            pose: Pose = Pose.Identity,
        ): GltfModelEntity =
            GltfModelEntity(
                adapter.createGltfEntity(pose, model.model, adapter.activitySpaceRootImpl),
                entityManager,
            )

        /**
         * Public factory function for a [GltfModelEntity].
         *
         * This method must be called from the main thread.
         * https://developer.android.com/guide/components/processes-and-threads
         *
         * @param session Session to create the [GltfModel] in.
         * @param model The [GltfModel] this Entity is referencing.
         * @param pose The initial pose of the entity.
         * @return a GltfModelEntity instance
         */
        @MainThread
        @JvmStatic
        @JvmOverloads
        public fun create(
            session: Session,
            model: GltfModel,
            pose: Pose = Pose.Identity,
        ): GltfModelEntity =
            GltfModelEntity.create(
                session.platformAdapter,
                session.scene.entityManager,
                model,
                pose
            )
    }

    /** Returns the current animation state of this glTF entity. */
    @AnimationStateValue
    public fun getAnimationState(): Int {
        return when (rtEntity.animationState) {
            RtGltfEntity.AnimationState.PLAYING -> return AnimationState.PLAYING
            RtGltfEntity.AnimationState.STOPPED -> return AnimationState.STOPPED
            else -> AnimationState.STOPPED
        }
    }

    /**
     * Starts the animation with the given name.
     *
     * This method must be called from the main thread.
     * https://developer.android.com/guide/components/processes-and-threads
     *
     * @param animationName The name of the animation to start. If null, the first animation found
     *   in the glTF will be played.
     * @param loop Whether the animation should loop.
     */
    @MainThread
    @JvmOverloads
    public fun startAnimation(loop: Boolean, animationName: String? = null) {
        rtEntity.startAnimation(loop, animationName)
    }

    /**
     * Stops the animation of the glTF entity.
     *
     * This method must be called from the main thread.
     * https://developer.android.com/guide/components/processes-and-threads
     */
    @MainThread
    public fun stopAnimation() {
        rtEntity.stopAnimation()
    }

    /**
     * Sets a material override for a mesh in the glTF model.
     *
     * This method must be called from the main thread.
     * https://developer.android.com/guide/components/processes-and-threads
     *
     * If the material is not created or the mesh name is not found in the glTF model, this method
     * will throw an IllegalStateException.
     *
     * @param material The material to use for the mesh.
     * @param meshName The name of the mesh to use the material for.
     */
    @MainThread
    public fun setMaterialOverride(material: Material, meshName: String) {
        rtEntity.setMaterialOverride(material.material!!, meshName)
    }
}
