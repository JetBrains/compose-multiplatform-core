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

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.runtime.Config
import androidx.xr.runtime.FieldOfView
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.testing.FakeLifecycleManager
import androidx.xr.runtime.testing.FakePerceptionManager
import androidx.xr.runtime.testing.FakeRuntimeFactory
import androidx.xr.runtime.testing.FakeRuntimeViewCamera
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(AndroidJUnit4::class)
class ViewCameraTest {

    private lateinit var activityController: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var session: Session
    private lateinit var xrResourcesManager: XrResourcesManager

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        activityController = Robolectric.buildActivity(ComponentActivity::class.java)
        activity = activityController.get()
        xrResourcesManager = XrResourcesManager()

        val shadowApplication = shadowOf(activity.application)
        shadowApplication.grantPermissions(Manifest.permission.CAMERA)
        FakeLifecycleManager.TestPermissions.forEach { permission ->
            shadowApplication.grantPermissions(permission)
        }
        FakeRuntimeFactory.hasCreatePermission = true

        activityController.create()

        session = (Session.create(activity, testDispatcher) as SessionCreateSuccess).session
        session.configure(Config(headTracking = Config.HeadTrackingMode.LAST_KNOWN))
        xrResourcesManager.lifecycleManager = session.runtime.lifecycleManager
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getAll_returnsPose() =
        runTest(testDispatcher) {
            val expectedPose = Pose(Vector3(1f, 2f, 3f), Quaternion(4f, 5f, 6f, 7f))
            val initialViewCameras = ViewCamera.getAll(session)
            check(initialViewCameras.isNotEmpty())
            check(initialViewCameras[0].state.value.pose == Pose())
            val perceptionManager = session.runtime.perceptionManager as FakePerceptionManager
            val runtimeViewCameras = perceptionManager.viewCameras
            check(runtimeViewCameras.isNotEmpty())
            runtimeViewCameras[0].pose = expectedPose
            activityController.resume()
            advanceUntilIdle()
            activityController.pause()

            assertThat(ViewCamera.getAll(session)[0].state.value.pose).isEqualTo(expectedPose)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getAll_returnsFov() =
        runTest(testDispatcher) {
            val expectedFov = FieldOfView(1f, 2f, 3f, 4f)
            val initialViewCameras = ViewCamera.getAll(session)
            check(initialViewCameras.isNotEmpty())
            check(initialViewCameras[0].state.value.fieldOfView == FieldOfView(0f, 0f, 0f, 0f))
            val perceptionManager = session.runtime.perceptionManager as FakePerceptionManager
            val runtimeViewCameras = perceptionManager.viewCameras
            check(runtimeViewCameras.isNotEmpty())
            runtimeViewCameras[0].fieldOfView = expectedFov

            activityController.resume()
            advanceUntilIdle()
            activityController.pause()

            assertThat(ViewCamera.getAll(session)[0].state.value.fieldOfView).isEqualTo(expectedFov)
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
}
