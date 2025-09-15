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

package androidx.xr.arcore.openxr

import androidx.xr.arcore.EyeState
import androidx.xr.runtime.math.Pose

internal data class EyeData(val state: EyeState, val pose: Pose) {}

internal fun EyeState.Companion.fromOpenXrEyeState(nativeValue: Int): EyeState =
    when (nativeValue) {
        0 -> EyeState.INVALID
        1 -> EyeState.GAZING
        2 -> EyeState.SHUT
        else -> throw IllegalStateException("Unknown eye state")
    }
