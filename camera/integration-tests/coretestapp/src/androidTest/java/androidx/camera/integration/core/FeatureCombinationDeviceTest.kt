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
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ExperimentalSessionConfig
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.featurecombination.ExperimentalFeatureCombination
import androidx.camera.core.featurecombination.Feature.Companion.FPS_60
import androidx.camera.core.featurecombination.Feature.Companion.HDR_HLG10
import androidx.camera.core.featurecombination.Feature.Companion.IMAGE_ULTRA_HDR
import androidx.camera.core.featurecombination.Feature.Companion.PREVIEW_STABILIZATION
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.testing.impl.CameraPipeConfigTestRule
import androidx.camera.testing.impl.CameraUtil
import androidx.camera.testing.impl.fakes.FakeLifecycleOwner
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
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
import org.junit.runners.Parameterized

@OptIn(ExperimentalFeatureCombination::class, ExperimentalSessionConfig::class)
@LargeTest
@RunWith(Parameterized::class)
class FeatureCombinationDeviceTest(
    private val selectorName: String,
    private val cameraSelector: CameraSelector,
    private val implName: String,
    private val cameraXConfig: CameraXConfig
) {
    @get:Rule
    val useCamera =
        CameraUtil.grantCameraPermissionAndPreTestAndPostTest(
            CameraUtil.PreTestCameraIdList(
                if (implName == Camera2Config::class.simpleName) {
                    Camera2Config.defaultConfig()
                } else {
                    CameraPipeConfig.defaultConfig()
                }
            )
        )

    @get:Rule
    val cameraPipeConfigTestRule =
        CameraPipeConfigTestRule(
            active = implName == CameraPipeConfig::class.simpleName,
        )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var fakeLifecycleOwner: FakeLifecycleOwner

    private val preview = Preview.Builder().build()
    private val imageCapture = ImageCapture.Builder().build()
    private val videoCapture = VideoCapture.withOutput(Recorder.Builder().build())

    @Before
    fun setUp() = runBlocking {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(cameraSelector.lensFacing!!))

        ProcessCameraProvider.configureInstance(cameraXConfig)
        cameraProvider = ProcessCameraProvider.getInstance(context)[10, TimeUnit.SECONDS]

        withContext(Dispatchers.Main) {
            fakeLifecycleOwner = FakeLifecycleOwner().apply { startAndResume() }
        }
    }

    @After
    fun tearDown() {
        if (::cameraProvider.isInitialized) {
            cameraProvider.shutdownAsync()[10, TimeUnit.SECONDS]
        }
    }

    @Test
    fun bindToLifecycle_hlg10_canBindSuccessfullyWhenSupported(): Unit = runBlocking {
        assumeTrue(
            cameraProvider
                .getCameraInfo(cameraSelector)
                .isFeatureCombinationSupported(setOf(preview, videoCapture), setOf(HDR_HLG10))
        )

        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                cameraSelector,
                SessionConfig(
                    useCases = listOf(preview, videoCapture),
                    requiredFeatures = setOf(HDR_HLG10)
                )
            )
        }

        // TODO: b/413177092: Check camera capture results or recording to confirm if features are
        //  actually set to the camera framework, not too useful right now as
        //  isFeatureCombinationSupported may not support multiple features properly due to not
        //  adding feature combination surface resolutions in SupportedSurfaceCombination yet
    }

    @Test
    fun bindToLifecycle_fps60_canBindSuccessfullyWhenSupported(): Unit = runBlocking {
        assumeTrue(
            cameraProvider
                .getCameraInfo(cameraSelector)
                .isFeatureCombinationSupported(setOf(preview, videoCapture), setOf(FPS_60))
        )

        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                cameraSelector,
                SessionConfig(
                    useCases = listOf(preview, videoCapture),
                    requiredFeatures = setOf(FPS_60)
                )
            )
        }

        // TODO: b/413177092: Check camera capture results or recording to confirm if features are
        //  actually set to the camera framework, not too useful right now as
        //  isFeatureCombinationSupported may not support multiple features properly due to not
        //  adding feature combination surface resolutions in SupportedSurfaceCombination yet
    }

    @Test
    fun bindToLifecycle_previewStabilization_canBindSuccessfullyWhenSupported(): Unit =
        runBlocking {
            assumeTrue(
                cameraProvider
                    .getCameraInfo(cameraSelector)
                    .isFeatureCombinationSupported(
                        setOf(preview, videoCapture),
                        setOf(PREVIEW_STABILIZATION)
                    )
            )

            withContext(Dispatchers.Main) {
                cameraProvider.bindToLifecycle(
                    fakeLifecycleOwner,
                    cameraSelector,
                    SessionConfig(
                        useCases = listOf(preview, videoCapture),
                        requiredFeatures = setOf(PREVIEW_STABILIZATION)
                    )
                )
            }

            // TODO: b/413177092: Check camera capture results or recording to confirm if features
            //  are actually set to the camera framework, not too useful right now as
            //  isFeatureCombinationSupported may not support multiple features properly due to not
            //  adding feature combination surface resolutions in SupportedSurfaceCombination yet
        }

    @Test
    fun bindToLifecycle_jpegUltraHdr_canBindSuccessfullyWhenSupported(): Unit = runBlocking {
        assumeTrue(
            cameraProvider
                .getCameraInfo(cameraSelector)
                .isFeatureCombinationSupported(setOf(preview, imageCapture), setOf(IMAGE_ULTRA_HDR))
        )

        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                cameraSelector,
                SessionConfig(
                    useCases = listOf(preview, imageCapture),
                    requiredFeatures = setOf(IMAGE_ULTRA_HDR)
                )
            )
        }

        // TODO: b/413177092: Check camera capture results or recording to confirm if features are
        //  actually set to the camera framework, not too useful right now as
        //  isFeatureCombinationSupported may not support multiple features properly due to not
        //  adding feature combination surface resolutions in SupportedSurfaceCombination yet
    }

    @Test
    fun bindToLifecycle_bothHdrAndFps60Required_canBindSuccessfullyIfSupported(): Unit =
        runBlocking {
            val features = setOf(HDR_HLG10, FPS_60)

            assumeTrue(
                cameraProvider
                    .getCameraInfo(cameraSelector)
                    .isFeatureCombinationSupported(setOf(preview, videoCapture), features)
            )

            withContext(Dispatchers.Main) {
                cameraProvider.bindToLifecycle(
                    fakeLifecycleOwner,
                    cameraSelector,
                    SessionConfig(
                        useCases = listOf(preview, videoCapture),
                        requiredFeatures = features
                    )
                )
            }

            // TODO: b/413177092: Check camera capture results or recording to confirm if features
            //  are actually set to the camera framework, not too useful right now as
            //  isFeatureCombinationSupported may not support multiple features properly due to not
            //  adding feature combination surface resolutions in SupportedSurfaceCombination yet
        }

    @Test
    fun bindToLifecycle_bothHdrAndStabilizationRequired_canBindSuccessfullyIfSupported(): Unit =
        runBlocking {
            val features = setOf(HDR_HLG10, PREVIEW_STABILIZATION)

            assumeTrue(
                cameraProvider
                    .getCameraInfo(cameraSelector)
                    .isFeatureCombinationSupported(setOf(preview, videoCapture), features)
            )

            withContext(Dispatchers.Main) {
                cameraProvider.bindToLifecycle(
                    fakeLifecycleOwner,
                    cameraSelector,
                    SessionConfig(
                        useCases = listOf(preview, videoCapture),
                        requiredFeatures = features
                    )
                )
            }

            // TODO: b/413177092: Check camera capture results or recording to confirm if features
            //  are actually set to the camera framework, not too useful right now as
            //  isFeatureCombinationSupported may not support multiple features properly due to not
            //  adding feature combination surface resolutions in SupportedSurfaceCombination yet
        }

    @Test
    fun bindToLifecycle_moreThanTwoFeaturesRequired_canBindSuccessfullyIfSupported(): Unit =
        runBlocking {
            val features = setOf(HDR_HLG10, FPS_60, PREVIEW_STABILIZATION)

            assumeTrue(
                cameraProvider
                    .getCameraInfo(cameraSelector)
                    .isFeatureCombinationSupported(setOf(preview, videoCapture), features)
            )

            withContext(Dispatchers.Main) {
                cameraProvider.bindToLifecycle(
                    fakeLifecycleOwner,
                    cameraSelector,
                    SessionConfig(
                        useCases = listOf(preview, videoCapture),
                        requiredFeatures = features
                    )
                )
            }

            // TODO: b/413177092: Check camera capture results or recording to confirm if features
            //  are actually set to the camera framework, not too useful right now as
            //  isFeatureCombinationSupported may not support multiple features properly due to not
            //  adding feature combination surface resolutions in SupportedSurfaceCombination yet
        }

    @Test
    fun bindToLifecycle_multiplePreferredFeatures_canBindSuccessfully(): Unit = runBlocking {
        val orderedFeatures = listOf(HDR_HLG10, FPS_60, PREVIEW_STABILIZATION)

        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                cameraSelector,
                SessionConfig(
                    useCases = listOf(preview, videoCapture),
                    preferredFeatures = orderedFeatures
                )
            )
        }

        // TODO: b/413177092: Check camera capture results or recording to confirm if features
        //  are actually set to the camera framework, not too useful right now as
        //  isFeatureCombinationSupported may not support multiple features properly due to not
        //  adding feature combination surface resolutions in SupportedSurfaceCombination yet
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "selector={0},config={2}")
        fun data() =
            listOf(
                arrayOf(
                    "back",
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    Camera2Config::class.simpleName,
                    Camera2Config.defaultConfig()
                ),
                arrayOf(
                    "back",
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    CameraPipeConfig::class.simpleName,
                    CameraPipeConfig.defaultConfig()
                ),
                arrayOf(
                    "front",
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    Camera2Config::class.simpleName,
                    Camera2Config.defaultConfig()
                ),
                arrayOf(
                    "front",
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    CameraPipeConfig::class.simpleName,
                    CameraPipeConfig.defaultConfig()
                ),
            )
    }
}
