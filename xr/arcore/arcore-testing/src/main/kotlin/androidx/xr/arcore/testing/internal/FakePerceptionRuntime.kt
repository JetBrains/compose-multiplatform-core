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

package androidx.xr.arcore.testing.internal

import android.app.Activity
import android.content.pm.PackageManager
import androidx.xr.arcore.runtime.PerceptionRuntime
import androidx.xr.runtime.Config
import androidx.xr.runtime.DepthEstimationMode
import androidx.xr.runtime.DeviceTrackingMode
import androidx.xr.runtime.DisplayBlendMode
import androidx.xr.runtime.EyeTrackingMode
import androidx.xr.runtime.FaceTrackingMode
import androidx.xr.runtime.GeospatialMode
import androidx.xr.runtime.HandTrackingMode
import androidx.xr.runtime.PlaneTrackingMode
import kotlin.time.ComparableTimeMark

internal class FakePerceptionRuntime(
    override val lifecycleManager: FakeLifecycleManager,
    override val perceptionManager: FakePerceptionManager,
    private val activityContext: Activity? = null,
) : PerceptionRuntime {

    var xrDevicePreferredDisplayBlendMode: DisplayBlendMode = DisplayBlendMode.NO_DISPLAY

    override var config: Config = Config()
        private set

    override fun initialize() {
        lifecycleManager.create()
    }

    override fun configure(config: Config) {
        this.config = config
        checkPermissions(config)
        lifecycleManager.configure(config)
        perceptionManager.updateTrackingStates(config)
    }

    override fun getPreferredDisplayBlendMode(): DisplayBlendMode {
        return xrDevicePreferredDisplayBlendMode
    }

    override fun resume() {
        lifecycleManager.resume()
    }

    override suspend fun update(): ComparableTimeMark {
        val timeMark = lifecycleManager.update()

        perceptionManager.updateTrackingStates(config)

        return timeMark
    }

    override fun pause() {
        lifecycleManager.pause()
    }

    override fun destroy() {
        lifecycleManager.stop()
    }

    private fun checkPermissions(config: Config) {
        if (activityContext != null) {
            val requiredPermissions: MutableSet<String> = mutableSetOf()
            if (config.planeTracking == PlaneTrackingMode.HORIZONTAL_AND_VERTICAL) {
                requiredPermissions.add(androidx.xr.runtime.manifest.SCENE_UNDERSTANDING_COARSE)
            }
            if (config.depthEstimation != DepthEstimationMode.DISABLED) {
                requiredPermissions.add(androidx.xr.runtime.manifest.SCENE_UNDERSTANDING_FINE)
            }
            if (config.handTracking != HandTrackingMode.DISABLED) {
                requiredPermissions.add(androidx.xr.runtime.manifest.HAND_TRACKING)
            }
            if (config.faceTracking == FaceTrackingMode.BLEND_SHAPES) {
                requiredPermissions.add(androidx.xr.runtime.manifest.FACE_TRACKING)
            }
            if (config.deviceTracking != DeviceTrackingMode.DISABLED) {
                requiredPermissions.add(androidx.xr.runtime.manifest.HEAD_TRACKING)
            }
            if (config.eyeTracking == EyeTrackingMode.COARSE_TRACKING) {
                requiredPermissions.add(androidx.xr.runtime.manifest.EYE_TRACKING_COARSE)
            }
            if (config.eyeTracking == EyeTrackingMode.FINE_TRACKING) {
                requiredPermissions.add(androidx.xr.runtime.manifest.EYE_TRACKING_FINE)
            }
            if (config.geospatial == GeospatialMode.VPS_AND_GPS) {
                requiredPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
            requiredPermissions
                .filter {
                    activityContext.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                }
                .let {
                    if (it.isNotEmpty()) {
                        throw SecurityException(
                            "Found missing Permissions required for config: $it"
                        )
                    }
                }
        }
    }
}
