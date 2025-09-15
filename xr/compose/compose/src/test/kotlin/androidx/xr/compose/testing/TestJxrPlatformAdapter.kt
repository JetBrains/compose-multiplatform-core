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

package androidx.xr.compose.testing

import android.app.Activity
import androidx.xr.arcore.testing.FakePerceptionRuntimeFactory
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.BoundingBox
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.impl.JxrPlatformAdapterAxr
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider
import androidx.xr.scenecore.impl.impress.FakeImpressApiImpl
import androidx.xr.scenecore.impl.perception.PerceptionLibrary
import androidx.xr.scenecore.internal.ActivityPose
import androidx.xr.scenecore.internal.ActivitySpace
import androidx.xr.scenecore.internal.CameraViewActivityPose
import androidx.xr.scenecore.internal.CameraViewActivityPose.Fov
import androidx.xr.scenecore.internal.Entity
import androidx.xr.scenecore.internal.HeadActivityPose
import androidx.xr.scenecore.internal.HitTestResult
import androidx.xr.scenecore.internal.JxrPlatformAdapter
import androidx.xr.scenecore.internal.PanelEntity
import androidx.xr.scenecore.internal.PixelDimensions
import androidx.xr.scenecore.testing.FakeScheduledExecutorService
import com.google.androidxr.splitengine.SplitEngineSubspaceManager
import com.google.ar.imp.view.splitengine.ImpSplitEngineRenderer
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import org.mockito.Mockito.mock

/**
 * Create a fake [Session] for testing.
 *
 * A convenience method that creates a fake [Session] for testing. If runtime is not provided, a
 * fake [JxrPlatformAdapter] will be created by default.
 *
 * @param activity The [Activity] to use for the [Session].
 * @param runtime The [JxrPlatformAdapter] to use for the [Session].
 */
fun createFakeSession(
    activity: Activity,
    runtime: JxrPlatformAdapter = createFakeRuntime(activity),
): Session =
    Session(
        activity,
        runtimes =
            listOf(
                FakePerceptionRuntimeFactory().createRuntime(activity).apply {
                    lifecycleManager.create()
                },
                runtime,
            ),
    )

/**
 * Create a fake [JxrPlatformAdapter] for testing.
 *
 * A convenience method that creates a fake [JxrPlatformAdapter] for testing.
 *
 * @param activity The [Activity] to use for the [JxrPlatformAdapter].
 */
fun createFakeRuntime(activity: Activity): JxrPlatformAdapter =
    // FakeJxrPlatformAdapterFactory().createPlatformAdapter(activity)
    JxrPlatformAdapterAxr.create(
        /* activity = */ activity,
        /* executor = */ FakeScheduledExecutorService(),
        /* extensions = */ XrExtensionsProvider.getXrExtensions()!!,
        /* impressApi = */ FakeImpressApiImpl(),
        /* perceptionLibrary = */ PerceptionLibrary(),
        /* splitEngineSubspaceManager = */ mock(SplitEngineSubspaceManager::class.java),
        /* splitEngineRenderer = */ mock(ImpSplitEngineRenderer::class.java),
    )

/**
 * A test implementation of [JxrPlatformAdapter] that allows for setting custom values for the
 * ActivitySpace, HeadActivityPose, and CameraViewActivityPose.
 *
 * [fakeRuntimeBase] is the base [JxrPlatformAdapter] to use for the [JxrPlatformAdapter]
 * implementation.
 *
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
class TestJxrPlatformAdapter
private constructor(
    private val fakeRuntimeBase: JxrPlatformAdapter,
    override var mainPanelEntity: PanelEntity = fakeRuntimeBase.mainPanelEntity,
    override var activitySpace: ActivitySpace = fakeRuntimeBase.activitySpace,
    override var headActivityPose: TestHeadActivityPose? =
        TestHeadActivityPose(activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))),
    var leftCameraViewPose: TestCameraViewActivityPose? =
        TestCameraViewActivityPose(
            cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
            fov = Fov(angleLeft = -1.57f, angleRight = 1.00f, angleUp = 1.57f, angleDown = -1.57f),
        ),
    var rightCameraViewPose: TestCameraViewActivityPose? =
        TestCameraViewActivityPose(
            cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
            fov = Fov(angleLeft = -1.00f, angleRight = 1.57f, angleUp = 1.57f, angleDown = -1.57f),
        ),
    var unknownCameraViewPose: TestCameraViewActivityPose? =
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
    companion object {
        fun create(fakeRuntimeBase: JxrPlatformAdapter): TestJxrPlatformAdapter =
            TestJxrPlatformAdapter(fakeRuntimeBase = fakeRuntimeBase)

        fun create(activity: Activity): TestJxrPlatformAdapter =
            TestJxrPlatformAdapter(fakeRuntimeBase = createFakeRuntime(activity))
    }
}

/**
 * A test implementation of [HeadActivityPose] that allows for setting custom values for the
 * activity space pose and scales.
 *
 * @param activitySpacePose The pose of the head in ActivitySpace.
 * @param worldSpaceScale The scale of the head in WorldSpace.
 * @param activitySpaceScale The scale of the head in ActivitySpace.
 */
