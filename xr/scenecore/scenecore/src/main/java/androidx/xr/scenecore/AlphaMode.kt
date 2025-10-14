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
import androidx.annotation.RestrictTo

/**
 * Defines the constants for a [Material]'s alpha mode, which corresponds to the
 * [glTF specification](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html).
 */
public object AlphaMode {
    /**
     * The material is fully opaque and the alpha channel is ignored. Corresponds to glTF's
     * [OPAQUE alpha mode](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#_material-alphamode).
     */
    public const val ALPHA_MODE_OPAQUE: Int = 0

    /**
     * The material is opaque where alpha is greater than or equal to cutoff, otherwise it is
     * discarded. Corresponds to glTF's
     * [MASK alpha mode](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#_material-alphamode).
     */
    public const val ALPHA_MODE_MASK: Int = 1

    /**
     * The material is alpha-blended with the background. Corresponds to glTF's
     * [BLEND alpha mode](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#_material-alphamode).
     */
    public const val ALPHA_MODE_BLEND: Int = 2
}

/** Defines the constants for alpha mode. */
@Retention(AnnotationRetention.SOURCE)
@IntDef(
    value = [AlphaMode.ALPHA_MODE_OPAQUE, AlphaMode.ALPHA_MODE_MASK, AlphaMode.ALPHA_MODE_BLEND]
)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public annotation class AlphaModeValues
