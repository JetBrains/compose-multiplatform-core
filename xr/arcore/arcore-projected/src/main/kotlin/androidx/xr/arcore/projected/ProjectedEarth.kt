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

package androidx.xr.arcore.projected

import androidx.annotation.RestrictTo
import androidx.xr.arcore.runtime.Anchor
import androidx.xr.arcore.runtime.Earth
import androidx.xr.arcore.runtime.GeospatialPoseNotTrackingException
import androidx.xr.runtime.TrackingState
import androidx.xr.runtime.math.GeospatialPose
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3

/** Currently unimplemented implementation of [androidx.xr.arcore.runtime.Earth] on Projected. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ProjectedEarth internal constructor(private val xrResources: XrResources) : Earth {
    public override var state: Earth.State = Earth.State.STOPPED
        private set

    private val service: IProjectedPerceptionService
        get() = xrResources.service

    private fun checkTrackingState() {
        if (
            xrResources.deviceTrackingState == TrackingState.STOPPED ||
                xrResources.earthTrackingState == TrackingState.STOPPED
        ) {
            throw GeospatialPoseNotTrackingException()
        }
    }

    override public fun createPoseFromGeospatialPose(geospatialPose: GeospatialPose): Pose {
        checkTrackingState()
        val projectedQuaternion =
            ProjectedQuarternion().apply {
                x = geospatialPose.eastUpSouthQuaternion.x
                y = geospatialPose.eastUpSouthQuaternion.y
                z = geospatialPose.eastUpSouthQuaternion.z
                w = geospatialPose.eastUpSouthQuaternion.w
            }
        val projectedEarthPose =
            ProjectedEarthPose().apply {
                latitude = geospatialPose.latitude
                longitude = geospatialPose.longitude
                altitude = geospatialPose.altitude
                eus = projectedQuaternion
            }
        val projectedPose = service.createPoseFromGeospatialPose(projectedEarthPose)
        // TODO: b/446185235 - maybe we need better error handling or in service?
        if (projectedPose == null) {
            return Pose()
        }
        return Pose(
            Vector3(projectedPose.vector.x, projectedPose.vector.y, projectedPose.vector.z),
            Quaternion(projectedPose.q.x, projectedPose.q.y, projectedPose.q.z, projectedPose.q.w),
        )
    }

    override public fun createGeospatialPoseFromPose(pose: Pose): Earth.GeospatialPoseResult {
        checkTrackingState()
        val projectedVector =
            ProjectedVector3().apply {
                x = pose.translation.x
                y = pose.translation.y
                z = pose.translation.z
            }
        val projectedQuaternion =
            ProjectedQuarternion().apply {
                x = pose.rotation.x
                y = pose.rotation.y
                z = pose.rotation.z
                w = pose.rotation.w
            }
        val projectedPose =
            ProjectedPose().apply {
                vector = projectedVector
                q = projectedQuaternion
            }
        val projectedEarthPose = service.createGeospatialPoseFromPose(projectedPose)
        // TODO: b/446185235 - maybe we need better error handling or in service?
        if (projectedEarthPose == null) {
            return Earth.GeospatialPoseResult(
                geospatialPose = GeospatialPose(0.0, 0.0, 0.0, Quaternion()),
                horizontalAccuracy = 0.0,
                verticalAccuracy = 0.0,
                orientationYawAccuracy = 0.0,
            )
        }
        val geospatialPose =
            GeospatialPose(
                latitude = projectedEarthPose.latitude,
                longitude = projectedEarthPose.longitude,
                altitude = projectedEarthPose.altitude,
                eastUpSouthQuaternion =
                    Quaternion(
                        projectedEarthPose.eus.x,
                        projectedEarthPose.eus.y,
                        projectedEarthPose.eus.z,
                        projectedEarthPose.eus.w,
                    ),
            )
        return Earth.GeospatialPoseResult(
            geospatialPose = geospatialPose,
            horizontalAccuracy = projectedEarthPose.locationAccuracyMeters,
            verticalAccuracy = projectedEarthPose.altitudeAccuracyMeters,
            orientationYawAccuracy = projectedEarthPose.orientationYawAccuracyDegrees,
        )
    }

    override public fun createGeospatialPoseFromDevicePose(): Earth.GeospatialPoseResult {
        checkTrackingState()
        val projectedEarthPose = service.createGeospatialPoseFromDevicePose()
        // TODO: b/446185235 - maybe we need better error handling or in service?
        if (projectedEarthPose == null) {
            return Earth.GeospatialPoseResult(
                geospatialPose = GeospatialPose(0.0, 0.0, 0.0, Quaternion()),
                horizontalAccuracy = 0.0,
                verticalAccuracy = 0.0,
                orientationYawAccuracy = 0.0,
            )
        }
        val geospatialPose =
            GeospatialPose(
                latitude = projectedEarthPose.latitude,
                longitude = projectedEarthPose.longitude,
                altitude = projectedEarthPose.altitude,
                eastUpSouthQuaternion =
                    Quaternion(
                        projectedEarthPose.eus.x,
                        projectedEarthPose.eus.y,
                        projectedEarthPose.eus.z,
                        projectedEarthPose.eus.w,
                    ),
            )
        return Earth.GeospatialPoseResult(
            geospatialPose = geospatialPose,
            horizontalAccuracy = projectedEarthPose.locationAccuracyMeters,
            verticalAccuracy = projectedEarthPose.altitudeAccuracyMeters,
            orientationYawAccuracy = projectedEarthPose.orientationYawAccuracyDegrees,
        )
    }

    override public fun createAnchor(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        eastUpSouthQuaternion: Quaternion,
    ): Anchor {
        throw NotImplementedError("Not implemented yet.")
    }

    override public suspend fun createAnchorOnSurface(
        latitude: Double,
        longitude: Double,
        altitudeAboveSurface: Double,
        eastUpSouthQuaternion: Quaternion,
        surface: Earth.Surface,
    ): Anchor {
        throw NotImplementedError("Not implemented yet.")
    }
}