class TestHeadActivityPose(
    override var activitySpacePose: Pose = Pose.Identity,
    override var worldSpaceScale: Vector3 = Vector3(1f, 1f, 1f),
    override var activitySpaceScale: Vector3 = Vector3(1f, 1f, 1f),
) : HeadActivityPose {
    override fun transformPoseTo(pose: Pose, destination: ActivityPose): Pose {
        throw NotImplementedError("Intentionally left unimplemented for these test scenarios")
    }

    @Suppress("AsyncSuffixFuture")
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
 * @param displayResolutionInPixels The pixel dimensions of the camera view.
 */
class TestCameraViewActivityPose(
    override val cameraType: Int,
    override var fov: Fov =
        Fov(angleLeft = -1.57f, angleRight = 1.57f, angleUp = 1.57f, angleDown = -1.57f),
    override var activitySpacePose: Pose = Pose.Identity,
    override var activitySpaceScale: Vector3 = Vector3(1f, 1f, 1f),
    override val worldSpaceScale: Vector3 = Vector3(1f, 1f, 1f),
    override val displayResolutionInPixels: PixelDimensions = PixelDimensions(0, 0),
) : CameraViewActivityPose {
    override fun transformPoseTo(pose: Pose, destination: ActivityPose): Pose {
        throw NotImplementedError("Intentionally left unimplemented for these test scenarios")
    }

    @Suppress("AsyncSuffixFuture")
    override fun hitTest(
        origin: Vector3,
        direction: Vector3,
        hitTestFilter: Int,
    ): ListenableFuture<HitTestResult> {
        throw NotImplementedError("Intentionally left unimplemented for these test scenarios")
    }
}

/**
 * A test implementation of a SceneCore [ActivitySpace] that allows for setting custom values.
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
class TestActivitySpace(
    private val fakeRuntimeActivitySpaceBase: ActivitySpace,
    override var activitySpacePose: Pose = Pose.Identity,
    override var activitySpaceScale: Vector3 = Vector3(1f, 1f, 1f),
    override val recommendedContentBoxInFullSpace: BoundingBox =
        BoundingBox(
            min = Vector3(-1.73f / 2, -1.61f / 2, -0.5f / 2),
            max = Vector3(1.73f / 2, 1.61f / 2, 0.5f / 2),
        ),
) : ActivitySpace by fakeRuntimeActivitySpaceBase {

    override fun getScale(relativeTo: Int): Vector3 {
        return activitySpaceScale
    }

    private var spaceUpdateListener: Runnable? = null

    override fun setOnSpaceUpdatedListener(listener: Runnable?, executor: Executor?) {
        this.spaceUpdateListener = listener
    }

    /**
     * Manually triggers the listener that was captured via [setOnSpaceUpdatedListener].
     *
     * This method is primarily intended for use in unit tests. It simulates the runtime invoking
     * the listener, which is necessary for testing code that uses suspending functions like
     * `ActivitySpace.awaitUpdate()` or otherwise waits on the listener callback.
     *
     * Call this method in your test after simulating the condition that should cause the space
     * update (e.g., changing `activitySpaceScale` from zero to non-zero) to manually resume any
     * coroutine that might be suspended waiting for the `onSpaceUpdated()` callback.
     *
     * The listener's `onSpaceUpdated()` method is invoked directly on the calling thread.
     */
    fun triggerOnSpaceUpdatedListener() {
        spaceUpdateListener?.run()
    }
}
