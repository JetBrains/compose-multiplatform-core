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

import android.annotation.SuppressLint
import android.view.Surface
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.xr.runtime.Config
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.LifecycleManager
import androidx.xr.runtime.internal.SurfaceEntity as RtSurfaceEntity
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.FloatSize3d
import androidx.xr.runtime.math.Pose

/**
 * SurfaceEntity is an [Entity] that hosts a [Surface], which will be texture mapped onto the
 * [Shape]. If a stereoscopic [StereoMode] is specified, then the User will see left and right eye
 * content mapped to the appropriate display.
 *
 * Note that it is not currently possible to synchronize [Shape] and [StereoMode] changes with
 * application rendering or video decoding. Applications are advised to carefully hide this entity
 * around state transitions (for example in response to video events) to manage glitchiness.
 *
 * @property shape The [Shape] which describes the mesh to which the Surface is mapped.
 * @property stereoMode The [StereoMode] which describes how parts of the surface are displayed to
 *   the user's eyes.
 * @property dimensions The dimensions of the canvas in the local spatial coordinate system of the
 *   entity.
 * @property edgeFeatheringParams The [EdgeFeatheringParams] which describes the edge fading effects
 *   for the surface.
 */
public class SurfaceEntity
private constructor(
    private val lifecycleManager: LifecycleManager,
    rtEntity: RtSurfaceEntity,
    entityManager: EntityManager,
    shape: Shape,
    private var disposed: Boolean = false, // TODO b/427314036: remove this
) : BaseEntity<RtSurfaceEntity>(rtEntity, entityManager) {

    /** Represents the shape of the Canvas that backs a SurfaceEntity. */
    public interface Shape {

        /**
         * A Quadrilateral-shaped canvas. Width and height are expressed in the X and Y axis in the
         * local spatial coordinate system of the entity. (0,0) is the center of the Quad mesh; the
         * lower-left corner of the Quad is at (-width/2, -height/2).
         *
         * @property extents The size of the Quad in the local spatial coordinate system of the
         *   entity.
         */
        public class Quad(public val extents: FloatSize2d) : Shape {}

        /**
         * cal An inwards-facing sphere-shaped mesh, centered at (0,0,0) in the local coordinate
         * space. This is intended to be used by setting the entity's pose to the user's head pose.
         * Radius is represented in the local spatial coordinate system of the entity. The center of
         * the Surface will be mapped to (0, 0, -radius) in the local coordinate space, and texture
         * coordinate UVs are applied from positive X to negative X in an equirectangular
         * projection. This means that if this Entity is set to the user's head pose, then they will
         * be looking at the center of the video if it were viewed in a 2D player.
         *
         * @property radius The radius of the sphere in the local spatial coordinate system of the
         *   entity.
         */
        public class Sphere(public val radius: Float) : Shape {}

        /**
         * An inwards-facing hemisphere-shaped canvas, where (0,0,0) is the center of the base of
         * the hemisphere. Radius is represented in the local spatial coordinate system of the
         * entity. This is intended to be used by setting the entity's pose to the user's head pose.
         * The center of the Surface will be mapped to (0, 0, -radius) in the local coordinate
         * space, and texture coordinate UV's are applied from positive X to negative X in an
         * equirectangular projection.
         *
         * @property radius The radius of the hemisphere in the local spatial coordinate system of
         *   the entity.
         */
        public class Hemisphere(public val radius: Float) : Shape {}
    }

    /** Represents edge fading effects for a SurfaceEntity. */
    public abstract class EdgeFeatheringParams private constructor() {
        /**
         * @property leftRight a [Float] which controls the shape-relative radius of the edge
         *   fadeout on the left and right edges of the SurfaceEntity canvas.
         * @property topBottom a [Float] which controls the shape-relative radius of the edge
         *   fadeout on the top and bottom edges of the SurfaceEntity canvas.
         *
         * A radius of 0.05 represents 5% of the width of the visible canvas surface. Please note
         * that this is scaled by the aspect ratio of Quad-shaped canvases.
         *
         * Applications are encouraged to use [NoFeathering] on Spherical canvases. The behavior is
         * only defined for values between [0.0f - 0.5f]. Default values are 0.0f.
         */
        public class RectangleFeather(
            @FloatRange(from = 0.0, to = 0.5) public val leftRight: Float = 0.0f,
            @FloatRange(from = 0.0, to = 0.5) public val topBottom: Float = 0.0f,
        ) : EdgeFeatheringParams() {}

        /** Applies no edge fading to any canvas. */
        public class NoFeathering : EdgeFeatheringParams() {}
    }

    @IntDef(
        SurfaceProtection.SURFACE_PROTECTION_NONE,
        SurfaceProtection.SURFACE_PROTECTION_PROTECTED,
    )
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class SurfaceProtectionValue

    /**
     * Specifies whether the [Surface] which backs this [Entity] should be backed by
     * [android.hardware.HardwareBuffer]s with the USAGE_PROTECTED_CONTENT flag set. These buffers
     * support hardware paths for decoding protected content.
     *
     * @see https://developer.android.com/reference/android/media/MediaDrm for more details.
     */
    public object SurfaceProtection {
        /**
         * The Surface content is not protected. Non-protected content can be decoded into this
         * surface. Protected content can not be decoded into this Surface. Screen captures of the
         * SurfaceEntity will show the Surface content.
         */
        public const val SURFACE_PROTECTION_NONE: Int = 0

        /**
         * The Surface content is protected. Non-protected content can be decoded into this surface.
         * Protected content can be decoded into this Surface. Screen captures of the SurfaceEntity
         * will redact the Surface content.
         */
        public const val SURFACE_PROTECTION_PROTECTED: Int = 1
    }

    @IntDef(SuperSampling.SUPER_SAMPLING_NONE, SuperSampling.SUPER_SAMPLING_PENTAGON)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class SuperSamplingValue

    /**
     * Specifies whether super sampling should be enabled for this surface. Super sampling can
     * improve text clarity at a performance cost.
     */
    public object SuperSampling {
        /** Super sampling is disabled. */
        public const val SUPER_SAMPLING_NONE: Int = 0

        /**
         * Super sampling is enabled with a default sampling pattern. This is the value that is set
         * if SuperSampling is not specified when the Entity is created.
         */
        public const val SUPER_SAMPLING_PENTAGON: Int = 1
    }

    /**
     * Specifies how the surface content will be routed for stereo viewing. Applications must render
     * into the surface in accordance with what they provided here in order for the compositor to
     * correctly produce a stereoscopic view to the user.
     *
     * Values here match values from [androidx.media3.common.C.StereoMode].
     *
     * @see https://developer.android.com/reference/androidx/media3/common/C.StereoMode
     */
    @IntDef(
        StereoMode.STEREO_MODE_MONO,
        StereoMode.STEREO_MODE_TOP_BOTTOM,
        StereoMode.STEREO_MODE_SIDE_BY_SIDE,
        StereoMode.STEREO_MODE_MULTIVIEW_LEFT_PRIMARY,
        StereoMode.STEREO_MODE_MULTIVIEW_RIGHT_PRIMARY,
    )
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class StereoModeValue

    public object StereoMode {
        /** Each eye will see the entire surface (no separation) */
        public const val STEREO_MODE_MONO: Int = 0
        /** The [top, bottom] halves of the surface will map to [left, right] eyes */
        public const val STEREO_MODE_TOP_BOTTOM: Int = 1
        /** The [left, right] halves of the surface will map to [left, right] eyes */
        public const val STEREO_MODE_SIDE_BY_SIDE: Int = 2
        /** Multiview video, [primary, auxiliary] views will map to [left, right] eyes */
        public const val STEREO_MODE_MULTIVIEW_LEFT_PRIMARY: Int = 4
        /** Multiview video, [primary, auxiliary] views will map to [right, left] eyes */
        public const val STEREO_MODE_MULTIVIEW_RIGHT_PRIMARY: Int = 5
    }

    /**
     * Color information for the content drawn on a surface. This is used to hint to the system how
     * the content should be rendered depending on display settings.
     *
     * @property colorSpace The color space of the content.
     * @property colorTransfer The transfer function to apply to the content.
     * @property colorRange The color range of the content.
     * @property maxContentLightLevel The maximum brightness of the content (in nits).
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public class ContentColorMetadata(
        @ColorSpaceValue public val colorSpace: Int = ColorSpace.COLOR_SPACE_BT709,
        @ColorTransferValue public val colorTransfer: Int = ColorTransfer.COLOR_TRANSFER_SRGB,
        @ColorRangeValue public val colorRange: Int = ColorRange.COLOR_RANGE_FULL,
        public val maxContentLightLevel: Int = Companion.MAX_CONTENT_LIGHT_LEVEL_UNKNOWN,
    ) {

        /**
         * Specifies the color space of the media asset drawn on the surface.
         *
         * These values are a superset of androidx.media3.common.C.ColorSpace.
         */
        @IntDef(
            ColorSpace.COLOR_SPACE_BT709,
            ColorSpace.COLOR_SPACE_BT601_PAL,
            ColorSpace.COLOR_SPACE_BT2020,
            ColorSpace.COLOR_SPACE_BT601_525,
            ColorSpace.COLOR_SPACE_DISPLAY_P3,
            ColorSpace.COLOR_SPACE_DCI_P3,
            ColorSpace.COLOR_SPACE_ADOBE_RGB,
        )
        @Retention(AnnotationRetention.SOURCE)
        internal annotation class ColorSpaceValue

        public object ColorSpace {
            /** Please see androidx.media3.common.C.COLOR_SPACE_BT709 */
            public const val COLOR_SPACE_BT709: Int = 1
            /** Please see androidx.media3.common.C.COLOR_SPACE_BT601 */
            public const val COLOR_SPACE_BT601_PAL: Int = 2
            /** Please see androidx.media3.common.C.COLOR_SPACE_BT2020 */
            public const val COLOR_SPACE_BT2020: Int = 6
            /** Please see ADataSpace::ADATASPACE_BT601_525 */
            public const val COLOR_SPACE_BT601_525: Int = 0xf0
            /** Please see ADataSpace::ADATASPACE_DISPLAY_P3 */
            public const val COLOR_SPACE_DISPLAY_P3: Int = 0xf1
            /** Please see ADataSpace::ADATASPACE_DCI_P3 */
            public const val COLOR_SPACE_DCI_P3: Int = 0xf2
            /** Please see ADataSpace::ADATASPACE_ADOBE_RGB */
            public const val COLOR_SPACE_ADOBE_RGB: Int = 0xf3
        }

        @IntDef(
            ColorTransfer.COLOR_TRANSFER_LINEAR,
            ColorTransfer.COLOR_TRANSFER_SRGB,
            ColorTransfer.COLOR_TRANSFER_SDR,
            ColorTransfer.COLOR_TRANSFER_GAMMA_2_2,
            ColorTransfer.COLOR_TRANSFER_ST2084,
            ColorTransfer.COLOR_TRANSFER_HLG,
        )
        @Retention(AnnotationRetention.SOURCE)
        internal annotation class ColorTransferValue

        /**
         * Specifies the color transfer function of the media asset drawn on the surface.
         *
         * Enum members cover the transfer functions available in android::ADataSpace Enum values
         * match values from androidx.media3.common.C.ColorTransfer.
         */
        public object ColorTransfer {
            /** Linear transfer characteristic curve. */
            public const val COLOR_TRANSFER_LINEAR: Int = 1
            /** The standard RGB transfer function, used for some SDR use-cases like image input. */
            public const val COLOR_TRANSFER_SRGB: Int = 2
            /**
             * SMPTE 170M transfer characteristic curve used by BT.601/BT.709/BT.2020. This is the
             * curve used by most non-HDR video content.
             */
            public const val COLOR_TRANSFER_SDR: Int = 3
            /** The Gamma 2.2 transfer function, used for some SDR use-cases like tone-mapping. */
            public const val COLOR_TRANSFER_GAMMA_2_2: Int = 10
            /** SMPTE ST 2084 transfer function. This is used by some HDR video content. */
            public const val COLOR_TRANSFER_ST2084: Int = 6
            /**
             * ARIB STD-B67 hybrid-log-gamma transfer function. This is used by some HDR video
             * content.
             */
            public const val COLOR_TRANSFER_HLG: Int = 7
        }

        @IntDef(ColorRange.COLOR_RANGE_FULL, ColorRange.COLOR_RANGE_LIMITED)
        @Retention(AnnotationRetention.SOURCE)
        internal annotation class ColorRangeValue

        /**
         * Specifies the color range of the media asset drawn on the surface.
         *
         * Enum values match values from androidx.media3.common.C.ColorRange.
         */
        public object ColorRange {
            /** Please see android.media.MediaFormat.COLOR_RANGE_FULL */
            public const val COLOR_RANGE_FULL: Int = 1
            /** Please see android.media.MedaiFormat.COLOR_RANGE_LIMITED */
            public const val COLOR_RANGE_LIMITED: Int = 2
        }

        public companion object {
            /**
             * Represents an unknown maximum content light level.
             *
             * Note that the smallest value for this is 1 nit, so this value should only be used if
             * the actual value is unknown or if the content is constant luminance.
             */
            public const val MAX_CONTENT_LIGHT_LEVEL_UNKNOWN: Int = 0

            /**
             * A Default (unset) value for ContentColorMetadata. Setting this will cause the system
             * to render the content according to values set on the underlying [HardwareBuffer]s;
             * these are usually set correctly by the MediaCodec.
             */
            public val DEFAULT_UNSET_CONTENT_COLOR_METADATA: ContentColorMetadata =
                ContentColorMetadata(
                    colorSpace = ColorSpace.COLOR_SPACE_BT709,
                    colorTransfer = ColorTransfer.COLOR_TRANSFER_SRGB,
                    colorRange = ColorRange.COLOR_RANGE_FULL,
                    maxContentLightLevel = MAX_CONTENT_LIGHT_LEVEL_UNKNOWN,
                )

            internal fun getRtColorSpace(colorSpace: Int): Int {
                return when (colorSpace) {
                    ColorSpace.COLOR_SPACE_BT709 -> RtSurfaceEntity.ColorSpace.BT709
                    ColorSpace.COLOR_SPACE_BT601_PAL -> RtSurfaceEntity.ColorSpace.BT601_PAL
                    ColorSpace.COLOR_SPACE_BT2020 -> RtSurfaceEntity.ColorSpace.BT2020
                    ColorSpace.COLOR_SPACE_BT601_525 -> RtSurfaceEntity.ColorSpace.BT601_525
                    ColorSpace.COLOR_SPACE_DISPLAY_P3 -> RtSurfaceEntity.ColorSpace.DISPLAY_P3
                    ColorSpace.COLOR_SPACE_DCI_P3 -> RtSurfaceEntity.ColorSpace.DCI_P3
                    ColorSpace.COLOR_SPACE_ADOBE_RGB -> RtSurfaceEntity.ColorSpace.ADOBE_RGB
                    else -> RtSurfaceEntity.ColorSpace.BT709
                }
            }

            internal fun getRtColorTransfer(colorTransfer: Int): Int {
                return when (colorTransfer) {
                    ColorTransfer.COLOR_TRANSFER_LINEAR -> RtSurfaceEntity.ColorTransfer.LINEAR
                    ColorTransfer.COLOR_TRANSFER_SRGB -> RtSurfaceEntity.ColorTransfer.SRGB
                    ColorTransfer.COLOR_TRANSFER_SDR -> RtSurfaceEntity.ColorTransfer.SDR
                    ColorTransfer.COLOR_TRANSFER_GAMMA_2_2 ->
                        RtSurfaceEntity.ColorTransfer.GAMMA_2_2
                    ColorTransfer.COLOR_TRANSFER_ST2084 -> RtSurfaceEntity.ColorTransfer.ST2084
                    ColorTransfer.COLOR_TRANSFER_HLG -> RtSurfaceEntity.ColorTransfer.HLG
                    else -> RtSurfaceEntity.ColorTransfer.SRGB
                }
            }

            internal fun getRtColorRange(colorRange: Int): Int {
                return when (colorRange) {
                    ColorRange.COLOR_RANGE_FULL -> RtSurfaceEntity.ColorRange.FULL
                    ColorRange.COLOR_RANGE_LIMITED -> RtSurfaceEntity.ColorRange.LIMITED
                    else -> RtSurfaceEntity.ColorRange.FULL
                }
            }
        }
    }

    // TODO b/427314036: remove this once this is enforced within BaseEntity.
    override fun dispose() {
        super.dispose()
        disposed = true
    }

    // TODO b/427314036: remove this once this is enforced within BaseEntity.
    private fun checkDisposed() {
        if (disposed) {
            throw IllegalStateException("Entity is disposed.")
        }
    }

    public companion object {
        private fun getRtStereoMode(stereoMode: Int): Int {
            return when (stereoMode) {
                StereoMode.STEREO_MODE_MONO -> RtSurfaceEntity.StereoMode.MONO
                StereoMode.STEREO_MODE_TOP_BOTTOM -> RtSurfaceEntity.StereoMode.TOP_BOTTOM
                StereoMode.STEREO_MODE_MULTIVIEW_LEFT_PRIMARY ->
                    RtSurfaceEntity.StereoMode.MULTIVIEW_LEFT_PRIMARY
                StereoMode.STEREO_MODE_MULTIVIEW_RIGHT_PRIMARY ->
                    RtSurfaceEntity.StereoMode.MULTIVIEW_RIGHT_PRIMARY
                else -> RtSurfaceEntity.StereoMode.SIDE_BY_SIDE
            }
        }

        private fun getRtSurfaceProtection(surfaceProtection: Int): Int {
            return when (surfaceProtection) {
                SurfaceProtection.SURFACE_PROTECTION_NONE -> RtSurfaceEntity.SurfaceProtection.NONE
                SurfaceProtection.SURFACE_PROTECTION_PROTECTED ->
                    RtSurfaceEntity.SurfaceProtection.PROTECTED
                else -> RtSurfaceEntity.SurfaceProtection.NONE
            }
        }

        private fun getRtSuperSampling(superSampling: Int): Int {
            return when (superSampling) {
                SuperSampling.SUPER_SAMPLING_NONE -> RtSurfaceEntity.SuperSampling.NONE
                SuperSampling.SUPER_SAMPLING_PENTAGON -> RtSurfaceEntity.SuperSampling.DEFAULT
                else -> RtSurfaceEntity.SuperSampling.DEFAULT
            }
        }

        /**
         * Factory method for SurfaceEntity.
         *
         * @param lifecycleManager A SceneCore LifecycleManager
         * @param adapter JxrPlatformAdapter to use.
         * @param entityManager A SceneCore EntityManager
         * @param stereoMode An [Int] which defines how surface subregions map to eyes
         * @param pose Pose for this StereoSurface entity, relative to its parent.
         * @param shape The [Shape] which describes the spatialized shape of the canvas.
         * @param surfaceProtection The Int member of [SurfaceProtection] which describes whether
         *   DRM is enabled for the surface - which will create protected hardware buffers for
         *   presentation.
         * @param contentColorMetadata The [ContentColorMetadata] of the content (nullable).
         * @param superSampling The [SuperSampling] which describes whether super sampling is
         *   enabled for the surface.
         * @return a SurfaceEntity instance
         */
        internal fun create(
            lifecycleManager: LifecycleManager,
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
            @StereoModeValue stereoMode: Int = StereoMode.STEREO_MODE_MONO,
            pose: Pose = Pose.Identity,
            shape: Shape = Shape.Quad(FloatSize2d(1.0f, 1.0f)),
            @SurfaceProtectionValue
            surfaceProtection: Int = SurfaceProtection.SURFACE_PROTECTION_NONE,
            contentColorMetadata: ContentColorMetadata? = null,
            @SuperSamplingValue superSampling: Int = SuperSampling.SUPER_SAMPLING_PENTAGON,
        ): SurfaceEntity {
            val rtShape =
                when (shape) {
                    is Shape.Quad -> RtSurfaceEntity.Shape.Quad(shape.extents)
                    is Shape.Sphere -> RtSurfaceEntity.Shape.Sphere(shape.radius)
                    is Shape.Hemisphere -> RtSurfaceEntity.Shape.Hemisphere(shape.radius)
                    else -> throw IllegalArgumentException("Unsupported shape: $shape")
                }
            val surfaceEntity =
                SurfaceEntity(
                    lifecycleManager,
                    adapter.createSurfaceEntity(
                        getRtStereoMode(stereoMode),
                        pose,
                        rtShape,
                        getRtSurfaceProtection(surfaceProtection),
                        getRtSuperSampling(superSampling),
                        adapter.activitySpaceRootImpl,
                    ),
                    entityManager,
                    shape,
                )
            surfaceEntity.contentColorMetadata = contentColorMetadata
            return surfaceEntity
        }

        /**
         * Public factory function for a SurfaceEntity.
         *
         * @param session Session to create the SurfaceEntity in.
         * @param stereoMode Stereo mode for the surface.
         * @param pose Pose of this entity relative to its parent, default value is Identity.
         * @param shape The [Shape] which describes the spatialized shape of the canvas.
         * @param surfaceProtection The [SurfaceProtection] which describes whether the hosted
         *   surface should support Widevine DRM.
         * @param superSampling The [SuperSampling] which describes whether super sampling is
         *   enabled for the surface.
         * @return a SurfaceEntity instance
         */
        @MainThread
        @JvmOverloads
        @JvmStatic
        public fun create(
            session: Session,
            pose: Pose = Pose.Identity,
            shape: Shape = Shape.Quad(FloatSize2d(1.0f, 1.0f)),
            @StereoModeValue stereoMode: Int = StereoMode.STEREO_MODE_MONO,
            @SuperSamplingValue superSampling: Int = SuperSampling.SUPER_SAMPLING_PENTAGON,
            @SurfaceProtectionValue
            surfaceProtection: Int = SurfaceProtection.SURFACE_PROTECTION_NONE,
        ): SurfaceEntity =
            SurfaceEntity.create(
                session.runtime.lifecycleManager,
                session.platformAdapter,
                session.scene.entityManager,
                stereoMode,
                pose,
                shape,
                surfaceProtection,
                null,
                superSampling,
            )
    }

    /**
     * Controls how the surface content will be routed for stereo viewing. Applications must render
     * into the surface in accordance with what is specified here in order for the compositor to
     * correctly produce a stereoscopic view to the user.
     *
     * @throws IllegalStateException when setting this value if the Entity has been disposed.
     */
    @StereoModeValue
    public var stereoMode: Int
        get() {
            checkDisposed()
            return rtEntity.stereoMode
        }
        @MainThread
        set(value) {
            checkDisposed()
            rtEntity.stereoMode = getRtStereoMode(value)
        }

    /**
     * Returns the size of the canvas in the local spatial coordinate system of the entity.
     *
     * This value is entirely determined by the value of [shape].
     */
    public val dimensions: FloatSize3d
        get() {
            checkDisposed()
            return rtEntity.dimensions.toFloatSize3d()
        }

    /**
     * The shape of the canvas that backs the Entity. Updating this value will alter the
     * [dimensions] of the Entity.
     *
     * @throws IllegalArgumentException if an invalid canvas shape is provided.
     * @throws IllegalStateException when setting this value if the Entity has been disposed.
     */
    public var shape: Shape = shape
        @MainThread
        set(value) {
            checkDisposed()
            val rtShape =
                when (value) {
                    is Shape.Quad -> RtSurfaceEntity.Shape.Quad(value.extents)
                    is Shape.Sphere -> RtSurfaceEntity.Shape.Sphere(value.radius)
                    is Shape.Hemisphere -> RtSurfaceEntity.Shape.Hemisphere(value.radius)
                    else -> throw IllegalArgumentException("Unsupported canvas shape: $value")
                }
            rtEntity.shape = rtShape
            field = value
        }

    /**
     * The texture to be composited into the alpha channel of the surface. If null, the alpha mask
     * will be disabled.
     *
     * @throws IllegalStateException when setting this value if the Entity has been disposed.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public var primaryAlphaMaskTexture: Texture? = null
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        get() {
            checkDisposed()
            return field
        }
        @MainThread
        @SuppressLint("HiddenTypeParameter")
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        set(value) {
            checkDisposed()
            rtEntity.setPrimaryAlphaMaskTexture(value?.texture)
            field = value
        }

    /**
     * The texture to be composited into the alpha channel of the secondary view of the surface.
     * This is only used for interleaved stereo content. If null, the alpha mask will be disabled.
     *
     * @throws IllegalStateException when setting this value if the Entity has been disposed.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public var auxiliaryAlphaMaskTexture: Texture? = null
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        get() {
            checkDisposed()
            return field
        }
        @MainThread
        @SuppressLint("HiddenTypeParameter")
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        set(value) {
            checkDisposed()
            rtEntity.setAuxiliaryAlphaMaskTexture(value?.texture)
            field = value
        }

    /**
     * The [EdgeFeatheringParams] feathering pattern to be used along the edges of the Shape. This
     * value must only be set from the main thread.
     *
     * @throws IllegalStateException when setting this value if the Entity has been disposed.
     */
    public var edgeFeatheringParams: EdgeFeatheringParams = EdgeFeatheringParams.NoFeathering()
        get() {
            checkDisposed()
            return field
        }
        @MainThread
        set(value) {
            checkDisposed()
            val rtEdgeFeather =
                when (value) {
                    is EdgeFeatheringParams.NoFeathering ->
                        RtSurfaceEntity.EdgeFeather.NoFeathering()
                    is EdgeFeatheringParams.RectangleFeather ->
                        RtSurfaceEntity.EdgeFeather.RectangleFeather(
                            value.leftRight,
                            value.topBottom,
                        )
                    else -> throw IllegalArgumentException("Unsupported edge feather: $value")
                }
            rtEntity.edgeFeather = rtEdgeFeather
            field = value
        }

    /**
     * Manages the explicit [ContentColorMetadata] for the surface's content.
     *
     * Describes how the application wants the system renderer to color convert [Surface] content to
     * the Display. When this is null, the system will make a best guess at the appropriate
     * conversion. Most applications will not need to set this - video playback libraries such as
     * ExoPlayer will automatically apply the correct conversion for media playback. Applications
     * rendering to the surface using APIs such as Vulkan are encouraged to use Vulkan extensions to
     * specify the color space and transfer function of their content and leave this value as null.
     *
     * The setter must be called from the main thread.
     *
     * @throws IllegalStateException when setting this value if the Entity has been disposed.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public var contentColorMetadata: ContentColorMetadata? = null
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        get() {
            checkDisposed()
            return if (!rtEntity.contentColorMetadataSet) {
                null
            } else {
                return field
            }
        }
        @MainThread
        @SuppressLint("HiddenTypeParameter")
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        set(value) {
            checkDisposed()
            if (value == null) {
                rtEntity.resetContentColorMetadata()
            } else {
                rtEntity.setContentColorMetadata(
                    ContentColorMetadata.getRtColorSpace(value.colorSpace),
                    ContentColorMetadata.getRtColorTransfer(value.colorTransfer),
                    ContentColorMetadata.getRtColorRange(value.colorRange),
                    value.maxContentLightLevel,
                )
            }
            field = value
        }

    /**
     * Returns a surface into which the application can render stereo image content. Note that
     * android.graphics.Canvas Apis are not currently supported on this Canvas.
     *
     * @throws IllegalStateException if the Entity has been disposed.
     */
    @MainThread
    public fun getSurface(): Surface {
        checkDisposed()
        return rtEntity.surface
    }

    /**
     * Gets the perceived resolution of the entity in the camera view.
     *
     * This API is only intended for use in Full Space Mode and will return
     * [PerceivedResolutionResult.InvalidCameraView] in Home Space Mode.
     *
     * The entity's own rotation and the camera's viewing direction are disregarded; this value
     * represents the dimensions of the entity on the camera view if its largest surface was facing
     * the camera without changing the distance of the entity to the camera.
     *
     * @return A [PerceivedResolutionResult] which encapsulates the outcome:
     *     - [PerceivedResolutionResult.Success] containing the [PixelDimensions] if the calculation
     *       is successful.
     *     - [PerceivedResolutionResult.EntityTooClose] if the entity is too close to the camera.
     *     - [PerceivedResolutionResult.InvalidCameraView] if the camera information required for
     *       the calculation is invalid or unavailable.
     *
     * @throws [IllegalStateException] if [Session.config.headTracking] is set to
     *   [Config.HeadTrackingMode.DISABLED].
     * @see PerceivedResolutionResult
     */
    public fun getPerceivedResolution(): PerceivedResolutionResult {
        checkDisposed()
        check(lifecycleManager.config.headTracking != Config.HeadTrackingMode.DISABLED) {
            "Config.HeadTrackingMode is set to Disabled."
        }
        return rtEntity.getPerceivedResolution().toPerceivedResolutionResult()
    }
}
