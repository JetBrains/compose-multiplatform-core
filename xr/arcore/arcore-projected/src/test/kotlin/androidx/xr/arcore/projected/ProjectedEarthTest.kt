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

package androidx.xr.arcore.projected

import androidx.xr.arcore.runtime.GeospatialPoseNotTrackingException
import androidx.xr.runtime.TrackingState
import androidx.xr.runtime.math.GeospatialPose
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectedEarthTest {
    @Mock private lateinit var service: IProjectedPerceptionService
    private lateinit var xrResources: XrResources
    private lateinit var projectedEarth: ProjectedEarth

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        xrResources = XrResources()
        xrResources.service = service
        projectedEarth = xrResources.earth
        xrResources.deviceTrackingState = TrackingState.TRACKING
        xrResources.earthTrackingState = TrackingState.TRACKING
    }

    @Test
    fun createPoseFromGeospatialPose_returnsCorrectPose() {
        val geospatialPose = GeospatialPose(1.0, 2.0, 3.0, Quaternion(0.1f, 0.2f, 0.3f, 0.4f))
        val expectedPose =
            ProjectedPose().apply {
                vector =
                    ProjectedVector3().apply {
                        x = 1f
                        y = 2f
                        z = 3f
                    }
                q =
                    ProjectedQuarternion().apply {
                        x = 4f
                        y = 5f
                        z = 6f
                        w = 7f
                    }
            }
        `when`(service.createPoseFromGeospatialPose(any(ProjectedEarthPose::class.java)))
            .thenReturn(expectedPose)

        val resultPose = projectedEarth.createPoseFromGeospatialPose(geospatialPose)

        val expectedResultPose =
            Pose(
                Vector3(expectedPose.vector.x, expectedPose.vector.y, expectedPose.vector.z),
                Quaternion(expectedPose.q.x, expectedPose.q.y, expectedPose.q.z, expectedPose.q.w),
            )
        assertThat(resultPose).isEqualTo(expectedResultPose)
    }

    @Test
    fun createGeospatialPoseFromPose_returnsCorrectGeospatialPoseResult() {
        val pose = Pose(Vector3(1f, 2f, 3f), Quaternion(0.1f, 0.2f, 0.3f, 0.4f))
        val expectedEarthPose =
            ProjectedEarthPose().apply {
                latitude = 10.0
                longitude = 20.0
                altitude = 30.0
                eus =
                    ProjectedQuarternion().apply {
                        x = 0.5f
                        y = 0.6f
                        z = 0.7f
                        w = 0.8f
                    }
                locationAccuracyMeters = 1.0
                altitudeAccuracyMeters = 2.0
                orientationYawAccuracyDegrees = 3.0
            }

        `when`(service.createGeospatialPoseFromPose(any(ProjectedPose::class.java)))
            .thenReturn(expectedEarthPose)

        val result = projectedEarth.createGeospatialPoseFromPose(pose)

        val expectedNormalizedQuaternion =
            Quaternion(
                expectedEarthPose.eus.x,
                expectedEarthPose.eus.y,
                expectedEarthPose.eus.z,
                expectedEarthPose.eus.w,
            )
        assertThat(result.geospatialPose.latitude).isEqualTo(expectedEarthPose.latitude)
        assertThat(result.geospatialPose.longitude).isEqualTo(expectedEarthPose.longitude)
        assertThat(result.geospatialPose.altitude).isEqualTo(expectedEarthPose.altitude)
        assertThat(result.geospatialPose.eastUpSouthQuaternion.x)
            .isEqualTo(expectedNormalizedQuaternion.x)
        assertThat(result.geospatialPose.eastUpSouthQuaternion.y)
            .isEqualTo(expectedNormalizedQuaternion.y)
        assertThat(result.geospatialPose.eastUpSouthQuaternion.z)
            .isEqualTo(expectedNormalizedQuaternion.z)
        assertThat(result.geospatialPose.eastUpSouthQuaternion.w)
            .isEqualTo(expectedNormalizedQuaternion.w)
        assertThat(result.horizontalAccuracy).isEqualTo(expectedEarthPose.locationAccuracyMeters)
        assertThat(result.verticalAccuracy).isEqualTo(expectedEarthPose.altitudeAccuracyMeters)
        assertThat(result.orientationYawAccuracy)
            .isEqualTo(expectedEarthPose.orientationYawAccuracyDegrees)
    }

    @Test
    fun createGeospatialPoseFromDevicePose_returnsCorrectGeospatialPoseResult() {
        val expectedEarthPose =
            ProjectedEarthPose().apply {
                latitude = 10.0
                longitude = 20.0
                altitude = 30.0
                eus =
                    ProjectedQuarternion().apply {
                        x = 0.5f
                        y = 0.6f
                        z = 0.7f
                        w = 0.8f
                    }
                locationAccuracyMeters = 1.0
                altitudeAccuracyMeters = 2.0
                orientationYawAccuracyDegrees = 3.0
            }

        `when`(service.createGeospatialPoseFromDevicePose()).thenReturn(expectedEarthPose)

        val result = projectedEarth.createGeospatialPoseFromDevicePose()

        val expectedNormalizedQuaternion =
            Quaternion(
                expectedEarthPose.eus.x,
                expectedEarthPose.eus.y,
                expectedEarthPose.eus.z,
                expectedEarthPose.eus.w,
            )
        assertThat(result.geospatialPose.latitude).isEqualTo(expectedEarthPose.latitude)
        assertThat(result.geospatialPose.longitude).isEqualTo(expectedEarthPose.longitude)
        assertThat(result.geospatialPose.altitude).isEqualTo(expectedEarthPose.altitude)
        assertThat(result.geospatialPose.eastUpSouthQuaternion.x)
            .isEqualTo(expectedNormalizedQuaternion.x)
        assertThat(result.geospatialPose.eastUpSouthQuaternion.y)
            .isEqualTo(expectedNormalizedQuaternion.y)
        assertThat(result.geospatialPose.eastUpSouthQuaternion.z)
            .isEqualTo(expectedNormalizedQuaternion.z)
        assertThat(result.geospatialPose.eastUpSouthQuaternion.w)
            .isEqualTo(expectedNormalizedQuaternion.w)
        assertThat(result.horizontalAccuracy).isEqualTo(expectedEarthPose.locationAccuracyMeters)
        assertThat(result.verticalAccuracy).isEqualTo(expectedEarthPose.altitudeAccuracyMeters)
        assertThat(result.orientationYawAccuracy)
            .isEqualTo(expectedEarthPose.orientationYawAccuracyDegrees)
    }

    @Test
    fun createPoseFromGeospatialPose_notTracking_throwsException() {
        xrResources.deviceTrackingState = TrackingState.STOPPED
        xrResources.earthTrackingState = TrackingState.STOPPED
        assertFailsWith<GeospatialPoseNotTrackingException> {
            projectedEarth.createPoseFromGeospatialPose(GeospatialPose())
        }
    }

    @Test
    fun createPoseFromGeospatialPose_partiallyTracking_throwsException() {
        // This should throw
        xrResources.deviceTrackingState = TrackingState.STOPPED
        xrResources.earthTrackingState = TrackingState.TRACKING
        assertFailsWith<GeospatialPoseNotTrackingException> {
            projectedEarth.createPoseFromGeospatialPose(GeospatialPose())
        }

        // This should also throw
        xrResources.deviceTrackingState = TrackingState.TRACKING
        xrResources.earthTrackingState = TrackingState.STOPPED
        assertFailsWith<GeospatialPoseNotTrackingException> {
            projectedEarth.createPoseFromGeospatialPose(GeospatialPose())
        }
    }
}
