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

package androidx.xr.scenecore

import android.app.Activity
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.ScenePose.HitTestFilter
import androidx.xr.scenecore.runtime.HitTestResult as RtHitTestResult
import androidx.xr.scenecore.runtime.SceneRuntime
import androidx.xr.scenecore.testing.FakePerceptionSpaceScenePose
import androidx.xr.scenecore.testing.FakeSceneRuntimeFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [org.robolectric.annotation.Config.TARGET_SDK])
class ScenePoseTest {
    private val entityManager = EntityManager()
    private lateinit var activitySpace: ActivitySpace
    private lateinit var perceptionSpace: PerceptionSpace

    private val activity = Robolectric.buildActivity(Activity::class.java).create().start().get()
    private lateinit var fakeRuntime: SceneRuntime

    @Before
    fun setUp() {
        val fakeRuntimeFactory = FakeSceneRuntimeFactory()
        fakeRuntime = fakeRuntimeFactory.create(activity)

        activitySpace = ActivitySpace.create(fakeRuntime, entityManager)
        perceptionSpace = PerceptionSpace.create(fakeRuntime)
    }

    @Test
    fun allScenePoseTransformPoseTo_callsRuntimeScenePoseImplTransformPoseTo() {
        val pose = Pose.Identity

        assertThat(perceptionSpace.transformPoseTo(pose, perceptionSpace)).isEqualTo(pose)
    }

    @Test
    fun allScenePoseTransformPoseToEntity_callsRuntimeScenePoseImplTransformPoseTo() {
        val pose = Pose.Identity

        assertThat(perceptionSpace.transformPoseTo(pose, activitySpace)).isEqualTo(pose)
    }

    @Test
    fun allScenePoseTransformPoseFromEntity_callsRuntimeScenePoseImplTransformPoseTo() {
        val pose = Pose.Identity

        assertThat(activitySpace.transformPoseTo(pose, perceptionSpace)).isEqualTo(pose)
    }

    @Test
    fun allScenePoseGetActivitySpacePose_callsRuntimeScenePoseImplGetActivitySpacePose() {
        val pose = Pose.Identity

        assertThat(perceptionSpace.activitySpacePose).isEqualTo(pose)
    }

    @Test
    fun hitTest_callsRuntimeHitTest() {
        val origin = Vector3(1f, 2f, 3f)
        val direction = Vector3(4f, 5f, 6f)
        val hitTestFilter = HitTestFilter.SELF_SCENE
        val hitPosition = Vector3(7f, 8f, 9f)
        val surfaceNormal = Vector3(10f, 11f, 12f)
        val distance = 7f
        val surfaceType = RtHitTestResult.HitTestSurfaceType.HIT_TEST_RESULT_SURFACE_TYPE_PLANE
        val expectedHitTestResult =
            HitTestResult(hitPosition, surfaceNormal, surfaceType.toHitTestSurfaceType(), distance)

        // Set the hit test results.
        val rtHitTestResult = RtHitTestResult(hitPosition, surfaceNormal, surfaceType, distance)
        (fakeRuntime.perceptionSpaceActivityPose as FakePerceptionSpaceScenePose).hitTestResult =
            rtHitTestResult

        runBlocking {
            assertThat(perceptionSpace.hitTest(origin, direction, hitTestFilter))
                .isEqualTo(expectedHitTestResult)
        }
    }

    @Test
    fun hitTest_withDefaultHitTestFilter_callsRuntimeHitTest() {
        val origin = Vector3(1f, 2f, 3f)
        val direction = Vector3(4f, 5f, 6f)
        val hitPosition = Vector3(7f, 8f, 9f)
        val surfaceNormal = Vector3(10f, 11f, 12f)
        val distance = 7f
        val surfaceType = RtHitTestResult.HitTestSurfaceType.HIT_TEST_RESULT_SURFACE_TYPE_PLANE
        val expectedHitTestResult =
            HitTestResult(hitPosition, surfaceNormal, surfaceType.toHitTestSurfaceType(), distance)

        // Set the hit test results.
        val rtHitTestResult = RtHitTestResult(hitPosition, surfaceNormal, surfaceType, distance)
        (fakeRuntime.perceptionSpaceActivityPose as FakePerceptionSpaceScenePose).hitTestResult =
            rtHitTestResult

        runBlocking {
            assertThat(perceptionSpace.hitTest(origin, direction)).isEqualTo(expectedHitTestResult)
        }
    }

    @Test
    fun hitTest_convertsNullResult() {
        val origin = Vector3(1f, 2f, 3f)
        val direction = Vector3(4f, 5f, 6f)
        val hitTestFilter = HitTestFilter.SELF_SCENE
        // a null hitPosition in RtHitTestResult should result in a null (public API) HitTestResult
        val hitPosition = null
        val surfaceNormal = null
        val distance = 0f
        val surfaceType = RtHitTestResult.HitTestSurfaceType.HIT_TEST_RESULT_SURFACE_TYPE_PLANE

        // Set the hit test results.
        val rtHitTestResult = RtHitTestResult(hitPosition, surfaceNormal, surfaceType, distance)
        (fakeRuntime.perceptionSpaceActivityPose as FakePerceptionSpaceScenePose).hitTestResult =
            rtHitTestResult

        runBlocking {
            assertThat(perceptionSpace.hitTest(origin, direction, hitTestFilter)).isNull()
        }
    }
}
