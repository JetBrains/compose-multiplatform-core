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

package androidx.xr.compose.testing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.xr.compose.platform.LocalHasXrSpatialFeature
import androidx.xr.compose.platform.LocalSession
import androidx.xr.runtime.Config
import androidx.xr.runtime.HeadTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.ActivityPose
import androidx.xr.runtime.internal.ActivitySpace
import androidx.xr.runtime.internal.CameraViewActivityPose
import androidx.xr.runtime.internal.CameraViewActivityPose.Fov
import androidx.xr.runtime.internal.Entity
import androidx.xr.runtime.internal.HeadActivityPose
import androidx.xr.runtime.internal.HitTestResult
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.PanelEntity
import androidx.xr.runtime.internal.PerceptionSpaceActivityPose
import androidx.xr.runtime.internal.PixelDimensions
import androidx.xr.runtime.internal.SpatialCapabilities
import androidx.xr.runtime.internal.SpatialEnvironment
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.testing.FakeRuntimeFactory
import androidx.xr.scenecore.scene
import com.google.common.util.concurrent.ListenableFuture
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * A Test environment composable wrapper to support testing elevated components locally.
 *
 * @param isXrEnabled Whether to enable XR.
 * @param isFullSpace Whether to enable full space mode.
 * @param runtime The [JxrPlatformAdapter] to use for the [Session].
 * @param content The content block containing the compose content to be tested.
 */
@Composable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun TestSetup(
    isXrEnabled: Boolean = true,
    isFullSpace: Boolean = true,
    runtime: JxrPlatformAdapter =
        TestJxrPlatformAdapter.create(
            createFakeRuntime(LocalContext.current.getActivity() as SubspaceTestingActivity)
        ),
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current.getActivity() as SubspaceTestingActivity
    activity.session = createFakeSessionWithTestConfigs(activity, runtime)
    val session = remember {
        if (isXrEnabled) {
            activity.session.apply {
                // TODO: b/405401088 - There's functions aren't being honored at the moment of
                // TestSetup
                // creation.
                if (isFullSpace) {
                    scene.spatialEnvironment.requestFullSpaceMode()
                } else {
                    scene.spatialEnvironment.requestHomeSpaceMode()
                }
                resume()
                configure(Config(headTracking = HeadTrackingMode.Enabled))
            }
        } else {
            null
        }
    }

    CompositionLocalProvider(
        LocalSession provides session,
        LocalHasXrSpatialFeature provides isXrEnabled,
        content = content,
    )
}

private fun createNonXrSession(activity: Activity): Session {
    val mockJxrRuntime = mock<JxrPlatformAdapter>()
    val mockActivitySpace =
        mock<ActivitySpace>(defaultAnswer = { throw UnsupportedOperationException() })
    mockJxrRuntime.stub {
        on { mockJxrRuntime.spatialEnvironment } doReturn mock<SpatialEnvironment>()
        on { activitySpace } doReturn mockActivitySpace
        on { activitySpaceRootImpl } doReturn mockActivitySpace
        on { headActivityPose } doReturn mock<HeadActivityPose>()
        on { perceptionSpaceActivityPose } doReturn
            mock<PerceptionSpaceActivityPose>(
                defaultAnswer = { throw UnsupportedOperationException() }
            )
        on { mainPanelEntity } doReturn mock<PanelEntity>()
        on { requestHomeSpaceMode() } doAnswer { throw UnsupportedOperationException() }
        on { requestFullSpaceMode() } doAnswer { throw UnsupportedOperationException() }
        on { spatialCapabilities } doReturn SpatialCapabilities(0)
        on { createActivityPanelEntity(any(), any(), any(), any(), any()) } doAnswer
            {
                throw UnsupportedOperationException()
            }
        on { createAnchorEntity(any(), any(), any(), any()) } doAnswer
            {
                throw UnsupportedOperationException()
            }
        on { createEntity(any(), any(), any()) } doAnswer { throw UnsupportedOperationException() }
        on { createGltfEntity(any(), any(), any()) } doAnswer
            {
                throw UnsupportedOperationException()
            }
        on {
            createPanelEntity(
                any<Context>(),
                any<Pose>(),
                any<View>(),
                any<PixelDimensions>(),
                any<String>(),
                any<Entity>(),
            )
        } doAnswer { throw UnsupportedOperationException() }
        on { createLoggingEntity(any()) } doAnswer { throw UnsupportedOperationException() }
    }

    return Session(activity, FakeRuntimeFactory().createRuntime(activity), mockJxrRuntime)
}

private tailrec fun Context.getActivity(): Activity =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> error("Unexpected Context type when trying to resolve the context's Activity.")
    }

