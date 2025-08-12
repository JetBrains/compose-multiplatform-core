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

package androidx.xr.runtime.testing

import android.graphics.ImageFormat
import android.media.ImageReader
import android.view.Surface
import androidx.annotation.RestrictTo
import androidx.xr.runtime.internal.Dimensions
import androidx.xr.runtime.internal.PerceivedResolutionResult
import androidx.xr.runtime.internal.SurfaceEntity
import androidx.xr.runtime.internal.SurfaceEntity.CanvasShape
import androidx.xr.runtime.internal.TextureResource

/**
 * Test-only implementation of [SurfaceEntity].
 *
 * Interface for a spatialized Entity which manages an Android Surface. Applications can render to
 * this Surface in various ways, such as via MediaPlayer, ExoPlayer, or custom rendering. The
 * Surface content is texture mapped to the geometric shape defined by the [CanvasShape]. The
 * application can render stereoscopic content into the Surface and specify how it is routed to the
 * User's eyes for stereo viewing using the [stereoMode] property.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FakeSurfaceEntity() : FakeEntity(), SurfaceEntity {
    /**
     * Specifies how the surface content will be routed for stereo viewing. Applications must render
     * into the surface in accordance with what is specified here in order for the compositor to
     * correctly produce a stereoscopic view to the user.
     */
    override var stereoMode: Int = SurfaceEntity.StereoMode.SIDE_BY_SIDE

    /** Specifies the shape of the spatial canvas which the surface is texture mapped to. */
    override var canvasShape: CanvasShape = CanvasShape.Quad(0f, 0f)

    /**
     * Retrieves the dimensions of the "spatial canvas" which the surface is mapped to. These values
     * are not impacted by scale.
     *
     * @return The canvas [Dimensions].
     */
    override val dimensions: Dimensions
        get() = canvasShape.dimensions

    private var _surface: Surface =
        ImageReader.newInstance(1, 1, ImageFormat.YUV_420_888, 1).surface

    /**
     * Retrieves the surface that the Entity will display. The app can write into this surface
     * however it wants, i.e. MediaPlayer, ExoPlayer, or custom rendering.
     *
     * @return an Android [Surface]
     */
    override val surface: Surface
        get() = _surface

    /**
     * For test purposes only. Sets or replaces the underlying [Surface] for this fake entity.
     *
     * <p>This allows tests to provide a specific [Surface] instance, such as one connected to a
     * test-controlled producer, to verify rendering behavior.
     *
     * @param surface The new [Surface] to associate with this entity.
     */
    public fun setSurface(surface: Surface) {
        _surface = surface
    }

    /** For test purposes only. Represents the result of [setPrimaryAlphaMaskTexture]. */
    public var primaryAlphaMask: TextureResource? = null
        private set

    /**
     * The texture to be composited into the alpha channel of the surface. If null, the alpha mask
     * will be disabled.
     *
     * @param alphaMask The primary alpha mask texture.
     */
    override fun setPrimaryAlphaMaskTexture(alphaMask: TextureResource?) {
        primaryAlphaMask = alphaMask
    }

    /** For test purposes only. Represents the result of [setAuxiliaryAlphaMaskTexture] */
    public var auxiliaryAlphaMask: TextureResource? = null
        private set

    /**
     * The texture to be composited into the alpha channel of the auxiliary view of the surface.
     * This is only used for interleaved stereo content. If null, the alpha mask will be disabled.
     *
     * @param alphaMask The auxiliary alpha mask texture.
     */
    override fun setAuxiliaryAlphaMaskTexture(alphaMask: TextureResource?) {
        auxiliaryAlphaMask = alphaMask
    }

    /**
     * For test purposes only.
     *
     * The [PerceivedResolutionResult] that will be returned by [getPerceivedResolution]. This can
     * be modified in tests to simulate different perceived resolution.
     */
    public var perceivedResolutionResult: PerceivedResolutionResult =
        PerceivedResolutionResult.InvalidCameraView()

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
     * @see PerceivedResolutionResult
     */
    override fun getPerceivedResolution(): PerceivedResolutionResult {
        return perceivedResolutionResult
    }

    /** For test purposes only. Specifies the value returned by [contentColorMetadataSet] */
    public var mContentColorMetadataSet: Boolean = false

    /**
     * Indicates whether explicit color information has been set for the surface content. If
     * `false`, the runtime should signal the backend to use its best effort color correction and
     * tonemapping. If `true`, the runtime should inform the backend to use the values specified in
     * [colorSpace], [colorTransfer], [colorRange], and [maxCLL] for color correction and
     * tonemapping of the surface content.
     *
     * This property is typically managed by the `setContentColorMetadata` and
     * `resetContentColorMetadata` methods.
     */
    override val contentColorMetadataSet: Boolean
        get() = mContentColorMetadataSet

    private var _colorSpace: Int = SurfaceEntity.ColorSpace.BT709

    /**
     * The active color space of the media asset drawn on the surface. Use constants from
     * [SurfaceEntity.ColorSpace]. This value is used if [contentColorMetadataSet] is `true`.
     */
    override val colorSpace: Int
        get() = _colorSpace

    private var _colorTransfer: Int = SurfaceEntity.ColorTransfer.LINEAR

    /**
     * The active color transfer function of the media asset drawn on the surface. Use constants
     * from [SurfaceEntity.ColorTransfer]. This value is used if [contentColorMetadataSet] is
     * `true`.
     */
    override val colorTransfer: Int
        get() = _colorTransfer

    private var _colorRange: Int = SurfaceEntity.ColorRange.FULL

    /**
     * The active color range of the media asset drawn on the surface. Use constants from
     * [SurfaceEntity.ColorRange]. This value is used if [contentColorMetadataSet] is `true`.
     */
    override val colorRange: Int
        get() = _colorRange

    private var _maxCLL: Int = 0

    /**
     * The active maximum content light level (MaxCLL) in nits. A value of 0 indicates that MaxCLL
     * is not set or is unknown. This value is used if [contentColorMetadataSet] is `true`.
     */
    override val maxCLL: Int
        get() = _maxCLL

    /**
     * Sets the explicit color information for the surface content. This will also set
     * [contentColorMetadataSet] to `true`.
     *
     * @param colorSpace The runtime color space value (e.g., [SurfaceEntity.ColorSpace.BT709]).
     * @param colorTransfer The runtime color transfer value (e.g.,
     *   [SurfaceEntity.ColorTransfer.SRGB]).
     * @param colorRange The runtime color range value (e.g., [SurfaceEntity.ColorRange.FULL]).
     * @param maxCLL The maximum content light level in nits.
     */
    override fun setContentColorMetadata(
        colorSpace: Int,
        colorTransfer: Int,
        colorRange: Int,
        maxCLL: Int,
    ) {
        _colorSpace = colorSpace
        _colorTransfer = colorTransfer
        _colorRange = colorRange
        _maxCLL = maxCLL
    }

    /**
     * Resets the color information to the runtime's default handling. This will set
     * [contentColorMetadataSet] to `false` and typically involves reverting [colorSpace],
     * [colorTransfer], [colorRange], and [maxCLL] to their default runtime values.
     */
    override fun resetContentColorMetadata() {
        _colorSpace = SurfaceEntity.ColorSpace.BT709
        _colorTransfer = SurfaceEntity.ColorTransfer.LINEAR
        _colorRange = SurfaceEntity.ColorRange.FULL
        _maxCLL = 0
    }

    /**
     * The edge feathering effect for the spatialized geometry.
     *
     * @throws IllegalStateException if the Entity has been disposed.
     */
    override var edgeFeather: SurfaceEntity.EdgeFeather = SurfaceEntity.EdgeFeather.SolidEdge()
}
