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

import android.view.Surface
import androidx.annotation.IntDef
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.SurfaceEntity as RtSurfaceEntity
import androidx.xr.runtime.math.Pose

/**
 * SurfaceEntity is a concrete implementation of Entity that hosts a StereoSurface Canvas. The
 * entity creates and owns an Android Surface into which the application can render stereo image
 * content. This Surface is then texture mapped to the canvas, and if a stereoscopic StereoMode is
 * specified, then the User will see left and right eye content mapped to the appropriate display.
 *
 * Note that it is not currently possible to synchronize CanvasShape and StereoMode changes with
 * application rendering or video decoding. Applications are advised to carefully hide this entity
 * around transitions to manage glitchiness.
 *
 * @property canvasShape The [CanvasShape] which describes the mesh to which the Surface is mapped.
 * @property stereoMode The [StereoMode] which describes how parts of the surface are displayed to
 *   the user's eyes.
 * @property dimensions The dimensions of the canvas in the local spatial coordinate system of the
 *   entity.
 * @property alphaMaskTexture The texture to be composited into the alpha channel of the surface. If
 *   null, the alpha mask will be disabled.
 * @property auxiliaryAlphaMaskTexture The texture to be composited into the alpha channel of the
 *   secondary view of the surface. This is only used for interleaved stereo content. If null, the
 *   alpha mask will be disabled.
 * @property featherRadiusX a [Float] which controls the canvas-relative radius of the edge fadeout
 *   on the left and right edges of the SurfaceEntity canvas.
 * @property featherRadiusY a [Float] which controls the canvas-relative radius of the edge fadeout
 *   on the top and bottom edges of the SurfaceEntity canvas.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SurfaceEntity
private constructor(
    rtEntity: RtSurfaceEntity,
    entityManager: EntityManager,
    canvasShape: CanvasShape,
) : BaseEntity<RtSurfaceEntity>(rtEntity, entityManager) {

    /** Represents the shape of the Canvas that backs a SurfaceEntity. */
    public abstract class CanvasShape private constructor() {
        public open val dimensions: Dimensions = Dimensions(0.0f, 0.0f, 0.0f)

        // A Quad-shaped canvas. Width and height are represented in the local spatial coordinate
        // system of the entity. (0,0,0) is the center of the canvas.
        public class Quad(public val width: Float, public val height: Float) : CanvasShape() {
            override val dimensions: Dimensions
                get() = Dimensions(width, height, 0.0f)
        }

        // An inwards-facing sphere-shaped canvas, centered at (0,0,0) in the local coordinate
        // space.
        // This is intended to be used by setting the entity's pose to the user's head pose.
        // Radius is represented in the local spatial coordinate system of the entity.
        // The center of the Surface will be mapped to (0, 0, -radius) in the local coordinate
        // space,
        // and UV's are applied from positive X to negative X in an equirectangular projection.
        public class Vr360Sphere(public val radius: Float) : CanvasShape() {
            override val dimensions: Dimensions
                get() = Dimensions(radius * 2, radius * 2, radius * 2)
        }

        // An inwards-facing hemisphere-shaped canvas, where (0,0,0) is the center of the base of
        // the
        // hemisphere. Radius is represented in the local spatial coordinate system of the entity.
        // This is intended to be used by setting the entity's pose to the user's head pose.
        // The center of the Surface will be mapped to (0, 0, -radius) in the local coordinate
        // space,
        // and UV's are applied from positive X to negative X in an equirectangular projection.
        public class Vr180Hemisphere(public val radius: Float) : CanvasShape() {
            override val dimensions: Dimensions
                get() = Dimensions(radius * 2, radius * 2, radius)
        }
    }

    /**
     * Specifies how the surface content will be routed for stereo viewing. Applications must render
     * into the surface in accordance with what is specified here in order for the compositor to
     * correctly produce a stereoscopic view to the user.
     *
     * Values here match values from androidx.media3.common.C.StereoMode in
     * //third_party/java/android_libs/media:common
     */
    @IntDef(StereoMode.MONO, StereoMode.TOP_BOTTOM, StereoMode.SIDE_BY_SIDE)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class StereoModeValue

    public object StereoMode {
        // Each eye will see the entire surface (no separation)
        public const val MONO: Int = 0
        // The [bottom, top] halves of the surface will map to [left, right] eyes
        public const val TOP_BOTTOM: Int = 1
        // The [left, right] halves of the surface will map to [left, right] eyes
        public const val SIDE_BY_SIDE: Int = 2
        // Multiview video, [primary, auxiliary] views will map to [left, right] eyes
        public const val MULTIVIEW_LEFT_PRIMARY: Int = 4
        // Multiview video, [primary, auxiliary] views will map to [right, left] eyes
        public const val MULTIVIEW_RIGHT_PRIMARY: Int = 5
    }

    public companion object {
        private fun getRtStereoMode(stereoMode: Int): Int {
            return when (stereoMode) {
                StereoMode.MONO -> RtSurfaceEntity.StereoMode.MONO
                StereoMode.TOP_BOTTOM -> RtSurfaceEntity.StereoMode.TOP_BOTTOM
                StereoMode.MULTIVIEW_LEFT_PRIMARY ->
                    RtSurfaceEntity.StereoMode.MULTIVIEW_LEFT_PRIMARY
                StereoMode.MULTIVIEW_RIGHT_PRIMARY ->
                    RtSurfaceEntity.StereoMode.MULTIVIEW_RIGHT_PRIMARY
                else -> RtSurfaceEntity.StereoMode.SIDE_BY_SIDE
            }
        }

        /**
         * Factory method for SurfaceEntity.
         *
         * @param adapter JxrPlatformAdapter to use.
         * @param entityManager A SceneCore EntityManager
         * @param stereoMode An [Int] which defines how surface subregions map to eyes
         * @param pose Pose for this StereoSurface entity, relative to its parent.
         * @param canvasShape The [CanvasShape] which describes the spatialized shape of the canvas.
         */
        internal fun create(
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
            stereoMode: Int = StereoMode.SIDE_BY_SIDE,
            pose: Pose = Pose.Identity,
            canvasShape: CanvasShape = CanvasShape.Quad(1.0f, 1.0f),
        ): SurfaceEntity {
            val rtCanvasShape =
                when (canvasShape) {
                    is CanvasShape.Quad ->
                        RtSurfaceEntity.CanvasShape.Quad(canvasShape.width, canvasShape.height)
                    is CanvasShape.Vr360Sphere ->
                        RtSurfaceEntity.CanvasShape.Vr360Sphere(canvasShape.radius)
                    is CanvasShape.Vr180Hemisphere ->
                        RtSurfaceEntity.CanvasShape.Vr180Hemisphere(canvasShape.radius)
                    else -> throw IllegalArgumentException("Unsupported canvas shape: $canvasShape")
                }
            return SurfaceEntity(
                adapter.createSurfaceEntity(
                    getRtStereoMode(stereoMode),
                    rtCanvasShape,
                    pose,
                    adapter.activitySpaceRootImpl,
                ),
                entityManager,
                canvasShape,
            )
        }

        /**
         * Public factory function for a SurfaceEntity.
         *
         * This method must be called from the main thread.
         * https://developer.android.com/guide/components/processes-and-threads
         *
         * @param session Session to create the SurfaceEntity in.
         * @param stereoMode Stereo mode for the surface.
         * @param pose Pose of this entity relative to its parent, default value is Identity.
         * @param canvasShape The [CanvasShape] which describes the spatialized shape of the canvas.
         * @return a SurfaceEntity instance
         */
        @MainThread
        @JvmOverloads
        @JvmStatic
        public fun create(
            session: Session,
            stereoMode: Int = SurfaceEntity.StereoMode.SIDE_BY_SIDE,
            pose: Pose = Pose.Identity,
            canvasShape: CanvasShape = CanvasShape.Quad(1.0f, 1.0f),
        ): SurfaceEntity =
            SurfaceEntity.create(
                session.platformAdapter,
                session.entityManager,
                stereoMode,
                pose,
                canvasShape,
            )
    }

    /**
     * Controls how the surface content will be routed for stereo viewing. Applications must render
     * into the surface in accordance with what is specified here in order for the compositor to
     * correctly produce a stereoscopic view to the user.
     *
     * Values must be one of the values from [StereoMode].
     */
    public var stereoMode: Int
        get() = rtEntity.stereoMode
        @MainThread
        set(value) {
            rtEntity.stereoMode = getRtStereoMode(value)
        }

    /**
     * Returns the dimensions of the Entity.
     *
     * This is the size of the canvas in the local spatial coordinate system of the entity. This
     * field cannot be directly set - to update the dimensions of the canvas, update the value of
     * [canvasShape].
     */
    public val dimensions: Dimensions
        get() = rtEntity.dimensions.toDimensions()

    /**
     * The shape of the canvas that backs the Entity.
     *
     * Updating this value will alter the dimensions of the Entity.
     */
    public var canvasShape: CanvasShape = canvasShape
        @MainThread
        set(value) {
            val rtCanvasShape =
                when (value) {
                    is CanvasShape.Quad ->
                        RtSurfaceEntity.CanvasShape.Quad(value.width, value.height)
                    is CanvasShape.Vr360Sphere ->
                        RtSurfaceEntity.CanvasShape.Vr360Sphere(value.radius)
                    is CanvasShape.Vr180Hemisphere ->
                        RtSurfaceEntity.CanvasShape.Vr180Hemisphere(value.radius)
                    else -> throw IllegalArgumentException("Unsupported canvas shape: $value")
                }
            rtEntity.canvasShape = rtCanvasShape
            field = value
        }

    /**
     * The texture to be composited into the alpha channel of the surface. If null, the alpha mask
     * will be disabled.
     */
    public var primaryAlphaMaskTexture: Texture? = null
        @MainThread
        set(value) {
            rtEntity.setPrimaryAlphaMaskTexture(value?.texture)
            field = value
        }

    /**
     * The texture to be composited into the alpha channel of the secondary view of the surface.
     * This is only used for interleaved stereo content. If null, the alpha mask will be disabled.
     */
    public var auxiliaryAlphaMaskTexture: Texture? = null
        @MainThread
        set(value) {
            rtEntity.setAuxiliaryAlphaMaskTexture(value?.texture)
            field = value
        }

    /**
     * Controls the canvas-relative radius of the edge fadeout on the left and right edges of the
     * SurfaceEntity canvas. A radius of 0.05 represents 5% of the width of the visible canvas
     * surface. Please note that this is scaled by the aspect ratio of Quad-shaped canvases.
     *
     * Applications are encouraged to set this to 0.0 on 360 canvases. The behavior is only defined
     * between [0.0f - 0.5f]. Default value is 0.0f.
     *
     * Setter must be called from the main thread.
     */
    public var featherRadiusX: Float
        get() = rtEntity.featherRadiusX
        @MainThread
        set(value) {
            rtEntity.featherRadiusX = value
        }

    /**
     * Controls the canvas-relative radius of the edge fadeout on the top and bottom edges of the
     * SurfaceEntity canvas. A radius of 0.05 represents 5% of the height of the visible canvas
     * surface. Please note that this is scaled by the aspect ratio of Quad-shaped canvases.
     *
     * Applications are encouraged to set this to 0.0 on 360 canvases. The behavior is only defined
     * between [0.0f - 0.5f]. Default value is 0.0f.
     *
     * Setter must be called from the main thread.
     */
    public var featherRadiusY: Float
        get() = rtEntity.featherRadiusY
        @MainThread
        set(value) {
            rtEntity.featherRadiusY = value
        }

    /**
     * Returns a surface into which the application can render stereo image content.
     *
     * This method must be called from the main thread.
     * https://developer.android.com/guide/components/processes-and-threads
     */
    @MainThread
    public fun getSurface(): Surface {
        return rtEntity.surface
    }
}
