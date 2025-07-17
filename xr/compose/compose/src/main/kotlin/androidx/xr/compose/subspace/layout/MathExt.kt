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

package androidx.xr.compose.subspace.layout

import androidx.compose.ui.unit.Density
import androidx.xr.compose.unit.Meter
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3

/** Converts the translation from pixels to meters, taking into account [density]. */
internal fun Pose.convertPixelsToMeters(density: Density): Pose =
    Pose(translation = translation.convertPixelsToMeters(density), rotation = rotation)

/** Converts the translation from meters to pixels, taking into account [density]. */
internal fun Pose.convertMetersToPixels(density: Density): Pose =
    Pose(translation = translation.convertMetersToPixels(density), rotation = rotation)

/** Converts values from pixels to meters, taking into account [density]. */
internal fun Vector3.convertPixelsToMeters(density: Density): Vector3 =
    Vector3(
        Meter.fromPixel(x, density).value,
        Meter.fromPixel(y, density).value,
        Meter.fromPixel(z, density).value,
    )

/** Converts values from meters to pixels, taking into account [density]. */
internal fun Vector3.convertMetersToPixels(density: Density): Vector3 =
    Vector3(Meter(x).toPx(density), Meter(y).toPx(density), Meter(z).toPx(density))
