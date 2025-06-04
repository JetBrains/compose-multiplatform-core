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

package androidx.xr.arcore

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.xr.runtime.Config
import androidx.xr.runtime.Config.PlaneTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.TrackingState
import androidx.xr.runtime.internal.Plane as RuntimePlane
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector2
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.testing.FakePerceptionManager
import androidx.xr.runtime.testing.FakeRuntimeAnchor
import androidx.xr.runtime.testing.FakeRuntimePlane
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaneTest {
    private lateinit var xrResourcesManager: XrResourcesManager
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var session: Session

    @get:Rule
    val grantPermissionRule =
        GrantPermissionRule.grant(
            "android.permission.SCENE_UNDERSTANDING_COARSE",
            "android.permission.HAND_TRACKING",
        )

    @Before
    fun setUp() {
        xrResourcesManager = XrResourcesManager()
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        FakeRuntimeAnchor.anchorsCreated = 0
    }

    @After
    fun tearDown() {
        xrResourcesManager.clear()
    }

    @Test
    fun constructor_convertsRuntimePlaneType() {
        val plane1 =
            Plane(
                FakeRuntimePlane(type = RuntimePlane.Type.HORIZONTAL_UPWARD_FACING),
                xrResourcesManager,
            )
        val plane2 =
            Plane(
                FakeRuntimePlane(type = RuntimePlane.Type.HORIZONTAL_DOWNWARD_FACING),
                xrResourcesManager,
            )
        val plane3 = Plane(FakeRuntimePlane(type = RuntimePlane.Type.VERTICAL), xrResourcesManager)

        assertThat(plane1.type).isEqualTo(Plane.Type.HORIZONTAL_UPWARD_FACING)
        assertThat(plane2.type).isEqualTo(Plane.Type.HORIZONTAL_DOWNWARD_FACING)
        assertThat(plane3.type).isEqualTo(Plane.Type.VERTICAL)
    }

    @Test
    fun constructor_convertsRuntimePlaneLabel() {
        val plane1 = Plane(FakeRuntimePlane(label = RuntimePlane.Label.UNKNOWN), xrResourcesManager)
        val plane2 = Plane(FakeRuntimePlane(label = RuntimePlane.Label.WALL), xrResourcesManager)
        val plane3 = Plane(FakeRuntimePlane(label = RuntimePlane.Label.FLOOR), xrResourcesManager)
        val plane4 = Plane(FakeRuntimePlane(label = RuntimePlane.Label.CEILING), xrResourcesManager)
        val plane5 = Plane(FakeRuntimePlane(label = RuntimePlane.Label.TABLE), xrResourcesManager)

        assertThat(plane1.state.value.label).isEqualTo(Plane.Label.UNKNOWN)
        assertThat(plane2.state.value.label).isEqualTo(Plane.Label.WALL)
        assertThat(plane3.state.value.label).isEqualTo(Plane.Label.FLOOR)
        assertThat(plane4.state.value.label).isEqualTo(Plane.Label.CEILING)
        assertThat(plane5.state.value.label).isEqualTo(Plane.Label.TABLE)
    }

    @Test
    fun subscribe_collectReturnsPlane() =
        createTestSessionAndRunTest(testDispatcher) {
            runTest(testDispatcher) {
                val perceptionManager = session.runtime.perceptionManager as FakePerceptionManager
                val runtimePlane = FakeRuntimePlane()
                perceptionManager.addTrackable(runtimePlane)
                awaitNewCoreState(session, testScope)

                var underTest = emptyList<Plane>()
                testScope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    underTest = Plane.subscribe(session).first().toList()
                }

                assertThat(underTest.size).isEqualTo(1)
                assertThat(underTest.first().runtimePlane).isEqualTo(runtimePlane)
            }
        }

    @Test
    fun subscribe_planeTrackingDisabled_throwsIllegalStateException() =
        createTestSessionAndRunTest(testDispatcher) {
            runTest(testDispatcher) {
                session.configure(Config(planeTracking = PlaneTrackingMode.DISABLED))

                assertFailsWith<IllegalStateException> { Plane.subscribe(session) }
            }
        }

    @Test
    fun createAnchor_usesGivenPose() =
        createTestSessionAndRunTest(testDispatcher) {
            val runtimePlane = FakeRuntimePlane()
            xrResourcesManager.syncTrackables(listOf(runtimePlane))
            val underTest = xrResourcesManager.trackablesMap.values.first() as Plane
            val pose = Pose(Vector3(1.0f, 2.0f, 3.0f), Quaternion(1.0f, 2.0f, 3.0f, 4.0f))

            val anchor = (underTest.createAnchor(pose) as AnchorCreateSuccess).anchor

            assertThat(anchor.state.value.pose).isEqualTo(pose)
        }

    @Test
    fun createAnchor_anchorLimitReached_returnsAnchorResourcesExhaustedResult() =
        createTestSessionAndRunTest(testDispatcher) {
            val runtimePlane = FakeRuntimePlane()
            xrResourcesManager.syncTrackables(listOf(runtimePlane))
            val underTest = xrResourcesManager.trackablesMap.values.first() as Plane
            repeat(FakeRuntimeAnchor.ANCHOR_RESOURCE_LIMIT) { underTest.createAnchor(Pose()) }

            assertThat(underTest.createAnchor(Pose()))
                .isInstanceOf(AnchorCreateResourcesExhausted::class.java)
        }

    @Test
    fun createAnchor_planeTrackingDisabled_throwsIllegalStateException() =
        createTestSessionAndRunTest(testDispatcher) {
            val runtimePlane = FakeRuntimePlane()
            xrResourcesManager.syncTrackables(listOf(runtimePlane))
            val underTest = xrResourcesManager.trackablesMap.values.first() as Plane
            session.configure(Config(planeTracking = PlaneTrackingMode.DISABLED))

            assertFailsWith<IllegalStateException> { underTest.createAnchor(Pose()) }
        }

    @Test
    fun update_trackingStateMatchesRuntime() = runBlocking {
        // arrange
        val runtimePlane = FakeRuntimePlane()
        runtimePlane.trackingState = TrackingState.STOPPED
        xrResourcesManager.syncTrackables(listOf(runtimePlane))
        val underTest = xrResourcesManager.trackablesMap.values.first() as Plane
        check(underTest.state.value.trackingState == TrackingState.STOPPED)

        // act
        runtimePlane.trackingState = TrackingState.TRACKING
        underTest.update()

        // assert
        assertThat(underTest.state.value.trackingState).isEqualTo(TrackingState.TRACKING)
    }

    @Test
    fun update_centerPoseMatchesRuntime() = runBlocking {
        // arrange
        val runtimePlane = FakeRuntimePlane()
        xrResourcesManager.syncTrackables(listOf(runtimePlane))
        val underTest = xrResourcesManager.trackablesMap.values.first() as Plane
        check(
            underTest.state.value.centerPose.equals(
                Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1.0f))
            )
        )

        // act
        val newPose = Pose(Vector3(1.0f, 2.0f, 3.0f), Quaternion(1.0f, 2.0f, 3.0f, 4.0f))
        runtimePlane.centerPose = newPose
        underTest.update()

        // assert
        assertThat(underTest.state.value.centerPose).isEqualTo(newPose)
    }

    @Test
    fun update_extentsMatchesRuntime() = runBlocking {
        // arrange
        val runtimePlane = FakeRuntimePlane()
        val extents = Vector2(1.0f, 2.0f)
        runtimePlane.extents = extents
        xrResourcesManager.syncTrackables(listOf(runtimePlane))
        val underTest = xrResourcesManager.trackablesMap.values.first() as Plane
        check(underTest.state.value.extents == extents)

        // act
        val newExtents = Vector2(3.0f, 4.0f)
        runtimePlane.extents = newExtents
        underTest.update()

        // assert
        assertThat(underTest.state.value.extents).isEqualTo(newExtents)
    }

    @Test
    fun update_verticesMatchesRuntime() = runBlocking {
        // arrange
        val runtimePlane = FakeRuntimePlane()
        val vertices = listOf(Vector2(1.0f, 2.0f), Vector2(3.0f, 4.0f))
        runtimePlane.vertices = vertices
        xrResourcesManager.syncTrackables(listOf(runtimePlane))
        val underTest = xrResourcesManager.trackablesMap.values.first() as Plane
        check(underTest.state.value.vertices == vertices)

        // act
        val newVertices = listOf(Vector2(3.0f, 4.0f), Vector2(5.0f, 6.0f))
        runtimePlane.vertices = newVertices
        underTest.update()

        // assert
        assertThat(underTest.state.value.vertices).isEqualTo(newVertices)
    }

    @Test
    fun update_subsumedByMatchesRuntime() = runBlocking {
        // arrange
        val runtimePlane = FakeRuntimePlane()
        val subsumedByPlane = FakeRuntimePlane()
        xrResourcesManager.syncTrackables(listOf(runtimePlane, subsumedByPlane))
        xrResourcesManager.update()
        val underTest = xrResourcesManager.trackablesMap[runtimePlane] as Plane
        check(underTest.state.value.subsumedBy == null)

        // act
        runtimePlane.subsumedBy = subsumedByPlane
        xrResourcesManager.update()

        // assert
        assertThat(underTest.state.value.subsumedBy).isNotNull()
        assertThat(underTest.state.value.subsumedBy!!.runtimePlane).isEqualTo(subsumedByPlane)
    }

    @Test
    fun labelToString_returnsCorrectString() {
        assertThat(Plane.Label.WALL.toString()).isEqualTo("WALL")
        assertThat(Plane.Label.FLOOR.toString()).isEqualTo("FLOOR")
        assertThat(Plane.Label.CEILING.toString()).isEqualTo("CEILING")
        assertThat(Plane.Label.TABLE.toString()).isEqualTo("TABLE")
        assertThat(Plane.Label.UNKNOWN.toString()).isEqualTo("UNKNOWN")
    }

    @Test
    fun typeToString_returnsCorrectString() {
        assertThat(Plane.Type.HORIZONTAL_UPWARD_FACING.toString())
            .isEqualTo("HORIZONTAL_UPWARD_FACING")
        assertThat(Plane.Type.HORIZONTAL_DOWNWARD_FACING.toString())
            .isEqualTo("HORIZONTAL_DOWNWARD_FACING")
        assertThat(Plane.Type.VERTICAL.toString()).isEqualTo("VERTICAL")
    }

    private fun createTestSessionAndRunTest(
        coroutineDispatcher: CoroutineDispatcher = StandardTestDispatcher(),
        testBody: () -> Unit,
    ) {
        ActivityScenario.launch(Activity::class.java).use {
            it.onActivity { activity ->
                session =
                    (Session.create(activity, coroutineDispatcher) as SessionCreateSuccess).session
                xrResourcesManager.lifecycleManager = session.runtime.lifecycleManager

                testBody()
            }
        }
    }

    /** Resumes and pauses the session just enough to emit a new CoreState. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun awaitNewCoreState(session: Session, testScope: TestScope) {
        session.resume()
        testScope.advanceUntilIdle()
        session.pause()
    }
}
