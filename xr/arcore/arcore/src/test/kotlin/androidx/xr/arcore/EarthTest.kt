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

package androidx.xr.arcore

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.arcore.runtime.AnchorNotAuthorizedException
import androidx.xr.arcore.runtime.AnchorResourcesExhaustedException
import androidx.xr.arcore.runtime.AnchorUnsupportedLocationException
import androidx.xr.arcore.runtime.Earth as RuntimeEarth
import androidx.xr.arcore.runtime.GeospatialPoseNotTrackingException
import androidx.xr.arcore.testing.FakeLifecycleManager
import androidx.xr.arcore.testing.FakePerceptionManager
import androidx.xr.arcore.testing.FakeRuntimeEarth
import androidx.xr.runtime.Config
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.math.GeospatialPose
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EarthTest {

    companion object {
        private val ROTATION: Quaternion = Quaternion.Identity
        private val TRANSLATION: Vector3 = Vector3(1f, 2f, 3f)
        private val POSE: Pose = Pose(TRANSLATION, ROTATION)
        private val GEOSPATIAL_POSE: GeospatialPose = GeospatialPose(1.0, 2.0, 3.0, ROTATION)
        private const val HORIZONTAL_ACCURACY: Double = 1.0
        private const val VERTICAL_ACCURACY: Double = 2.0
        private const val ORIENTATION_YAW_ACCURACY: Double = 3.0
        private const val LATITUDE: Double = 10.0
        private const val LONGITUDE: Double = 20.0
        private const val ALTITUDE: Double = 30.0
        private const val ALTITUDE_ABOVE_SURFACE: Double = 5.0
        private val EUS_QUATERNION: Quaternion = Quaternion.Identity
    }

    private lateinit var xrResourcesManager: XrResourcesManager
    private lateinit var runtimeEarth: FakeRuntimeEarth
    private lateinit var session: Session

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) {
        runBlocking(block = block)
    }

    @Before
    fun setUp() {
        xrResourcesManager = XrResourcesManager()
        runtimeEarth = FakeRuntimeEarth(RuntimeEarth.State.STOPPED)
    }

    @Test
    fun getInstance_returnsEarth() {
        createTestSessionAndRunTest() { assertThat(Earth.getInstance(session)).isNotNull() }
    }

    private fun createTestSessionAndRunTest(
        coroutineDispatcher: CoroutineDispatcher = StandardTestDispatcher(),
        testBody: () -> Unit,
    ) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                session =
                    (Session.create(activity, coroutineDispatcher) as SessionCreateSuccess).session
                xrResourcesManager.lifecycleManager = session.perceptionRuntime.lifecycleManager
                session.configure(Config(geospatial = Config.GeospatialMode.EARTH))

                testBody()
            }
        }
    }

    @Test
    fun state_defaultStateIsStopped() = runBlocking {
        val runtimeEarth = FakeRuntimeEarth()
        val underTest = Earth(runtimeEarth, xrResourcesManager)

        assertThat(underTest.state.value).isEqualTo(Earth.State.STOPPED)
    }

    @Test
    fun update_stateMatchesRuntimeEarth() = runBlocking {
        val runtimeEarth = FakeRuntimeEarth(RuntimeEarth.State.STOPPED)
        val underTest = Earth(runtimeEarth, xrResourcesManager)
        check(underTest.state.value == Earth.State.STOPPED)

        // Update to Running state.
        runtimeEarth.state = RuntimeEarth.State.RUNNING
        underTest.update()

        assertThat(underTest.state.value).isEqualTo(Earth.State.RUNNING)

        // Update to Stopped state with error.
        runtimeEarth.state = RuntimeEarth.State.ERROR_INTERNAL
        underTest.update()

        assertThat(underTest.state.value).isEqualTo(Earth.State.ERROR_INTERNAL)
    }

    @Test
    fun createGeospatialPoseFromDevicePose_success_returnsSuccessResult() =
        createTestSessionAndRunTest {
            val underTest = Earth.getInstance(session)
            getFakeRuntimeEarth().nextGeospatialPoseResult =
                RuntimeEarth.GeospatialPoseResult(
                    GEOSPATIAL_POSE,
                    HORIZONTAL_ACCURACY,
                    VERTICAL_ACCURACY,
                    ORIENTATION_YAW_ACCURACY,
                )

            val result = underTest.createGeospatialPoseFromDevicePose()
            val successResult = result as CreateGeospatialPoseFromPoseSuccess

            assertThat(successResult.pose).isEqualTo(GEOSPATIAL_POSE)
            assertThat(successResult.horizontalAccuracy).isEqualTo(HORIZONTAL_ACCURACY)
            assertThat(successResult.verticalAccuracy).isEqualTo(VERTICAL_ACCURACY)
            assertThat(successResult.orientationYawAccuracy).isEqualTo(ORIENTATION_YAW_ACCURACY)
        }

    @Test
    fun createGeospatialPoseFromDevicePose_notTracking_returnsNotTrackingResult() =
        createTestSessionAndRunTest {
            val underTest = Earth.getInstance(session)
            getFakeRuntimeEarth().nextException = GeospatialPoseNotTrackingException()

            val result = underTest.createGeospatialPoseFromDevicePose()
            assertThat(result).isInstanceOf(CreateGeospatialPoseFromPoseNotTracking::class.java)
        }

    @Test
    fun createGeospatialPoseFromPose_success_returnsSuccessResult() = createTestSessionAndRunTest {
        val underTest = Earth.getInstance(session)
        getFakeRuntimeEarth().nextGeospatialPoseResult =
            RuntimeEarth.GeospatialPoseResult(
                GEOSPATIAL_POSE,
                HORIZONTAL_ACCURACY,
                VERTICAL_ACCURACY,
                ORIENTATION_YAW_ACCURACY,
            )

        val result = underTest.createGeospatialPoseFromPose(Pose(Vector3(), Quaternion()))
        val successResult = result as CreateGeospatialPoseFromPoseSuccess
        assertThat(successResult.pose).isEqualTo(GEOSPATIAL_POSE)
        assertThat(successResult.horizontalAccuracy).isEqualTo(HORIZONTAL_ACCURACY)
        assertThat(successResult.verticalAccuracy).isEqualTo(VERTICAL_ACCURACY)
        assertThat(successResult.orientationYawAccuracy).isEqualTo(ORIENTATION_YAW_ACCURACY)
    }

    @Test
    fun createGeospatialPoseFromPose_notTracking_returnsNotTrackingResult() =
        createTestSessionAndRunTest {
            val underTest = Earth.getInstance(session)
            getFakeRuntimeEarth().nextException = GeospatialPoseNotTrackingException()

            val result = underTest.createGeospatialPoseFromPose(Pose(Vector3(), Quaternion()))

            assertThat(result).isInstanceOf(CreateGeospatialPoseFromPoseNotTracking::class.java)
        }

    @Test
    fun createPoseFromGeospatialPose_success_returnsSuccessResult() = createTestSessionAndRunTest {
        val underTest = Earth.getInstance(session)
        getFakeRuntimeEarth().nextPose = POSE

        val result = underTest.createPoseFromGeospatialPose(GeospatialPose())
        val successResult = result as CreatePoseFromGeospatialPoseSuccess

        assertThat(successResult.pose).isEqualTo(POSE)
    }

    @Test
    fun createPoseFromGeospatialPose_notTracking_returnsNotTrackingResult() =
        createTestSessionAndRunTest {
            val underTest = Earth.getInstance(session)
            getFakeRuntimeEarth().nextException = GeospatialPoseNotTrackingException()

            val result = underTest.createPoseFromGeospatialPose(GeospatialPose())

            assertThat(result).isInstanceOf(CreatePoseFromGeospatialPoseNotTracking::class.java)
        }

    @Test
    fun createAnchor_success_returnsSuccessResultWithAnchor() = createTestSessionAndRunTest {
        val underTest = Earth(runtimeEarth, xrResourcesManager)
        val fakePerceptionManager = getFakePerceptionManager()
        val fakeAnchor = fakePerceptionManager.createAnchor(Pose.Identity)
        runtimeEarth.nextAnchor = fakeAnchor

        val result = underTest.createAnchor(LATITUDE, LONGITUDE, ALTITUDE, EUS_QUATERNION)

        assertThat(result).isInstanceOf(AnchorCreateSuccess::class.java)
        val successResult = result as AnchorCreateSuccess
        assertThat(successResult.anchor.runtimeAnchor).isEqualTo(fakeAnchor)
        assertThat((xrResourcesManager.updatables.firstOrNull() as Anchor).runtimeAnchor)
            .isEqualTo(fakeAnchor)
    }

    @Test
    fun createAnchor_resourceExhausted_returnsResourcesExhaustedResult() =
        createTestSessionAndRunTest {
            val underTest = Earth.getInstance(session)
            getFakeRuntimeEarth().nextException = AnchorResourcesExhaustedException()

            val result = underTest.createAnchor(LATITUDE, LONGITUDE, ALTITUDE, EUS_QUATERNION)

            assertThat(result).isInstanceOf(AnchorCreateResourcesExhausted::class.java)
        }

    @Test
    fun createAnchor_illegalState_returnsIllegalStateResult() = createTestSessionAndRunTest {
        val underTest = Earth.getInstance(session)
        getFakeRuntimeEarth().nextException = IllegalStateException()

        val result = underTest.createAnchor(LATITUDE, LONGITUDE, ALTITUDE, EUS_QUATERNION)

        assertThat(result).isInstanceOf(AnchorCreateIllegalState::class.java)
    }

    @Test
    fun createAnchor_invalidLatitude_throwsIllegalArgumentException() =
        createTestSessionAndRunTest {
            val underTest = Earth.getInstance(session)
            getFakeRuntimeEarth().nextException = IllegalArgumentException()

            assertFailsWith<IllegalArgumentException> {
                underTest.createAnchor(90.0, LONGITUDE, ALTITUDE, EUS_QUATERNION)
            }
        }

    @Test
    fun createPoseFromGeospatialPose_withVpsDisabled_throwsIllegalStateException() {
        val newConfig = Config(geospatial = Config.GeospatialMode.DISABLED)
        val fakeLifecycleManager = FakeLifecycleManager()
        fakeLifecycleManager.config = newConfig
        xrResourcesManager.lifecycleManager = fakeLifecycleManager
        val underTest = Earth(runtimeEarth, xrResourcesManager)
        val geospatialPose = GeospatialPose(1.0, 2.0, 3.0, Quaternion(0.1f, 0.2f, 0.3f, 0.4f))

        val exception =
            assertFailsWith<IllegalStateException> {
                underTest.createPoseFromGeospatialPose(geospatialPose)
            }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("To use this function, Config.GeospatialMode must be set to EARTH.")
    }

    @Test
    fun createAnchorOnSurface_success_returnsSuccessResultWithAnchor() =
        createTestSessionAndRunTest {
            doBlocking {
                val underTest = Earth(runtimeEarth, xrResourcesManager)
                val fakePerceptionManager = getFakePerceptionManager()
                val fakeAnchor = fakePerceptionManager.createAnchor(Pose.Identity)
                runtimeEarth.nextAnchor = fakeAnchor

                val result =
                    underTest.createAnchorOnSurface(
                        LATITUDE,
                        LONGITUDE,
                        ALTITUDE_ABOVE_SURFACE,
                        EUS_QUATERNION,
                        Earth.Surface.TERRAIN,
                    )

                assertThat(result).isInstanceOf(AnchorCreateSuccess::class.java)
                val successResult = result as AnchorCreateSuccess
                assertThat(successResult.anchor.runtimeAnchor).isEqualTo(fakeAnchor)
                assertThat((xrResourcesManager.updatables.firstOrNull() as Anchor).runtimeAnchor)
                    .isEqualTo(fakeAnchor)
            }
        }

    @Test
    fun createAnchorOnSurface_illegalState_returnsIllegalStateResult() =
        createTestSessionAndRunTest {
            doBlocking {
                val underTest = Earth.getInstance(session)
                getFakeRuntimeEarth().nextException = IllegalStateException()

                val result =
                    underTest.createAnchorOnSurface(
                        LATITUDE,
                        LONGITUDE,
                        ALTITUDE_ABOVE_SURFACE,
                        EUS_QUATERNION,
                        Earth.Surface.TERRAIN,
                    )

                assertThat(result).isInstanceOf(AnchorCreateIllegalState::class.java)
            }
        }

    @Test
    fun createAnchorOnSurface_resourceExhausted_returnsResourcesExhaustedResult() =
        createTestSessionAndRunTest {
            doBlocking {
                val underTest = Earth.getInstance(session)
                getFakeRuntimeEarth().nextException = AnchorResourcesExhaustedException()

                val result =
                    underTest.createAnchorOnSurface(
                        LATITUDE,
                        LONGITUDE,
                        ALTITUDE_ABOVE_SURFACE,
                        EUS_QUATERNION,
                        Earth.Surface.TERRAIN,
                    )

                assertThat(result).isInstanceOf(AnchorCreateResourcesExhausted::class.java)
            }
        }

    @Test
    fun createAnchorOnSurface_notAuthorized_returnsNotAuthorizedResult() =
        createTestSessionAndRunTest {
            doBlocking {
                val underTest = Earth.getInstance(session)
                getFakeRuntimeEarth().nextException = AnchorNotAuthorizedException()

                val result =
                    underTest.createAnchorOnSurface(
                        LATITUDE,
                        LONGITUDE,
                        ALTITUDE_ABOVE_SURFACE,
                        EUS_QUATERNION,
                        Earth.Surface.TERRAIN,
                    )

                assertThat(result).isInstanceOf(AnchorCreateNotAuthorized::class.java)
            }
        }

    @Test
    fun createAnchorOnSurface_unsupportedLocation_returnsUnsupportedLocationResult() =
        createTestSessionAndRunTest {
            doBlocking {
                val underTest = Earth.getInstance(session)
                getFakeRuntimeEarth().nextException = AnchorUnsupportedLocationException()

                val result =
                    underTest.createAnchorOnSurface(
                        LATITUDE,
                        LONGITUDE,
                        ALTITUDE_ABOVE_SURFACE,
                        EUS_QUATERNION,
                        Earth.Surface.TERRAIN,
                    )

                assertThat(result).isInstanceOf(AnchorCreateUnsupportedLocation::class.java)
            }
        }

    @Test
    fun createAnchorOnSurface_invalidLatitude_throwsIllegalArgumentException() =
        createTestSessionAndRunTest {
            doBlocking {
                val underTest = Earth.getInstance(session)
                getFakeRuntimeEarth().nextException = IllegalArgumentException()

                assertFailsWith<IllegalArgumentException> {
                    underTest.createAnchorOnSurface(
                        90.0,
                        LONGITUDE,
                        ALTITUDE_ABOVE_SURFACE,
                        EUS_QUATERNION,
                        Earth.Surface.TERRAIN,
                    )
                }
            }
        }

    private fun getFakePerceptionManager(): FakePerceptionManager {
        return session.perceptionRuntime.perceptionManager as FakePerceptionManager
    }

    private fun getFakeRuntimeEarth(): FakeRuntimeEarth {
        return getFakePerceptionManager().earth as FakeRuntimeEarth
    }
}
