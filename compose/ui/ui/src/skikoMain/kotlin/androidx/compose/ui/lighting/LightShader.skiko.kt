/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.lighting

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

internal actual fun LightShader.Companion.isSupported(): Boolean = false

// TODO(https://youtrack.jetbrains.com/issue/CMP-11181) Implement the lighting API for non-Android targets.
internal actual fun LightShader(
    lights: List<Light>,
    surfaceMaterial: SurfaceMaterial,
    surfaceGeometrySpec: SurfaceGeometrySpec,
    density: Density,
    layoutDirection: LayoutDirection,
    size: Size,
): LightShader =
    throw UnsupportedOperationException("Lighting is not supported on this platform")

internal actual val LightShader.Companion.MaxLightsAllowed: Int
    get() = 4

internal actual fun createFlatNormalShader(outline: Outline): Shader =
    throw UnsupportedOperationException("Lighting is not supported on this platform")

internal actual fun createConvexNormalShader(outline: Outline, bevelWidth: Float): Shader =
    throw UnsupportedOperationException("Lighting is not supported on this platform")

internal actual fun createConcaveNormalShader(outline: Outline, bevelWidth: Float): Shader =
    throw UnsupportedOperationException("Lighting is not supported on this platform")
