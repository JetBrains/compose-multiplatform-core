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
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.testing.impl.CameraPipeConfigTestRule
import androidx.camera.testing.impl.CameraUtil
import androidx.camera.testing.impl.CameraUtil.PreTestCameraIdList
import androidx.camera.testing.impl.LabTestRule
import androidx.camera.testing.impl.SurfaceTextureProvider
import androidx.camera.testing.impl.fakes.FakeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized // Or your preferred test runner

/** API Compat test for [CameraState] APIs */
@LargeTest
@RunWith(Parameterized::class) // Example if you parameterize by camera
class CameraStateTest(
    private val testName: String,
    private val lensFacing: Int,
    private val implName: String,
    private val cameraConfig: CameraXConfig,
) {
    @get:Rule
    val cameraPipeConfigTestRule =
        CameraPipeConfigTestRule(active = implName == CameraPipeConfig::class.simpleName)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() =
            mutableListOf<Array<Any?>>().apply {
                CameraUtil.getAvailableCameraSelectors().forEach { selector ->
                    val lensFacing = selector.lensFacing
                    add(
                        arrayOf(
                            "config=${Camera2Config::class.simpleName} lensFacing={$lensFacing}",
                            lensFacing,
                            Camera2Config::class.simpleName,
                            Camera2Config.defaultConfig(),
                        )
                    )
                    add(
                        arrayOf(
                            "config=${CameraPipeConfig::class.simpleName} lensFacing={$lensFacing}",
                            lensFacing,
                            CameraPipeConfig::class.simpleName,
                            CameraPipeConfig.defaultConfig(),
                        )
                    )
                }
            }
    }

    @get:Rule
    val cameraRule =
        CameraUtil.grantCameraPermissionAndPreTestAndPostTest(PreTestCameraIdList(cameraConfig))

    @get:Rule val labTestRule = LabTestRule()

    private lateinit var cameraProvider: ProcessCameraProvider
    private val lifecycleOwner = FakeLifecycleOwner()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

    @Before
    fun setUp() = runBlocking {
        ProcessCameraProvider.configureInstance(cameraConfig)
        cameraProvider = ProcessCameraProvider.awaitInstance(context)
        lifecycleOwner.startAndResume()

        // Ensure there's at least one camera available for the selector
        assumeTrue(cameraProvider.hasCamera(cameraSelector))
    }

    @After
    fun tearDown() = runBlocking {
        if (::cameraProvider.isInitialized) {
            withContext(Dispatchers.Main) { cameraProvider.unbindAll() }
            cameraProvider.shutdownAsync().get(10, TimeUnit.SECONDS)
        }
    }

    private suspend fun createPreview(): Preview {
        return withContext(Dispatchers.Main) {
            Preview.Builder().build().apply {
                surfaceProvider = SurfaceTextureProvider.createAutoDrainingSurfaceTextureProvider()
            }
        }
    }

    @Test
    fun cameraGoesToOpenState_whenUseCaseIsBound(): Unit = runBlocking {
        val preview = createPreview()
        val imageCapture = ImageCapture.Builder().build()
        val camera =
            withContext(Dispatchers.Main) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                )
            }

        camera.assertCameraState(CameraState.Type.OPEN)

        // Explicitly check current state
        val currentCameraState = camera.cameraInfo.cameraState.value
        assertThat(currentCameraState?.type).isEqualTo(CameraState.Type.OPEN)
        assertThat(currentCameraState?.error).isNull()
    }

    @Test
    fun cameraGoesToOpenState_whenLifecycleStartsLater(): Unit = runBlocking {
        val preview = createPreview()
        val imageCapture = ImageCapture.Builder().build()
        val lifecycleOwner = FakeLifecycleOwner()

        val camera =
            withContext(Dispatchers.Main) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                )
            }

        camera.assertCameraState(CameraState.Type.CLOSED)

        lifecycleOwner.startAndResume()

        camera.assertCameraState(CameraState.Type.OPEN)
        // Explicitly check current state
        val currentCameraState = camera.cameraInfo.cameraState.value
        assertThat(currentCameraState?.type).isEqualTo(CameraState.Type.OPEN)
        assertThat(currentCameraState?.error).isNull()
    }

    private suspend fun Camera.assertCameraState(expectedType: CameraState.Type): Unit {
        val latch = CountDownLatch(1)
        withContext(Dispatchers.Main) {
            cameraInfo.cameraState.observeForever { state ->
                if (state.type == expectedType) {
                    latch.countDown()
                }
            }
        }
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun cameraGoesToClosedState_whenUnbound() = runBlocking {
        val preview = createPreview()
        val camera =
            withContext(Dispatchers.Main) {
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            }

        camera.assertCameraState(CameraState.Type.OPEN)

        withContext(Dispatchers.Main) { cameraProvider.unbind(preview) }

        camera.assertCameraState(CameraState.Type.CLOSED)
    }

    @Test
    fun cameraGoesToClosedState_whenLifecycleStops() = runBlocking {
        val preview = createPreview()
        val camera =
            withContext(Dispatchers.Main) {
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            }

        camera.assertCameraState(CameraState.Type.OPEN)

        lifecycleOwner.pauseAndStop()

        camera.assertCameraState(CameraState.Type.CLOSED)
    }

    @Test
    fun cameraTransitionsThroughOpeningState() = runBlocking {
        val preview = createPreview()
        val openingLatch = CountDownLatch(1)
        val openLatch = CountDownLatch(1)
        var observedOpening = false
        var observedOpen = false

        withContext(Dispatchers.Main) {
            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            camera.cameraInfo.cameraState.observeForever { state ->
                when (state.type) {
                    CameraState.Type.OPENING -> {
                        observedOpening = true
                        openingLatch.countDown()
                    }

                    CameraState.Type.OPEN -> {
                        if (observedOpening) { // Ensure OPENING was seen first
                            observedOpen = true
                        }
                        openLatch.countDown()
                    }
                    else -> {}
                }
            }
        }

        assertThat(openingLatch.await(3, TimeUnit.SECONDS)).isTrue()
        assertThat(observedOpening).isTrue()
        assertThat(openLatch.await(3, TimeUnit.SECONDS)).isTrue()
        assertThat(observedOpen).isTrue() // Check if OPEN was observed after OPENING
    }

    @Test
    fun cameraTransitionsThroughClosingState(): Unit = runBlocking {
        val preview = createPreview()

        // Bind and wait for open
        val camera =
            withContext(Dispatchers.Main) {
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            }
        camera.assertCameraState(CameraState.Type.OPEN) // Wait until open

        val closingLatch = CountDownLatch(1)
        val closedLatch = CountDownLatch(1)
        var observedClosing = false
        var observedClosed = false

        withContext(Dispatchers.Main) {
            camera.cameraInfo.cameraState.observeForever { state ->
                when (state.type) {
                    CameraState.Type.CLOSING -> {
                        observedClosing = true
                        closingLatch.countDown()
                    }
                    CameraState.Type.CLOSED -> {
                        if (observedClosing) { // Ensure CLOSING was seen first
                            observedClosed = true
                        }
                        closedLatch.countDown()
                    }
                    else -> {}
                }
            }
        }

        // Trigger unbind
        withContext(Dispatchers.Main) { cameraProvider.unbind(preview) }

        assertThat(closingLatch.await(3, TimeUnit.SECONDS)).isTrue()
        assertThat(observedClosing).isTrue()
        assertThat(closedLatch.await(3, TimeUnit.SECONDS)).isTrue()
        assertThat(observedClosed).isTrue()
    }
}
