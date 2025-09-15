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

package androidx.xr.arcore.testapp.eyetracking

import android.graphics.Color
import androidx.xr.arcore.Eye
import androidx.xr.arcore.EyeState
import androidx.xr.runtime.Config
import androidx.xr.runtime.math.Pose

fun getEyeGazePose(config: Config, eye: Eye?): Pose? = getEyeGazePose(config, eye?.state?.value)

fun getEyeGazePose(config: Config, eye: Eye.State?): Pose? {
    if (eye == null) return null

    return when (config.eyeTracking) {
        Config.EyeTrackingMode.COARSE_TRACKING -> {
            eye.coarseEyePose
        }
        Config.EyeTrackingMode.FINE_TRACKING -> {
            eye.fineEyePose
        }
        Config.EyeTrackingMode.COARSE_AND_FINE_TRACKING -> {
            eye.fineEyePose ?: eye.coarseEyePose
        }
        else -> {
            return null
        }
    }
}

const val GAZE_LEFT = Color.GREEN
const val GAZE_RIGHT = Color.RED
const val SHUT_LEFT = Color.BLUE
const val SHUT_RIGHT = Color.YELLOW
const val INVALID = Color.WHITE

fun getEyeState(config: Config, eye: Eye?): EyeState? = getEyeState(config, eye?.state?.value)

fun getEyeState(config: Config, eye: Eye.State?): EyeState? {
    if (eye == null) return null

    return when (config.eyeTracking) {
        Config.EyeTrackingMode.COARSE_TRACKING -> {
            eye.coarseEyeState
        }
        Config.EyeTrackingMode.FINE_TRACKING -> {
            eye.fineEyeState
        }
        Config.EyeTrackingMode.COARSE_AND_FINE_TRACKING -> {
            eye.fineEyeState ?: eye.coarseEyeState
        }
        else -> {
            return null
        }
    }
}
