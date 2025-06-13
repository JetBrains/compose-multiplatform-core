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

import android.app.Activity
import android.content.ContentResolver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.runtime.Config
import androidx.xr.runtime.FieldOfView
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.testing.FakePerceptionManager
import androidx.xr.runtime.testing.FakeRuntimeViewCamera
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class ViewCameraTest {

    private lateinit var xrResourcesManager: XrResourcesManager
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var session: Session

    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setUp() {
        xrResourcesManager = XrResourcesManager()
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        mockContentResolver = mock<ContentResolver>()
    }

    @After
    fun tearDown() {
        xrResourcesManager.clear()
    }

    @Test
    fun getInstance_returnsPose() =
        createTestSessionAndRunTest(testDispatcher) {
            runTest(testDispatcher) {
                val expectedPose = Pose(Vector3(1f, 2f, 3f), Quaternion(4f, 5f, 6f, 7f))
                session.configure(Config(deviceTracking = Config.DeviceTrackingMode.LAST_KNOWN))
                val perceptionManager = session.runtime.perceptionManager as FakePerceptionManager
                check(ViewCamera.getAll(session).isNotEmpty())
                check(ViewCamera.getAll(session)[0].state.value.pose == Pose())

                val runtimeViewCameras = perceptionManager.viewCameras
                runtimeViewCameras[0].pose = expectedPose
                awaitNewCoreState(session, testScope)

                assertThat(ViewCamera.getAll(session)[0].state.value.pose).isEqualTo(expectedPose)
            }
        }

    @Test
    fun getInstance_returnsFov() =
        createTestSessionAndRunTest(testDispatcher) {
            runTest(testDispatcher) {
                val expectedFov = FieldOfView(1f, 2f, 3f, 4f)
                session.configure(Config(deviceTracking = Config.DeviceTrackingMode.LAST_KNOWN))
                val perceptionManager = session.runtime.perceptionManager as FakePerceptionManager
                check(ViewCamera.getAll(session).isNotEmpty())
                check(
                    ViewCamera.getAll(session)[0].state.value.fieldOfView ==
                        FieldOfView(0f, 0f, 0f, 0f)
                )

                val runtimeViewCameras = perceptionManager.viewCameras
                runtimeViewCameras[0].fieldOfView = expectedFov
                awaitNewCoreState(session, testScope)

                assertThat(ViewCamera.getAll(session)[0].state.value.fieldOfView)
                    .isEqualTo(expectedFov)
            }
        }

    @Test
    fun update_stateMatchesRuntimeViewCamera() = runBlocking {
        val expectedPose = Pose(Vector3(1f, 2f, 3f), Quaternion(4f, 5f, 6f, 7f))
        val expectedFov = FieldOfView(1f, 2f, 3f, 4f)
        val runtimeViewCamera = FakeRuntimeViewCamera()
        val underTest = ViewCamera(runtimeViewCamera)
        check(underTest.state.value.pose == Pose())
        check(underTest.state.value.fieldOfView == FieldOfView(0f, 0f, 0f, 0f))

        runtimeViewCamera.pose = expectedPose
        runtimeViewCamera.fieldOfView = expectedFov
        underTest.update()

        assertThat(underTest.state.value.pose).isEqualTo(expectedPose)
        assertThat(underTest.state.value.fieldOfView).isEqualTo(expectedFov)
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
