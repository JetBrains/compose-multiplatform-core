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

import android.content.Context
import android.view.View
import androidx.annotation.RestrictTo
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.PanelEntity as RtPanelEntity
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3

/** Provides implementations for common Panel functionality. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public sealed class BasePanelEntity<out RtPanelEntityType : RtPanelEntity>(
    private val rtPanelEntity: RtPanelEntityType,
    entityManager: EntityManager,
) : BaseEntity<RtPanelEntity>(rtPanelEntity, entityManager) {

    /**
     * Sets the corner radius of the PanelEntity.
     *
     * @param radius The radius of the corners, in meters.
     * @throws IllegalArgumentException if radius is <= 0.0f.
     */
    public fun setCornerRadius(radius: Float) {
        rtPanelEntity.cornerRadius = radius
    }

    /** Gets the corner radius of this PanelEntity in meters. Has a default value of 0. */
    public fun getCornerRadius(): Float {
        return rtPanelEntity.cornerRadius
    }

    /**
     * Returns the dimensions in local space, in unscaled meters for this panel entity.
     *
     * <p>
     * Users of this api can convert this entity's local space dimensions to real world meters by
     * multiplying the local space dimensions with relative scale of this entity in unscaled world
     * space i.e. real meters = local space dimensions getScale(Space.REAL_WORLD) and vice versa.
     * For example a panel entity 1x1 dimensions in local space will look 2x2 meters in real world
     * if the relative scale applied to this entity in the world space is 2.
     */
    public fun getSize(): Dimensions {
        return rtPanelEntity.size.toDimensions()
    }

    /** Returns the dimensions in Pixels for this panel entity. */
    public fun getSizeInPixels(): PixelDimensions {
        return rtPanelEntity.sizeInPixels.toPixelDimensions()
    }

    /**
     * Gets the number of pixels per meter for this panel. This value reflects changes to scale,
     * including parent scale.
     *
     * @return Vector3 scale applied to pixels within the Panel. (Z will be 0)
     */
    @Deprecated("This api will be removed in a future release.")
    public fun getPixelDensity(): Vector3 {
        return rtPanelEntity.pixelDensity
    }

    /**
     * Sets the size in meters for the surface on which the View is laid out. The dimensions
     * provided are unscaled meters in the local space.
     *
     * <p>
     * Users of this api can convert this entity's local space dimensions to real world meters by
     * multiplying the local space dimensions with relative scale of this entity in unscaled world
     * space i.e. real meters = local space dimensions * getScale(Space.REAL_WORLD) and vice versa.
     * For example a panel entity 1x1 dimensions in local space will look 2x2 meters in real world
     * if the relative scale applied to this entity in the world space is 2.
     *
     * @param dimensions Dimensions in meters in local space.
     */
    public fun setSize(dimensions: Dimensions) {
        rtPanelEntity.size = dimensions.toRtDimensions()
    }

    /**
     * Sets the size in pixels for the surface on which the view is laid out. This essentially sets
     * the resolution of underlying surface. The dimensions provided are in pixels.
     *
     * <p>
     * This API doesn't do any scale compensation to pixel dimensions provided.
     *
     * @param pixelDimensions Dimensions in pixels.
     */
    public fun setSizeInPixels(pixelDimensions: PixelDimensions) {
        rtPanelEntity.sizeInPixels = pixelDimensions.toRtPixelDimensions()
    }
}

/** PanelEntity creates a spatial panel in Android XR. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public open class PanelEntity
internal constructor(
    rtEntity: RtPanelEntity,
    entityManager: EntityManager,
    // TODO(ricknels): move isMainPanelEntity check to JxrPlatformAdapter.
    public val isMainPanelEntity: Boolean = false,
) : BasePanelEntity<RtPanelEntity>(rtEntity, entityManager) {

    public companion object {
        internal fun create(
            context: Context,
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
            view: View,
            dimensions: Dimensions,
            name: String,
            pose: Pose = Pose.Identity,
        ): PanelEntity =
            PanelEntity(
                adapter.createPanelEntity(
                    context,
                    pose,
                    view,
                    dimensions.toRtDimensions(),
                    name,
                    adapter.activitySpaceRootImpl,
                ),
                entityManager,
            )

        internal fun create(
            context: Context,
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
            view: View,
            pixelDimensions: PixelDimensions,
            name: String,
            pose: Pose = Pose.Identity,
        ): PanelEntity =
            PanelEntity(
                adapter.createPanelEntity(
                    context,
                    pose,
                    view,
                    pixelDimensions.toRtPixelDimensions(),
                    name,
                    adapter.activitySpaceRootImpl,
                ),
                entityManager,
            )

        /**
         * Public factory function for a spatialized PanelEntity.
         *
         * @param session Session to create the PanelEntity in.
         * @param view View to embed in this panel entity.
         * @param dimensions Dimensions for the underlying surface for the given view in meters.
         * @param name Name of the panel.
         * @param pose Pose of this entity relative to its parent, default value is Identity.
         * @return a PanelEntity instance.
         */
        @JvmOverloads
        @JvmStatic
        public fun create(
            session: Session,
            view: View,
            dimensions: Dimensions,
            name: String,
            pose: Pose = Pose.Identity,
        ): PanelEntity =
            PanelEntity.create(
                session.activity,
                session.platformAdapter,
                session.scene.entityManager,
                view,
                dimensions,
                name,
                pose,
            )

        /**
         * Public factory function for a spatialized PanelEntity.
         *
         * @param session Session to create the PanelEntity in.
         * @param view View to embed in this panel entity.
         * @param pixelDimensions Dimensions for the underlying surface for the given view in
         *   pixels.
         * @param name Name of the panel.
         * @param pose Pose of this entity relative to its parent, default value is Identity.
         * @return a PanelEntity instance.
         */
        @JvmOverloads
        @JvmStatic
        public fun create(
            session: Session,
            view: View,
            pixelDimensions: PixelDimensions,
            name: String,
            pose: Pose = Pose.Identity,
        ): PanelEntity =
            PanelEntity.create(
                session.activity,
                session.platformAdapter,
                session.scene.entityManager,
                view,
                pixelDimensions,
                name,
                pose,
            )

        /** Returns the PanelEntity backed by the main window for the Activity. */
        internal fun createMainPanelEntity(
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
        ): PanelEntity =
            PanelEntity(adapter.mainPanelEntity, entityManager, isMainPanelEntity = true)
    }
}
