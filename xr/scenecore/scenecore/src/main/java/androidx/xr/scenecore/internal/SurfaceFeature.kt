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

import android.view.Surface
import androidx.annotation.RestrictTo
import androidx.xr.scenecore.internal.SurfaceEntity.ColorRange
import androidx.xr.scenecore.internal.SurfaceEntity.ColorSpace
import androidx.xr.scenecore.internal.SurfaceEntity.ColorTransfer
import androidx.xr.scenecore.internal.SurfaceEntity.EdgeFeather
import androidx.xr.scenecore.internal.SurfaceEntity.Shape

/**
 * Interface for a spatialized rendering feature of Entity which manages an Android Surface.
 * Applications can render to this Surface in various ways, such as via MediaPlayer, ExoPlayer, or
 * custom rendering. The Surface content is texture mapped to the geometric shape defined by the
 * [Shape]. The application can render stereoscopic content into the Surface and specify how it is
 * routed to the User's eyes for stereo viewing using the [stereoMode] property.
 *
 * If the Entity is disposed, attempting to set any values on it will result in
 * [IllegalStateException].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface SurfaceFeature : RenderingFeature {
    /**
     * Specifies how the surface content will be routed for stereo viewing. Applications must render
     * into the surface in accordance with what is specified here in order for the compositor to
     * correctly produce a stereoscopic view to the user.
     */
    @SurfaceEntity.StereoMode public var stereoMode: Int

    /** Specifies the geometry of the spatial canvas which the surface is texture mapped to. */
    public var shape: Shape

    /**
     * Retrieves the dimensions of the "spatial canvas" which the surface is mapped to. These values
     * are not impacted by scale.
     *
     * @return The canvas [Dimensions].
     */
    public val dimensions: Dimensions

    /**
     * Retrieves the surface that the Entity will display. The app can write into this surface
     * however it wants, i.e. MediaPlayer, ExoPlayer, or custom rendering.
     *
     * @return an Android [Surface]
     */
    public val surface: Surface

    /**
     * The texture to be composited into the alpha channel of the surface. If null, the alpha mask
     * will be disabled.
     *
     * @param alphaMask The primary alpha mask texture.
     */
    public fun setPrimaryAlphaMaskTexture(alphaMask: TextureResource?)

    /**
     * The texture to be composited into the alpha channel of the auxiliary view of the surface.
     * This is only used for interleaved stereo content. If null, the alpha mask will be disabled.
     *
     * @param alphaMask The auxiliary alpha mask texture.
     */
    public fun setAuxiliaryAlphaMaskTexture(alphaMask: TextureResource?)

    /**
     * Indicates whether explicit color information has been set for the surface content. If
     * `false`, the runtime should signal the backend to use its best effort color correction and
     * tone-mapping. If `true`, the runtime should inform the backend to use the values specified in
     * [colorSpace], [colorTransfer], [colorRange], and [maxContentLightLevel] for color correction
     * and tone-mapping of the surface content.
     *
     * This property is typically managed by the `setContentColorMetadata` and
     * `resetContentColorMetadata` methods.
     */
    public val contentColorMetadataSet: Boolean

    /**
     * The active color space of the media asset drawn on the surface. Use constants from
     * [SurfaceEntity.ColorSpace]. This value is used if [contentColorMetadataSet] is `true`.
     */
    @ColorSpace public val colorSpace: Int

    /**
     * The active color transfer function of the media asset drawn on the surface. Use constants
     * from [SurfaceEntity.ColorTransfer]. This value is used if [contentColorMetadataSet] is
     * `true`.
     */
    @ColorTransfer public val colorTransfer: Int

    /**
     * The active color range of the media asset drawn on the surface. Use constants from
     * [SurfaceEntity.ColorRange]. This value is used if [contentColorMetadataSet] is `true`.
     */
    @ColorRange public val colorRange: Int

    /**
     * The active maximum content light level (MaxCLL) in nits. A value of 0 indicates that MaxCLL
     * is not set or is unknown. This value is used if [contentColorMetadataSet] is `true`.
     */
    public val maxContentLightLevel: Int

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
    public fun setContentColorMetadata(
        @ColorSpace colorSpace: Int,
        @ColorTransfer colorTransfer: Int,
        @ColorRange colorRange: Int,
        maxCLL: Int,
    )

    /**
     * Resets the color information to the runtime's default handling. This will set
     * [contentColorMetadataSet] to `false` and typically involves reverting [colorSpace],
     * [colorTransfer], [colorRange], and [maxContentLightLevel] to their default runtime values.
     */
    public fun resetContentColorMetadata()

    /** The edge feathering effect for the spatialized geometry. */
    public var edgeFeather: EdgeFeather
}
