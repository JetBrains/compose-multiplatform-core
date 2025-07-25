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

import android.view.Surface
import androidx.annotation.RestrictTo

/** Interface for a surface which images can be rendered into. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface SurfaceEntity : Entity {
    /**
     * Specifies how the surface content will be routed for stereo viewing. Applications must render
     * into the surface in accordance with what is specified here in order for the compositor to
     * correctly produce a stereoscopic view to the user.
     *
     * @param mode An int StereoMode
     */
    public var stereoMode: Int

    /** Specifies the shape of the spatial canvas which the surface is texture mapped to. */
    public var canvasShape: CanvasShape

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
     * Selects the view configuration for the surface. MONO creates a surface contains a single
     * view. SIDE_BY_SIDE means the surface is split in half with two views. The first half of the
     * surface maps to the left eye and the second half mapping to the right eye.
     */
    public annotation class StereoMode {
        public companion object {
            // Each eye will see the entire surface (no separation)
            public const val MONO: Int = 0
            // The [top, bottom] halves of the surface will map to [left, right] eyes
            public const val TOP_BOTTOM: Int = 1
            // The [left, right] halves of the surface will map to [left, right] eyes
            public const val SIDE_BY_SIDE: Int = 2
            // Multiview video, [primary, auxiliary] views will map to [left, right] eyes
            public const val MULTIVIEW_LEFT_PRIMARY: Int = 4
            // Multiview video, [primary, auxiliary] views will map to [right, left] eyes
            public const val MULTIVIEW_RIGHT_PRIMARY: Int = 5
        }
    }

    /** Represents the shape of the spatial canvas which the surface is texture mapped to. */
    public interface CanvasShape {
        public val dimensions: Dimensions

        /**
         * A 2D rectangle-shaped canvas. Width and height are represented in the local spatial
         * coordinate system of the entity. (0,0,0) is the center of the canvas.
         */
        public class Quad(public val width: Float, public val height: Float) : CanvasShape {
            override val dimensions: Dimensions = Dimensions(width, height, 0f)
        }

        /**
         * A sphere-shaped canvas. Radius is represented in the local spatial coordinate system of
         * the entity. (0,0,0) is the center of the sphere.
         */
        public class Vr360Sphere(public val radius: Float) : CanvasShape {
            override val dimensions: Dimensions = Dimensions(radius * 2, radius * 2, radius * 2)
        }

        /**
         * A hemisphere-shaped canvas. Radius is represented in the local spatial coordinate system
         * of the entity. (0,0,0) is the center of the base of the hemisphere.
         */
        public class Vr180Hemisphere(public val radius: Float) : CanvasShape {
            override val dimensions: Dimensions = Dimensions(radius * 2, radius * 2, radius)
        }
    }

    /** The width of the left/right feathered edges of the canvas. */
    public var featherRadiusX: Float

    /** The width of the top/bottom feathered edges of the canvas. */
    public var featherRadiusY: Float
}
