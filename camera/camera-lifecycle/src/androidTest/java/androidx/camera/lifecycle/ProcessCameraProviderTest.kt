/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.lifecycle

import android.content.Context
import android.content.pm.PackageManager.FEATURE_CAMERA_CONCURRENT
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.Surface
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.camera.core.CameraSelector.LENS_FACING_FRONT
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.concurrent.CameraCoordinator.CAMERA_OPERATING_MODE_UNSPECIFIED
import androidx.camera.core.impl.AdapterCameraInfo
import androidx.camera.core.impl.CameraConfig
import androidx.camera.core.impl.CameraFactory
import androidx.camera.core.impl.CameraInfoInternal
import androidx.camera.core.impl.CameraThreadConfig
import androidx.camera.core.impl.Config
import androidx.camera.core.impl.ExtendedCameraConfigProviderStore
import androidx.camera.core.impl.Identifier
import androidx.camera.core.impl.MutableOptionsBundle
import androidx.camera.core.impl.SessionProcessor
import androidx.camera.core.impl.UseCaseConfigFactory.CaptureType
import androidx.camera.core.impl.utils.executor.CameraXExecutors.mainThreadExecutor
import androidx.camera.core.internal.utils.ImageUtil
import androidx.camera.testing.fakes.FakeAppConfig
import androidx.camera.testing.fakes.FakeCamera
import androidx.camera.testing.fakes.FakeCameraInfoInternal
import androidx.camera.testing.impl.CameraPipeConfigTestRule
import androidx.camera.testing.impl.CameraUtil
import androidx.camera.testing.impl.fakes.FakeCameraConfig
import androidx.camera.testing.impl.fakes.FakeCameraCoordinator
import androidx.camera.testing.impl.fakes.FakeCameraDeviceSurfaceManager
import androidx.camera.testing.impl.fakes.FakeCameraFactory
import androidx.camera.testing.impl.fakes.FakeCameraFilter
import androidx.camera.testing.impl.fakes.FakeLifecycleOwner
import androidx.camera.testing.impl.fakes.FakeSessionProcessor
import androidx.camera.testing.impl.fakes.FakeSurfaceEffect
import androidx.camera.testing.impl.fakes.FakeSurfaceProcessor
import androidx.camera.testing.impl.fakes.FakeUseCase
import androidx.camera.testing.impl.fakes.FakeUseCaseConfig
import androidx.camera.testing.impl.fakes.FakeUseCaseConfigFactory
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.concurrent.futures.await
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.testutils.assertThrows
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SmallTest
@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = 21)
class ProcessCameraProviderTest(
    private val implName: String,
    private val cameraConfig: CameraXConfig,
) {

    @get:Rule
    val cameraPipeConfigTestRule =
        CameraPipeConfigTestRule(
            active = implName.contains(CameraPipeConfig::class.simpleName!!),
        )

    @get:Rule
    val cameraRule =
        CameraUtil.grantCameraPermissionAndPreTestAndPostTest(
            CameraUtil.PreTestCameraIdList(cameraConfig)
        )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() =
            listOf(
                arrayOf(Camera2Config::class.simpleName, Camera2Config.defaultConfig()),
                arrayOf(CameraPipeConfig::class.simpleName, CameraPipeConfig.defaultConfig())
            )
    }

    private val context = ApplicationProvider.getApplicationContext() as Context
    private val lifecycleOwner0 = FakeLifecycleOwner()
    private val lifecycleOwner1 = FakeLifecycleOwner()
    private val cameraCoordinator = FakeCameraCoordinator()
    private val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private lateinit var provider: ProcessCameraProvider

    @Before
    fun setUp() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(cameraSelector.lensFacing!!))
    }

    @After
    fun tearDown() {
        runBlocking(MainScope().coroutineContext) {
            try {
                val provider = ProcessCameraProvider.getInstance(context).await()
                provider.shutdownAsync().await()
            } catch (e: IllegalStateException) {
                // ProcessCameraProvider may not be configured. Ignore.
            }
        }
    }

    @Test
    fun bindUseCaseGroupWithEffect_effectIsSetOnUseCase() {
        // Arrange.
        ProcessCameraProvider.configureInstance(cameraConfig)
        val surfaceProcessor = FakeSurfaceProcessor(mainThreadExecutor())
        val effect = FakeSurfaceEffect(mainThreadExecutor(), surfaceProcessor)
        val preview = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
        val useCaseGroup = UseCaseGroup.Builder().addUseCase(preview).addEffect(effect).build()

        runBlocking(MainScope().coroutineContext) {
            // Act.
            provider = ProcessCameraProvider.getInstance(context).await()
            provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCaseGroup)

            // Assert.
            assertThat(preview.effect).isEqualTo(effect)
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @OptIn(ExperimentalCameraProviderConfiguration::class)
    @Test
    fun canRetrieveCamera_withZeroUseCases() {
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val camera = provider.bindToLifecycle(lifecycleOwner0, cameraSelector)
            assertThat(camera).isNotNull()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bindUseCase_isBound() {
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase)

            assertThat(provider.isBound(useCase)).isTrue()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bindSecondUseCaseToDifferentLifecycle_firstUseCaseStillBound() {
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase0)
            provider.bindToLifecycle(lifecycleOwner1, cameraSelector, useCase1)

            // TODO(b/158595693) Add check on whether or not camera for fakeUseCase0 should be
            //  exist or not
            // assertThat(fakeUseCase0.camera).isNotNull() (or isNull()?)
            assertThat(provider.isBound(useCase0)).isTrue()
            assertThat(useCase1.camera).isNotNull()
            assertThat(provider.isBound(useCase1)).isTrue()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun isNotBound_afterUnbind() {
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase)

            provider.unbind(useCase)

            assertThat(provider.isBound(useCase)).isFalse()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun unbindFirstUseCase_secondUseCaseStillBound() {
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase0, useCase1)

            provider.unbind(useCase0)

            assertThat(useCase0.camera).isNull()
            assertThat(provider.isBound(useCase0)).isFalse()
            assertThat(useCase1.camera).isNotNull()
            assertThat(provider.isBound(useCase1)).isTrue()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun unbindAll_unbindsAllUseCasesFromCameras() {
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase)

            provider.unbindAll()

            assertThat(useCase.camera).isNull()
            assertThat(provider.isBound(useCase)).isFalse()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bindMultipleUseCases() {
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase0, useCase1)

            assertThat(provider.isBound(useCase0)).isTrue()
            assertThat(provider.isBound(useCase1)).isTrue()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bind_createsDifferentLifecycleCameras_forDifferentLifecycles() {
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val camera0 = provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase0)

            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val camera1 = provider.bindToLifecycle(lifecycleOwner1, cameraSelector, useCase1)

            assertThat(camera0).isNotEqualTo(camera1)
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bind_returnTheSameCameraForSameSelectorAndLifecycleOwner() {
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            val camera0 = provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase0)
            val camera1 = provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase1)

            assertThat(camera0).isSameInstanceAs(camera1)
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bindUseCases_withDifferentLensFacingButSameLifecycleOwner() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LENS_FACING_FRONT))
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase0)

            assertThrows<IllegalArgumentException> {
                provider.bindToLifecycle(
                    lifecycleOwner0,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    useCase1
                )
            }
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bindUseCases_withDifferentLensFacingAndLifecycle() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LENS_FACING_FRONT))
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            val camera0 = provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase0)

            val camera1 =
                provider.bindToLifecycle(
                    lifecycleOwner1,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    useCase1
                )

            assertThat(camera0).isNotEqualTo(camera1)
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bindUseCases_withNotExistedLensFacingCamera() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LENS_FACING_FRONT))
        val cameraFactoryProvider =
            CameraFactory.Provider { _, _, _, _ ->
                val cameraFactory = FakeCameraFactory()
                cameraFactory.insertCamera(LENS_FACING_BACK, "0") {
                    FakeCamera("0", null, FakeCameraInfoInternal("0", 0, LENS_FACING_BACK))
                }
                cameraFactory.cameraCoordinator = FakeCameraCoordinator()
                cameraFactory
            }

        val appConfigBuilder =
            CameraXConfig.Builder()
                .setCameraFactoryProvider(cameraFactoryProvider)
                .setDeviceSurfaceManagerProvider { _, _, _ -> FakeCameraDeviceSurfaceManager() }
                .setUseCaseConfigFactoryProvider { FakeUseCaseConfigFactory() }

        ProcessCameraProvider.configureInstance(appConfigBuilder.build())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()

            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            // The front camera is not defined, we should get the IllegalArgumentException when it
            // tries to get the camera.
            assertThrows<IllegalArgumentException> {
                provider.bindToLifecycle(
                    lifecycleOwner0,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    useCase
                )
            }
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bindUseCases_viewPortUpdated() {
        runBlocking(MainScope().coroutineContext) {
            // Arrange.
            ProcessCameraProvider.configureInstance(cameraConfig)
            provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build()
            val imageCapture = ImageCapture.Builder().build()
            val imageAnalysis = ImageAnalysis.Builder().build()
            val videoCapture = VideoCapture.Builder(Recorder.Builder().build()).build()
            val aspectRatio = Rational(2, 1)
            val viewPort = ViewPort.Builder(aspectRatio, Surface.ROTATION_0).build()

            // Act.
            provider.bindToLifecycle(
                FakeLifecycleOwner(),
                cameraSelector,
                UseCaseGroup.Builder()
                    .setViewPort(viewPort)
                    .addUseCase(preview)
                    .addUseCase(imageCapture)
                    .addUseCase(imageAnalysis)
                    .addUseCase(videoCapture)
                    .build()
            )

            // Assert: The aspect ratio of the use cases should be close to the aspect ratio of the
            // view port set to the UseCaseGroup.
            val aspectRatioThreshold = 0.01
            assertThat(preview.viewPortCropRect!!.aspectRatio().toDouble())
                .isWithin(aspectRatioThreshold)
                .of(preview.getExpectedAspectRatio(aspectRatio))
            assertThat(imageCapture.viewPortCropRect!!.aspectRatio().toDouble())
                .isWithin(aspectRatioThreshold)
                .of(imageCapture.getExpectedAspectRatio(aspectRatio))
            assertThat(imageAnalysis.viewPortCropRect!!.aspectRatio().toDouble())
                .isWithin(aspectRatioThreshold)
                .of(imageAnalysis.getExpectedAspectRatio(aspectRatio))
            assertThat(videoCapture.viewPortCropRect!!.aspectRatio().toDouble())
                .isWithin(aspectRatioThreshold)
                .of(videoCapture.getExpectedAspectRatio(aspectRatio))
        }
    }

    private fun UseCase.getExpectedAspectRatio(aspectRatio: Rational): Double {
        val camera = this.camera!!
        val isStreamSharingOn = !camera.hasTransform
        // If stream sharing is on, the expected aspect ratio doesn't have to be adjusted with
        // sensor rotation.
        val rotation = if (isStreamSharingOn) 0 else camera.cameraInfo.sensorRotationDegrees
        return ImageUtil.getRotatedAspectRatio(rotation, aspectRatio).toDouble()
    }

    @Test
    fun lifecycleCameraIsNotActive_withZeroUseCases_bindBeforeLifecycleStarted() {
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(lifecycleOwner0, cameraSelector) as LifecycleCamera
            lifecycleOwner0.startAndResume()
            assertThat(camera.isActive).isFalse()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun lifecycleCameraIsNotActive_withZeroUseCases_bindAfterLifecycleStarted() {
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            lifecycleOwner0.startAndResume()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(lifecycleOwner0, cameraSelector) as LifecycleCamera
            assertThat(camera.isActive).isFalse()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun lifecycleCameraIsActive_withUseCases_bindBeforeLifecycleStarted() {
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase)
                    as LifecycleCamera
            lifecycleOwner0.startAndResume()
            assertThat(camera.isActive).isTrue()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun lifecycleCameraIsActive_withUseCases_bindAfterLifecycleStarted() {
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            lifecycleOwner0.startAndResume()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(lifecycleOwner0, cameraSelector, useCase)
                    as LifecycleCamera
            assertThat(camera.isActive).isTrue()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun lifecycleCameraIsNotActive_bindAfterLifecycleDestroyed() {
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            lifecycleOwner0.destroy()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(
                    lifecycleOwner0,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    useCase
                ) as LifecycleCamera
            assertThat(camera.isActive).isFalse()
        }
    }

    @Test
    fun lifecycleCameraIsNotActive_unbindUseCase() {
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            lifecycleOwner0.startAndResume()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(
                    lifecycleOwner0,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    useCase
                ) as LifecycleCamera
            assertThat(camera.isActive).isTrue()
            provider.unbind(useCase)
            assertThat(camera.isActive).isFalse()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun lifecycleCameraIsNotActive_unbindAll() {
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            lifecycleOwner0.startAndResume()
            val camera: LifecycleCamera =
                provider.bindToLifecycle(
                    lifecycleOwner0,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    useCase
                ) as LifecycleCamera
            assertThat(camera.isActive).isTrue()
            provider.unbindAll()
            assertThat(camera.isActive).isFalse()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun getAvailableCameraInfos_usesAllCameras() {
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking {
            provider = ProcessCameraProvider.getInstance(context).await()
            val cameraCount =
                cameraConfig
                    .getCameraFactoryProvider(null)!!
                    .newInstance(
                        context,
                        CameraThreadConfig.create(
                            mainThreadExecutor(),
                            Handler(Looper.getMainLooper())
                        ),
                        null,
                        -1L
                    )
                    .availableCameraIds
                    .size

            assertThat(provider.availableCameraInfos.size).isEqualTo(cameraCount)
        }
    }

    @Test
    fun getAvailableCameraInfos_usesFilteredCameras() {
        ProcessCameraProvider.configureInstance(
            FakeAppConfig.create(CameraSelector.DEFAULT_BACK_CAMERA)
        )
        runBlocking {
            provider = ProcessCameraProvider.getInstance(context).await()

            val cameraInfos = provider.availableCameraInfos
            assertThat(cameraInfos.size).isEqualTo(1)

            val cameraInfo = cameraInfos.first() as FakeCameraInfoInternal
            assertThat(cameraInfo.lensFacing).isEqualTo(LENS_FACING_BACK)
        }
    }

    @Test
    fun getCameraInfo_sameCameraInfoWithBindToLifecycle_afterBinding() {
        // Arrange.
        ProcessCameraProvider.configureInstance(cameraConfig)

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Act: getting the camera info after bindToLifecycle.
            val camera = provider.bindToLifecycle(lifecycleOwner0, cameraSelector)
            val cameraInfoInternal1: CameraInfoInternal =
                provider.getCameraInfo(cameraSelector) as CameraInfoInternal
            val cameraInfoInternal2: CameraInfoInternal = camera.cameraInfo as CameraInfoInternal

            // Assert.
            assertThat(cameraInfoInternal1).isSameInstanceAs(cameraInfoInternal2)
        }
    }

    @Test
    fun getCameraInfo_sameCameraInfoWithBindToLifecycle_beforeBinding() {
        // Arrange.
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Act: getting the camera info before bindToLifecycle.
            val cameraInfoInternal1: CameraInfoInternal =
                provider.getCameraInfo(cameraSelector) as CameraInfoInternal
            val camera = provider.bindToLifecycle(lifecycleOwner0, cameraSelector)
            val cameraInfoInternal2: CameraInfoInternal = camera.cameraInfo as CameraInfoInternal

            // Assert.
            assertThat(cameraInfoInternal1).isSameInstanceAs(cameraInfoInternal2)
        }
    }

    @Test
    fun getCameraInfo_containExtendedCameraConfig() {
        // Arrange.
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking {
            provider = ProcessCameraProvider.getInstance(context).await()
            val id = Identifier.create("FakeId")
            val cameraConfig = FakeCameraConfig(postviewSupported = true)
            ExtendedCameraConfigProviderStore.addConfig(id) { _, _ -> cameraConfig }
            val cameraSelector =
                CameraSelector.Builder().addCameraFilter(FakeCameraFilter(id)).build()

            // Act.
            val adapterCameraInfo = provider.getCameraInfo(cameraSelector) as AdapterCameraInfo

            // Assert.
            assertThat(adapterCameraInfo.isPostviewSupported).isTrue()
        }
    }

    @Test
    fun getCameraInfo_exceptionWhenCameraSelectorInvalid() {
        // Arrange.
        ProcessCameraProvider.configureInstance(cameraConfig)
        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            // Intentionally create a camera selector that doesn't result in a camera.
            val cameraSelector =
                CameraSelector.Builder().addCameraFilter { ArrayList<CameraInfo>() }.build()

            // Act & Assert.
            assertThrows(IllegalArgumentException::class.java) {
                provider.getCameraInfo(cameraSelector)
            }
        }
    }

    @Test
    fun getAvailableConcurrentCameraInfos() {
        ProcessCameraProvider.configureInstance(createConcurrentCameraAppConfig())
        runBlocking {
            provider = ProcessCameraProvider.getInstance(context).await()
            assertThat(provider.availableConcurrentCameraInfos.size).isEqualTo(2)
            assertThat(provider.availableConcurrentCameraInfos[0].size).isEqualTo(2)
            assertThat(provider.availableConcurrentCameraInfos[1].size).isEqualTo(2)
        }
    }

    @Test
    fun shutdown_clearsPreviousConfiguration() {
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())

        runBlocking {
            provider = ProcessCameraProvider.getInstance(context).await()
            // Clear the configuration so we can reinit
            provider.shutdownAsync().await()
        }

        // Should not throw exception
        ProcessCameraProvider.configureInstance(FakeAppConfig.create())
        assertThat(cameraCoordinator.cameraOperatingMode)
            .isEqualTo(CAMERA_OPERATING_MODE_UNSPECIFIED)
        assertThat(cameraCoordinator.concurrentCameraSelectors).isEmpty()
        assertThat(cameraCoordinator.activeConcurrentCameraInfos).isEmpty()
    }

    @Test
    fun bindConcurrentCamera_isBound() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LENS_FACING_FRONT))
        ProcessCameraProvider.configureInstance(createConcurrentCameraAppConfig())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            val singleCameraConfig0 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase0).build(),
                    lifecycleOwner0
                )
            val singleCameraConfig1 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase1).build(),
                    lifecycleOwner1
                )

            if (context.packageManager.hasSystemFeature(FEATURE_CAMERA_CONCURRENT)) {
                val concurrentCamera =
                    provider.bindToLifecycle(listOf(singleCameraConfig0, singleCameraConfig1))

                assertThat(concurrentCamera).isNotNull()
                assertThat(concurrentCamera.cameras.size).isEqualTo(2)
                assertThat(provider.isBound(useCase0)).isTrue()
                assertThat(provider.isBound(useCase1)).isTrue()
                assertThat(provider.isConcurrentCameraModeOn).isTrue()
            } else {
                assertThrows<UnsupportedOperationException> {
                    provider.bindToLifecycle(listOf(singleCameraConfig0, singleCameraConfig1))
                }
            }
        }
    }

    @Test
    fun bindConcurrentPhysicalCamera_isBound() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LENS_FACING_FRONT))
        ProcessCameraProvider.configureInstance(createConcurrentCameraAppConfig())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            val singleCameraConfig0 =
                SingleCameraConfig(
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build(),
                    UseCaseGroup.Builder().addUseCase(useCase0).build(),
                    lifecycleOwner0
                )
            val singleCameraConfig1 =
                SingleCameraConfig(
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build(),
                    UseCaseGroup.Builder().addUseCase(useCase1).build(),
                    lifecycleOwner0
                )

            val concurrentCamera =
                provider.bindToLifecycle(listOf(singleCameraConfig0, singleCameraConfig1))

            assertThat(concurrentCamera).isNotNull()
            assertThat(concurrentCamera.cameras.size).isEqualTo(1)
            assertThat(provider.isBound(useCase0)).isTrue()
            assertThat(provider.isBound(useCase1)).isTrue()
            assertThat(provider.isConcurrentCameraModeOn).isFalse()
        }
    }

    @Test
    fun bindConcurrentCameraTwice_isBound() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LENS_FACING_FRONT))
        ProcessCameraProvider.configureInstance(createConcurrentCameraAppConfig())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase2 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            val singleCameraConfig0 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase0).build(),
                    lifecycleOwner0
                )
            val singleCameraConfig1 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase1).build(),
                    lifecycleOwner1
                )
            val singleCameraConfig2 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase2).build(),
                    lifecycleOwner1
                )

            if (context.packageManager.hasSystemFeature(FEATURE_CAMERA_CONCURRENT)) {
                val concurrentCamera0 =
                    provider.bindToLifecycle(listOf(singleCameraConfig0, singleCameraConfig1))

                assertThat(concurrentCamera0).isNotNull()
                assertThat(concurrentCamera0.cameras.size).isEqualTo(2)
                assertThat(provider.isBound(useCase0)).isTrue()
                assertThat(provider.isBound(useCase1)).isTrue()
                assertThat(provider.isConcurrentCameraModeOn).isTrue()
            } else {
                assertThrows<UnsupportedOperationException> {
                    provider.bindToLifecycle(listOf(singleCameraConfig0, singleCameraConfig1))
                }
            }

            if (context.packageManager.hasSystemFeature(FEATURE_CAMERA_CONCURRENT)) {
                val concurrentCamera1 =
                    provider.bindToLifecycle(listOf(singleCameraConfig0, singleCameraConfig2))

                assertThat(concurrentCamera1).isNotNull()
                assertThat(concurrentCamera1.cameras.size).isEqualTo(2)
                assertThat(provider.isBound(useCase0)).isTrue()
                assertThat(provider.isBound(useCase2)).isTrue()
                assertThat(provider.isConcurrentCameraModeOn).isTrue()
            } else {
                assertThrows<UnsupportedOperationException> {
                    provider.bindToLifecycle(listOf(singleCameraConfig0, singleCameraConfig2))
                }
            }
        }
    }

    @Test
    fun bindConcurrentCamera_lessThanTwoSingleCameraConfigs() {
        ProcessCameraProvider.configureInstance(createConcurrentCameraAppConfig())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            val singleCameraConfig0 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase0).build(),
                    lifecycleOwner0
                )

            assertThrows<IllegalArgumentException> {
                provider.bindToLifecycle(listOf(singleCameraConfig0))
            }
        }
    }

    @Test
    fun bindConcurrentCamera_moreThanTwoSingleCameraConfigs() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LENS_FACING_FRONT))
        ProcessCameraProvider.configureInstance(createConcurrentCameraAppConfig())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()

            val singleCameraConfig0 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase0).build(),
                    lifecycleOwner0
                )
            val singleCameraConfig1 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase1).build(),
                    lifecycleOwner1
                )
            val singleCameraConfig2 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase0).build(),
                    lifecycleOwner1
                )

            assertThrows<java.lang.IllegalArgumentException> {
                provider.bindToLifecycle(
                    listOf(singleCameraConfig0, singleCameraConfig1, singleCameraConfig2)
                )
            }
        }
    }

    @Test
    fun bindConcurrentCamera_isDualRecording() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LENS_FACING_FRONT))
        ProcessCameraProvider.configureInstance(createConcurrentCameraAppConfig())

        runBlocking(MainScope().coroutineContext) {
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase0 = Preview.Builder().setSessionOptionUnpacker { _, _, _ -> }.build()
            val useCase1 =
                FakeUseCase(
                    FakeUseCaseConfig.Builder(CaptureType.VIDEO_CAPTURE).useCaseConfig,
                    CaptureType.VIDEO_CAPTURE
                )

            val singleCameraConfig0 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase0).addUseCase(useCase1).build(),
                    lifecycleOwner0
                )
            val singleCameraConfig1 =
                SingleCameraConfig(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    UseCaseGroup.Builder().addUseCase(useCase0).addUseCase(useCase1).build(),
                    lifecycleOwner1
                )

            if (context.packageManager.hasSystemFeature(FEATURE_CAMERA_CONCURRENT)) {
                val concurrentCamera =
                    provider.bindToLifecycle(listOf(singleCameraConfig0, singleCameraConfig1))

                assertThat(concurrentCamera).isNotNull()
                assertThat(concurrentCamera.cameras.size).isEqualTo(1)
                assertThat(provider.isBound(useCase0)).isTrue()
                assertThat(provider.isBound(useCase1)).isTrue()
                assertThat(provider.isConcurrentCameraModeOn).isTrue()
            } else {
                assertThrows<UnsupportedOperationException> {
                    provider.bindToLifecycle(listOf(singleCameraConfig0, singleCameraConfig1))
                }
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 23)
    fun bindWithExtensions_doesNotImpactPreviousCamera(): Unit =
        runBlocking(Dispatchers.Main) {
            // 1. Arrange.
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val cameraSelectorWithExtensions =
                getCameraSelectorWithLimitedCapabilities(
                    cameraSelector,
                    emptySet() // All capabilities are not supported.
                )
            ProcessCameraProvider.configureInstance(cameraConfig)
            provider = ProcessCameraProvider.getInstance(context).await()
            val useCase = Preview.Builder().build()

            // 2. Act: bind with and then without Extensions.
            // bind with regular cameraSelector to get the regular camera (with empty use cases)
            val camera = provider.bindToLifecycle(lifecycleOwner0, cameraSelector)
            // bind with extensions cameraSelector to get the restricted version of camera.
            val cameraWithExtensions =
                provider.bindToLifecycle(lifecycleOwner0, cameraSelectorWithExtensions, useCase)

            // 3. Assert: ensure we can different instances of Camera and one does not affect the
            // other.
            assertThat(camera).isNotSameInstanceAs(cameraWithExtensions)

            // the Extensions CameraControl does not support the zoom.
            assertThrows<IllegalStateException> {
                cameraWithExtensions.cameraControl.setZoomRatio(1.0f).await()
            }

            // only the Extensions CameraInfo does not support the zoom.
            assertThat(camera.cameraInfo.zoomState.value!!.maxZoomRatio).isGreaterThan(1.0f)
            assertThat(cameraWithExtensions.cameraInfo.zoomState.value!!.maxZoomRatio)
                .isEqualTo(1.0f)
        }

    @RequiresApi(23)
    private fun getCameraSelectorWithLimitedCapabilities(
        cameraSelector: CameraSelector,
        supportedCapabilities: Set<Int>
    ): CameraSelector {
        val identifier = Identifier.create("idStr")
        val sessionProcessor =
            FakeSessionProcessor(supportedCameraOperations = supportedCapabilities)
        ExtendedCameraConfigProviderStore.addConfig(identifier) { _, _ ->
            object : CameraConfig {
                override fun getConfig(): Config {
                    return MutableOptionsBundle.create()
                }

                override fun getCompatibilityId(): Identifier {
                    return identifier
                }

                override fun getSessionProcessor(valueIfMissing: SessionProcessor?) =
                    sessionProcessor

                override fun getSessionProcessor() = sessionProcessor
            }
        }

        val builder = CameraSelector.Builder.fromSelector(cameraSelector)
        builder.addCameraFilter(
            object : CameraFilter {
                override fun filter(cameraInfos: MutableList<CameraInfo>): MutableList<CameraInfo> {
                    val newCameraInfos = mutableListOf<CameraInfo>()
                    newCameraInfos.addAll(cameraInfos)
                    return newCameraInfos
                }

                override fun getIdentifier(): Identifier {
                    return identifier
                }
            }
        )

        return builder.build()
    }

    private fun createConcurrentCameraAppConfig(): CameraXConfig {
        val combination0 =
            mapOf(
                "0" to CameraSelector.Builder().requireLensFacing(LENS_FACING_BACK).build(),
                "1" to CameraSelector.Builder().requireLensFacing(LENS_FACING_FRONT).build()
            )
        val combination1 =
            mapOf(
                "0" to CameraSelector.Builder().requireLensFacing(LENS_FACING_BACK).build(),
                "2" to CameraSelector.Builder().requireLensFacing(LENS_FACING_FRONT).build()
            )

        cameraCoordinator.addConcurrentCameraIdsAndCameraSelectors(combination0)
        cameraCoordinator.addConcurrentCameraIdsAndCameraSelectors(combination1)
        val cameraFactoryProvider =
            CameraFactory.Provider { _, _, _, _ ->
                val cameraFactory = FakeCameraFactory()
                cameraFactory.insertCamera(LENS_FACING_BACK, "0") {
                    FakeCamera("0", null, FakeCameraInfoInternal("0", 0, LENS_FACING_BACK))
                }
                cameraFactory.insertCamera(LENS_FACING_FRONT, "1") {
                    FakeCamera("1", null, FakeCameraInfoInternal("1", 0, LENS_FACING_FRONT))
                }
                cameraFactory.insertCamera(LENS_FACING_FRONT, "2") {
                    FakeCamera("2", null, FakeCameraInfoInternal("2", 0, LENS_FACING_FRONT))
                }
                cameraFactory.cameraCoordinator = cameraCoordinator
                cameraFactory
            }
        val appConfigBuilder =
            CameraXConfig.Builder()
                .setCameraFactoryProvider(cameraFactoryProvider)
                .setDeviceSurfaceManagerProvider { _, _, _ -> FakeCameraDeviceSurfaceManager() }
                .setUseCaseConfigFactoryProvider { FakeUseCaseConfigFactory() }

        return appConfigBuilder.build()
    }

    private fun Rect.aspectRatio(rotationDegrees: Int = 0): Rational {
        return if (rotationDegrees % 180 != 0) Rational(height(), width())
        else Rational(width(), height())
    }
}
