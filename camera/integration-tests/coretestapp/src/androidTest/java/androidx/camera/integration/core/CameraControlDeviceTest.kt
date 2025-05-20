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

package androidx.camera.integration.core

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.CONTROL_MAX_REGIONS_AE
import android.hardware.camera2.CameraCharacteristics.CONTROL_MAX_REGIONS_AF
import android.hardware.camera2.CameraCharacteristics.CONTROL_MAX_REGIONS_AWB
import android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON
import android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
import android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
import android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY
import android.hardware.camera2.CameraMetadata.FLASH_MODE_OFF
import android.hardware.camera2.CameraMetadata.FLASH_MODE_TORCH
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION
import android.hardware.camera2.params.MeteringRectangle
import android.os.Build
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.impl.CameraControlInternal
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.core.internal.CameraUseCaseAdapter.CameraException
import androidx.camera.integration.core.util.Camera2InteropUtil
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.testing.impl.CameraPipeConfigTestRule
import androidx.camera.testing.impl.CameraUtil
import androidx.camera.testing.impl.CameraUtil.PreTestCameraIdList
import androidx.camera.testing.impl.fakes.FakeLifecycleOwner
import androidx.concurrent.futures.await
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Test if camera control functionality can run well in real devices. */
@LargeTest
@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = 21)
class CameraControlDeviceTest(
    private val cameraSelector: CameraSelector,
    private val implName: String,
    private val cameraConfig: CameraXConfig
) {
    @get:Rule
    val cameraPipeConfigTestRule =
        CameraPipeConfigTestRule(
            active = implName == CameraPipeConfig::class.simpleName,
        )

    @get:Rule
    val cameraRule =
        CameraUtil.grantCameraPermissionAndPreTestAndPostTest(PreTestCameraIdList(cameraConfig))

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "selector={0},implName={1}")
        fun data() =
            listOf(
                arrayOf(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    Camera2Config::class.simpleName,
                    Camera2Config.defaultConfig()
                ),
                arrayOf(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    CameraPipeConfig::class.simpleName,
                    CameraPipeConfig.defaultConfig()
                ),
                arrayOf(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    Camera2Config::class.simpleName,
                    Camera2Config.defaultConfig()
                ),
                arrayOf(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    CameraPipeConfig::class.simpleName,
                    CameraPipeConfig.defaultConfig()
                )
            )

        private val METERING_REGIONS_DEFAULT: Array<MeteringRectangle> =
            arrayOf(MeteringRectangle(0, 0, 0, 0, 0))
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val analyzer = ImageAnalysis.Analyzer { obj: ImageProxy -> obj.close() }
    private val lifecycleOwner = FakeLifecycleOwner().also { it.startAndResume() }
    private val cameraCharacteristics =
        CameraUtil.getCameraCharacteristics(
            CameraUtil.getCameraIdWithLensFacing(cameraSelector.lensFacing!!)!!
        )!!
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var cameraControl: CameraControlInternal
    private lateinit var captureCallback: Camera2InteropUtil.CaptureCallback

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProcessCameraProvider.configureInstance(cameraConfig)
        cameraProvider = ProcessCameraProvider.awaitInstance(context)
        assumeTrue(cameraProvider.hasCamera(cameraSelector))
        captureCallback = Camera2InteropUtil.CaptureCallback()
    }

    @After
    fun tearDown() {
        if (::cameraProvider.isInitialized) {
            cameraProvider.shutdownAsync()[10000, TimeUnit.MILLISECONDS]
        }
    }

    @Test
    fun setFlashModeAuto_aeModeSetAndResultUpdated() {
        assumeTrue(CameraUtil.hasFlashUnitWithLensFacing(cameraSelector.lensFacing!!))
        bindUseCases()

        cameraControl.flashMode = ImageCapture.FLASH_MODE_AUTO

        captureCallback.verifyLastCaptureResult(
            mapOf(CaptureResult.CONTROL_AE_MODE to CONTROL_AE_MODE_ON_AUTO_FLASH)
        )
        assertThat(cameraControl.flashMode).isEqualTo(ImageCapture.FLASH_MODE_AUTO)
    }

    @Test
    fun setFlashModeOff_aeModeSetAndResultUpdated() {
        assumeTrue(CameraUtil.hasFlashUnitWithLensFacing(cameraSelector.lensFacing!!))
        bindUseCases()

        cameraControl.flashMode = ImageCapture.FLASH_MODE_OFF

        captureCallback.verifyLastCaptureResult(
            mapOf(CaptureResult.CONTROL_AE_MODE to CONTROL_AE_MODE_ON)
        )
        assertThat(cameraControl.flashMode).isEqualTo(ImageCapture.FLASH_MODE_OFF)
    }

    @Test
    fun setFlashModeOn_aeModeSetAndResultUpdated() {
        assumeTrue(CameraUtil.hasFlashUnitWithLensFacing(cameraSelector.lensFacing!!))
        bindUseCases()

        cameraControl.flashMode = ImageCapture.FLASH_MODE_ON

        captureCallback.verifyLastCaptureResult(
            mapOf(CaptureResult.CONTROL_AE_MODE to CONTROL_AE_MODE_ON_ALWAYS_FLASH)
        )
        assertThat(cameraControl.flashMode).isEqualTo(ImageCapture.FLASH_MODE_ON)
    }

    @Test
    fun startFocusAndMetering_3ARegionsUpdated() = runBlocking {
        assumeTrue(is3ASupported())
        val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
        val action = FocusMeteringAction.Builder(factory.createPoint(0f, 0f)).build()
        bindUseCases()

        cameraControl.startFocusAndMetering(action).await()

        val expectedAfCount =
            cameraCharacteristics.getMaxRegionCount(CONTROL_MAX_REGIONS_AF).coerceAtMost(1)
        val expectedAeCount =
            cameraCharacteristics.getMaxRegionCount(CONTROL_MAX_REGIONS_AE).coerceAtMost(1)
        val expectedAwbCount =
            cameraCharacteristics.getMaxRegionCount(CONTROL_MAX_REGIONS_AWB).coerceAtMost(1)
        // Check capture request instead of capture result because the focus and metering may not
        // converge depending on the environment.
        captureCallback.verifyFor(numOfCaptures = 60) { captureRequests, _ ->
            val captureRequest = captureRequests.last()
            val afRegions = captureRequest[CaptureRequest.CONTROL_AF_REGIONS] ?: emptyArray()
            val aeRegions = captureRequest[CaptureRequest.CONTROL_AE_REGIONS] ?: emptyArray()
            val awbRegions = captureRequest[CaptureRequest.CONTROL_AWB_REGIONS] ?: emptyArray()
            afRegions.weightedRegionCount == expectedAfCount &&
                aeRegions.weightedRegionCount == expectedAeCount &&
                awbRegions.weightedRegionCount == expectedAwbCount
        }
    }

    @Test
    fun cancelFocusAndMetering_3ARegionsReset() = runBlocking {
        assumeTrue(is3ASupported())
        val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
        val action = FocusMeteringAction.Builder(factory.createPoint(0f, 0f)).build()
        bindUseCases()

        cameraControl.startFocusAndMetering(action).await()
        cameraControl.cancelFocusAndMetering().await()
        // Check capture request instead of capture result because the focus and metering may not
        // converge depending on the environment.
        captureCallback.verifyFor(numOfCaptures = 60) { captureRequests, _ ->
            val captureRequest = captureRequests.last()
            val afRegions = captureRequest[CaptureRequest.CONTROL_AF_REGIONS] ?: emptyArray()
            val aeRegions = captureRequest[CaptureRequest.CONTROL_AE_REGIONS] ?: emptyArray()
            val awbRegions = captureRequest[CaptureRequest.CONTROL_AWB_REGIONS] ?: emptyArray()

            afRegions.weightedRegionCount == 0 &&
                aeRegions.weightedRegionCount == 0 &&
                awbRegions.weightedRegionCount == 0
        }
    }

    @Test
    fun setExposureCompensation_resultUpdated() {
        val exposureState = cameraProvider.getCameraInfo(cameraSelector).exposureState
        assumeTrue(exposureState.isExposureCompensationSupported)
        val upper = exposureState.exposureCompensationRange.upper
        bindUseCases()

        cameraControl.setExposureCompensationIndex(upper).get(3000, TimeUnit.MILLISECONDS)

        captureCallback.verifyLastCaptureResult(mapOf(CONTROL_AE_EXPOSURE_COMPENSATION to upper))
    }

    @Test
    fun enableTorch_resultUpdated() = runBlocking {
        assumeTrue(CameraUtil.hasFlashUnitWithLensFacing(cameraSelector.lensFacing!!))
        bindUseCases()

        assertFutureCompletes(cameraControl.enableTorch(true))

        captureCallback.verifyLastCaptureResult(mapOf(CaptureResult.FLASH_MODE to FLASH_MODE_TORCH))
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun setTorchStrengthLevel_resultUpdated() {
        assumeTrue(cameraProvider.getCameraInfo(cameraSelector).isTorchStrengthSupported)

        bindUseCases()

        cameraControl.enableTorch(true).get(3000, TimeUnit.MILLISECONDS)
        cameraControl
            .setTorchStrengthLevel(camera.cameraInfo.maxTorchStrengthLevel)
            .get(3000, TimeUnit.MILLISECONDS)

        captureCallback.verifyLastCaptureResult(
            mapOf(CaptureResult.FLASH_STRENGTH_LEVEL to camera.cameraInfo.maxTorchStrengthLevel)
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun setTorchStrengthLevel_torchDisabled_futureCompletes() {
        assumeTrue(cameraProvider.getCameraInfo(cameraSelector).isTorchStrengthSupported)

        bindUseCases()

        assertFutureCompletes(
            cameraControl.setTorchStrengthLevel(camera.cameraInfo.maxTorchStrengthLevel)
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun setTorchStrengthLevel_throwExceptionIfLessThanOne() {
        assumeTrue(cameraProvider.getCameraInfo(cameraSelector).isTorchStrengthSupported)

        bindUseCases()

        try {
            cameraControl.setTorchStrengthLevel(0).get(3000, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            assertThat(e.cause).isInstanceOf(java.lang.IllegalArgumentException::class.java)
            return
        }

        Assert.fail(
            "setTorchStrength didn't fail with an IllegalArgumentException when the given level is less than 1."
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun setTorchStrengthLevel_throwExceptionIfLargerThanMax() {
        assumeTrue(cameraProvider.getCameraInfo(cameraSelector).isTorchStrengthSupported)

        bindUseCases()

        try {
            cameraControl
                .setTorchStrengthLevel(camera.cameraInfo.maxTorchStrengthLevel + 1)
                .get(3000, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            assertThat(e.cause).isInstanceOf(java.lang.IllegalArgumentException::class.java)
            return
        }

        Assert.fail(
            "setTorchStrength didn't fail with an IllegalArgumentException when the given level is larger than the maximum."
        )
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM - 1)
    fun setTorchStrengthLevel_throwExceptionWhenApiNotMet() {
        bindUseCases()

        try {
            cameraControl
                .setTorchStrengthLevel(camera.cameraInfo.maxTorchStrengthLevel)
                .get(3000, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            assertThat(e.cause).isInstanceOf(UnsupportedOperationException::class.java)
            return
        }

        Assert.fail(
            "setTorchStrength didn't fail with an UnsupportedOperationException when the API level is lower than the requirement."
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun setLowLightBoost_resultUpdated() {
        assumeTrue(cameraProvider.getCameraInfo(cameraSelector).isLowLightBoostSupported)

        bindUseCases()

        cameraControl.enableLowLightBoostAsync(true).get(3000, TimeUnit.MILLISECONDS)

        captureCallback.verifyLastCaptureResult(
            mapOf(
                CaptureResult.CONTROL_AE_MODE to
                    CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY,
                CaptureResult.FLASH_MODE to FLASH_MODE_OFF
            )
        )
    }

    private fun bindUseCases() {
        instrumentation.runOnMainSync {
            try {
                val useCase =
                    ImageAnalysis.Builder()
                        .also { imageAnalysisBuilder ->
                            Camera2InteropUtil.setCamera2InteropOptions(
                                implName = implName,
                                builder = imageAnalysisBuilder,
                                captureCallback = captureCallback
                            )
                        }
                        .build()
                        .apply { setAnalyzer(CameraXExecutors.ioExecutor(), analyzer) }
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, useCase)
                cameraControl = camera.cameraControl as CameraControlInternal
            } catch (e: CameraException) {
                throw IllegalArgumentException(e)
            }
        }
    }

    private fun CameraCharacteristics.getMaxRegionCount(
        optionMaxRegions: CameraCharacteristics.Key<Int>
    ) = get(optionMaxRegions) ?: 0

    private val Array<MeteringRectangle>.weightedRegionCount: Int
        get() {
            var count = 0
            forEach { count += if (it.meteringWeight != 0) 1 else 0 }
            return count
        }

    private fun is3ASupported(): Boolean {
        return cameraCharacteristics.getMaxRegionCount(CONTROL_MAX_REGIONS_AF) > 0 ||
            cameraCharacteristics.getMaxRegionCount(CONTROL_MAX_REGIONS_AE) > 0 ||
            cameraCharacteristics.getMaxRegionCount(CONTROL_MAX_REGIONS_AWB) > 0
    }

    private fun <T> assertFutureCompletes(future: ListenableFuture<T>) {
        try {
            future[10, TimeUnit.SECONDS]
        } catch (e: Exception) {
            Assert.fail("future fail:$e")
        }
    }
}