/**
 * A test implementation of [HeadActivityPose] that allows for setting custom values for the
 * activity space pose and scales.
 *
 * @param activitySpacePose The pose of the head in ActivitySpace.
 * @param worldSpaceScale The scale of the head in WorldSpace.
 * @param activitySpaceScale The scale of the head in ActivitySpace.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class TestHeadActivityPose(
    override var activitySpacePose: Pose = Pose.Identity,
    override var worldSpaceScale: Vector3 = Vector3(1f, 1f, 1f),
    override var activitySpaceScale: Vector3 = Vector3(1f, 1f, 1f),
) : HeadActivityPose {
    override fun transformPoseTo(pose: Pose, destination: ActivityPose): Pose {
        throw NotImplementedError("Intentionally left unimplemented for these test scenarios")
    }

    override fun hitTest(
        origin: Vector3,
        direction: Vector3,
        hitTestFilter: Int,
    ): ListenableFuture<HitTestResult> {
        throw NotImplementedError("Intentionally left unimplemented for these test scenarios")
    }
}

/**
 * A test implementation of [CameraViewActivityPose] that allows for setting custom values for the
 * cameraType, field of view, ActivitySpace pose, and scales.
 *
 * @param cameraType The type of camera.
 * @param fov The field of view of the camera.
 * @param activitySpacePose The pose of the camera in ActivitySpace.
 * @param activitySpaceScale The scale of the camera in ActivitySpace.
 * @param worldSpaceScale The scale of the camera in WorldSpace.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class TestCameraViewActivityPose(
    override val cameraType: Int,
    override var fov: CameraViewActivityPose.Fov =
        Fov(angleLeft = -1.57f, angleRight = 1.57f, angleUp = 1.57f, angleDown = -1.57f),
    override var activitySpacePose: Pose = Pose.Identity,
    override var activitySpaceScale: Vector3 = Vector3(1f, 1f, 1f),
    override val worldSpaceScale: Vector3 = Vector3(1f, 1f, 1f),
) : CameraViewActivityPose {
    override fun transformPoseTo(pose: Pose, destination: ActivityPose): Pose {
        throw NotImplementedError("Intentionally left unimplemented for these test scenarios")
    }

    override fun hitTest(
        origin: Vector3,
        direction: Vector3,
        hitTestFilter: Int,
    ): ListenableFuture<HitTestResult> {
        throw NotImplementedError("Intentionally left unimplemented for these test scenarios")
    }
}

/**
 * A test implementation of [ActivitySpace] that allows for setting custom values.
 *
 * This class delegates non-overridden functionality to a base ActivitySpace instance but provides
 * direct control over key properties like [activitySpacePose] and [activitySpaceScale] (via the
 * overridden [getScale] method).
 *
 * @param fakeRuntimeActivitySpaceBase The base [ActivitySpace] to use for the [ActivitySpace]
 *   implementation.
 * @param activitySpacePose The pose of the ActivitySpace. Defaults to [Pose.Identity].
 * @param activitySpaceScale The scale of the ActivitySpace. Defaults to one.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class TestActivitySpace(
    private val fakeRuntimeActivitySpaceBase: ActivitySpace,
    override var activitySpacePose: Pose = Pose.Identity,
    override var activitySpaceScale: Vector3 = Vector3(1f, 1f, 1f),
) : ActivitySpace by fakeRuntimeActivitySpaceBase {

    override fun getScale(relativeTo: Int): Vector3 {
        return activitySpaceScale
    }
}

/**
 * A test implementation of [JxrPlatformAdapter] that allows for setting custom values for the
 * ActivitySpace, HeadActivityPose, and CameraViewActivityPose.
 *
 * @param fakeRuntimeBase The base [JxrPlatformAdapter] to use for the [JxrPlatformAdapter]
 *   implementation.
 * @param activitySpace The [ActivitySpace] to use for the [JxrPlatformAdapter] implementation.
 * @param headActivityPose The [TestHeadActivityPose] to use for the [JxrPlatformAdapter]
 *   implementation.
 * @param leftCameraViewPose The [TestCameraViewActivityPose] to use for the [JxrPlatformAdapter]
 *   implementation for the left camera.
 * @param rightCameraViewPose The [TestCameraViewActivityPose] to use for the [JxrPlatformAdapter]
 *   implementation for the right camera.
 * @param unknownCameraViewPose The [TestCameraViewActivityPose] to use for the [JxrPlatformAdapter]
 *   implementation for the unknown camera.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class TestJxrPlatformAdapter
private constructor(
    private val fakeRuntimeBase: JxrPlatformAdapter,
    override var activitySpace: ActivitySpace = fakeRuntimeBase.activitySpace,
    override var headActivityPose: TestHeadActivityPose? =
        TestHeadActivityPose(activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))),
    public var leftCameraViewPose: TestCameraViewActivityPose? =
        TestCameraViewActivityPose(
            cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
            fov =
                CameraViewActivityPose.Fov(
                    angleLeft = -1.57f,
                    angleRight = 1.00f,
                    angleUp = 1.57f,
                    angleDown = -1.57f,
                ),
        ),
    public var rightCameraViewPose: TestCameraViewActivityPose? =
        TestCameraViewActivityPose(
            cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
            fov =
                CameraViewActivityPose.Fov(
                    angleLeft = -1.00f,
                    angleRight = 1.57f,
                    angleUp = 1.57f,
                    angleDown = -1.57f,
                ),
        ),
    public var unknownCameraViewPose: TestCameraViewActivityPose? =
        TestCameraViewActivityPose(
            cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_UNKNOWN
        ),
) : JxrPlatformAdapter by fakeRuntimeBase {
    override val activitySpaceRootImpl: Entity
        get() = activitySpace

    override fun getCameraViewActivityPose(
        @CameraViewActivityPose.CameraType cameraType: Int
    ): CameraViewActivityPose? {
        return when (cameraType) {
            CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE -> leftCameraViewPose
            CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE -> rightCameraViewPose
            else -> unknownCameraViewPose
        }
    }

    /**
     * Creates a [TestJxrPlatformAdapter] with the provided [fakeRuntimeBase].
     *
     * @param fakeRuntimeBase The base [JxrPlatformAdapter] to use for the [JxrPlatformAdapter]
     *   implementation.
     * @return The [TestJxrPlatformAdapter] with the provided [fakeRuntimeBase].
     */
    public companion object {
        public fun create(fakeRuntimeBase: JxrPlatformAdapter): TestJxrPlatformAdapter =
            TestJxrPlatformAdapter(fakeRuntimeBase = fakeRuntimeBase)
    }
}
