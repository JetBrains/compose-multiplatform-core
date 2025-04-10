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

package androidx.xr.runtime.internal

import androidx.annotation.RestrictTo
import androidx.xr.runtime.math.Vector3

/** Interface for a XR Runtime Panel entity */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface PanelEntity : Entity {
    /**
     * Sets the pixel (not Dp) dimensions of the view underlying this PanelEntity. Calling this
     * might cause the layout of the Panel contents to change. Updating this will not cause the
     * scale or pixel density to change.
     *
     * @param dimensions The [PixelDimensions] of the underlying surface to set.
     */
    public var sizeInPixels: PixelDimensions

    /**
     * Sets a corner radius on all four corners of this PanelEntity.
     *
     * @param value Corner radius in meters.
     * @throws IllegalArgumentException if radius is <= 0.0f.
     */
    public var cornerRadius: Float

    /**
     * Gets the number of pixels per meter for this panel. This value reflects changes to scale,
     * including parent scale.
     *
     * @return Vector3 scale applied to pixels within the Panel. (Z will be 0)
     * @deprecated This method will be removed in a future release.
     */
    public val pixelDensity: Vector3

    /**
     * Returns the spatial size of this Panel in meters. This includes any scaling applied to this
     * panel by itself or its parents, which might be set via changes to setScale.
     *
     * @return [Dimensions] size of this panel in meters. (Z will be 0)
     */
    public var size: Dimensions
}
