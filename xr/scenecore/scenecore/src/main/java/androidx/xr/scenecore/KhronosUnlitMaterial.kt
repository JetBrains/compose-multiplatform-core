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

package androidx.xr.scenecore

import androidx.annotation.FloatRange
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.Vector4
import androidx.xr.scenecore.internal.JxrPlatformAdapter
import androidx.xr.scenecore.internal.MaterialResource as RtMaterial

/**
 * Represents a [Material] in SceneCore.
 *
 * A [Material] defines the visual appearance of a surface when rendered. It encapsulates properties
 * like color, texture, and how light interacts with the surface.
 *
 * It's important to dispose of the [Material] when it's no longer needed to free up resources. This
 * can be done by calling the [dispose] method.
 */
public interface Material {
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public val material: RtMaterial

    /**
     * Disposes the [Material] and releases its underlying graphics resources.
     *
     * After disposal, the [Material] should not be used further.
     */
    @MainThread public fun dispose()
}

/**
 * Represents an unlit material, which is not affected by scene lighting.
 *
 * This material type is defined by a base color and supports a limited set of parameters. It is a
 * direct implementation of the following glTF features:
 * - [KHR_materials_unlit
 *   extension](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos/KHR_materials_unlit)
 * - [baseColorTexture](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#metallic-roughness-material)
 * - [baseColorFactor](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#metallic-roughness-material)
 * - [alphaMode (and
 *   alphaCutoff)](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#alpha-coverage)
 */
@Suppress("NotCloseable")
public class KhronosUnlitMaterial
internal constructor(
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) override val material: RtMaterial,
    @AlphaModeValues internal val alphaMode: Int,
    internal val session: Session,
) : Material {

    /** Disposes the [KhronosUnlitMaterial] and releases its underlying graphics resources. */
    @MainThread
    override public fun dispose() {
        session.platformAdapter.destroyKhronosPbrMaterial(material)
    }

    /**
     * Sets the material's base color using a texture.
     *
     * By default this is a white texture, where all pixels are [1, 1, 1, 1]. In other words, if
     * this is left as default, the base color will always be the base color factor.
     *
     * @param texture The [Texture] to be used as the base color texture, in sRGB color space.
     * @param sampler The [TextureSampler] to be used when sampling the base color texture.
     */
    @JvmOverloads
    @MainThread
    public fun setBaseColorTexture(texture: Texture, sampler: TextureSampler = TextureSampler()) {
        session.platformAdapter.setBaseColorTextureOnKhronosPbrMaterial(
            material,
            texture.texture,
            sampler.toRtTextureSampler(),
        )
    }

    /**
     * Sets a linear multiplier for the base color.
     *
     * By default this is [1, 1, 1, 1].
     *
     * @param factor The [Vector4] (RGBA) factor multiplied component-wise with the base color
     *   texture.
     */
    @MainThread
    public fun setBaseColorFactor(factor: Vector4) {
        session.platformAdapter.setBaseColorFactorsOnKhronosPbrMaterial(material, factor)
    }

    /**
     * Sets the alpha cutoff threshold.
     *
     * This value is only used when the material's [alphaMode] is [AlphaMode.ALPHA_MODE_MASK].
     *
     * @param alphaCutoff The alpha cutoff. Fragments with alpha below this value are discarded.
     *   Default is 0.5. Valid values are between 0.0 and 1.0, inclusive.
     * @throws IllegalArgumentException if the material's alphaMode is not ALPHA_MODE_MASK.
     */
    @MainThread
    public fun setAlphaCutoff(@FloatRange(from = 0.0, to = 1.0) alphaCutoff: Float) {
        check(alphaMode == AlphaMode.ALPHA_MODE_MASK) {
            "Alpha cutoff can only be set when the material's alpha mode is set to ALPHA_MODE_MASK."
        }
        session.platformAdapter.setAlphaCutoffOnKhronosPbrMaterial(material, alphaCutoff)
    }

    public companion object {
        internal suspend fun createAsync(
            platformAdapter: JxrPlatformAdapter,
            @AlphaModeValues alphaMode: Int,
            session: Session,
        ): KhronosUnlitMaterial {
            val material =
                platformAdapter
                    .createKhronosPbrMaterial(alphaMode.toRtKhronosUnlitMaterialSpec())
                    .awaitSuspending()
            return KhronosUnlitMaterial(material, alphaMode, session)
        }

        /**
         * Asynchronously creates a [KhronosUnlitMaterial].
         *
         * @param session The active [Session] in which to create the material.
         * @param alphaMode The [AlphaMode] to use for the material.
         * @return The newly created [KhronosUnlitMaterial].
         */
        @MainThread
        @JvmStatic
        public suspend fun create(
            session: Session,
            @AlphaModeValues alphaMode: Int,
        ): KhronosUnlitMaterial {
            return KhronosUnlitMaterial.createAsync(session.platformAdapter, alphaMode, session)
        }
    }
}
