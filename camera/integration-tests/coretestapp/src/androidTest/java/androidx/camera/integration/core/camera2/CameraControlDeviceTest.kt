/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.camera.integration.core.camera2

import android.content.Context
import android.os.Build
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.core.internal.CameraUseCaseAdapter.CameraException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.testing.impl.CameraPipeConfigTestRule
import androidx.camera.testing.impl.CameraUtil
import androidx.camera.testing.impl.CameraUtil.PreTestCameraIdList
import androidx.camera.testing.impl.fakes.FakeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test if camera control functionality can run well in real devices. Autofocus may not work well in
 * devices because the camera might be faced down to the desktop and the auto-focus will never
 * finish on some devices. Thus we don't test AF related functions.
 */
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
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val analyzer = ImageAnalysis.Analyzer { obj: ImageProxy -> obj.close() }
    private val lifecycleOwner = FakeLifecycleOwner().also { it.startAndResume() }
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProcessCameraProvider.configureInstance(cameraConfig)
        cameraProvider = ProcessCameraProvider.awaitInstance(context)
        assumeTrue(cameraProvider.hasCamera(cameraSelector))
    }

    @After
    fun tearDown() {
        if (::cameraProvider.isInitialized) {
            cameraProvider.shutdownAsync()[10000, TimeUnit.MILLISECONDS]
        }
    }

    @Test
    fun enableTorch_futureCompletes() {
        assumeTrue(CameraUtil.hasFlashUnitWithLensFacing(cameraSelector.lensFacing!!))

        bindUseCases()

        assertFutureCompletes(camera.cameraControl.enableTorch(true))
    }

    @Test
    fun rebindAndEnableTorch_futureCompletes() = runBlocking {
        assumeTrue(CameraUtil.hasFlashUnitWithLensFacing(cameraSelector.lensFacing!!))
        val camera =
            withContext(Dispatchers.Main) {
                try {
                    val useCase1 = ImageAnalysis.Builder().build()
                    useCase1.setAnalyzer(CameraXExecutors.ioExecutor(), analyzer)
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, useCase1)
                    cameraProvider.unbindAll()

                    val useCase2 = ImageAnalysis.Builder().build()
                    useCase2.setAnalyzer(CameraXExecutors.ioExecutor(), analyzer)
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, useCase2)
                } catch (e: CameraException) {
                    throw IllegalArgumentException(e)
                }
            }
        val result = camera.cameraControl.enableTorch(true)
        assertFutureCompletes(result)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun setTorchStrengthLevel_torchEnabled_futureCompletes() {
        assumeTrue(cameraProvider.getCameraInfo(cameraSelector).isTorchStrengthSupported)

        bindUseCases()

        camera.cameraControl.enableTorch(true).get()
        assertFutureCompletes(
            camera.cameraControl.setTorchStrengthLevel(camera.cameraInfo.maxTorchStrengthLevel)
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun setTorchStrengthLevel_torchDisabled_futureCompletes() {
        assumeTrue(cameraProvider.getCameraInfo(cameraSelector).isTorchStrengthSupported)

        bindUseCases()

        assertFutureCompletes(
            camera.cameraControl.setTorchStrengthLevel(camera.cameraInfo.maxTorchStrengthLevel)
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun setTorchStrengthLevel_throwExceptionIfLessThanOne() {
        assumeTrue(cameraProvider.getCameraInfo(cameraSelector).isTorchStrengthSupported)

        bindUseCases()

        try {
            camera.cameraControl.setTorchStrengthLevel(0).get()
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
            camera.cameraControl
                .setTorchStrengthLevel(camera.cameraInfo.maxTorchStrengthLevel + 1)
                .get()
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
            camera.cameraControl
                .setTorchStrengthLevel(camera.cameraInfo.maxTorchStrengthLevel)
                .get()
        } catch (e: ExecutionException) {
            assertThat(e.cause).isInstanceOf(java.lang.UnsupportedOperationException::class.java)
            return
        }

        Assert.fail(
            "setTorchStrength didn't fail with an UnsupportedOperationException when the API level is lower than the requirement."
        )
    }

    private fun bindUseCases() {
        instrumentation.runOnMainSync {
            try {
                val useCase = ImageAnalysis.Builder().build()
                useCase.setAnalyzer(CameraXExecutors.ioExecutor(), analyzer)
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, useCase)
            } catch (e: CameraException) {
                throw IllegalArgumentException(e)
            }
        }
    }

    private fun <T> assertFutureCompletes(future: ListenableFuture<T>) {
        try {
            future[10, TimeUnit.SECONDS]
        } catch (e: Exception) {
            Assert.fail("future fail:$e")
        }
    }
}
