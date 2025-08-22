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
import androidx.annotation.IntRange
import androidx.annotation.RestrictTo

/**
 * Defines the sampling behavior for a texture.
 *
 * The fields of this sampler are based on the public
 * [Filament TextureSampler class](https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/TextureSampler.java)
 * but may diverge over time.
 *
 * @property minificationFilter an [Int] which describes how neighboring texels are sampled when the
 *   rendered size is smaller than the texture.
 * @property magnificationFilter an [Int] which describes how neighboring texels are sampled when
 *   the rendered size is larger than the texture.
 * @property wrapModeHorizontal an [Int] which describes how texture coordinates outside the [0-1]
 *   range are handled along the horizontal axis.
 * @property wrapModeVertical an [Int] which describes how texture coordinates outside the [0-1]
 *   range are handled along the vertical axis.
 * @property wrapModeDepth an [Int] which describes how texture coordinates outside the [0-1] range
 *   are handled along the depth axis.
 */
public class TextureSampler
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
constructor(
    @MinificationFilter public val minificationFilter: Int = MINIFICATION_FILTER_LINEAR,
    @MagnificationFilter public val magnificationFilter: Int = MAGNIFICATION_FILTER_LINEAR,
    @WrapMode public val wrapModeHorizontal: Int = WRAP_MODE_REPEAT,
    @WrapMode public val wrapModeVertical: Int = WRAP_MODE_REPEAT,
    @WrapMode public val wrapModeDepth: Int = WRAP_MODE_REPEAT,
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @CompareMode
    public val compareMode: Int = COMPARE_MODE_NONE,
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @CompareFunction
    public val compareFunction: Int = COMPARE_FUNCTION_LESSER_OR_EQUAL,
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @IntRange(from = 0)
    public val anisotropyLog2: Int = 0,
) {
    /**
     * Defines the sampling behavior for a texture.
     *
     * The fields of this sampler are based on the public
     * [Filament TextureSampler class](https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/TextureSampler.java)
     * but may diverge over time.
     *
     * @param minificationFilter an [Int] which describes how neighboring texels are sampled when
     *   the rendered size is smaller than the texture.
     * @param magnificationFilter an [Int] which describes how neighboring texels are sampled when
     *   the rendered size is larger than the texture.
     * @param wrapModeHorizontal an [Int] which describes how texture coordinates outside the [0-1]
     *   range are handled along the horizontal axis.
     * @param wrapModeVertical an [Int] which describes how texture coordinates outside the [0-1]
     *   range are handled along the vertical axis.
     * @param wrapModeDepth an [Int] which describes how texture coordinates outside the [0-1] range
     *   are handled along the depth axis.
     */
    @JvmOverloads
    public constructor(
        @MinificationFilter minificationFilter: Int = MINIFICATION_FILTER_LINEAR,
        @MagnificationFilter magnificationFilter: Int = MAGNIFICATION_FILTER_LINEAR,
        @WrapMode wrapModeHorizontal: Int = WRAP_MODE_REPEAT,
        @WrapMode wrapModeVertical: Int = WRAP_MODE_REPEAT,
        @WrapMode wrapModeDepth: Int = WRAP_MODE_REPEAT,
    ) : this(
        minificationFilter,
        magnificationFilter,
        wrapModeHorizontal,
        wrapModeVertical,
        wrapModeDepth,
        COMPARE_MODE_NONE,
        COMPARE_FUNCTION_LESSER_OR_EQUAL,
        0,
    )

    public companion object {
        /** The edge of the texture extends to infinity. */
        public const val WRAP_MODE_CLAMP_TO_EDGE: Int = 0
        /** The texture infinitely repeats in the wrap direction. */
        public const val WRAP_MODE_REPEAT: Int = 1
        /** The texture infinitely repeats and mirrors in the wrap direction. */
        public const val WRAP_MODE_MIRRORED_REPEAT: Int = 2

        /** Defines the constants for texture wrap modes. */
        @Retention(AnnotationRetention.SOURCE)
        @IntDef(value = [WRAP_MODE_CLAMP_TO_EDGE, WRAP_MODE_REPEAT, WRAP_MODE_MIRRORED_REPEAT])
        internal annotation class WrapMode

        /** No filtering. Nearest neighbor is used. */
        public const val MINIFICATION_FILTER_NEAREST: Int = 0
        /** Box filtering. Weighted average of 4 neighbors is used. */
        public const val MINIFICATION_FILTER_LINEAR: Int = 1
        /** Mip-mapping is activated, but no filtering occurs. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val MINIFICATION_FILTER_NEAREST_MIPMAP_NEAREST: Int = 2
        /** Box filtering within a mip-map level. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val MINIFICATION_FILTER_LINEAR_MIPMAP_NEAREST: Int = 3
        /** Mip-map levels are interpolated, but no other filtering occurs. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val MINIFICATION_FILTER_NEAREST_MIPMAP_LINEAR: Int = 4
        /** Both interpolated Mip-mapping and linear filtering are used. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val MINIFICATION_FILTER_LINEAR_MIPMAP_LINEAR: Int = 5

        /** Defines the constants for texture minification filters. */
        @Retention(AnnotationRetention.SOURCE)
        @IntDef(
            value =
                [
                    MINIFICATION_FILTER_NEAREST,
                    MINIFICATION_FILTER_LINEAR,
                    MINIFICATION_FILTER_NEAREST_MIPMAP_NEAREST,
                    MINIFICATION_FILTER_LINEAR_MIPMAP_NEAREST,
                    MINIFICATION_FILTER_NEAREST_MIPMAP_LINEAR,
                    MINIFICATION_FILTER_LINEAR_MIPMAP_LINEAR,
                ]
        )
        internal annotation class MinificationFilter

        /** No filtering. Nearest neighbor is used. */
        public const val MAGNIFICATION_FILTER_NEAREST: Int = 0
        /** Box filtering. Weighted average of 4 neighbors is used. */
        public const val MAGNIFICATION_FILTER_LINEAR: Int = 1

        /** Defines the constants for texture magnification filters. */
        @Retention(AnnotationRetention.SOURCE)
        @IntDef(value = [MAGNIFICATION_FILTER_NEAREST, MAGNIFICATION_FILTER_LINEAR])
        internal annotation class MagnificationFilter

        /** The comparison function is not used. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_MODE_NONE: Int = 0
        /** The comparison function is used. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_MODE_COMPARE_TO_TEXTURE: Int = 1

        /** Defines the constants for depth texture comparison modes. */
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @Retention(AnnotationRetention.SOURCE)
        @IntDef(value = [COMPARE_MODE_NONE, COMPARE_MODE_COMPARE_TO_TEXTURE])
        internal annotation class CompareMode

        /** Passes if the incoming depth is less than or equal to the stored depth. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_FUNCTION_LESSER_OR_EQUAL: Int = 0
        /** Passes if the incoming depth is greater than or equal to the stored depth. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_FUNCTION_GREATER_OR_EQUAL: Int = 1
        /** Passes if the incoming depth is strictly less than the stored depth. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_FUNCTION_LESSER: Int = 2
        /** Passes if the incoming depth is strictly greater than the stored depth. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_FUNCTION_GREATER: Int = 3
        /** Passes if the incoming depth is equal to the stored depth. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_FUNCTION_EQUAL: Int = 4
        /** Passes if the incoming depth is not equal to the stored depth. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_FUNCTION_NOT_EQUAL: Int = 5
        /** Always passes. Depth testing is effectively deactivated. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_FUNCTION_ALWAYS: Int = 6
        /** Never passes. The depth test always fails. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public const val COMPARE_FUNCTION_NEVER: Int = 7

        /** Defines the constants for depth texture comparison functions. */
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @Retention(AnnotationRetention.SOURCE)
        @IntDef(
            value =
                [
                    COMPARE_FUNCTION_LESSER_OR_EQUAL,
                    COMPARE_FUNCTION_GREATER_OR_EQUAL,
                    COMPARE_FUNCTION_LESSER,
                    COMPARE_FUNCTION_GREATER,
                    COMPARE_FUNCTION_EQUAL,
                    COMPARE_FUNCTION_NOT_EQUAL,
                    COMPARE_FUNCTION_ALWAYS,
                    COMPARE_FUNCTION_NEVER,
                ]
        )
        internal annotation class CompareFunction
    }
}
